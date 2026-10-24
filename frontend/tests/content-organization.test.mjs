import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(new URL('../package.json', import.meta.url))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { ref, computed, watch, reactive, effectScope, nextTick } = require('vue')
const source = path => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8').replace(/\r\n/g, '\n')
const plain = text => text.replace(/^import[^\n]*\n/gm, '').replaceAll('export ', '')
const nav = new Function('nextTick', plain(source('listNavigation.js')) + ';return {legacyListLocation,listReturn,listKey,finishList,rememberList}')(nextTick)
const response = (data, status = 200) => new Response(JSON.stringify(data), {status})
const settle = async () => { for (let i=0;i<16;i++) await nextTick() }
function deferred() { let resolve; const promise = new Promise(done => {resolve=done}); return {promise,resolve} }
function context(fetch) {
  const leaves=[], mounted=[], scope=effectScope()
  const bindings={ref,computed,watch,fetch,onBeforeUnmount:fn=>leaves.push(fn),onMounted:fn=>mounted.push(fn),onBeforeRouteLeave:()=>{},usePageMeta:()=>{},...nav}
  bindings.usePresentation = new Function(...Object.keys(bindings), plain(source('usePresentation.js'))+';return usePresentation')(...Object.values(bindings))
  function component(file, names, extra={}) {
    const env={...bindings,...extra}; let state
    scope.run(()=>{state=new Function(...Object.keys(env),plain(parse(source(file)).descriptor.scriptSetup.content)+`;return {${names}}`)(...Object.values(env))})
    return state
  }
  return {bindings,component,mounted,leave:()=>{leaves.forEach(fn=>fn());scope.stop()}}
}

test('第三版新增实际组件全部可编译',()=>{
  for(const file of ['views/HomeView.vue','views/CollectionView.vue','components/CollectionTrail.vue','components/LearningPaths.vue']) {
    const {descriptor,errors}=parse(source(file)); assert.deepEqual(errors,[])
    const script=compileScript(descriptor,{id:file})
    assert.deepEqual(compileTemplate({id:file,filename:file,source:descriptor.template.content,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  }
})
test('旧首页参数和锚点迁移，裸首页不迁移；首页、文章库及各专题位置互相隔离',()=>{
  assert.equal(nav.legacyListLocation({path:'/',query:{},hash:''}),undefined)
  const target=nav.legacyListLocation({path:'/',query:{type:'NOTE',page:'2',categoryId:'9',tagId:'3',keyword:' Java ',bad:'x'},hash:'#article-search'})
  assert.deepEqual(target,{path:'/articles',query:{type:'NOTE',categoryId:'9',tagId:'3',keyword:'Java',page:'2'},hash:'#article-search',replace:true})
  assert.equal(nav.legacyListLocation({path:'/',query:{},hash:'#entries'}).path,'/articles')
  assert.equal(nav.legacyListLocation({path:'/articles',query:{page:'2'}}),undefined)
  assert.deepEqual(nav.listReturn('/?page=2#entries'),{path:'/articles',query:{page:'2'},hash:'#entries'})
  assert.deepEqual(nav.listReturn('/collections/java-path?bad=x#entries'),{path:'/collections/java-path',query:{},hash:'#entries'})
  assert.equal(nav.listReturn('/projects').path,'/projects')
  for (const url of ['/collections/a/b','/collections/%2fadmin','//evil.invalid']) assert.equal(nav.listReturn(url).path,'/')
  assert.equal(new Set(['/', '/articles', '/collections/a', '/collections/b'].map(path=>nav.listKey({path,query:{}}))).size,4)
})
test('公开编排失败可重试，旧响应和卸载后的结果不能覆盖',async()=>{
  const queue=[],c=context(()=>{const d=deferred();queue.push(d);return d.promise}),app=c.bindings.usePresentation()
  const first=app.loadPresentation(),second=app.loadPresentation()
  queue[1].resolve(response({featured:[{id:2}],collections:[]}));await second
  queue[0].resolve(response({featured:[{id:1}],collections:[]}));await first
  assert.equal(app.presentation.value.featured[0].id,2)
  const fail=app.loadPresentation();queue[2].resolve(response({},503));await fail
  assert.ok(app.presentationError.value);assert.equal(app.presentation.value,null)
  const retry=app.loadPresentation();queue[3].resolve(response({featured:[],collections:[]}));await retry
  assert.equal(app.presentationError.value,'')
  const last=app.loadPresentation();c.leave();queue[4].resolve(response({featured:[{id:9}]}));await last
  assert.deepEqual(app.presentation.value.featured,[])
})
test('真实首页精选优先；没有编排使用公开记录，接口失败独立重试且等待完成再恢复位置',async()=>{
  let curated={featured:[],collections:[]}, fail=false
  const calls=[],finished=[]
  const c=context(async url=>{calls.push(url);return url==='/api/presentation'?response(curated):response({items:[{id:1},{id:2}]},fail?503:200)})
  const app=c.component('views/HomeView.vue','articles,lead,featured,error,loading,loadRecent,loadPresentation', {useRoute:()=>({path:'/',query:{}}),finishList:async key=>finished.push(key)})
  await c.mounted[0]();assert.deepEqual(finished,['/?']);assert.equal(app.lead.value[0].id,1)
  curated={featured:[{id:9}],collections:[]};await app.loadPresentation();assert.equal(app.lead.value[0].id,9)
  fail=true;await app.loadRecent();assert.ok(app.error.value);assert.equal(app.lead.value[0].id,9)
  fail=false;await app.loadRecent();assert.equal(app.error.value,'');assert.equal(app.articles.value.length,2)
  c.leave()
})
test('专题切换的旧404不覆盖新内容，缺失专题与网络失败区分且都释放等待',async()=>{
  const queue=[],route=reactive({path:'/collections/first',params:{slug:'first'},query:{}}),finished=[]
  const c=context(()=>{const d=deferred();queue.push(d);return d.promise})
  const app=c.component('views/CollectionView.vue','collection,missing,error,loading,load',{useRoute:()=>route,finishList:async key=>finished.push(key)})
  route.path='/collections/second';route.params.slug='second';await settle()
  queue[1].resolve(response({slug:'second',articles:[{id:2},{id:1}]}));await settle()
  queue[0].resolve(response({},404));await settle()
  assert.equal(app.collection.value.slug,'second');assert.equal(app.missing.value,false)
  const missing=app.load();queue[2].resolve(response({},404));await missing;assert.equal(app.missing.value,true);assert.equal(app.error.value,'')
  const failure=app.load();queue[3].resolve(response({},503));await failure;assert.ok(app.error.value);assert.equal(app.missing.value,false);assert.ok(finished.length>=3)
  c.leave()
})
test('专题前后篇按公开顺序导航，边界、已移除文章和无效来源不产生错误链接',async()=>{
  const props=reactive({articleId:2,from:'/collections/java'}),data={slug:'java',title:'Java',articles:[{id:2},{id:1},{id:4}]}
  let calls=0
  const c=context(async()=>{calls++;return response(data)})
  const app=c.component('components/CollectionTrail.vue','previous,next,index,collection,origin',{defineProps:()=>props})
  await settle();assert.equal(app.previous.value,null);assert.equal(app.next.value.id,1)
  props.articleId=4;await settle();assert.equal(app.previous.value.id,1);assert.equal(app.next.value,undefined)
  props.articleId=3;await settle();assert.equal(app.index.value,-1);assert.equal(app.next.value,null)
  const before=calls;props.from='//evil.invalid';await settle();assert.equal(calls,before);assert.equal(app.collection.value,null)
  c.leave()
})
