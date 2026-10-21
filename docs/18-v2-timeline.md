# 第四轮4B：年月归档与公开日记时间线

指南日期：2026-10-19；实际验收：2026-10-20。基线为已完成验收的4A工作区。**用户已完成手敲，4B实际验收通过，无需修正业务代码。** 候选预检与实际验收分别记录，见[4B实际验收记录](V2-TIMELINE-ACCEPTANCE.md)。

## 1. 本轮目标和数据流

- `/archive`展示全部公开文章的年月目录和时间线，按首次发布时间倒序；置顶不改变时间顺序。
- `/diary`只展示已发布的DIARY记录：独立月份和数量，列表采用日期、标题和纯文本摘要；正文沿用第三轮阅读页。
- 桌面按年份分组选择月份，手机使用原生月份下拉框。每页10篇；网址保存`month=YYYY-MM&page=2`，刷新/前进/后退可恢复。首次进入也会用replace补上最新公开月份，避免稍后新增月份改变正文返回的来源。
- 从归档或日记进入正文，再连续阅读后返回，保留原路径、月份、页码和会话内位置。前后篇仍按第三轮规则浏览全站公开文章，不限制为同栏目；返回来源始终保留。
- 草稿即使带有日期，也不进入目录、数量或列表。目录和记录独立失败/重试，不使用旧响应覆盖当前月份。

数据流：`GET /api/articles/archive`（日记追加`?type=DIARY`）→选择公开月份→既有`GET /api/articles?month=...&page=...&size=10`（日记追加`type=DIARY`）→正文携带`from=/archive...`或`from=/diary...`→原来源返回。

**本节不新增数据库迁移、不安装依赖。** 首页原日记栏目筛选继续保留，首屏“浏览日记”改为独立日记页入口。公开内容仍由原写作后台录入；不在个人库自动添加验收样例。

## 2. 手敲顺序

根目录：`E:\PersonalWebsite2`。只输入代码框内的内容，不输入反引号、文件说明或HTML文档标记。

| 顺序 | 文件 | 修改方式 |
| --- | --- | --- |
| 1 | personalwebsite/.../ArticleController.java | 只替换archive方法，其他方法不变 |
| 2 | frontend/src/listNavigation.js | 完整替换，扩展月份参数与返回白名单 |
| 3 | frontend/src/views/ArchiveView.vue | 完整替换，归档/日记复用同一组件 |
| 4 | frontend/src/main.js | 完整替换，增加日记路由与参数规范化 |
| 5 | frontend/src/components/SiteHeader.vue | 完整替换，日记入口及当前导航修正 |
| 6 | frontend/src/views/ArticleListView.vue | 只修改首屏链接并增加侧栏入口 |
| 7 | frontend/src/v2.css | 末尾追加本节样式一次 |

测试由助手维护，你不必手敲tests目录。所有业务步骤完成后再启动验收，避免新路由与旧组件混用。

## 3. 后端：只替换月份目录方法

文件：`personalwebsite/src/main/java/com/firefly/personalwebsite/ArticleController.java`。

找到`public List<ArchiveMonth> archive()`。从它上面的`@GetMapping("/archive")`开始，替换到该方法的结束右花括号；停止在下一段`@GetMapping`和`public ArticlePage list(...)`之前。

保留上方`ArchiveMonth`record和下方list/detail方法。当前文件已有Set、ArrayList、List及RequestParam相关import，不需要新增import。

```java
    @GetMapping("/archive")
    @Transactional(readOnly = true)
    public List<ArchiveMonth> archive(
            @RequestParam(name = "type", required = false) String type) {
        // 不传type时保留全部公开文章目录；日记页面传DIARY。
        // 参数经过校验并绑定到占位符，不把输入拼成SQL。
        if (type != null && !Set.of("NOTE", "THOUGHT", "DIARY").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文章类型");
        }
        String sql = """
            SELECT DATE_FORMAT(published_at, '%Y-%m') AS archive_month,
                   COUNT(*) AS article_count
            FROM article
            WHERE status = 'PUBLISHED' AND published_at IS NOT NULL
            """;
        List<Object> parameters = new ArrayList<>();
        if (type != null) {
            sql += " AND type = ?";
            parameters.add(type);
        }
        sql += """
             GROUP BY DATE_FORMAT(published_at, '%Y-%m')
             ORDER BY archive_month DESC
            """;
        // 只返回月份及公开数量；草稿即使带日期也不会进入目录。
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ArchiveMonth(
                rs.getString("archive_month"), rs.getLong("article_count")), parameters.toArray());
    }
```

