<script setup>
import PageState from '../components/PageState.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { finishList, listKey, listQuery, rememberList } from '../listNavigation.js'
import ArticleCard from '../components/ArticleCard.vue'
import ArticlePagination from '../components/ArticlePagination.vue'
import { compactArticles } from '../articleDisplay.js'

const route = useRoute()
const router = useRouter()
const filtersExpanded = ref(false)
const compact = compactArticles
// 从页头搜索入口进入时，自动打开筛选区，保证锚点目标可见。
const showFilters = computed(() => filtersExpanded.value || route.hash === '#article-search')


const articles = ref([])
const matches = ref({})
const type = ref('')
const loading = ref(false)
const error = ref('')
const articleCategories = ref([])
const categoryId = ref('')
const categoryError = ref('')
const topicLoading = ref(false)
const tags = ref([])
const tagId = ref('') // 空字符串表示全部标签；选择标签后是数字编号。
const tagError = ref('')
const keywordInput = ref('') // 输入框中的文字，尚未提交时不影响翻页。
const keyword = ref('') // 最近一次提交的关键词。
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)

// 离开文章库时记录当前查询对应的滚动位置，供正文中的返回链接使用。
onBeforeRouteLeave(() => {
  rememberList(route, { left: window.scrollX, top: window.scrollY })
})

// 分类只在页面进入时加载一次。
// 离开页面后，不再接受这次加载结果。
let disposed = false

// 只用于判断请求先后，不参与页面渲染，因此不需要 ref。
let latestRequestId = 0

// 离开页面时，让尚未结束的请求失效。
onBeforeUnmount(() => {
  latestRequestId += 1
  disposed = true
})

// 栏目值必须与后端 ArticleController 接受的值相同。
const typeOptions = [
  { value: '', label: '全部' },
  { value: 'NOTE', label: '笔记' },
  { value: 'THOUGHT', label: '心得' },
  { value: 'DIARY', label: '日记' },
]

