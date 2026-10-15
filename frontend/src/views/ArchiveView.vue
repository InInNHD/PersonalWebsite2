<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const months = ref([])
const selectedMonth = ref('')
const articles = ref([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')

// 月份目录由后端倒序排列，同一年自然连续，直接按顺序分组。
// computed 会在 months 改变时重新计算，供模板生成 optgroup。
const yearGroups = computed(() => {
  const groups = []
  for (const item of months.value) {
    const year = item.month.slice(0, 4)
    let group = groups.at(-1)
    if (!group || group.year !== year) {
      group = { year, months: [] }
      groups.push(group)
    }
    group.months.push(item)
  }
  return groups
})

// 月份目录与文章请求共同使用这个编号，重试或切换月份使旧请求失效。
let latestRequestId = 0
onBeforeUnmount(() => { latestRequestId += 1 })

async function loadArticles() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({
      month: selectedMonth.value,
      page: String(page.value),
      size: String(pageSize),
    })
    const response = await fetch(`/api/articles?${params.toString()}`)
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    // 等 JSON 解析完成后再判断，旧请求不能覆盖本页内容和页码。
    if (requestId !== latestRequestId) return
    articles.value = data.items
    total.value = data.total
    page.value = data.page
    totalPages.value = data.totalPages
  } catch (cause) {
    if (requestId !== latestRequestId) return
    articles.value = []
    total.value = 0
    totalPages.value = 0
    error.value = cause instanceof Error ? cause.message : '加载归档文章失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

async function loadArchive() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''
  // 重试从最新月份开始，先清空旧目录和列表。
  months.value = []
  selectedMonth.value = ''
  articles.value = []
  page.value = 1
  total.value = 0
  totalPages.value = 0
  try {
    const response = await fetch('/api/articles/archive')
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId) return
    months.value = data
    // 空目录不再发文章请求，避免发送 month= 的非法参数。
    if (data.length === 0) return
    selectedMonth.value = data[0].month
    // 文章加载会取得新编号，并在自己的 finally 中结束 loading。
    await loadArticles()
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '加载归档目录失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

function changeMonth() {
  page.value = 1
  return loadArticles()
}

function changePage(nextPage) {
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  return loadArticles()
}

onMounted(loadArchive)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>文章归档</h1>
        <p>按首次发布时间，回看笔记、心得与公开的生活记录。</p>
      </div>
      <label v-if="months.length">
        归档月份
        <select v-model="selectedMonth" @change="changeMonth">
          <optgroup v-for="group in yearGroups" :key="group.year" :label="`${group.year} 年`">
            <option v-for="item in group.months" :key="item.month" :value="item.month">
              {{ item.month }}（{{ item.total }} 篇）
            </option>
          </optgroup>
        </select>
      </label>
    </div>

    <p v-if="loading" role="status">正在加载归档…</p>
    <div v-else-if="error">
      <p class="error" role="alert">{{ error }}</p>
      <button type="button" @click="loadArchive">重新加载归档</button>
    </div>
    <p v-else-if="months.length === 0">暂时没有已发布的文章。</p>
    <div v-else>
      <p role="status">{{ selectedMonth }} · 共 {{ total }} 篇文章</p>
      <p v-if="articles.length === 0">这个月暂时没有已发布的文章。</p>
      <ul v-else class="archive-list">
        <li v-for="article in articles" :key="article.id">
          <RouterLink :to="`/articles/${article.id}`">
            <time :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) }}</time>
            <span>{{ article.title }}</span>
          </RouterLink>
        </li>
      </ul>
      <nav v-if="totalPages > 0" class="archive-pagination" aria-label="归档分页">
        <button type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </nav>
    </div>
  </section>
</template>

<style scoped>
.archive-list {
  padding: 0;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}
.archive-list li + li { border-top: 1px solid var(--color-border); }
.archive-list a { display: flex; align-items: baseline; gap: 16px; padding: 18px 20px; color: var(--color-text); }
.archive-list a:hover { color: var(--color-accent); }
.archive-list time { flex: 0 0 auto; color: var(--color-muted); font-family: Consolas, monospace; }
.archive-list span { min-width: 0; overflow-wrap: anywhere; }
.archive-pagination { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
@media (max-width: 600px) {
  .archive-list a { flex-direction: column; gap: 6px; }
}
</style>