import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
const require = createRequire(new URL('../package.json',import.meta.url))
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const {ref,computed,watch,nextTick,effectScope}=require('vue')
function code(file){return readFileSync(new URL(`../src/${file}`,import.meta.url),'utf8').replace(/\r\n/g,'\n')}
function navigation(){return new Function('nextTick',code('listNavigation.js').replace(/^import[^\n]*\n/gm,'').replaceAll('export ','')+';return {archiveQuery,listQuery,topicQuery,pageQuery,pageHash,listReturn,listKey,prepareList,finishList,listScroll,rememberList}')(nextTick)}
const response=(data,status=200)=>new Response(JSON.stringify(data),{status})
const months=[{month:'2040-03',total:12},{month:'2040-02',total:2},{month:'2039-12',total:1}]
const page=(id=1,p=1,total=12)=>({items:[{id}],page:p,size:10,total,totalPages:Math.ceil(total/10)})
const settle=async()=>{for(let i=0;i<16;i++)await nextTick()}
function deferred(){let resolve;const promise=new Promise(done=>{resolve=done});return {promise,resolve}}
function app(path,query,fetch){
  const nav=navigation(),scope=effectScope(),mounted=[],leaves=[],finished=[]
  const current=ref({path,query,hash:'',fullPath:path+'?'+new URLSearchParams(query),matched:[{}]})
  const route={get path(){return current.value.path},get query(){return current.value.query},get hash(){return current.value.hash},get fullPath(){return current.value.fullPath},get matched(){return current.value.matched}}
  const update=async target=>{const old={...route};const path=target.path??route.path,query=nav.archiveQuery(target.query),hash=target.hash??'';const next={path,query,hash,fullPath:path+'?'+new URLSearchParams(query)+hash,matched:[{}]};nav.prepareList(next,old);current.value=next;await settle()}
  let state
  const router={push:update,replace:update}
  const bindings={usePageMeta:()=>{},ref,computed,watch,useRoute:()=>route,useRouter:()=>router,onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>leaves.push(fn),onBeforeRouteLeave:()=>{},onBeforeRouteUpdate:()=>{},window:{scrollX:0,scrollY:0},fetch,...nav,finishList:async key=>{finished.push({key,directoryVisible:state?!state.directoryLoading.value:false});await nav.finishList(key)}}
  const script=parse(code('views/ArchiveView.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  scope.run(()=>{state=new Function(...Object.keys(bindings),script+';return {diary,months,selectedMonth,selected,yearGroups,publicTotal,directoryLoading,directoryError,articles,loading,error,page,total,totalPages,loadArchive,loadArticles,changePage,monthLocation,articleLocation}')(...Object.values(bindings))})
  return {...state,route,router,nav,finished,mount:()=>mounted[0](),leave:()=>{leaves.forEach(fn=>fn());scope.stop()}}
}

test('归档/日记组件可编译，月份参数、白名单及四页位置键隔离',()=>{
  for(const file of ['views/ArchiveView.vue','components/SiteHeader.vue']){const {descriptor,errors}=parse(code(file));assert.deepEqual(errors,[]);const script=compileScript(descriptor,{id:file});assert.deepEqual(compileTemplate({source:descriptor.template.content,id:file,filename:file,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])}
  const n=navigation()
  assert.deepEqual(n.archiveQuery({month:['2040-03','2039-12'],page:'2',type:'NOTE',extra:'x'}),{month:'2040-03',page:'2'})
  for(const month of ['0000-01','2040-13','2040-1','2040-00','10000-01',''])assert.deepEqual(n.archiveQuery({month,page:'2'}),{})
  assert.deepEqual(n.archiveQuery({month:'9999-12',page:'1000001'}),{month:'9999-12'})
  const keys=['/','/topics','/archive','/diary'].map(path=>n.listKey({path,query:{}}));assert.equal(new Set(keys).size,4)
  assert.deepEqual(n.listReturn('/diary?month=2040-03&page=2&type=NOTE#entries'),{path:'/diary',query:{month:'2040-03',page:'2'},hash:'#entries'})
  for(const url of ['//evil.invalid/diary','/admin','/archive/../admin','https://evil.invalid/diary'])assert.deepEqual(n.listReturn(url),{path:'/'})
  assert.equal(n.pageHash('/diary','#article-search'),'')
})

test('直达第二页、跨年分组、切月回第一页、末页纠正与来源链接',async()=>{
  const urls=[],s=app('/archive',{month:'2040-03',page:'2'},async url=>{urls.push(url);if(url==='/api/articles/archive')return response(months);const q=new URL(url,'http://localhost').searchParams;return response(page(7,Math.min(Number(q.get('page')),2),q.get('month')==='2040-03'?12:2))})
  try{
    await s.mount();assert.equal(s.page.value,2);assert.equal(s.publicTotal.value,15);assert.deepEqual(s.yearGroups.value.map(g=>[g.year,g.total]),[['2040',14],['2039',1]]);assert.ok(s.finished.every(x=>x.directoryVisible));assert.match(urls[1],/month=2040-03/)
    assert.equal(s.articleLocation({id:7}).query.from,s.route.fullPath)
    await s.router.push(s.monthLocation('2040-02'));assert.equal(s.page.value,1);assert.equal(s.total.value,2)
    await s.router.push({query:{month:'2040-03',page:'999'}});await settle();assert.equal(s.route.query.page,'2');assert.equal(s.page.value,2)
    const before=urls.length;await s.changePage(0);await s.changePage(3);assert.equal(urls.length,before)
    await s.router.push({query:{month:'2040-03',page:'2'},hash:'#entries'});assert.equal(urls.length,before)
  }finally{s.leave()}
})

test('日记目录与列表始终带DIARY，归档与日记切换重新取得各自目录',async()=>{
  const urls=[],s=app('/diary',{},async url=>{urls.push(url);return response(url.startsWith('/api/articles/archive')?months:page())})
  try{
    await s.mount();await settle();assert.equal(s.route.query.month,'2040-03');assert.equal(s.diary.value,true);assert.equal(urls[0],'/api/articles/archive?type=DIARY');assert.equal(new URL(urls[1],'http://localhost').searchParams.get('type'),'DIARY')
    await s.router.push({path:'/archive',query:{month:'2040-03'}});await settle();assert.equal(s.diary.value,false);assert.ok(urls.includes('/api/articles/archive'));assert.equal(new URL(urls.at(-1),'http://localhost').searchParams.has('type'),false)
    await s.router.push({path:'/diary',query:{month:'2039-12'}});await settle();assert.equal(new URL(urls.at(-1),'http://localhost').searchParams.get('type'),'DIARY')
  }finally{s.leave()}
})

test('空目录和草稿专用合法月份不请求文章；旧月份响应失效',async()=>{
  const calls=[],empty=app('/diary',{},async url=>{calls.push(url);return response([])})
  try{await empty.mount();assert.deepEqual(calls,['/api/articles/archive?type=DIARY']);assert.equal(empty.publicTotal.value,0);assert.equal(empty.loading.value,false)}finally{empty.leave()}
  const urls=[],unknown=app('/diary',{month:'2041-01'},async url=>{urls.push(url);return response(months)})
  try{await unknown.mount();assert.equal(unknown.selected.value,undefined);assert.equal(urls.length,1);assert.equal(unknown.articles.value.length,0)}finally{unknown.leave()}
  const pending=[],s=app('/archive',{month:'2040-03'},url=>url.includes('/archive')?Promise.resolve(response(months)):(()=>{const task=deferred();pending.push(task);return task.promise})())
  try{const first=s.mount();await settle();await s.router.push(s.monthLocation('2040-02'));pending[1].resolve(response(page(22,1,2)));await settle();pending[0].resolve(response(page(11)));await first;assert.equal(s.articles.value[0].id,22);assert.equal(s.total.value,2)}finally{s.leave()}
})

test('目录与记录独立重试，失败后切月也释放滚动等待',async()=>{
  let failDirectory=true,failList=false
  const s=app('/diary',{month:'2040-03',page:'2'},async url=>url.includes('/archive')?response(months,failDirectory?503:200):response(page(2,2),failList?503:200))
  try{
    await s.mount();assert.match(s.directoryError.value,/503/);assert.equal(s.loading.value,false)
    await s.router.push(s.monthLocation('2040-02'));assert.equal(s.finished.at(-1).key,s.nav.listKey(s.route))
    failDirectory=false;await s.router.push(s.monthLocation('2040-03',2));await s.loadArchive();assert.equal(s.directoryError.value,'');assert.equal(s.page.value,2)
    failList=true;await s.loadArticles();assert.match(s.error.value,/503/);assert.equal(s.directoryError.value,'');assert.equal(s.months.value.length,3);assert.equal(s.articles.value.length,0)
    failList=false;await s.loadArticles();assert.equal(s.error.value,'');assert.equal(s.articles.value[0].id,2)
  }finally{s.leave()}
})

test('跨归档/日记旧目录和离页后旧请求不回写',async()=>{
  const pending=[],s=app('/archive',{},url=>{const task=deferred();pending.push({url,...task});return task.promise})
  try{const first=s.mount();await s.router.push({path:'/diary',query:{}});pending[1].resolve(response([]));await settle();pending[0].resolve(response(months));await first;assert.equal(s.months.value.length,0);assert.equal(s.publicTotal.value,0);const old=s.loadArchive();s.leave();pending[2].resolve(response(months));await old;assert.equal(s.months.value,null)}finally{s.leave()}
})

test('归档和日记来源保持月份页码，渲染后精确恢复位置且旧任务不滚动',async()=>{
  const n=navigation()
  for(const path of ['/archive','/diary']){
    const origin={path,query:{month:'2040-03',page:'2'},hash:'',fullPath:path+'?month=2040-03&page=2',matched:[{}]}
    const detail={path:'/articles/7',query:{from:origin.fullPath},fullPath:'/articles/7'}
    n.rememberList(origin,{top:987,left:0});n.prepareList(origin,detail)
    let done=false;const result=n.listScroll(origin,detail,null,()=>origin).then(x=>{done=true;return x});await settle();assert.equal(done,false);await n.finishList(n.listKey(origin));assert.deepEqual(await result,{top:987,left:0})
    n.prepareList(origin,detail);const old=n.listScroll(origin,detail,null,()=>({fullPath:'/about'}));await n.finishList(n.listKey(origin));assert.equal(await old,false)
  }
})
