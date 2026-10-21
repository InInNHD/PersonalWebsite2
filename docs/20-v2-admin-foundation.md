# 第五轮5A：独立管理布局、登录与文章路由

> 历史指南：第五轮5A/5B/5C现已按用户授权直接实现并统一验收。旧AdminView工作区已被独立页面替代，下面的代码仅留作历史参考，**不要再照本指南覆盖当前源码**。实际使用、006迁移和接口变更见[第21节](21-v2-admin-workflow.md)，最终结果见[统一验收](V2-ADMIN-WORKFLOW-ACCEPTANCE.md)。

日期：2026-10-21。以下保留基于本地提交`928a010`的原始5A手敲指南及当时的候选状态。

## 1. 本节目标与完成后的页面

后台从一个长页面分为独立登录、文章管理、新建/编辑、分类标签和资源管理。桌面使用左导航及顶部工具区，手机将导航放到顶部换行。配色沿用既有暗色与萤火绿，访客页面不改布局。

| 地址 | 用途 |
| --- | --- |
| `/admin` | 自动转到文章管理，未登录则进入登录页 |
| `/admin/login` | 独立登录页，仅输入管理员密码 |
| `/admin/articles` | 全部文章列表、草稿/公开计数、新建及编辑入口 |
| `/admin/articles/new` | 新建文章；保存和发布仍使用现有接口 |
| `/admin/articles/编号/edit` | 按编号读取并编辑文章，刷新后先登录再回到这篇文章 |
| `/admin/taxonomy` | 分类与标签管理 |
| `/admin/resources` | 资源列表和编辑 |

数据流：后台地址→路由检查内存登录状态→登录页调用`/api/admin/check`→回到合法原地址→共享请求方法携带Basic认证→写入前取当前CSRF→原控制器保存。退出先经过未保存内容守卫，再POST后端原生退出接口，成功后清空内存认证并显示登录页。

5A先迁移登录、布局和文章列表，AdminView暂作为按任务显示的工作区，后续逐个拆表单。文章列表当前仍读取现有完整管理列表；搜索、状态/栏目/分类筛选与服务端分页放在5B。分类标签仍沿用原生输入框，5B再改站内表单。撤回、回收和恢复在5C实现。

新建文章保存后会显示后端编号，后续保存为PUT；本节仍留在`/new`页面，返回文章管理即可进入正式编号编辑网址。5B会一起完善保存状态与网址同步。刷新前先保存；密码仅在内存中，刷新后需要重新登录。前端守卫用于页面流程，后台接口的鉴权和CSRF仍由Spring Security执行。

本节不新增依赖，不执行数据库迁移，不改真实密码或个人数据库结构。

## 2. 新建共享认证与请求模块

在`E:\PersonalWebsite2\frontend\src`下新建`adminSession.js`。

认证头只保存在模块变量中；切换后台页面不会重复登录，刷新会重置。每次写请求先取新CSRF，204退出响应不解析JSON。generation用于拒绝旧会话的异步结果，超时为20秒。

文件：`frontend/src/adminSession.js`。

```js
import { ref } from 'vue'

// 只在当前标签页内存中保存认证头，刷新后重新登录；不写入本地存储或网址。
export const adminConnected = ref(false)
export const adminLeaving = ref(false)
export const adminSessionError = ref('')
let authorization = ''
let generation = 0

export function clearAdminSession() {
  generation += 1
  authorization = ''
  adminConnected.value = false
}

export async function adminRequest(path, options = {}) {
  const version = generation
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  headers.set('Authorization', authorization)
  headers.set('X-Requested-With', 'XMLHttpRequest') // 失败由页面提示，避免原生Basic弹窗。
  if (options.body) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await adminRequest('/csrf') // 每次写入重新取当前会话token。
    headers.set(csrf.headerName, csrf.token)
  }
  if (version !== generation) throw new Error('登录状态已变化，请重新操作。')
  const response = await fetch(`/api/admin${path}`, { ...options, method, headers, credentials: 'same-origin', signal: options.signal ?? AbortSignal.timeout(20000) })
  if (version !== generation) throw new Error('登录状态已变化，请重新操作。')
  if (!response.ok) {
    if (response.status === 401) clearAdminSession()
    const descriptions = {
      400: '输入校验失败，请检查必填项、长度、链接格式和排序范围。',
      401: '管理员认证失败，当前输入仍保留；请核对密码后重新登录。',
      403: '安全校验失败，请重试；若仍失败，请检查后端日志。',
      404: '记录不存在，请刷新列表。',
      409: path.startsWith('/tags') ? '标签名称已存在，请换一个名称。' : '分类名称已存在，请换一个名称。',
    }
    throw new Error(descriptions[response.status] ?? `请求失败：HTTP ${response.status}`)
  }
  const data = response.status === 204 ? null : await response.json()
  if (version !== generation) throw new Error('登录状态已变化，请重新操作。')
  return data
}

export async function loginAdmin(password) {
  clearAdminSession()
  adminSessionError.value = ''
  const version = generation
  const bytes = new TextEncoder().encode(`admin:${password}`)
  authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`
  try {
    await adminRequest('/check')
    if (version === generation) adminConnected.value = true
  } catch (error) {
    if (version === generation) clearAdminSession()
    throw error
  }
}

// 登录后仅允许回到已实现的后台页面，拒绝外部地址和login自身循环。
export function adminTarget(raw) {
  if (typeof raw !== 'string') return '/admin/articles'
  if (['/admin/articles', '/admin/articles/new', '/admin/taxonomy', '/admin/resources'].includes(raw)) return raw
  const match = raw.match(/^\/admin\/articles\/([1-9]\d{0,18})\/edit$/)
  return match && BigInt(match[1]) <= 9223372036854775807n ? raw : '/admin/articles'
}

