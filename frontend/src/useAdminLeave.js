import { onMounted, onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'

// 同一处管理路由离开、文章换号、资源切换及浏览器关闭的未保存保护。
export function useAdminLeave(dirty, busy) {
  const discardOpen = ref(false)
  let answer = null
  let internalNavigation = false
  function resolveDiscard(accepted) {
    discardOpen.value = false
    answer?.(accepted)
    answer = null
  }
  function askDiscard() {
    if (busy.value) return false
    if (!dirty.value) return true
    if (answer) return false
    discardOpen.value = true
    return new Promise(resolve => { answer = resolve })
  }
  const guard = () => internalNavigation || askDiscard()
  onBeforeRouteLeave(guard)
  onBeforeRouteUpdate(guard)
  // 首次保存取得编号后只同步网址，不应被“正在保存”守卫误拦截。
  async function navigateSaved(action) {
    internalNavigation = true
    try { return await action() } finally { internalNavigation = false }
  }
  function beforeUnload(event) {
    if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' }
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); resolveDiscard(false) })
  return { discardOpen, askDiscard, resolveDiscard, navigateSaved }
}
