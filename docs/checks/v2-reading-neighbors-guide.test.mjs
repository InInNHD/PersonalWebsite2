import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'

const require = createRequire(new URL('../../frontend/package.json', import.meta.url))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { ref, computed, reactive } = require('vue')
const guide = readFileSync(new URL('../16-v2-reading-neighbors.md', import.meta.url), 'utf8').replace(/\r\n/g, '\n')
const component = guide.split('文件：`frontend/src/components/ArticleNeighbors.vue`。')[1].match(/```vue\n([\s\S]*?)\n```/)[1]
const navigation = readFileSync(new URL('../../frontend/src/listNavigation.js', import.meta.url), 'utf8')
const { listReturn } = new Function('nextTick', navigation.replace(/^import[^\n]*\n/gm, '').replaceAll('export ', '') + ';return {listReturn}')(() => {})
function app(fetch, from = '/?type=NOTE&tagId=9007199254740993&keyword=测试&page=2#entries') {
  const props = reactive({ articleId: 1, from }), leaves = [], watchers = []
  const bindings = { computed, ref, listReturn, defineProps: () => props, fetch, watch: (_, fn) => watchers.push(fn), onBeforeUnmount: fn => leaves.push(fn) }
  const script = parse(component).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const state = new Function(...Object.keys(bindings), script + ';return {previous,next,loading,error,returnPath,articleLocation,loadNeighbors}')(...Object.values(bindings))
  return { ...state, props, leave: () => leaves.forEach(fn => fn()), change: () => watchers.forEach(fn => fn()) }
}
const response = (data, status = 200) => new Response(JSON.stringify(data), { status })
function deferred() { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }

test('3C导航组件可编译并具备加载/重试/边界状态', () => {
  const { descriptor, errors } = parse(component)
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'neighbors' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: 'ArticleNeighbors.vue', id: 'neighbors', compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
  assert.match(component, /已经是最早一篇公开文章/)
  assert.match(component, /已经是最新一篇公开文章/)
  assert.doesNotMatch(component, /v-html/)
})

test('只请求导航接口，正确保存相邻项；仅一篇时两边为空', async () => {
  const requests = [], before = { id: 2, title: '<script>标题</script>', publishedAt: '2026-10-01T10:00:00' }, after = { id: 3, title: '下一篇' }
  const state = app(async url => { requests.push(url); return response({ previous: before, next: after }) })
  await state.loadNeighbors()
  assert.deepEqual(requests, ['/api/articles/1/neighbors'])
  assert.deepEqual(state.previous.value, before)
  assert.deepEqual(state.next.value, after)
  assert.equal(state.error.value, ''); assert.equal(state.loading.value, false)
  const single = app(async () => response({ previous: null, next: null }))
  await single.loadNeighbors()
  assert.equal(single.previous.value, null); assert.equal(single.next.value, null)
})

test('连续阅读保留合法来源与Long字符串，拒绝站外和管理地址', () => {
  const state = app(() => {})
  const location = state.articleLocation({ id: 3 })
  assert.equal(location.path, '/articles/3')
  const query = new URL(location.query.from, 'https://local.invalid')
  assert.equal(query.searchParams.get('type'), 'NOTE')
  assert.equal(query.searchParams.get('tagId'), '9007199254740993')
  assert.equal(query.searchParams.get('keyword'), '测试')
  assert.equal(query.searchParams.get('page'), '2'); assert.equal(query.hash, '#entries')
  for (const from of ['https://evil.invalid', '//evil.invalid', '/admin', '/\\evil.invalid', ['/?page=2']]) {
    state.props.from = from
    assert.equal(state.returnPath.value, '/')
  }
})

test('换文时清空旧链接，过期导航不能覆盖最新文章', async () => {
  const requests = [], state = app(() => { const task = deferred(); requests.push(task); return task.promise })
  const old = state.loadNeighbors()
  state.props.articleId = 2
  const current = state.loadNeighbors()
  assert.equal(state.previous.value, null); assert.equal(state.next.value, null); assert.equal(state.loading.value, true)
  requests[1].resolve(response({ previous: { id: 1 }, next: { id: 3 } })); await current
  requests[0].resolve(response({ previous: null, next: { id: 2 } })); await old
  assert.equal(state.previous.value.id, 1); assert.equal(state.next.value.id, 3)
})

test('导航失败不保留旧链接，可重试；404有明确提示', async () => {
  let status = 503
  const state = app(async () => response({ previous: null, next: { id: 2 } }, status))
  await state.loadNeighbors()
  assert.match(state.error.value, /HTTP 503/); assert.equal(state.next.value, null); assert.equal(state.loading.value, false)
  status = 200; await state.loadNeighbors()
  assert.equal(state.next.value.id, 2); assert.equal(state.error.value, '')
  status = 404; await state.loadNeighbors()
  assert.match(state.error.value, /已下线或不存在/); assert.equal(state.next.value, null)
})

test('卸载后不接受导航响应或失败', async () => {
  const task = deferred(), state = app(() => task.promise)
  const pending = state.loadNeighbors()
  state.leave(); task.resolve(response({ previous: { id: 4 }, next: { id: 8 } })); await pending
  assert.equal(state.previous.value, null); assert.equal(state.next.value, null); assert.equal(state.error.value, '')
})
