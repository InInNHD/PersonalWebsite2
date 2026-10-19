# 第三轮 3A：文章布局、目录与阅读进度

日期：2026-10-18。接续已验收的第二轮B2，当前本地基线提交beef2dc。按你的安排继续第三轮本地开发。

本节是IDEA手敲指南。**2026-10-18实际源码已完成并验收通过：前端23项、生产构建、长文/手机/错误恢复/B2返回流程及真实个人文章读取通过。** 范围见[3A验收记录](V2-READING-FOUNDATION-ACCEPTANCE.md)。第三轮分为3A阅读基础、3B代码高亮/复制和图片查看、3C公开前后篇入口及整轮验收。本节只做前端，不需要改数据库或安装依赖。

## 1. 页面效果

- 文章头部显示中文栏目、标题、摘要、作者、发布/更新时间、约计阅读时长、分类标签，以及可选封面；旧文章缺少更新时间时隐藏该项。
- 桌面是正文与右侧目录两栏，目录随滚动停留在视口内；手机目录放在正文前，默认折叠。
- 自动提取h2/h3目录，点击章节定位，并给当前章节标记。所有标题层级共同生成唯一ID，重复标题不会跳到同一个位置；同一篇内容刷新后ID稳定。修改标题会相应改变该标题的链接。
- 章节写入URL hash，复制章节链接可直接打开；先等待正文渲染，再滚动，避免接口较慢时定位失败。只改hash不重新请求文章。
- 顶部显示正文阅读进度，读到文末为100%；滚过500px出现回顶按钮。它是本地视口估算，不保存已读记录或统计阅读次数。
- 保留B2从正文返回原列表的条件和位置。浏览器前进/后退优先恢复保存的位置，不能被章节hash覆盖。
- 增加详情加载失败/404提示和重试；草稿可见性仍由后端的公开接口决定。

阅读时长口径：Markdown解析后的文字、图片替代文字与代码参与估算，链接地址等标记不计入；汉字每分钟300字，其他语言每分钟200词，向上取整且至少1分钟。显示“约”，不作为准确阅读计时。

正文阅读进度以正文顶部到末尾的可滚动范围计算；短正文完整出现在视口时为100%。在正文进入视口上方参考线之前为0，正文末尾进入视口底部时为100%。图片加载、窗口大小变化可能改变估算。

安全规则：Markdown原始HTML继续关闭；目录名称用Vue文本插值显示，代码按Markdown默认规则转义。章节查找使用getElementById，不把URL hash当任意CSS选择器。未知章节或损坏编码回到文章顶部。

## 2. 文件顺序和数据流

根目录：`E:\PersonalWebsite2`。只把代码框内的内容写入文件，外围说明和三个反引号不要输入。

| 顺序 | 文件 | 操作 |
| --- | --- | --- |
| 1 | frontend/src/articleReading.js | 新建：Markdown解析、唯一锚点、目录和约计时长 |
| 2 | frontend/src/readingNavigation.js | 新建：等待正文后定位章节/恢复滚动 |
| 3 | frontend/src/components/ReadingToc.vue | 新建：桌面与手机共用的目录链接 |
| 4 | frontend/src/main.js | 完整替换：接入文章滚动，保留B2列表逻辑 |
| 5 | frontend/src/views/ArticleDetailView.vue | 完整替换：头部、正文、目录、进度、状态 |
| 6 | frontend/src/v2.css | 只在末尾追加本节CSS一次 |

不要修改listNavigation.js、文章列表或Java接口。本节调用现有公开详情接口的contentMarkdown、coverUrl、updatedAt等字段。

数据流：进入详情 → 路由登记等待 → 请求现有详情接口 → 一次Markdown解析产生正文HTML和目录 → Vue渲染 → 通知路由正文已就绪 → 恢复位置或定位章节。滚动监听按requestAnimationFrame合并，离开详情时移除监听并取消待执行帧，旧请求不能覆盖新文章。