export async function guardAdmin(to) {
  if (to.meta.requiresAdmin && !adminConnected.value) {
    return { path: '/admin/login', query: { next: adminTarget(to.path) }, replace: true }
  }
  if (to.meta.adminSection === 'article' && to.params.id && adminTarget(to.path) !== to.path) {
    return { path: '/admin/articles', replace: true }
  }
  // 组件离开守卫先检查未保存内容，确认允许离开后才执行退出。
  if (to.path === '/admin/login' && adminConnected.value) {
    if (adminLeaving.value) return false
    adminLeaving.value = true
    adminSessionError.value = ''
    try {
      await adminRequest('/logout', { method: 'POST' })
      clearAdminSession()
    } catch (error) {
      adminSessionError.value = `退出未完成：${error.message}`
      return false // 后端失败时留在原页，不悄悄丢弃表单。
    } finally { adminLeaving.value = false }
  }
}
```

## 3. 新建独立管理布局

在`frontend/src/views`新建`AdminLayout.vue`。只有后台父路由使用该外框；登录页隐藏左导航。RouterView按路径重建工作区，换文章时重新加载对应编号。

文件：`frontend/src/views/AdminLayout.vue`。

```vue
<script setup>
import { useRoute, useRouter } from 'vue-router'
import { siteProfile } from '../siteProfile.js'
import { adminConnected, adminLeaving, adminSessionError } from '../adminSession.js'
const route = useRoute()
const router = useRouter()
function leaveAdmin() {
  if (!adminLeaving.value) return router.push('/admin/login')
}
</script>

<template>
  <div class="studio-shell" :class="{ 'is-login': route.path === '/admin/login' }">
    <aside v-if="route.path !== '/admin/login'" class="studio-sidebar">
      <RouterLink class="studio-brand" to="/"><span>f</span><strong>{{ siteProfile.siteName }}<small>WRITING STUDIO</small></strong></RouterLink>
      <p class="studio-eyebrow">内容工作台</p>
      <nav aria-label="管理导航">
        <RouterLink to="/admin/articles" :class="{ 'is-current': route.path.startsWith('/admin/articles') }">文章管理</RouterLink>
        <RouterLink to="/admin/taxonomy">分类与标签</RouterLink>
        <RouterLink to="/admin/resources">资源管理</RouterLink>
      </nav>
      <p class="studio-side-note">把今天的想法，<br>整理成下一篇记录。</p>
    </aside>
    <div class="studio-main">
      <header class="studio-topbar">
        <div><p class="studio-eyebrow">FIREFLY / WRITING STUDIO</p><strong>{{ route.meta.adminTitle }}</strong></div>
        <div class="studio-actions"><RouterLink to="/">查看网站 ↗</RouterLink>
          <button v-if="route.path !== '/admin/login'" type="button" :disabled="adminLeaving" @click="leaveAdmin">
            {{ adminLeaving ? '正在退出…' : (adminConnected ? '退出登录' : '重新登录') }}
          </button>
        </div>
      </header>
      <main class="studio-content">
        <p v-if="adminSessionError" class="error" role="alert">{{ adminSessionError }}</p>
        <!-- 按页面路径重建编辑组件，不让上一篇文章的表单残留到下一篇。 -->
        <RouterView :key="route.path" />
      </main>
    </div>
  </div>
</template>
```

## 4. 新建登录页

在相同目录新建`AdminLoginView.vue`。验证期间禁用重复提交及离开，成功后允许跳转；next只接受本站已实现的后台地址，不能填外部网址。

文件：`frontend/src/views/AdminLoginView.vue`。

```vue
<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { adminLeaving, adminTarget, loginAdmin } from '../adminSession.js'
const route = useRoute()
const router = useRouter()
const password = ref('')
const busy = ref(false)
const error = ref('')
let disposed = false
let allowLeave = false
onBeforeRouteLeave(() => !busy.value || allowLeave)
onBeforeUnmount(() => { disposed = true; password.value = '' })
async function connect() {
  if (busy.value || adminLeaving.value) return
  busy.value = true
  error.value = ''
  try {
    await loginAdmin(password.value)
    if (!disposed) {
      allowLeave = true
      await router.replace(adminTarget(route.query.next))
    }
  } catch (cause) { if (!disposed) error.value = cause.message }
  finally { password.value = ''; busy.value = false }
}
</script>

<template>
  <section class="studio-login" aria-labelledby="studio-login-title">
    <p class="studio-eyebrow">A QUIET PLACE TO WRITE</p>
    <h1 id="studio-login-title">回到你的写作空间。</h1>
    <p>整理笔记、留下思考，也收藏愿意公开的生活片段。</p>
    <form @submit.prevent="connect">
      <p>管理员账号：<strong>admin</strong></p>
      <label>管理员密码<input v-model="password" type="password" autocomplete="current-password" required :disabled="busy || adminLeaving" /></label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="busy || adminLeaving">{{ busy ? '正在验证…' : '进入工作台 →' }}</button>
    </form>
    <small>刷新页面后需要重新登录。密码不会保存在本地存储中。</small>
  </section>
</template>
```

## 5. 新建文章管理页

在相同目录新建`AdminArticleListView.vue`。列表使用已有`GET /api/admin/articles`，不请求文章正文；列表失败和重试独立于登录。切走页面及旧响应不会写回结果。

文件：`frontend/src/views/AdminArticleListView.vue`。

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { adminRequest } from '../adminSession.js'
const articles = ref([])
const loading = ref(false)
const error = ref('')
const typeNames = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
const drafts = computed(() => articles.value.filter(item => item.status === 'DRAFT').length)
let latestRequestId = 0
onBeforeUnmount(() => { latestRequestId += 1 })
async function loadArticles() {
  const id = ++latestRequestId
  loading.value = true
  error.value = ''
  try {
    const data = await adminRequest('/articles')
    if (!Array.isArray(data)) throw new Error('文章列表格式不正确。')
    if (id === latestRequestId) articles.value = data
  } catch (cause) { if (id === latestRequestId) error.value = cause.message }
  finally { if (id === latestRequestId) loading.value = false }
}
onMounted(loadArticles)
</script>

<template>
  <section class="studio-articles" aria-labelledby="studio-articles-title">
    <header class="studio-page-heading"><div><p class="studio-eyebrow">YOUR WORDS / IN ONE PLACE</p><h1 id="studio-articles-title">文章管理</h1><p>每篇记录都有自己的位置，草稿可以慢慢完成。</p></div><RouterLink class="studio-primary" to="/admin/articles/new">＋ 新建文章</RouterLink></header>
    <div class="studio-list-toolbar"><p v-if="!loading && !error" role="status">共 {{ articles.length }} 篇 · 草稿 {{ drafts }} 篇 · 已发布 {{ articles.length - drafts }} 篇</p><button type="button" :disabled="loading" @click="loadArticles">刷新列表</button></div>
    <p v-if="loading" class="v2-state" role="status">正在读取文章…</p>
    <div v-else-if="error" class="v2-state" role="alert"><p>{{ error }}</p><button type="button" @click="loadArticles">重新加载文章</button></div>
    <div v-else-if="!articles.length" class="v2-state">还没有文章，从第一篇草稿开始吧。</div>
    <ul v-else class="studio-article-list">
      <li v-for="article in articles" :key="article.id">
        <div><div class="studio-row-meta"><span :class="['studio-status', { published: article.status === 'PUBLISHED' }]">{{ article.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span><span>{{ typeNames[article.type] || '文章' }}</span><span v-if="article.pinned">置顶</span></div><h2><RouterLink :to="`/admin/articles/${article.id}/edit`">{{ article.title }}</RouterLink></h2><small>文章编号 {{ article.id }}</small></div>
        <RouterLink class="studio-edit-link" :to="`/admin/articles/${article.id}/edit`" :aria-label="`编辑：${article.title}`">继续编辑 →</RouterLink>
      </li>
    </ul>
  </section>
</template>
```

