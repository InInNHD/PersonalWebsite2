<script setup>
import PageState from '../components/PageState.vue'
import { usePageMeta } from '../pageMeta.js'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { archiveQuery, finishList, listKey, rememberList } from '../listNavigation.js'
import ArticlePagination from '../components/ArticlePagination.vue'

const route = useRoute()
const router = useRouter()
// 归档和日记共用月份导航、分页与异步处理，只改变内容范围和展示。
const diary = computed(() => route.path === '/diary')
const months = ref(null) // null表示目录尚未取得；[]才表示公开目录为空。
const directoryLoading = ref(true)
const directoryError = ref('')
const articles = ref([])
const loading = ref(false)
const error = ref('')
const page = ref(1)
const total = ref(0)
const totalPages = ref(0)
const query = computed(() => archiveQuery(route.query))
const selectedMonth = computed(() => query.value.month ?? months.value?.[0]?.month ?? '')
const selected = computed(() => months.value?.find(item => item.month === selectedMonth.value))
const monthLabel = computed(() => selectedMonth.value
    ? `${selectedMonth.value.slice(0, 4)} 年 ${Number(selectedMonth.value.slice(5))} 月` : '暂无公开记录')
const publicTotal = computed(() => months.value?.reduce((sum, item) => sum + item.total, 0) ?? 0)
const names = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
// 接口按年月倒序，直接按连续年份分组；不重新加载全文计算目录。
const yearGroups = computed(() => {
  const groups = []
  for (const item of months.value ?? []) {
    const year = item.month.slice(0, 4)
    let group = groups.at(-1)
    if (!group || group.year !== year) {
      group = { year, months: [], total: 0 }
      groups.push(group)
    }
    group.months.push(item)
    group.total += item.total
  }
  return groups
})
usePageMeta(() => `${diary.value ? '公开日记' : '文章归档'}${selectedMonth.value ? ' · ' + selectedMonth.value : ''}`, () => diary.value ? '精选公开的生活片段，按月份回看。' : '按年月回看已发布的笔记、心得与日记。')
let latestRequestId = 0
let latestDirectoryId = 0
let disposed = false

onBeforeRouteLeave(() => rememberList(route, { left: window.scrollX, top: window.scrollY }))
onBeforeRouteUpdate((to, from) => rememberList(from, { left: window.scrollX, top: window.scrollY }))
onBeforeUnmount(() => { disposed = true; latestRequestId += 1; latestDirectoryId += 1 })
// 两个路由复用同一组件时不会离开组件，切换前也保存原页面位置。
function monthLocation(month, nextPage = 1) {
  return { path: route.path, query: archiveQuery({ month, page: nextPage }), hash: '#entries' }
}
function articleLocation(article) {
  return { path: `/articles/${article.id}`, query: { from: route.fullPath } }
}

