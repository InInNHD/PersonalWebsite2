<script setup>
import { siteProfile } from '../siteProfile.js'
const year = new Date().getFullYear()
</script>

<template>
  <footer class="v2-footer v2-wrap">
    <div><strong>{{ siteProfile.siteName }} · {{ siteProfile.nickname }}</strong><p>{{ siteProfile.tagline }}</p></div>
    <nav aria-label="页脚导航">
      <span>© {{ year }}</span>
      <a v-if="siteProfile.githubUrl" :href="siteProfile.githubUrl" target="_blank" rel="noopener noreferrer">GitHub ↗</a>
      <RouterLink to="/admin">内容管理</RouterLink>
    </nav>
  </footer>
</template>