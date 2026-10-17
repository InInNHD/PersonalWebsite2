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