<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const resources = ref([])
const loading = ref(false)
const error = ref('')

// 延续文章页面的处理方式，只允许最新请求更新页面。
let latestRequestId = 0

onBeforeUnmount(() => {
  latestRequestId += 1
})

// 外部链接只允许完整的 HTTP 或 HTTPS 地址。
// 无法解析或使用其他协议时，返回 null，不生成可点击的链接。
function toHttpUrl(value) {
  try {
    const url = new URL(value)

    if (url.protocol === 'http:' || url.protocol === 'https:') {
      return url.href
    }
  } catch {
    // 地址格式错误时，交给页面显示“链接地址无效”。
  }

  return null
}

async function loadResources() {
  const requestId = ++latestRequestId

  loading.value = true
  error.value = ''

  try {
    const response = await fetch('/api/resources')

    if (!response.ok) {
      throw new Error(`请求失败：HTTP ${response.status}`)
    }

    const data = await response.json()

    if (requestId !== latestRequestId) return

    // 保留接口返回的数据，并为每条资源计算可用于跳转的地址。
    resources.value = data.map(resource => ({
      ...resource,
      href: toHttpUrl(resource.url),
    }))
  } catch (cause) {
    if (requestId !== latestRequestId) return

    resources.value = []
    error.value = cause instanceof Error
        ? cause.message
        : '加载资源失败'
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
    }
  }
}

onMounted(loadResources)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>资源</h1>
        <p>收藏的网站、工具与学习资料。</p>
      </div>
    </div>

    <p v-if="loading">正在加载资源…</p>

    <div v-else-if="error">
      <p class="error">{{ error }}</p>
      <button type="button" @click="loadResources">
        重新加载
      </button>
    </div>

    <p v-else-if="resources.length === 0">
      暂时没有公开的资源。
    </p>

    <!-- 复用现有卡片布局，让资源页与文章页保持一致。 -->
    <div v-else class="article-grid">
      <article
          v-for="resource in resources"
          :key="resource.id"
          class="article-card resource-card"
      >
        <h2>{{ resource.title }}</h2>
        <p>{{ resource.description }}</p>

        <!-- 外部网址使用 a 标签，在新标签页中打开。 -->
        <a
            v-if="resource.href"
            :href="resource.href"
            target="_blank"
            rel="noopener noreferrer"
        >
          访问资源 ↗
        </a>

        <span v-else class="invalid-link">
          链接地址无效
        </span>
      </article>
    </div>
  </section>
</template>

<style scoped>
.resource-card {
  /* 长名称或连续字符可以换行，避免撑破手机页面。 */
  overflow-wrap: anywhere;
}

.resource-card a {
  color: #216187;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.resource-card .invalid-link {
  color: #b42318;
}
</style>