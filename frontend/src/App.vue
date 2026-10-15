<script setup>
import { siteProfile } from './siteProfile.js'
</script>

<template>
  <div class="site">
    <header class="site-header">
      <RouterLink class="brand" to="/">{{ siteProfile.siteName }}</RouterLink>
      <nav>
        <RouterLink to="/">文章</RouterLink>
        <RouterLink to="/archive">归档</RouterLink>
        <RouterLink to="/resources">资源</RouterLink>
        <RouterLink to="/about">关于</RouterLink>
        <RouterLink to="/admin">管理</RouterLink>
      </nav>
    </header>

    <!-- 当前网址对应的页面会显示在这里。 -->
    <main class="container">
      <RouterView />
    </main>

    <footer class="site-footer">{{ siteProfile.tagline }}</footer>
  </div>
</template>

<style scoped>
.brand, .site-footer { overflow-wrap: anywhere; }
</style>
