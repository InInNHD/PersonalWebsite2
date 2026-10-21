<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { siteProfile } from '../siteProfile.js'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { finishList, listKey, listQuery, rememberList } from '../listNavigation.js'
import ArticleCard from '../components/ArticleCard.vue'
import SiteStats from '../components/SiteStats.vue'
import ProfileAvatar from '../components/ProfileAvatar.vue'
import ArticlePagination from '../components/ArticlePagination.vue'
import heroArt from '../assets/hero.svg'

const route = useRoute()
const router = useRouter()
const filtersExpanded = ref(false)
// 从页头搜索入口进入时，自动打开筛选区，保证锚点目标可见。
const showFilters = computed(() => filtersExpanded.value || route.hash === '#article-search')


const articles = ref([])
const type = ref('')
const loading = ref(false)
const error = ref('')
const articleCategories = ref([])
const categoryId = ref('')
const categoryError = ref('')
const tags = ref([])
const tagId = ref('') // 空字符串表示全部标签；选择标签后是数字编号。
const tagError = ref('')
const keywordInput = ref('') // 输入框中的文字，尚未提交时不影响翻页。
const keyword = ref('') // 最近一次提交的关键词。
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)

// 离开首页时记录当前查询对应的滚动位置，供正文中的返回链接使用。
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

    const response = await fetch(`/api/articles?${params.toString()}`)
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    // 列表与总数、页码都必须通过同一个请求编号检查。
    if (requestId !== latestRequestId || requestKey !== listKey(route)) return

    articles.value = data.items
    total.value = data.total
    page.value = data.page
    totalPages.value = data.totalPages
    // 内容减少时，后端可能把超出末页的请求纠正到末页。
    // 同步修正网址，replace避免把错误页码留在历史中。
    if (data.page !== Number(route.query.page ?? 1)) {
      await router.replace({ path: '/', query: listQuery({ ...route.query, page: data.page }), hash: route.hash })
    }
  } catch (cause) {
    if (requestId !== latestRequestId) return
    articles.value = []
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
  await router.push({ path: '/', query, hash: '#entries' })
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

// 首页只展示被公开文章使用的分类和标签；管理端保留原全量接口。
async function loadTopics() {
  categoryError.value = ''
  tagError.value = ''
  try {
    const response = await fetch('/api/topics')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    if (disposed) return
    articleCategories.value = data.categories
    tags.value = data.tags
  } catch {
    if (!disposed) categoryError.value = '主题加载失败，文章仍可阅读；请刷新页面重试。'
  }
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
  <div class="v2-home">
    <section class="v2-hero" aria-labelledby="home-title">
      <div class="v2-hero-text">
        <p class="v2-eyebrow">A PERSONAL NOTEBOOK, ALWAYS GROWING</p>
        <h1 id="home-title">{{ siteProfile.heroTitle }}<br><span>{{ siteProfile.heroAccent }}</span></h1>
        <p class="v2-hero-copy">你好，我是 {{ siteProfile.nickname }}。<br>{{ siteProfile.heroDescription }}</p>
        <div class="v2-hero-actions">
          <a class="v2-action primary" href="#entries" @click.prevent="selectType('NOTE')">阅读笔记 →</a>
          <RouterLink class="v2-action" to="/diary">浏览日记</RouterLink>
        </div>
        <p class="v2-hero-sign">LEARN SOMETHING. KEEP A LITTLE LIGHT.</p>
      </div>
      <figure class="v2-hero-art">
        <img :src="heroArt" alt="打开的笔记终端与萤火轨迹" width="530" height="400">
        <figcaption>FIREFLY LAB / SKETCH 001</figcaption>
      </figure>
    </section>

    <div class="v2-content">
      <section id="entries" aria-labelledby="entries-title" :aria-busy="loading">
        <header class="v2-entry-heading">
          <h2 id="entries-title">最近的记录<span>.</span></h2>
          <small v-if="!loading && !error" role="status">共 {{ total }} 篇文章</small>
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
            <label>搜索标题或摘要
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
        <p v-if="categoryError" class="error" role="alert">{{ categoryError }}</p>
        <p v-if="tagError" class="error" role="alert">{{ tagError }}</p>

        <div v-if="loading" class="v2-state" role="status">正在整理内容，请稍候…</div>
        <div v-else-if="error" class="v2-state" role="alert">
          <h3>内容暂时没能加载</h3><p>{{ error }}</p>
          <button type="button" @click="loadArticles">重新加载文章</button>
        </div>
        <div v-else-if="articles.length === 0" class="v2-state">
          <h3>暂时没有找到记录</h3><p>当前搜索或筛选条件下没有已发布的文章。</p>
          <button type="button" @click="clearAll">清空全部条件</button>
        </div>
        <div v-else class="v2-grid">
          <ArticleCard v-for="(article, index) in articles" :key="article.id" :article="article"
                       :featured="page === 1 && index === 0" :return-to="route.fullPath" />
        </div>
        <ArticlePagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading" @change="changePage" />
      </section>

      <aside class="v2-sidebar" aria-label="作者与内容导航">
        <section class="v2-profile">
          <ProfileAvatar class="v2-avatar" />
          <h2>{{ siteProfile.nickname }}</h2>
          <span class="v2-eyebrow">LEARNING IN PUBLIC</span>
          <p>{{ siteProfile.profileDescription }}</p>
          <SiteStats />
          <div class="v2-profile-links">
            <a v-if="siteProfile.githubUrl" :href="siteProfile.githubUrl" target="_blank" rel="noopener noreferrer">GitHub ↗</a>
            <RouterLink to="/about">关于我 →</RouterLink>
          </div>
        </section>
        <section class="v2-side-links">
          <h3>在这里探索</h3>
          <RouterLink to="/topics">分类与标签 <span aria-hidden="true">→</span></RouterLink>
          <a v-for="category in articleCategories" :key="category.id" href="#entries"
             @click.prevent="selectCategory(category.id)">{{ category.name }} <span aria-hidden="true">→</span></a>
          <RouterLink to="/archive">浏览归档 <span aria-hidden="true">→</span></RouterLink>
          <RouterLink to="/diary">公开日记 <span aria-hidden="true">→</span></RouterLink>
          <RouterLink to="/resources">资源收藏 <span aria-hidden="true">→</span></RouterLink>
        </section>
        <p class="v2-side-note">不必一次照亮整条路，<br>记住今天走过的这一段。</p>
      </aside>
    </div>
  </div>
</template>