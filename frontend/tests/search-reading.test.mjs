import assert from 'node:assert/strict'
import test from 'node:test'
import {readFileSync} from 'node:fs'
import {parse,compileScript,compileTemplate} from '@vue/compiler-sfc'
import {ref,computed,watch,reactive,nextTick,effectScope,createSSRApp} from 'vue'
import {renderToString} from '@vue/server-renderer'
const source=file=>readFileSync(new URL('../src/'+file,import.meta.url),'utf8').replace(/\r\n/g,'\n')
const plain=text=>text.replace(/^import[^\n]*\n/gm,'').replaceAll('export ','')
const script=file=>plain(parse(source(file)).descriptor.scriptSetup.content)
test('搜索片段与阅读工具实际组件可编译，恶意命中仅作为文本显示',async()=>{
  for(const file of ['components/SearchSnippet.vue','components/ReadingTools.vue','views/ArticleDetailView.vue']) {
    const {descriptor,errors}=parse(source(file));assert.deepEqual(errors,[])
    const compiled=compileScript(descriptor,{id:file})
    assert.deepEqual(compileTemplate({id:file,filename:file,source:descriptor.template.content,compilerOptions:{bindingMetadata:compiled.bindings}}).errors,[])
  }
  const match={field:'body',before:'<script>',match:'<img src=x onerror=alert(1)>',after:'</script>',leading:true,trailing:true}
  const fields={body:'正文命中'}
  const html=await renderToString(createSSRApp({setup:()=>({match,fields}),template:parse(source('components/SearchSnippet.vue')).descriptor.template.content}))
  assert.match(html,/<mark>&lt;img/);assert.doesNotMatch(html,/<script>|<img /);assert.match(html,/正文命中/)
})
function tools(clipboard,stored='16',brokenStorage=false) {
  const events=[],leaves=[],mounted=[],callbacks={},props=reactive({articleId:1}),scope=effectScope()
  const window={location:{origin:'http://localhost:5173'},addEventListener:(name,fn)=>callbacks[name]=fn,removeEventListener:name=>delete callbacks[name]}
  const storage={getItem:()=>{if(brokenStorage)throw Error();return stored},setItem:(key,value)=>{if(brokenStorage)throw Error();stored=value}}
  let dialog=false,state
  const bindings={ref,computed,watch,defineProps:()=>props,defineEmits:()=>((...args)=>events.push(args)),onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>leaves.push(fn),window,localStorage:storage,navigator:{clipboard},document:{querySelector:()=>dialog?{}:null}}
  scope.run(()=>{state=new Function(...Object.keys(bindings),script('components/ReadingTools.vue')+';return {size,focused,focusButton,copyState,copying,shareUrl,setSize,toggleFocus,copyLink}')(...Object.values(bindings))})
  return {...state,props,events,callbacks,mount:()=>mounted.forEach(fn=>fn()),dialog:value=>dialog=value,stored:()=>stored,leave:()=>{leaves.forEach(fn=>fn());scope.stop()}}
}
test('字号安全记忆，禁用存储仍可阅读；专注Escape不干扰图片弹窗并清理监听',()=>{
  const app=tools({},'19');app.mount();assert.equal(app.size.value,19)
  app.setSize(16);assert.equal(app.stored(),'16');app.setSize(50);assert.equal(app.size.value,16)
  app.toggleFocus();assert.equal(app.focused.value,true)
  app.dialog(true);app.callbacks.keydown({key:'Escape'});assert.equal(app.focused.value,true)
  app.dialog(false);let focused=false;app.focusButton.value={focus:()=>focused=true}
  app.callbacks.keydown({key:'Escape',preventDefault:()=>{}});assert.equal(app.focused.value,false);assert.ok(focused)
  app.leave();assert.equal(app.callbacks.keydown,undefined)
  const blocked=tools({},'19',true);blocked.setSize(19);assert.equal(blocked.size.value,19);blocked.leave()
})
test('分享仅复制文章干净地址；拒绝时给手动链接，换文和卸载忽略旧结果',async()=>{
  const copied=[],app=tools({writeText:async text=>copied.push(text)})
  await app.copyLink();assert.deepEqual(copied,['http://localhost:5173/articles/1']);assert.match(app.copyState.value,/已复制/)
  const failed=tools(undefined);await failed.copyLink();assert.match(failed.copyState.value,/手动复制/);assert.equal(failed.copying.value,false)
  let resolve;const stale=tools({writeText:()=>new Promise(done=>resolve=done)})
  const pending=stale.copyLink();stale.props.articleId=2;await nextTick();resolve();await pending;assert.equal(stale.copyState.value,'')
  const leaving=stale.copyLink();stale.leave();resolve();await leaving;assert.equal(stale.copyState.value,'')
  app.leave();failed.leave()
})
test('章节定位为换行后的工具条留出空间，浏览器保存位置仍优先',async()=>{
  const node={id:'read-section'},document={getElementById:()=>node,querySelector:()=>({getBoundingClientRect:()=>({height:108})})}
  const nav=new Function('nextTick','document',plain(source('readingNavigation.js'))+';return {readingScroll}')(nextTick,document)
  const to={path:'/articles/1',fullPath:'/articles/1#read-section',hash:'#read-section'}
  assert.deepEqual(await nav.readingScroll(to,null,()=>to),{el:node,top:132})
  assert.deepEqual(await nav.readingScroll(to,{top:600,left:0},()=>to),{top:600,left:0})
})
