<script setup>
import { computed } from 'vue'
const props = defineProps({
  kind: { type: String, default: 'empty' }, title: String, message: String,
  retryLabel: String, heading: { type: String, default: 'h2' },
  layout: { type: String, default: 'lines' }, compact: Boolean,
})
defineEmits(['retry'])
const caption = computed(() => {
  const text = props.message ?? ''
  if (props.kind !== 'error') return text
  if (/TimeoutError|timeout|timed out|aborted/i.test(text)) return '等待响应时间过长，请稍后重试。'
  if (/Failed to fetch|NetworkError|fetch failed|Load failed/i.test(text)) return '暂时无法连接，请检查网络后重试。'
  if (/HTTP\s*5\d\d/.test(text)) return '服务暂时无法响应，请稍后重试。'
  if (/Unexpected token|JSON|格式不正确/i.test(text)) return '收到的内容暂时无法使用，请重新加载。'
  return text || '内容暂时没能加载，请重试。'
})
</script>
<template>
  <section class="page-state" :class="[kind, { compact }]" :role="kind === 'error' ? 'alert' : kind === 'loading' ? 'status' : undefined" :aria-busy="kind === 'loading' || undefined">
    <p v-if="kind === 'not-found'" class="page-state-code" aria-hidden="true">404</p>
    <component :is="heading" v-if="title">{{ title }}</component>
    <p v-if="caption">{{ caption }}</p>
    <div v-if="kind === 'loading'" class="page-skeleton" :class="layout" aria-hidden="true"><div v-for="n in layout === 'article' ? 1 : 3" :key="n" class="page-skeleton-item"><i v-if="layout !== 'lines'" class="page-skeleton-cover"></i><i></i><i></i><i></i></div></div>
    <div v-if="retryLabel || $slots.default" class="page-state-actions"><button v-if="retryLabel" type="button" @click="$emit('retry')">{{ retryLabel }}</button><slot /></div>
  </section>
</template>
