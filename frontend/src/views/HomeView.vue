<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { siteProfile } from '../siteProfile.js'
import { finishList, listKey, rememberList } from '../listNavigation.js'
import { usePresentation } from '../usePresentation.js'
import PageState from '../components/PageState.vue'
import ArticleCard from '../components/ArticleCard.vue'
import LearningPaths from '../components/LearningPaths.vue'

const route = useRoute()
const { presentation, presentationLoading, presentationError, loadPresentation } = usePresentation()
const articles = ref([]), loading = ref(false), error = ref('')
const featured = computed(() => presentation.value?.featured ?? [])
const lead = computed(() => featured.value.length ? featured.value : articles.value.slice(0, 1))
let request = 0
onBeforeUnmount(() => { request += 1 })
onBeforeRouteLeave(() => rememberList(route, { left: window.scrollX, top: window.scrollY }))
async function loadRecent() {
  const id = ++request
  loading.value = true; error.value = ''
  try {
    const response = await fetch('/api/articles?size=5', { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    if (id === request) articles.value = data.items
  } catch {
    if (id === request) { articles.value = []; error.value = '公开文章暂时无法加载。' }
  } finally { if (id === request) loading.value = false }
}
onMounted(async () => {
  const key = listKey(route)
  await Promise.all([loadRecent(), loadPresentation()])
  await finishList(key)
})
</script>
<template>
  <div class="v3-home">
    <section class="v3-intro" aria-labelledby="home-title">
      <div><p class="v2-eyebrow">FIREFLY / FIELD NOTES</p>
        <h1 id="home-title">{{ siteProfile.heroTitle }}<br><span>{{ siteProfile.heroAccent }}</span></h1>
        <p>你好，我是 {{ siteProfile.nickname }}。{{ siteProfile.heroDescription }}</p>
        <div class="v3-actions"><RouterLink class="v2-action primary" to="/articles">进入文章库 →</RouterLink><RouterLink class="v2-action" to="/about">认识作者</RouterLink></div>
      </div>
      <aside class="v3-current"><p class="v2-eyebrow">BUILD / IN PUBLIC</p><h2>把学习过程，留在自己的站点里。</h2>
        <p>从前后端接口到内容管理，记录 PersonalWebsite2 的持续迭代。</p>
        <RouterLink to="/projects">项目案例与更新 →</RouterLink>
      </aside>
    </section>
    <section id="entries" class="v3-discover" aria-labelledby="home-featured">
      <div><header class="v3-section-heading"><h2 id="home-featured">{{ featured.length ? '精选阅读' : '公开记录' }}</h2><RouterLink to="/articles">全部文章 →</RouterLink></header>
        <PageState v-if="loading || presentationLoading" kind="loading" title="正在整理公开内容…" />
        <template v-else>
          <PageState v-if="presentationError" compact kind="error" :message="presentationError" title="精选暂不可用" retry-label="重试精选" @retry="loadPresentation" />
          <PageState v-if="error" compact kind="error" :message="error" title="文章暂不可用" retry-label="重试文章" @retry="loadRecent" />
          <div v-if="lead.length" class="v3-featured"><ArticleCard v-for="article in lead" :key="article.id" :article="article" featured return-to="/" /></div>
          <PageState v-else-if="!error" title="第一篇公开记录，还在路上" message="先从项目与资源认识这里。" />
        </template>
      </div>
      <LearningPaths v-if="presentation?.collections.length" :collections="presentation.collections" />
      <aside v-else class="v3-paths"><p class="v2-eyebrow">EXPLORE / YOUR OWN PACE</p><h2>从感兴趣的地方开始</h2><p>按分类阅读笔记，或沿时间回看生活。</p><div class="v3-actions"><RouterLink to="/topics">分类与标签 →</RouterLink><RouterLink to="/diary">公开日记 →</RouterLink><RouterLink to="/resources">资源收藏 →</RouterLink></div></aside>
    </section>
    <section v-if="articles.length" class="v3-recent" aria-labelledby="home-recent"><header class="v3-section-heading"><h2 id="home-recent">最近与置顶</h2><RouterLink to="/archive">按时间回看 →</RouterLink></header>
      <ol class="v3-rows"><li v-for="article in articles" :key="article.id"><div><small>{{ article.pinned ? '置顶 · ' : '' }}{{ article.publishedAt?.slice(0, 10) }}</small><h3><RouterLink :to="{path: `/articles/${article.id}`, query: {from: '/'}}">{{ article.title }}</RouterLink></h3><p>{{ article.summary }}</p></div><span aria-hidden="true">↗</span></li></ol>
    </section>
  </div>
</template>
