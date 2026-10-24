import { ref, onBeforeUnmount } from 'vue'

// 首页与主题目录共用相同的公开编排接口。失败可单独重试，不阻塞文章库。
export function usePresentation() {
  const presentation = ref(null), presentationLoading = ref(false), presentationError = ref('')
  let request = 0
  onBeforeUnmount(() => { request += 1 })
  async function loadPresentation() {
    const id = ++request
    presentationLoading.value = true
    presentationError.value = ''
    try {
      const response = await fetch('/api/presentation', { signal: AbortSignal.timeout(15000) })
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const data = await response.json()
      if (id === request) presentation.value = data
    } catch {
      if (id === request) { presentation.value = null; presentationError.value = '精选与专题暂时无法加载。' }
    } finally { if (id === request) presentationLoading.value = false }
  }
  return { presentation, presentationLoading, presentationError, loadPresentation }
}