async function loadArticles() {
  const requestId = ++latestRequestId
  const key = listKey(route)
  articles.value = []
  total.value = 0
  totalPages.value = 0
  loading.value = false
  error.value = ''
  page.value = Number(query.value.page ?? 1)
  // 目录先就绪，才能判断月份是否公开；草稿专用月份不会触发列表请求。
  if (!months.value) {
    if (!directoryLoading.value) await finishList(key)
    return
  }
  if (!selected.value) { await finishList(key); return }
  loading.value = true
  try {
    const params = new URLSearchParams({ month: selectedMonth.value, page: String(page.value), size: '10' })
    if (diary.value) params.set('type', 'DIARY')
    const response = await fetch(`/api/articles?${params}`, { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`记录加载失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId || key !== listKey(route)) return
    articles.value = data.items
    total.value = data.total
    totalPages.value = data.totalPages
    page.value = data.page
    // 内容变少时同步后端纠正的末页，不把无效页码留在历史中。
    if (data.page !== Number(query.value.page ?? 1)) {
      await router.replace({ path: route.path,
        query: archiveQuery({ month: selectedMonth.value, page: data.page }), hash: route.hash })
    }
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '记录加载失败'
  } finally {
    if (requestId === latestRequestId) { loading.value = false; await finishList(key) }
  }
}

async function loadArchive() {
  const requestId = ++latestDirectoryId
  const path = route.path
  latestRequestId += 1
  directoryLoading.value = true
  directoryError.value = ''
  months.value = null
  articles.value = []
  loading.value = false
  error.value = ''
  total.value = 0
  totalPages.value = 0
  try {
    const response = await fetch(diary.value ? '/api/articles/archive?type=DIARY' : '/api/articles/archive', { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`月份目录加载失败：HTTP ${response.status}`)
    const data = await response.json()
    if (disposed || requestId !== latestDirectoryId || path !== route.path) return
    months.value = data
    // 先渲染目录，再等待文章；滚动恢复要发生在真实内容出现后。
    directoryLoading.value = false
    // 首次进入时也把实际月份写进网址，稍后新增月份不会改变正文的返回来源。
    if (data.length && !query.value.month) {
      await router.replace({ path, query: { month: data[0].month }, hash: route.hash })
    } else {
      await loadArticles()
    }
  } catch (cause) {
    if (disposed || requestId !== latestDirectoryId) return
    directoryError.value = cause instanceof Error ? cause.message : '月份目录加载失败'
  } finally {
    if (!disposed && requestId === latestDirectoryId) {
      directoryLoading.value = false
      if (!months.value) await finishList(listKey(route))
    }
  }
}

function changePage(nextPage) {
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  return router.push(monthLocation(selectedMonth.value, nextPage))
}
// 顺序有意如此：先清理跨页面旧目录，再响应本次路径/月份/页码。
watch(() => route.path, loadArchive, { flush: 'sync' })
watch(() => listKey(route), loadArticles, { immediate: true, flush: 'sync' })
onMounted(loadArchive)
</script>

<template>
  <section class="timeline-page" :class="{ 'is-diary': diary }">
    <header class="timeline-heading">
      <p class="v2-eyebrow">{{ diary ? 'LIFE / SELECTED MOMENTS' : 'ARCHIVE / THROUGH THE YEARS' }}</p>
      <h1>{{ diary ? '公开日记' : '文章归档' }}</h1>
      <p>{{ diary ? '收藏生活里的微光。这里只展示选择公开的记录。' : '沿着时间的线索，回看笔记、心得与公开的生活记录。' }}</p>
      <RouterLink :to="diary ? '/archive' : '/diary'">{{ diary ? '浏览全部归档 →' : '只看公开日记 →' }}</RouterLink>
    </header>
    <PageState v-if="directoryLoading" kind="loading" title="正在整理月份目录…" />
    <PageState v-else-if="directoryError" kind="error" title="月份目录暂时没能加载" :message="directoryError" retry-label="重新加载月份" @retry="loadArchive" />
    <template v-else-if="months">
      <p class="timeline-total">{{ diary ? '公开日记' : '公开记录' }} {{ publicTotal }} 篇 · {{ months.length }} 个月</p>
      <PageState v-if="!months.length" :title="diary ? '暂时没有公开日记' : '暂时没有已发布的文章'" message="只收录选择公开的记录。可以先浏览其他内容。"><RouterLink class="v2-action" to="/">回到首页 →</RouterLink></PageState>
      <div v-else class="timeline-layout">
        <nav class="timeline-months" aria-label="按年月浏览">
          <label class="timeline-month-select">快速选择月份
            <select :value="selectedMonth" @change="router.push(monthLocation($event.target.value))">
              <option v-if="!selected" :value="selectedMonth">{{ selectedMonth }}（无公开记录）</option>
              <optgroup v-for="group in yearGroups" :key="group.year" :label="`${group.year} 年`">
                <option v-for="item in group.months" :key="item.month" :value="item.month">{{ item.month }}（{{ item.total }} 篇）</option>
              </optgroup>
            </select>
          </label>
          <section v-for="group in yearGroups" :key="group.year" class="timeline-year">
            <h2>{{ group.year }} <small>{{ group.total }} 篇</small></h2>
            <div>
              <RouterLink v-for="item in group.months" :key="item.month" :to="monthLocation(item.month)"
                          :aria-current="selectedMonth === item.month ? 'page' : null">
                <span>{{ Number(item.month.slice(5)) }} 月</span><small>{{ item.total }} 篇</small>
              </RouterLink>
            </div>
          </section>
        </nav>
        <section id="entries" class="timeline-results" :aria-busy="loading" aria-labelledby="timeline-month-title">
          <header><p class="v2-eyebrow">{{ diary ? 'SELECTED DIARY' : 'PUBLISHED RECORDS' }}</p><h2 id="timeline-month-title">{{ monthLabel }}</h2></header>
          <PageState v-if="!selected" title="这个月没有公开记录" message="请选择其他月份，或回到最近的一次记录。" retry-label="查看最新月份" @retry="router.push(monthLocation(months[0].month))" />
          <PageState v-else-if="loading" kind="loading" title="正在查找这个月的记录…" />
          <PageState v-else-if="error" kind="error" title="记录暂时没能加载" :message="error" retry-label="重新加载记录" @retry="loadArticles" />
          <template v-else>
            <p class="topics-note" role="status">{{ selectedMonth }} · 共 {{ total }} 篇{{ diary ? '公开日记' : '公开记录' }}</p>
            <PageState v-if="!articles.length" title="这个月暂时没有公开记录" message="可以选择其他月份继续浏览。" />
            <ol v-else class="timeline-items">
              <li v-for="article in articles" :key="article.id">
                <time :datetime="article.publishedAt">{{ article.publishedAt?.slice(5, 10) }}</time>
                <div>
                  <span v-if="!diary" class="timeline-kind">{{ names[article.type] ?? '记录' }}</span>
                  <h3><RouterLink :to="articleLocation(article)">{{ article.title }}</RouterLink></h3>
                  <!-- 日记只展示纯文本摘要，正文仍进入既有阅读页，不在列表展开私密内容。 -->
                  <p v-if="diary && article.summary">{{ article.summary }}</p>
                  <RouterLink class="timeline-read" :to="articleLocation(article)">{{ diary ? '阅读这一天 →' : '阅读记录 →' }}</RouterLink>
                </div>
              </li>
            </ol>
            <ArticlePagination :page="page" :total-pages="totalPages" :loading="loading" @change="changePage" />
          </template>
        </section>
      </div>
    </template>
  </section>
</template>