## 3. articleReading.js

新建完整文件。

文件：`frontend/src/articleReading.js`。

```js
import MarkdownIt from 'markdown-it'

// 保持关闭原始HTML。文章文字、代码、目录名称都不能作为脚本执行。
const markdown = new MarkdownIt({ html: false, linkify: true })
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
  return {
    html: markdown.renderer.render(tokens, markdown.options, {}),
    toc,
    minutes: Math.max(1, Math.ceil(chinese / 300 + words / 200)),
  }
}
```

## 4. readingNavigation.js

新建完整文件。成功和失败均结束等待；离开或更换文章会释放旧任务。

文件：`frontend/src/readingNavigation.js`。

```js
import { nextTick } from 'vue'

// 列表的异步滚动仍由listNavigation负责；这里只等待文章正文。
let pending = null
export function prepareReading(to, from) {
  if (to.path === from.path && from.matched?.length) return
  pending?.resolve()
  pending = null
  if (to.path.startsWith('/articles/')) {
    let resolve
    const promise = new Promise(done => { resolve = done })
    pending = { path: to.path, promise, resolve }
  }
}

export async function finishReading(path) {
  await nextTick()
  if (pending?.path === path) pending.resolve()
}

export async function readingScroll(to, savedPosition, currentRoute) {
  if (pending?.path === to.path) await pending.promise
  await nextTick()
  if (currentRoute().fullPath !== to.fullPath) return false
  // 浏览器后退/前进优先恢复真实位置，不能被章节锚点覆盖。
  if (savedPosition) return savedPosition
  try {
    const id = decodeURIComponent(to.hash.slice(1))
    // 用getElementById查找，避免把任意URL内容当CSS选择器。
    const heading = id.startsWith('read-') ? document.getElementById(id) : null
    if (heading) return { el: heading, top: 28 }
  } catch { /* 无效的百分号编码按普通文章入口处理。 */ }
  return { top: 0 }
}
```

## 5. components/ReadingToc.vue

新建完整文件。所有目录链接保留B2的from查询参数。

文件：`frontend/src/components/ReadingToc.vue`。

```vue
<script setup>
import { useRoute } from 'vue-router'
defineProps({
  items: { type: Array, required: true },
  activeId: { type: String, default: '' },
})
const route = useRoute()
// 保留B2的from查询参数；目录跳转不会触发文章重新请求。
const target = id => ({ path: route.path, query: route.query, hash: `#${id}` })
</script>

<template>
  <nav aria-label="文章目录" class="reading-toc-links">
    <RouterLink v-for="item in items" :key="item.id" :to="target(item.id)"
                :class="{ 'is-child': item.level === 3 }"
                :aria-current="activeId === item.id ? 'location' : null">
      {{ item.title }}
    </RouterLink>
  </nav>
</template>
```

## 6. main.js

替换整个文件，不要只追加新的路由钩子。

文件：`frontend/src/main.js`。

```js
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { listHash, listQuery, listScroll, prepareList } from './listNavigation.js'
import { prepareReading, readingScroll } from './readingNavigation.js'
import App from './App.vue'
import ArticleListView from './views/ArticleListView.vue'
import ArticleDetailView from './views/ArticleDetailView.vue'
import './style.css'
// 新配色和布局覆盖基础样式，顺序不能反过来。
import './v2.css'
import AdminView from './views/AdminView.vue'
import ResourceView from './views/ResourceView.vue'
import ArchiveView from './views/ArchiveView.vue'
import AboutView from './views/AboutView.vue'

