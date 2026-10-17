# 第二版第二轮 B2：筛选网址与列表返回位置

日期：2026-10-17。接续已验收的B1。

这是供你在IDEA手敲的指南。**2026-10-18：实际源码已完成并通过验收，前端17项测试、生产构建、浏览器导航/返回位置及真实个人库只读接口检查通过。** 范围见 [B2验收记录](V2-LIST-NAVIGATION-ACCEPTANCE.md)。本节仅修改前端，不需要数据库迁移或新增依赖。

## 1. 完成后的行为

- 栏目、分类、标签、已提交的关键词和页码写入URL。例如 `/?type=NOTE&categoryId=1&tagId=1&keyword=Vue&page=2#entries`。
- 刷新、复制链接、浏览器后退和前进都按网址恢复条件。输入框里尚未点搜索的文字不进入网址，不影响翻页。
- 筛选变化回到第一页；翻页保留条件。第一页和空条件从网址省略。无效栏目、非法编号、超范围页码及未知参数会被清理；有效但不存在的分类/标签保留并显示空结果。
- 后端发现页码超出末页时，前端使用replace修正网址，避免留下错误历史记录。
- 从首页卡片进入正文，链接携带当前列表来源。正文中的“返回文章列表”和浏览器后退会恢复条件，并在列表渲染后恢复位置。新标签页或刷新详情后仍能恢复条件；精确滚动位置仅保存在当前页面会话，整页刷新后不保证保留。
- 返回地址只允许本站首页列表，外部地址和其他页面回退到首页。归档来源的专门返回路径留在后续内容导航轮次处理。
- 文章和搜索页头入口在首页保留当前条件；点击“首页”仍回到全部文章。

## 2. 文件与手敲顺序

根目录是 `E:\PersonalWebsite2`。以下六个文件给出完整内容，逐个替换可以避免漏掉导入、watch或模板事件。新文件先建，其余五个文件全选替换，**不要追加到旧代码后面**。

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | frontend/src/listNavigation.js | URL参数校验、站内返回目标、异步滚动协调 |
| 2 | frontend/src/main.js | 路由参数清理与滚动入口 |
| 3 | frontend/src/views/ArticleListView.vue | 从网址加载、统一筛选导航、离开时保存位置 |
| 4 | frontend/src/components/ArticleCard.vue | 所有文章入口携带列表来源 |
| 5 | frontend/src/views/ArticleDetailView.vue | 校验并使用列表返回目标 |
| 6 | frontend/src/components/SiteHeader.vue | 首页文章/搜索入口保留条件 |

**只输入代码框内的内容。代码框外的文件名、说明以及三个反引号都不要写入文件。** 本指南不使用上一轮的HTML文件定位标记。

数据流：点击筛选 → router.push更新网址 → watch读取规范化参数 → 请求文章 → 渲染卡片 → 通知路由可以滚动。按钮不直接和watch同时请求，旧请求仍通过编号丢弃。

滚动协调只服务首页列表，不缓存文章、不引入全局状态库。最多保留20组列表位置；请求成功或失败都通知完成，离开或切换查询会释放旧等待，避免过期任务移动其他页面。

## 3. listNavigation.js

新建文件。编号以字符串传递，避免Java Long精度丢失；参数中的数组取第一个值。

文件：`frontend/src/listNavigation.js`。

