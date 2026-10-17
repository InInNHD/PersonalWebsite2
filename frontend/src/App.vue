<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteHeader from './components/SiteHeader.vue'
import SiteFooter from './components/SiteFooter.vue'

const route = useRoute()
// 访客与管理员使用不同外框；管理页面的登录和表单逻辑保持原组件负责。
const isAdmin = computed(() => route.path === '/admin' || route.path.startsWith('/admin/'))
</script>

<template>
  <div class="v2-site">
    <header v-if="isAdmin" class="v2-admin-header">
      <strong>内容管理</strong><RouterLink to="/">← 返回网站</RouterLink>
    </header>
    <SiteHeader v-else />
    <main :class="[isAdmin ? 'container' : 'v2-main v2-wrap', { 'is-home': route.path === '/' }]">
      <RouterView />
    </main>
    <SiteFooter v-if="!isAdmin" />
  </div>
</template>