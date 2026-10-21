# 第四轮4A：分类、标签与内容地图

日期：2026-10-19。基线为已提交的bee1ca9及本地v0.8.0标签。本节是IDEA手敲指南；候选副本预检与手敲后实际验收分别记录。**用户已完成手敲，4A实际验收通过，无需修正业务代码。** 详见[4A验收记录](V2-TOPICS-ACCEPTANCE.md)。

## 1. 本轮顺序与效果

第四轮分为4A分类/标签导航、4B归档与公开日记时间线、4C关于页个人展示。本节只做4A：

- 导航栏增加“主题”，进入`/topics`内容地图；分类和标签各显示名称与公开文章数。
- 点击后进入独立落地状态，例如`/topics?kind=category&id=1`或`/topics?kind=tag&id=2&page=2`，沿用现有卡片、公开列表接口和10篇分页。
- 卡片和正文中的分类、标签都可点击；没有分类时显示普通“未分类”文字，不生成编号为0的假链接。
- 首页的公开分类/标签选项改读新公开目录；后台仍可管理未使用的元数据。目录只包含被PUBLISHED文章使用的项，同一文章可在不同标签中各计一次。
- 主题页、首页的位置记录使用不同键，刷新/后退/前进恢复选择及页码；正文和连续阅读保留来源，返回原主题页的位置。
- 分类/标签不存在、被删除或不再有公开内容时显示通用空状态，不从全量维护接口查询并暴露名称。目录/文章失败各有状态和重试入口，过期请求不能覆盖当前主题。

数据流：`GET /api/topics`聚合公开目录 → URL中的kind/id选择目录项 → `GET /api/articles?categoryId=...`或`tagId=...`分页 → 原ArticleCard → 详情与前后篇 → 经过白名单处理的from返回主题页。

**不新增数据库表或迁移，不安装依赖。** 原`/api/categories`、`/api/tags`仍沿用既有维护语义，管理员表单不受影响；新目录不是新的权限接口，它只为访客筛选公开内容。本节不改归档、日记、关于页或后台写作流程。

## 2. 手敲顺序

根目录`E:\PersonalWebsite2`。仅输入代码框内容，文件说明、三个反引号不要写进代码。

| 顺序 | 文件 | 操作 |
| --- | --- | --- |
| 1 | personalwebsite/.../TopicController.java | 新建完整文件 |
| 2 | frontend/src/listNavigation.js | 完整替换，兼容首页并增加主题返回白名单 |
| 3 | frontend/src/views/TopicView.vue | 新建 |
| 4 | frontend/src/main.js | 完整替换，增加路由与参数规范化 |
| 5 | frontend/src/components/ArticleCard.vue | 完整替换，分类/标签变为独立链接 |
| 6 | frontend/src/views/ArticleDetailView.vue | 替换分类标签区域 |
| 7 | frontend/src/components/ArticleNeighbors.vue | 返回来源改一行 |
| 8 | frontend/src/components/SiteHeader.vue | 加一个导航链接 |
| 9 | frontend/src/views/ArticleListView.vue | 目录读取、移除旧标签按钮回调、增加入口 |
| 10 | frontend/src/v2.css | 末尾追加CSS |

步骤2、3、4、7共同保证新页面的滚动等待及返回路径，建议全部完成后再运行验收。测试仍由助手维护，你不必手敲tests目录。

## 3. 新建 TopicController.java

文件：`personalwebsite/src/main/java/com/firefly/personalwebsite/TopicController.java`。

与现有ArticleController在同一个包内，新建类，不放进旧控制器内部。

