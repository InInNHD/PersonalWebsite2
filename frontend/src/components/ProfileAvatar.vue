<script setup>
import { computed, ref, watch } from 'vue'
import { siteProfile } from '../siteProfile.js'

const failed = ref(false)
const source = computed(() => siteProfile.avatarUrl.trim())
const initial = computed(() => [...siteProfile.nickname.trim()][0] || 'f')
watch(source, () => { failed.value = false })
</script>

<template>
  <span class="profile-avatar">
    <img v-if="source && !failed" :src="source" :alt="`${siteProfile.nickname}的头像`"
         referrerpolicy="no-referrer" @error="failed = true">
    <span v-else role="img" :aria-label="`${siteProfile.nickname}的头像标识`">{{ initial }}</span>
  </span>
</template>