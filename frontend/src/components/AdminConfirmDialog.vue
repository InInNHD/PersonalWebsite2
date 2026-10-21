<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
defineProps({ title: String, message: String, confirmText: { type: String, default: '确认' } })
const emit = defineEmits(['resolve'])
const dialog = ref(null)
let previousFocus
onMounted(() => { previousFocus = document.activeElement; dialog.value.showModal() })
onBeforeUnmount(() => { dialog.value?.close(); if (previousFocus?.isConnected) previousFocus.focus() })
</script>
<template>
  <dialog ref="dialog" class="studio-dialog" aria-labelledby="admin-confirm-title" aria-describedby="admin-confirm-message" @cancel.prevent="emit('resolve', false)">
    <h2 id="admin-confirm-title">{{ title }}</h2><p id="admin-confirm-message">{{ message }}</p>
    <div class="studio-dialog-actions"><button type="button" autofocus @click="emit('resolve', false)">取消</button><button type="button" class="studio-primary" @click="emit('resolve', true)">{{ confirmText }}</button></div>
  </dialog>
</template>
