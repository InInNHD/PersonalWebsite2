import { ref } from 'vue'

// 只在当前标签页内存中保存认证头，刷新后重新登录；不写入本地存储或网址。
export const adminConnected = ref(false)
export const adminLeaving = ref(false)
export const adminSessionError = ref('')
export const adminNotice = ref('')
let authorization = ''
let generation = 0
let noticeTimer
export function notifyAdmin(message) {
  clearTimeout(noticeTimer)
  adminNotice.value = message
  if (message) noticeTimer = setTimeout(() => { adminNotice.value = '' }, 6000)
}

export function clearAdminSession() {
  generation += 1
  authorization = ''
  adminConnected.value = false
}

export async function adminRequest(path, options = {}) {
  if (!authorization) throw new Error('请先重新验证管理员身份。')
  const version = generation
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  headers.set('Authorization', authorization)
  headers.set('X-Requested-With', 'XMLHttpRequest') // 失败由页面提示，避免原生Basic弹窗。
  // FormData的boundary由浏览器生成，不能手动覆盖Content-Type。
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await adminRequest('/csrf') // 每次写入重新取当前会话token。
    headers.set(csrf.headerName, csrf.token)
  }
  if (version !== generation) throw new Error('登录状态已变化，请重新操作。')
  const response = await fetch(`/api/admin${path}`, { ...options, method, headers, credentials: 'same-origin', signal: options.signal ?? AbortSignal.timeout(20000) })
  if (version !== generation) throw new Error('登录状态已变化，请重新操作。')
  if (!response.ok) {
    if (response.status === 401) clearAdminSession()
    const descriptions = {
      400: path.startsWith('/media') ? '图片校验失败：请使用有效PNG/JPEG，大小不超过5MiB，边长不超过6000px且总像素不超过2000万。' : '输入校验失败，请检查必填项、长度、链接格式和排序范围。',
      401: '管理员认证失败，当前输入仍保留；请核对密码后重新登录。',
      403: '安全校验失败，请重试；若仍失败，请检查后端日志。',
      404: '记录不存在，请刷新列表。',
      413: '图片过大，请选择不超过5MiB的PNG或JPEG图片。',
      409: path.startsWith('/resource-groups') ? '资源分组名称已存在，请换一个名称。' : path.startsWith('/tags') ? '标签名称已存在，请换一个名称。' : path.startsWith('/categories') ? '分类名称已存在，请换一个名称。' : '文章已被回收，请刷新列表并先恢复为草稿。',
    }
    throw new Error(descriptions[response.status] ?? `请求失败：HTTP ${response.status}`)
  }
  const data = response.status === 204 ? null : options.responseType === 'blob' ? await response.blob() : await response.json()
  if (version !== generation) throw new Error('登录状态已变化，请重新操作。')
  return data
}

export async function loginAdmin(password) {
  clearAdminSession()
  adminSessionError.value = ''
  const version = generation
  const bytes = new TextEncoder().encode(`admin:${password}`)
  authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`
  try {
    await adminRequest('/check')
    if (version === generation) adminConnected.value = true
  } catch (error) {
    if (version === generation) clearAdminSession()
    throw error
  }
}

// 登录后仅允许回到已实现的后台页面，拒绝外部地址和login自身循环。
export function adminListQuery(input = {}) {
  const one = value => Array.isArray(value) ? value[0] : value
  const result = {}
  const keyword = String(one(input.keyword) ?? '').trim().slice(0, 100)
  if (keyword) result.keyword = keyword
  for (const [name, allowed] of [['status', ['DRAFT', 'PUBLISHED']], ['type', ['NOTE', 'THOUGHT', 'DIARY']]]) {
    if (allowed.includes(one(input[name]))) result[name] = one(input[name])
  }
  const category = String(one(input.categoryId) ?? '')
  if (/^[1-9]\d{0,18}$/.test(category) && BigInt(category) <= 9223372036854775807n) result.categoryId = category
  if (one(input.recycled) === 'true') result.recycled = 'true'
  const page = String(one(input.page) ?? '')
  if (/^[1-9]\d{0,6}$/.test(page) && +page > 1 && +page <= 1000000) result.page = page
  return result
}
export function adminTarget(raw) {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return '/admin/articles'
  const url = new URL(raw, 'https://admin.invalid')
  if (url.origin !== 'https://admin.invalid') return '/admin/articles'
  if (url.pathname === '/admin/articles') {
    const query = new URLSearchParams(adminListQuery(Object.fromEntries(url.searchParams))).toString()
    return url.pathname + (query ? '?' + query : '')
  }
  if (['/admin/taxonomy', '/admin/resources'].includes(url.pathname)) return url.pathname
  const match = url.pathname.match(/^\/admin\/articles\/([1-9]\d{0,18})\/edit$/)
  if (url.pathname === '/admin/articles/new' || (match && BigInt(match[1]) <= 9223372036854775807n)) {
    const from = url.searchParams.get('from')
    const query = from && from.startsWith('/admin/articles?') ? new URLSearchParams({ from: adminTarget(from) }).toString() : ''
    return url.pathname + (query ? '?' + query : '')
  }
  return '/admin/articles'
}

export async function guardAdmin(to) {
  if (to.meta.requiresAdmin && !adminConnected.value) {
    return { path: '/admin/login', query: { next: adminTarget(to.fullPath) }, replace: true }
  }
  if (to.path === '/admin/articles') {
    const query = adminListQuery(to.query)
    if (JSON.stringify(query) !== JSON.stringify(to.query) || to.hash) return { path: to.path, query, replace: true }
  }
  if (to.meta.adminSection === 'article' && to.params.id && adminTarget(to.path) !== to.path) {
    return { path: '/admin/articles', replace: true }
  }
  // 组件离开守卫先检查未保存内容，确认允许离开后才执行退出。
  if (to.path === '/admin/login' && adminConnected.value) {
    if (adminLeaving.value) return false
    adminLeaving.value = true
    adminSessionError.value = ''
    try {
      await adminRequest('/logout', { method: 'POST' })
      clearAdminSession()
    } catch (error) {
      adminSessionError.value = `退出未完成：${error.message}`
      return false // 后端失败时留在原页，不悄悄丢弃表单。
    } finally { adminLeaving.value = false }
  }
}
