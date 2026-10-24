import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { legacyListLocation, listScroll, pageHash, pageQuery, prepareList } from './listNavigation.js'
import { prepareReading, readingScroll } from './readingNavigation.js'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import CollectionView from './views/CollectionView.vue'
import ArticleListView from './views/ArticleListView.vue'
import ArticleDetailView from './views/ArticleDetailView.vue'
import './style.css'
// 新配色和布局覆盖基础样式，顺序不能反过来。
import './v2.css'
import './v3.css'
import AdminArticleEditorView from './views/AdminArticleEditorView.vue'
import AdminTaxonomyView from './views/AdminTaxonomyView.vue'
import AdminResourceView from './views/AdminResourceView.vue'
import AdminLayout from './views/AdminLayout.vue'
import AdminLoginView from './views/AdminLoginView.vue'
import AdminArticleListView from './views/AdminArticleListView.vue'
import { guardAdmin } from './adminSession.js'
import './admin.css'
import ResourceView from './views/ResourceView.vue'
import ArchiveView from './views/ArchiveView.vue'
import AboutView from './views/AboutView.vue'
import TopicView from './views/TopicView.vue'
import NotFoundView from './views/NotFoundView.vue'

// 首页、文章库和专题独立；文章详情地址继续兼容第二版。
const router = createRouter({
    history: createWebHistory(),
    // 首页等待列表渲染完成，再执行浏览器后退或正文返回的位置恢复。
    scrollBehavior(to, from, savedPosition) {
        if (/^\/articles\/[^/]+\/?$/.test(to.path)) {
            return readingScroll(to, savedPosition, () => router.currentRoute.value)
        }
        return listScroll(to, from, savedPosition, () => router.currentRoute.value)
    },
    routes: [
        { path: '/', component: HomeView },
        { path: '/articles', component: ArticleListView },
        { path: '/collections/:slug', component: CollectionView },
        { path: '/projects', component: AboutView, props: { projectPage: true } },
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
                { path: 'articles/new', component: AdminArticleEditorView, meta: { requiresAdmin: true, adminTitle: '新建文章', adminSection: 'article' } },
                { path: 'articles/:id/edit', component: AdminArticleEditorView, meta: { requiresAdmin: true, adminTitle: '编辑文章', adminSection: 'article' } },
                { path: 'taxonomy', component: AdminTaxonomyView, meta: { requiresAdmin: true, adminTitle: '分类与标签' } },
                { path: 'resources', component: AdminResourceView, meta: { requiresAdmin: true, adminTitle: '资源管理' } },
                { path: ':pathMatch(.*)*', redirect: '/admin/articles' },
            ],
        },
        { path: '/:pathMatch(.*)*', component: NotFoundView },
    ],
})

// 非法值、重复参数和未知参数统一清理；replace不额外增加浏览器历史。
router.beforeEach(to => {
    const legacy = legacyListLocation(to)
    if (legacy) return legacy
    if (!['/', '/articles', '/topics', '/archive', '/diary'].includes(to.path) && !to.path.startsWith('/collections/')) return
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
