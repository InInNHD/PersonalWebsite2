import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
const require = createRequire(new URL('../package.json',import.meta.url))
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const {ref,computed,reactive,watch,nextTick,effectScope}=require('vue')
function code(file){return readFileSync(new URL(`../src/${file}`,import.meta.url),'utf8').replace(/\r\n/g,'\n')}
function navigation(){return new Function('nextTick',code('listNavigation.js').replace(/^import[^\n]*\n/gm,'').replaceAll('export ','')+';return {topicQuery,listQuery,listReturn,listKey,prepareList,finishList,listScroll,rememberList}')(nextTick)}
const response=(data,status=200)=>new Response(JSON.stringify(data),{status})
const directory={categories:[{id:'1',name:'Java',total:12}],tags:[{id:'2',name:'Vue',total:3}]}
const page=(id=1,p=1,total=12)=>({items:[{id}],page:p,size:10,total,totalPages:Math.ceil(total/10)})
const settle=async()=>{for(let i=0;i<14;i++)await nextTick()}
function deferred(){let resolve;const promise=new Promise(done=>{resolve=done});return {promise,resolve}}
function app(query,fetch){
  const nav=navigation(),scope=effectScope(),mounted=[],leaves=[],finished=[]
  const route=reactive({path:'/topics',query,hash:'',fullPath:'/topics',matched:[{}]})
  const update=async target=>{const old={...route};const current={path:'/topics',query:nav.topicQuery(target.query),hash:target.hash??'',fullPath:'/topics?'+new URLSearchParams(nav.topicQuery(target.query))+(target.hash??'')};nav.prepareList(current,old);Object.assign(route,current);await settle()}
  const router={push:update,replace:update}
  let state
  const bindings={ref,computed,watch,useRoute:()=>route,useRouter:()=>router,onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>leaves.push(fn),onBeforeRouteLeave:()=>{},window:{scrollX:0,scrollY:0},fetch,...nav,finishList:async key=>{finished.push({key,directoryVisible:state? !state.directoryLoading.value:false});await nav.finishList(key)}}
  const script=parse(code('views/TopicView.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  scope.run(()=>{state=new Function(...Object.keys(bindings),script+';return {directory,directoryLoading,directoryError,articles,loading,error,selected,page,total,totalPages,loadDirectory,loadArticles,changePage,topicLocation}')(...Object.values(bindings))})
  return {...state,route,router,nav,finished,mount:()=>mounted[0](),leave:()=>{leaves.forEach(fn=>fn());scope.stop()}}
}

test('目录页和卡片可编译；参数成对校验，编号保持字符串，首页/主题键隔离',()=>{
  for(const file of ['views/TopicView.vue','components/ArticleCard.vue']){const {descriptor,errors}=parse(code(file));assert.deepEqual(errors,[]);const script=compileScript(descriptor,{id:file});assert.deepEqual(compileTemplate({source:descriptor.template.content,id:file,filename:file,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])}
  const n=navigation()
  assert.deepEqual(n.topicQuery({kind:['tag','category'],id:'9223372036854775807',page:'2',extra:'x'}),{kind:'tag',id:'9223372036854775807',page:'2'})
  for(const value of [{kind:'x',id:'1'},{kind:'tag',id:'0'},{kind:'category',id:'9223372036854775808'},{page:'2'}])assert.deepEqual(n.topicQuery(value),{})
  assert.notEqual(n.listKey({path:'/',query:{}}),n.listKey({path:'/topics',query:{}}))
  assert.equal(n.listReturn('/topics?kind=tag&id=2&page=2#entries').path,'/topics')
  for(const url of ['//evil.invalid/topics','/admin','/topics/../../admin','https://evil.invalid/topics'])assert.deepEqual(n.listReturn(url),{path:'/'})
})

test('目录首页不请求全站文章；直达第二页等待目录与列表后释放滚动',async()=>{
  const urls=[],state=app({kind:'category',id:'1',page:'2'},async url=>{urls.push(url);return response(url==='/api/topics'?directory:page(7,2))})
  try{await state.mount();assert.equal(urls.length,2);assert.match(urls[1],/categoryId=1/);assert.match(urls[1],/page=2/);assert.equal(state.page.value,2);assert.equal(state.articles.value[0].id,7);assert.equal(state.directoryLoading.value,false);assert.ok(state.finished.every(item=>item.directoryVisible))}finally{state.leave()}
  const index=app({},async url=>{assert.equal(url,'/api/topics');return response(directory)})
  try{await index.mount();assert.equal(index.articles.value.length,0);assert.equal(index.selected.value,null)}finally{index.leave()}
})

test('分类切换标签，旧文章响应和旧页码不能覆盖当前主题',async()=>{
  const pending=[],state=app({kind:'category',id:'1'},url=>url==='/api/topics'?Promise.resolve(response(directory)):(()=>{const task=deferred();pending.push(task);return task.promise})())
  try{
    const first=state.mount();await settle();assert.equal(pending.length,1)
    await state.router.push({query:{kind:'tag',id:'2'}});assert.equal(pending.length,2)
    pending[1].resolve(response(page(22,1,3)));await settle()
    pending[0].resolve(response(page(11,2)));await first;await settle()
    assert.equal(state.selected.value.name,'Vue');assert.equal(state.articles.value[0].id,22);assert.equal(state.page.value,1)
  }finally{state.leave()}
})

test('未知主题不查询全量维护接口；目录失败可重试且不挂起路由等待',async()=>{
  const urls=[],state=app({kind:'category',id:'999'},async url=>{urls.push(url);return response(directory)})
  try{await state.mount();assert.equal(state.selected.value,null);assert.deepEqual(urls,['/api/topics'])}finally{state.leave()}
  let failure=true
  const retry=app({kind:'category',id:'1'},async url=>response(url==='/api/topics'?directory:page(),failure?503:200))
  try{await retry.mount();assert.match(retry.directoryError.value,/503/);await retry.router.push({query:{kind:'tag',id:'2'}});assert.ok(retry.finished.length>=2);failure=false;await retry.loadDirectory();assert.equal(retry.directoryError.value,'');assert.equal(retry.articles.value.length,1)}finally{retry.leave()}
})

test('文章失败保持目录可操作，重试恢复；过大页码同步纠正URL',async()=>{
  let failure=true
  const state=app({kind:'category',id:'1',page:'999'},async url=>url==='/api/topics'?response(directory):response(page(7,2),failure?503:200))
  try{await state.mount();assert.match(state.error.value,/503/);assert.equal(state.directory.value.categories.length,1);failure=false;await state.loadArticles();await settle();assert.equal(state.route.query.page,'2');assert.equal(state.articles.value[0].id,7);assert.equal(state.loading.value,false)}finally{state.leave()}
})

test('主题列表恢复独立位置，前后篇保留主题来源，非法来源仍拒绝',async()=>{
  const n=navigation(),to={path:'/topics',query:{kind:'tag',id:'2',page:'2'},fullPath:'/topics?kind=tag&id=2&page=2',hash:''},from={path:'/articles/1',query:{from:'/topics?kind=tag&id=2&page=2'}}
  n.rememberList(to,{left:0,top:1200});assert.deepEqual(await n.listScroll(to,from,null,()=>to),{left:0,top:1200})
  const index={path:'/topics',query:{},hash:'',fullPath:'/topics'}
  assert.deepEqual(await n.listScroll(index,to,null,()=>index),{top:0})
  const source=code('components/ArticleNeighbors.vue')
  const script=parse(source).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  const state=new Function('computed','ref','watch','onBeforeUnmount','defineProps','listReturn',script+';return {returnPath}')(computed,ref,()=>{},()=>{},()=>({articleId:1,from:'/topics?kind=tag&id=2&page=2'}),n.listReturn)
  assert.equal(state.returnPath.value,'/topics?kind=tag&id=2&page=2')
})

test('卸载时目录与文章旧响应不写回，hash变化不重复查询',async()=>{
  const task=deferred(),state=app({},()=>task.promise)
  const pending=state.mount();state.leave();task.resolve(response(directory));await pending
  assert.equal(state.directory.value,null)
  const urls=[],active=app({kind:'tag',id:'2'},async url=>{urls.push(url);return response(url==='/api/topics'?directory:page())})
  try{await active.mount();const count=urls.length;await active.router.push({query:{kind:'tag',id:'2'},hash:'#entries'});assert.equal(urls.length,count)}finally{active.leave()}
})