```js
import { nextTick } from 'vue'

// URL输入不可信：只接受本页支持的参数，避免非法值直接交给接口。
// 编号保留字符串，避免Java Long转成JavaScript Number时丢失精度。
const first = value => Array.isArray(value) ? value[0] : value
function positiveId(value) {
  const text = String(first(value) ?? '')
  if (!/^[1-9]\d{0,18}$/.test(text)) return ''
  return BigInt(text) <= 9223372036854775807n ? text : ''
}

export function listQuery(query = {}) {
  const result = {}
  const type = first(query.type)
  if (['NOTE', 'THOUGHT', 'DIARY'].includes(type)) result.type = type
  for (const name of ['categoryId', 'tagId']) {
    const id = positiveId(query[name])
    if (id) result[name] = id
  }
  const keyword = String(first(query.keyword) ?? '').trim().slice(0, 100)
  if (keyword) result.keyword = keyword
  const page = String(first(query.page) ?? '')
  if (/^[1-9]\d{0,6}$/.test(page) && Number(page) > 1 && Number(page) <= 1000000) result.page = page
  return result // 默认第一页和空条件省略，网址更短。
}

export const listKey = route => new URLSearchParams(listQuery(route.query)).toString()
export const listHash = hash => ['#entries', '#article-search'].includes(hash) ? hash : ''

// 正文的返回地址只允许本网站首页。外部地址、其他页面和异常参数回退。
export function listReturn(raw) {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return { path: '/' }
  try {
    const url = new URL(raw, 'https://list.invalid')
    if (url.origin !== 'https://list.invalid' || url.pathname !== '/') return { path: '/' }
    return { path: '/', query: listQuery(Object.fromEntries(url.searchParams)), hash: listHash(url.hash) }
  } catch {
    return { path: '/' }
  }
}

// 只保存当前页面会话中最近20个列表的位置，不保存正文或管理员信息。
const positions = new Map()
export function rememberList(route, position) {
  const key = listKey(route)
  positions.delete(key)
  positions.set(key, position)
  if (positions.size > 20) positions.delete(positions.keys().next().value)
}

// 路由先登记“列表尚未完成”，组件加载并渲染后通知它。
// 这样浏览器后退时，savedPosition不会在加载提示阶段被截断。
let pending = null
export function prepareList(to, from) {
  if (to.path === '/' && from.path === '/' && from.matched?.length && listKey(to) === listKey(from)) return
  pending?.resolve() // 离开或更换目标时释放旧等待者。
  pending = null
  if (to.path === '/') {
    let resolve
    const promise = new Promise(done => { resolve = done })
    pending = { key: listKey(to), promise, resolve }
  }
}

export async function finishList(key) {
  await nextTick() // 等待v-if把加载提示替换成卡片或错误信息。
  if (pending?.key === key) pending.resolve()
}

export async function listScroll(to, from, savedPosition, currentRoute) {
  if (to.path === '/') {
    const task = pending
    if (task?.key === listKey(to)) await task.promise
    await nextTick()
    // 用户可能已经去了别处，旧滚动任务不能移动新页面。
    if (currentRoute().fullPath !== to.fullPath) return false
    if (savedPosition) return savedPosition
    // 点击正文中的返回链接也恢复位置；直接访问详情没有历史位置则定位列表。
    if (from.path.startsWith('/articles/') && from.query.from) {
      const origin = listReturn(from.query.from)
      if (listKey(origin) === listKey(to)) return positions.get(listKey(to)) ?? { el: '#entries', top: 24 }
    }
    if (to.hash) return { el: to.hash, top: 24 }
    if (from.path === '/' && listKey(to) !== listKey(from)) return { el: '#entries', top: 24 }
  }
  return savedPosition ?? { top: 0 }
}
```

## 4. main.js

替换整个文件。beforeEach规范化网址，beforeResolve登记等待，scrollBehavior等待列表完成。

文件：`frontend/src/main.js`。

```js
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { listHash, listQuery, listScroll, prepareList } from './listNavigation.js'
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
router.beforeResolve((to, from) => { prepareList(to, from) })

createApp(App).use(router).mount('#app')
```

## 5. views/ArticleListView.vue

替换整个文件。保留B1卡片、公开统计、旧请求保护；新增网址watch，取消原来的onMounted(loadArticles)。

文件：`frontend/src/views/ArticleListView.vue`。

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { siteProfile } from '../siteProfile.js'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { finishList, listKey, listQuery, rememberList } from '../listNavigation.js'
import ArticleCard from '../components/ArticleCard.vue'
import SiteStats from '../components/SiteStats.vue'
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

async function loadCategories() {
  try {
    const response = await fetch('/api/categories')

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const data = await response.json()

    if (disposed) return

    articleCategories.value = data
  } catch {
    if (disposed) return

    // 分类加载失败不会阻止文章列表显示。
    categoryError.value = '分类加载失败，请刷新页面重试。'
  }
}

async function loadTags() {
  try {
    const response = await fetch('/api/tags')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const data = await response.json()

    // 页面已卸载时，丢弃尚未完成的标签加载结果。
    if (disposed) return
    tags.value = data
  } catch {
    if (disposed) return
    tagError.value = '标签加载失败，请刷新页面重试。'
  }
}

