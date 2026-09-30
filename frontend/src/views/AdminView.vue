<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import MarkdownIt from 'markdown-it'

const password = ref('')
const connected = ref(false)
const busy = ref(false)
const error = ref('')
const message = ref('')
const articles = ref([])

// 认证信息保存在当前页面内存，刷新后重新输入。
let authorization = ''

function emptyArticle() {
  return {
    id: null,
    title: '',
    summary: '',
    contentMarkdown: '',
    type: 'NOTE',
    status: 'DRAFT',
  }
}

const form = reactive(emptyArticle())
const savedSnapshot = ref(JSON.stringify(form))
const dirty = computed(() => JSON.stringify(form) !== savedSnapshot.value)

const markdown = new MarkdownIt({ html: false, linkify: true })
const preview = computed(() => markdown.render(form.contentMarkdown))

function fillForm(article) {
  Object.assign(form, article)
  savedSnapshot.value = JSON.stringify(form)
}

function canDiscard() {
  return !dirty.value || window.confirm('有未保存的修改，是否放弃？')
}

// 请求统一携带认证信息；写入之前获取当前会话的 CSRF token。
async function request(path, options = {}) {
  const method = options.method ?? 'GET'
  const headers = new Headers(options.headers)

  headers.set('Authorization',authorization)

  // 认证失败时由页面显示错误，避免浏览器反复弹出登录窗口。
  headers.set('X-Requested-With', 'XMLHttpRequest')

  if (options.body) {
    headers.set('Content-Type', 'application/json')
  }

  if (method !== 'GET') {
    const csrf = await request('/csrf')
    headers.set(csrf.headerName, csrf.token)
  }

  const response = await fetch(`/api/admin${path}`,{
    ...options,
    headers,
    credentials: 'same-origin',
  })

if(!response.ok) {
  const descriptions = {
    400: '内容校验失败，请检查标题、正文、摘要长度和栏目。',
    403: '安全校验失败，请重试；若仍失败，请检查后端日志。',
    404: '文章不存在。',
  }
  throw new Error(descriptions[response.status] ?? `请求失败: HTTP ${response.status}`)
}
return response.json()
}

