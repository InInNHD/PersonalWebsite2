# 第三轮 3B：代码高亮、复制与正文图片查看

日期：2026-10-18。接续已验收的3A。**这是IDEA手敲指南。2026-10-19实际源码已完成并验收通过：28项前端测试、生产构建、代码与图片交互、四档宽度及3A/B2回归通过。** 详见[3B实际验收记录](V2-READING-MEDIA-ACCEPTANCE.md)。下文保留实现步骤供查阅。

## 1. 本节效果与范围

- 围栏代码块、缩进代码块有统一工具栏：左侧语言，右侧复制。Java/JavaScript/TypeScript/HTML(XML)/CSS/SQL/JSON/Bash/Python按需高亮；支持库内别名，如js、ts、html、sh、py。没有语言或未知语言显示转义后的纯文本，不猜测语言。
- 复制保留缩进、特殊字符和结尾换行，不带语言名、行号或按钮文字；成功显示“已复制”，失败显示“复制失败”，可手动选择代码复制。
- 独立正文图片可通过点击、Tab+Enter/空格打开查看；原生dialog限制焦点，支持关闭按钮、Escape及点击图片面板外背景。关闭后恢复原位置、焦点和滚动状态。
- 已放入Markdown链接的图片继续作为链接；封面不加入本节查看器。失效图片不给空白弹窗，弹窗内加载失败也显示提示。
- 沿用3A的唯一章节ID、目录、时长、进度、回顶及B2的筛选/返回来源。Java接口、数据库不变。

