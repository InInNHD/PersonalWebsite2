<script setup>
import { useRoute } from 'vue-router'
defineProps({
  items: { type: Array, required: true },
  activeId: { type: String, default: '' },
})
const route = useRoute()
// 保留B2的from查询参数；目录跳转不会触发文章重新请求。
const target = id => ({ path: route.path, query: route.query, hash: `#${id}` })
</script>

<template>
  <nav aria-label="文章目录" class="reading-toc-links">
    <RouterLink v-for="item in items" :key="item.id" :to="target(item.id)"
                :class="{ 'is-child': item.level === 3 }"
                :aria-current="activeId === item.id ? 'location' : null">
      {{ item.title }}
    </RouterLink>
  </nav>
</template>