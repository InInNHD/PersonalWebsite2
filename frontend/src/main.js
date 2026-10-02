import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ArticleListView from './views/ArticleListView.vue'
import ArticleDetailView from './views/ArticleDetailView.vue'
import './style.css'
import AdminView from './views/AdminView.vue'
import ResourceView from './views/ResourceView.vue'

// 首页显示文章列表；点击文章后通过 id 打开详情。
const router = createRouter({
    history: createWebHistory(),
    routes: [
        { path: '/', component: ArticleListView },
        { path: '/articles/:id', component: ArticleDetailView },
        { path: '/resources', component: ResourceView },
        { path: '/admin', component: AdminView },

    ],
})

createApp(App).use(router).mount('#app')
