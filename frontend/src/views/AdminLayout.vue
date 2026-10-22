<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ref } from 'vue'
import { siteProfile } from '../siteProfile.js'
import { adminConnected, adminLeaving, adminSessionError, adminNotice, loginAdmin } from '../adminSession.js'
const route = useRoute()
const router = useRouter()
const password = ref(''), verifying = ref(false), verificationError = ref('')
async function verifyAgain() {
  if (verifying.value) return
  verifying.value = true; verificationError.value = ''
  try { await loginAdmin(password.value) } catch (error) { verificationError.value = error.message }
  finally { password.value = ''; verifying.value = false }
}
function leaveAdmin() {
  if (!adminLeaving.value) return router.push('/admin/login')
}
</script>

<template>
  <div class="studio-shell" :class="{ 'is-login': route.path === '/admin/login' }">
    <aside v-if="route.path !== '/admin/login'" class="studio-sidebar">
      <RouterLink class="studio-brand" to="/"><span>f</span><strong>{{ siteProfile.siteName }}<small>WRITING STUDIO</small></strong></RouterLink>
      <p class="studio-eyebrow">内容工作台</p>
      <nav aria-label="管理导航">
        <RouterLink to="/admin/articles" :class="{ 'is-current': route.path.startsWith('/admin/articles') }">文章管理</RouterLink>
        <RouterLink to="/admin/taxonomy">分类与标签</RouterLink>
        <RouterLink to="/admin/resources">资源管理</RouterLink>
      </nav>
      <p class="studio-side-note">把今天的想法，<br>整理成下一篇记录。</p>
    </aside>
    <div class="studio-main">
      <header class="studio-topbar">
        <div><p class="studio-eyebrow">FIREFLY / WRITING STUDIO</p><strong>{{ route.meta.adminTitle }}</strong></div>
        <div class="studio-actions"><RouterLink to="/">查看网站 ↗</RouterLink>
          <button v-if="route.path !== '/admin/login'" type="button" :disabled="adminLeaving" @click="leaveAdmin">
            {{ adminLeaving ? '正在退出…' : (adminConnected ? '退出登录' : '重新登录') }}
          </button>
        </div>
      </header>
      <main id="main-content" tabindex="-1" class="studio-content">
        <p v-if="adminSessionError" class="error" role="alert">{{ adminSessionError }}</p>
        <p v-if="adminNotice" class="studio-notice" role="status">{{ adminNotice }}</p>
        <form v-if="route.meta.requiresAdmin && !adminConnected" class="studio-reauth" @submit.prevent="verifyAgain">
          <p>认证已失效。当前输入仍保留，验证后可继续保存。</p>
          <label>重新验证密码<input v-model="password" type="password" autocomplete="current-password" required :disabled="verifying" /></label>
          <p v-if="verificationError" class="error" role="alert">{{ verificationError }}</p><button :disabled="verifying">重新验证</button>
        </form>
        <!-- 编辑器在新建取得编号时保留实例；换号由编辑器的加载保护负责。 -->
        <RouterView v-slot="{ Component }"><component :is="Component" :key="route.meta.adminSection === 'article' ? 'article-editor' : route.path" /></RouterView>
      </main>
    </div>
  </div>
</template>
