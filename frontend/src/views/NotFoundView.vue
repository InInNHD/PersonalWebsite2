<script setup>
import PageState from '../components/PageState.vue'
</script>
<template>
  <PageState kind="not-found" heading="h1" title="这条小路还没有记录" message="链接可能已变更，或地址输入有误。回到熟悉的地方，继续看看吧。">
    <RouterLink class="v2-action primary" to="/">返回首页</RouterLink><RouterLink class="v2-action" to="/archive">浏览文章归档 →</RouterLink>
  </PageState>
</template>
