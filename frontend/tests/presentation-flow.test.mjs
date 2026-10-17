// 读取当前业务组件，只替换网络与组件生命周期入口。
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { ref, computed, reactive, watch, nextTick } from 'vue'
function code(file) {
  return readFileSync(new URL(`../src/components/${file}`, import.meta.url), 'utf8')
}
function stats(fetch) {
  let leave
  const source = parse(code('SiteStats.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const app = new Function('ref', 'onMounted', 'onBeforeUnmount', 'fetch', `${source}\nreturn {stats,loading,error,loadStats}`)(ref, () => {}, fn => { leave = fn }, fetch)
  return {...app, leave: () => leave()}
}
test('实际封面和统计组件可编译', () => {
  for (const file of ['ArticleCard.vue', 'SiteStats.vue']) {
    const {descriptor, errors} = parse(code(file))
    assert.deepEqual(errors, [])
    const script = compileScript(descriptor, {id:file})
    assert.deepEqual(compileTemplate({source:descriptor.template.content, filename:file, id:file, compilerOptions:{bindingMetadata:script.bindings}}).errors, [])
  }
})
test('封面为空、失败与更换地址时正确回退或重试', async () => {
  const props=reactive({article:{id:1,type:'NOTE',coverUrl:null}})
  const source=parse(code('ArticleCard.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const app=new Function('computed','ref','watch','defineProps','defineEmits','noteCover','thoughtCover','diaryCover', `${source}\nreturn {cover,coverFailed}`)(computed,ref,watch,()=>props,()=>()=>{},'note.svg','thought.svg','diary.svg')
  assert.equal(app.cover.value,'note.svg')
  props.article.coverUrl='https://example.com/one.png'; await nextTick()
  assert.equal(app.cover.value,props.article.coverUrl)
  app.coverFailed.value=true
  assert.equal(app.cover.value,'note.svg')
  props.article.coverUrl='https://example.com/two.png'; await nextTick()
  assert.equal(app.cover.value,props.article.coverUrl)
})
test('统计失败不冒充零，重试可恢复，卸载后不接受结果', async () => {
  let fail=true
  const values={articles:2,categories:1,tags:1,resources:0}
  const app=stats(async()=>new Response(JSON.stringify(values), {status:fail?503:200}))
  await app.loadStats(); assert.equal(app.stats.value,null); assert.ok(app.error.value)
  fail=false; await app.loadStats(); assert.deepEqual(app.stats.value,values); assert.equal(app.error.value,'')
  let resolve, calls=0
  const leaving=stats(()=>{calls++; return new Promise(yes=>{resolve=yes})})
  const pending=leaving.loadStats(); await leaving.loadStats(); assert.equal(calls,1)
  leaving.leave(); resolve(new Response(JSON.stringify(values))); await pending
  assert.equal(leaving.stats.value,null)
})
