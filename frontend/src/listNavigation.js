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

// 主题页仅支持kind/id/page。成对合法才选中主题；非法值回到目录。
export function topicQuery(query = {}) {
    const kind = first(query.kind)
    const id = positiveId(query.id)
    if (!['category', 'tag'].includes(kind) || !id) return {}
    const result = { kind, id }
    const page = listQuery({ page: query.page }).page
    if (page) result.page = page
    return result
}

// 归档和日记只接受月份和页码；不带月份时使用最新公开月份。
export function archiveQuery(query = {}) {
    const result = {}
    const month = String(first(query.month) ?? '')
    if (/^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(month)) result.month = month
    const page = listQuery({ page: query.page }).page
    if (result.month && page) result.page = page
    return result
}

const isList = path => ['/', '/topics', '/archive', '/diary'].includes(path)
export function pageQuery(path, query) {
    if (path === '/topics') return topicQuery(query)
    if (path === '/archive' || path === '/diary') return archiveQuery(query)
    return listQuery(query)
}
// 首页已有调用只传query；缺少path时继续按首页处理。
export const listKey = route => (['/topics', '/archive', '/diary'].includes(route.path) ? route.path.slice(1) + '?' : '')
    + new URLSearchParams(pageQuery(route.path, route.query)).toString()
export const listHash = hash => ['#entries', '#article-search'].includes(hash) ? hash : ''
export const pageHash = (path, hash) => path === '/' ? listHash(hash) : (hash === '#entries' ? hash : '')

// 正文的返回地址只允许本站首页、主题、归档和日记页。外部地址、其他页面和异常参数回退。
export function listReturn(raw) {
    if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return { path: '/' }
    try {
        const url = new URL(raw, 'https://list.invalid')
        if (url.origin !== 'https://list.invalid' || !isList(url.pathname)) return { path: '/' }
        return { path: url.pathname, query: pageQuery(url.pathname, Object.fromEntries(url.searchParams)),
            hash: pageHash(url.pathname, url.hash) }
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
    if (isList(to.path) && from.path === to.path && from.matched?.length && listKey(to) === listKey(from)) return
    pending?.resolve() // 离开或更换目标时释放旧等待者。
    pending = null
    if (isList(to.path)) {
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
    if (isList(to.path)) {
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
        // 回到主题总目录时显示目录顶部，不停在下方的选择提示处。
        if (to.path === '/topics' && !topicQuery(to.query).id && !to.hash) return { top: 0 }
        // 初始化补上最新月份时留在页头；主动选择月份才定位时间线。
        if (['/archive', '/diary'].includes(to.path) && from.path === to.path
            && !archiveQuery(from.query).month && !to.hash) return { top: 0 }
        if (to.hash) return { el: to.hash, top: 24 }
        if (from.path === to.path && isList(from.path) && listKey(to) !== listKey(from)) return { el: '#entries', top: 24 }
    }
    return savedPosition ?? { top: 0 }
}