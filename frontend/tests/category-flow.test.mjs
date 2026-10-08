import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { parse, compileTemplate } from '@vue/compiler-sfc'
import { computed, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'

// 执行真实组件脚本；只替换网络和浏览器对话框，不复制组件的业务函数。
function component(file, names, fetch, window = {}) {
  const source = readFileSync(new URL(`../src/views/${file}`, import.meta.url), 'utf8')
  const { descriptor, errors } = parse(source)
  assert.deepEqual(errors, [])
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: file, id: file }).errors, [])
  const cleanup = []
  const route = reactive({ params: { id: '1' } })
  const script = descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const app = new Function('ref', 'computed', 'reactive', 'onMounted', 'onUnmounted',
    'onBeforeUnmount', 'onBeforeRouteLeave', 'MarkdownIt', 'window', 'fetch', 'useRoute', 'watch',
    `${script}\nreturn {${names}}`)(ref, computed, reactive, () => {}, f => cleanup.push(f),
    f => cleanup.push(f), () => {}, MarkdownIt, window, fetch, () => route, () => {})
  return { ...app, unmount: () => cleanup.forEach(f => f()) }
}

const response = (value, status = 200) => new Response(JSON.stringify(value), { status })
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('栏目与分类同时传入，旧筛选结果不能覆盖最新列表', async () => {
  const pending = []
  const app = component('ArticleListView.vue', 'loadArticles, type, categoryId, articles, error, loading', url => {
    const item = { url, ...deferred() }
    pending.push(item)
    return item.promise
  })
  app.type.value = 'NOTE'
  app.categoryId.value = 11
  const first = app.loadArticles()
  app.categoryId.value = 22
  const second = app.loadArticles()
  assert.equal(pending[0].url, '/api/articles?type=NOTE&categoryId=11')
  assert.equal(pending[1].url, '/api/articles?type=NOTE&categoryId=22')
  pending[1].resolve(response([{ id: 2, categoryName: 'Vue' }]))
  await second
  pending[0].resolve(response([{ id: 1, categoryName: 'Java' }]))
  await first
  assert.equal(app.articles.value[0].categoryName, 'Vue')
  assert.equal(app.error.value, '')
  assert.equal(app.loading.value, false)
})

test('旧请求结束不关闭新请求加载状态，失败也不覆盖新结果', async () => {
  const pending = []
  const app = component('ArticleListView.vue', 'loadArticles, articles, error, loading', () => {
    const item = deferred()
    pending.push(item)
    return item.promise
  })
  const first = app.loadArticles()
  const second = app.loadArticles()
  pending[0].reject(new Error('obsolete failure'))
  await first
  assert.equal(app.loading.value, true)
  assert.equal(app.error.value, '')
  pending[1].resolve(response([{ id: 2 }]))
  await second
  assert.equal(app.articles.value[0].id, 2)
  assert.equal(app.loading.value, false)
})

test('详情的旧正文和分类不会覆盖新文章', async () => {
  const pending = []
  const app = component('ArticleDetailView.vue', 'loadArticle, article, contentHtml', () => {
    const item = deferred()
    pending.push(item)
    return item.promise
  })
  const first = app.loadArticle(1)
  const second = app.loadArticle(2)
  pending[1].resolve(response({ id: 2, categoryName: 'Vue', contentMarkdown: '# Current' }))
  await second
  pending[0].resolve(response({ id: 1, categoryName: 'Java', contentMarkdown: '# Old' }))
  await first
  assert.equal(app.article.value.id, 2)
  assert.equal(app.article.value.categoryName, 'Vue')
  assert.match(app.contentHtml.value, /Current/)
  assert.doesNotMatch(app.contentHtml.value, /Old/)
})

test('后台登录、分类新增重名与改名、文章归类发布及恢复未分类', async () => {
  const categories = []
  let article
  let input = 'Java'
  const app = component('AdminView.vue',
    'connect, password, connected, editCategory, articleCategories, form, save, publish, openArticle, dirty, error',
    async (path, options = {}) => {
      const method = options.method ?? 'GET'
      assert.match(options.headers.get('Authorization'), /^Basic /)
      assert.equal(options.headers.get('X-Requested-With'), 'XMLHttpRequest')
      if (method !== 'GET') assert.equal(options.headers.get('X-CSRF-TOKEN'), 'fixture-token')
      const body = options.body ? JSON.parse(options.body) : null
      if (path.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'fixture-token' })
      if (path.endsWith('/check')) return response({ ok: true })
      if (path.endsWith('/resources')) return response([])
      if (path.endsWith('/tags') && method === 'GET') return response([])
      if (path.endsWith('/categories')) {
        if (method === 'GET') return response(categories)
        if (categories.some(c => c.name === body.name)) return response({}, 409)
        const item = { id: categories.length + 1, name: body.name }
        categories.push(item)
        return response(item, 201)
      }
      if (path.includes('/categories/')) {
        const item = categories.find(c => c.id === Number(path.split('/').at(-1)))
        item.name = body.name
        return response(item)
      }
      if (path.endsWith('/articles') && method === 'GET') return response(article ? [article] : [])
      if (path.endsWith('/articles') && method === 'POST') {
        article = { ...body, id: 41, status: 'DRAFT' }
        return response({ id: article.id, status: article.status }, 201)
      }
      if (path.endsWith('/publish')) {
        article.status = 'PUBLISHED'
        return response({ id: article.id, status: article.status })
      }
      if (path.endsWith('/articles/41')) {
        if (method === 'PUT') article = { ...article, ...body }
        return response(article)
      }
      throw new Error(`Unexpected request: ${method} ${path}`)
    }, { prompt: () => input, confirm: () => true })

  app.password.value = 'test-only'
  await app.connect()
  assert.equal(app.connected.value, true)
  await app.editCategory()
  assert.equal(app.articleCategories.value[0].name, 'Java')
  await app.editCategory()
  assert.match(app.error.value, /分类名称已存在/)
  input = 'Java 后端'
  await app.editCategory(app.articleCategories.value[0])
  assert.equal(app.articleCategories.value[0].name, input)

  Object.assign(app.form, { title: '分类测试', summary: '', contentMarkdown: '# Body', categoryId: 1 })
  assert.equal(app.dirty.value, true)
  await app.save()
  assert.equal(article.categoryId, 1)
  assert.equal(app.form.id, 41)
  assert.equal(app.dirty.value, false)
  await app.openArticle(41)
  assert.equal(app.form.categoryId, 1)
  await app.publish()
  assert.equal(article.status, 'PUBLISHED')
  app.form.categoryId = null
  await app.save()
  assert.equal(article.categoryId, null)
  assert.equal(article.status, 'PUBLISHED')
  await app.openArticle(41)
  assert.equal(app.form.categoryId, null)
  assert.equal(app.dirty.value, false)
  assert.equal(app.error.value, '')
})

