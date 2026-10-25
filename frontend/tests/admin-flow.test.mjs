import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { ref, computed, reactive, nextTick, watch, effectScope } from 'vue'
import MarkdownIt from 'markdown-it'
const code=file=>readFileSync(new URL('../src/'+file,import.meta.url),'utf8').replace(/\r\n/g,'\n')
const response=(data,status=200)=>new Response(status===204?null:JSON.stringify(data),{status})
function session(fetch){return new Function('ref','fetch',code('adminSession.js').replace(/^import[^\n]*\n/gm,'').replace(/export /g,'')+';return {adminConnected,adminRequest,loginAdmin,clearAdminSession,adminTarget,adminListQuery,guardAdmin,adminSessionError}')(ref,fetch)}
function component(file,extra,names){
  const source=parse(code(file)).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  const bindings={usePageMeta:()=>{},ref,computed,reactive,nextTick,watch:()=>{},onMounted:()=>{},onBeforeUnmount:()=>{},MarkdownIt,notifyAdmin:()=>{},...extra}
  return new Function(...Object.keys(bindings),source+';return {'+names+'}')(...Object.values(bindings))
}
const leave=()=>({discardOpen:ref(false),resolveDiscard:()=>{},askDiscard:()=>true,navigateSaved:async action=>action()})
function editor(request,extra={}){
  const route=reactive({params:{},query:{from:'/admin/articles?status=DRAFT&page=2'}}), destinations=[]
  const app=component('views/AdminArticleEditorView.vue',{useRoute:()=>route,useRouter:()=>({replace:async next=>{destinations.push(next);route.params.id=next.path?.split('/')[3]}}),adminRequest:request,adminTarget:raw=>raw??'/admin/articles',useAdminLeave:leave,...extra},'form,ready,busy,loading,error,savedSnapshot,dirty,fillForm,emptyArticle,loadDocument,persist,save,action,resolveAction,payload,textarea,insertMarkdown,preview,conflict,serverCopy,compareServer,retryLoad')
  return {...app,route,destinations}
}

