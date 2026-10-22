import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { ref, reactive, computed, nextTick } from 'vue'
import MarkdownIt from 'markdown-it'
const code = name => readFileSync(new URL('../src/' + name, import.meta.url), 'utf8').replace(/\r\n/g, '\n')
function component(name, extra, names) {
  const source = parse(code(name)).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const values = {usePageMeta:()=>{}, ref, reactive, computed, nextTick, onMounted: () => {}, onBeforeUnmount: () => {}, watch: () => {}, ...extra }
  return new Function(...Object.keys(values), source + ';return {' + names + '}')(...Object.values(values))
}
test('第六轮资源页、图标、图片库和两个管理页面实际模板可编译', () => {
  for (const name of ['views/ResourceView.vue', 'components/ResourceIcon.vue', 'components/AdminMediaPanel.vue', 'views/AdminArticleEditorView.vue', 'views/AdminResourceView.vue']) {
    const { descriptor, errors } = parse(code(name)); assert.deepEqual(errors, [])
    const script = compileScript(descriptor, { id: name })
    assert.deepEqual(compileTemplate({ id: name, filename: name, source: descriptor.template.content, compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
  }
})
test('资源按公开结果分组、组内推荐理由搜索及URL回放，拒绝不安全外链', async () => {
  const route = reactive({ query: {} }), paths = []
  const app = component('views/ResourceView.vue', { useRoute: () => route, useRouter: () => ({ replace: next => { paths.push(next); route.query = next.query ?? {} } }), fetch: async () => new Response(JSON.stringify([
    { id: 1, title: 'Java', description: '文档', url: 'https://example.com', groupId: 2, groupName: '学习', recommendation: '入门' },
    { id: 2, title: '工具', description: '中文', url: 'javascript:bad()', groupId: 3, groupName: '工具' },
    { id: 3, title: '无分组', description: '', url: 'https://user:password@example.com', groupId: null },
  ])) }, 'resources,groups,visibleGroups,visibleCount,search,change,httpUrl,loadResources')
  await app.loadResources(); assert.equal(app.groups.value.length, 3); assert.equal(app.visibleCount.value, 3)
  app.change({ group: '2', q: '入门' }); assert.equal(app.visibleCount.value, 1); assert.equal(app.visibleGroups.value[0].items[0].id, 1)
  app.search.value = '工具'; assert.equal(app.visibleCount.value, 0); assert.equal(route.query.group, '2')
  assert.equal(app.httpUrl('ftp://example.com'), null); assert.equal(app.resources.value[2].href, undefined)
  assert.equal(app.httpUrl('//evil.com'), null); assert.equal(paths.length, 2)
})
test('资源过期响应与卸载结果被忽略，失败重试恢复', async () => {
  const pending = []; let cleanup; let fail = false
  const app = component('views/ResourceView.vue', { useRoute: () => ({ query: {} }), useRouter: () => ({}), onBeforeUnmount: fn => { cleanup = fn }, fetch: () => fail ? Promise.resolve(new Response('{}', { status: 503 })) : new Promise(resolve => pending.push(resolve)) }, 'resources,error,loadResources')
  const old = app.loadResources(), latest = app.loadResources()
  pending[1](new Response(JSON.stringify([{ id: 2, url: 'https://example.com' }]))); await latest
  pending[0](new Response(JSON.stringify([{ id: 1, url: 'https://example.com' }]))); await old; assert.equal(app.resources.value[0].id, 2)
  fail = true; await app.loadResources(); assert.match(app.error.value, /503/)
  fail = false; const abandoned = app.loadResources(); cleanup(); pending[2](new Response('[]')); await abandoned; assert.equal(app.resources.value[0].id, 2)
})
test('共享请求自动处理multipart边界、CSRF、图片blob及413错误', async () => {
  let oversized = false; const calls = []
  const app = new Function('ref', 'fetch', code('adminSession.js').replace(/^import[^\n]*\n/gm, '').replace(/export /g, '') + ';return {loginAdmin,adminRequest}')(ref, async (path, options) => {
    calls.push({ path, options })
    if (path.endsWith('/check')) return new Response('{}')
    if (path.endsWith('/csrf')) return new Response('{"headerName":"X-CSRF-TOKEN","token":"fresh"}')
    if (path.endsWith('/content')) return new Response(new Blob(['png'], { type: 'image/png' }))
    return oversized ? new Response('{}', { status: 413 }) : new Response('{}', { status: 201 })
  })
  await app.loginAdmin('test'); const body = new FormData(); body.append('file', new Blob(['png'], { type: 'image/png' }), 'x.png')
  await app.adminRequest('/media', { method: 'POST', body }); const upload = calls.at(-1)
  assert.equal(upload.options.headers.has('Content-Type'), false); assert.equal(upload.options.headers.get('X-CSRF-TOKEN'), 'fresh')
  const image = await app.adminRequest('/media/x/content', { responseType: 'blob' }); assert.equal(await image.text(), 'png')
  oversized = true; await assert.rejects(app.adminRequest('/media', { method: 'POST', body }), /5MiB/)
})
test('图片库前置校验、上传失败保留文件、成功刷新与预览URL释放', async () => {
  const emitted = [], revoked = []; let cleanup; let fail = true; const calls = []
  const app = component('components/AdminMediaPanel.vue', { defineProps: () => ({ references: [] }), defineEmits: () => (...args) => emitted.push(args), onBeforeUnmount: fn => { cleanup = fn }, URL: { createObjectURL: () => 'blob:preview', revokeObjectURL: value => revoked.push(value) }, adminRequest: async (path, options) => {
    calls.push(path)
    if (options?.method === 'POST') { if (fail) throw Error('HTTP 503'); return { id: 1 } }
    return options?.responseType === 'blob' ? new Blob(['png']) : { items: [{ id: '1', url: '/api/media/test.png' }], page: 1, totalPages: 1 }
  } }, 'input,error,upload,load,previews,choose')
  app.input.value = { files: [new File(['bad'], 'x.svg', { type: 'image/svg+xml' })], value: 'selected' }
  await app.upload(); assert.match(app.error.value, /PNG/); assert.equal(calls.length, 0)
  app.input.value.files = [new File(['png'], 'x.png', { type: 'image/png' })]
  await app.upload(); assert.match(app.error.value, /503/); assert.equal(app.input.value.value, 'selected')
  fail = false; await app.upload(); assert.equal(app.input.value.value, ''); assert.equal(app.previews.value['/api/media/test.png'], 'blob:preview')
  app.choose({ url: '/api/media/test.png' }, 'COVER'); assert.deepEqual(emitted.at(-1), ['choose', { url: '/api/media/test.png', role: 'COVER' }])
  cleanup(); assert.deepEqual(revoked, ['blob:preview'])
})
test('资源分组新增和改名保留编号、校验失败不丢输入、未保存分组阻止覆盖', async () => {
  const writes = []; let fail = false
  const app = component('views/AdminResourceView.vue', { notifyAdmin: () => {}, useAdminLeave: () => ({ askDiscard: () => true }), adminRequest: async (path, options) => {
    if (!options) return [{ id: 1, name: '学习', sortOrder: 0 }]
    if (fail) throw Error('分组名称已存在')
    writes.push({ path, method: options.method }); return { id: 1, ...JSON.parse(options.body) }
  } }, 'groupForm,groupError,saveGroup,editGroup,cancelGroup,dirty')
  app.groupForm.name = '学习'; await app.saveGroup(); assert.equal(writes[0].method, 'POST')
  app.editGroup({ id: 1, name: '学习', sortOrder: 0 }); app.groupForm.name = '文档'; assert.equal(app.dirty.value, true)
  app.editGroup({ id: 2, name: '其他', sortOrder: 0 }); assert.equal(app.groupForm.id, 1)
  fail = true; await app.saveGroup(); assert.equal(app.groupForm.name, '文档'); assert.match(app.groupError.value, /已存在/)
  fail = false; await app.saveGroup(); assert.deepEqual(writes[1], { path: '/resource-groups/1', method: 'PUT' })
})
test('正文媒体取自真实Markdown目标，排除代码和外部参数，支持query、fragment与引用式图片', () => {
  const url='/api/media/00000000-0000-0000-0000-000000000001.png'
  const app=component('views/AdminArticleEditorView.vue', { MarkdownIt, useRoute:()=>({query:{},params:{}}),useRouter:()=>({}),adminTarget:()=>'/admin/articles',useAdminLeave:()=>({}),notifyAdmin:()=>{} },'form,payload,localImages,imagePreviews,preview')
  app.form.coverUrl=url+'#cover'
  app.form.contentMarkdown='```text\n'+url+'\n```\n\n![外部](https://example.com/image?src='+url+')'
  assert.deepEqual(app.payload().bodyMediaUrls,[])
  app.form.contentMarkdown+='\n\n![真实]('+url+'?v=1)\n\n![引用][photo]\n\n[photo]: '+url+'#large'
  assert.deepEqual(app.payload().bodyMediaUrls,[url+'?v=1',url+'#large'])
  assert.deepEqual(app.localImages.value,[url]);app.imagePreviews.value[url]='blob:private-preview'
  assert.match(app.preview.value,/src="blob:private-preview"/)
  assert.match(app.preview.value,/src="https:\/\/example.com\/image\?src=/)
})