## 6. 新建后台样式

在`frontend/src`新建`admin.css`，不用删除或替换`v2.css`。下面仅匹配studio前缀及后台工作区，保持访客端原效果。

文件：`frontend/src/admin.css`。

```css
/* 第五轮5A：独立写作后台。颜色沿用访客端，布局与阅读页分开。 */
.studio-shell { min-height:100vh; display:grid; grid-template-columns:220px minmax(0,1fr); }
.studio-sidebar { padding:32px 22px; border-right:1px solid var(--color-border); background:var(--color-surface); }
.studio-brand { display:flex; align-items:center; gap:12px; color:var(--color-text); }
.studio-brand > span { font:italic 32px Georgia,serif; color:var(--color-accent); }
.studio-brand small { display:block; font-size:9px; letter-spacing:2px; margin-top:5px; }
.studio-eyebrow { font-size:10px; letter-spacing:2px; color:var(--color-accent); }
.studio-sidebar > .studio-eyebrow { margin-top:52px; }
.studio-sidebar nav { display:grid; gap:8px; }
.studio-sidebar nav a { min-height:46px; display:flex; align-items:center; padding:10px 12px; color:var(--color-muted); border-radius:8px; }
.studio-sidebar nav a.router-link-active, .studio-sidebar nav a.is-current { background:var(--color-accent-soft); color:var(--color-accent); }
.studio-side-note { margin-top:60px; line-height:2; font-size:12px; color:var(--color-muted); }
.studio-main { min-width:0; }
.studio-topbar { min-height:94px; padding:20px 36px; border-bottom:1px solid var(--color-border); display:flex; justify-content:space-between; align-items:center; gap:16px; }
.studio-topbar .studio-eyebrow { margin:0 0 5px; }
.studio-actions { display:flex; flex-wrap:wrap; align-items:center; gap:20px; font-size:13px; }
.studio-actions a { min-height:44px; display:inline-flex; align-items:center; }
.studio-content { padding:36px; max-width:1440px; margin:auto; }
.studio-page-heading { display:flex; justify-content:space-between; align-items:center; gap:24px; }
.studio-page-heading h1 { font-size:30px; margin:10px 0; }
.studio-page-heading p:not(.studio-eyebrow) { color:var(--color-muted); }
.studio-primary { display:inline-flex; justify-content:center; align-items:center; min-height:44px; padding:10px 16px; border:1px solid var(--color-accent); border-radius:8px; background:var(--color-accent-soft); white-space:nowrap; }
.studio-list-toolbar { display:flex; align-items:center; justify-content:space-between; gap:16px; margin:30px 0 18px; color:var(--color-muted); font-size:13px; }
.studio-article-list { list-style:none; padding:0; margin:0; border:1px solid var(--color-border); border-radius:12px; overflow:hidden; background:var(--color-surface); }
.studio-article-list > li { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:24px; align-items:center; padding:24px; border-bottom:1px solid var(--color-border); }
.studio-article-list > li:last-child { border:0; }
.studio-article-list h2 { margin:12px 0 8px; font-size:19px; }
.studio-article-list h2 a { color:var(--color-text); }
.studio-row-meta { display:flex; flex-wrap:wrap; align-items:center; gap:12px; color:var(--color-muted); font-size:11px; }
.studio-status { padding:3px 9px; border-radius:5px; background:var(--color-surface-raised); }
.studio-status.published { background:var(--color-accent-soft); color:var(--color-accent); }
.studio-edit-link { display:flex; align-items:center; min-height:44px; font-size:13px; }
.studio-shell.is-login { display:block; }
.studio-login { max-width:520px; margin:5vh auto; padding:36px; background:var(--color-surface); border:1px solid var(--color-border); border-radius:16px; }
.studio-login h1 { font-size:30px; }
.studio-login > p:not(.studio-eyebrow), .studio-login > small { color:var(--color-muted); }
.studio-login form { margin:30px 0 22px; }
.studio-login label { display:block; margin:18px 0; }
.studio-login button { width:100%; }
.studio-content .admin > h1 { font-size:28px; }
.studio-content .admin .article-editor { grid-template-columns:1fr; }
@media(max-width:900px) {
  .studio-shell { grid-template-columns:175px minmax(0,1fr); }
  .studio-sidebar { padding:26px 14px; }
  .studio-topbar { padding:18px 24px; }
  .studio-content { padding:24px; }
}
@media(max-width:600px) {
  .studio-shell { display:block; }
  .studio-sidebar { padding:18px; border-right:0; border-bottom:1px solid var(--color-border); }
  .studio-sidebar > .studio-eyebrow, .studio-side-note { display:none; }
  .studio-sidebar nav { display:flex; flex-wrap:wrap; gap:4px; margin-top:16px; }
  .studio-sidebar nav a { padding:8px 10px; font-size:13px; }
  .studio-topbar { padding:16px 18px; flex-wrap:wrap; }
  .studio-actions { gap:16px; }
  .studio-content { padding:24px 18px; }
  .studio-page-heading { display:block; }
  .studio-page-heading h1 { font-size:26px; }
  .studio-primary { margin-top:12px; }
  .studio-article-list > li { grid-template-columns:1fr; gap:10px; padding:20px 16px; }
  .studio-login { margin:12px 0; padding:24px 18px; }
  .studio-login h1 { font-size:26px; }
}
```