test('标签新增、重名、改名、取消、空白校验及重新加载', async () => {
  const stored = []
  let input = ' Spring Boot '
  let writes = 0
  const app = component('AdminView.vue', 'editTag, loadTags, tags, error, message', async (path, options) => {
    if (path.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'token' })
    const method = options.method ?? 'GET'
    if (method === 'GET') return response(stored)
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'token')
    const { name } = JSON.parse(options.body)
    const id = method === 'POST' ? stored.length + 1 : Number(path.split('/').at(-1))
    if (stored.some(item => item.name === name && item.id !== id) ||
        (method === 'POST' && stored.some(item => item.name === name))) return response({}, 409)
    writes++
    const result = { id, name }
    if (method === 'POST') stored.push(result)
    else stored[stored.findIndex(item => item.id === id)] = result
    return response(result, method === 'POST' ? 201 : 200)
  }, { prompt: () => input })
  await app.editTag()
  assert.equal(app.tags.value[0].name, 'Spring Boot')
  await app.editTag()
  assert.match(app.error.value, /标签名称已存在/)
  input = 'Spring Security'
  await app.editTag(app.tags.value[0])
  assert.equal(app.tags.value[0].id, 1)
  assert.equal(app.tags.value[0].name, input)
  await app.editTag(app.tags.value[0])
  assert.equal(app.error.value, '')
  const before = writes
  input = null
  await app.editTag()
  input = '   '
  await app.editTag()
  assert.match(app.error.value, /1～50/)
  input = 'x'.repeat(51)
  await app.editTag()
  assert.equal(writes, before)
  app.tags.value = []
  await app.loadTags()
  assert.deepEqual(JSON.parse(JSON.stringify(app.tags.value)), stored)
})

test('文章标签发送、回显、清空、新建重置及保存失败保留修改', async () => {
  let stored
  let rejectSave = false
  const app = component('AdminView.vue',
    'form, save, openArticle, newArticle, fillForm, articleDirty, error',
    async (path, options = {}) => {
      if (path.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'tag-token' })
      const method = options.method ?? 'GET'
      if (method === 'GET') return response(path.endsWith('/articles') ? [stored] : stored)
      assert.equal(options.headers.get('X-CSRF-TOKEN'), 'tag-token')
      if (rejectSave) return response({}, 400)
      const payload = JSON.parse(options.body)
      assert.ok(payload.tagIds.every(Number.isInteger))
      stored = { ...payload, id: 51, status: 'DRAFT' }
      return response(stored, method === 'POST' ? 201 : 200)
    }, { confirm: () => true })

  Object.assign(app.form, { title: '标签文章', contentMarkdown: '# Body', tagIds: [11, 22] })
  await app.save()
  assert.deepEqual(stored.tagIds, [11, 22])
  assert.equal(app.articleDirty.value, false)
  await app.openArticle(51)
  assert.deepEqual([...app.form.tagIds], [11, 22])

  app.form.tagIds = [22]
  assert.equal(app.articleDirty.value, true)
  rejectSave = true
  await app.save()
  assert.deepEqual(stored.tagIds, [11, 22])
  assert.deepEqual([...app.form.tagIds], [22])
  assert.equal(app.articleDirty.value, true)
  assert.match(app.error.value, /输入校验失败/)

  rejectSave = false
  app.form.tagIds = []
  await app.save()
  assert.deepEqual(stored.tagIds, [])
  const source = { ...stored, tagIds: [11] }
  app.fillForm(source)
  app.form.tagIds.push(22)
  assert.deepEqual(source.tagIds, [11])
  app.newArticle()
  assert.deepEqual([...app.form.tagIds], [])
  assert.equal(app.form.id, null)
  assert.equal(app.articleDirty.value, false)
})
