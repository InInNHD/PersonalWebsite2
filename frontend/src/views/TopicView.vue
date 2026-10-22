<script setup>
import PageState from '../components/PageState.vue'
import { usePageMeta } from '../pageMeta.js'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { finishList, listKey, rememberList, topicQuery } from '../listNavigation.js'
import ArticleCard from '../components/ArticleCard.vue'
import ArticlePagination from '../components/ArticlePagination.vue'

const route = useRoute()
const router = useRouter()
const directory = ref(null)
const directoryLoading = ref(true)
const directoryError = ref('')
const articles = ref([])
const loading = ref(false)
const error = ref('')
const page = ref(1)
const total = ref(0)
const totalPages = ref(0)
const query = computed(() => topicQuery(route.query))
const selected = computed(() => {
  if (!query.value.id || !directory.value) return null
  const items = query.value.kind === 'category' ? directory.value.categories : directory.value.tags
  return items.find(item => String(item.id) === query.value.id) ?? null
})
const kindName = computed(() => query.value.kind === 'category' ? '分类' : '标签')
usePageMeta(() => selected.value ? `${selected.value.name} · ${kindName.value}` : '内容地图', () => selected.value ? `浏览${selected.value.name}中的公开文章。` : '沿着分类与标签探索公开记录。')
let latestRequestId = 0
let latestDirectoryId = 0
let disposed = false

onBeforeRouteLeave(() => rememberList(route, { left: window.scrollX, top: window.scrollY }))
onBeforeUnmount(() => { disposed = true; latestRequestId += 1; latestDirectoryId += 1 })

function topicLocation(kind, id) {
  return { path: '/topics', query: topicQuery({ kind, id }), hash: '#entries' }
}

async function loadArticles() {
  const requestId = ++latestRequestId
  const key = listKey(route)
  articles.value = []
  error.value = ''
  loading.value = false
  total.value = 0
  totalPages.value = 0
  page.value = Number(query.value.page ?? 1)
  // 目录尚未就绪时，滚动等待由loadDirectory成功或失败后释放。
  if (!directory.value) {
    if (!directoryLoading.value) await finishList(key)
    return
  }
  if (!query.value.id || !selected.value) {
    await finishList(key)
    return
  }
  loading.value = true
  try {
    const params = new URLSearchParams({ page: String(page.value), size: '10' })
    params.set(query.value.kind === 'category' ? 'categoryId' : 'tagId', query.value.id)
    const response = await fetch(`/api/articles?${params}`, { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId || key !== listKey(route)) return
    articles.value = data.items
    page.value = data.page
    total.value = data.total
    totalPages.value = data.totalPages
    if (data.page !== Number(query.value.page ?? 1)) {
      await router.replace({ path: '/topics', query: topicQuery({ ...query.value, page: data.page }), hash: route.hash })
    }
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '文章加载失败'
  } finally {
    if (requestId === latestRequestId) { loading.value = false; await finishList(key) }
  }
}

async function loadDirectory() {
  const requestId = ++latestDirectoryId
  latestRequestId += 1
  directoryLoading.value = true
  directoryError.value = ''
  directory.value = null
  articles.value = []
  loading.value = false
  try {
    const response = await fetch('/api/topics', { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    if (disposed || requestId !== latestDirectoryId) return
    directory.value = data
    // 先显示目录和文章容器，finishList才能在真实列表出现后恢复位置。
    directoryLoading.value = false
    await loadArticles()
  } catch (cause) {
    if (disposed || requestId !== latestDirectoryId) return
    directoryError.value = `主题目录加载失败：${cause instanceof Error ? cause.message : '未知错误'}`
  } finally {
    if (!disposed && requestId === latestDirectoryId) {
      directoryLoading.value = false
      if (!directory.value) await finishList(listKey(route))
    }
  }
}

function changePage(nextPage) {
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  return router.push({ path: '/topics', query: topicQuery({ ...query.value, page: nextPage }), hash: '#entries' })
}
watch(() => listKey(route), loadArticles, { immediate: true, flush: 'sync' })
onMounted(loadDirectory)
</script>

<template>
  <section class="topics-page">
    <header class="topics-heading">
      <p class="v2-eyebrow">EXPLORE / FIELD NOTES</p><h1>内容地图</h1>
      <p>沿着一个分类，或一枚标签，找到值得继续阅读的记录。</p>
    </header>
    <PageState v-if="directoryLoading" kind="loading" title="正在整理公开主题…" />
    <PageState v-else-if="directoryError" kind="error" title="主题目录暂时没能加载" :message="directoryError" retry-label="重新加载主题" @retry="loadDirectory" />
    <template v-else-if="directory">
      <div class="topics-directory">
        <section aria-labelledby="topic-categories">
          <h2 id="topic-categories">分类 <small>{{ directory.categories.length }}</small></h2>
          <p v-if="!directory.categories.length">暂时没有公开文章使用分类。</p>
          <div v-else class="topic-links">
            <RouterLink v-for="item in directory.categories" :key="item.id" :to="topicLocation('category', item.id)"
                        :aria-current="query.kind === 'category' && query.id === String(item.id) ? 'page' : null">
              <span>{{ item.name }}</span><small>{{ item.total }} 篇</small>
            </RouterLink>
          </div>
        </section>
        <section aria-labelledby="topic-tags">
          <h2 id="topic-tags">标签 <small>{{ directory.tags.length }}</small></h2>
          <p v-if="!directory.tags.length">暂时没有公开文章使用标签。</p>
          <div v-else class="topic-links">
            <RouterLink v-for="item in directory.tags" :key="item.id" :to="topicLocation('tag', item.id)"
                        :aria-current="query.kind === 'tag' && query.id === String(item.id) ? 'page' : null">
              <span>#{{ item.name }}</span><small>{{ item.total }} 篇</small>
            </RouterLink>
          </div>
        </section>
      </div>
      <section id="entries" class="topics-results" :aria-busy="loading" aria-labelledby="topic-results-title">
        <header class="topics-result-heading">
          <div>
            <p class="v2-eyebrow">{{ query.id ? kindName : '开始探索' }}</p>
            <h2 id="topic-results-title">{{ selected?.name ?? (query.id ? '暂无公开内容的主题' : '选择一个分类或标签') }}</h2>
          </div>
          <RouterLink v-if="query.id" to="/topics">查看全部主题 →</RouterLink>
        </header>
        <p v-if="!query.id" class="topics-note">上方数量按公开文章统计；同一篇文章可以出现在多个标签中。</p>
        <PageState v-else-if="!selected" title="这个主题还没有公开记录" message="主题可能不存在，或暂时没有已发布文章。可以选择上方其他主题。" />
        <PageState v-else-if="loading" kind="loading" layout="cards" title="正在查找相关记录…" />
        <PageState v-else-if="error" kind="error" title="相关记录暂时没能加载" :message="error" retry-label="重新加载文章" @retry="loadArticles" />
        <template v-else>
          <p class="topics-note" role="status">{{ kindName }}「{{ selected.name }}」· 共 {{ total }} 篇公开文章</p>
          <PageState v-if="!articles.length" title="这个主题暂时没有公开文章" message="可以稍后再来，或浏览其他分类与标签。" />
          <div v-else class="v2-grid">
            <ArticleCard v-for="article in articles" :key="article.id" :article="article" :return-to="route.fullPath" />
          </div>
          <ArticlePagination :page="page" :total-pages="totalPages" :loading="loading" @change="changePage" />
        </template>
      </section>
    </template>
  </section>
</template>