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
