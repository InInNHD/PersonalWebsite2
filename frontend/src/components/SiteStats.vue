<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const stats = ref(null)
const loading = ref(false)
const error = ref('')
let disposed = false
onBeforeUnmount(() => { disposed = true })

async function loadStats() {
  // 同一时间只发一次请求，重复点击重试不会引起响应竞争。
  if (loading.value || disposed) return
  loading.value = true
  error.value = ''
  try {
    const response = await fetch('/api/site-stats')
    if (!response.ok) throw new Error('统计暂时无法加载')
    const data = await response.json()
    if (!disposed) stats.value = data
  } catch {
    if (!disposed) {
      stats.value = null
      error.value = '统计暂时无法加载'
    }
  } finally {
    if (!disposed) loading.value = false
  }
}
onMounted(loadStats)
</script>

<template>
  <section class="v2-stats" aria-label="公开内容统计" :aria-busy="loading">
    <p v-if="loading" role="status">正在读取公开内容统计…</p>
    <div v-else-if="error" role="alert">
      <p>{{ error }}</p>
      <button type="button" @click="loadStats">重试统计</button>
    </div>
    <!-- 失败不能展示成0；只有成功取得统计对象时才显示数字。 -->
    <dl v-else-if="stats">
      <div><dt>文章</dt><dd>{{ stats.articles }}</dd></div>
      <div><dt>分类</dt><dd>{{ stats.categories }}</dd></div>
      <div><dt>标签</dt><dd>{{ stats.tags }}</dd></div>
      <div><dt>资源</dt><dd>{{ stats.resources }}</dd></div>
    </dl>
  </section>
</template>