async function loadArticles() {
  const requestId = ++latestRequestId
  const requestKey = listKey(route)
  loading.value = true
  error.value = ''

  try {
    const params = new URLSearchParams()
    if (type.value) params.set('type', type.value)
    if (categoryId.value !== '') params.set('categoryId', String(categoryId.value))
    if (tagId.value !== '') params.set('tagId', String(tagId.value))
    if (keyword.value) params.set('keyword', keyword.value)
    params.set('page', String(page.value))
    params.set('size', String(pageSize))

    const response = await fetch(`/api/articles?${params.toString()}`, { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    // 列表与总数、页码都必须通过同一个请求编号检查。
    if (requestId !== latestRequestId || requestKey !== listKey(route)) return

    articles.value = data.items
    matches.value = data.matches ?? {}
    total.value = data.total
    page.value = data.page
    totalPages.value = data.totalPages
    // 内容减少时，后端可能把超出末页的请求纠正到末页。
    // 同步修正网址，replace避免把错误页码留在历史中。
    if (data.page !== Number(route.query.page ?? 1)) {
      await router.replace({ path: '/articles', query: listQuery({ ...route.query, page: data.page }), hash: route.hash })
    }
  } catch (cause) {
    if (requestId !== latestRequestId) return
    articles.value = []
    matches.value = {}
    total.value = 0
    totalPages.value = 0
    error.value = cause instanceof Error ? cause.message : '加载文章列表失败'
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
      await finishList(requestKey)
    }
  }
}

// 交互只更新网址，watch统一读取网址并加载，避免按钮和watch重复请求。
async function navigateList() {
  const query = listQuery({ type: type.value, categoryId: categoryId.value,
    tagId: tagId.value, keyword: keyword.value, page: page.value })
  const sameQuery = listKey({ query }) === listKey(route)
  await router.push({ path: '/articles', query, hash: '#entries' })
  // 相同条件再次点搜索时watch不会触发，允许主动重新加载。
  if (sameQuery) return loadArticles()
}

// 搜索提交与筛选条件变化都从第一页开始。
function applyFilters() {
  page.value = 1
  return navigateList()
}

function searchArticles() {
  keyword.value = keywordInput.value.trim()
  return applyFilters()
}

function clearSearch() {
  keywordInput.value = ''
  keyword.value = ''
  return applyFilters()
}

function changePage(nextPage) {
  // 加载期间和边界外不发请求，避免重复点击翻页。
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  return navigateList()
}

// 文章库只展示被公开文章使用的分类和标签；管理端保留原全量接口。
async function loadTopics() {
  if (topicLoading.value || disposed) return
  topicLoading.value = true
  categoryError.value = ''
  tagError.value = ''
  try {
    const response = await fetch('/api/topics', { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    if (disposed) return
    articleCategories.value = data.categories
    tags.value = data.tags
  } catch {
    if (!disposed) categoryError.value = '主题加载失败，文章仍可阅读。'
  } finally { if (!disposed) topicLoading.value = false }
}
onMounted(loadTopics)

// 直接打开链接、刷新、后退/前进都走同一入口。
// 只观察查询条件；展开搜索面板等纯锚点变化不应重新请求文章。
watch(() => listKey(route), () => {
  const query = listQuery(route.query)
  type.value = query.type ?? ''
  categoryId.value = query.categoryId ?? ''
  tagId.value = query.tagId ?? ''
  keyword.value = query.keyword ?? ''
  keywordInput.value = keyword.value
  page.value = Number(query.page ?? 1)
  if (categoryId.value || tagId.value || keyword.value) filtersExpanded.value = true
  void loadArticles()
}, { immediate: true, flush: 'sync' })

// 所有入口继续调用原有 applyFilters，统一回到第一页并防止旧响应覆盖。
function toggleFilters() {
  if (route.hash === '#article-search') {
    // 移除搜索锚点后才允许收起；下次点击页头搜索仍能重新展开。
    filtersExpanded.value = false
    return router.replace({ path: route.path, query: route.query, hash: '' })
  }
  filtersExpanded.value = !filtersExpanded.value
}

function selectType(value) {
  type.value = value
  return applyFilters()
}

function selectCategory(id) {
  categoryId.value = id
  filtersExpanded.value = true
  return applyFilters()
}

function clearAll() {
  type.value = ''
  categoryId.value = ''
  tagId.value = ''
  keywordInput.value = ''
  keyword.value = ''
  return applyFilters()
}
</script>

<template>
  <div class="v3-library">
    <header class="v3-page-heading"><p class="v2-eyebrow">LIBRARY / FIELD NOTES</p><h1>文章库</h1><p>按栏目、分类与标签，找到此刻想读的一篇。</p></header>
    <div class="v3-library-content">
      <section id="entries" aria-labelledby="entries-title" :aria-busy="loading">
        <header class="v2-entry-heading">
          <h2 id="entries-title">所有公开文章<span>.</span></h2>
          <small v-if="!loading && !error" role="status">共 {{ total }} 篇文章</small><button type="button" class="v2-type" :aria-pressed="compact" @click="compact = !compact">紧凑列表</button>
        </header>
        <div class="v2-content-tools" role="group" aria-label="文章栏目">
          <button v-for="option in typeOptions" :key="option.value" type="button" class="v2-type"
                  :aria-pressed="type === option.value" @click="selectType(option.value)">{{ option.label }}</button>
          <button type="button" class="v2-filter-toggle" :aria-expanded="showFilters" aria-controls="article-search"
                  @click="toggleFilters">筛选</button>
        </div>

        <!-- v-show 保留输入内容。筛选面板收起时，已选条件仍然生效。 -->
        <div v-show="showFilters" id="article-search" class="v2-filter-panel">
          <label>栏目
            <select v-model="type" @change="applyFilters">
              <option v-for="option in typeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <label>分类
            <select v-model="categoryId" @change="applyFilters">
              <option value="">全部分类</option>
              <option v-for="category in articleCategories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
          </label>
          <label>标签
            <select v-model="tagId" @change="applyFilters">
              <option value="">全部标签</option>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
          <form class="v2-search" @submit.prevent="searchArticles">
            <label>搜索标题、摘要或正文
              <input v-model="keywordInput" type="search" maxlength="100" placeholder="例如：Java、生活记录">
            </label>
            <button type="submit">搜索</button>
            <button type="button" @click="clearSearch">清空搜索</button>
            <button type="button" @click="clearAll">重置筛选</button>
          </form>
        </div>
        <p v-if="categoryId !== '' || tagId !== '' || keyword" class="v2-meta">
          正在使用筛选条件：{{ keyword ? `关键词「${keyword}」` : '' }}
          {{ categoryId !== '' ? '已选分类' : '' }} {{ tagId !== '' ? '已选标签' : '' }}
          <button type="button" class="v2-type" @click="filtersExpanded = true">查看条件</button>
        </p>
        <p v-if="topicLoading" class="v2-meta" role="status">正在读取分类与标签…</p>
        <PageState v-if="categoryError" compact kind="error" heading="h3" title="分类与标签暂时无法加载" :message="categoryError" retry-label="重新加载分类标签" @retry="loadTopics" />
        <PageState v-if="loading" kind="loading" layout="cards" heading="h3" title="正在整理内容，请稍候…" />
        <PageState v-else-if="error" kind="error" heading="h3" title="内容暂时没能加载" :message="error" retry-label="重新加载文章" @retry="loadArticles" />
        <PageState v-else-if="articles.length === 0 && (type || categoryId || tagId || keyword)" heading="h3" title="暂时没有找到记录" message="当前搜索或筛选条件下没有已发布的文章。" retry-label="清空全部条件" @retry="clearAll" />
        <PageState v-else-if="articles.length === 0" heading="h3" title="第一篇公开记录，还在路上" message="笔记与生活会慢慢整理到这里。也可以先看看资源与个人介绍。"><RouterLink class="v2-action" to="/resources">浏览资源 →</RouterLink><RouterLink class="v2-action" to="/about">关于作者 →</RouterLink></PageState>
        <div v-else class="v2-grid" :class="{ compact }">
          <ArticleCard v-for="article in articles" :key="article.id" :article="article"
                       :featured="false" :match="matches[article.id]" :return-to="route.fullPath" />
        </div>
        <ArticlePagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading" @change="changePage" />
      </section>


    </div>
  </div>
</template>
