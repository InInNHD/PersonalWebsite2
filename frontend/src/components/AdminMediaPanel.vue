<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { adminRequest } from '../adminSession.js'
const props = defineProps({ references: { type: Array, default: () => [] } })
const emit = defineEmits(['choose', 'previews', 'busy'])
const items = ref([]), page = ref(1), pages = ref(0), loading = ref(false), uploading = ref(false), error = ref('')
const input = ref(null), previews = ref({})
let revision = 0, disposed = false
function choose(item, role) { emit('choose', { url: item.url, role }) }
async function load(nextPage = page.value) {
  const version = ++revision
  loading.value = true; error.value = ''
  try {
    const data = await adminRequest('/media?page=' + nextPage)
    if (disposed || version !== revision) return
    items.value = data.items; page.value = data.page; pages.value = data.totalPages
    const needed = new Set([...data.items.map(item => item.url), ...props.references])
    for (const [url, value] of Object.entries(previews.value)) {
      if (!needed.has(url)) { URL.revokeObjectURL(value); delete previews.value[url] }
    }
    await Promise.all([...needed].map(async url => {
      if (previews.value[url]) return
      const blob = await adminRequest('/media/' + url.split('/').at(-1) + '/content', { responseType: 'blob' })
      if (disposed || version !== revision) return
      previews.value[url] = URL.createObjectURL(blob)
    }))
    if (!disposed && version === revision) emit('previews', { ...previews.value })
  } catch (cause) { if (!disposed && version === revision) error.value = cause.message }
  finally { if (!disposed && version === revision) { loading.value = false; emit('previews', { ...previews.value }) } }
}
async function upload() {
  const file = input.value?.files?.[0]
  if (!file || uploading.value) return
  error.value = ''
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024 || file.size === 0) { error.value = '请选择不超过5MiB的PNG或JPEG图片。'; return }
  uploading.value = true; emit('busy', true)
  try {
    const body = new FormData(); body.append('file', file)
    await adminRequest('/media', { method: 'POST', body })
    if (!disposed) { input.value.value = ''; await load(1) }
  } catch (cause) { if (!disposed) error.value = cause.message }
  finally { if (!disposed) { uploading.value = false; emit('busy', false) } }
}
onMounted(() => load())
watch(() => props.references.join('|'), () => { if (!uploading.value) load() })
onBeforeUnmount(() => { disposed = true; revision += 1; for (const value of Object.values(previews.value)) URL.revokeObjectURL(value) })
</script>
<template>
  <section class="studio-media"><h2>图片库</h2><p>PNG / JPEG · 最大5MiB · 边长最多6000px。保存关联并发布文章后，图片才会公开。</p><label>上传本地图片<input ref="input" type="file" accept="image/png,image/jpeg" :disabled="uploading || loading" @change="upload" /></label><p v-if="uploading" role="status">正在校验并保存图片…</p><p v-if="error" class="error" role="alert">{{ error }}</p><button v-if="error && input?.files?.length" type="button" :disabled="uploading || loading" @click="upload">重试上传</button><button v-if="error" type="button" :disabled="uploading || loading" @click="load(page)">重新读取图片库</button><p v-if="loading" role="status">正在读取图片…</p><p v-else-if="!items.length && !error">还没有图片，上传一张试试。</p><ul class="studio-media-grid"><li v-for="item in items" :key="item.id"><img v-if="previews[item.url]" :src="previews[item.url]" alt="图片库预览" width="160" height="100" /><span v-else class="studio-media-placeholder">预览暂不可用</span><small>{{ item.width }} × {{ item.height }} · {{ Math.ceil(item.byteSize / 1024) }}KB</small><div><button type="button" :disabled="loading || uploading" @click="choose(item, 'COVER')">设为封面</button><button type="button" :disabled="loading || uploading" @click="choose(item, 'BODY')">插入正文</button></div></li></ul><nav v-if="pages > 1" aria-label="图片库分页"><button type="button" :disabled="page <= 1 || loading || uploading" @click="load(page - 1)">上一页</button><span>{{ page }} / {{ pages }}</span><button type="button" :disabled="page >= pages || loading || uploading" @click="load(page + 1)">下一页</button></nav>
  </section>
</template>
