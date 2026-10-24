<script setup>
defineProps({ collections: { type: Array, default: () => [] } })
</script>
<template>
  <section v-if="collections.length" class="v3-paths" aria-labelledby="learning-paths">
    <p class="v2-eyebrow">LEARN / STEP BY STEP</p><h2 id="learning-paths">沿一个专题读下去</h2>
    <article v-for="item in collections" :key="item.slug">
      <h3><RouterLink :to="`/collections/${item.slug}`">{{ item.title }} →</RouterLink></h3>
      <p>{{ item.description }}</p><small>{{ item.articles.length }} 篇公开文章 · 按阅读顺序整理</small>
    </article>
  </section>
</template>
