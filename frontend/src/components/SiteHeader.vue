<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { siteProfile } from '../siteProfile.js'

const route = useRoute()
// 在首页点击文章/搜索入口时保留条件；其他页面进入时使用全部文章。
const homeQuery = computed(() => route.path === '/' ? route.query : {})
const menuOpen = ref(false)
const menuButton = ref(null)
function closeMenu(returnFocus = false) { menuOpen.value = false; if (returnFocus) menuButton.value?.focus() }
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
         @click="closeMenu()" @keydown.esc.stop.prevent="closeMenu(true)">
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
      <button ref="menuButton" class="v2-icon-button v2-menu" type="button" aria-controls="visitor-nav"
              :aria-expanded="menuOpen" :aria-label="menuOpen ? '收起导航菜单' : '展开导航菜单'"
              @click="menuOpen = !menuOpen" @keydown.esc="menuOpen = false">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="menuOpen ? 'm6 6 12 12M18 6 6 18' : 'M4 6h16M4 12h16M4 18h16'"/></svg>
      </button>
    </div>
  </header>
</template>