// 首页显示文章列表；点击文章后通过 id 打开详情。
const router = createRouter({
    history: createWebHistory(),
    // 首页等待列表渲染完成，再执行浏览器后退或正文返回的位置恢复。
    scrollBehavior(to, from, savedPosition) {
        if (to.path.startsWith('/articles/')) {
            return readingScroll(to, savedPosition, () => router.currentRoute.value)
        }
        return listScroll(to, from, savedPosition, () => router.currentRoute.value)
    },
    routes: [
        { path: '/', component: ArticleListView },
        { path: '/articles/:id', component: ArticleDetailView },
        { path: '/archive', component: ArchiveView },
        { path: '/resources', component: ResourceView },
        { path: '/about', component: AboutView },
        { path: '/admin', component: AdminView },

    ],
})

// 非法值、重复参数和未知参数统一清理；replace不额外增加浏览器历史。
router.beforeEach(to => {
    if (to.path !== '/') return
    const query = listQuery(to.query)
    const hash = listHash(to.hash)
    if (JSON.stringify(to.query) !== JSON.stringify(query) || to.hash !== hash) {
        return { path: '/', query, hash, replace: true }
    }
})
router.beforeResolve((to, from) => {
    prepareList(to, from)
    prepareReading(to, from)
})

createApp(App).use(router).mount('#app')
```

## 7. views/ArticleDetailView.vue

替换整个文件，保留返回列表及过期响应保护。

文件：`frontend/src/views/ArticleDetailView.vue`。

```vue
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listReturn } from '../listNavigation.js'
import { finishReading } from '../readingNavigation.js'
import { renderArticle } from '../articleReading.js'
import { siteProfile } from '../siteProfile.js'
import ReadingToc from '../components/ReadingToc.vue'

const route = useRoute()
const returnLocation = computed(() => listReturn(route.query.from))
const article = ref(null)
const loading = ref(false)
const error = ref('')
const body = ref(null)
const coverFailed = ref(false)
const progress = ref(0)
const activeId = ref('')
const showTop = ref(false)
const types = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
const rendered = computed(() => renderArticle(article.value?.contentMarkdown ?? ''))
const contentHtml = computed(() => rendered.value.html)
let latestRequestId = 0
let disposed = false
let frame = 0

function updateReading() {
  if (!body.value) return
  const rect = body.value.getBoundingClientRect()
  const range = rect.height - window.innerHeight + 28
  const value = range > 0 ? (28 - rect.top) / range * 100 : rect.bottom <= window.innerHeight ? 100 : 0
  progress.value = Math.round(Math.max(0, Math.min(100, value)))
  showTop.value = window.scrollY > 500
  // 选中最近经过视口上方100px参考线的h2/h3；到文末时选中最后一章。
  let current = rendered.value.toc[0]?.id ?? ''
  for (const item of rendered.value.toc) {
    const heading = document.getElementById(item.id)
    if (heading && heading.getBoundingClientRect().top <= 100) current = item.id
  }
  if (progress.value === 100) current = rendered.value.toc.at(-1)?.id ?? ''
  activeId.value = current
}

function scheduleReading() {
  if (frame || disposed) return
  frame = window.requestAnimationFrame(() => { frame = 0; updateReading() })
}

function backToTop() {
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', scheduleReading, { passive: true })
  window.addEventListener('resize', scheduleReading)
})
onBeforeUnmount(() => {
  latestRequestId += 1
  disposed = true
  window.removeEventListener('scroll', scheduleReading)
  window.removeEventListener('resize', scheduleReading)
  if (frame) window.cancelAnimationFrame(frame)
})

async function loadArticle(id) {
  const requestId = ++latestRequestId
  const path = route.path
  loading.value = true
  error.value = ''
  article.value = null
  coverFailed.value = false
  progress.value = 0
  activeId.value = ''
  showTop.value = false
  try {
    const response = await fetch(`/api/articles/${encodeURIComponent(id)}`)
    if (response.status === 404) throw new Error('文章不存在或尚未发布')
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId) return
    article.value = data
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '加载文章失败'
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
      // 成功和失败都要释放路由等待，失败时才能正确显示重试入口。
      await nextTick()
      if (!disposed && requestId === latestRequestId) {
        await finishReading(path)
        scheduleReading()
      }
    }
  }
}
watch(() => route.params.id, loadArticle, { immediate: true })
</script>

