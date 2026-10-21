<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { adminRequest, adminTarget, notifyAdmin } from '../adminSession.js'
import { useAdminLeave } from '../useAdminLeave.js'
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue'
const route = useRoute(), router = useRouter()
function emptyArticle() { return { id: null, title: '', summary: '', contentMarkdown: '', type: 'NOTE', status: 'DRAFT', categoryId: null, tagIds: [], coverUrl: '', pinned: false, deletedAt: null } }
const form = reactive(emptyArticle()), categories = ref([]), tags = ref([])
const busy = ref(false), loading = ref(false), ready = ref(false), error = ref(''), savedSnapshot = ref('')
const mode = ref('split'), textarea = ref(null), action = ref('')
const payload = () => ({ title: form.title, summary: form.summary, contentMarkdown: form.contentMarkdown, type: form.type, categoryId: form.categoryId, tagIds: [...form.tagIds], coverUrl: form.coverUrl.trim() || null, pinned: form.pinned })
const dirty = computed(() => ready.value && !form.deletedAt && JSON.stringify(payload()) !== savedSnapshot.value)
const leavingBusy = computed(() => busy.value || loading.value)
const { discardOpen, askDiscard, resolveDiscard, navigateSaved } = useAdminLeave(dirty, leavingBusy)
const backTarget = computed(() => { const value = adminTarget(route.query.from); return value.startsWith('/admin/articles?') || value === '/admin/articles' ? value : '/admin/articles' })
const markdown = new MarkdownIt({ html: false, linkify: true })
const preview = computed(() => markdown.render(form.contentMarkdown))
let latestRequestId = 0
onBeforeUnmount(() => { latestRequestId += 1 })
function fillForm(article) {
  const empty = emptyArticle()
  for (const key of Object.keys(empty)) form[key] = article[key] ?? empty[key]
  form.tagIds = [...(article.tagIds ?? [])]
  savedSnapshot.value = JSON.stringify(payload())
}
async function loadDocument() {
  // 首次保存只把/new替换成真实编号，已有输入及错误提示不因换网址而消失。
  if (form.id !== null && String(form.id) === String(route.params.id) && ready.value) return
  const id = ++latestRequestId
  loading.value = true; ready.value = false; error.value = ''
  try {
    const [nextCategories, nextTags, article] = await Promise.all([adminRequest('/categories'), adminRequest('/tags'), route.params.id ? adminRequest(`/articles/${route.params.id}`) : Promise.resolve(emptyArticle())])
    if (id !== latestRequestId) return
    categories.value = nextCategories; tags.value = nextTags; fillForm(article); ready.value = true
  } catch (cause) { if (id === latestRequestId) error.value = cause.message }
  finally { if (id === latestRequestId) loading.value = false }
}
async function persist() {
  if (!form.title.trim() || !form.contentMarkdown.trim()) throw new Error('请填写标题和Markdown正文。')
  const isNew = form.id === null
  const result = await adminRequest(isNew ? '/articles' : `/articles/${form.id}`, { method: isNew ? 'POST' : 'PUT', body: JSON.stringify(payload()) })
  // 先记录编号和快照；后续发布或网址同步失败也不会重复创建文章。
  form.id = result.id; form.status = result.status; savedSnapshot.value = JSON.stringify(payload())
  if (isNew) await navigateSaved(() => router.replace({ path: `/admin/articles/${result.id}/edit`, query: { from: backTarget.value } }))
}
async function save(publish = false) {
  if (busy.value || loading.value || !ready.value || form.deletedAt) return
  busy.value = true; error.value = ''; notifyAdmin('')
  let persisted = false
  try {
    await persist(); persisted = true
    if (publish) { const result = await adminRequest(`/articles/${form.id}/publish`, { method: 'POST' }); form.status = result.status }
    notifyAdmin(publish ? '文章已发布，访客现在可以阅读。' : '文章已保存。')
  } catch (cause) { error.value = (publish && persisted ? '文章已保存，但发布未完成：' : '') + cause.message }
  finally { busy.value = false }
}
async function resolveAction(accepted) {
  const requested = action.value; action.value = ''
  if (!accepted || busy.value) return
  if (requested === 'publish') return save(true)
  busy.value = true; error.value = ''
  try {
    if (requested === 'unpublish') {
      const result = await adminRequest(`/articles/${form.id}/unpublish`, { method: 'POST' })
      form.status = result.status; notifyAdmin('文章已撤回，当前未保存的输入仍保留。')
    } else if (requested === 'recycle') {
      await adminRequest(`/articles/${form.id}`, { method: 'DELETE' })
      savedSnapshot.value = JSON.stringify(payload()); notifyAdmin('文章已移到回收站。')
      await navigateSaved(() => router.replace(backTarget.value))
    }
  } catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}
