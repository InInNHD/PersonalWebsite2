<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listReturn } from '../listNavigation.js'
import { finishReading } from '../readingNavigation.js'
import { renderArticle } from '../articleReading.js'
import { siteProfile } from '../siteProfile.js'
import ReadingToc from '../components/ReadingToc.vue'
import ReadingContent from '../components/ReadingContent.vue'
import ArticleNeighbors from '../components/ArticleNeighbors.vue'


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
               @error.capture="scheduleReading">
            <ReadingContent :html="contentHtml" :code-blocks="rendered.codeBlocks" />
          </div>
          <ArticleNeighbors :article-id="article.id" :from="route.query.from" />
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