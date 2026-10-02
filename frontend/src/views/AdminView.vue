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

// 文章和资源分别记录是否修改，避免互相干扰。
const articleDirty = computed(
    () => JSON.stringify(form) !== savedSnapshot.value
)

const resources = ref([])

function emptyResource() {
  return {
    id: null,
    title: '',
    url: '',
    description: '',
    published: false,
    sortOrder: 0,
  }
}

const resourceForm = reactive(emptyResource())
const resourceSnapshot = ref(JSON.stringify(resourceForm))

const resourceDirty = computed(
    () => JSON.stringify(resourceForm) !== resourceSnapshot.value
)

// 离开整个管理页面时，两种内容都要检查。
const dirty = computed(
    () => articleDirty.value || resourceDirty.value
)

const markdown = new MarkdownIt({ html: false, linkify: true })
const preview = computed(() => markdown.render(form.contentMarkdown))

function fillForm(article) {
  Object.assign(form, article)
  savedSnapshot.value = JSON.stringify(form)
}

function canDiscard() {
  return !articleDirty.value
      || window.confirm('文章有未保存的修改，是否放弃？')
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
    400: '输入校验失败，请检查必填项、长度、链接格式和排序范围。',
    401: '管理员认证失败，请检查密码；若密码已变更，请重新登录。',
    403: '安全校验失败，请重试；若仍失败，请检查后端日志。',
    404: '记录不存在，请刷新列表。',
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
    await loadResources()
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

//// 切换路由和关闭页面时，提醒尚未保存的修改。
onBeforeRouteLeave(() => {
  // 保存期间暂不切换页面。
  if (busy.value) return false

  return !dirty.value
      || window.confirm('有未保存的文章或资源修改，是否离开？')
})

function beforeUnload(event) {
  if (dirty.value || busy.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))

async function loadResources() {
  resources.value = await request('/resources')
}

function fillResource(resource) {
  Object.assign(resourceForm, resource)

  // 记录保存后的内容，用于识别之后的修改。
  resourceSnapshot.value = JSON.stringify(resourceForm)
}

function canDiscardResource() {
  return !resourceDirty.value
      || window.confirm('资源有未保存的修改，是否放弃？')
}

function newResource() {
  if (busy.value || !canDiscardResource()) return

  fillResource(emptyResource())
  error.value = ''
  message.value = ''
}

function openResource(resource) {
  if (busy.value || !canDiscardResource()) return

  // 管理列表已包含全部编辑字段，直接填入表单。
  // Object.assign 复制字段，编辑表单不会同时改动列表对象。
  fillResource(resource)
  error.value = ''
  message.value = ''
}

async function saveResource() {
  await run(async () => {
    const isNew = resourceForm.id === null

    const result = await request(
        isNew ? '/resources' : `/resources/${resourceForm.id}`,
        {
          method: isNew ? 'POST' : 'PUT',
          body: JSON.stringify({
            title: resourceForm.title,
            url: resourceForm.url,
            description: resourceForm.description,
            published: resourceForm.published,
            sortOrder: resourceForm.sortOrder,
          }),
        }
    )

    // 先记录后端返回的编号和内容，再刷新列表。
    // 即使刷新失败，下次保存仍然是修改，不会再次新增。
    fillResource(result)

    message.value = result.published
        ? '资源已保存并公开，刷新资源页即可查看。'
        : '资源已保存为隐藏状态。'

    try {
      await loadResources()
    } catch {
      error.value = '保存已成功，但列表刷新失败，请点击“刷新资源列表”。'
    }
  })
}

async function refreshResources() {
  // 仅刷新左侧列表，保留右侧正在填写的内容。
  await run(loadResources)
}
</script>

<template>
  <section class="admin">
    <h1>内容管理</h1>
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
      <section class="resource-admin">
        <h2>资源管理</h2>

        <div class="toolbar">
          <button type="button" @click="newResource">
            新建资源
          </button>
          <button type="button" @click="refreshResources">
            刷新资源列表
          </button>
          <span v-if="resourceDirty">资源有未保存的修改</span>
        </div>

        <div class="admin-layout">
          <aside>
            <h3>全部资源</h3>
            <p v-if="resources.length === 0">暂时没有资源。</p>

            <ul>
              <li v-for="resource in resources" :key="resource.id">
                <button
                    type="button"
                    :class="{ selected: resource.id === resourceForm.id }"
                    @click="openResource(resource)"
                >
                  {{ resource.title }}
                  <small>
                    {{ resource.published ? '公开' : '隐藏' }}
                    · 排序：{{ resource.sortOrder }}
                  </small>
                </button>
              </li>
            </ul>
          </aside>

          <form @submit.prevent="saveResource">
            <p>
              {{
                resourceForm.id === null
                    ? '新资源'
                    : `资源编号：${resourceForm.id}`
              }}
            </p>

            <label>
              资源名称
              <input
                  v-model="resourceForm.title"
                  maxlength="200"
                  required
              />
            </label>

            <label>
              资源链接
              <input
                  v-model="resourceForm.url"
                  type="url"
                  maxlength="2048"
                  placeholder="https://example.com"
                  required
              />
            </label>

            <label>
              资源说明
              <textarea
                  v-model="resourceForm.description"
                  maxlength="500"
                  rows="4"
              ></textarea>
            </label>

            <label>
              排序数字（越小越靠前）
              <input
                  v-model.number="resourceForm.sortOrder"
                  type="number"
                  min="0"
                  max="999999"
                  step="1"
                  required
              />
            </label>

            <label>
              展示状态
              <select v-model="resourceForm.published">
                <option :value="false">隐藏</option>
                <option :value="true">公开</option>
              </select>
            </label>

            <p>点击保存后，内容、排序和展示状态一起生效。</p>

            <button type="submit">
              {{ busy ? '正在保存…' : '保存资源' }}
            </button>
            <!-- 在资源操作附近显示反馈，避免提示被上方的文章编辑区隔开。 -->
            <p v-if="error" class="error" role="alert">
              {{ error }}
            </p>

            <p v-if="message" role="status">
              {{ message }}
            </p>
          </form>
        </div>
      </section>
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
.resource-admin {
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid #dce2e6;
}
.toolbar {
  flex-wrap: wrap;
}
</style>