<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminRequest, adminListQuery, notifyAdmin } from '../adminSession.js'
import ArticlePagination from '../components/ArticlePagination.vue'
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue'
import { useAdminLeave } from '../useAdminLeave.js'
const route = useRoute(), router = useRouter()
const query = computed(() => adminListQuery(route.query))
const filters = reactive({ keyword: '', status: '', type: '', categoryId: '' })
const result = ref(null), categories = ref([]), loading = ref(false), busy = ref(false), error = ref(''), taxonomyError = ref('')
const confirmation = ref(null)
const recycled = computed(() => query.value.recycled === 'true')
const typeNames = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
const { discardOpen, resolveDiscard, navigateSaved } = useAdminLeave(ref(false), busy)
let latestRequestId = 0, disposed = false
onBeforeUnmount(() => { latestRequestId += 1; disposed = true })
async function loadArticles() {
  const id = ++latestRequestId
  loading.value = true; error.value = ''; result.value = null
  try {
    const data = await adminRequest('/articles?' + new URLSearchParams(query.value))
    if (id !== latestRequestId) return
    if (!Array.isArray(data.items)) throw new Error('文章列表格式不正确。')
    result.value = data
    if (String(data.page) !== String(query.value.page ?? 1)) await navigateSaved(() => router.replace({ path: '/admin/articles', query: adminListQuery({ ...query.value, page: String(data.page) }) }))
  } catch (cause) { if (id === latestRequestId) error.value = cause.message }
  finally { if (id === latestRequestId) loading.value = false }
}
async function loadCategories() {
  taxonomyError.value = ''
  try { const data = await adminRequest('/categories'); if (!disposed) categories.value = data }
  catch (cause) { if (!disposed) taxonomyError.value = cause.message }
}
function applyFilters() {
  if (!busy.value) return router.push({ path: '/admin/articles', query: adminListQuery({ ...filters, recycled: query.value.recycled }) })
}
function changePage(page) {
  if (!busy.value && !loading.value) return router.push({ path: '/admin/articles', query: adminListQuery({ ...query.value, page: String(page) }) })
}
function switchRecycle(value) {
  if (!busy.value) return router.push({ path: '/admin/articles', query: value ? { recycled: 'true' } : {} })
}
function editorLocation(id) { return { path: id === 'new' ? '/admin/articles/new' : `/admin/articles/${id}/edit`, query: { from: route.fullPath } } }
const actionNames = { unpublish: '撤回公开', recycle: '移到回收站', restore: '恢复为草稿' }
async function performAction(accepted) {
  const item = confirmation.value
  confirmation.value = null
  if (!accepted || busy.value || !item) return
  busy.value = true; error.value = ''
  try {
    await adminRequest(`/articles/${item.article.id}${item.action === 'recycle' ? '' : '/' + item.action}`, { method: item.action === 'recycle' ? 'DELETE' : 'POST' })
    notifyAdmin(`${actionNames[item.action]}成功：${item.article.title}`)
    await loadArticles()
  } catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}
watch(() => JSON.stringify(query.value), () => {
  Object.assign(filters, { keyword: '', status: '', type: '', categoryId: '' }, query.value)
  loadArticles()
}, { immediate: true })
onMounted(loadCategories)
</script>
<template>
  <section class="studio-articles" aria-labelledby="studio-articles-title">
    <header class="studio-page-heading"><div><p class="studio-eyebrow">YOUR WORDS / IN ONE PLACE</p><h1 id="studio-articles-title">{{ recycled ? '文章回收站' : '文章管理' }}</h1><p>{{ recycled ? '内容仍被保留；恢复后先回到草稿。' : '整理草稿、继续写作，或管理已经公开的记录。' }}</p></div><RouterLink class="studio-primary" :to="editorLocation('new')">＋ 新建文章</RouterLink></header>
    <div class="studio-tabs" role="group" aria-label="文章范围"><button type="button" :aria-pressed="!recycled" :disabled="busy" @click="switchRecycle(false)">文章</button><button type="button" :aria-pressed="recycled" :disabled="busy" @click="switchRecycle(true)">回收站</button></div>
    <form class="studio-filters" @submit.prevent="applyFilters"><fieldset :disabled="busy">
      <label>搜索标题或摘要<input v-model="filters.keyword" maxlength="100" type="search" placeholder="输入关键词" /></label>
      <label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option></select></label>
      <label>栏目<select v-model="filters.type"><option value="">全部栏目</option><option value="NOTE">笔记</option><option value="THOUGHT">心得</option><option value="DIARY">日记</option></select></label>
      <label>分类<select v-model="filters.categoryId"><option value="">全部分类</option><option v-for="item in categories" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select></label><button type="submit">应用筛选</button>
    </fieldset></form>
    <p v-if="taxonomyError" class="error" role="alert">分类读取失败：{{ taxonomyError }} <button type="button" @click="loadCategories">重试分类</button></p>
    <div class="studio-list-toolbar"><p v-if="result" role="status">共 {{ result.total }} 篇匹配记录</p><button type="button" :disabled="loading || busy" @click="loadArticles">刷新列表</button></div>
    <p v-if="loading" class="v2-state" role="status">正在读取文章…</p>
    <div v-else-if="error" class="v2-state" role="alert"><p>{{ error }}</p><button type="button" :disabled="busy" @click="loadArticles">重新加载文章</button></div>
    <template v-else-if="result">
      <p v-if="!result.items.length" class="v2-state">{{ recycled ? '当前条件下没有回收记录。' : '没有匹配的文章，可以调整筛选或新建草稿。' }}</p>
      <ul v-else class="studio-article-list"><li v-for="article in result.items" :key="article.id">
        <div><div class="studio-row-meta"><span :class="['studio-status', { published: !recycled && article.status === 'PUBLISHED' }]">{{ recycled ? '已回收' : article.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span><span>{{ typeNames[article.type] }}</span><span>{{ article.categoryName || '未分类' }}</span><span v-if="article.pinned">置顶</span></div><h2><span v-if="recycled">{{ article.title }}</span><RouterLink v-else :to="editorLocation(article.id)">{{ article.title }}</RouterLink></h2><small>编号 {{ article.id }} · {{ article.updatedAt ? '更新于 ' + article.updatedAt.replace('T', ' ').slice(0, 16) : '尚无更新时间' }}</small></div>
        <div class="studio-row-actions"><template v-if="!recycled"><RouterLink :to="editorLocation(article.id)" :aria-label="`编辑：${article.title}`">继续编辑 →</RouterLink><button v-if="article.status === 'PUBLISHED'" type="button" :disabled="busy" @click="confirmation = { article, action: 'unpublish' }">撤回公开</button><button type="button" :disabled="busy" @click="confirmation = { article, action: 'recycle' }">回收</button></template><button v-else type="button" :disabled="busy" @click="confirmation = { article, action: 'restore' }">恢复为草稿</button></div>
      </li></ul>
      <ArticlePagination :page="result.page" :total-pages="result.totalPages" :loading="loading || busy" @change="changePage" />
    </template>
    <AdminConfirmDialog v-if="confirmation" :title="actionNames[confirmation.action]" :message="`文章：${confirmation.article.title}。${confirmation.action === 'restore' ? '恢复后不会自动公开。' : '操作后访客将无法读取这篇文章。正文和分类标签会保留。'}`" :confirm-text="actionNames[confirmation.action]" @resolve="performAction" />
    <AdminConfirmDialog v-if="discardOpen" title="离开当前页面？" message="有尚未完成的操作。" @resolve="resolveDiscard" />
  </section>
</template>