```java
package com.firefly.personalwebsite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TopicController {
    private final JdbcTemplate jdbcTemplate;

    public TopicController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 新接口编号使用字符串，避免Java Long在浏览器中丢失精度。
    public record TopicItem(String id, String name, long total) {}
    public record TopicDirectory(List<TopicItem> categories, List<TopicItem> tags) {}

    @GetMapping("/api/topics")
    @Transactional(readOnly = true)
    public TopicDirectory topics() {
        // INNER JOIN只留下被公开文章使用的分类；未分类文章不虚构分类编号。
        var categories = jdbcTemplate.query("""
                SELECT c.id, c.name, COUNT(*) AS total
                FROM category c JOIN article a ON a.category_id = c.id
                WHERE a.status = 'PUBLISHED'
                GROUP BY c.id, c.name ORDER BY total DESC, c.id ASC
                """, (rs, rowNum) -> new TopicItem(
                rs.getString("id"), rs.getString("name"), rs.getLong("total")));

        // 一篇文章有多个标签时，每个标签分别计数，但同一标签内不重复文章。
        // 目录不含空标签和仅被草稿使用的标签，管理端原接口仍可管理所有标签。
        var tags = jdbcTemplate.query("""
                SELECT t.id, t.name, COUNT(DISTINCT a.id) AS total
                FROM tag t JOIN article_tag link ON link.tag_id = t.id
                JOIN article a ON a.id = link.article_id
                WHERE a.status = 'PUBLISHED'
                GROUP BY t.id, t.name ORDER BY total DESC, t.id ASC
                """, (rs, rowNum) -> new TopicItem(
                rs.getString("id"), rs.getString("name"), rs.getLong("total")));
        return new TopicDirectory(categories, tags);
    }
}
```

接口结构示例：

```json
{"categories":[{"id":"1","name":"后端实践","total":3}],"tags":[{"id":"2","name":"Java","total":2}]}
```

没有公开文章使用分类和标签时为`{"categories":[],"tags":[]}`。新接口编号使用字符串，保留Java Long精度；数量为数字。分类按文章数降序、编号升序排列；标签同样排序。无需修改原控制器或安全配置。

## 4. 替换列表导航工具

文件：`frontend/src/listNavigation.js`。

完整替换文件（新文件则完整新建）：

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

const isList = path => path === '/' || path === '/topics'
export const listKey = route => route.path === '/topics'
    ? 'topics?' + new URLSearchParams(topicQuery(route.query)).toString()
    : new URLSearchParams(listQuery(route.query)).toString()
export const listHash = hash => ['#entries', '#article-search'].includes(hash) ? hash : ''

// 正文的返回地址只允许本站首页或主题页。外部地址、其他页面和异常参数回退。
export function listReturn(raw) {
    if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return { path: '/' }
    try {
        const url = new URL(raw, 'https://list.invalid')
        if (url.origin !== 'https://list.invalid' || !isList(url.pathname)) return { path: '/' }
        return { path: url.pathname, query: url.pathname === '/topics'
            ? topicQuery(Object.fromEntries(url.searchParams)) : listQuery(Object.fromEntries(url.searchParams)),
            hash: url.pathname === '/topics' ? (url.hash === '#entries' ? url.hash : '') : listHash(url.hash) }
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
        if (to.hash) return { el: to.hash, top: 24 }
        if (from.path === to.path && isList(from.path) && listKey(to) !== listKey(from)) return { el: '#entries', top: 24 }
    }
    return savedPosition ?? { top: 0 }
}
```

## 5. 新建主题落地页

文件：`frontend/src/views/TopicView.vue`。

完整替换文件（新文件则完整新建）：

```vue
<script setup>
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
    const response = await fetch(`/api/articles?${params}`)
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
    const response = await fetch('/api/topics')
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
    <div v-if="directoryLoading" class="v2-state" role="status">正在整理公开主题…</div>
    <div v-else-if="directoryError" class="v2-state" role="alert">
      <p>{{ directoryError }}</p><button type="button" @click="loadDirectory">重新加载主题</button>
    </div>
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
        <div v-else-if="!selected" class="v2-state">这个主题不存在，或暂时没有公开文章。可以选择其他主题。</div>
        <div v-else-if="loading" class="v2-state" role="status">正在查找相关记录…</div>
        <div v-else-if="error" class="v2-state" role="alert">
          <p>{{ error }}</p><button type="button" @click="loadArticles">重新加载文章</button>
        </div>
        <template v-else>
          <p class="topics-note" role="status">{{ kindName }}「{{ selected.name }}」· 共 {{ total }} 篇公开文章</p>
          <p v-if="!articles.length" class="v2-state">这个主题暂时没有公开文章。</p>
          <div v-else class="v2-grid">
            <ArticleCard v-for="article in articles" :key="article.id" :article="article" :return-to="route.fullPath" />
          </div>
          <ArticlePagination :page="page" :total-pages="totalPages" :loading="loading" @change="changePage" />
        </template>
      </section>
    </template>
  </section>
