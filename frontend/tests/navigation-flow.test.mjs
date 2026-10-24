import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
const require = createRequire(new URL('../package.json', import.meta.url))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { ref, computed, reactive, watch, nextTick, effectScope } = require('vue')
// 读取实际业务源码；只替换网络、生命周期和路由环境。
function code(file) {
  return readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8').replace(/\r\n/g,'\n')
}
function navigation() {
  const source=code('listNavigation.js').replace(/^import[^\n]*\n/gm,'').replaceAll('export ','')
  return new Function('nextTick',`${source};return {listQuery,listKey,listHash,listReturn,prepareList,finishList,listScroll,rememberList}`)(nextTick)
}
const response=(items,page=1,total=items.length)=>new Response(JSON.stringify({items,page,total,size:10,totalPages:Math.ceil(total/10)}))
const settle=async()=>{for(let i=0;i<12;i++) await nextTick()}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject}}
function list(initialQuery,fetch){
  const nav=navigation(), cleanup=[], history=[]
  const route=reactive({path:'/articles',query:initialQuery,hash:'',fullPath:'/articles',matched:[{}]})
  let app
  const update=async(target,replace)=>{
    const query=nav.listQuery(target.query)
    const next={path:'/articles',query,hash:target.hash??'',fullPath:'/articles?'+new URLSearchParams(query)+(target.hash??'')}
    const previous={...route}
    nav.prepareList(next,previous)
    Object.assign(route,next)
    history.push({replace,...next})
    await settle()
  }
  const router={push:t=>update(t,false),replace:t=>update(t,true)}
  const script=parse(code('views/ArticleListView.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  const scope=effectScope()
  const bindings={compactArticles:ref(false),ref,computed,watch,onBeforeUnmount:f=>cleanup.push(f),onMounted:()=>{},onBeforeRouteLeave:()=>{},useRoute:()=>route,useRouter:()=>router,fetch,window:{scrollX:0,scrollY:100},...nav}
  scope.run(()=>{app=new Function(...Object.keys(bindings),`${script};return {type,categoryId,tagId,keyword,keywordInput,page,totalPages,articles,error,loading,selectType,selectCategory,searchArticles,clearSearch,clearAll,changePage,loadArticles}`)(...Object.values(bindings))})
  return {...app,route,router,history,leave:()=>{cleanup.forEach(fn=>fn());scope.stop()}}
}

test('网址参数规范化并保留Long字符串，站外返回地址回退',()=>{
  const n=navigation()
  assert.deepEqual(n.listQuery({type:['NOTE','DIARY'],categoryId:'9223372036854775807',tagId:'001',keyword:'  Vue  ',page:'2',extra:'x'}),{type:'NOTE',categoryId:'9223372036854775807',keyword:'Vue',page:'2'})
  assert.deepEqual(n.listQuery({type:'BAD',categoryId:'9223372036854775808',tagId:'-1',page:'1000001'}),{})
  assert.deepEqual(n.listQuery({page:'1',keyword:' '}),{})
  assert.equal(n.listQuery({keyword:'字'.repeat(101)}).keyword.length,100)
  for(const bad of ['https://example.com','//example.com','/\\example.com','/admin','/articles/1',null,['/?page=2']]) assert.deepEqual(n.listReturn(bad),{path:'/'})
  assert.deepEqual(n.listReturn('/?type=NOTE&page=2&unknown=1#entries'),{path:'/articles',query:{type:'NOTE',page:'2'},hash:'#entries'})
})

test('实际四个Vue组件可编译',()=>{
  for(const file of ['views/ArticleListView.vue','components/ArticleCard.vue','views/ArticleDetailView.vue','components/SiteHeader.vue']){
    const {descriptor,errors}=parse(code(file));assert.deepEqual(errors,[])
    const script=compileScript(descriptor,{id:file})
    assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:file,id:file,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  }
})

test('直接链接恢复全部条件，翻页保留已提交词，筛选回第一页，重置清空',async()=>{
  const urls=[]
  const app=list({type:'NOTE',categoryId:'1',tagId:'2',keyword:'Vue',page:'2'},async url=>{urls.push(url);const p=Number(new URL(url,'http://local').searchParams.get('page'));return response([{id:p}],p,35)})
  try{
    await settle();assert.equal(app.page.value,2);assert.equal(app.keywordInput.value,'Vue');assert.equal(urls.length,1)
    app.keywordInput.value='尚未提交'
    await app.changePage(3)
    assert.equal(app.route.query.page,'3');assert.equal(app.route.query.keyword,'Vue');assert.equal(urls.length,2)
    await app.selectCategory(9)
    assert.equal(app.route.query.page,undefined);assert.equal(app.route.query.categoryId,'9');assert.equal(app.page.value,1)
    app.keywordInput.value=' Java '
    await app.searchArticles();assert.equal(app.route.query.keyword,'Java')
    await app.clearSearch();assert.equal(app.route.query.keyword,undefined);assert.equal(app.route.query.categoryId,'9')
    await app.clearAll();assert.deepEqual({...app.route.query},{})
    // 模拟浏览器后退恢复旧查询，仍通过真实watch触发加载。
    await app.router.push({query:{type:'DIARY',keyword:'旧条件',page:'2'}})
    assert.equal(app.type.value,'DIARY');assert.equal(app.keywordInput.value,'旧条件');assert.equal(app.page.value,2)
  }finally{app.leave()}
})

test('超出末页通过replace纠正网址，锚点变化不重复请求',async()=>{
  let calls=0
  const app=list({page:'999999'},async()=>{calls++;return response([{id:1}],2,12)})
  try{
    await settle();assert.equal(app.route.query.page,'2');assert.equal(app.page.value,2)
    assert.ok(app.history[0].replace);assert.equal(calls,2)
    await app.router.push({query:app.route.query,hash:'#article-search'})
    assert.equal(calls,2)
  }finally{app.leave()}
})

test('切换网址后旧响应不能覆盖新列表，失败可重试且卸载失效',async()=>{
  const queue=[]
  const app=list({type:'NOTE'},()=>{const p=deferred();queue.push(p);return p.promise})
  try{
    await app.selectType('DIARY')
    queue[1].resolve(response([{id:2}]));await settle()
    queue[0].resolve(response([{id:1}]));await settle()
    assert.equal(app.articles.value[0].id,2)
    const retry=app.loadArticles();queue[2].reject(Error('offline'));await retry
    assert.equal(app.error.value,'offline');assert.deepEqual(app.articles.value,[]);assert.equal(app.loading.value,false)
    const recover=app.loadArticles();queue[3].resolve(response([{id:3}]));await recover
    assert.equal(app.error.value,'');assert.equal(app.articles.value[0].id,3)
    const leaving=app.loadArticles();app.leave();queue[4].resolve(response([{id:4}]));await leaving
    assert.equal(app.articles.value[0].id,3)
  }finally{app.leave()}
})

test('滚动等待列表完成，优先浏览器保存位置，正文返回恢复记录',async()=>{
  const n=navigation(),to={path:'/articles',query:{page:'2'},hash:'#entries',fullPath:'/articles?page=2#entries'}
  const from={path:'/articles/1',query:{from:'/?page=2#entries'}}
  n.rememberList(to,{left:0,top:1234});n.prepareList(to,from)
  let completed=false
  const waiting=n.listScroll(to,from,{left:0,top:999},()=>to).then(value=>{completed=true;return value})
  await settle();assert.equal(completed,false)
  await n.finishList(n.listKey(to));assert.deepEqual(await waiting,{left:0,top:999})
  assert.deepEqual(await n.listScroll(to,from,null,()=>to),{left:0,top:1234})
  const fresh={path:'/articles',query:{page:'3'},hash:'',fullPath:'/articles?page=3'}
  n.prepareList(fresh,from);await n.finishList(n.listKey(fresh))
  assert.deepEqual(await n.listScroll(fresh,{path:'/articles/1',query:{from:'/?page=3'}},null,()=>fresh),{el:'#entries',top:24})
})

test('首次加载必须等待；离开页面释放等待且旧滚动不影响新页面',async()=>{
  const n=navigation(),to={path:'/articles',query:{},hash:'#entries',fullPath:'/#entries'}
  n.prepareList(to,{path:'/articles',query:{},matched:[]})
  let complete=false,current=to
  const waiting=n.listScroll(to,{path:'/articles',query:{}},null,()=>current).then(value=>{complete=true;return value})
  await settle();assert.equal(complete,false)
  current={path:'/about',fullPath:'/about'};n.prepareList(current,to)
  assert.equal(await waiting,false)
})

test('重复提交相同条件允许重试，翻页边界与加载期间不新增请求',async()=>{
  let calls=0
  const app=list({keyword:'Vue'},async url=>{calls++;const page=Number(new URL(url,'http://local').searchParams.get('page'));return response([{id:calls}],page,15)})
  try{
    await settle();assert.equal(calls,1)
    app.keywordInput.value=' Vue '
    await app.searchArticles();assert.equal(calls,2);assert.equal(app.keyword.value,'Vue')
    await app.changePage(0);await app.changePage(3);assert.equal(calls,2)
    app.loading.value=true;await app.changePage(2);assert.equal(calls,2)
    app.loading.value=false;await app.changePage(2);assert.equal(calls,3);assert.equal(app.route.query.page,'2')
  }finally{app.leave()}
})
