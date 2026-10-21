<script setup>
import { computed, ref, watch } from 'vue'
import noteCover from '../assets/note.svg'
import thoughtCover from '../assets/thought.svg'
import diaryCover from '../assets/diary.svg'

const props = defineProps({
  article: { type: Object, required: true },
  // 仅用于首篇版式强调；是否置顶仍以article.pinned为准。
  featured: { type: Boolean, default: false },
  // 正文链接携带当前列表网址，刷新详情后仍可返回相同条件。
  returnTo: { type: String, default: '/' },
})
const names = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
const covers = { NOTE: noteCover, THOUGHT: thoughtCover, DIARY: diaryCover }
// 自定义封面加载失败时回退到栏目图；切换地址后允许重新尝试。
const coverFailed = ref(false)
watch(() => props.article.coverUrl, () => { coverFailed.value = false })
const cover = computed(() =>
    !coverFailed.value && props.article.coverUrl
        ? props.article.coverUrl
        : (covers[props.article.type] ?? noteCover)
)
const path = computed(() => ({ path: `/articles/${props.article.id}`, query: { from: props.returnTo } }))
</script>

<template>
  <article class="v2-post" :class="{ featured }">
    <RouterLink class="v2-cover" :to="path" :aria-label="`阅读：${article.title}`">
      <!-- 固定栏目图是装饰；文章标题已由链接的无障碍名称提供。 -->
      <img :src="cover" alt="" width="480" height="300" :loading="featured ? 'eager' : 'lazy'"
           referrerpolicy="no-referrer" @error="coverFailed = true">
    </RouterLink>
    <div class="v2-post-body">
      <div class="v2-meta"><span v-if="article.pinned" class="v2-pin">置顶</span><span class="v2-badge">{{ names[article.type] ?? '文章' }}</span><RouterLink v-if="article.categoryId != null" :to="{ path: '/topics', query: { kind: 'category', id: String(article.categoryId) }, hash: '#entries' }">{{ article.categoryName }}</RouterLink><span v-else>未分类</span></div>
      <h3><RouterLink :to="path">{{ article.title }}</RouterLink></h3>
      <p class="v2-summary">{{ article.summary || '打开文章，继续阅读。' }}</p>
      <time class="v2-meta" :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) ?? '发布时间未记录' }}</time>
      <time v-if="article.updatedAt" class="v2-meta" :datetime="article.updatedAt">
        更新于 {{ article.updatedAt.slice(0, 10) }}
      </time>
      <div class="v2-post-bottom">
        <div class="v2-tags" aria-label="文章标签">
          <!-- 分类/标签各自是链接，不嵌套在文章链接中。 -->
          <RouterLink v-for="tag in article.tags ?? []" :key="tag.id"
                      :to="{ path: '/topics', query: { kind: 'tag', id: String(tag.id) }, hash: '#entries' }">#{{ tag.name }}</RouterLink>
        </div>
        <RouterLink :to="path" class="v2-read-more" :aria-label="`阅读全文：${article.title}`">→</RouterLink>
      </div>
    </div>
  </article>
</template>