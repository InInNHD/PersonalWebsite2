import { nextTick } from 'vue'

// URL输入不可信：只接受本页支持的参数，避免非法值直接交给接口。
// 编号保留字符串，避免Java Long转成JavaScript Number时丢失精度。
const first = value => Array.isArray(value) ? value[0] : value
function positiveId(value) {
    const text = String(first(value) ?? '')
    if (!/^[1-9]\d{0,18}$/.test(text)) return ''
    return BigInt(text) <= 9223372036854775807n ? text : ''
}

export function listQuery(query = {}) {
    const result = {}
    const type = first(query.type)
    if (['NOTE', 'THOUGHT', 'DIARY'].includes(type)) result.type = type
    for (const name of ['categoryId', 'tagId']) {
        const id = positiveId(query[name])
        if (id) result[name] = id
    }
    const keyword = String(first(query.keyword) ?? '').trim().slice(0, 100)
    if (keyword) result.keyword = keyword
    const page = String(first(query.page) ?? '')
    if (/^[1-9]\d{0,6}$/.test(page) && Number(page) > 1 && Number(page) <= 1000000) result.page = page
    return result // 默认第一页和空条件省略，网址更短。
}

export const listKey = route => new URLSearchParams(listQuery(route.query)).toString()
export const listHash = hash => ['#entries', '#article-search'].includes(hash) ? hash : ''

// 正文的返回地址只允许本网站首页。外部地址、其他页面和异常参数回退。
export function listReturn(raw) {
    if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return { path: '/' }
    try {
        const url = new URL(raw, 'https://list.invalid')
        if (url.origin !== 'https://list.invalid' || url.pathname !== '/') return { path: '/' }
        return { path: '/', query: listQuery(Object.fromEntries(url.searchParams)), hash: listHash(url.hash) }
    } catch {
        return { path: '/' }
    }
}

// 只保存当前页面会话中最近20个列表的位置，不保存正文或管理员信息。
const positions = new Map()
export function rememberList(route, position) {
    const key = listKey(route)
    positions.delete(key)
    positions.set(key, position)
    if (positions.size > 20) positions.delete(positions.keys().next().value)
}

// 路由先登记“列表尚未完成”，组件加载并渲染后通知它。
// 这样浏览器后退时，savedPosition不会在加载提示阶段被截断。
let pending = null
export function prepareList(to, from) {
    if (to.path === '/' && from.path === '/' && from.matched?.length && listKey(to) === listKey(from)) return
    pending?.resolve() // 离开或更换目标时释放旧等待者。
    pending = null
    if (to.path === '/') {
        let resolve
        const promise = new Promise(done => { resolve = done })
        pending = { key: listKey(to), promise, resolve }
    }
}

export async function finishList(key) {
    await nextTick() // 等待v-if把加载提示替换成卡片或错误信息。
    if (pending?.key === key) pending.resolve()
}

export async function listScroll(to, from, savedPosition, currentRoute) {
    if (to.path === '/') {
        const task = pending
        if (task?.key === listKey(to)) await task.promise
        await nextTick()
        // 用户可能已经去了别处，旧滚动任务不能移动新页面。
        if (currentRoute().fullPath !== to.fullPath) return false
        if (savedPosition) return savedPosition
        // 点击正文中的返回链接也恢复位置；直接访问详情没有历史位置则定位列表。
        if (from.path.startsWith('/articles/') && from.query.from) {
            const origin = listReturn(from.query.from)
            if (listKey(origin) === listKey(to)) return positions.get(listKey(to)) ?? { el: '#entries', top: 24 }
        }
        if (to.hash) return { el: to.hash, top: 24 }
        if (from.path === '/' && listKey(to) !== listKey(from)) return { el: '#entries', top: 24 }
    }
    return savedPosition ?? { top: 0 }
}