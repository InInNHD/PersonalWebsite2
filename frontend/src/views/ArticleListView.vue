<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const articles = ref([])
const type = ref('')
const loading = ref(false)
const error = ref('')
const articleCategories = ref([])
const categoryId = ref('')
const categoryError = ref('')
const tags = ref([])
const tagId = ref('') // 空字符串表示全部标签；选择标签后是数字编号。
const tagError = ref('')
const keywordInput = ref('') // 输入框中的文字，尚未提交时不影响翻页。
const keyword = ref('') // 最近一次提交的关键词。
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)

// 分类只在页面进入时加载一次。
// 离开页面后，不再接受这次加载结果。
let disposed = false

// 只用于判断请求先后，不参与页面渲染，因此不需要 ref。
let latestRequestId = 0

// 离开页面时，让尚未结束的请求失效。
onBeforeUnmount(() => {
  latestRequestId += 1
  disposed = true
})

// 栏目值必须与后端 ArticleController 接受的值相同。
const typeOptions = [
  { value: '', label: '全部' },
  { value: 'NOTE', label: '笔记' },
  { value: 'THOUGHT', label: '心得' },
  { value: 'DIARY', label: '日记' },
]

async function loadArticles() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''

  try {
    const params = new URLSearchParams()
    if (type.value) params.set('type', type.value)
    if (categoryId.value !== '') params.set('categoryId', String(categoryId.value))
    if (tagId.value !== '') params.set('tagId', String(tagId.value))
    if (keyword.value) params.set('keyword', keyword.value)
    params.set('page', String(page.value))
    params.set('size', String(pageSize))

    const response = await fetch(`/api/articles?${params.toString()}`)
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    // 列表与总数、页码都必须通过同一个请求编号检查。
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
    error.value = cause instanceof Error ? cause.message : '加载文章列表失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

// 搜索提交与筛选条件变化都从第一页开始。
function applyFilters() {
  page.value = 1
  return loadArticles()
}

function searchArticles() {
  keyword.value = keywordInput.value.trim()
  return applyFilters()
}

function clearSearch() {
  keywordInput.value = ''
  keyword.value = ''
  return applyFilters()
}

function changePage(nextPage) {
  // 加载期间和边界外不发请求，避免重复点击翻页。
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  return loadArticles()
}

async function loadCategories() {
  try {
    const response = await fetch('/api/categories')

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const data = await response.json()

    if (disposed) return

    articleCategories.value = data
  } catch {
    if (disposed) return

    // 分类加载失败不会阻止文章列表显示。
    categoryError.value = '分类加载失败，请刷新页面重试。'
  }
}

async function loadTags() {
  try {
    const response = await fetch('/api/tags')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const data = await response.json()

    // 页面已卸载时，丢弃尚未完成的标签加载结果。
    if (disposed) return
    tags.value = data
  } catch {
    if (disposed) return
    tagError.value = '标签加载失败，请刷新页面重试。'
  }
}

onMounted(loadCategories)

onMounted(loadTags)

onMounted(loadArticles)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
      <h1>文章</h1>
        <p>
          笔记、心得与公开的生活记录。
        </p>
    </div>

      <div class="article-filters">
        <label>
          栏目
          <select v-model="type" @change="applyFilters">
            <option
                v-for="option in typeOptions"
                :key="option.value"
                :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>

        <label>
          分类
          <select v-model="categoryId" @change="applyFilters">
            <option value="">全部分类</option>

            <option
                v-for="category in articleCategories"
                :key="category.id"
                :value="category.id"
            >
              {{ category.name }}
            </option>
          </select>
        </label>
        <label>
          标签
          <select v-model="tagId" @change="applyFilters">
            <option value="">全部标签</option>

            <option v-for="tag in tags" :key="tag.id" :value="tag.id">
              {{ tag.name }}
            </option>
          </select>
        </label>
      </div>

    </div>

    <form class="article-search" @submit.prevent="searchArticles">
      <label>
        搜索标题或摘要
        <input
            v-model="keywordInput"
            type="search"
            maxlength="100"
            placeholder="例如：Java、生活记录"
        >
      </label>
      <button type="submit">搜索</button>
      <button type="button" @click="clearSearch">清空搜索</button>
    </form>

      <p v-if="categoryError" class="error" role="alert">
        {{ categoryError }}
      </p>
    <p v-if="tagError" class="error" role="alert">
      {{ tagError }}
    </p>
    <p v-if="!loading && !error" role="status">共 {{ total }} 篇文章</p>
      <p v-if="loading">正在加载文章...</p>
    <p v-else-if="error" class="error" role="alert">{{ error }}</p>
      <p v-else-if="articles.length === 0">
        当前搜索或筛选条件下没有已发布的文章。
      </p>

    <div v-else class="article-grid">
      <RouterLink
        v-for="article in articles"
        :key="article.id"
        class="article-card"
        :to="`/articles/${article.id}`"
      >
        <small>{{ article.type }}  · {{ article.publishedAt?.slice(0, 10) }}</small>
        <p>{{ article.categoryName ?? '未分类' }}</p>
        <h2>{{ article.title }}</h2>
        <p>{{ article.summary }}</p>
        <div v-if="article.tags?.length" class="article-tags" aria-label="文章标签">
  <span v-for="tag in article.tags" :key="tag.id" class="tag-badge">
    #{{ tag.name }}
  </span>
        </div>
        <span>阅读全文 →</span>
      </RouterLink>
    </div>
    <nav v-if="!error && totalPages > 0" class="article-pagination" aria-label="文章分页">
      <button type="button" :disabled="loading || page <= 1" @click="changePage(page - 1)">
        上一页
      </button>
      <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
      <button type="button" :disabled="loading || page >= totalPages" @click="changePage(page + 1)">
        下一页
      </button>
    </nav>
  </section>
</template>

<style scoped>
.article-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.article-search, .article-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
  margin: 20px 0;
}
.article-search label { flex: 1 1 220px; min-width: 0; }
.article-search input {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 8px;
}
.article-search button, .article-pagination button { padding: 8px 12px; }
.article-pagination { align-items: center; }
button:disabled { cursor: not-allowed; opacity: 0.6; }
</style>
