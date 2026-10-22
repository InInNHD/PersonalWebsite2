<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { adminRequest, notifyAdmin } from '../adminSession.js'
import { useAdminLeave } from '../useAdminLeave.js'
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue'
function emptyResource() { return { id: null, title: '', url: '', description: '', published: false, sortOrder: 0, groupId: null, iconKey: 'link', recommendation: '' } }
const form = reactive(emptyResource()), items = ref([]), snapshot = ref(JSON.stringify(form))
const busy = ref(false), loading = ref(false), error = ref(''), listError = ref('')
const groups = ref([]), groupForm = reactive({ id: null, name: '', sortOrder: 0 })
const groupSnapshot = ref(JSON.stringify(groupForm)), groupError = ref('')
const dirty = computed(() => JSON.stringify(form) !== snapshot.value || JSON.stringify(groupForm) !== groupSnapshot.value)
const { discardOpen, askDiscard, resolveDiscard } = useAdminLeave(dirty, busy)
let latestRequestId = 0, latestGroupRequest = 0
onBeforeUnmount(() => { latestRequestId += 1; latestGroupRequest += 1 })
function fillForm(item) { Object.assign(form, emptyResource(), item); snapshot.value = JSON.stringify(form) }
async function openResource(item = emptyResource()) { if (await askDiscard()) { fillForm(item); cancelGroup(); error.value = '' } }
function editGroup(item = { id: null, name: '', sortOrder: 0 }) {
  if (JSON.stringify(groupForm) !== groupSnapshot.value) { groupError.value = '请先保存或取消当前分组修改。'; return }
  Object.assign(groupForm, item); groupSnapshot.value = JSON.stringify(groupForm); groupError.value = ''
}
function cancelGroup() { Object.assign(groupForm, { id: null, name: '', sortOrder: 0 }); groupSnapshot.value = JSON.stringify(groupForm); groupError.value = '' }
async function loadGroups() {
  const version = ++latestGroupRequest
  try { const data = await adminRequest('/resource-groups'); if (version === latestGroupRequest) groups.value = data }
  catch (cause) { if (version === latestGroupRequest) groupError.value = cause.message }
}
async function saveGroup() {
  if (busy.value) return
  busy.value = true; groupError.value = ''
  try {
    const { id, ...payload } = groupForm
    await adminRequest('/resource-groups' + (id == null ? '' : '/' + id), { method: id == null ? 'POST' : 'PUT', body: JSON.stringify(payload) })
    cancelGroup(); notifyAdmin('资源分组已保存。'); await loadGroups()
  } catch (cause) { groupError.value = cause.message }
  finally { busy.value = false }
}
async function loadResources() {
  const id = ++latestRequestId
  loading.value = true; listError.value = ''
  try { const data = await adminRequest('/resources'); if (id === latestRequestId) items.value = data }
  catch (cause) { if (id === latestRequestId) listError.value = cause.message }
  finally { if (id === latestRequestId) loading.value = false }
}
async function saveResource() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const { id, ...payload } = form
    const result = await adminRequest(id === null ? '/resources' : '/resources/' + id, { method: id === null ? 'POST' : 'PUT', body: JSON.stringify(payload) })
    fillForm(result); notifyAdmin(result.published ? '资源已保存并公开。' : '资源已保存为隐藏状态。')
    await loadResources() // 列表失败独立显示，已保存编号与表单不会丢失。
  } catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}
onMounted(() => { loadResources(); loadGroups() })
</script>
<template>
  <section><header class="studio-page-heading"><div><p class="studio-eyebrow">COLLECT / USEFUL LINKS</p><h1>资源管理</h1><p>按用途分组，为收藏补上推荐理由与展示顺序。</p></div><button type="button" :disabled="busy" @click="openResource()">新建资源</button></header>
    <div class="studio-resource-grid"><aside class="studio-panel"><h2>全部资源</h2><button type="button" :disabled="loading || busy" @click="loadResources">刷新资源列表</button><p v-if="loading" role="status">正在读取资源…</p><p v-if="listError" class="error" role="alert">{{ listError }}</p><p v-if="!loading && !listError && !items.length">暂时没有资源。</p><ul class="studio-name-list"><li v-for="item in items" :key="item.id"><button type="button" :disabled="busy" @click="openResource(item)">{{ item.title }} · {{ item.published ? '公开' : '隐藏' }}</button></li></ul></aside>
      <form class="studio-panel" @submit.prevent="saveResource"><fieldset :disabled="busy"><h2>{{ form.id === null ? '新资源' : '编辑资源' }}</h2><p role="status">{{ busy ? '保存中…' : dirty ? '有未保存的资源修改' : '当前无未保存修改' }}</p><p v-if="error" class="error" role="alert">{{ error }}</p><label>资源名称<input v-model="form.title" maxlength="200" required /></label><label>资源链接<input v-model="form.url" type="url" maxlength="2048" required placeholder="https://example.com" /></label><label>资源说明<textarea v-model="form.description" maxlength="500" rows="4"></textarea></label><label>用途分组<select v-model="form.groupId"><option :value="null">未分组</option><option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</option></select></label><label>卡片图标<select v-model="form.iconKey"><option value="link">链接</option><option value="code">代码</option><option value="book">阅读</option><option value="tool">工具</option><option value="course">课程</option></select></label><label>推荐理由（可选）<textarea v-model="form.recommendation" maxlength="300" rows="3" placeholder="它解决了什么问题，适合什么时候使用？"></textarea></label><label>组内排序数字<input v-model.number="form.sortOrder" type="number" min="0" max="999999" step="1" required /></label><label>展示状态<select v-model="form.published"><option :value="false">隐藏</option><option :value="true">公开</option></select></label><button type="submit">保存资源</button></fieldset></form>
    </div>
    <section class="studio-panel studio-group-settings"><h2>用途分组</h2><p>数字越小越靠前；没有公开资源的分组不会显示给访客。改名保留关联。</p><p v-if="groupError" class="error" role="alert">{{ groupError }}</p><button v-if="groupError" type="button" :disabled="busy" @click="loadGroups">重新读取分组</button><form @submit.prevent="saveGroup"><fieldset :disabled="busy"><label>分组名称<input v-model="groupForm.name" maxlength="50" required /></label><label>分组排序<input v-model.number="groupForm.sortOrder" type="number" min="0" max="999999" required /></label><button type="submit">{{ groupForm.id == null ? '新建分组' : '保存分组' }}</button><button type="button" @click="cancelGroup">取消分组编辑</button></fieldset></form><ul class="studio-name-list"><li v-for="group in groups" :key="group.id"><span>{{ group.name }} · 顺序 {{ group.sortOrder }}</span><button type="button" :disabled="busy" @click="editGroup(group)">编辑分组</button></li></ul></section>
    <AdminConfirmDialog v-if="discardOpen" title="放弃资源修改？" message="尚未保存的资源输入会丢失，是否继续？" confirm-text="放弃并继续" @resolve="resolveDiscard" />
  </section>
</template>
