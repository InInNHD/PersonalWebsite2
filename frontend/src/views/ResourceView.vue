<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ResourceIcon from '../components/ResourceIcon.vue'
const route = useRoute(), router = useRouter()
const resources = ref([]), loading = ref(false), error = ref('')
const search = computed({ get: () => String(route.query.q ?? '').slice(0, 100), set: value => change({ q: value.slice(0, 100) || undefined }) })
const selectedGroup = computed(() => String(route.query.group ?? 'all'))
const groups = computed(() => {
  const result = new Map()
  for (const item of resources.value) {
    const key = item.groupId == null ? 'none' : String(item.groupId)
    if (!result.has(key)) result.set(key, { key, name: item.groupName || '未分组', count: 0 })
    result.get(key).count += 1
  }
  return [...result.values()]
})
const visibleGroups = computed(() => groups.value.filter(group => selectedGroup.value === 'all' || selectedGroup.value === group.key).map(group => ({
  ...group, items: resources.value.filter(item => (item.groupId == null ? 'none' : String(item.groupId)) === group.key && `${item.title} ${item.description} ${item.recommendation ?? ''}`.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())),
})).filter(group => group.items.length))
const visibleCount = computed(() => visibleGroups.value.reduce((count, group) => count + group.items.length, 0))
function change(next) { router.replace({ path: '/resources', query: { ...route.query, ...next } }) }
function httpUrl(value) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url : null } catch { return null }
}
let latestRequestId = 0
onBeforeUnmount(() => { latestRequestId += 1 })
async function loadResources() {
  const requestId = ++latestRequestId
  loading.value = true; error.value = ''
  try {
    const response = await fetch('/api/resources', { signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId) return
    resources.value = data.map(item => { const url = httpUrl(item.url); return { ...item, href: url?.href, host: url?.hostname ?? '地址无效' } })
  } catch (cause) { if (requestId === latestRequestId) error.value = cause.message }
  finally { if (requestId === latestRequestId) loading.value = false }
}
onMounted(loadResources)
</script>
<template>
  <section class="resource-library">
    <header class="resource-hero"><p class="v2-eyebrow">COLLECT / SHARE / LEARN</p><h1>值得收藏的小工具与好资料。</h1><p>按用途整理，留下一点使用体会。愿这些收藏也能帮到你。</p><span>{{ resources.length }} 项公开收藏 · 链接将在新标签页打开</span></header>
    <p v-if="loading" role="status">正在整理资源…</p>
    <div v-else-if="error" class="v2-state"><p class="error" role="alert">{{ error }}</p><button type="button" @click="loadResources">重新加载</button></div>
    <p v-else-if="!resources.length" class="v2-state">暂时没有公开的资源。</p>
    <template v-else>
      <div class="resource-controls"><nav aria-label="资源分组"><button type="button" :aria-pressed="selectedGroup === 'all'" @click="change({ group: undefined })">全部 {{ resources.length }}</button><button v-for="group in groups" :key="group.key" type="button" :aria-pressed="selectedGroup === group.key" @click="change({ group: group.key })">{{ group.name }} {{ group.count }}</button></nav><label>在当前分组中搜索<input v-model="search" type="search" maxlength="100" placeholder="名称、说明或推荐理由" /></label></div>
      <p class="v2-meta" role="status">找到 {{ visibleCount }} 项资源</p>
      <div v-if="!visibleCount" class="v2-state"><h2>没有找到匹配资源</h2><p>试试其他关键词，或浏览全部收藏。</p><button type="button" @click="router.replace('/resources')">清空筛选</button></div>
      <section v-for="group in visibleGroups" :key="group.key" class="resource-group"><header><h2>{{ group.name }}</h2><span>{{ group.items.length }} 项收藏</span></header><div class="resource-cards"><article v-for="item in group.items" :key="item.id" class="resource-entry"><div class="resource-entry-top"><span class="resource-icon"><ResourceIcon :name="item.iconKey" /></span><span class="resource-host">{{ item.host }}</span></div><h3>{{ item.title }}</h3><p>{{ item.description || '打开链接，了解更多。' }}</p><blockquote v-if="item.recommendation"><span>为什么推荐</span>{{ item.recommendation }}</blockquote><a v-if="item.href" :href="item.href" target="_blank" rel="noopener noreferrer">访问资源 <span aria-hidden="true">↗</span><span class="sr-only">（新标签页）</span></a><span v-else class="error">链接地址无效</span></article></div></section>
    </template>
  </section>
</template>
<style scoped>
.resource-hero { padding:32px 0 42px; border-bottom:1px solid var(--color-border); }
.resource-hero h1 { max-width:760px; font-size:clamp(28px,4vw,44px); line-height:1.4; letter-spacing:-1px; margin:18px 0; }
.resource-hero > p:not(:first-child), .resource-hero > span { color:var(--color-muted); line-height:1.9; }
.resource-hero > span { font-size:12px; }
.resource-controls { display:flex; justify-content:space-between; gap:24px; margin:32px 0 20px; align-items:end; }
.resource-controls nav { display:flex; flex-wrap:wrap; gap:8px; }
.resource-controls button[aria-pressed="true"] { background:var(--color-accent-soft); border-color:var(--color-accent); color:var(--color-accent); }
.resource-controls label { min-width:220px; font-size:12px; color:var(--color-muted); }
.resource-group { margin:36px 0 48px; }
.resource-group > header { display:flex; align-items:center; gap:18px; margin-bottom:20px; }
.resource-group h2 { margin:0; font-size:23px; }
.resource-group > header > span { color:var(--color-muted); font-size:12px; }
.resource-cards { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:20px; }
.resource-entry { display:flex; flex-direction:column; padding:24px; border:1px solid var(--color-border); border-radius:14px; background:var(--color-surface); overflow-wrap:anywhere; min-width:0; }
.resource-entry-top { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.resource-icon { display:inline-flex; padding:12px; border-radius:12px; background:var(--color-accent-soft); color:var(--color-accent); }
.resource-host { color:var(--color-muted); font-size:11px; min-width:0; }
.resource-entry h3 { margin:22px 0 10px; font-size:20px; }
.resource-entry > p { color:var(--color-muted); line-height:1.8; margin:0 0 20px; }
.resource-entry blockquote { margin:0 0 20px; border-left:2px solid var(--color-accent); padding:4px 0 4px 14px; font-size:13px; line-height:1.9; }
.resource-entry blockquote span { display:block; color:var(--color-muted); font-size:10px; margin-bottom:4px; }
.resource-entry a { display:flex; justify-content:space-between; align-items:center; min-height:44px; margin-top:auto; color:var(--color-accent); border-top:1px solid var(--color-border); padding-top:12px; }
.sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip-path:inset(50%); }
@media(max-width:1000px) { .resource-cards { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media(max-width:600px) { .resource-controls { display:block; } .resource-controls label { display:block; margin-top:22px; } .resource-cards { grid-template-columns:minmax(0,1fr); } .resource-hero { padding:16px 0 28px; } }
</style>