onMounted(loadCategories)

onMounted(loadTags)

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

function selectTag(id) {
  tagId.value = id
  filtersExpanded.value = true
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
          <a class="v2-action" href="#entries" @click.prevent="selectType('DIARY')">浏览日记</a>
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
                       :featured="page === 1 && index === 0" :return-to="route.fullPath" @select-tag="selectTag" />
        </div>
        <ArticlePagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading" @change="changePage" />
      </section>

      <aside class="v2-sidebar" aria-label="作者与内容导航">
        <section class="v2-profile">
          <span class="v2-avatar" aria-hidden="true">f</span>
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
          <a v-for="category in articleCategories" :key="category.id" href="#entries"
             @click.prevent="selectCategory(category.id)">{{ category.name }} <span aria-hidden="true">→</span></a>
          <RouterLink to="/archive">浏览归档 <span aria-hidden="true">→</span></RouterLink>
          <RouterLink to="/resources">资源收藏 <span aria-hidden="true">→</span></RouterLink>
        </section>
        <p class="v2-side-note">不必一次照亮整条路，<br>记住今天走过的这一段。</p>
      </aside>
    </div>
  </div>
</template>
```

## 6. components/ArticleCard.vue

替换整个文件。封面、标题、箭头三处链接共用同一个目标。

文件：`frontend/src/components/ArticleCard.vue`。

```vue
<script setup>
import { computed, ref, watch } from 'vue'
import noteCover from '../assets/note.svg'
import thoughtCover from '../assets/thought.svg'
import diaryCover from '../assets/diary.svg'

const props = defineProps({
  article: { type: Object, required: true },
  // 仅用于首篇版式强调；是否置顶仍以article.pinned为准。
  featured: { type: Boolean, default: false },
  // 正文链接携带当前列表网址，刷新详情后仍可返回相同条件。
  returnTo: { type: String, default: '/' },
})
const emit = defineEmits(['select-tag'])
const names = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
const covers = { NOTE: noteCover, THOUGHT: thoughtCover, DIARY: diaryCover }
// 自定义封面加载失败时回退到栏目图；切换地址后允许重新尝试。
const coverFailed = ref(false)
watch(() => props.article.coverUrl, () => { coverFailed.value = false })
const cover = computed(() =>
    !coverFailed.value && props.article.coverUrl
        ? props.article.coverUrl
        : (covers[props.article.type] ?? noteCover)
)
const path = computed(() => ({ path: `/articles/${props.article.id}`, query: { from: props.returnTo } }))
</script>

<template>
  <article class="v2-post" :class="{ featured }">
    <RouterLink class="v2-cover" :to="path" :aria-label="`阅读：${article.title}`">
      <!-- 固定栏目图是装饰；文章标题已由链接的无障碍名称提供。 -->
      <img :src="cover" alt="" width="480" height="300" :loading="featured ? 'eager' : 'lazy'"
           referrerpolicy="no-referrer" @error="coverFailed = true">
    </RouterLink>
    <div class="v2-post-body">
      <div class="v2-meta"><span v-if="article.pinned" class="v2-pin">置顶</span><span class="v2-badge">{{ names[article.type] ?? '文章' }}</span><span>{{ article.categoryName ?? '未分类' }}</span></div>
      <h3><RouterLink :to="path">{{ article.title }}</RouterLink></h3>
      <p class="v2-summary">{{ article.summary || '打开文章，继续阅读。' }}</p>
      <time class="v2-meta" :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) ?? '发布时间未记录' }}</time>
      <time v-if="article.updatedAt" class="v2-meta" :datetime="article.updatedAt">
        更新于 {{ article.updatedAt.slice(0, 10) }}
      </time>
      <div class="v2-post-bottom">
        <div class="v2-tags" aria-label="文章标签">
          <!-- 卡片不是整块链接，因此标签按钮不会嵌套在文章链接里。 -->
          <button v-for="tag in article.tags ?? []" :key="tag.id" type="button"
                  @click="emit('select-tag', tag.id)">#{{ tag.name }}</button>
        </div>
        <RouterLink :to="path" class="v2-read-more" :aria-label="`阅读全文：${article.title}`">→</RouterLink>
      </div>
    </div>
  </article>
