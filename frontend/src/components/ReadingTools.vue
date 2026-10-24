<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
const props = defineProps({ articleId: { type: [String, Number], required: true } })
const emit = defineEmits(['change'])
const size = ref(16), focused = ref(false), focusButton = ref(null)
const copyState = ref(''), copying = ref(false)
// 只保存字号，不保存文章内容、来源或登录信息；禁用存储时照常阅读。
try { if (localStorage.getItem('reading-font-size') === '19') size.value = 19 } catch { /* 使用标准字号 */ }
const shareUrl = computed(() => `${window.location.origin}/articles/${props.articleId}`)
let request = 0
function notify() { emit('change', { size: size.value, focused: focused.value }) }
function setSize(value) {
  if (![16, 19].includes(value)) return
  size.value = value
  try { localStorage.setItem('reading-font-size', String(value)) } catch { /* 本次会话仍生效 */ }
  notify()
}
function toggleFocus() { focused.value = !focused.value; notify() }
function escape(event) {
  // 图片弹窗先处理自己的Escape，避免一次按键同时退出两个模式。
  if (event.key === 'Escape' && focused.value && !document.querySelector('dialog[open]')) {
    event.preventDefault(); focused.value = false; notify(); focusButton.value?.focus({preventScroll:true})
  }
}
async function copyLink() {
  if (copying.value) return
  const id = ++request, url = shareUrl.value
  copying.value = true; copyState.value = ''
  try {
    await navigator.clipboard.writeText(url)
    if (id === request) copyState.value = '已复制文章链接'
  } catch { if (id === request) copyState.value = '复制失败，请选中下方链接手动复制' }
  finally { if (id === request) copying.value = false }
}
watch(() => props.articleId, () => { request += 1; copying.value = false; copyState.value = '' })
onMounted(() => { notify(); window.addEventListener('keydown', escape) })
onBeforeUnmount(() => { request += 1; window.removeEventListener('keydown', escape) })
</script>
<template>
  <section class="reading-tools" aria-label="阅读设置与分享">
    <div class="reading-tool-buttons"><div role="group" aria-label="正文字号">
      <button type="button" :aria-pressed="size === 16" @click="setSize(16)">标准字号</button>
      <button type="button" :aria-pressed="size === 19" @click="setSize(19)">大字号</button>
    </div>
    <button ref="focusButton" type="button" :aria-pressed="focused" @click="toggleFocus">{{ focused ? '退出专注' : '专注阅读' }}</button>
    <button type="button" :disabled="copying" @click="copyLink">{{ copying ? '正在复制…' : '复制文章链接' }}</button></div>
    <p v-if="copyState" role="status">{{ copyState }}</p>
    <label v-if="copyState.startsWith('复制失败')">文章链接<input :value="shareUrl" readonly @focus="$event.target.select()"></label>
  </section>
</template>
