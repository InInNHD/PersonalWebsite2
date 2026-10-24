import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
const require=createRequire(new URL('../package.json',import.meta.url))
const {parse,compileScript,compileTemplate}=require('@vue/compiler-sfc')
const {ref,computed,nextTick}=require('vue')
// 读取实际业务源码，只替换网络、路由与浏览器生命周期环境。
function code(file){
  return readFileSync(new URL(`../src/${file}`,import.meta.url),'utf8').replace(/\r\n/g,'\n')
}
const { renderArticle } = await import('../src/articleReading.js')
function navigation(document={getElementById:()=>null}){
  return new Function('nextTick','document',code('readingNavigation.js').replace(/^import[^\n]*\n/gm,'').replaceAll('export ','')+';return {prepareReading,finishReading,readingScroll}')(nextTick,document)
}
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject}}
const response=(data,status=200)=>new Response(JSON.stringify(data),{status})
function detail(fetch,headings={},toolsHeight=0){
  const mounted=[],leaves=[],finished=[],events=[],frames=new Map()
  let serial=0
  const route={path:'/articles/1',params:{id:'1'},query:{from:'/?page=2'}}
  const window={innerHeight:900,scrollY:0,addEventListener:(type)=>events.push('add:'+type),removeEventListener:(type)=>events.push('remove:'+type),requestAnimationFrame:fn=>{frames.set(++serial,fn);return serial},cancelAnimationFrame:id=>frames.delete(id),matchMedia:()=>({matches:true}),scrollTo:options=>events.push(options)}
  const bindings={usePageMeta:()=>{},ref,computed,nextTick,onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>leaves.push(fn),watch:()=>{},useRoute:()=>route,listReturn:()=>({path:'/'}),finishReading:async path=>{finished.push(path)},renderArticle,siteProfile:{nickname:'tester'},fetch,window,document:{getElementById:id=>headings[id]??null,querySelector:()=>({getBoundingClientRect:()=>({height:toolsHeight})})}}
  const script=parse(code('views/ArticleDetailView.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm,'')
  const app=new Function(...Object.keys(bindings),`${script};return {loadArticle,article,contentHtml,error,notFound,loading,body,progress,activeId,showTop,rendered,updateReading,scheduleReading,backToTop,changeReading,readingSize,focusedReading}`)(...Object.values(bindings))
  return {...app,window,route,finished,events,frames,mount:()=>mounted.forEach(fn=>fn()),leave:()=>leaves.forEach(fn=>fn())}
}

test('阅读组件可编译，目录和正文共享稳定且唯一的标题ID',()=>{
  for(const file of ['views/ArticleDetailView.vue','components/ReadingToc.vue']){
    const {descriptor,errors}=parse(code(file));assert.deepEqual(errors,[])
    const script=compileScript(descriptor,{id:file})
    assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:file,id:file,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  }
  const source='# foo\n\n## foo\n\n### foo\n\n## foo-2\n\n## **中文** `代码`'
  const result=renderArticle(source)
  const ids=[...result.html.matchAll(/ id="([^"]+)"/g)].map(match=>match[1])
  assert.equal(ids.length,new Set(ids).size)
  assert.deepEqual(result,renderArticle(source))
  assert.deepEqual(result.toc.map(item=>item.id),['read-foo-2','read-foo-3','read-foo-2-2','read-中文-代码'])
  assert.deepEqual(result.toc.map(item=>item.level),[2,3,2,2])
  for(const item of result.toc) assert.ok(result.html.includes(`id="${item.id}"`))
})

test('原始HTML和代码转义，危险协议不生成链接',()=>{
  const result=renderArticle('## <img src=x onerror=alert(1)>\n\n<script>alert(1)</script>\n\n[x](javascript:alert(1))\n\n```html\n<img src=x onerror=alert(2)>\n```')
  assert.doesNotMatch(result.html,/<script\b|<img\b|href="javascript:/i)
  assert.match(result.html,/&lt;script&gt;/)
  assert.match(result.html,/&lt;img/)
  assert.ok(result.toc[0].title.includes('<img'))
})

test('阅读时长按中文和其他词估算，空内容无空目录',()=>{
  assert.equal(renderArticle('字'.repeat(600)).minutes,2)
  assert.equal(renderArticle('word '.repeat(400)).minutes,2)
  assert.equal(renderArticle('字'.repeat(300)+'word '.repeat(200)).minutes,2)
  assert.equal(renderArticle('').minutes,1)
  assert.deepEqual(renderArticle('没有标题的短文').toc,[])
})

test('真实阅读布局变更保留当前段落位置，离开后的异步布局不再滚动',async()=>{
  const app=detail(async()=>response({}));app.window.scrollY=600
  let top=160
  const anchor={isConnected:true,getBoundingClientRect:()=>({top,bottom:top+100})}
  app.body.value={querySelectorAll:()=>[anchor]}
  const changing=app.changeReading({size:19,focused:true});top=240;await changing
  assert.equal(app.readingSize.value,19);assert.equal(app.focusedReading.value,true)
  assert.deepEqual(app.events.at(-1),{top:680,behavior:'instant'})
  const pending=app.changeReading({size:16,focused:false});app.leave();const before=app.events.length;top=500;await pending
  assert.equal(app.events.length,before)
})

test('文章旧响应丢弃，404/失败可重试，离开后不接受数据',async()=>{
  const pending=[]
  const app=detail(()=>{const p=deferred();pending.push(p);return p.promise})
  const old=app.loadArticle(1),current=app.loadArticle(2)
  pending[1].resolve(response({id:2,contentMarkdown:'## 当前'}));await current
  pending[0].resolve(response({id:1,contentMarkdown:'## 旧正文'}));await old
  assert.equal(app.article.value.id,2);assert.match(app.contentHtml.value,/当前/);assert.doesNotMatch(app.contentHtml.value,/旧正文/)
  const missing=app.loadArticle(3);pending[2].resolve(response({},404));await missing
  assert.match(app.error.value,/不存在或尚未发布/);assert.equal(app.article.value,null);assert.equal(app.loading.value,false)
  const retry=app.loadArticle(3);pending[3].resolve(response({id:3,contentMarkdown:'恢复'}));await retry
  assert.equal(app.error.value,'');assert.equal(app.article.value.id,3)
  const leaving=app.loadArticle(4);app.leave();pending[4].resolve(response({id:4}));await leaving
  assert.equal(app.article.value,null)
})

test('正文就绪后才定位章节，浏览器保存位置优先，未知hash安全回顶',async()=>{
  const element={id:'read-中文'},nav=navigation({getElementById:id=>id===element.id?element:null})
  const to={path:'/articles/1',fullPath:'/articles/1#read-中文',hash:'#read-中文'}
  nav.prepareReading(to,{path:'/',matched:[{}]})
  let done=false
  const pending=nav.readingScroll(to,null,()=>to).then(value=>{done=true;return value})
  await nextTick();assert.equal(done,false)
  await nav.finishReading(to.path)
  assert.deepEqual(await pending,{el:element,top:28})
  assert.deepEqual(await nav.readingScroll(to,{left:0,top:1200},()=>to),{left:0,top:1200})
  for(const hash of ['#entries','#%','#[data-x]']){
    const bad={...to,hash,fullPath:to.path+hash}
    assert.deepEqual(await nav.readingScroll(bad,null,()=>bad),{top:0})
  }
})

test('离开文章释放等待，过期滚动不能移动其他页面',async()=>{
  const nav=navigation(),to={path:'/articles/1',fullPath:'/articles/1',hash:''},other={path:'/about',fullPath:'/about'}
  nav.prepareReading(to,{path:'/',matched:[{}]})
  let current=to
  const pending=nav.readingScroll(to,null,()=>current)
  current=other;nav.prepareReading(other,to)
  assert.equal(await pending,false)
})

test('阅读进度限制范围并更新目录，短文和监听清理正常',()=>{
  let rect={top:100,height:3000,bottom:3100}
  const headings={'read-开头':{getBoundingClientRect:()=>({top:-200})},'read-结尾':{getBoundingClientRect:()=>({top:2000})}}
  const app=detail(async()=>response({}),headings)
  app.article.value={contentMarkdown:'## 开头\n\n正文\n\n## 结尾'}
  app.body.value={getBoundingClientRect:()=>rect}
  app.window.scrollY=600;app.updateReading();assert.equal(app.progress.value,0);assert.equal(app.activeId.value,'read-开头');assert.equal(app.showTop.value,true)
  rect={top:-2200,height:3000,bottom:800};app.updateReading();assert.equal(app.progress.value,100);assert.equal(app.activeId.value,'read-结尾')
  rect={top:100,height:100,bottom:200};app.updateReading();assert.equal(app.progress.value,100)
  app.mount();app.scheduleReading();app.scheduleReading();assert.equal(app.frames.size,1)
  app.backToTop();assert.deepEqual(app.events.at(-1),{top:0,behavior:'auto'})
  app.leave();assert.equal(app.frames.size,0);assert.ok(app.events.includes('remove:scroll'));assert.ok(app.events.includes('remove:resize'))
  // 窄屏工具条换行后，定位在136px的章节也应立即高亮。
  headings['read-结尾']={getBoundingClientRect:()=>({top:136})}
  const mobile=detail(async()=>response({}),headings,112)
  mobile.article.value={contentMarkdown:'## 开头\n\n正文\n\n## 结尾'}
  mobile.body.value={getBoundingClientRect:()=>({top:-200,height:3000,bottom:2800})}
  mobile.updateReading();assert.equal(mobile.activeId.value,'read-结尾')
})
test('旧404不能覆盖新正文，卸载后忽略404，非法编号不发送网络请求',async()=>{
  const first=deferred(),second=deferred();let calls=0
  const app=detail(()=>++calls===1?first.promise:second.promise)
  const old=app.loadArticle('1'),latest=app.loadArticle('2')
  second.resolve(response({id:2,title:'新正文',contentMarkdown:'内容'}));await latest
  first.resolve(response({},404));await old
  assert.equal(app.article.value.id,2);assert.equal(app.notFound.value,false);assert.equal(app.error.value,'')
  const pending=deferred(),abandoned=detail(()=>pending.promise),loading=abandoned.loadArticle('1')
  abandoned.leave();pending.resolve(response({},404));await loading;assert.equal(abandoned.notFound.value,false)
  const invalid=detail(()=>{throw Error('不应请求网络')})
  for(const id of ['0','abc','-1','9223372036854775808']){await invalid.loadArticle(id);assert.equal(invalid.notFound.value,true);assert.match(invalid.error.value,/不存在/)}
})