## 4. 扩展统一列表导航

文件：`frontend/src/listNavigation.js`。

完整替换此文件。

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

// 主题页仅支持kind/id/page。成对合法才选中主题；非法值回到目录。
export function topicQuery(query = {}) {
    const kind = first(query.kind)
    const id = positiveId(query.id)
    if (!['category', 'tag'].includes(kind) || !id) return {}
    const result = { kind, id }
    const page = listQuery({ page: query.page }).page
    if (page) result.page = page
    return result
}

// 归档和日记只接受月份和页码；不带月份时使用最新公开月份。
export function archiveQuery(query = {}) {
    const result = {}
    const month = String(first(query.month) ?? '')
    if (/^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(month)) result.month = month
    const page = listQuery({ page: query.page }).page
    if (result.month && page) result.page = page
    return result
}

const isList = path => ['/', '/topics', '/archive', '/diary'].includes(path)
export function pageQuery(path, query) {
    if (path === '/topics') return topicQuery(query)
    if (path === '/archive' || path === '/diary') return archiveQuery(query)
    return listQuery(query)
}
// 首页已有调用只传query；缺少path时继续按首页处理。
export const listKey = route => (['/topics', '/archive', '/diary'].includes(route.path) ? route.path.slice(1) + '?' : '')
    + new URLSearchParams(pageQuery(route.path, route.query)).toString()
export const listHash = hash => ['#entries', '#article-search'].includes(hash) ? hash : ''
export const pageHash = (path, hash) => path === '/' ? listHash(hash) : (hash === '#entries' ? hash : '')

// 正文的返回地址只允许本站首页、主题、归档和日记页。外部地址、其他页面和异常参数回退。
export function listReturn(raw) {
    if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return { path: '/' }
    try {
        const url = new URL(raw, 'https://list.invalid')
        if (url.origin !== 'https://list.invalid' || !isList(url.pathname)) return { path: '/' }
        return { path: url.pathname, query: pageQuery(url.pathname, Object.fromEntries(url.searchParams)),
            hash: pageHash(url.pathname, url.hash) }
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
    if (isList(to.path) && from.path === to.path && from.matched?.length && listKey(to) === listKey(from)) return
    pending?.resolve() // 离开或更换目标时释放旧等待者。
    pending = null
    if (isList(to.path)) {
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
    if (isList(to.path)) {
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
        // 回到主题总目录时显示目录顶部，不停在下方的选择提示处。
        if (to.path === '/topics' && !topicQuery(to.query).id && !to.hash) return { top: 0 }
        // 初始化补上最新月份时留在页头；主动选择月份才定位时间线。
        if (['/archive', '/diary'].includes(to.path) && from.path === to.path
            && !archiveQuery(from.query).month && !to.hash) return { top: 0 }
        if (to.hash) return { el: to.hash, top: 24 }
        if (from.path === to.path && isList(from.path) && listKey(to) !== listKey(from)) return { el: '#entries', top: 24 }
    }
    return savedPosition ?? { top: 0 }
}
```

## 5. 归档与公开日记页面

文件：`frontend/src/views/ArchiveView.vue`。

完整替换此文件。

```vue
<script setup>
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
    const response = await fetch(`/api/articles?${params}`)
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
    const response = await fetch(diary.value ? '/api/articles/archive?type=DIARY' : '/api/articles/archive')
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
    <div v-if="directoryLoading" class="v2-state" role="status">正在整理月份目录…</div>
    <div v-else-if="directoryError" class="v2-state" role="alert">
      <p>{{ directoryError }}</p><button type="button" @click="loadArchive">重新加载月份</button>
    </div>
    <template v-else-if="months">
      <p class="timeline-total">{{ diary ? '公开日记' : '公开记录' }} {{ publicTotal }} 篇 · {{ months.length }} 个月</p>
      <div v-if="!months.length" class="v2-state">{{ diary ? '暂时没有公开日记。' : '暂时没有已发布的文章。' }}</div>
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
          <div v-if="!selected" class="v2-state">这个月暂时没有公开记录，请选择其他月份。</div>
          <div v-else-if="loading" class="v2-state" role="status">正在查找这个月的记录…</div>
          <div v-else-if="error" class="v2-state" role="alert">
            <p>{{ error }}</p><button type="button" @click="loadArticles">重新加载记录</button>
          </div>
          <template v-else>
            <p class="topics-note" role="status">{{ selectedMonth }} · 共 {{ total }} 篇{{ diary ? '公开日记' : '公开记录' }}</p>
            <div v-if="!articles.length" class="v2-state">这个月暂时没有公开记录。</div>
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
```

## 6. 路由接入

文件：`frontend/src/main.js`。

完整替换此文件。

```js
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { listScroll, pageHash, pageQuery, prepareList } from './listNavigation.js'
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
import TopicView from './views/TopicView.vue'

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
        { path: '/topics', component: TopicView },
        { path: '/archive', component: ArchiveView },
        { path: '/diary', component: ArchiveView },
        { path: '/resources', component: ResourceView },
        { path: '/about', component: AboutView },
        { path: '/admin', component: AdminView },

    ],
})

