<script setup>
import PageState from '../components/PageState.vue'
import { usePageMeta } from '../pageMeta.js'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listReturn } from '../listNavigation.js'
import { finishReading } from '../readingNavigation.js'
import { renderArticle } from '../articleReading.js'
import { siteProfile } from '../siteProfile.js'
import ReadingToc from '../components/ReadingToc.vue'
import ReadingContent from '../components/ReadingContent.vue'
import CollectionTrail from '../components/CollectionTrail.vue'
import ArticleNeighbors from '../components/ArticleNeighbors.vue'
import ReadingTools from '../components/ReadingTools.vue'


const route = useRoute()
const returnLocation = computed(() => listReturn(route.query.from))
const article = ref(null)
const loading = ref(false)
const error = ref('')
const notFound = ref(false)
const body = ref(null)
const coverFailed = ref(false)
const progress = ref(0)
const activeId = ref('')
const showTop = ref(false)
const readingSize = ref(16), focusedReading = ref(false)
let layoutChange = 0
async function changeReading(settings) {
  const change = ++layoutChange
  // 以屏幕中正在读的段落为锚点，改字号或布局后保持它的位置。
  const anchor = [...(body.value?.querySelectorAll('h2,h3,p,pre,li') ?? [])]
    .find(node => node.getBoundingClientRect().bottom > 140)
  const top = anchor?.getBoundingClientRect().top
  readingSize.value = settings.size
  focusedReading.value = settings.focused
  await nextTick()
  if (disposed || change !== layoutChange) return
  if (anchor?.isConnected) window.scrollTo({top: window.scrollY + anchor.getBoundingClientRect().top - top, behavior: 'instant'})
  scheduleReading()
}
const types = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
usePageMeta(() => notFound.value ? '文章不存在' : article.value?.title || '文章阅读', () => article.value?.summary || '阅读公开笔记、心得与生活记录。', () => notFound.value)
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
  // 参考线随吸顶工具条换行而下移，与目录跳转的预留高度一致。
  const toolsHeight = document.querySelector?.('.reading-tools')?.getBoundingClientRect().height ?? 0
  const headingLine = Math.max(100, toolsHeight + 25)
  let current = rendered.value.toc[0]?.id ?? ''
  for (const item of rendered.value.toc) {
    const heading = document.getElementById(item.id)
    if (heading && heading.getBoundingClientRect().top <= headingLine) current = item.id
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
  layoutChange += 1
  focusedReading.value = false
  const path = route.path
  loading.value = true
  error.value = ''
  notFound.value = false
  article.value = null
  coverFailed.value = false
  progress.value = 0
  activeId.value = ''
  showTop.value = false
  try {
    // 非法编号在客户端直接显示404，不向后端发送无意义请求。
    if (!/^[1-9]\d{0,18}$/.test(String(id)) || BigInt(id) > 9223372036854775807n) { notFound.value = true; throw new Error('文章不存在或尚未发布') }
    const response = await fetch(`/api/articles/${encodeURIComponent(id)}`, { signal: AbortSignal.timeout(15000) })
    // 404也属于页面状态；旧文章的404不能遮住已经打开的新文章。
    if (requestId !== latestRequestId) return
    if (response.status === 404) { notFound.value = true; throw new Error('文章不存在或尚未发布') }
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
  <div class="reading-page" :class="{ 'is-focused': focusedReading }" :style="{ '--reading-size': `${readingSize}px` }">
    <RouterLink class="back-link" :to="returnLocation">← 返回来处</RouterLink>
    <PageState v-if="loading" kind="loading" heading="h1" layout="article" title="正在打开这篇文章…" />
    <PageState v-else-if="notFound" kind="not-found" heading="h1" title="这篇记录暂时无法找到" message="文章可能尚未公开、已经撤回，或链接有误。可以返回列表浏览其他记录。" retry-label="重新检查文章" @retry="loadArticle(route.params.id)"><RouterLink class="v2-action" :to="returnLocation">返回来处 →</RouterLink></PageState>
    <PageState v-else-if="error" kind="error" heading="h1" title="暂时无法阅读" :message="error" retry-label="重新加载文章" @retry="loadArticle(route.params.id)" />
    <template v-else-if="article">
      <ReadingTools :article-id="article.id" @change="changeReading" />
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
          <RouterLink v-if="article.categoryId != null" class="v2-badge"
                      :to="{ path: '/topics', query: { kind: 'category', id: String(article.categoryId) }, hash: '#entries' }">{{ article.categoryName }}</RouterLink>
          <span v-else class="v2-badge">未分类</span>
          <RouterLink v-for="tag in article.tags ?? []" :key="tag.id" class="reading-tag"
                      :to="{ path: '/topics', query: { kind: 'tag', id: String(tag.id) }, hash: '#entries' }">#{{ tag.name }}</RouterLink>
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
               @error.capture="scheduleReading">
            <ReadingContent :html="contentHtml" :code-blocks="rendered.codeBlocks" />
          </div>
          <CollectionTrail v-if="returnLocation.path.startsWith('/collections/')" :article-id="article.id" :from="route.query.from" />
          <ArticleNeighbors v-else :article-id="article.id" :from="route.query.from" />
          <footer class="reading-end">
            <span>这篇记录，到这里告一段落。</span>
            <RouterLink :to="returnLocation">返回来处，继续探索 →</RouterLink>
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