test('第五轮独立后台和确认组件可编译',()=>{
  for(const file of ['App.vue','views/AdminLayout.vue','views/AdminLoginView.vue','views/AdminArticleListView.vue','views/AdminArticleEditorView.vue','views/AdminTaxonomyView.vue','views/AdminResourceView.vue','components/AdminTaxonomyPanel.vue','components/AdminConfirmDialog.vue']){
    const {descriptor,errors}=parse(code(file));assert.deepEqual(errors,[])
    const script=compileScript(descriptor,{id:file});assert.deepEqual(compileTemplate({id:file,filename:file,source:descriptor.template.content,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  }
})
test('真实内存认证、中文密码、新CSRF、204退出及安全返回地址',async()=>{
  const calls=[];const app=session(async(path,options)=>{
    calls.push({path,options});assert.equal(options.headers.get('X-Requested-With'),'XMLHttpRequest')
    if(path.endsWith('/check'))return response({ok:true})
    if(path.endsWith('/csrf'))return response({headerName:'X-CSRF-TOKEN',token:'fresh'})
    assert.equal(options.headers.get('X-CSRF-TOKEN'),'fresh');return response(null,204)
  })
  await app.loginAdmin('测试密码');assert.equal(Buffer.from(calls[0].options.headers.get('Authorization').slice(6),'base64').toString('utf8'),'admin:测试密码')
  await app.guardAdmin({path:'/admin/login',meta:{},params:{}});assert.equal(app.adminConnected.value,false)
  for(const raw of ['//evil.example','/admin/login','/admin/articles/0/edit','/admin/articles/9223372036854775808/edit'])assert.equal(app.adminTarget(raw),'/admin/articles')
  assert.equal(app.adminTarget('/admin/articles?page=2&status=DRAFT&junk=x'),'/admin/articles?status=DRAFT&page=2')
  assert.deepEqual(app.adminListQuery({page:'0',type:'PRIVATE',recycled:'true',categoryId:'-1'}),{recycled:'true'})
  const result=await app.guardAdmin({path:'/admin/resources',fullPath:'/admin/resources',meta:{requiresAdmin:true},params:{}});assert.equal(result.query.next,'/admin/resources')
  assert.doesNotMatch(code('adminSession.js'),/localStorage|sessionStorage/)
})
test('过期会话的延迟JSON无效；401清理认证；退出失败停留',async()=>{
  let finish;const old=session(async()=>({ok:true,status:200,json:()=>new Promise(done=>{finish=done})}))
  const pending=old.loginAdmin('old');await Promise.resolve();await Promise.resolve();old.clearAdminSession();finish({ok:true});await assert.rejects(pending,/登录状态已变化/)
  const fail=session(async()=>response({},401));await assert.rejects(fail.loginAdmin('wrong'),/认证失败/);assert.equal(fail.adminConnected.value,false)
  const app=session(async path=>path.endsWith('/check')?response({ok:true}):path.endsWith('/csrf')?response({headerName:'X-CSRF-TOKEN',token:'token'}):response({},503));await app.loginAdmin('fixture')
  assert.equal(await app.guardAdmin({path:'/admin/login',meta:{},params:{}}),false);assert.match(app.adminSessionError.value,/503/);assert.equal(app.adminConnected.value,true)
})
test('站内未保存确认的取消、继续、保存期间阻止离开及关闭保护',async()=>{
  const dirty=ref(true),busy=ref(false),guards=[],cleanup=[],events=[]
  const source=code('useAdminLeave.js').replace(/^import[^\n]*\n/gm,'').replace('export function','function')
  const use=new Function('ref','onMounted','onBeforeUnmount','onBeforeRouteLeave','onBeforeRouteUpdate','window',source+';return useAdminLeave')(ref,fn=>fn(),fn=>cleanup.push(fn),fn=>guards.push(fn),fn=>guards.push(fn),{addEventListener:(...args)=>events.push(args),removeEventListener:()=>{}})
  const app=use(dirty,busy),cancel=app.askDiscard();assert.equal(app.discardOpen.value,true);app.resolveDiscard(false);assert.equal(await cancel,false)
  const accepted=guards[1]();app.resolveDiscard(true);assert.equal(await accepted,true)
  busy.value=true;assert.equal(guards[0](),false);assert.equal(await app.navigateSaved(()=>guards[0]()),true)
  let prevented=false;events[0][1]({preventDefault:()=>{prevented=true},returnValue:null});assert.equal(prevented,true)
  busy.value=false;const abandoned=app.askDiscard();cleanup[0]();assert.equal(await abandoned,false)
})
test('编辑器第一次保存同步编号，发布失败保留草稿并以PUT重试，分类标签/预览不丢失',async()=>{
  const calls=[];let failPublish=true,failSave=false
  const app=editor(async(path,options)=>{
    calls.push({path,options});if(path.endsWith('/publish')){if(failPublish)throw Error('503');return {id:41,status:'PUBLISHED'}}
    if(failSave)throw Error('输入校验失败')
    const payload=JSON.parse(options.body);assert.equal(payload.categoryId,5);assert.deepEqual(payload.tagIds,[11,22]);return {id:41,status:'DRAFT'}
  })
  app.fillForm(app.emptyArticle());app.ready.value=true
  Object.assign(app.form,{title:'笔记',contentMarkdown:'# Body\n<script>bad()</script>',categoryId:5,tagIds:[11,22]})
  await app.save(true);assert.equal(app.form.id,41);assert.match(app.error.value,/已保存.*503/);assert.equal(app.dirty.value,false);assert.equal(app.destinations.length,1)
  assert.match(app.preview.value,/&lt;script&gt;/);assert.doesNotMatch(app.preview.value,/<script>/)
  failPublish=false;await app.save(true);assert.equal(app.form.status,'PUBLISHED');assert.equal(calls.filter(x=>x.options.method==='POST'&&x.path==='/articles').length,1);assert.equal(calls[2].options.method,'PUT')
  app.form.title='未保存';failSave=true;await app.save();assert.equal(app.form.title,'未保存');assert.equal(app.dirty.value,true)
  const source={...app.emptyArticle(),tagIds:[1]};app.fillForm(source);app.form.tagIds.push(2);assert.deepEqual(source.tagIds,[1])
})
test('编辑加载失败、旧响应和回收只读保护，撤回保留未保存内容及工具栏选择',async()=>{
  const requests=[];let unmount;const app=editor(path=>path==='/categories'||path==='/tags'?Promise.resolve([]):new Promise(done=>requests.push(done)),{onBeforeUnmount:fn=>{unmount=fn}})
  app.route.params.id='1';const pending=app.loadDocument();await Promise.resolve();app.route.params.id='2';const latest=app.loadDocument();await Promise.resolve();requests[1]({id:2,title:'recycled',deletedAt:'2040-01-01',tagIds:[]});await latest
  requests[0]({id:1,title:'old',tagIds:[]});await pending;assert.equal(app.form.id,2)
  app.route.params.id='3';const abandoned=app.loadDocument();unmount();requests[2]({id:3,title:'unmounted'});await abandoned
  assert.equal(app.form.id,2);assert.equal(app.dirty.value,false)
  // 独立执行撤回，尚未保存的正文不会被服务器旧内容覆盖。
  const active=editor(async()=>({id:3,status:'DRAFT'}));active.fillForm({...active.emptyArticle(),id:3,status:'PUBLISHED'});active.ready.value=true;active.form.contentMarkdown='未保存正文';active.action.value='unpublish';await active.resolveAction(true);assert.equal(active.form.status,'DRAFT');assert.equal(active.form.contentMarkdown,'未保存正文');assert.equal(active.dirty.value,true)
  const selected=[];active.textarea.value={selectionStart:0,selectionEnd:2,focus:()=>{},setSelectionRange:(...range)=>selected.push(range)};await active.insertMarkdown('**','**');assert.match(active.form.contentMarkdown,/^\*\*未保\*\*/);assert.deepEqual(selected[0],[2,4])
})
test('站内分类标签表单：新增、重名、改名、取消及校验，保留关联编号',async()=>{
  const stored=[],emitted=[],scope=effectScope();let app
  scope.run(()=>{app=component('components/AdminTaxonomyPanel.vue',{defineProps:()=>({path:'/tags',label:'标签'}),defineEmits:()=>((...args)=>emitted.push(args)),watch,adminRequest:async(path,options)=>{
    if(!options)return stored
    const body=JSON.parse(options.body);if(stored.some(item=>item.name===body.name)&&options.method==='POST')throw Error('标签名称已存在')
    const item={id:options.method==='POST'?stored.length+1:+path.split('/').at(-1),name:body.name};stored.splice(Math.max(0,stored.findIndex(x=>x.id===item.id)),options.method==='POST'?0:1,item);return item
  }},'name,original,editingId,items,error,message,saveName,edit,cancel,loadItems')})
  app.name.value=' Vue ';await app.saveName();assert.equal(app.items.value[0].name,'Vue');app.name.value='Vue';await app.saveName();assert.match(app.error.value,/已存在/)
  app.cancel();app.edit(app.items.value[0]);app.name.value='Vue 3';await app.saveName();assert.equal(app.items.value[0].id,1);assert.equal(app.items.value[0].name,'Vue 3')
  app.name.value='x'.repeat(51);await app.saveName();assert.match(app.error.value,/1～50/);app.cancel();assert.equal(app.name.value,'');scope.stop()
})
test('资源保存成功后列表失败仍保留编号，后续PUT和取消切换保护',async()=>{
  const writes=[];let cancel=false
  const app=component('views/AdminResourceView.vue',{useAdminLeave:()=>({...leave(),askDiscard:()=>!cancel}),adminRequest:async(path,options)=>{
    if(!options)throw Error('列表503')
    writes.push(options.method);return {...JSON.parse(options.body),id:9}
  }},'form,items,dirty,error,listError,saveResource,openResource')
  Object.assign(app.form,{title:'资源',url:'https://example.com',description:'说明'});await app.saveResource();assert.equal(app.form.id,9);assert.equal(app.dirty.value,false);assert.match(app.listError.value,/503/)
  app.form.description='已修改';await app.saveResource();assert.deepEqual(writes,['POST','PUT'])
  app.form.title='保留输入';cancel=true;await app.openResource();assert.equal(app.form.title,'保留输入')
})
test('后台分页只接受最新响应，回收和恢复调用正确接口并重新读取列表',async()=>{
  const route=reactive({query:{status:'DRAFT'},fullPath:'/admin/articles?status=DRAFT'}),calls=[]
  const app=component('views/AdminArticleListView.vue',{useRoute:()=>route,useRouter:()=>({replace:async()=>{},push:async()=>{}}),adminListQuery:input=>input,useAdminLeave:leave,adminRequest:async(path,options)=>{calls.push({path,options});return {items:[],total:0,page:1,totalPages:0}}},'confirmation,performAction,result,error,loadArticles,editorLocation')
  assert.equal(app.editorLocation('new').path,'/admin/articles/new')
  await app.loadArticles();assert.equal(app.result.value.total,0);app.confirmation.value={article:{id:7,title:'草稿'},action:'recycle'};await app.performAction(true);assert.equal(calls[1].path,'/articles/7');assert.equal(calls[1].options.method,'DELETE')
  app.confirmation.value={article:{id:7,title:'草稿'},action:'restore'};await app.performAction(true);assert.ok(calls.some(x=>x.path==='/articles/7/restore'));assert.equal(app.editorLocation(7).query.from,route.fullPath)
})

test('公开文保存只更新编辑稿，显式发布使用保存返回的新版本',async()=>{
  const calls=[],notices=[];let version=7
  const app=editor(async(path,options)=>{
    calls.push({path,options});assert.equal(options.headers['If-Match'],`"${version}"`)
    version++
    return {id:42,status:'PUBLISHED',version,hasUnpublishedChanges:!path.endsWith('/publish')}
  },{notifyAdmin:text=>notices.push(text)})
  app.fillForm({...app.emptyArticle(),id:42,title:'已公开',contentMarkdown:'旧正文',status:'PUBLISHED',version});app.ready.value=true
  app.form.contentMarkdown='新编辑稿';await app.save()
  assert.equal(app.form.version,8);assert.equal(app.form.hasUnpublishedChanges,true);assert.equal(app.dirty.value,false)
  assert.deepEqual(calls.map(x=>x.options.method),['PUT']);assert.match(notices.at(-1),/公开版本尚未更新/)
  await app.save(true)
  assert.deepEqual(calls.map(x=>x.options.method),['PUT','PUT','POST']);assert.equal(calls.at(-1).path,'/articles/42/publish');assert.equal(app.form.version,10);assert.equal(app.form.hasUnpublishedChanges,false)
})

test('冲突保留输入和旧版本，查看服务器稿不自动授权覆盖，取消重载仍可复制本地稿',async()=>{
  const app=editor(async(path,options)=>{
    if(options)throw Object.assign(new Error('另一窗口已保存'),{status:412})
    return {id:42,title:'服务器标题',contentMarkdown:'服务器正文',version:9,status:'PUBLISHED'}
  },{useAdminLeave:()=>({...leave(),askDiscard:()=>false})})
  app.fillForm({...app.emptyArticle(),id:42,title:'原稿',contentMarkdown:'原文',version:7});app.ready.value=true
  app.form.title='本地标题';app.form.contentMarkdown='必须保留的本地正文';await app.save()
  assert.equal(app.conflict.value,true);assert.equal(app.dirty.value,true);assert.equal(app.form.version,7)
  await app.compareServer();assert.equal(app.serverCopy.value.version,9);assert.equal(app.form.version,7);assert.equal(app.form.contentMarkdown,'必须保留的本地正文')
  await app.retryLoad();assert.equal(app.form.title,'本地标题');assert.equal(app.ready.value,true)
})

test('保存后发生竞争时发布412不误报成功、不重复新建，错误状态可以区分',async()=>{
  const calls=[],notices=[]
  const app=editor(async(path,options)=>{
    calls.push({path,options});if(path.endsWith('/publish')){assert.equal(options.headers['If-Match'],'"0"');throw Object.assign(new Error('发布版本冲突'),{status:412})}
    return {id:99,status:'DRAFT',version:0}
  },{notifyAdmin:text=>notices.push(text)})
  app.fillForm(app.emptyArticle());app.ready.value=true;app.form.title='新稿';app.form.contentMarkdown='新文'
  await app.save(true);assert.equal(app.form.id,99);assert.equal(app.form.version,0);assert.equal(app.conflict.value,true);assert.match(app.error.value,/已保存.*发布未完成/);assert.equal(app.dirty.value,false)
  assert.equal(notices.filter(x=>x.includes('已发布')).length,0);assert.equal(calls.filter(x=>x.path==='/articles').length,1)
  const client=session(async path=>response(path.endsWith('/check')?{ok:true}:path.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'x'}:{},path.endsWith('/check')||path.endsWith('/csrf')?200:412))
  await client.loginAdmin('fixture');await assert.rejects(client.adminRequest('/articles/99',{method:'PUT',headers:{'If-Match':'"0"'},body:'{}'}),error=>error.status===412&&/当前输入仍保留/.test(error.message))
})
