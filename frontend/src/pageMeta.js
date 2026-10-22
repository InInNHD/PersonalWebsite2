import { ref, toValue, watchEffect, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { siteProfile } from './siteProfile.js'

// 哈希只控制定位，不改变内容身份；旧请求的标题不能覆盖其他文章或筛选。
export const contentMeta = ref(null)
export function pageMetaKey(route) {
  return route.path + JSON.stringify(Object.entries(route.query ?? {}).sort(([a], [b]) => a.localeCompare(b)))
}
export function usePageMeta(title, description, noIndex = false) {
  const route = useRoute(), owner = Symbol('page')
  watchEffect(() => { contentMeta.value = { owner, key: pageMetaKey(route), title: toValue(title), description: toValue(description), noIndex: toValue(noIndex) } })
  onBeforeUnmount(() => { if (contentMeta.value?.owner === owner) contentMeta.value = null })
}
export function resolvePageMeta(route, custom = null) {
  const names = { '/': '首页', '/topics': '内容地图', '/archive': '文章归档', '/diary': '公开日记', '/resources': '资源收藏', '/about': `关于 ${siteProfile.nickname}` }
  const descriptions = {
    '/': '记录学习中的笔记、思考与愿意公开的生活片段。',
    '/topics': '沿着分类与标签探索公开笔记、心得和日记。',
    '/archive': '按年月回看已发布的学习与生活记录。',
    '/diary': '精选公开的生活片段，收藏日常里的微光。',
    '/resources': '按用途整理工具、网站与学习资料，分享使用体会。',
    '/about': `${siteProfile.nickname}的个人介绍、公开项目与项目更新记录。`,
  }
  const admin = route.path === '/admin' || route.path.startsWith('/admin/')
  const article = /^\/articles\/[^/]+\/?$/.test(route.path)
  const known = names[route.path] || article || admin
  const entry = custom?.key === pageMetaKey(route) ? custom : null
  const title = entry?.title || (admin ? route.meta?.adminTitle || '内容管理' : names[route.path] || (article ? '文章阅读' : '页面不存在'))
  const description = entry?.description || (admin ? '个人网站的内容写作与管理工作台。' : descriptions[route.path] || (article ? '阅读公开笔记、心得与生活记录。' : '这个页面不存在，可以返回首页继续浏览。'))
  return { title: `${String(title).replace(/\s+/g, ' ').trim().slice(0, 100)} · ${siteProfile.siteName}`, description: String(description).replace(/\s+/g, ' ').trim().slice(0, 180), robots: admin || !known || entry?.noIndex ? 'noindex, nofollow' : 'index, follow' }
}
export function writePageMeta(route, custom, document) {
  const result = resolvePageMeta(route, custom)
  document.title = result.title
  for (const [name, content] of [['description', result.description], ['robots', result.robots]]) {
    let tag = document.querySelector(`meta[name="${name}"]`)
    if (!tag) { tag = document.createElement('meta'); tag.setAttribute('name', name); document.head.appendChild(tag) }
    tag.setAttribute('content', content)
  }
}