</template>
```

## 6. 替换路由入口

文件：`frontend/src/main.js`。

完整替换文件（新文件则完整新建）：

```js
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { listHash, listQuery, listScroll, prepareList, topicQuery } from './listNavigation.js'
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
        { path: '/resources', component: ResourceView },
        { path: '/about', component: AboutView },
        { path: '/admin', component: AdminView },

    ],
})

// 非法值、重复参数和未知参数统一清理；replace不额外增加浏览器历史。
router.beforeEach(to => {
    if (!['/', '/topics'].includes(to.path)) return
    const query = to.path === '/topics' ? topicQuery(to.query) : listQuery(to.query)
    const hash = to.path === '/topics' ? (to.hash === '#entries' ? to.hash : '') : listHash(to.hash)
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

## 7. 替换文章卡片

文件：`frontend/src/components/ArticleCard.vue`。

完整替换文件（新文件则完整新建）：

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
      <div class="v2-meta"><span v-if="article.pinned" class="v2-pin">置顶</span><span class="v2-badge">{{ names[article.type] ?? '文章' }}</span><RouterLink v-if="article.categoryId != null" :to="{ path: '/topics', query: { kind: 'category', id: String(article.categoryId) }, hash: '#entries' }">{{ article.categoryName }}</RouterLink><span v-else>未分类</span></div>
      <h3><RouterLink :to="path">{{ article.title }}</RouterLink></h3>
      <p class="v2-summary">{{ article.summary || '打开文章，继续阅读。' }}</p>
      <time class="v2-meta" :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) ?? '发布时间未记录' }}</time>
      <time v-if="article.updatedAt" class="v2-meta" :datetime="article.updatedAt">
        更新于 {{ article.updatedAt.slice(0, 10) }}
      </time>
      <div class="v2-post-bottom">
        <div class="v2-tags" aria-label="文章标签">
          <!-- 分类/标签各自是链接，不嵌套在文章链接中。 -->
          <RouterLink v-for="tag in article.tags ?? []" :key="tag.id"
                      :to="{ path: '/topics', query: { kind: 'tag', id: String(tag.id) }, hash: '#entries' }">#{{ tag.name }}</RouterLink>
        </div>
        <RouterLink :to="path" class="v2-read-more" :aria-label="`阅读全文：${article.title}`">→</RouterLink>
      </div>
    </div>
  </article>
</template>
```

## 8. ArticleDetailView.vue：替换分类标签区域

文件：`frontend/src/views/ArticleDetailView.vue`。

在template中找到`<div class="reading-taxonomy">`。用下面内容替换整个div，包括其开始和结束标签；其他头部、正文和目录均保留：

```vue
        <div class="reading-taxonomy">
          <RouterLink v-if="article.categoryId != null" class="v2-badge"
                      :to="{ path: '/topics', query: { kind: 'category', id: String(article.categoryId) }, hash: '#entries' }">{{ article.categoryName }}</RouterLink>
          <span v-else class="v2-badge">未分类</span>
          <RouterLink v-for="tag in article.tags ?? []" :key="tag.id" class="reading-tag"
                      :to="{ path: '/topics', query: { kind: 'tag', id: String(tag.id) }, hash: '#entries' }">#{{ tag.name }}</RouterLink>
        </div>
```

## 9. ArticleNeighbors.vue：返回路径改一行

文件：`frontend/src/components/ArticleNeighbors.vue`。

在`const returnPath = computed(() => { ... })`内，找到原来的：

```js
  return '/' + (query ? `?${query}` : '') + (origin.hash ?? '')
```

替换成：

```js
  return origin.path + (query ? `?${query}` : '') + (origin.hash ?? '')