// 操作期间禁用按钮，并将失败原因展示到页面。
async function run(action) {
  if (busy.value) return

  busy.value = true
  error.value = ''
  message.value = ''

  try {
    await action()
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}

async function loadList() {
  articles.value = await request('/articles')
}

async function connect() {
  await run(async () => {
    // UTF-8 编码后再转换为 Base64，也能处理包含中文的密码。
    const bytes = new TextEncoder().encode(`admin:${password.value}`)
    authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`

    await request('/check')
    await loadList()
    connected.value = true
    password.value = ''
  })
}

function newArticle() {
  if (busy.value || !canDiscard()) return

  fillForm(emptyArticle())
  error.value = ''
  message.value = ''
}

async function openArticle(id) {
  if (!canDiscard()) return

  await run(async () => {
    fillForm(await request(`/articles/${id}`))
  })
}

// 首次保存使用 POST；取得 id 后，后续保存使用 PUT。
async function persist() {
  const isNew = form.id === null

  const result = await request(
      isNew ? '/articles' : `/articles/${form.id}`,
      {
        method: isNew ? 'POST' : 'PUT',
        body: JSON.stringify({
          title: form.title,
          summary: form.summary,
          contentMarkdown: form.contentMarkdown,
          type: form.type,
        }),
      }
  )

  // 先记录已保存的 id；即使随后刷新列表失败，也不会重复创建。
  form.id = result.id
  form.status = result.status
  savedSnapshot.value = JSON.stringify(form)
}

async function save() {
  await run(async () => {
    await persist()
    message.value = '文章已保存。'
    await loadList()
  })
}

async function publish() {
  await run(async () => {
    // 先保存当前输入，确保发布的内容包含最新修改。
    await persist()
    const result = await request(`/articles/${form.id}/publish`, {
      method: 'POST',
    })

    form.status = result.status
    savedSnapshot.value = JSON.stringify(form)
    message.value = '文章已发布，前台现在可以阅读。'
    await loadList()
  })
}

// 切换路由和关闭页面时，提醒尚未保存的修改。
onBeforeRouteLeave(() => !busy.value && canDiscard())

function beforeUnload(event) {
  if (dirty.value || busy.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section class="admin">
    <h1>文章管理</h1>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" aria-live="polite">{{ message }}</p>

    <form v-if="!connected" class="login" @submit.prevent="connect">
      <p>管理员账号：admin</p>
      <label>
        管理员密码
        <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            :disabled="busy"
        />
      </label>
      <button :disabled="busy">
        {{ busy ? '连接中…' : '进入管理' }}
      </button>
    </form>

    <fieldset v-else :disabled="busy">
      <div class="toolbar">
        <button type="button" @click="newArticle">新建文章</button>
        <span v-if="busy">正在处理…</span>
        <span v-else-if="dirty">有未保存的修改</span>
      </div>

      <div class="admin-layout">
        <aside>
          <h2>全部文章</h2>
          <p v-if="articles.length === 0">暂时没有文章。</p>
          <ul>
            <li v-for="article in articles" :key="article.id">
              <button
                  type="button"
                  :class="{ selected: article.id === form.id }"
                  @click="openArticle(article.id)"
              >
                {{ article.title }}
                <small>
                  {{ article.status === 'DRAFT' ? '草稿' : '已发布' }}
                </small>
              </button>
            </li>
          </ul>
        </aside>

        <form @submit.prevent="save">
          <p>
            {{ form.id === null ? '新文章' : `文章编号：${form.id}` }}
            · {{ form.status === 'DRAFT' ? '草稿' : '已发布' }}
          </p>
          <p v-if="form.status === 'PUBLISHED'">
            保存后，修改会立即展示到前台。
          </p>

          <label>
            标题
            <input v-model="form.title" maxlength="200" required />
          </label>

          <label>
            栏目
            <select v-model="form.type">
              <option value="NOTE">笔记</option>
              <option value="THOUGHT">心得</option>
              <option value="DIARY">日记</option>
            </select>
          </label>

          <label>
            摘要
            <textarea v-model="form.summary" maxlength="500" rows="3"></textarea>
          </label>

          <div class="writing">
            <label>
              Markdown 正文
              <textarea v-model="form.contentMarkdown" rows="18" required></textarea>
            </label>

            <div class="preview">
              <strong>正文预览</strong>
              <div class="markdown-body" v-html="preview"></div>
            </div>
          </div>

          <div class="toolbar">
            <button type="submit">
              {{ form.status === 'PUBLISHED' ? '保存公开修改' : '保存草稿' }}
            </button>
            <button
                type="button"
                :disabled="form.status === 'PUBLISHED'"
                @click="publish"
            >
              保存并发布
            </button>
          </div>
        </form>
      </div>
    </fieldset>
  </section>
</template>

<style scoped>
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
label { display: block; margin-bottom: 16px; }
input, textarea, select { width: 100%; padding: 10px; margin-top: 6px; }
textarea { resize: vertical; font: inherit; }
button { padding: 10px 14px; cursor: pointer; }
button:disabled { cursor: default; opacity: 0.6; }
.login { max-width: 360px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin: 20px 0; }
.admin-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 24px; }
ul { list-style: none; padding: 0; }
li { margin-bottom: 8px; }
li button { width: 100%; text-align: left; overflow-wrap: anywhere; }
small { display: block; margin-top: 6px; color: #607078; }
.selected { border: 2px solid #216187; }
.writing { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.writing > * { min-width: 0; }
.preview { padding: 16px; background: white; border: 1px solid #dce2e6; }
@media (max-width: 760px) {
  .admin-layout, .writing { grid-template-columns: 1fr; }
}
</style>