<template>
  <div class="reading-page">
    <RouterLink class="back-link" :to="returnLocation">← 返回文章列表</RouterLink>
    <div v-if="loading" class="reading-state" role="status">正在打开这篇文章…</div>
    <div v-else-if="error" class="reading-state" role="alert">
      <h1>暂时无法阅读</h1><p>{{ error }}</p>
      <button type="button" @click="loadArticle(route.params.id)">重新加载文章</button>
    </div>
    <template v-else-if="article">
      <div class="reading-progress" role="progressbar" aria-label="正文阅读进度"
           :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
        <span :style="{ width: `${progress}%` }"></span>
      </div>
      <header class="reading-header">
        <p class="reading-kicker">FIREFLY / {{ types[article.type] ?? '文章' }}</p>
        <h1>{{ article.title }}</h1>
        <p v-if="article.summary" class="reading-lead">{{ article.summary }}</p>
        <div class="reading-meta">
          <span>{{ siteProfile.nickname }}</span>
          <span>发布于 <time :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) ?? '未记录' }}</time></span>
          <span v-if="article.updatedAt">更新于 <time :datetime="article.updatedAt">{{ article.updatedAt.slice(0, 10) }}</time></span>
          <span>约 {{ rendered.minutes }} 分钟阅读</span>
        </div>
        <div class="reading-taxonomy">
          <span class="v2-badge">{{ article.categoryName ?? '未分类' }}</span>
          <span v-for="tag in article.tags ?? []" :key="tag.id" class="reading-tag">#{{ tag.name }}</span>
        </div>
        <img v-if="article.coverUrl && !coverFailed" class="reading-cover" :src="article.coverUrl"
             alt="" width="1200" height="500" referrerpolicy="no-referrer"
             @load="scheduleReading" @error="coverFailed = true; scheduleReading()">
      </header>
      <div class="reading-layout">
        <div class="reading-main">
          <!-- 手机目录默认折叠，长目录不会挡在正文前；原生details支持键盘。 -->
          <details v-if="rendered.toc.length" class="reading-mobile-toc">
            <summary>文章目录 · {{ rendered.toc.length }} 个章节</summary>
            <ReadingToc :items="rendered.toc" :active-id="activeId" />
          </details>
          <!-- IDs和正文来自同一次Markdown解析；没有原始HTML执行入口。 -->
          <!-- 图片加载会改变正文高度，捕获load/error后重新估算进度。 -->
          <div ref="body" class="markdown-body reading-body" @load.capture="scheduleReading"
               @error.capture="scheduleReading" v-html="contentHtml"></div>
          <footer class="reading-end">
            <span>这篇记录，到这里告一段落。</span>
            <RouterLink :to="returnLocation">返回列表，继续探索 →</RouterLink>
          </footer>
        </div>
        <aside v-if="rendered.toc.length" class="reading-toc">
          <p class="v2-eyebrow">ON THIS PAGE</p><h2>本文目录</h2>
          <ReadingToc :items="rendered.toc" :active-id="activeId" />
          <p class="reading-toc-progress">正文阅读进度 {{ progress }}%</p>
        </aside>
      </div>
      <button v-if="showTop" type="button" class="reading-top" @click="backToTop" aria-label="返回文章顶部">↑ 回顶</button>
    </template>
  </div>