</template>
```

## 7. views/ArticleDetailView.vue

替换整个文件。正文渲染和旧请求保护沿用当前实现。

文件：`frontend/src/views/ArticleDetailView.vue`。

```vue
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listReturn } from '../listNavigation.js'
import MarkdownIt from 'markdown-it'

const route = useRoute()
// 不使用任意外部returnUrl；只返回经过校验的本站列表。
const returnLocation = computed(() => listReturn(route.query.from))
const article = ref(null)
const loading = ref(false)
const error = ref('')

// 每个组件实例维护自己的请求编号。
let latestRequestId = 0

onBeforeUnmount(() => {
  latestRequestId += 1
})

// 不允许 Markdown 中的原始 HTML 直接进入页面。
const markdown = new MarkdownIt({ html: false, linkify: true })
const contentHtml = computed(() =>
    markdown.render(article.value?.contentMarkdown ?? '')
)

async function loadArticle(id) {
  const requestId = ++latestRequestId

  loading.value = true
  error.value = ''
  article.value = null

  try {
    const response = await fetch(
        `/api/articles/${encodeURIComponent(id)}`
    )

    if (response.status === 404) {
      throw new Error('文章不存在或尚未发布')
    }

    if (!response.ok) {
      throw new Error(`请求失败：HTTP ${response.status}`)
    }

    const data = await response.json()

    // 只接受当前最后一次请求的文章内容。
    if (requestId !== latestRequestId) return

    article.value = data
  } catch (cause) {
    // 防止上一篇文章的错误覆盖当前文章。
    if (requestId !== latestRequestId) return

    error.value = cause instanceof Error
        ? cause.message
        : '加载文章失败'
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
    }
  }
}

// 当网址中的文章 id 改变时，重新读取对应文章。
watch(() => route.params.id, loadArticle, { immediate: true })
</script>

<template>
  <article>
    <RouterLink class="back-link" :to="returnLocation">← 返回文章列表</RouterLink>

    <p v-if="loading">正在加载文章…</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <template v-else-if="article">
      <header class="article-header">
        <small>{{ article.type }} · {{ article.publishedAt?.slice(0, 10) }}</small>
        <h1>{{ article.title }}</h1>
        <p>分类：{{ article.categoryName ?? '未分类' }}</p>
        <p>{{ article.summary }}</p>
        <div v-if="article.tags?.length" class="article-tags" aria-label="文章标签">
  <span v-for="tag in article.tags" :key="tag.id" class="tag-badge">
    #{{ tag.name }}
  </span>
        </div>
      </header>

      <!-- Markdown 转换后的内容；解析器已关闭原始 HTML。 -->
      <div class="markdown-body" v-html="contentHtml"></div>
    </template>
  </article>
</template>
```

## 8. components/SiteHeader.vue

替换整个文件。手机菜单仍按路由变化收起。

文件：`frontend/src/components/SiteHeader.vue`。

```vue
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { siteProfile } from '../siteProfile.js'

const route = useRoute()
// 在首页点击文章/搜索入口时保留条件；其他页面进入时使用全部文章。
const homeQuery = computed(() => route.path === '/' ? route.query : {})
const menuOpen = ref(false)
// 切换页面后收起手机菜单；点击当前页入口也通过 click 收起。
watch(() => route.fullPath, () => { menuOpen.value = false })
</script>

