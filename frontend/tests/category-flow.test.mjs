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

// 本轮列表响应为分页对象，详情与管理员响应仍使用原 response()。
function articlePage(items, total = items.length, page = 1, size = 10) {
  return response({ items, total, page, size, totalPages: Math.ceil(total / size) })
}

test('搜索与全部筛选同时传入，旧结果不能覆盖列表和分页信息', async () => {
  const pending = []
  const app = component('ArticleListView.vue',
    'loadArticles, type, categoryId, tagId, keyword, page, articles, total, totalPages, error, loading', url => {
      const item = { url, ...deferred() }
      pending.push(item)
      return item.promise
    })
  app.type.value = 'NOTE'
  app.categoryId.value = 11
  app.tagId.value = 101
  app.keyword.value = 'Java 学习'
  app.page.value = 2
  const first = app.loadArticles()
  app.tagId.value = 202
  app.page.value = 1
  const second = app.loadArticles()
  const query = new URL(pending[0].url, 'http://localhost').searchParams
  assert.equal(query.get('type'), 'NOTE')
  assert.equal(query.get('categoryId'), '11')
  assert.equal(query.get('tagId'), '101')
  assert.equal(query.get('keyword'), 'Java 学习')
  assert.equal(query.get('page'), '2')
  assert.equal(query.get('size'), '10')
  pending[1].resolve(articlePage([{ id: 2, tags: [{ id: 202 }] }], 11, 1))
  await second
  pending[0].resolve(articlePage([{ id: 1 }], 99, 2))
  await first
  assert.equal(app.articles.value[0].id, 2)
  assert.equal(app.articles.value[0].tags[0].id, 202)
  assert.equal(app.total.value, 11)
  assert.equal(app.totalPages.value, 2)
  assert.equal(app.page.value, 1)
  assert.equal(app.error.value, '')
  assert.equal(app.loading.value, false)

  app.tagId.value = ''
  const allTags = app.loadArticles()
  const cleared = new URL(pending[2].url, 'http://localhost').searchParams
  assert.equal(cleared.has('tagId'), false)
  assert.equal(cleared.get('keyword'), 'Java 学习')
  pending[2].resolve(articlePage([]))
  await allTags
})

test('旧失败不影响新请求，新失败清理分页，卸载不再接受结果', async () => {
  const pending = []
  const app = component('ArticleListView.vue',
    'loadArticles, articles, total, totalPages, error, loading', () => {
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
  pending[1].resolve(articlePage([{ id: 2 }], 21))
  await second
  assert.equal(app.totalPages.value, 3)
  const failed = app.loadArticles()
  pending[2].reject(new Error('network failed'))
  await failed
  assert.equal(app.error.value, 'network failed')
  assert.deepEqual(app.articles.value, [])
  assert.equal(app.total.value, 0)
  assert.equal(app.totalPages.value, 0)
  const leaving = app.loadArticles()
  app.unmount()
  pending[3].resolve(articlePage([{ id: 99 }], 99))
  await leaving
  assert.deepEqual(app.articles.value, [])
  assert.equal(app.total.value, 0)
})

test('搜索提交与清空回第一页，翻页保留已提交关键词，条件变化也重置页码', async () => {
  const urls = []
  const app = component('ArticleListView.vue',
    'searchArticles, clearSearch, applyFilters, changePage, keywordInput, keyword, page, categoryId, loading, totalPages',
    async url => {
      urls.push(url)
      const requested = Number(new URL(url, 'http://localhost').searchParams.get('page'))
      // 模拟文章减少后，后端把第三页纠正为第二页。
      return articlePage([{ id: 1 }], 11, Math.min(requested, 2))
    })
  app.page.value = 3
  app.keywordInput.value = '  Java  '
  await app.searchArticles()
  assert.equal(app.page.value, 1)
  assert.equal(app.keyword.value, 'Java')
  app.keywordInput.value = '尚未提交的新文字'
  await app.changePage(2)
  assert.equal(app.page.value, 2)
  assert.equal(new URL(urls.at(-1), 'http://localhost').searchParams.get('keyword'), 'Java')
  const before = urls.length
  await app.changePage(0)
  await app.changePage(3)
  app.loading.value = true
  await app.changePage(1)
  app.loading.value = false
  assert.equal(urls.length, before)
  app.totalPages.value = 3
  await app.changePage(3)
  assert.equal(app.page.value, 2) // 接受后端返回的实际页码。
  app.categoryId.value = 11
  await app.applyFilters()
  assert.equal(app.page.value, 1)
  assert.equal(new URL(urls.at(-1), 'http://localhost').searchParams.get('categoryId'), '11')
  await app.clearSearch()
  assert.equal(app.keyword.value, '')
  assert.equal(app.keywordInput.value, '')
  assert.equal(app.page.value, 1)
  assert.equal(new URL(urls.at(-1), 'http://localhost').searchParams.has('keyword'), false)
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