```

注释相应改为“from只允许本站首页或主题页及合法参数”。原来的硬编码`/`会把主题来源错误地改成首页，因此这一行不要漏改。白名单校验仍由listReturn统一执行。

## 10. SiteHeader.vue：增加导航

文件：`frontend/src/components/SiteHeader.vue`。

在现有归档链接的前一行增加主题链接，结果为：

```vue
      <RouterLink to="/topics">主题</RouterLink>
      <RouterLink to="/archive">归档</RouterLink>
```

不要重复添加归档链接，其他手机菜单和搜索逻辑保留。

## 11. ArticleListView.vue：四处修改

文件：`frontend/src/views/ArticleListView.vue`。

### 11.1 用一个公开目录请求替换两个全量请求

在script中找到`async function loadCategories() {`，从此行开始，一直删除到`onMounted(loadTags)`这一行结束。这个范围包括loadCategories、loadTags两个完整函数，以及onMounted(loadCategories)、onMounted(loadTags)两处注册。

用下面代码替换上述整个范围（后面的“直接打开链接、刷新、后退/前进”注释和watch保留）：

```js
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
```

### 11.2 删除旧标签按钮回调

原卡片标签现在使用RouterLink，因此把script内这个完整函数删除：

```js
function selectTag(id) {
  tagId.value = id
  filtersExpanded.value = true
  return applyFilters()
}
```

保留紧随其后的selectCategory、clearAll及原筛选表单逻辑。首页下拉框仍可组合筛选。

### 11.3 移除卡片旧事件监听

找到template中ArticleCard引用末尾：

```vue
                       :featured="page === 1 && index === 0" :return-to="route.fullPath" @select-tag="selectTag" />
```

改成：

```vue
                       :featured="page === 1 && index === 0" :return-to="route.fullPath" />
```

### 11.4 增加首页主题入口

找到侧栏的`<h3>在这里探索</h3>`，在其下一行增加：

```vue
          <RouterLink to="/topics">分类与标签 <span aria-hidden="true">→</span></RouterLink>
```

原分类快捷筛选和归档/资源入口仍保留。

## 12. v2.css末尾追加

文件：`frontend/src/v2.css`。

保留原样式，末尾追加一次：

```css
/* 第四轮4A：公开分类/标签目录与落地页。 */
.topics-page { padding-bottom: 36px; min-width: 0; }
.topics-heading { max-width: 750px; padding: 12px 0 30px; }
.topics-heading h1 { font-size: clamp(32px, 4vw, 46px); margin: 14px 0; }
.topics-heading > p:last-child { color: var(--color-muted); line-height: 1.9; }
.topics-directory { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.topics-directory > section { min-width: 0; padding: 24px; border: 1px solid var(--color-border); border-radius: 14px; background: var(--color-surface); }
.topics-directory h2 { margin: 0 0 20px; font-size: 20px; }
.topics-directory h2 small { margin-left: 8px; font-size: 12px; color: var(--color-muted); }
.topics-directory p, .topics-note { color: var(--color-muted); font-size: 13px; line-height: 1.8; }
.topic-links { display: flex; flex-wrap: wrap; gap: 10px; }
.topic-links a { min-width: 0; display: inline-flex; align-items: center; gap: 14px; min-height: 44px; padding: 8px 12px; border: 1px solid var(--color-border); border-radius: 8px; color: var(--color-text); }
.topic-links span { overflow-wrap: anywhere; }
.topic-links small { color: var(--color-muted); white-space: nowrap; }
.topic-links a[aria-current="page"], .topic-links a:hover { border-color: var(--color-accent); background: var(--color-accent-soft); text-decoration: none; }
.topics-results { padding-top: 36px; margin-top: 28px; border-top: 1px solid var(--color-border); scroll-margin-top: 24px; }
.topics-result-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.topics-result-heading h2 { margin: 8px 0 0; font-size: 26px; overflow-wrap: anywhere; }
.topics-result-heading > a { flex: none; min-height: 44px; display: inline-flex; align-items: center; font-size: 13px; }
.v2-tags a { display: inline-flex; align-items: center; min-height: 32px; color: var(--color-accent); font-size: 11px; padding: 4px 7px; border-radius: 5px; background: var(--color-accent-soft); overflow-wrap: anywhere; }
.v2-meta > a { color: var(--color-muted); }
a.reading-tag { display: inline-flex; align-items: center; min-height: 36px; }
@media (max-width: 700px) {
  .topics-directory { grid-template-columns: minmax(0, 1fr); gap: 18px; }
  .topics-directory > section { padding: 20px; }
  .topics-result-heading { align-items: start; flex-direction: column; gap: 8px; }
}
```

## 13. 运行与检查

保存后用Java17重启后端。在前端目录运行`npm.cmd run dev`，无需npm install，也不执行SQL。先打开`http://localhost:8080/api/topics`，再打开前端导航“主题”。端口以你实际启动结果为准。

| 检查 | 通过标准 |
| --- | --- |
| 公开目录 | 名称与数量对应公开文章，不含空项或只用于草稿的项；同一文章可贡献多个不同标签的计数 |
| 分类/标签落地 | 点击后网址保留kind/id，列表只显示该主题内容，10篇分页正常 |
| 入口 | 页头主题、首页侧栏、卡片分类/标签、详情分类/标签均可到达主题页 |
| 连续阅读返回 | 主题第2页进入正文，再点前后篇，返回仍为原主题/页码及已有位置 |
| 刷新/前进后退 | 正确恢复主题与页码；仅锚点变化不重复请求列表 |
| 无效参数 | 错误kind/id、重复参数、无效页码被规范化；不存在的合法编号显示通用无内容状态 |
| 删除或暂无公开内容 | 不暴露全量维护接口的名称；页面仍可回到全部主题 |
| 失败/重试 | 目录和文章加载失败有各自提示/重试；不把失败显示为0篇，也不留下旧列表 |
| 手机 | 320/390px目录和卡片单列，长名称换行，没有整页横向溢出 |
| 回归 | 首页组合筛选、正文目录、复制、图片、后台分类标签维护继续正常 |

日记/归档的独立返回路径留到4B，个人学习方向和项目经历留到4C；缺少资料的关于区块不会编造内容。

## 14. 预检、实际验收与版本

2026-10-19候选副本预检通过：

- Java17专用空MySQL库：21项后端测试（既有15项、3C导航3项、本节公开主题3项）及生产打包通过，写入随测试事务回滚。
- 7项指南专项、候选34项既有前端回归及生产构建通过。专项覆盖参数/Long字符串、首页与主题位置键隔离、目录等待、旧响应、独立失败重试、末页纠正、卸载和连续阅读来源。
- 7组真实HTTP断言验证分类12/1篇、标签12/2篇、多标签计数、草稿专用项不进入公开目录、字符串编号、中文和第2页2条结果。
- 浏览器验证第2页直达、分类/标签切换、刷新/前进后退、页码999纠正、错误kind规范化、草稿专用合法编号的通用空状态、目录及文章503独立重试、首页标签和正文分类入口。
- 主题第2页进入正文并连续阅读后，返回原kind/id/page，滚动前后均为696.6667px。320/390/768/1440px无整页横向溢出，手机目录和卡片单列。
- 预检中确认了“目录可见后再恢复位置”“回全部主题时回到目录顶部”的时机，已包含在交付代码中。
- 实际业务源码SHA256未变化，未安装实际项目依赖，个人库五表校验值未变化。临时进程、专用库、连接配置和含配置编译产物已清理；截图/结果保存在Git忽略的artifacts/v2-topics-preflight。

以上为指南交付时的候选预检记录。2026-10-19用户手敲后的实际验收也已通过：10处业务代码与指南一致，实际前端41项、隔离MySQL后端21项及两端构建通过，7组真实HTTP及浏览器目录/第二页/连续阅读返回/错误重试/四档宽度检查通过。个人库公开分类及标签目前均0项，与数据库统计一致，页面正常显示空状态，五表校验值未变；40个业务文件SHA256前后一致。助手已维护实际测试和验收文档，临时服务、验收库及含配置产物已清理。详见[实际验收记录](V2-TOPICS-ACCEPTANCE.md)。

4A只是第四轮第一节，不作为新的Release节点；4B、4C完成并整轮验收后再提醒版本节点。本次不发布、不部署。
