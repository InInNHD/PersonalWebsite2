<script setup>
import { computed, nextTick, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import SiteHeader from './components/SiteHeader.vue'
import SiteFooter from './components/SiteFooter.vue'
import { contentMeta, writePageMeta } from './pageMeta.js'

const route = useRoute()
// 后台父路由负责自己的外框；访客端继续使用原页头和页脚。
const isAdmin = computed(() => route.path === '/admin' || route.path.startsWith('/admin/'))
watchEffect(() => writePageMeta(route, contentMeta.value, document))
// 焦点进入新页面主体，但不改变既有列表/正文的滚动恢复结果。
watch(() => route.path, async path => {
  await nextTick()
  if (path === route.path) document.getElementById('main-content')?.focus({ preventScroll: true })
})
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <RouterView v-if="isAdmin" />
  <div v-else class="v2-site">
    <SiteHeader />
    <main id="main-content" tabindex="-1" :class="['v2-main v2-wrap', { 'is-home': route.path === '/' }]"><RouterView /></main>
    <SiteFooter />
  </div>
</template>
