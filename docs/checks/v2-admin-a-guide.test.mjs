import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require=createRequire(new URL('../../frontend/package.json',import.meta.url))
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const {ref,computed,reactive}=require('vue')
const MarkdownIt=require('markdown-it')
// 直接读取指南中的完整参考代码，独立于Git忽略的候选目录。
// 用户手敲后，助手会把此入口改为读取实际源码再执行验收。
const guide=readFileSync(new URL('../20-v2-admin-foundation.md',import.meta.url),'utf8').replace(/\r\n/g,'\n')
const code=file=>{
  const section=guide.split(`文件：\`frontend/${file}\`。`)[1]
  assert.ok(section,'Missing guide file: '+file)
  return section.match(/```(?:js|vue|css)\n([\s\S]*?)\n```/)[1]
}
const response=(body,status=200)=>new Response(status===204?null:JSON.stringify(body),{status})
function session(fetch){
  const source=code('src/adminSession.js').replace(/^import[^\n]*\n/gm,'').replace(/export /g,'')
  return new Function('ref','fetch',source+';return {adminConnected,adminLeaving,adminSessionError,clearAdminSession,adminRequest,loginAdmin,adminTarget,guardAdmin}')(ref,fetch)
}
function component(file,bindings,names){
  const source=parse(code('src/views/'+file)).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  return new Function(...Object.keys(bindings),source+';return {'+names+'}')(...Object.values(bindings))
}

test('5A全部候选组件可编译，访客与后台外框和嵌套路由存在',()=>{
  for(const file of ['src/App.vue','src/views/AdminView.vue','src/views/AdminLayout.vue','src/views/AdminLoginView.vue','src/views/AdminArticleListView.vue']){
    const {descriptor,errors}=parse(code(file));assert.deepEqual(errors,[])
    const compiled=compileScript(descriptor,{id:file})
    assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:file,id:file,compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  }
  assert.match(code('src/main.js'),/router.beforeEach\(guardAdmin\)/)
  assert.match(code('src/main.js'),/path: 'articles\/:id\/edit'/)
  assert.match(code('src/App.vue'),/<RouterView v-if="isAdmin"/)
})

test('内存认证支持中文密码、每次写入新CSRF、204退出及失败后清理',async()=>{
  const calls=[];let reject=false
  const app=session(async(path,options)=>{
    calls.push({path,options})
    assert.equal(options.headers.get('X-Requested-With'),'XMLHttpRequest')
    assert.equal(options.credentials,'same-origin')
    if(path.endsWith('/check'))return response({ok:true},reject?401:200)
    if(path.endsWith('/csrf'))return response({headerName:'X-CSRF-TOKEN',token:'fresh'})
    assert.equal(options.headers.get('X-CSRF-TOKEN'),'fresh')
    return response(null,204)
  })
  await app.loginAdmin('中文密码');assert.equal(app.adminConnected.value,true)
  const auth=calls[0].options.headers.get('Authorization')
  assert.equal(Buffer.from(auth.slice(6),'base64').toString('utf8'),'admin:中文密码')
  assert.equal(await app.adminRequest('/logout',{method:'POST'}),null)
  assert.equal(calls.filter(x=>x.path.endsWith('/csrf')).length,1)
  app.clearAdminSession();reject=true
  await assert.rejects(app.loginAdmin('wrong'),/认证失败/);assert.equal(app.adminConnected.value,false)
  assert.doesNotMatch(code('src/adminSession.js'),/localStorage|sessionStorage/)
})

test('旧请求包括延迟JSON在会话变化后失效，不把旧登录结果写回',async()=>{
  let finish
  const app=session(async()=>({ok:true,status:200,json:()=>new Promise(done=>{finish=done})}))
  const pending=app.loginAdmin('old')
  await Promise.resolve();await Promise.resolve()
  app.clearAdminSession();finish({ok:true})
  await assert.rejects(pending,/登录状态已变化/);assert.equal(app.adminConnected.value,false)
})

