<script setup>
import { onMounted, ref } from 'vue'

const articles = ref([])
const type = ref('')
const loading = ref(false)
const error = ref('')

// 栏目值必须与后端 ArticleController 接受的值相同。
const categories = [
  { value: '', label: '全部' },
  { value: 'NOTE', label: '笔记' },
  { value: 'THOUGHT', label: '心得' },
  { value: 'DIARY', label: '日记' },
]

async function loadArticles() {
  loading.value = true
  error.value = ''

  try {
    const query = type.value ? `?type=${encodeURIComponent(type.value)}` : ''
    const response = await fetch(`/api/articles${query}`)

    if (!response.ok) {
      throw new Error(`请求失败：HTTP ${response.status}`)
    }

    articles.value = await response.json()
  } catch (cause) {
    articles.value = []
    error.value = cause.message
  } finally {
    loading.value = false
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
