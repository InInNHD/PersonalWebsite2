import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { ref, reactive, computed, nextTick, effectScope, watchEffect, toValue, createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
const code = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8').replace(/\r\n/g, '\n')
function metadata(route = reactive({ path: '/', query: {}, meta: {} })) {
  const cleanup = [], scope = effectScope(); let api
  const source = code('pageMeta.js').replace(/^import[^\n]*\n/gm, '').replace(/export /g, '')
  scope.run(() => { api = new Function('ref', 'watchEffect', 'toValue', 'onBeforeUnmount', 'useRoute', 'siteProfile', source + ';return {contentMeta,pageMetaKey,usePageMeta,resolvePageMeta,writePageMeta}')(ref, watchEffect, toValue, fn => cleanup.push(fn), () => route, { nickname: '测试作者', siteName: '测试站点' }) })
  return { ...api, route, run: fn => scope.run(fn), stop: () => { scope.stop(); cleanup.forEach(fn => fn()) }, cleanup }
}
function stateComponent() {
  const descriptor = parse(code('components/PageState.vue')).descriptor
  const script = descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  let definitions
  const execute = props => new Function('computed', 'defineProps', 'defineEmits', script + ';return {caption}')(computed, spec => { definitions = spec; return props }, () => () => {})
  execute({})
  return { props: definitions, setup: execute, template: descriptor.template.content }
}
test('第七轮实际页面、统一状态与404模板可编译', () => {
  for (const file of ['App.vue', 'components/PageState.vue', 'components/SiteHeader.vue', 'views/NotFoundView.vue', 'views/ArticleListView.vue', 'views/ArticleDetailView.vue', 'views/TopicView.vue', 'views/ArchiveView.vue', 'views/ResourceView.vue', 'views/AboutView.vue', 'views/AdminArticleListView.vue', 'views/AdminLayout.vue']) {
    const { descriptor, errors } = parse(code(file)); assert.deepEqual(errors, [])
    const script = compileScript(descriptor, { id: file })
    assert.deepEqual(compileTemplate({ id: file, filename: file, source: descriptor.template.content, compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
  }
})
test('真实状态组件有语义骨架、重试按钮与404，错误文案不暴露网络和JSON细节', async () => {
  const component = stateComponent()
  const render = props => renderToString(createSSRApp(component, props))
  const loading = await render({ kind: 'loading', title: '加载中', layout: 'cards' })
  assert.match(loading, /role="status"/); assert.match(loading, /aria-busy="true"/); assert.match(loading, /aria-hidden="true"/)
  const error = await render({ kind: 'error', title: '加载失败', message: 'Failed to fetch', retryLabel: '重试' })
  assert.match(error, /role="alert"/); assert.match(error, /暂时无法连接/); assert.doesNotMatch(error, /Failed to fetch/); assert.match(error, /<button[^>]*>重试/)
  assert.match(await render({ kind: 'error', message: 'HTTP 503' }), /服务暂时无法响应/)
  assert.match(await render({ kind: 'error', message: 'TimeoutError' }), /等待响应时间过长/)
  assert.match(await render({ kind: 'error', message: 'Unexpected token in JSON' }), /收到的内容暂时无法使用/)
  const missing = await render({ kind: 'not-found', heading: 'h1', title: '<script>不存在</script>' })
  assert.match(missing, /404/); assert.match(missing, /<h1>/); assert.doesNotMatch(missing, /<script>/)
})
test('静态与动态metadata区分后台、未知路径和文章404，不接受旧内容身份', () => {
  const app = metadata(), make = (path, query = {}, meta = {}) => ({ path, query, meta })
  for (const path of ['/', '/topics', '/archive', '/diary', '/resources', '/about']) assert.equal(app.resolvePageMeta(make(path)).robots, 'index, follow')
  assert.match(app.resolvePageMeta(make('/about')).title, /测试作者/)
  assert.equal(app.resolvePageMeta(make('/admin/articles')).robots, 'noindex, nofollow')
  assert.equal(app.resolvePageMeta(make('/articles/1/unknown')).robots, 'noindex, nofollow')
  assert.match(app.resolvePageMeta(make('/unknown')).title, /页面不存在/)
  const route = make('/articles/2'), custom = { key: app.pageMetaKey(make('/articles/1')), title: '旧文章' }
  assert.doesNotMatch(app.resolvePageMeta(route, custom).title, /旧文章/)
  custom.key = app.pageMetaKey(route); custom.title = '文章不存在'; custom.noIndex = true
  assert.equal(app.resolvePageMeta(route, custom).robots, 'noindex, nofollow'); app.stop()
})
test('真实watchEffect跟随内容和query，旧组件卸载不会擦除新页面metadata', async () => {
  const app = metadata(), title = ref('第一篇'), description = ref('第一篇摘要')
  app.route.path = '/articles/1'; app.run(() => app.usePageMeta(title, description))
  await nextTick(); assert.equal(app.contentMeta.value.title, '第一篇')
  title.value = '新标题'; await nextTick(); assert.equal(app.contentMeta.value.title, '新标题')
  app.route.query = { from: '/?page=2' }; await nextTick(); assert.equal(app.contentMeta.value.key, app.pageMetaKey(app.route))
  app.run(() => app.usePageMeta('新的页面', '新摘要')); app.cleanup[0](); assert.equal(app.contentMeta.value.title, '新的页面')
  app.stop(); assert.equal(app.contentMeta.value, null)
})
test('metadata只更新一个description与robots节点，长文字收敛且属性写入不执行HTML', () => {
  const app = metadata(), tags = new Map(), document = { title: '', querySelector: selector => tags.get(selector.match(/name="([^"]+)"/)[1]), createElement: () => ({ attributes: {}, setAttribute(name, value) { this.attributes[name] = value } }), head: { appendChild: tag => tags.set(tag.attributes.name, tag) } }
  const route = { path: '/articles/1', query: {} }, custom = { key: app.pageMetaKey(route), title: '<script>alert(1)</script>' + '长'.repeat(200), description: '摘'.repeat(300) }
  app.writePageMeta(route, custom, document); app.writePageMeta(route, custom, document)
  assert.equal(tags.size, 2); assert.equal(tags.get('description').attributes.content.length, 180); assert.equal(document.title.length < 130, true)
  assert.match(document.title, /<script>/); app.stop()
})
test('未知的文章子路径不会进入等待正文的滚动锁，合法详情仍等待并释放', async () => {
  const source = code('readingNavigation.js').replace(/^import[^\n]*\n/gm, '').replace(/export /g, '')
  const nav = new Function('nextTick', 'document', source + ';return {prepareReading,readingScroll,finishReading}')(nextTick, { getElementById: () => null })
  const unknown = { path: '/articles/1/no-page', fullPath: '/articles/1/no-page', hash: '' }
  nav.prepareReading(unknown, { path: '/' }); assert.deepEqual(await nav.readingScroll(unknown, null, () => unknown), { top: 0 })
  const detail = { path: '/articles/1', fullPath: '/articles/1', hash: '' }; nav.prepareReading(detail, unknown)
  const scrolling = nav.readingScroll(detail, null, () => detail); await nav.finishReading(detail.path)
  assert.deepEqual(await scrolling, { top: 0 })
})