## 7. 修改后台外框与路由

### 7.1 完整替换App.vue

去掉旧的临时管理头部，后台改由AdminLayout负责。保留访客端SiteHeader/SiteFooter及首页样式类。

文件：`frontend/src/App.vue`。

```vue
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteHeader from './components/SiteHeader.vue'
import SiteFooter from './components/SiteFooter.vue'

const route = useRoute()
// 后台父路由负责自己的外框；访客端继续使用原页头和页脚。
const isAdmin = computed(() => route.path === '/admin' || route.path.startsWith('/admin/'))
</script>

<template>
  <RouterView v-if="isAdmin" />
  <div v-else class="v2-site">
    <SiteHeader />
    <main :class="['v2-main v2-wrap', { 'is-home': route.path === '/' }]"><RouterView /></main>
    <SiteFooter />
  </div>
</template>
```

### 7.2 完整替换main.js

本文件保留原访客路由、列表位置与阅读位置处理；只加入后台导入、嵌套路由和后台守卫。文件中的路径、引号和meta字段需要保持一致。

文件：`frontend/src/main.js`。

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
import AdminLayout from './views/AdminLayout.vue'
import AdminLoginView from './views/AdminLoginView.vue'
import AdminArticleListView from './views/AdminArticleListView.vue'
import { guardAdmin } from './adminSession.js'
import './admin.css'
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
        {
            path: '/admin', component: AdminLayout,
            children: [
                { path: '', redirect: '/admin/articles' },
                { path: 'login', component: AdminLoginView, meta: { adminTitle: '管理员登录' } },
                { path: 'articles', component: AdminArticleListView, meta: { requiresAdmin: true, adminTitle: '文章管理' } },
                { path: 'articles/new', component: AdminView, meta: { requiresAdmin: true, adminTitle: '新建文章', adminSection: 'article' } },
                { path: 'articles/:id/edit', component: AdminView, meta: { requiresAdmin: true, adminTitle: '编辑文章', adminSection: 'article' } },
                { path: 'taxonomy', component: AdminView, meta: { requiresAdmin: true, adminTitle: '分类与标签', adminSection: 'taxonomy' } },
                { path: 'resources', component: AdminView, meta: { requiresAdmin: true, adminTitle: '资源管理', adminSection: 'resources' } },
                { path: ':pathMatch(.*)*', redirect: '/admin/articles' },
            ],
        },

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
router.beforeEach(guardAdmin)
router.beforeResolve((to, from) => {
    prepareList(to, from)
    prepareReading(to, from)
})

createApp(App).use(router).mount('#app')
```

## 8. 修改现有AdminView.vue

文件：`E:\PersonalWebsite2\frontend\src\views\AdminView.vue`。

本节只改下列位置，其余文章/分类/标签/资源的保存函数和表单继续保留。IDEA可用Ctrl+F查找给出的唯一锚点。末尾提供完整对照，原有未变化部分无需重敲。

### 8.1 替换vue-router导入并加入共享请求

找到原`import { onBeforeRouteLeave } from 'vue-router'`这一行，替换为：

```js
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { adminRequest as request } from '../adminSession.js'
```

### 8.2 替换文件顶部旧登录状态

从`const password = ref('')`开始，删除到`let authorization = ''`为止，包括它前面的认证注释。保留下方`function emptyArticle()`及以后内容。在删除位置写入：

```js
const route = useRoute()
const section = computed(() => route.meta.adminSection)
const busy = ref(false)
const ready = ref(false)
const error = ref('')
const message = ref('')
let disposed = false

// 5A先分开页面入口，原有编辑、分类标签和资源操作继续复用。
// 5B再逐个提取表单组件并完善编辑器，避免一次重写所有写入流程。
```

### 8.3 删除组件内旧请求方法

从`function canDiscard()`开始，删除该函数、后面的“请求统一携带认证信息”注释以及整个`async function request(path, options = {})`。一直删到`// 操作期间禁用按钮，并将失败原因展示到页面。`前面，**保留该注释和其后的async function run(action)**。

现在request来自第8.1步的共享模块，不能同时保留旧同名函数。

### 8.4 替换旧登录和文章打开函数

在run函数后，找到`async function loadList()`。从这里开始，删除loadList、connect、newArticle、openArticle四个完整函数，删除到`// 首次保存使用 POST；取得 id 后，后续保存使用 PUT。`前面。保留这条注释和persist。原位置写入：

```js
async function loadWorkspace() {
  await run(async () => {
    ready.value = false
    if (section.value === 'article') {
      await loadCategories()
      await loadTags()
      if (route.params.id) fillForm(await request(`/articles/${route.params.id}`))
      else fillForm(emptyArticle())
    } else if (section.value === 'taxonomy') {
      await loadCategories()
      await loadTags()
    } else if (section.value === 'resources') {
      await loadResources()
    }
    if (!disposed) ready.value = true
  })
}
onMounted(loadWorkspace)
```

ready成功后才显示表单；读取不存在的文章时不会显示一个可误保存为空草稿的表单。分类标签页只取分类标签，资源页只取资源，文章页只取分类、标签和选定文章。

### 8.5 删除保存和发布后的旧列表刷新

分别找到`async function save()`和`async function publish()`，各删除其中一行：

```js
await loadList()
```

两处都删除，不用改persist、保存反馈、发布请求或savedSnapshot。新文章管理页在进入时自行重新读取列表。

### 8.6 替换路由离开守卫

从`//// 切换路由和关闭页面时，提醒尚未保存的修改。`开始，删除到`function beforeUnload(event)`前面，**保留beforeUnload整个函数**。原位置改为：

```js
// 路径参数切换也会离开当前文章，和切到其他后台页执行同一保护。
function canLeave() {
  if (busy.value) return false
  return !dirty.value || window.confirm('有未保存的文章或资源修改，是否离开？')
}
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
```

