<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const articles = ref([])
const type = ref('')
const loading = ref(false)
const error = ref('')

// 只用于判断请求先后，不参与页面渲染，因此不需要 ref。
let latestRequestId = 0

// 离开页面时，让尚未结束的请求失效。
onBeforeUnmount(() => {
  latestRequestId += 1
})

// 栏目值必须与后端 ArticleController 接受的值相同。
const categories = [
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
    const query = type.value
        ? `?type=${encodeURIComponent(type.value)}`
        : ''

    const response = await fetch(`/api/articles${query}`)

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

    <label>
      栏目
      <select v-model="type" @change="loadArticles">
        <option
            v-for="category in categories"
            :key="category.value"
            :value="category.value"
        >
          {{ category.label }}
        </option>
      </select>
    </label>
    </div>

    <p v-if="loading">正在加载文章...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="articles.length === 0">这个栏目暂时没有已发布的文章。</p>

    <div v-else class="article-grid">
      <RouterLink
        v-for="article in articles"
        :key="article.id"
        class="article-card"
        :to="`/articles/${article.id}`"
      >
        <small>{{ article.type }}  · {{ article.publishedAt?.slice(0, 10) }}</small>
        <h2>{{ article.title }}</h2>
        <p>{{ article.summary }}</p>
        <span>阅读全文 →</span>
      </RouterLink>
    </div>
  </section>
</template>
