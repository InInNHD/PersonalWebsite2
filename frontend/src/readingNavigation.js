import { nextTick } from 'vue'

// 列表的异步滚动仍由listNavigation负责；这里只等待文章正文。
let pending = null
export function prepareReading(to, from) {
    if (to.path === from.path && from.matched?.length) return
    pending?.resolve()
    pending = null
    if (/^\/articles\/[^/]+\/?$/.test(to.path)) {
        let resolve
        const promise = new Promise(done => { resolve = done })
        pending = { path: to.path, promise, resolve }
    }
}

export async function finishReading(path) {
    await nextTick()
    if (pending?.path === path) pending.resolve()
}

export async function readingScroll(to, savedPosition, currentRoute) {
    if (pending?.path === to.path) await pending.promise
    await nextTick()
    if (currentRoute().fullPath !== to.fullPath) return false
    // 浏览器后退/前进优先恢复真实位置，不能被章节锚点覆盖。
    if (savedPosition) return savedPosition
    try {
        const id = decodeURIComponent(to.hash.slice(1))
        // 用getElementById查找，避免把任意URL内容当CSS选择器。
        const heading = id.startsWith('read-') ? document.getElementById(id) : null
        // 字号/专注工具条会吸顶；章节定位留出实际高度，窄屏换行也不遮住标题。
        const toolsHeight = document.querySelector?.('.reading-tools')?.getBoundingClientRect().height ?? 0
        if (heading) return { el: heading, top: Math.max(28, toolsHeight + 24) }
    } catch { /* 无效的百分号编码按普通文章入口处理。 */ }
    return { top: 0 }
}