### 8.7 替换卸载清理

找到原来的`onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))`一行，替换为：

```js
onUnmounted(() => { disposed = true; window.removeEventListener('beforeunload', beforeUnload) })
```

保留上方onMounted注册beforeunload的那一行。第8.4新增的onMounted(loadWorkspace)也要保留，两项职责不同。

### 8.8 删除旧登录模板，改为工作区状态

在`<template>`中保留外层`<section class="admin">`。从它里面的`<h1>内容管理</h1>`开始，一直删除到原`<fieldset v-else :disabled="busy">`这一行（包含），**保留下方分类管理section及后续内容**。原位置写入：

```vue
    <h1>{{ route.meta.adminTitle }}</h1>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" aria-live="polite">{{ message }}</p>
    <p v-if="!ready && busy" role="status">正在准备工作区…</p>
    <button v-if="!ready && !busy" type="button" @click="loadWorkspace">重新加载工作区</button>
    <fieldset v-if="ready" :disabled="busy">
```

### 8.9 为三个管理区块加显示条件

只替换三处section开始标签，里面的表单与按钮保持原样：

```vue
<section v-if="section === 'taxonomy'" class="category-admin">
<section v-if="section === 'taxonomy'" class="tag-admin">
<section v-if="section === 'resources'" class="resource-admin">
```

这三行分别对应原category-admin、tag-admin和resource-admin，不是连续写在一起。

### 8.10 删除编辑页中的旧文章列表

找到包含`<button type="button" @click="newArticle">新建文章</button>`的toolbar。**从这个toolbar的外层`<div class="toolbar">`开始**，删除整个toolbar、后面的`<div class="admin-layout">`及其中整个`<aside>`文章列表。删除到`<form @submit.prevent="save">`前面，保留这张文章表单以及原本关闭admin-layout的`</div>`。

原删除位置只写这一行：

```vue
<div v-if="section === 'article'" class="admin-layout article-editor">
```

在紧接着保留的`<form @submit.prevent="save">`内部第一行加入：

```vue
<RouterLink to="/admin/articles">← 返回文章管理</RouterLink>
<p role="status">{{ busy ? '正在处理…' : articleDirty ? '有未保存的修改' : form.id === null ? '尚未创建草稿' : '当前修改已保存' }}</p>
```

再把文章标签空提示“暂时没有标签，请先在上方的标签管理中创建。”改为“暂时没有标签，可到‘分类与标签’页面创建。”。

到此文章页只显示文章编辑表单，分类标签页只显示对应管理区，资源页只显示资源表单。原AdminView末尾style保持不变，新增admin.css会把文章编辑外层设为单列。

<details>
<summary>AdminView完整对照：只用于核对上述改动，无需重敲未变化部分</summary>

文件：`frontend/src/views/AdminView.vue`。