<template>
  <header class="v2-header v2-wrap">
    <RouterLink class="v2-brand" to="/" aria-label="返回首页">
      <span class="v2-mark" aria-hidden="true">f</span>
      <span><strong>{{ siteProfile.siteName }}</strong><small>FIREFLY / FIELD NOTES</small></span>
    </RouterLink>
    <nav id="visitor-nav" class="v2-nav" :class="{ open: menuOpen }" aria-label="网站导航"
         @click="menuOpen = false" @keydown.esc="menuOpen = false">
      <RouterLink to="/" exact-active-class="v2-exact" :class="{ 'is-active': route.path === '/' && route.hash !== '#entries' }"
                  :aria-current="route.path === '/' && route.hash !== '#entries' ? 'page' : null">首页</RouterLink>
      <RouterLink :to="{ path: '/', query: homeQuery, hash: '#entries' }" exact-active-class="v2-exact"
                  :class="{ 'is-active': route.hash === '#entries' || route.path.startsWith('/articles/') }"
                  :aria-current="route.hash === '#entries' ? 'location' : null">文章</RouterLink>
      <RouterLink to="/archive">归档</RouterLink>
      <RouterLink to="/resources">资源</RouterLink>
      <RouterLink to="/about">关于</RouterLink>
    </nav>
    <div class="v2-header-tools">
      <RouterLink class="v2-icon-button" :to="{ path: '/', query: homeQuery, hash: '#article-search' }" aria-label="搜索文章">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/></svg>
      </RouterLink>
      <button class="v2-icon-button v2-menu" type="button" aria-controls="visitor-nav"
              :aria-expanded="menuOpen" :aria-label="menuOpen ? '收起导航菜单' : '展开导航菜单'"
              @click="menuOpen = !menuOpen" @keydown.esc="menuOpen = false">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="menuOpen ? 'm6 6 12 12M18 6 6 18' : 'M4 6h16M4 12h16M4 18h16'"/></svg>
      </button>
    </div>
  </header>
</template>
```

## 9. 启动与自查

全部保存后，按原配置启动Java后端；前端终端进入 `E:\PersonalWebsite2\frontend` 执行 `npm run dev`，使用终端实际显示的地址。下面的网址示例把端口替换成你的前端端口；不要直接在8080打开Vue页面。

| 操作 | 预期 |
| --- | --- |
| 选择笔记、一个现有分类和标签，搜索已有关键词 | 网址出现type/categoryId/tagId/keyword，结果符合条件 |
| 点击第二页，再刷新 | 网址保留page=2，条件和页码恢复；不足两页时会被后端纠正 |
| 修改输入框但不提交，翻页 | 仍按上次已提交关键词加载 |
| 筛选后点击页头搜索，随后收起面板 | 条件保留，搜索锚点可移除；纯锚点变化不重复请求列表 |
| 第二页向下滚动，打开正文，再点正文返回链接 | 回到同一页、同一条件和离开位置 |
| 正文中点击浏览器后退，再前进 | 后退恢复列表位置，前进进入对应正文 |
| 复制正文带from参数的链接，在新标签页打开再返回 | 条件保留；新页面没有旧位置时定位列表区 |
| 打开 `/?type=BAD&page=-1&categoryId=0&unknown=x` | 无效参数被清理，正常显示全部文章 |
| 打开 `/?page=999999` | 最终页码与后端实际结果一致，URL被replace纠正 |
| 打开 `/articles/1?from=https://example.com` | 返回链接指向本站首页；文章1不存在时也不会生成外部跳转 |
| 断开后端后刷新带条件列表，再恢复后端并重试 | 显示加载错误，恢复后可重新加载 |

当前个人库只有3篇公开文章，无法用真实内容覆盖第二页和长列表滚动。助手会用隔离样例测试，不需要你为了验收往个人库填写占位文章。

## 10. 验收与下一步

自动化测试、生产构建与浏览器验收由助手完成。2026-10-17指南预检已通过：7项针对网址和异步位置恢复的测试、9项既有详情/后台/归档/封面/统计回归，以及候选前端生产构建。测试直接读取本指南代码，命令为 `node --test docs/checks/v2-navigation-guide.test.mjs`。

模拟30篇公开文章、列表延迟250ms的浏览器预检已验证：第二页直达、组合筛选、未提交词不影响翻页、第三页刷新恢复、页头搜索保留条件、非法参数清理、超出末页replace纠正、错误重试、正文返回及浏览器后退/前进。返回位置在浏览器像素取整范围内恢复（观测偏差小于3px）；320px展开筛选没有整页横向溢出，外部from地址回退本站首页。证据保存在Git忽略的 `artifacts/v2-navigation-preflight/`。本次使用模拟公开API，不连接个人数据库。

2026-10-18手敲后的实际验收已通过。实际源码前端17项回归及生产构建通过，浏览器验证条件/页码/刷新/前进后退/正文返回位置及手机流程，另完成真实个人库8组只读检查。候选预检和实际验收记录分别保留。

本轮不添加CSS或改视觉参数。第二轮A、B1已验收；B2实际源码与效果共同验收后，再评估第二轮的GitHub Release节点。之后进入文章阅读体验：目录、代码区、图片查看、阅读进度等。