// 非法值、重复参数和未知参数统一清理；replace不额外增加浏览器历史。
router.beforeEach(to => {
    if (!['/', '/topics', '/archive', '/diary'].includes(to.path)) return
    const query = pageQuery(to.path, to.query)
    const hash = pageHash(to.path, to.hash)
    if (JSON.stringify(to.query) !== JSON.stringify(query) || to.hash !== hash) {
        return { path: to.path, query, hash, replace: true }
    }
})
router.beforeResolve((to, from) => {
    prepareList(to, from)
    prepareReading(to, from)
})

createApp(App).use(router).mount('#app')
```

## 7. 页头导航

文件：`frontend/src/components/SiteHeader.vue`。

完整替换此文件。

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
                  :class="{ 'is-active': (route.path === '/' && route.hash === '#entries') || route.path.startsWith('/articles/') }"
                  :aria-current="route.path === '/' && route.hash === '#entries' ? 'location' : null">文章</RouterLink>
      <RouterLink to="/topics">主题</RouterLink>
      <RouterLink to="/archive">归档</RouterLink>
      <RouterLink to="/diary">日记</RouterLink>
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

## 8. 首页：两处小修改

文件：`frontend/src/views/ArticleListView.vue`。

### 8.1 首屏日记入口

在template中的`v2-hero-actions`内，找到以下整行：

```vue
          <a class="v2-action" href="#entries" @click.prevent="selectType('DIARY')">浏览日记</a>
```

替换为：

```vue
          <RouterLink class="v2-action" to="/diary">浏览日记</RouterLink>
```

保留前一行“阅读笔记”、下面的栏目按钮及`selectType()`方法。这里的RouterLink沿用项目已有的全局路由组件，不新增import。

### 8.2 侧栏日记入口

在侧栏“在这里探索”下，找到现有归档入口：

```vue
          <RouterLink to="/archive">浏览归档 <span aria-hidden="true">→</span></RouterLink>
```

保留该行，在紧接的下一行增加：

```vue
          <RouterLink to="/diary">公开日记 <span aria-hidden="true">→</span></RouterLink>