```vue
<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { adminRequest as request } from '../adminSession.js'
import MarkdownIt from 'markdown-it'

const route = useRoute()
const section = computed(() => route.meta.adminSection)
const busy = ref(false)
const ready = ref(false)
const error = ref('')
const message = ref('')
let disposed = false

// 5A先分开页面入口，原有编辑、分类标签和资源操作继续复用。
// 5B再逐个提取表单组件并完善编辑器，避免一次重写所有写入流程。
function emptyArticle() {
  return {
    id: null,
    title: '',
    summary: '',
    coverUrl: '', // 空地址表示使用栏目默认图。
    pinned: false,
    contentMarkdown: '',
    type: 'NOTE',
    status: 'DRAFT',
    categoryId: null,
    tagIds: [], // 保存标签编号；复选框会向这个数组添加或移除编号。
  }
}

const form = reactive(emptyArticle())
const savedSnapshot = ref(JSON.stringify(form))

// 文章和资源分别记录是否修改，避免互相干扰。
const articleDirty = computed(
    () => JSON.stringify(form) !== savedSnapshot.value
)

const resources = ref([])

function emptyResource() {
  return {
    id: null,
    title: '',
    url: '',
    description: '',
    published: false,
    sortOrder: 0,
  }
}

const resourceForm = reactive(emptyResource())
const resourceSnapshot = ref(JSON.stringify(resourceForm))

const resourceDirty = computed(
    () => JSON.stringify(resourceForm) !== resourceSnapshot.value
)

// 离开整个管理页面时，两种内容都要检查。
const dirty = computed(
    () => articleDirty.value || resourceDirty.value
)

const markdown = new MarkdownIt({ html: false, linkify: true })
const preview = computed(() => markdown.render(form.contentMarkdown))

function fillForm(article) {
  Object.assign(form, emptyArticle(), article)

  // 复制数组，让编辑表单拥有自己的标签选择。
  form.tagIds = [...(article.tagIds ?? [])]
  form.coverUrl = article.coverUrl ?? ''
  savedSnapshot.value = JSON.stringify(form)
}

// 操作期间禁用按钮，并将失败原因展示到页面。
async function run(action) {
  if (busy.value) return

  busy.value = true
  error.value = ''
  message.value = ''

  try {
    await action()
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}

async function loadWorkspace() {
  await run(async () => {
    ready.value = false
    if (section.value === 'article') {
      await loadCategories()
      await loadTags()
      if (route.params.id) fillForm(await request(`/articles/${route.params.id}`))
      else fillForm(emptyArticle())
    } else if (section.value === 'taxonomy') {
      await loadCategories()
      await loadTags()
    } else if (section.value === 'resources') {
      await loadResources()
    }
    if (!disposed) ready.value = true
  })
}
onMounted(loadWorkspace)

// 首次保存使用 POST；取得 id 后，后续保存使用 PUT。
async function persist() {
  const isNew = form.id === null

  const result = await request(
      isNew ? '/articles' : `/articles/${form.id}`,
      {
        method: isNew ? 'POST' : 'PUT',
        body: JSON.stringify({
          title: form.title,
          summary: form.summary,
          coverUrl: form.coverUrl.trim() || null,
          pinned: form.pinned,
          contentMarkdown: form.contentMarkdown,
          type: form.type,
          categoryId: form.categoryId,
          tagIds: [...form.tagIds],
        }),
      }
  )

  // 先记录已保存的 id；即使随后刷新列表失败，也不会重复创建。
  form.id = result.id
  form.status = result.status
  savedSnapshot.value = JSON.stringify(form)
}

async function save() {
  await run(async () => {
    await persist()
    message.value = '文章已保存。'
  })
}

async function publish() {
  await run(async () => {
    // 先保存当前输入，确保发布的内容包含最新修改。
    await persist()
    const result = await request(`/articles/${form.id}/publish`, {
      method: 'POST',
    })

    form.status = result.status
    savedSnapshot.value = JSON.stringify(form)
    message.value = '文章已发布，前台现在可以阅读。'
  })
}

// 路径参数切换也会离开当前文章，和切到其他后台页执行同一保护。
function canLeave() {
  if (busy.value) return false
  return !dirty.value || window.confirm('有未保存的文章或资源修改，是否离开？')
}
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)

function beforeUnload(event) {
  if (dirty.value || busy.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onUnmounted(() => { disposed = true; window.removeEventListener('beforeunload', beforeUnload) })

async function loadResources() {
  resources.value = await request('/resources')
}

function fillResource(resource) {
  Object.assign(resourceForm, resource)

  // 记录保存后的内容，用于识别之后的修改。
  resourceSnapshot.value = JSON.stringify(resourceForm)
}

function canDiscardResource() {
  return !resourceDirty.value
      || window.confirm('资源有未保存的修改，是否放弃？')
}

function newResource() {
  if (busy.value || !canDiscardResource()) return

  fillResource(emptyResource())
  error.value = ''
  message.value = ''
}

function openResource(resource) {
  if (busy.value || !canDiscardResource()) return

  // 管理列表已包含全部编辑字段，直接填入表单。
  // Object.assign 复制字段，编辑表单不会同时改动列表对象。
  fillResource(resource)
  error.value = ''
  message.value = ''
}

async function saveResource() {
  await run(async () => {
    const isNew = resourceForm.id === null

    const result = await request(
        isNew ? '/resources' : `/resources/${resourceForm.id}`,
        {
          method: isNew ? 'POST' : 'PUT',
          body: JSON.stringify({
            title: resourceForm.title,
            url: resourceForm.url,
            description: resourceForm.description,
            published: resourceForm.published,
            sortOrder: resourceForm.sortOrder,
          }),
        }
    )

    // 先记录后端返回的编号和内容，再刷新列表。
    // 即使刷新失败，下次保存仍然是修改，不会再次新增。
    fillResource(result)

    message.value = result.published
        ? '资源已保存并公开，刷新资源页即可查看。'
        : '资源已保存为隐藏状态。'

    try {
      await loadResources()
    } catch {
      error.value = '保存已成功，但列表刷新失败，请点击“刷新资源列表”。'
    }
  })
}

async function refreshResources() {
  // 仅刷新左侧列表，保留右侧正在填写的内容。
  await run(loadResources)
}

const articleCategories = ref([])

async function loadCategories() {
  articleCategories.value = await request('/categories')
}

// 分类只有名称一个字段，使用浏览器输入框完成新增和重命名。
async function editCategory(category = null) {
  if (busy.value) return

  const input = window.prompt(
      category ? '修改分类名称' : '输入新分类名称',
      category?.name ?? ''
  )

  // 点击取消时不发送请求。
  if (input === null) return

  await run(async () => {
    const name = input.trim()

    if (!name || name.length > 50) {
      throw new Error('分类名称需要包含 1～50 个字符。')
    }

    const result = await request(
        category ? `/categories/${category.id}` : '/categories',
        {
          method: category ? 'PUT' : 'POST',
          body: JSON.stringify({ name }),
        }
    )

    // 用保存结果更新当前列表，文章下拉框也会立即更新名称。
    articleCategories.value = [
      ...articleCategories.value.filter(item => item.id !== result.id),
      result,
    ].sort((a, b) => a.id - b.id)

    message.value = category ? '分类名称已更新。' : '分类已创建。'
  })
}

const tags = ref([])

async function loadTags() {
  tags.value = await request('/tags')
}

async function editTag(tag = null) {
  if (busy.value) return

  const input = window.prompt(
      tag ? '修改标签名称' : '输入新标签名称',
      tag?.name ?? ''
  )

  // 点击取消时保留当前状态。
  if (input === null) return

  await run(async () => {
    const name = input.trim()

    if (!name || name.length > 50) {
      throw new Error('标签名称需要包含 1～50 个字符。')
    }

    const result = await request(
        tag ? `/tags/${tag.id}` : '/tags',
        {
          method: tag ? 'PUT' : 'POST',
          body: JSON.stringify({ name }),
        }
    )

    // 用后端返回的数据更新列表。
    // 重命名保留原编号，新增则增加一条记录。
    tags.value = [
      ...tags.value.filter(item => item.id !== result.id),
      result,
    ].sort((a, b) => a.id - b.id)

    message.value = tag ? '标签名称已更新。' : '标签已创建。'
  })
}
</script>

<template>
  <section class="admin">
    <h1>{{ route.meta.adminTitle }}</h1>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" aria-live="polite">{{ message }}</p>
    <p v-if="!ready && busy" role="status">正在准备工作区…</p>
    <button v-if="!ready && !busy" type="button" @click="loadWorkspace">重新加载工作区</button>
    <fieldset v-if="ready" :disabled="busy">
      <section v-if="section === 'taxonomy'" class="category-admin">
        <h2>分类管理</h2>

        <button type="button" @click="editCategory()">
          新增分类
        </button>

        <p v-if="articleCategories.length === 0">
          暂时没有分类，可以先创建“Java”或“Vue”。
        </p>

        <ul>
          <li v-for="category in articleCategories" :key="category.id">
            <button type="button" @click="editCategory(category)">
              {{ category.name }} · 重命名
            </button>
          </li>
        </ul>

        <!-- 就近显示反馈，避免再次出现“操作了但看不到提示”。 -->
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="message" role="status">{{ message }}</p>
      </section>

      <section v-if="section === 'taxonomy'" class="tag-admin">
        <h2>标签管理</h2>

        <button type="button" @click="editTag()">
          新增标签
        </button>

        <p v-if="tags.length === 0">
          暂时没有标签，可以先创建“Spring Boot”或“学习记录”。
        </p>

        <ul>
          <li v-for="tag in tags" :key="tag.id">
            <button type="button" @click="editTag(tag)">
              {{ tag.name }} · 重命名
            </button>
          </li>
        </ul>

        <!-- 操作反馈放在标签管理区附近。 -->
        <p v-if="error" class="error" role="alert">
          {{ error }}
        </p>

        <p v-if="message" role="status">
          {{ message }}
        </p>
      </section>

      <div v-if="section === 'article'" class="admin-layout article-editor">
        <form @submit.prevent="save">
          <RouterLink to="/admin/articles">← 返回文章管理</RouterLink>
          <p role="status">{{ busy ? '正在处理…' : articleDirty ? '有未保存的修改' : form.id === null ? '尚未创建草稿' : '当前修改已保存' }}</p>
          <p>
            {{ form.id === null ? '新文章' : `文章编号：${form.id}` }}
            · {{ form.status === 'DRAFT' ? '草稿' : '已发布' }}
          </p>
          <p v-if="form.status === 'PUBLISHED'">
            保存后，修改会立即展示到前台。
          </p>

          <label>
            标题
            <input v-model="form.title" maxlength="200" required />
          </label>

          <label>
            栏目
            <select v-model="form.type">
              <option value="NOTE">笔记</option>
              <option value="THOUGHT">心得</option>
              <option value="DIARY">日记</option>
            </select>
          </label>

          <label>
            分类
            <select v-model="form.categoryId">
              <!-- 冒号保证绑定的是 null，而不是字符串 "null"。 -->
              <option :value="null">未分类</option>

              <option
                  v-for="category in articleCategories"
                  :key="category.id"
                  :value="category.id"
              >
                {{ category.name }}
              </option>
            </select>
          </label>

          <div class="article-tag-picker" role="group" aria-labelledby="article-tags-label">
            <p id="article-tags-label">
              文章标签（可选，最多 20 个）
            </p>

            <p v-if="tags.length === 0">
              暂时没有标签，可到“分类与标签”页面创建。
            </p>

            <div class="tag-options">
              <label v-for="tag in tags" :key="tag.id" class="tag-option">
                <!-- :value 绑定数字编号；不带冒号会变成字符串。 -->
                <input
                    v-model="form.tagIds"
                    type="checkbox"
                    :value="tag.id"
                    :disabled="form.tagIds.length >= 20 && !form.tagIds.includes(tag.id)"
                />
                <span>{{ tag.name }}</span>
              </label>
            </div>

            <p>已选 {{ form.tagIds.length }} 个；全部取消后保存即可清空标签。</p>
          </div>

          <label>
            封面地址（可选）
            <input v-model="form.coverUrl" type="url" maxlength="2048"
                   placeholder="https://example.com/cover.jpg" />
          </label>
          <p class="hint">留空使用栏目配图；图片无法加载时也会自动回退。建议使用稳定的HTTPS图片地址。</p>
          <label class="presentation-pin">
            <input v-model="form.pinned" type="checkbox" />置顶这篇文章
          </label>
          <p class="hint">置顶只影响公开列表顺序，勾选不会自动发布草稿。</p>


          <label>
            摘要
            <textarea v-model="form.summary" maxlength="500" rows="3"></textarea>
          </label>

          <div class="writing">
            <label>
              Markdown 正文
              <textarea v-model="form.contentMarkdown" rows="18" required></textarea>
            </label>

            <div class="preview">
              <strong>正文预览</strong>
              <div class="markdown-body" v-html="preview"></div>
            </div>
          </div>

          <div class="toolbar">
            <button type="submit">
              {{ form.status === 'PUBLISHED' ? '保存公开修改' : '保存草稿' }}
            </button>
            <button
                type="button"
                :disabled="form.status === 'PUBLISHED'"
                @click="publish"
            >
              保存并发布
            </button>
          </div>
        </form>
      </div>
      <section v-if="section === 'resources'" class="resource-admin">
        <h2>资源管理</h2>

        <div class="toolbar">
          <button type="button" @click="newResource">
            新建资源
          </button>
          <button type="button" @click="refreshResources">
            刷新资源列表
          </button>
          <span v-if="resourceDirty">资源有未保存的修改</span>
        </div>

        <div class="admin-layout">
          <aside>
            <h3>全部资源</h3>
            <p v-if="resources.length === 0">暂时没有资源。</p>

            <ul>
              <li v-for="resource in resources" :key="resource.id">
                <button
                    type="button"
                    :class="{ selected: resource.id === resourceForm.id }"
                    @click="openResource(resource)"
                >
                  {{ resource.title }}
                  <small>
                    {{ resource.published ? '公开' : '隐藏' }}
                    · 排序：{{ resource.sortOrder }}
                  </small>
                </button>
              </li>
            </ul>
          </aside>

          <form @submit.prevent="saveResource">
            <p>
              {{
                resourceForm.id === null
                    ? '新资源'
                    : `资源编号：${resourceForm.id}`
              }}
            </p>

            <label>
              资源名称
              <input
                  v-model="resourceForm.title"
                  maxlength="200"
                  required
              />
            </label>

            <label>
              资源链接
              <input
                  v-model="resourceForm.url"
                  type="url"
                  maxlength="2048"
                  placeholder="https://example.com"
                  required
              />
            </label>

            <label>
              资源说明
              <textarea
                  v-model="resourceForm.description"
                  maxlength="500"
                  rows="4"
              ></textarea>
            </label>

            <label>
              排序数字（越小越靠前）
              <input
                  v-model.number="resourceForm.sortOrder"
                  type="number"
                  min="0"
                  max="999999"
                  step="1"
                  required
              />
            </label>

            <label>
              展示状态
              <select v-model="resourceForm.published">
                <option :value="false">隐藏</option>
                <option :value="true">公开</option>
              </select>
            </label>

            <p>点击保存后，内容、排序和展示状态一起生效。</p>

            <button type="submit">
              {{ busy ? '正在保存…' : '保存资源' }}
            </button>
            <!-- 在资源操作附近显示反馈，避免提示被上方的文章编辑区隔开。 -->
            <p v-if="error" class="error" role="alert">
              {{ error }}
            </p>

            <p v-if="message" role="status">
              {{ message }}
            </p>
          </form>
        </div>
      </section>
    </fieldset>
  </section>
</template>

<style scoped>
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
label { display: block; margin-bottom: 16px; }
.login {
  max-width: 400px;
  padding: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}
.toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 20px 0; }
.admin-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 24px; }
.admin-layout > * { min-width: 0; }
.admin-layout > aside, .admin-layout > form {
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}
.admin-layout aside h2, .admin-layout aside h3 { margin-top: 0; }
ul { list-style: none; padding: 0; }
li { margin-bottom: 8px; }
li button { width: 100%; text-align: left; overflow-wrap: anywhere; }
small { display: block; margin-top: 6px; color: var(--color-muted); }
.selected { border-color: var(--color-accent); background: var(--color-accent-soft); color: var(--color-accent); }

/* 外层列表/表单和内层编辑器/预览分别控制，避免平板出现嵌套窄列。 */
.writing { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.writing > * { min-width: 0; }
.writing textarea { font-family: "Cascadia Code", Consolas, monospace; line-height: 1.7; }
.preview { padding: 16px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface-raised); }
.resource-admin { margin-top: 40px; padding-top: 24px; border-top: 1px solid var(--color-border); }
.category-admin, .tag-admin { padding-bottom: 24px; margin-bottom: 24px; border-bottom: 1px solid var(--color-border); }
.category-admin ul, .tag-admin ul { display: flex; flex-wrap: wrap; gap: 8px; }
.category-admin li, .tag-admin li { min-width: 0; max-width: 100%; margin: 0; }
.article-tag-picker { margin-bottom: 16px; }
.tag-options { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  max-width: 100%;
  padding: 6px 10px;
  margin: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-raised);
}
.tag-option input { width: 18px; height: 18px; margin: 0; padding: 0; flex-shrink: 0; }
.tag-option span { min-width: 0; overflow-wrap: anywhere; }
@media (max-width: 1000px) {
  .writing { grid-template-columns: 1fr; }
}
@media (max-width: 900px) {
  .admin-layout { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .admin-layout { gap: 16px; }
  .admin-layout > aside, .admin-layout > form, .login { padding: 16px; }
}
</style>
```