test('管理入口守卫、返回地址白名单、退出失败保留会话和退出成功清理',async()=>{
  let fail=true
  const app=session(async(path)=>path.endsWith('/check')?response({ok:true}):path.endsWith('/csrf')?response({headerName:'X-CSRF-TOKEN',token:'token'}):response(null,fail?503:204))
  for(const raw of ['https://evil.example','//evil.example','/admin/login','/admin/articles/0/edit','/admin/articles/9223372036854775808/edit','/admin/articles?next=//evil'])assert.equal(app.adminTarget(raw),'/admin/articles')
  assert.equal(app.adminTarget('/admin/articles/9223372036854775807/edit'),'/admin/articles/9223372036854775807/edit')
  const target={path:'/admin/resources',meta:{requiresAdmin:true},params:{}}
  assert.deepEqual(await app.guardAdmin(target),{path:'/admin/login',query:{next:'/admin/resources'},replace:true})
  await app.loginAdmin('only-fixture')
  assert.equal(await app.guardAdmin({path:'/admin/login',meta:{},params:{}}),false)
  assert.equal(app.adminConnected.value,true);assert.match(app.adminSessionError.value,/503/)
  fail=false;await app.guardAdmin({path:'/admin/login',meta:{},params:{}})
  assert.equal(app.adminConnected.value,false);assert.equal(app.adminLeaving.value,false)
})

test('独立登录防重复提交、阻止验证中离开、失败反馈与成功安全返回',async()=>{
  let resolve,leave,calls=0;const destinations=[]
  const app=component('AdminLoginView.vue',{ref,onBeforeUnmount:()=>{},onBeforeRouteLeave:fn=>{leave=fn},useRoute:()=>({query:{next:'//evil'}}),useRouter:()=>({replace:async url=>destinations.push(url)}),adminLeaving:ref(false),adminTarget:()=>'/admin/articles',loginAdmin:()=>{calls++;return new Promise(done=>{resolve=done})}},'password,busy,error,connect')
  app.password.value='fixture';const first=app.connect();await app.connect();assert.equal(calls,1);assert.equal(leave(),false)
  resolve();await first;assert.deepEqual(destinations,['/admin/articles']);assert.equal(app.password.value,'');assert.equal(app.busy.value,false);assert.equal(leave(),true)
})

test('独立文章列表的草稿计数、错误重试、旧响应与卸载保护',async()=>{
  let fail=true;const leaves=[]
  const app=component('AdminArticleListView.vue',{ref,computed,onMounted:()=>{},onBeforeUnmount:fn=>leaves.push(fn),adminRequest:async()=>{if(fail)throw Error('503');return [{id:1,status:'DRAFT'},{id:2,status:'PUBLISHED'}]}},'articles,loading,error,drafts,loadArticles')
  await app.loadArticles();assert.match(app.error.value,/503/);fail=false;await app.loadArticles();assert.equal(app.drafts.value,1);assert.equal(app.articles.value.length,2)
  const pending=[];let unmount
  const race=component('AdminArticleListView.vue',{ref,computed,onMounted:()=>{},onBeforeUnmount:fn=>{unmount=fn},adminRequest:()=>new Promise(done=>pending.push(done))},'articles,loadArticles')
  const old=race.loadArticles(),latest=race.loadArticles();pending[1]([{id:2}]);await latest;pending[0]([{id:1}]);await old;assert.equal(race.articles.value[0].id,2)
  const abandoned=race.loadArticles();unmount();pending[2]([{id:3}]);await abandoned;assert.equal(race.articles.value[0].id,2)
})

test('过渡工作区仅加载对应任务，编辑失败不出现空新建表单，离开和换ID均检查未保存内容',async()=>{
  const route=reactive({meta:{adminSection:'article'},params:{id:'42'}}),calls=[],guards=[]
  let fail=false,confirm=false
  const bindings={ref,computed,reactive,MarkdownIt,onMounted:()=>{},onUnmounted:()=>{},onBeforeRouteLeave:fn=>guards.push(fn),onBeforeRouteUpdate:fn=>guards.push(fn),useRoute:()=>route,window:{confirm:()=>confirm},request:async path=>{calls.push(path);if(fail)throw Error('404');return path==='/categories'||path==='/tags'?[]:path==='/resources'?[]:{id:42,title:'saved',tagIds:[]}}}
  const app=component('AdminView.vue',bindings,'form,ready,busy,error,loadWorkspace,articleDirty')
  await app.loadWorkspace();assert.deepEqual(calls,['/categories','/tags','/articles/42']);assert.equal(app.form.id,42);assert.equal(app.ready.value,true)
  app.form.title='unsaved';assert.equal(app.articleDirty.value,true);assert.equal(guards.length,2);assert.equal(guards[0](),false);assert.equal(guards[1](),false);confirm=true;assert.equal(guards[0](),true)
  fail=true;await app.loadWorkspace();assert.equal(app.ready.value,false);assert.match(app.error.value,/404/)
  fail=false;calls.length=0;route.meta.adminSection='resources';await app.loadWorkspace();assert.deepEqual(calls,['/resources'])
})