```

## 9. 追加样式

文件：`frontend/src/v2.css`。

保留现有全部样式，在文件末尾追加一次：

```css
/* 第四轮4B：归档与公开日记共用时间导航，日记使用更轻的摘要样式。 */
.timeline-heading { max-width: 760px; padding: 12px 0 30px; }
.timeline-heading h1 { margin: 14px 0; font-size: clamp(32px, 4vw, 46px); }
.timeline-heading > p:last-of-type { color: var(--color-muted); line-height: 1.9; }
.timeline-heading > a { display: inline-flex; align-items: center; min-height: 44px; font-size: 13px; }
.timeline-total { color: var(--color-muted); font-size: 13px; margin: 0 0 28px; }
.timeline-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 48px; align-items: start; }
.timeline-months { min-width: 0; padding: 22px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 14px; }
.timeline-year + .timeline-year { margin-top: 26px; }
.timeline-year h2 { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin: 0 0 12px; font-size: 21px; }
.timeline-year small { color: var(--color-muted); font-size: 12px; font-weight: 400; }
.timeline-year a { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 44px; padding: 8px 10px; border-radius: 7px; color: var(--color-muted); }
.timeline-year a[aria-current="page"], .timeline-year a:hover { color: var(--color-accent); background: var(--color-accent-soft); text-decoration: none; }
.timeline-month-select { display: none; }
.timeline-results { min-width: 0; scroll-margin-top: 24px; }
.timeline-results > header h2 { margin: 10px 0 24px; font-size: 28px; }
.timeline-items { padding: 0; margin: 28px 0; list-style: none; }
.timeline-items > li { position: relative; display: grid; grid-template-columns: 68px minmax(0, 1fr); gap: 24px; padding: 0 0 28px; }
.timeline-items > li::before { content: ''; position: absolute; top: 8px; bottom: -8px; left: 78px; width: 1px; background: var(--color-border); }
.timeline-items > li:last-child::before { bottom: 28px; }
.timeline-items > li::after { content: ''; position: absolute; left: 75px; top: 8px; width: 7px; height: 7px; border-radius: 50%; background: var(--color-accent); }
.timeline-items time { color: var(--color-muted); font: 13px/1.8 Consolas, monospace; }
.timeline-items h3 { font-size: 20px; line-height: 1.6; margin: 5px 0 10px; overflow-wrap: anywhere; }
.timeline-items h3 a { color: var(--color-text); }
.timeline-items h3 a:hover { color: var(--color-accent); }
.timeline-kind { color: var(--color-muted); font-size: 11px; }
.timeline-read { display: inline-flex; min-height: 36px; align-items: center; font-size: 12px; }
.is-diary .timeline-items > li > div { background: var(--color-surface); border: 1px solid var(--color-border); padding: 20px 24px; border-radius: 12px; }
.is-diary .timeline-items h3 { font-size: 21px; margin-top: 0; }
.is-diary .timeline-items p { color: var(--color-muted); font-size: 14px; line-height: 1.9; white-space: pre-line; overflow-wrap: anywhere; }
@media (max-width: 700px) {
  .timeline-layout { grid-template-columns: minmax(0, 1fr); gap: 28px; }
  .timeline-months { padding: 18px; }
  .timeline-month-select { display: grid; gap: 10px; font-size: 13px; color: var(--color-muted); }
  .timeline-month-select select { min-width: 0; width: 100%; }
  .timeline-year { display: none; }
  .timeline-items > li { grid-template-columns: 48px minmax(0, 1fr); gap: 22px; }
  .timeline-items > li::before { left: 57px; }
  .timeline-items > li::after { left: 54px; }
  .timeline-items time { font-size: 11px; }
  .timeline-items h3 { font-size: 18px; }
  .is-diary .timeline-items > li > div { padding: 16px; }
  .is-diary .timeline-items h3 { font-size: 18px; }
}
```

## 10. 运行与验收标准

1. 保存后用Java17重启后端；不要执行SQL。
2. 在`E:\PersonalWebsite2\frontend`运行`npm.cmd run dev`；端口以终端输出为准，无需npm install。
3. 打开`http://localhost:8080/api/articles/archive`与`http://localhost:8080/api/articles/archive?type=DIARY`核对目录；非法type应为400。没有公开日记时返回`[]`是正常结果。
4. 在前端导航分别打开归档和日记。个人库不够多页时不用造假内容，助手将在独立库准备跨年、多页和草稿样例。