</details>

## 9. 增加真实后端退出

文件：`E:\PersonalWebsite2\personalwebsite\src\main\java\com\firefly\personalwebsite\SecurityConfig.java`。

找到securityFilterChain方法中的这一行：

```java
.formLogin(form -> form.disable());
```

仅将这一行替换为：

```java
.formLogin(form -> form.disable())
                // 使用Spring Security原生退出：校验CSRF、失效会话并清理安全上下文。
                .logout(logout -> logout
                        .logoutUrl("/api/admin/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
```

注意：分号现在位于logout配置最后，formLogin后面不能保留旧分号；不改其他Bean，不关闭CSRF，不改变管理员密码。使用Spring Security原生LogoutFilter失效会话及清理安全上下文，以HTTP204响应前端。

退出顺序依据[Vue Router导航守卫顺序](https://router.vuejs.org/guide/advanced/navigation-guards.html#the-full-navigation-resolution-flow)：组件离开守卫先于全局beforeEach，所以取消未保存提醒时不会发退出请求。后端退出遵循[Spring Security官方说明](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)，写请求继续携带CSRF。

## 10. 保存、启动与本轮检查

先保存9个文件，IDEA重新编译并启动后端，再在PowerShell启动前端：

```powershell
cd E:\PersonalWebsite2\frontend
npm.cmd run dev
```

访问终端给出的前端地址加`/admin`，例如`http://localhost:5173/admin`。不要用后端8080来访问Vue后台页面。本节改过Java安全配置，**后端必须重启**，否则退出会404。

| 检查 | 应出现的结果 |
| --- | --- |
| 未登录直达/admin/resources | 转到登录页，next为资源页；密码正确后回资源页 |
| 密码错误 | 页面内错误，不反复出现浏览器Basic弹窗 |
| 文章管理 | 显示已有草稿/公开文章、中文栏目和新建/编辑入口 |
| 新建→保存→发布→返回列表 | 编号只生成一次，随后保存为更新；发布文章可在访客端读取 |
| 从列表进入已有文章 | 网址有编号，字段、分类标签、封面和置顶正确回显 |
| 刷新编号编辑页 | 先重新登录，再回到同一个编号页面 |
| 编辑页有未保存内容时点其他菜单或退出 | 取消后网址和输入不变；保存期间禁止离开 |
| 退出 | POST退出成功后回登录页；直接进入后台需要重新登录 |
| 分类标签和资源 | 分别独立显示，原新增/改名/保存功能保持可用 |
| 不存在的编号 | 显示错误与重试，不能误显示为新建文章表单 |
| 320/390/768/1440px | 登录、列表和编辑无整体横向溢出，手机管理导航可点 |

你完成后回复“5A完成，检查验收”。测试由助手完成：适配3项旧后台测试，加入本轮实际源码专项，在独立库验收写入和退出，个人库仅只读核对。不要把本节的候选预检记作你手敲后的实际验收。

## 11. 候选预检记录与范围

候选已有54项前端回归、7项5A专项、Java17真实MySQL后端25项及两端构建通过；浏览器确认独立登录/错误反馈/原地址返回、新建保存发布、编号重开及刷新登录返回、列表503重试、退出503停留及重试成功、分类标签独立入口和手机资源保存。登录/列表/编辑三页各在320/390/768/1440px无整体溢出。

内置浏览器的原生confirm交互导致一次工具超时，恢复后页面已退出；本轮不把它视为“取消确认”的浏览器验收。取消分支、保存中阻止离开和同路由换ID由组件专项覆盖，用户手敲后在本机浏览器再次验收；未擅自改成其他确认方式。

实际源码、真实配置及依赖未修改，个人数据库五表校验值不变；专用库与临时服务完成后清理，证据位于Git忽略的artifacts/v2-admin-a-preflight。当前只交付5A指南，不创建新Release。第五轮5A/5B/5C整体完成并验收时，再提醒下一个版本节点。
