<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { listReturn } from '../listNavigation.js'

const props = defineProps({
  articleId: { type: [String, Number], required: true },
  from: { default: null },
})
const previous = ref(null)
const next = ref(null)
const loading = ref(false)
const error = ref('')
let latestRequestId = 0

// 沿用B2的白名单：from只能指向本站首页及合法筛选条件。
// 同一个来源跟随前后篇传递，连续阅读后仍能回原列表。
const returnPath = computed(() => {
  const origin = listReturn(props.from)
  const query = new URLSearchParams(origin.query ?? {}).toString()
  return origin.path + (query ? `?${query}` : '') + (origin.hash ?? '')
})
function articleLocation(item) {
  return { path: `/articles/${item.id}`, query: { from: returnPath.value } }
}

async function loadNeighbors() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''
  previous.value = null
  next.value = null
  try {
    const response = await fetch(`/api/articles/${encodeURIComponent(props.articleId)}/neighbors`)
    if (response.status === 404) throw new Error('当前文章已下线或不存在')
    if (!response.ok) throw new Error(`前后篇加载失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId) return
    previous.value = data.previous
    next.value = data.next
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '前后篇加载失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

watch(() => props.articleId, loadNeighbors, { immediate: true })
onBeforeUnmount(() => { latestRequestId += 1 })
</script>

<template>
  <section class="reading-neighbors" aria-label="继续阅读">
    <div class="reading-neighbors-heading">
      <h2>继续阅读</h2><span>按发布时间浏览公开文章</span>
    </div>
    <p v-if="loading" role="status">正在查找前后篇…</p>
    <div v-else-if="error" class="reading-neighbors-error" role="alert">
      <p>{{ error }}，正文仍可阅读。</p>
      <button type="button" @click="loadNeighbors">重新加载前后篇</button>
    </div>
    <nav v-else class="reading-neighbor-grid" aria-label="前后篇文章">
      <RouterLink v-if="previous" :to="articleLocation(previous)" class="reading-neighbor">
        <small>← 上一篇 · 较早</small><strong>{{ previous.title }}</strong>
        <time :datetime="previous.publishedAt">{{ previous.publishedAt?.slice(0, 10) }}</time>
      </RouterLink>
      <div v-else class="reading-neighbor is-boundary">已经是最早一篇公开文章</div>
      <RouterLink v-if="next" :to="articleLocation(next)" class="reading-neighbor">
        <small>下一篇 · 较新 →</small><strong>{{ next.title }}</strong>
        <time :datetime="next.publishedAt">{{ next.publishedAt?.slice(0, 10) }}</time>
      </RouterLink>
      <div v-else class="reading-neighbor is-boundary">已经是最新一篇公开文章</div>
    </nav>
  </section>
</template>