| 检查 | 通过标准 |
| --- | --- |
| 年月目录 | 只统计公开文章；日记只统计DIARY；按年份分组，年/月数量与相同条件列表一致 |
| 时间线 | 首次发布时间与编号倒序，置顶不插队；日记只含日记，摘要以普通文本显示 |
| 月份/分页URL | 点击月份回第一页；翻页保留月份；刷新、前进、后退保持条件；超末页同步纠正 |
| 正文与连续阅读 | 从第2页进入正文，点前后篇再返回，仍为原归档/日记、月份、页码和位置 |
| 页面切换 | 归档和日记来回切换，不短暂显示另一页旧目录、计数或列表 |
| 无效/草稿月份 | 非法格式规范化；合法但无公开内容的月份显示通用空状态，不请求草稿内容 |
| 独立错误 | 月份目录失败与文章失败各有重试；文章失败时月份仍可选择；过期响应失效 |
| 手机 | 320/390px显示原生月份选择，时间线不挤压标题，无整页横向溢出 |
| 回归 | 首页组合筛选、4A主题、3A/3B阅读及3C来源传递仍正常 |

## 11. 预检与下一步

2026-10-19候选预检通过，结果如下：

| 检查 | 结果 |
| --- | --- |
| Java17 + 全新独立MySQL库 | 24项后端测试及Maven生产打包通过：既有15项、导航3项、主题3项、本节3项 |
| 指南前端专项 | 7项通过，直接读取本指南代码，覆盖参数/白名单/位置键、月份分页、日记范围、旧响应、独立重试、卸载及来源恢复 |
| 候选既有前端回归 | 39项通过；原41项中两项直接修改旧归档组件字段的测试由本节路由驱动专项取代，实际tests目录待手敲后统一适配 |
| 候选前端构建 | 通过，79个模块；CSS `index-Bwpb40LC.css` 33.03KB，JS `index-C9qU0SM4.js` 319.85KB（gzip120.34KB） |
| 真实HTTP | 7组通过；全站4个月16篇、日记3个月14篇，三月日记12篇分10/2页，草稿月份不公开，置顶不改变时间排序，非法type返回400 |
| 浏览器 | 跨年月份、第二页直达、刷新/前进/后退、末页纠正、草稿月份空状态、两类503重试、归档/日记切换及首页入口通过 |
| 连续阅读返回 | 日记第2页244px、归档第2页253.333328px，阅读下一篇后返回原路径/月/页，滚动前后相同 |
| 手机与宽度 | 320/390/768/1440px无整页横向溢出；手机原生下拉切换到2039-12并刷新正常；摘要中的HTML标签作为普通文字显示 |

预检中已修正导航函数对首页只传query调用的兼容性；首次进入归档/日记补上实际月份时保持页头，主动切月才定位时间线。MySQL样例工具显式设置UTF-8会话后，中文接口核对通过；该设置只作用于临时连接，没有修改数据库全局设置。

实际40个业务源码文件SHA-256前后一致，未安装实际项目依赖，个人库五表校验值未变化，个人内容0写入。临时服务、验收页面、独立库、连接配置及含配置编译产物已清理，视口覆盖已复原。截图和结果保存在Git忽略的`artifacts/v2-timeline-preflight/`。

以上为指南交付时的候选预检记录。2026-10-20实际验收通过：七处代码与指南一致，实际前端46项、实际Java源码隔离副本中24项MySQL测试及两端构建通过。7组真实HTTP及浏览器跨年/分页/历史/月份纠正/独立503重试/四档宽度/手机菜单与月份/首页入口通过，日记244px和归档253.333328px连续阅读返回位置一致。个人库3篇公开归档与数据库一致、无公开日记、1篇草稿详情404，五表校验值未变，0写入。助手已替换旧归档测试并新增读取实际源码的7项专项；40个业务文件SHA256前后一致，临时服务/页面、验收库、连接配置及含配置编译产物已清理。详见[实际验收记录](V2-TIMELINE-ACCEPTANCE.md)。

4B之后继续4C个人展示，再做第四轮整体验收。本节不是新的Release节点，不发布、不部署。
