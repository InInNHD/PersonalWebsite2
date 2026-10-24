<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { listReturn } from '../listNavigation.js'
const props = defineProps({ articleId: { type: [Number, String], required: true }, from: { type: String, default: '' } })
const collection = ref(null), error = ref(''), loading = ref(false)
const origin = computed(() => listReturn(props.from))
const index = computed(() => collection.value?.articles.findIndex(item => String(item.id) === String(props.articleId)) ?? -1)
const previous = computed(() => index.value > 0 ? collection.value.articles[index.value - 1] : null)
const next = computed(() => index.value >= 0 ? collection.value.articles[index.value + 1] : null)
let request = 0
onBeforeUnmount(() => { request += 1 })
async function load() {
  const id = ++request
  collection.value = null; error.value = ''; loading.value = false
  if (!origin.value.path.startsWith('/collections/')) return
  loading.value = true
  try {
    const response = await fetch(`/api${origin.value.path}`, { signal: AbortSignal.timeout(15000) })
    if (response.status === 404) return
    if (!response.ok) throw new Error()
    const data = await response.json()
    if (id === request) collection.value = data
  } catch { if (id === request) error.value = '专题顺序加载失败' }
  finally { if (id === request) loading.value = false }
}
watch(() => [props.articleId, props.from], load, { immediate: true })
</script>
<template>
  <section v-if="loading || error || index >= 0" class="v3-trail" aria-label="专题阅读顺序">
    <p v-if="loading" role="status">正在读取专题顺序…</p>
    <p v-else-if="error" role="alert">{{ error }} <button type="button" @click="load">重试专题顺序</button></p>
    <template v-else><RouterLink :to="origin">{{ collection.title }} · {{ index + 1 }} / {{ collection.articles.length }}</RouterLink>
      <nav class="v3-trail-links"><RouterLink v-if="previous" :to="{path: `/articles/${previous.id}`, query: {from: origin.path}}">← 上一节：{{ previous.title }}</RouterLink><RouterLink v-if="next" :to="{path: `/articles/${next.id}`, query: {from: origin.path}}">下一节：{{ next.title }} →</RouterLink><span v-else>已读到本专题最后一节</span></nav>
    </template>
  </section>
</template>