只新增highlight.js一个依赖。图片查看和复制分别使用浏览器原生dialog和Clipboard API；不新增灯箱、复制或Markdown插件。**Clipboard写入在localhost和HTTPS安全上下文中工作，也可能因浏览器权限失败；失败时页面给出手动复制提示，不显示假成功。** [Clipboard说明](https://developer.mozilla.org/en-US/docs/Web/API/Clipboard/writeText)、[dialog说明](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/dialog)、[highlight.js API](https://highlightjs.readthedocs.io/en/latest/api.html)。

## 2. 文件顺序和数据流

项目根目录：`E:\PersonalWebsite2`。代码框外的文件路径、说明、三个反引号不要输入源码。

| 顺序 | 文件 | 操作 |
| --- | --- | --- |
| 1 | frontend/package.json、package-lock.json | 在frontend执行一次安装命令，由npm更新，勿手改lock文件 |
| 2 | frontend/src/articleReading.js | 完整替换下面代码，保留3A算法并增加代码块和图片渲染 |
| 3 | frontend/src/components/ReadingContent.vue | 新建完整文件，处理复制、图片dialog及清理 |
| 4 | frontend/src/views/ArticleDetailView.vue | 只改两处：一个import和正文内部标签 |
| 5 | frontend/src/v2.css | 末尾追加一次CSS |

Markdown → 同一次解析生成HTML/目录/原始代码数组 → ReadingContent展示HTML → 通过工具栏数字索引复制原始代码；正文图片按钮打开dialog。原始HTML仍关闭，语言标签和代码均转义。复制异步结果携带版本号，换文或离开后丢弃；切换正文/卸载时关闭图片、恢复滚动和清理提示定时器。

## 3. 安装依赖

在IDEA终端执行（如果已经在frontend目录，不要重复cd）：

```powershell
cd E:\PersonalWebsite2\frontend
npm.cmd install highlight.js@11.11.1 --save-exact
```

安装后dependencies应多一项`"highlight.js": "11.11.1"`；package-lock.json同时更新。此处固定已预检的版本，不要求更新现有Vue/Vite。

## 4. 替换 articleReading.js

文件：`frontend/src/articleReading.js`。

从第一行到最后一行完整替换：

```js
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'

// 只加载当前写作常用的语言，不自动猜测语言，也不引入整套语言包。
for (const [name, grammar] of Object.entries({ java, javascript, typescript, xml, css, sql, json, bash, python })) {
  hljs.registerLanguage(name, grammar)
}


// 保持关闭原始HTML。文章文字、代码、目录名称都不能作为脚本执行。
const markdown = new MarkdownIt({ html: false, linkify: true })

// fence 和缩进代码块共用工具栏。代码原文放入返回数据，不写进HTML属性。
function renderCode(tokens, index, options, env) {
  const token = tokens[index]
  const requested = token.info.trim().split(/\s+/)[0].toLowerCase()
  const language = hljs.getLanguage(requested) ? requested : ''
  const label = markdown.utils.escapeHtml((requested || 'text').slice(0, 32))
  const number = env.codeBlocks.push(token.content) - 1
  // 未知语言和解析失败都回退为转义后的纯文本，正文仍然可读。
  let html = markdown.utils.escapeHtml(token.content)
  if (language) {
    try { html = hljs.highlight(token.content, { language, ignoreIllegals: true }).value }
    catch { /* 保留上面的纯文本结果。 */ }
  }
  return `<section class="reading-code" aria-label="代码块"><div class="reading-code-bar">`
    + `<span>${label}</span><button type="button" data-code-index="${number}" aria-label="复制第${number + 1}段代码">复制</button>`
    + `</div><pre><code class="hljs">${html}</code></pre></section>\n`
}
markdown.renderer.rules.fence = renderCode
markdown.renderer.rules.code_block = renderCode

const defaultImage = markdown.renderer.rules.image
markdown.renderer.rules.image = (tokens, index, options, env, renderer) => {
  // 已有链接的图片保留原链接行为，避免生成嵌套交互控件。
  let linkDepth = 0
  for (let i = 0; i < index; i += 1) {
    if (tokens[i].type === 'link_open') linkDepth += 1
    if (tokens[i].type === 'link_close') linkDepth -= 1
  }
  tokens[index].attrSet('referrerpolicy', 'no-referrer')
  const image = defaultImage(tokens, index, options, env, renderer)
  if (linkDepth > 0) return image
  const alt = renderer.renderInlineAsText(tokens[index].children ?? [], options, env)
  const label = markdown.utils.escapeHtml(`查看大图：${alt || '正文图片'}`)
  return `<button type="button" class="reading-image" data-reading-image aria-label="${label}">${image}</button>`
}

function inlineText(token) {
    return (token.children ?? []).map(child => {
        if (['text', 'code_inline', 'image'].includes(child.type)) return child.content
        if (['softbreak', 'hardbreak'].includes(child.type)) return ' '
        return ''
    }).join('')
}

export function renderArticle(source = '') {
    const tokens = markdown.parse(source, {})
    const used = new Set()
    const toc = []
    for (let index = 0; index < tokens.length; index += 1) {
        if (tokens[index].type !== 'heading_open') continue
        const title = inlineText(tokens[index + 1]) || '未命名章节'
        // 标题文字决定基础ID；重复标题逐个加后缀，所有层级共同去重。
        // read-前缀避免与站点外框和表单ID冲突。
        const slug = title.normalize('NFKC').toLowerCase()
            .replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '').slice(0, 64)
        const base = `read-${slug || 'section'}`
        let id = base
        let suffix = 2
        while (used.has(id)) id = `${base}-${suffix++}`
        used.add(id)
        tokens[index].attrSet('id', id)
        const level = Number(tokens[index].tag.slice(1))
        if (level === 2 || level === 3) toc.push({ id, title, level })
    }

    // 只统计可读文字和代码，排除Markdown链接地址等标记。
    // 这是估算：中文每分钟300字，其他语言每分钟200词，至少1分钟。
    const text = tokens.map(token => token.type === 'inline' ? inlineText(token)
        : ['fence', 'code_block'].includes(token.type) ? token.content : '').join('\n')
    const chinese = (text.match(/\p{Script=Han}/gu) ?? []).length
    const words = (text.replace(/\p{Script=Han}/gu, ' ').match(/[\p{L}\p{N}]+/gu) ?? []).length
    const env = { codeBlocks: [] }
    const html = markdown.renderer.render(tokens, markdown.options, env)
    return {
        html,
        codeBlocks: env.codeBlocks,
        toc,
        minutes: Math.max(1, Math.ceil(chinese / 300 + words / 200)),
    }
}
```

## 5. 新建 ReadingContent.vue

文件：`frontend/src/components/ReadingContent.vue`。

```vue
<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  html: { type: String, required: true },
  codeBlocks: { type: Array, default: () => [] },
})
const content = ref(null)
const viewer = ref(null)
const image = ref(null)
const imageFailed = ref(false)
const status = ref('')
let revision = 0
let copying = false
let copiedButton = null
let timer = 0
let previousOverflow = null

function resetCopy() {
  window.clearTimeout(timer)
  if (copiedButton?.isConnected) copiedButton.textContent = '复制'
  copiedButton = null
}

function releaseScroll() {
  if (previousOverflow === null) return
  document.documentElement.style.overflow = previousOverflow
  previousOverflow = null
}

function closeImage() {
  if (viewer.value?.open) viewer.value.close()
  image.value = null
  releaseScroll()
}

async function copyCode(button) {
  // 同一时刻只处理一次点击，离开文章后的异步结果不更新新正文。
  if (copying) return
  const index = Number(button.dataset.codeIndex)
  if (!Number.isInteger(index) || index < 0 || index >= props.codeBlocks.length) return
  const current = revision
  copying = true
  resetCopy()
  status.value = ''
  let message = '已复制'
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
    await navigator.clipboard.writeText(props.codeBlocks[index])
  } catch {
    message = '复制失败'
  } finally {
    if (current === revision) copying = false
  }
  if (current !== revision || !content.value?.contains(button)) return
  button.textContent = message
  copiedButton = button
  status.value = message === '已复制' ? '代码已复制。' : '复制失败，请选中代码后手动复制。'
  timer = window.setTimeout(resetCopy, 2400)
}

function onContentClick(event) {
  const button = event.target.closest?.('button')
  if (!button || !content.value?.contains(button)) return
  if (button.hasAttribute('data-code-index')) {
    void copyCode(button)
    return
  }
  if (!button.hasAttribute('data-reading-image')) return
  const target = button.querySelector('img')
  if (!target?.complete || !target.naturalWidth) {
    status.value = '图片尚未加载成功，请稍后再试。'
    return
  }
  image.value = { src: target.currentSrc || target.src, alt: target.alt || '正文图片' }
  imageFailed.value = false
  // showModal提供焦点限制、Escape关闭和关闭后返回触发按钮。
  viewer.value.showModal()
  previousOverflow = document.documentElement.style.overflow
  document.documentElement.style.overflow = 'hidden'
}

function resetContent() {
  revision += 1
  copying = false
  resetCopy()
  status.value = ''
  closeImage()
}

watch(() => props.html, resetContent)
onBeforeUnmount(resetContent)
</script>

<template>
  <div ref="content" @click="onContentClick" v-html="html"></div>
  <p class="reading-media-status" role="status" aria-live="polite">{{ status }}</p>
  <dialog ref="viewer" class="reading-viewer" aria-label="正文图片查看"
          @cancel.prevent="closeImage" @close="releaseScroll"
          @click="event => { if (event.target === viewer) closeImage() }">
    <div class="reading-viewer-panel">
      <header>
        <span>图片查看</span>
        <button type="button" autofocus @click="closeImage" aria-label="关闭图片查看">关闭 ×</button>
      </header>
      <template v-if="image">
        <p v-if="imageFailed" role="alert">图片加载失败，请关闭后重试。</p>
        <img v-else :src="image.src" :alt="image.alt" referrerpolicy="no-referrer" @error="imageFailed = true">
        <p class="reading-viewer-caption">{{ image.alt }}</p>
      </template>
    </div>
  </dialog>
</template>
```

## 6. ArticleDetailView.vue 只改两处

文件：`frontend/src/views/ArticleDetailView.vue`。

### 6.1 增加import

找到已有的：

```js
import ReadingToc from '../components/ReadingToc.vue'
```

在它的下一行加入：

```js
import ReadingContent from '../components/ReadingContent.vue'
```

### 6.2 替换正文div

在template内找到下面这两行（在手机目录details之后、reading-end footer之前）：

```vue
          <div ref="body" class="markdown-body reading-body" @load.capture="scheduleReading"
               @error.capture="scheduleReading" v-html="contentHtml"></div>
```

将整个div替换为：

```vue
          <div ref="body" class="markdown-body reading-body" @load.capture="scheduleReading"
               @error.capture="scheduleReading">
            <ReadingContent :html="contentHtml" :code-blocks="rendered.codeBlocks" />
          </div>
```

`ref="body"`继续指向外层真实DOM，3A测量高度的代码不用改；load/error捕获仍可感知正文图片高度变化。不要留下原来的v-html，也不要把ref移到Vue组件上。

## 7. v2.css末尾追加

文件：`frontend/src/v2.css`。

保留已有CSS，末尾另起一行追加下面片段一次：

```css
/* 第三轮3B：代码工具栏与图片查看。范围限定在文章阅读页。 */
.reading-code { min-width: 0; margin: 24px 0; border: 1px solid var(--color-border); border-radius: 10px; overflow: hidden; background: var(--color-code); }
.reading-code-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 4px 12px 4px 16px; background: var(--color-surface-raised); font: 12px Consolas, monospace; }
.reading-code-bar span { min-width: 0; overflow-wrap: anywhere; color: var(--color-muted); }
.reading-code-bar button { flex: none; min-height: 44px; padding: 6px 12px; background: transparent; color: var(--color-accent); font: inherit; }
.reading-body .reading-code pre { margin: 0; border: 0; border-radius: 0; padding: 18px; }
.reading-body .reading-code code { white-space: pre; }
/* 自定义一小组高亮颜色，避免外部主题覆盖站点背景与字体。 */
.reading-body .hljs-keyword, .reading-body .hljs-selector-tag, .reading-body .hljs-literal { color: #d9a9e8; }
.reading-body .hljs-string, .reading-body .hljs-attr, .reading-body .hljs-template-tag { color: #b7d8bd; }
.reading-body .hljs-number, .reading-body .hljs-symbol { color: #efc18b; }
.reading-body .hljs-title, .reading-body .hljs-built_in, .reading-body .hljs-type { color: #9dcde3; }
.reading-body .hljs-comment, .reading-body .hljs-quote { color: #97aaa2; font-style: italic; }
.reading-body .hljs-meta, .reading-body .hljs-tag, .reading-body .hljs-name { color: #e3b69d; }
.reading-body .reading-image { display: block; max-width: 100%; padding: 0; margin: 12px 0; border: 0; border-radius: 8px; background: transparent; cursor: zoom-in; }
.reading-body .reading-image img { margin: 0; }
.reading-image:focus-visible, .reading-code-bar button:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 3px; }
.reading-media-status { min-height: 1.5em; font-size: 12px; color: var(--color-muted); }
.reading-viewer { max-width: none; width: 100vw; height: 100dvh; max-height: none; margin: 0; padding: 20px; border: 0; background: transparent; color: var(--color-text); }
.reading-viewer[open] { display: grid; place-items: center; }
.reading-viewer::backdrop { background: #050b0ee8; }
.reading-viewer-panel { min-width: 0; max-width: min(1200px, 100%); max-height: 100%; padding: 12px 18px; border: 1px solid var(--color-border); border-radius: 12px; overflow: auto; background: var(--color-surface); }
.reading-viewer-panel header { display: flex; align-items: center; justify-content: space-between; gap: 20px; font-size: 13px; }
.reading-viewer-panel header button { min-height: 44px; }
.reading-viewer-panel img { display: block; width: auto; max-width: 100%; height: auto; max-height: 72dvh; margin: 12px auto; object-fit: contain; }
.reading-viewer-caption { margin: 12px 0 4px; font-size: 12px; color: var(--color-muted); overflow-wrap: anywhere; }
@media (max-width: 600px) {
  .reading-viewer { padding: 12px; }
  .reading-viewer-panel { padding: 10px; }
  .reading-body .reading-code pre { font-size: 13px; padding: 14px; }
}
```

## 8. 本地运行与验收

保持后端按原方式运行。在frontend终端运行`npm.cmd run dev`；浏览器使用终端实际显示的地址。生产构建命令为`npm.cmd run build`。**本节不执行SQL。**

可以先使用一篇测试草稿，写入下列Markdown，确认预览后按你的需要发布；不必修改个人现有文章。3B只接入访客详情页，后台编辑预览继续原样。

````markdown
## 代码测试

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("<script>不会执行</script>");
    }
}
```

```js
const message = '你好';
console.log(message);
```

```sql
SELECT id, title FROM article WHERE status = 'PUBLISHED';
```

```unknown
<img src=x onerror=alert(1)>
```

    这是四个空格缩进的代码块

## 图片测试

![站点标识](/favicon.svg)

[![带链接的图片](/favicon.svg)](/)

![失效图片](/not-exist-reading-test.png)
````

示例使用项目现有的`frontend/public/favicon.svg`。也可用你已有的可读图片URL替换，不要求上传功能。

| 检查 | 通过标准 |
| --- | --- |
| Java/js/sql | 语言标签正确，关键字/字符串等有颜色；代码不执行 |
| 未标语言/unknown/缩进代码 | 原样文字可读，有复制按钮，无高亮报错 |
| 复制原文 | 粘贴到空白文本中，空格、引号、尖括号、换行一致；不带标签/按钮文字 |
| 复制反馈 | 成功显示已复制；权限拒绝或API不可用显示失败提示，可手动选择复制 |
| 普通图片 | 点击和Tab+Enter/空格都可打开，关闭按钮/Escape/背景均可关闭 |
| 焦点和滚动 | 弹窗打开时焦点限制在弹窗，关闭回触发按钮且保留原位置；离开页面不残留滚动锁 |
| 带链接图片/坏图片 | 带链接图片沿原链接导航；坏图片提示未加载，不打开空白弹窗 |
| 异步与清理 | 复制中换文、离开文章、弹窗状态切换不把旧提示写入新正文 |
| 窄屏 | 320/390/768px与桌面可读，长代码只在代码框内横向滚动，弹窗不撑宽页面 |
| 3A/B2回归 | 章节直达、进度、回顶、返回原筛选及列表位置正常 |

## 9. 预检与下一步

2026-10-18指南预检完成：5项3B专项测试、候选副本23项既有回归和Vite生产构建通过。浏览器验证了高亮/未知语言纯文本/复制成功反馈、图片点击和键盘打开、关闭按钮/Escape/背景关闭、焦点限制与恢复、滚动恢复、坏图片提示、章节链接及B2第2页返回。320/390/768/1440px无整页横向溢出，390px弹窗正常。复制原文与失败/旧结果/卸载清理由专项测试核对。浏览器使用模拟公开接口；未连接数据库，未安装到实际frontend，也未修改业务源码。证据在Git忽略的artifacts/v2-reading-media-preflight，临时页面和服务已关闭。

这是指南候选副本的预检，不能替代你手敲后的实际验收。测试由助手维护和执行，你不用手敲tests目录；现有阅读测试的Markdown注入方式需要适配新依赖，实际验收时由助手处理。

2026-10-19用户手敲后实际验收已完成：四处代码与指南一致，安装及锁文件正确；实际28项测试、构建、复制反馈/图片交互/四档宽度/离页清理/3A与B2回归通过，业务源码无需修正。助手已维护实际源码测试和验收记录，下一节3C。

3B之后是3C：仅公开文章的前后篇入口，再进行第三轮整体效果验收。当前不创建Release，也不部署。