</template>
```

## 8. v2.css

只在现有文件末尾追加下列CSS一次。不要覆盖首页、管理端及B1/B2原有样式。

文件：`frontend/src/v2.css`。

```css
/* 第三轮3A：文章阅读布局。只影响reading-前缀，后台预览沿用原样式。 */
.reading-page { min-width: 0; padding-bottom: 48px; }
.reading-header { max-width: 900px; padding: 14px 0 32px; margin-bottom: 36px; border-bottom: 1px solid var(--color-border); }
.reading-kicker { font-size: 11px; letter-spacing: 2px; color: var(--color-accent); margin: 0 0 20px; }
.reading-header h1 { margin: 0 0 22px; font-size: clamp(30px, 4vw, 46px); letter-spacing: -.03em; line-height: 1.35; }
.reading-lead { color: var(--color-muted); font-size: 17px; line-height: 1.9; max-width: 65ch; }
.reading-meta { display: flex; gap: 8px 20px; flex-wrap: wrap; color: var(--color-muted); font-size: 12px; margin: 24px 0 16px; }
.reading-taxonomy { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; font-size: 12px; }
.reading-tag { color: var(--color-muted); overflow-wrap: anywhere; }
.reading-cover { display: block; width: 100%; height: auto; aspect-ratio: 12 / 5; max-height: 380px; object-fit: cover; border-radius: 14px; margin-top: 28px; }
.reading-layout { display: grid; grid-template-columns: minmax(0, 1fr) 230px; gap: 64px; align-items: start; }
.reading-main { min-width: 0; max-width: 820px; }
.reading-body { max-width: none; font-size: 16px; line-height: 1.95; }
.reading-body > :first-child { margin-top: 0; }
.reading-body h2 { font-size: 25px; margin: 2em 0 .8em; padding-top: 12px; }
.reading-body h3 { font-size: 20px; margin: 1.7em 0 .7em; }
.reading-body h1, .reading-body h2, .reading-body h3, .reading-body h4 { scroll-margin-top: 28px; }
.reading-body p { margin: 1.1em 0; }
.reading-body li + li { margin-top: 8px; }
.reading-body pre { font-size: 14px; }
.reading-toc { position: sticky; top: 28px; max-height: calc(100vh - 56px); overflow-y: auto; min-width: 0; padding-left: 20px; border-left: 1px solid var(--color-border); }
.reading-toc h2 { font-size: 15px; margin: 12px 0 16px; }
.reading-toc-links { display: grid; gap: 3px; }
.reading-toc-links a { display: block; min-height: 36px; padding: 8px 10px; border-left: 2px solid transparent; color: var(--color-muted); font-size: 13px; overflow-wrap: anywhere; line-height: 1.6; }
.reading-toc-links a.is-child { padding-left: 24px; font-size: 12px; }
.reading-toc-links a[aria-current="location"] { color: var(--color-accent); border-color: var(--color-accent); background: var(--color-accent-soft); text-decoration: none; }
.reading-toc-progress { font-size: 11px; color: var(--color-muted); padding-top: 16px; border-top: 1px solid var(--color-border); margin-top: 20px; }
.reading-mobile-toc { display: none; }
.reading-end { margin-top: 48px; border-top: 1px solid var(--color-border); padding-top: 24px; display: grid; gap: 12px; color: var(--color-muted); font-size: 13px; }
.reading-end a { display: inline-flex; align-items: center; min-height: 44px; }
.reading-progress { position: fixed; inset: 0 0 auto; height: 3px; z-index: 30; background: transparent; pointer-events: none; }
.reading-progress span { display: block; height: 100%; background: var(--color-accent); }
.reading-top { position: fixed; right: 24px; bottom: 24px; z-index: 20; box-shadow: 0 8px 30px #0005; color: var(--color-accent); font-size: 12px; }
.reading-state { padding: 40px 24px; border: 1px dashed var(--color-border); border-radius: 14px; color: var(--color-muted); }
.reading-state h1 { font-size: 24px; }
@media (max-width: 1000px) {
  .reading-layout { gap: 32px; grid-template-columns: minmax(0, 1fr) 190px; }
}
@media (max-width: 850px) {
  .reading-layout { display: block; }
  .reading-toc { display: none; }
  .reading-mobile-toc { display: block; margin-bottom: 28px; padding: 12px 16px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 12px; }
  .reading-mobile-toc summary { min-height: 44px; cursor: pointer; padding: 10px 0; color: var(--color-accent); font-size: 13px; }
  .reading-mobile-toc .reading-toc-links { max-height: 50vh; overflow-y: auto; margin: 10px 0; }
  .reading-toc-links a { min-height: 44px; }
}
@media (max-width: 600px) {
  .reading-header { padding-top: 4px; margin-bottom: 28px; }
  .reading-header h1 { font-size: 29px; }
  .reading-lead { font-size: 15px; }
  .reading-meta { gap: 10px 16px; }
  .reading-body { font-size: 15px; line-height: 1.95; }
  .reading-body h2 { font-size: 23px; }
  .reading-body h3 { font-size: 19px; }
  .reading-top { right: 16px; bottom: 16px; }
}
```

## 9. 启动与检查方法

写完六个文件后，保存全部，按原配置启动后端和前端，打开一篇已发布文章。不要再次执行005或其他数据库脚本。

| 操作 | 预期 |
| --- | --- |
| 打开旧文章 | 中文元信息正确，未知更新时间隐藏，摘要/封面为空时不留假内容 |
| 打开包含h2/h3、重复标题的长文 | 桌面显示目录，重复章节各有不同hash且定位正确 |
| 点击目录，再复制网址重新打开 | 正文加载完成后到达对应章节，位置距顶部约28px |
| 浏览器后退/前进，以及正文返回列表 | 文章滚动位置和B2的列表条件/位置正常恢复 |
| 滚动到后半篇及末尾 | 目录高亮变化、进度保持0～100且文末100，回顶按钮可用 |
| 手机打开目录并点章节 | 默认折叠，展开后目录可滚动，点击可定位 |
| 缺少h2/h3的短文章 | 不显示空目录，仍可阅读和返回 |
| 代码长行、12列表格、宽图片 | 正文阅读区域可用，代码和表格局部滚动，整页不横向溢出 |
| 不存在/未发布编号或后端断开 | 显示明确错误，重试后可恢复；不显示上篇正文 |

个人笔记不足以覆盖以上长文时，不需要往个人库填占位内容。助手会用独立模拟数据验证，并在你完成后对实际源码复查。

## 10. 测试与下一步

测试由助手完成。2026-10-18预检通过：7项读取本指南代码的专项测试，覆盖重复锚点、HTML/代码转义、危险协议、阅读时长、旧响应/404/重试/卸载、异步滚动与保存位置优先、进度和监听清理；候选副本另有16项既有前端回归通过。旧详情测试的环境已由新的专项检查替代，并未把旧环境不兼容记作业务缺陷。前端生产构建通过，共66个模块。本轮未运行后端测试，接口与数据库未变。

浏览器使用带重复h2/h3、长代码、12列表格和图片的隔离长文，详情延迟300ms。重复章节分别定位、章节链接刷新直达、目录高亮、文末100%、回顶、手机目录、B2第二页返回、短文无目录、404与503重试均通过。320/390/768/1440px四档无整页横向溢出；代码与表格局部滚动。预检发现正文图片加载后进度未立即重算，已在本节代码补充load/error捕获和重新估算，并复测。

测试命令为 `node --test docs/checks/v2-reading-guide.test.mjs`。模拟长文、浏览器检查JSON和截图保存在Git忽略的 `artifacts/v2-reading-preflight/`，没有连接个人数据库；预览服务在预检后关闭。实际业务源码未写入3A改动。

2026-10-18手敲后的实际验收已完成：六处代码与指南一致，23项实际源码测试及构建通过；使用实际产物完成长文目录/定位/进度/回顶、四档宽度、错误恢复和B2返回检查，并连接个人库验证正文读取、草稿隔离与筛选返回，五表校验值未变化。指南预检与实际验收分别记录，下一节3B。

完成本节后继续3B：代码高亮、语言标签、复制按钮及反馈、正文图片查看与关闭。然后3C实现仅公开文章的前后篇入口，并完成第三轮整体效果验收；整轮通过后再评估新的Release节点。