async function retryLoad() { if (await askDiscard()) { ready.value = false; return loadDocument() } }
async function insertMarkdown(before, after = '', sample = '文字') {
  const input = textarea.value
  if (!input) return
  const start = input.selectionStart, end = input.selectionEnd
  const selected = form.contentMarkdown.slice(start, end) || sample
  form.contentMarkdown = form.contentMarkdown.slice(0, start) + before + selected + after + form.contentMarkdown.slice(end)
  await nextTick(); input.focus(); input.setSelectionRange(start + before.length, start + before.length + selected.length)
}
watch(() => route.params.id, loadDocument, { immediate: true })
</script>
<template>
  <section class="studio-editor"><RouterLink :to="backTarget">← 返回文章管理</RouterLink>
    <header class="studio-page-heading"><div><p class="studio-eyebrow">WRITE / KEEP A LITTLE LIGHT</p><h1>{{ form.id === null ? '新建文章' : '编辑文章' }}</h1><p role="status">{{ loading ? '正在加载…' : busy ? '正在保存或处理…' : dirty ? '有未保存的修改' : form.id === null ? '尚未创建草稿' : '当前修改已保存' }}</p></div><span v-if="ready" class="studio-status">{{ form.deletedAt ? '已回收' : form.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span></header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button v-if="!ready && !loading" type="button" @click="retryLoad">重新加载文章</button>
    <div v-if="ready && form.deletedAt" class="v2-state">这篇文章已回收，请到回收站恢复为草稿后再编辑。<RouterLink to="/admin/articles?recycled=true">打开回收站 →</RouterLink></div>
    <form v-else-if="ready" @submit.prevent="save(false)"><fieldset :disabled="busy || loading">
      <p v-if="form.status === 'PUBLISHED'" class="studio-warning">这篇文章已经公开，保存会立即更新访客看到的内容。需要停止公开时可先撤回。</p>
      <label>标题<input v-model="form.title" maxlength="200" required class="studio-title-input" /></label>
      <div class="studio-editor-meta"><label>栏目<select v-model="form.type"><option value="NOTE">笔记</option><option value="THOUGHT">心得</option><option value="DIARY">日记</option></select></label><label>分类<select v-model="form.categoryId"><option :value="null">未分类</option><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select></label></div>
      <label>摘要<textarea v-model="form.summary" maxlength="500" rows="3"></textarea></label>
      <div class="studio-tabs" role="group" aria-label="编辑模式"><button v-for="item in [['edit','编辑'],['preview','预览'],['split','分屏']]" :key="item[0]" type="button" :aria-pressed="mode === item[0]" @click="mode = item[0]">{{ item[1] }}</button></div>
      <div v-show="mode !== 'preview'" class="studio-markdown-toolbar" role="toolbar" aria-label="Markdown工具栏"><button type="button" @click="insertMarkdown('## ', '', '标题')">二级标题</button><button type="button" @click="insertMarkdown('**', '**')">粗体</button><button type="button" @click="insertMarkdown('*', '*')">斜体</button><button type="button" @click="insertMarkdown('`', '`', 'code')">行内代码</button><button type="button" @click="insertMarkdown('\n```text\n', '\n```\n', '代码')">代码块</button><button type="button" @click="insertMarkdown('[', '](https://example.com)', '链接文字')">链接</button><button type="button" @click="insertMarkdown('- ', '', '列表项')">列表</button></div>
      <div class="studio-writing" :class="{ split: mode === 'split' }"><label v-show="mode !== 'preview'">Markdown 正文<textarea ref="textarea" v-model="form.contentMarkdown" rows="22" :required="mode !== 'preview'"></textarea></label><section v-if="mode !== 'edit'" class="studio-preview" aria-label="正文预览"><p class="studio-eyebrow">PREVIEW</p><div class="markdown-body" v-html="preview"></div></section></div>
      <div class="studio-editor-options"><label>封面地址（可选）<input v-model="form.coverUrl" type="url" maxlength="2048" placeholder="https://example.com/cover.jpg" /></label><label class="studio-checkbox"><input v-model="form.pinned" type="checkbox" />置顶这篇文章</label></div>
      <div role="group" aria-label="文章标签" class="studio-tag-picker"><p>文章标签（最多20个）</p><label v-for="tag in tags" :key="tag.id" class="studio-checkbox"><input v-model="form.tagIds" type="checkbox" :value="tag.id" :disabled="form.tagIds.length >= 20 && !form.tagIds.includes(tag.id)" />{{ tag.name }}</label><p v-if="!tags.length">暂无标签，可到分类与标签页面创建。</p></div>
      <div class="studio-editor-actions"><button type="submit">{{ form.status === 'PUBLISHED' ? '保存公开修改' : '保存草稿' }}</button><button v-if="form.status !== 'PUBLISHED'" type="button" @click="action = 'publish'">保存并发布</button><button v-if="form.status === 'PUBLISHED'" type="button" @click="action = 'unpublish'">撤回公开</button><button v-if="form.id" type="button" @click="action = 'recycle'">移到回收站</button><span>{{ form.contentMarkdown.length }} 字符</span></div>
    </fieldset></form>
    <AdminConfirmDialog v-if="discardOpen" title="放弃未保存的修改？" message="离开后，尚未保存的输入会丢失。可以取消并先保存。" confirm-text="放弃并离开" @resolve="resolveDiscard" />
    <AdminConfirmDialog v-if="action" :title="action === 'publish' ? '确认公开文章？' : action === 'unpublish' ? '撤回公开？' : '移到回收站？'" :message="action === 'publish' ? (form.type === 'DIARY' ? '这篇日记将向所有访客公开，请确认内容适合展示。' : '将先保存当前输入，再公开给所有访客。') : action === 'unpublish' ? '撤回后访客无法读取，未保存输入仍留在编辑器。' : '正文、分类标签会保留；尚未保存的修改会放弃，恢复时先回到草稿。'" :confirm-text="action === 'publish' ? '确认发布' : action === 'unpublish' ? '确认撤回' : '确认回收'" @resolve="resolveAction" />
  </section>
</template>
