<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { finishList, listKey, rememberList } from '../listNavigation.js'
import { usePageMeta } from '../pageMeta.js'
import PageState from '../components/PageState.vue'

const route = useRoute()
const collection = ref(null), loading = ref(false), error = ref(''), missing = ref(false)
usePageMeta(() => collection.value?.title || '学习专题', () => collection.value?.description || '按顺序阅读公开笔记。', () => missing.value)
let request = 0
onBeforeUnmount(() => { request += 1 })
onBeforeRouteLeave(() => rememberList(route, { left: window.scrollX, top: window.scrollY }))
async function load() {
  const id = ++request, key = listKey(route)
  loading.value = true; error.value = ''; collection.value = null; missing.value = false
  try {
    const response = await fetch(`/api/collections/${encodeURIComponent(route.params.slug)}`, { signal: AbortSignal.timeout(15000) })
    if (id !== request) return
    if (response.status === 404) { missing.value = true; return }
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    if (id === request) collection.value = data
  } catch { if (id === request) error.value = '专题暂时无法加载，请稍后重试。' }
  finally { if (id === request) { loading.value = false; await finishList(key) } }
}
watch(() => route.params.slug, load, { immediate: true })
</script>
<template>
  <section class="v3-collection"><RouterLink to="/topics">← 内容地图</RouterLink>
    <PageState v-if="loading" kind="loading" title="正在整理阅读顺序…" />
    <PageState v-else-if="error" kind="error" title="专题加载失败" :message="error" retry-label="重新加载专题" @retry="load" />
    <PageState v-else-if="missing" title="这个专题暂不可用" message="专题不存在，或公开内容还不足以组成阅读路径。"><RouterLink to="/articles">浏览全部文章 →</RouterLink></PageState>
    <template v-else-if="collection"><header class="v3-page-heading"><p class="v2-eyebrow">LEARNING PATH / {{ collection.articles.length }} ARTICLES</p><h1>{{ collection.title }}</h1><p>{{ collection.description }}</p></header>
      <ol id="entries" class="v3-rows"><li v-for="(article, index) in collection.articles" :key="article.id"><span class="v3-step">{{ String(index + 1).padStart(2, '0') }}</span><div><h2><RouterLink :to="{path: `/articles/${article.id}`, query: {from: route.path}}">{{ article.title }}</RouterLink></h2><p>{{ article.summary }}</p><small>{{ article.publishedAt?.slice(0, 10) }}</small></div></li></ol>
    </template>
  </section>
</template>
