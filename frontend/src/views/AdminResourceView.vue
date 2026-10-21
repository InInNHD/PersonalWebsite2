<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { adminRequest, notifyAdmin } from '../adminSession.js'
import { useAdminLeave } from '../useAdminLeave.js'
import AdminConfirmDialog from '../components/AdminConfirmDialog.vue'
function emptyResource() { return { id: null, title: '', url: '', description: '', published: false, sortOrder: 0 } }
const form = reactive(emptyResource()), items = ref([]), snapshot = ref(JSON.stringify(form))
const busy = ref(false), loading = ref(false), error = ref(''), listError = ref('')
const dirty = computed(() => JSON.stringify(form) !== snapshot.value)
const { discardOpen, askDiscard, resolveDiscard } = useAdminLeave(dirty, busy)
let latestRequestId = 0
onBeforeUnmount(() => { latestRequestId += 1 })
function fillForm(item) { Object.assign(form, emptyResource(), item); snapshot.value = JSON.stringify(form) }
async function openResource(item = emptyResource()) { if (await askDiscard()) { fillForm(item); error.value = '' } }
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
onMounted(loadResources)
</script>
<template>
  <section><header class="studio-page-heading"><div><p class="studio-eyebrow">COLLECT / USEFUL LINKS</p><h1>资源管理</h1><p>整理资源链接、说明与展示顺序。</p></div><button type="button" :disabled="busy" @click="openResource()">新建资源</button></header>
    <div class="studio-resource-grid"><aside class="studio-panel"><h2>全部资源</h2><button type="button" :disabled="loading || busy" @click="loadResources">刷新资源列表</button><p v-if="loading" role="status">正在读取资源…</p><p v-if="listError" class="error" role="alert">{{ listError }}</p><p v-if="!loading && !listError && !items.length">暂时没有资源。</p><ul class="studio-name-list"><li v-for="item in items" :key="item.id"><button type="button" :disabled="busy" @click="openResource(item)">{{ item.title }} · {{ item.published ? '公开' : '隐藏' }}</button></li></ul></aside>
      <form class="studio-panel" @submit.prevent="saveResource"><fieldset :disabled="busy"><h2>{{ form.id === null ? '新资源' : '编辑资源' }}</h2><p role="status">{{ busy ? '保存中…' : dirty ? '有未保存的资源修改' : '当前无未保存修改' }}</p><p v-if="error" class="error" role="alert">{{ error }}</p><label>资源名称<input v-model="form.title" maxlength="200" required /></label><label>资源链接<input v-model="form.url" type="url" maxlength="2048" required placeholder="https://example.com" /></label><label>资源说明<textarea v-model="form.description" maxlength="500" rows="4"></textarea></label><label>排序数字<input v-model.number="form.sortOrder" type="number" min="0" max="999999" step="1" required /></label><label>展示状态<select v-model="form.published"><option :value="false">隐藏</option><option :value="true">公开</option></select></label><button type="submit">保存资源</button></fieldset></form>
    </div>
    <AdminConfirmDialog v-if="discardOpen" title="放弃资源修改？" message="尚未保存的资源输入会丢失，是否继续？" confirm-text="放弃并继续" @resolve="resolveDiscard" />
  </section>
</template>
