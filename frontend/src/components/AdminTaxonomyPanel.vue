<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { adminRequest } from '../adminSession.js'
const props = defineProps({ path: String, label: String })
const emit = defineEmits(['dirty', 'busy'])
const items = ref([]), name = ref(''), original = ref(''), editingId = ref(null), loading = ref(false), busy = ref(false), error = ref(''), message = ref('')
const dirty = computed(() => name.value !== original.value)
let disposed = false, requestId = 0
watch(dirty, value => emit('dirty', value), { immediate: true })
watch(busy, value => emit('busy', value), { flush: 'sync' })
onBeforeUnmount(() => { disposed = true; requestId += 1 })
async function loadItems() {
  const id = ++requestId
  loading.value = true; error.value = ''
  try { const data = await adminRequest(props.path); if (id === requestId) items.value = data }
  catch (cause) { if (id === requestId) error.value = cause.message }
  finally { if (id === requestId) loading.value = false }
}
function edit(item) {
  if (dirty.value || busy.value) { error.value = '请先保存或取消当前名称修改。'; return }
  editingId.value = item.id; name.value = item.name; original.value = item.name; error.value = ''; message.value = ''
}
function cancel() { editingId.value = null; name.value = ''; original.value = ''; error.value = '' }
async function saveName() {
  if (busy.value) return
  busy.value = true; error.value = ''; message.value = ''
  try {
    const value = name.value.trim()
    if (!value || value.length > 50) throw new Error(`${props.label}名称需要包含1～50个字符。`)
    const result = await adminRequest(props.path + (editingId.value === null ? '' : '/' + editingId.value), { method: editingId.value === null ? 'POST' : 'PUT', body: JSON.stringify({ name: value }) })
    if (disposed) return
    items.value = [...items.value.filter(item => item.id !== result.id), result].sort((a, b) => a.id - b.id)
    message.value = `${props.label}已保存。`; cancel()
  } catch (cause) { if (!disposed) error.value = cause.message }
  finally { if (!disposed) busy.value = false }
}
onMounted(loadItems)
</script>
<template>
  <section class="studio-taxonomy-panel" :aria-label="label + '管理'">
    <h2>{{ label }}管理</h2><p v-if="loading" role="status">正在读取…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="message" role="status">{{ message }}</p>
    <form @submit.prevent="saveName"><label>{{ editingId === null ? '新增' : '重命名' }}{{ label }}<input v-model="name" required maxlength="50" :disabled="busy" /></label><div class="studio-dialog-actions"><button type="submit" :disabled="busy">{{ busy ? '保存中…' : '保存' + label }}</button><button type="button" :disabled="busy" @click="cancel">取消修改</button></div></form>
    <button type="button" :disabled="loading || busy" @click="loadItems">刷新{{ label }}列表</button>
    <p v-if="!loading && !items.length">暂时没有{{ label }}。</p>
    <ul class="studio-name-list"><li v-for="item in items" :key="item.id"><span>{{ item.name }}</span><button type="button" :disabled="busy" :aria-label="'重命名' + label + '：' + item.name" @click="edit(item)">重命名</button></li></ul>
  </section>
</template>
