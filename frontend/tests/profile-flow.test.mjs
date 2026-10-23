import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { mkdir,writeFile,readFile } from 'node:fs/promises'
import { promisify } from 'node:util'
import { execFile } from 'node:child_process'
import { createRequire } from 'node:module'
import { pathToFileURL,fileURLToPath } from 'node:url'
const require=createRequire(new URL('../package.json',import.meta.url))
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const {ref,computed,reactive,watch,nextTick,effectScope,createSSRApp}=require('vue')
const {renderToString}=require('@vue/server-renderer')
function code(file){return readFileSync(new URL(`../${file}`,import.meta.url),'utf8').replace(/\r\n/g,'\n')}
const profile=()=>new Function(code('src/siteProfile.js').replace('export const','const')+';return siteProfile')()
const response=(value,status=200,next=false)=>new Response(JSON.stringify(value),{status,headers:next?{link:'<https://api.github.com/next>; rel="next"'}:{}})
const snapshot=()=>({schemaVersion:1,owner:'InInNHD',syncedAt:'2026-10-20T08:00:00Z',projects:[{id:'1',name:'Alpha',fullName:'InInNHD/Alpha',description:'<script>alert(1)</script>',language:'Java',url:'https://github.com/InInNHD/Alpha',pushedAt:'2026-10-20T00:00:00Z'},{id:'2',name:'Beta',fullName:'InInNHD/Beta',description:'demo',language:'TypeScript',url:'https://github.com/InInNHD/Beta',pushedAt:null}],events:Array.from({length:12},(_,i)=>({id:String(i),repo:i===11?'InInNHD/Beta':'InInNHD/Alpha',kind:i===0?'RELEASE':i===1?'PUSH':'COMMIT',title:i===0?'<b>release</b>':'change '+i,url:'https://github.com/InInNHD/Alpha/commit/'+i,date:'2026-10-19T16:30:00Z',prerelease:i===0}))})
function about(fetch,details=profile()){
  const leaves=[],mounted=[]
  const bindings={ref,computed,nextTick,onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>leaves.push(fn),siteProfile:details,fetch}
  const script=parse(code('src/views/AboutView.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  const state=new Function(...Object.keys(bindings),script+';return {siteProfile,snapshot,loading,error,repository,kind,page,updatesTitle,externalLink,githubLink,directions,experiences,contacts,projects,languages,eventNames,filteredEvents,totalPages,visibleEvents,dateText,timeText,loadProjects,resetPage,changePage,showUpdates}')(...Object.values(bindings))
  return {...state,leave:()=>leaves.forEach(fn=>fn())}
}
async function html(state){
  const app=createSSRApp({setup:()=>state,template:parse(code('src/views/AboutView.vue')).descriptor.template.content})
  // 实际PageState替代旧内联状态；继续验证真实空状态文案。
  const descriptor=parse(code('src/components/PageState.vue')).descriptor
  const script=descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  app.component('PageState',{props:{kind:{default:'empty'},title:String,message:String,retryLabel:String,heading:{default:'h2'},layout:{default:'lines'},compact:Boolean},setup:props=>new Function('computed','defineProps','defineEmits',script+';return {caption}')(computed,()=>props,()=>()=>{}),template:descriptor.template.content})
  app.component('ProfileAvatar',{template:'<span>f</span>'})
  app.component('ArticlePagination',{props:['page','totalPages'],template:'<nav>{{page}} / {{totalPages}}</nav>'})
  app.component('RouterLink',{props:['to'],template:'<a><slot /></a>'})
  return renderToString(app)
}
test('关于页和头像可编译；采用已确认学习方向，个人经历与联系信息留空',()=>{
  for(const file of ['src/views/AboutView.vue','src/components/ProfileAvatar.vue']){const {descriptor,errors}=parse(code(file));assert.deepEqual(errors,[]);const script=compileScript(descriptor,{id:file});assert.deepEqual(compileTemplate({id:file,filename:file,source:descriptor.template.content,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])}
  const p=profile();assert.equal(p.githubUrl,'https://github.com/InInNHD');assert.equal(p.learningDirections.length,4);assert.ok(p.learningDirections.every(x=>x.title.trim()));assert.deepEqual(p.experiences,[]);assert.deepEqual(p.contactLinks,[])
})
test('真实模板：空个人区块隐藏，项目及提交标题HTML转义，非法外链不生成入口',async()=>{
  const p=profile();p.learningDirections=[];p.contactLinks=[{label:'safe',url:'https://example.com'},{label:'unsafe-contact',url:'javascript:alert(1)'}]
  const state=about(async()=>response(snapshot()),p);await state.loadProjects();const rendered=await html(state)
  assert.match(rendered,/Alpha/);assert.match(rendered,/TypeScript/);assert.match(rendered,/&lt;script&gt;alert\(1\)&lt;\/script&gt;/);assert.doesNotMatch(rendered,/<script>/)
  assert.match(rendered,/&lt;b&gt;release&lt;\/b&gt;/);assert.doesNotMatch(rendered,/<b>release/)
  assert.doesNotMatch(rendered,/id="about-learning"|id="about-experiences"|unsafe-contact/);assert.match(rendered,/safe/)
  const plain=about(async()=>response(snapshot()));await plain.loadProjects();assert.doesNotMatch(await html(plain),/id="about-contact"/)
})
test('项目与类型筛选、分页边界、查看更新定位及北京时间',async()=>{
  const state=about(async()=>response(snapshot()));await state.loadProjects();assert.equal(state.totalPages.value,2);assert.equal(state.visibleEvents.value.length,10)
  state.changePage(2);assert.equal(state.visibleEvents.value.length,2);state.changePage(3);assert.equal(state.page.value,2)
  state.repository.value='InInNHD/Beta';state.resetPage();assert.equal(state.page.value,1);assert.equal(state.filteredEvents.value.length,1)
  state.kind.value='RELEASE';state.resetPage();assert.equal(state.filteredEvents.value.length,0)
  const calls=[];state.updatesTitle.value={focus:()=>calls.push('focus'),scrollIntoView:()=>calls.push('scroll')}
  await state.showUpdates('InInNHD/Alpha');assert.equal(state.kind.value,'');assert.equal(state.page.value,1);assert.deepEqual(calls,['focus','scroll'])
  assert.match(state.dateText('2026-10-19T16:30:00Z'),/2026.*10.*20/);assert.equal(state.timeText('2026-10-19T16:30:00Z'),'00:30')
})
test('快照失败/格式错误/空结果与重试；卸载和旧JSON解析不覆盖新结果',async()=>{
  let value={},status=503
  const state=about(async()=>response(value,status));await state.loadProjects();assert.match(state.error.value,/503/);assert.equal(state.snapshot.value,null)
  status=200;await state.loadProjects();assert.match(state.error.value,/格式/)
  value=snapshot();value.events[0].date='invalid';await state.loadProjects();assert.match(state.error.value,/格式/)
  value={...snapshot(),projects:[],events:[]};await state.loadProjects();assert.equal(state.error.value,'');assert.match(await html(state),/暂时没有公开项目/)
  let resolve;const old=about(()=>new Promise(done=>{resolve=done}));const pending=old.loadProjects();old.leave();resolve(response(snapshot()));await pending;assert.equal(old.snapshot.value,null)
  const requests=[];const race=about(()=>new Promise(done=>requests.push(done)));const first=race.loadProjects();const second=race.loadProjects();requests[1](response({...snapshot(),events:[]}));await second;requests[0](response(snapshot()));await first;assert.equal(race.snapshot.value.events.length,0)
})
test('头像空值和失效回退，修改地址后允许新图片，Emoji昵称按字符取首位',async()=>{
  const p=reactive(profile()),scope=effectScope();let state
  const script=parse(code('src/components/ProfileAvatar.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  scope.run(()=>{state=new Function('ref','computed','watch','siteProfile',script+';return {failed,source,initial}')(ref,computed,watch,p)})
  assert.equal(state.source.value,'');assert.equal(state.initial.value,'f');state.failed.value=true;p.avatarUrl='/images/test.svg';await nextTick();assert.equal(state.failed.value,false)
  p.nickname='🌱记录';assert.equal(state.initial.value,'🌱');scope.stop()
})

// 同步脚本从实际源码导入，模块测试不执行CLI或真实网络。
const script=code('scripts/sync-github.mjs').replace(/^import \{ siteProfile \}[^\n]*\n/m,'const siteProfile = {githubName:"InInNHD"}\n').replace('if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1]))','if (false)')
const {collectGitHub}=await import('data:text/javascript;base64,'+Buffer.from(script).toString('base64'))
const repo=(id,name,extra={})=>({id,name,full_name:'InInNHD/'+name,private:false,owner:{login:'InInNHD'},description:name,language:'Java',fork:false,archived:false,created_at:'2026-01-01T00:00:00Z',pushed_at:'2026-10-19T01:00:00Z',...extra})
function github(){
  const calls=[]
  const commit=id=>({sha:id,html_url:'https://github.com/InInNHD/Alpha/commit/'+id,commit:{message:'change '+id+'\nprivate body not included',author:{date:'2026-01-01T00:00:00Z',email:'not-in-output@example.com'},committer:{date:'2026-10-01T00:00:00Z'}}})
  return {calls,request:async input=>{
    const url=new URL(input),path=url.pathname,page=url.searchParams.get('page');calls.push(url.href)
    if(path==='/users/InInNHD/repos')return page==='1'?response([repo(1,'Alpha'),repo(3,'secret',{private:true}),repo(9,'someone',{owner:{login:'Other'}})],200,true):response([repo(2,'Beta',{fork:true,archived:true})])
    if(path.endsWith('/events'))return response(path.includes('/Alpha/')?[{id:'123',public:true,type:'PushEvent',repo:{name:'InInNHD/Alpha'},created_at:'2026-10-19T02:00:00Z',payload:{ref:'refs/heads/main',head:'a'.repeat(40),before:'b'.repeat(40)}},{id:'secret',public:true,type:'PushEvent',repo:{name:'InInNHD/secret'},created_at:'2026-10-19T02:00:00Z',payload:{}}]:[])
    if(path.endsWith('/releases'))return response(path.includes('/Alpha/')?[{id:42,name:'',tag_name:'v1.0.0',published_at:'2026-10-20T00:00:00Z',html_url:'https://github.com/InInNHD/Alpha/releases/tag/v1.0.0',draft:false,prerelease:true},{id:43,draft:true,published_at:null}]:[])
    if(path.endsWith('/branches'))return response(path.includes('/Alpha/')?[{name:'main'},{name:'feature/x'}]:[])
    if(path.endsWith('/commits'))return url.searchParams.get('sha')==='feature/x'?response([commit('b'),commit('d')]):page==='1'?response([commit('a'),commit('b')],200,true):response([commit('c')])
    throw Error('Unexpected fixture request '+url.href)
  }}
}
test('同步遍历公开仓库、Release及分支提交分页，去重且不输出私有字段',async()=>{
  const api=github(),data=await collectGitHub('InInNHD',null,api.request)
  assert.equal(data.projects.length,2);assert.equal(data.projects.find(x=>x.name==='Beta').fork,true);assert.equal(data.projects.find(x=>x.name==='Beta').archived,true)
  assert.equal(data.events.filter(x=>x.kind==='COMMIT').length,4);assert.equal(data.events.filter(x=>x.kind==='PUSH').length,1);assert.equal(data.events.filter(x=>x.kind==='RELEASE').length,1)
  assert.equal(data.events[0].title,'v1.0.0');assert.equal(data.events[0].prerelease,true)
  assert.ok(api.calls.some(x=>x.includes('page=2')));assert.ok(api.calls.some(x=>x.includes('feature%2Fx')))
  assert.doesNotMatch(JSON.stringify(data),/not-in-output|private body|secret|Authorization/)
  assert.match(data.events.find(x=>x.kind==='PUSH').url,/\/compare\//)
})
test('同步累积旧公开推送、重复事件覆盖；移除不再公开仓库和旧发布/提交',async()=>{
  const previous={owner:'InInNHD',events:[{id:'push:old',kind:'PUSH',repo:'InInNHD/Alpha',date:'2025-01-01T00:00:00Z',title:'old push',url:'https://github.com/InInNHD/Alpha',email:'not-in-output@example.com'},{id:'push:123',kind:'PUSH',repo:'InInNHD/Alpha',date:'2026-10-19T02:00:00Z',title:'obsolete',url:'https://github.com/InInNHD/Alpha'},{id:'push:hidden',kind:'PUSH',repo:'InInNHD/secret',date:'2026-01-01T00:00:00Z'},{id:'release:deleted',kind:'RELEASE',repo:'InInNHD/Alpha',date:'2026-01-01T00:00:00Z'}]}
  const one=await collectGitHub('InInNHD',previous,github().request),two=await collectGitHub('InInNHD',one,github().request)
  assert.equal(one.events.filter(x=>x.kind==='PUSH').length,2);assert.equal(two.events.length,one.events.length);assert.doesNotMatch(JSON.stringify(two),/not-in-output|obsolete|deleted|hidden/)
  await assert.rejects(collectGitHub('bad_user',null,github().request),/用户名/)
  await assert.rejects(collectGitHub('InInNHD',null,async()=>response({},403)),/HTTP 403/)
})

test('fork提交在API端按本人过滤；原创分支不受限，公开fork项目和Release仍保留',async()=>{
  const api=github(),calls=[]
  const request=async input=>{
    const url=new URL(input);calls.push(url)
    if(url.pathname==='/repos/InInNHD/Beta/branches')return response([{name:'main'}])
    if(url.pathname==='/repos/InInNHD/Beta/commits'){
      assert.equal(url.searchParams.get('author'),'InInNHD')
      return response([{sha:'own-fork-commit',html_url:'https://github.com/InInNHD/Beta/commit/own-fork-commit',commit:{message:'自己的改动',author:{date:'2026-10-22T00:00:00Z'}}}])
    }
    if(url.pathname==='/repos/InInNHD/Beta/releases')return response([{id:99,tag_name:'v1',draft:false,published_at:'2026-10-23T00:00:00Z',html_url:'https://github.com/InInNHD/Beta/releases/tag/v1'}])
    return api.request(input)
  }
  const data=await collectGitHub('InInNHD',null,request)
  assert.equal(data.projects.find(x=>x.name==='Beta').fork,true)
  assert.ok(data.events.some(x=>x.id==='commit:2:own-fork-commit'))
  assert.ok(data.events.some(x=>x.id==='release:99'))
  assert.ok(calls.filter(x=>x.pathname==='/repos/InInNHD/Alpha/commits').every(x=>!x.searchParams.has('author')))
})
test('同步CLI请求失败时保留已有快照，不产生半份JSON',async()=>{
  const folder=new URL('../../artifacts/v2-profile-acceptance/cli-check/',import.meta.url)
  await mkdir(new URL('scripts/',folder),{recursive:true});await mkdir(new URL('src/',folder),{recursive:true});await mkdir(new URL('public/',folder),{recursive:true})
  const entry=new URL('scripts/sync-github.mjs',folder),destination=new URL('public/github-profile.json',folder)
  const previous=JSON.stringify({...snapshot(),events:[]})
  await writeFile(entry,code('scripts/sync-github.mjs'));await writeFile(new URL('src/siteProfile.js',folder),'export const siteProfile={githubName:"InInNHD"}');await writeFile(new URL('package.json',folder),'{"type":"module"}');await writeFile(destination,previous)
  const runner=`globalThis.fetch=async()=>new Response('{}',{status:503});process.argv[1]=${JSON.stringify(fileURLToPath(entry))};await import(${JSON.stringify(pathToFileURL(fileURLToPath(entry)).href)});`
  await assert.rejects(promisify(execFile)(process.execPath,['--input-type=module','-e',runner]),error=>/HTTP 503/.test(error.stderr))
  assert.equal(await readFile(destination,'utf8'),previous)
})
