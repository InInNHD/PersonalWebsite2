<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'

const route = useRoute()
const article = ref(null)
const loading = ref(false)
const error = ref('')

// 每个组件实例维护自己的请求编号。
let latestRequestId = 0

onBeforeUnmount(() => {
  latestRequestId += 1
})

// 不允许 Markdown 中的原始 HTML 直接进入页面。
const markdown = new MarkdownIt({ html: false, linkify: true })
const contentHtml = computed(() =>
    markdown.render(article.value?.contentMarkdown ?? '')
)

async function loadArticle(id) {
  const requestId = ++latestRequestId

  loading.value = true
  error.value = ''
  article.value = null

  try {
    const response = await fetch(
        `/api/articles/${encodeURIComponent(id)}`
    )

    if (response.status === 404) {
      throw new Error('文章不存在或尚未发布')
    }

    if (!response.ok) {
      throw new Error(`请求失败：HTTP ${response.status}`)
    }

    const data = await response.json()

    // 只接受当前最后一次请求的文章内容。
    if (requestId !== latestRequestId) return

    article.value = data
  } catch (cause) {
    // 防止上一篇文章的错误覆盖当前文章。
    if (requestId !== latestRequestId) return

    error.value = cause instanceof Error
        ? cause.message
        : '加载文章失败'
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
    }
  }
}

// 当网址中的文章 id 改变时，重新读取对应文章。
watch(() => route.params.id, loadArticle, { immediate: true })
</script>

<template>
  <article>
    <RouterLink class="back-link" to="/">← 返回文章列表</RouterLink>

    <p v-if="loading">正在加载文章…</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <template v-else-if="article">
      <header class="article-header">
        <small>{{ article.type }} · {{ article.publishedAt?.slice(0, 10) }}</small>
        <h1>{{ article.title }}</h1>
        <p>分类：{{ article.categoryName ?? '未分类' }}</p>
        <p>{{ article.summary }}</p>
        <div v-if="article.tags?.length" class="article-tags" aria-label="文章标签">
  <span v-for="tag in article.tags" :key="tag.id" class="tag-badge">
    #{{ tag.name }}
  </span>
        </div>
      </header>

      <!-- Markdown 转换后的内容；解析器已关闭原始 HTML。 -->
      <div class="markdown-body" v-html="contentHtml"></div>
    </template>
  </article>
</template>