import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
import test from 'node:test'

// 预检设置READING_MEDIA_ROOT指向隔离frontend；用户安装依赖后默认使用实际frontend。
const root = resolve(process.env.READING_MEDIA_ROOT || 'frontend')
const require = createRequire(resolve(root, 'package.json'))
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const { ref } = require('vue')
const guide = readFileSync(new URL('../15-v2-reading-media.md', import.meta.url), 'utf8').replace(/\r\n/g, '\n')
function code(file) {
  return guide.split(`文件：\`frontend/src/${file}\`。`)[1].match(/```(?:js|vue)\n([\s\S]*?)\n```/)[1]
}
const source = code('articleReading.js')
const imports = [...source.matchAll(/^import (\w+) from '([^']+)'/gm)]
const values = imports.map(([, , name]) => { const value = require(name); return value.default ?? value })
const render = new Function(...imports.map(([, name]) => name), source.replace(/^import[^\n]*\n/gm, '').replaceAll('export ', '') + ';return renderArticle')(...values)
function media(clipboard = { writeText: async () => {} }) {
  const timers = new Map(), leaves = [], watches = []
  const props = { html: 'initial', codeBlocks: ['  <tag>\n\t中文\n', 'second\n'] }
  const doc = { documentElement: { style: { overflow: 'clip' } } }
  const window = { clearTimeout: id => timers.delete(id), setTimeout: fn => { timers.set(1, fn); return 1 } }
  const script = parse(code('components/ReadingContent.vue')).descriptor.scriptSetup.content.replace(/^import[^\n]*\n/gm, '')
  const bindings = { defineProps: () => props, ref, onBeforeUnmount: fn => leaves.push(fn), watch: (_, fn) => watches.push(fn), window, document: doc, navigator: { clipboard } }
  const app = new Function(...Object.keys(bindings), script + ';return {content,viewer,image,imageFailed,status,copyCode,onContentClick,closeImage}')(...Object.values(bindings))
  const buttons = []
  app.content.value = { contains: node => buttons.includes(node) }
  app.viewer.value = { open: false, showModal() { this.open = true }, close() { this.open = false } }
  function button(index = 0, img = null) {
    const node = { dataset: { codeIndex: String(index) }, isConnected: true, textContent: '复制', hasAttribute: name => name === (img ? 'data-reading-image' : 'data-code-index'), querySelector: () => img }
    buttons.push(node)
    return node
  }
  return { ...app, props, doc, timers, button, change: () => watches.forEach(fn => fn()), leave: () => leaves.forEach(fn => fn()) }
}

test('3B组件可编译；高亮与未知语言回退，原文数组保留缩进和结尾换行', () => {
  const { descriptor, errors } = parse(code('components/ReadingContent.vue'))
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'media' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, id: 'media', filename: 'ReadingContent.vue', compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
  const result = render('## 重复\n\n```java\npublic class Hello {}\n```\n\n```js\nconst text = "<img>";\n```\n\n```unknown\n<script>alert(1)</script>\n```\n\n    缩进代码\n\n## 重复')
  assert.match(result.html, /hljs-keyword/)
  assert.match(result.html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
  assert.doesNotMatch(result.html, /<script|<img/)
  assert.deepEqual(result.codeBlocks, ['public class Hello {}\n', 'const text = "<img>";\n', '<script>alert(1)</script>\n', '缩进代码\n'])
  assert.deepEqual(result.toc.map(item => item.id), ['read-重复', 'read-重复-2'])
  const hljs = require('highlight.js/lib/core'), old = hljs.highlight
  try { hljs.highlight = () => { throw Error('grammar failed') }; assert.match(render('```java\n<raw>\n```').html, /&lt;raw&gt;/) }
  finally { hljs.highlight = old }
  assert.doesNotMatch(render('```\"onclick=alert(1)\ntext\n```').html, /<span>"/)
})

test('普通图片有可键盘操作的按钮，链接图片不嵌套按钮，危险图片协议被拒绝', () => {
  const result = render('![普通"图片](/image.svg)\n\n[![链接图片](/linked.svg)](/)\n\n![危险](javascript:alert(1))')
  assert.equal((result.html.match(/data-reading-image/g) ?? []).length, 1)
  assert.match(result.html, /aria-label="查看大图：普通&quot;图片"/)
  assert.match(result.html, /<a href="\/">\s*<img/)
  assert.doesNotMatch(result.html, /src="javascript:/)
})

test('复制成功保留原文，定时器恢复文字；拒绝/不可用显示失败，索引越界忽略', async () => {
  let copied
  const app = media({ writeText: async value => { copied = value } }), button = app.button()
  await app.copyCode(button)
  assert.equal(copied, app.props.codeBlocks[0]); assert.equal(button.textContent, '已复制')
  assert.match(app.status.value, /代码已复制/)
  app.timers.get(1)(); assert.equal(button.textContent, '复制')
  for (const clipboard of [null, { writeText: async () => { throw Error('denied') } }]) {
    const failure = media(clipboard), target = failure.button()
    await failure.copyCode(target)
    assert.equal(target.textContent, '复制失败'); assert.match(failure.status.value, /手动复制/)
  }
  copied = null; await app.copyCode(app.button(99)); assert.equal(copied, null)
})

test('复制中的旧结果在正文切换/卸载后失效，定时器和滚动锁被清理', async () => {
  let finish
  const app = media({ writeText: () => new Promise(done => { finish = done }) }), button = app.button()
  const pending = app.copyCode(button)
  app.change(); finish(); await pending
  assert.equal(button.textContent, '复制'); assert.equal(app.status.value, '')
  assert.equal(app.timers.size, 0)
  app.onContentClick({ target: { closest: () => app.button(0, { complete: true, naturalWidth: 800, currentSrc: '/img.svg', alt: '原图' }) } })
  assert.equal(app.viewer.value.open, true); assert.equal(app.doc.documentElement.style.overflow, 'hidden')
  app.leave(); assert.equal(app.viewer.value.open, false); assert.equal(app.doc.documentElement.style.overflow, 'clip')
})

test('图片未加载不弹窗，正常图片沿用当前src，关闭恢复原滚动样式', () => {
  const app = media(), failed = app.button(0, { complete: false, naturalWidth: 0 })
  app.onContentClick({ target: { closest: () => failed } })
  assert.equal(app.viewer.value.open, false); assert.match(app.status.value, /尚未加载成功/)
  const loaded = app.button(0, { complete: true, naturalWidth: 800, currentSrc: '/actual.svg', src: '/fallback.svg', alt: '示例' })
  app.onContentClick({ target: { closest: () => loaded } })
  assert.equal(app.image.value.src, '/actual.svg'); assert.equal(app.imageFailed.value, false)
  app.closeImage(); assert.equal(app.viewer.value.open, false); assert.equal(app.image.value, null)
  assert.equal(app.doc.documentElement.style.overflow, 'clip')
})
