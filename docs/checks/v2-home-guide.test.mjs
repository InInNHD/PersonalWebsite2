// 测试指南代码，不代表用户工作区已完成手敲。
// 从项目根目录运行：node --test docs/checks/v2-home-guide.test.mjs
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'

const require = createRequire(new URL('../../frontend/package.json', import.meta.url))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { ref, computed, reactive } = require('vue')
const guide = readFileSync(new URL('../11-v2-home-foundation.md', import.meta.url), 'utf8').replace(/\r\n/g, '\n')
const homeCode = guide.split('## 11. 替换首页')[1].match(/```vue\n([\s\S]*?)\n```/)[1]

function home(fetch) {
  const route = reactive({ path: '/', hash: '', query: {} })
  const router = { replace: async next => Object.assign(route, next) }
  const script = parse(homeCode).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const names = 'selectType, selectTag, selectCategory, clearAll, toggleFilters, showFilters, keyword, keywordInput, loadArticles, articles, total, page'
  const result = new Function('ref', 'computed', 'onMounted', 'onBeforeUnmount', 'useRoute', 'useRouter', 'fetch',
    `${script}\nreturn {${names}}`)(ref, computed, () => {}, () => {}, () => route, () => router, fetch)
  return { ...result, route }
}
const page = (items, total = items.length) => new Response(JSON.stringify({ items, total, page: 1, size: 10, totalPages: Math.ceil(total / 10) }))

test('指南内全部Vue文件可编译，素材存在', () => {
  const blocks = [...guide.matchAll(/```vue\n([\s\S]*?)\n```/g)]
  assert.equal(blocks.length, 6)
  blocks.forEach(([, code], index) => {
    const id = `guide-${index}`
    const { descriptor, errors } = parse(code)
    assert.deepEqual(errors, [])
    const script = compileScript(descriptor, { id })
    const result = compileTemplate({ source: descriptor.template.content, filename: `${id}.vue`, id,
      compilerOptions: { bindingMetadata: script.bindings } })
    assert.deepEqual(result.errors, [])
  })
  for (const name of ['hero', 'note', 'thought', 'diary']) {
    assert.match(readFileSync(new URL(`../design-v2/assets/${name}.svg`, import.meta.url), 'utf8'), /xmlns="http:\/\/www.w3.org\/2000\/svg"/)
  }
})

test('栏目、分类与标签入口保留已提交搜索词，重置清空所有条件', async () => {
  const urls = []
  const app = home(async url => { urls.push(url); return page([{ id: 1 }]) })
  app.keyword.value = 'Java'
  app.keywordInput.value = '尚未提交'
  await app.selectType('NOTE')
  await app.selectTag(5)
  await app.selectCategory(9)
  const params = new URL(urls.at(-1), 'http://localhost').searchParams
  assert.equal(params.get('type'), 'NOTE')
  assert.equal(params.get('tagId'), '5')
  assert.equal(params.get('categoryId'), '9')
  assert.equal(params.get('keyword'), 'Java')
  await app.clearAll()
  assert.equal(new URL(urls.at(-1), 'http://localhost').search, '?page=1&size=10')
  assert.equal(app.keywordInput.value, '')
})

test('搜索入口展开后可收起，再次进入仍能展开', async () => {
  const app = home(async () => page([]))
  app.route.hash = '#article-search'
  assert.equal(app.showFilters.value, true)
  await app.toggleFilters()
  assert.equal(app.route.hash, '')
  assert.equal(app.showFilters.value, false)
  app.route.hash = '#article-search'
  assert.equal(app.showFilters.value, true)
})

test('快速切换栏目时旧响应不能覆盖最新结果与总数', async () => {
  const pending = []
  const app = home(() => new Promise(resolve => pending.push(resolve)))
  const old = app.selectType('NOTE')
  const current = app.selectType('DIARY')
  pending[1](page([{ id: 2 }], 1))
  await current
  pending[0](page([{ id: 1 }], 40))
  await old
  assert.equal(app.articles.value[0].id, 2)
  assert.equal(app.total.value, 1)
})
