<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { adminLeaving, adminTarget, loginAdmin } from '../adminSession.js'
const route = useRoute()
const router = useRouter()
const password = ref('')
const busy = ref(false)
const error = ref('')
let disposed = false
let allowLeave = false
onBeforeRouteLeave(() => !busy.value || allowLeave)
onBeforeUnmount(() => { disposed = true; password.value = '' })
async function connect() {
  if (busy.value || adminLeaving.value) return
  busy.value = true
  error.value = ''
  try {
    await loginAdmin(password.value)
    if (!disposed) {
      allowLeave = true
      await router.replace(adminTarget(route.query.next))
    }
  } catch (cause) { if (!disposed) error.value = cause.message }
  finally { password.value = ''; busy.value = false }
}
</script>

<template>
  <section class="studio-login" aria-labelledby="studio-login-title">
    <p class="studio-eyebrow">A QUIET PLACE TO WRITE</p>
    <h1 id="studio-login-title">回到你的写作空间。</h1>
    <p>整理笔记、留下思考，也收藏愿意公开的生活片段。</p>
    <form @submit.prevent="connect">
      <p>管理员账号：<strong>admin</strong></p>
      <label>管理员密码<input v-model="password" type="password" autocomplete="current-password" required :disabled="busy || adminLeaving" /></label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button type="submit" :disabled="busy || adminLeaving">{{ busy ? '正在验证…' : '进入工作台 →' }}</button>
    </form>
    <small>刷新页面后需要重新登录。密码不会保存在本地存储中。</small>
  </section>
</template>
