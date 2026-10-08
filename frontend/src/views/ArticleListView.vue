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
  // 每次调用都会得到新编号，之前的请求自动成为过期请求。
  const requestId = ++latestRequestId

  loading.value = true
  error.value = ''

  try {
    // URLSearchParams 会处理多个参数之间的 & 和特殊字符编码。
    const params = new URLSearchParams()

    if (type.value) {
      params.set('type', type.value)
    }

    if (categoryId.value !== '') {
      params.set('categoryId', String(categoryId.value))
    }

    if (tagId.value !== '') {
      params.set('tagId', String(tagId.value))
    }
    const query = params.toString()

    const response = await fetch(
        `/api/articles${query ? `?${query}` : ''}`
    )

    if (!response.ok) {
      throw new Error(`请求失败：HTTP ${response.status}`)
    }

    // 解析 JSON 也是异步操作，所以必须等解析完成后再检查编号。
    const data = await response.json()

    // 如果期间又发起了请求，就丢弃这次结果。
    if (requestId !== latestRequestId) return

    articles.value = data
  } catch (cause) {
    // 旧请求失败时，也不能清空新列表或覆盖新请求的错误提示。
    if (requestId !== latestRequestId) return

    articles.value = []
    error.value = cause instanceof Error
        ? cause.message
        : '加载文章列表失败'
  } finally {
    // 旧请求结束时，不能关闭新请求的“正在加载”提示。
    if (requestId === latestRequestId) {
      loading.value = false
    }
  }
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
          <select v-model="type" @change="loadArticles">
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
          <select v-model="categoryId" @change="loadArticles">
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
          <select v-model="tagId" @change="loadArticles">
            <option value="">全部标签</option>

            <option v-for="tag in tags" :key="tag.id" :value="tag.id">
              {{ tag.name }}
            </option>
          </select>
        </label>
      </div>

    </div>

      <p v-if="categoryError" class="error" role="alert">
        {{ categoryError }}
      </p>
    <p v-if="tagError" class="error" role="alert">
      {{ tagError }}
    </p>
      <p v-if="loading">正在加载文章...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
      <p v-else-if="articles.length === 0">
        当前筛选条件下没有已发布的文章。
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
  </section>
</template>

<style scoped>
.article-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
</style>
