<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  html: { type: String, required: true },
  codeBlocks: { type: Array, default: () => [] },
})
const content = ref(null)
const viewer = ref(null)
const image = ref(null)
const imageFailed = ref(false)
const status = ref('')
let revision = 0
let copying = false
let copiedButton = null
let timer = 0
let previousOverflow = null

function resetCopy() {
  window.clearTimeout(timer)
  if (copiedButton?.isConnected) copiedButton.textContent = '复制'
  copiedButton = null
}

function releaseScroll() {
  if (previousOverflow === null) return
  document.documentElement.style.overflow = previousOverflow
  previousOverflow = null
}

function closeImage() {
  if (viewer.value?.open) viewer.value.close()
  image.value = null
  releaseScroll()
}

async function copyCode(button) {
  // 同一时刻只处理一次点击，离开文章后的异步结果不更新新正文。
  if (copying) return
  const index = Number(button.dataset.codeIndex)
  if (!Number.isInteger(index) || index < 0 || index >= props.codeBlocks.length) return
  const current = revision
  copying = true
  resetCopy()
  status.value = ''
  let message = '已复制'
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
    await navigator.clipboard.writeText(props.codeBlocks[index])
  } catch {
    message = '复制失败'
  } finally {
    if (current === revision) copying = false
  }
  if (current !== revision || !content.value?.contains(button)) return
  button.textContent = message
  copiedButton = button
  status.value = message === '已复制' ? '代码已复制。' : '复制失败，请选中代码后手动复制。'
  timer = window.setTimeout(resetCopy, 2400)
}

function onContentClick(event) {
  const button = event.target.closest?.('button')
  if (!button || !content.value?.contains(button)) return
  if (button.hasAttribute('data-code-index')) {
    void copyCode(button)
    return
  }
  if (!button.hasAttribute('data-reading-image')) return
  const target = button.querySelector('img')
  if (!target?.complete || !target.naturalWidth) {
    status.value = '图片尚未加载成功，请稍后再试。'
    return
  }
  image.value = { src: target.currentSrc || target.src, alt: target.alt || '正文图片' }
  imageFailed.value = false
  // showModal提供焦点限制、Escape关闭和关闭后返回触发按钮。
  viewer.value.showModal()
  previousOverflow = document.documentElement.style.overflow
  document.documentElement.style.overflow = 'hidden'
}

function resetContent() {
  revision += 1
  copying = false
  resetCopy()
  status.value = ''
  closeImage()
}

watch(() => props.html, resetContent)
onBeforeUnmount(resetContent)
</script>

<template>
  <div ref="content" @click="onContentClick" v-html="html"></div>
  <p class="reading-media-status" role="status" aria-live="polite">{{ status }}</p>
  <dialog ref="viewer" class="reading-viewer" aria-label="正文图片查看"
          @cancel.prevent="closeImage" @close="releaseScroll"
          @click="event => { if (event.target === viewer) closeImage() }">
    <div class="reading-viewer-panel">
      <header>
        <span>图片查看</span>
        <button type="button" autofocus @click="closeImage" aria-label="关闭图片查看">关闭 ×</button>
      </header>
      <template v-if="image">
        <p v-if="imageFailed" role="alert">图片加载失败，请关闭后重试。</p>
        <img v-else :src="image.src" :alt="image.alt" referrerpolicy="no-referrer" @error="imageFailed = true">
        <p class="reading-viewer-caption">{{ image.alt }}</p>
      </template>
    </div>
  </dialog>
</template>