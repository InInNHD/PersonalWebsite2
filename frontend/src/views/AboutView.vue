<script setup>
import PageState from '../components/PageState.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { siteProfile } from '../siteProfile.js'
import ProfileAvatar from '../components/ProfileAvatar.vue'
import ArticlePagination from '../components/ArticlePagination.vue'

const snapshot = ref(null)
const loading = ref(false)
const error = ref('')
const repository = ref('')
const kind = ref('')
const page = ref(1)
const updatesTitle = ref(null)
let latestRequestId = 0
onBeforeUnmount(() => { latestRequestId += 1 })

// 文案作为普通文本显示；外链只允许http/https，不使用v-html。
function externalLink(value) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : '' }
  catch { return '' }
}
const githubLink = computed(() => externalLink(siteProfile.githubUrl))
const directions = computed(() => siteProfile.learningDirections.filter(item => item.title.trim()))
const experiences = computed(() => siteProfile.experiences.filter(item => item.title.trim()))
const contacts = computed(() => siteProfile.contactLinks.filter(item => item.label.trim() && externalLink(item.url)))
const projects = computed(() => snapshot.value?.projects ?? [])
const languages = computed(() => [...new Set(projects.value.map(item => item.language).filter(Boolean))])
const eventNames = { PUSH: '推送', RELEASE: 'Release', COMMIT: '历史提交' }
const filteredEvents = computed(() => (snapshot.value?.events ?? []).filter(item =>
    (!repository.value || item.repo === repository.value) && (!kind.value || item.kind === kind.value)))
const totalPages = computed(() => Math.ceil(filteredEvents.value.length / 10))
const visibleEvents = computed(() => filteredEvents.value.slice((page.value - 1) * 10, page.value * 10))
// 日期统一按北京时间显示，不受读者电脑的时区影响。
const dateFormat = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' })
const timeFormat = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', hour12: false })
const dateText = value => dateFormat.format(new Date(value))
const timeText = value => timeFormat.format(new Date(value))
const validDate = value => typeof value === 'string' && Number.isFinite(Date.parse(value))
const strings = (item, keys) => item && keys.every(key => typeof item[key] === 'string')

async function loadProjects() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''
  snapshot.value = null
  try {
    // 读取本站同步快照，浏览页面时不向GitHub发请求，也不依赖Java服务。
    const response = await fetch('/github-profile.json', { cache: 'no-cache', signal: AbortSignal.timeout(15000) })
    if (!response.ok) throw new Error(`项目资料加载失败：HTTP ${response.status}`)
    const data = await response.json()
    if (data.schemaVersion !== 1 || data.owner !== siteProfile.githubName
        || !Array.isArray(data.projects) || !Array.isArray(data.events)
        || !validDate(data.syncedAt)) throw new Error('项目资料格式不正确')
    const repoNames = new Set(data.projects.map(item => item?.fullName))
    if (data.projects.some(item => !strings(item, ['id', 'name', 'fullName', 'description', 'language', 'url'])
            || !item.name.trim() || !item.fullName.startsWith(`${data.owner}/`)
            || (item.pushedAt != null && !validDate(item.pushedAt)))
        || data.events.some(item => !strings(item, ['id', 'repo', 'title', 'url'])
            || !repoNames.has(item.repo) || !Object.hasOwn(eventNames, item.kind) || !validDate(item.date))) {
      throw new Error('项目资料格式不正确')
    }
    if (requestId !== latestRequestId) return
    snapshot.value = data
    repository.value = ''
    kind.value = ''
    page.value = 1
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '项目资料加载失败'
  } finally { if (requestId === latestRequestId) loading.value = false }
}
function resetPage() { page.value = 1 }
function changePage(nextPage) {
  if (nextPage >= 1 && nextPage <= totalPages.value) page.value = nextPage
}
async function showUpdates(name) {
  repository.value = name
  kind.value = ''
  page.value = 1
  await nextTick()
  updatesTitle.value?.focus({ preventScroll: true })
  updatesTitle.value?.scrollIntoView({ block: 'start' })
}
onMounted(loadProjects)
</script>

<template>
  <div class="about-v2">
    <section class="about-intro" aria-labelledby="about-title">
      <div class="about-portrait"><ProfileAvatar /><p>FIREFLY / FIELD NOTES</p><span class="about-portrait-copy">{{ siteProfile.profileDescription }}</span></div>
      <div class="about-introduction">
        <p class="v2-eyebrow">ABOUT / BEHIND THE NOTES</p>
        <h1 id="about-title">你好，我是<br><span>{{ siteProfile.nickname }}。</span></h1>
        <p class="about-tagline">{{ siteProfile.tagline }}</p>
        <p v-for="(paragraph, index) in siteProfile.aboutParagraphs" :key="index">{{ paragraph }}</p>
        <div class="about-actions">
          <a v-if="githubLink" :href="githubLink" target="_blank" rel="noopener noreferrer">{{ siteProfile.githubName || 'GitHub' }} ↗</a>
          <RouterLink to="/diary">公开日记 →</RouterLink><RouterLink to="/topics">内容地图 →</RouterLink>
        </div>
      </div>
    </section>

    <section v-if="directions.length" class="about-section" aria-labelledby="about-learning">
      <p class="v2-eyebrow">LEARNING / IN PROGRESS</p><h2 id="about-learning">正在学习</h2>
      <div class="about-grid"><article v-for="item in directions" :key="item.title" class="about-info-card"><h3>{{ item.title }}</h3><p v-if="item.description">{{ item.description }}</p></article></div>
    </section>

    <section class="about-section" aria-labelledby="about-projects">
      <header class="about-section-heading"><div><p class="v2-eyebrow">BUILD / IN PUBLIC</p><h2 id="about-projects">公开项目</h2></div><span v-if="snapshot">{{ projects.length }} 个项目</span></header>
      <PageState v-if="loading" kind="loading" layout="cards" title="正在读取项目与更新…" />
      <PageState v-else-if="error" kind="error" title="项目资料暂时没能加载" :message="error" retry-label="重新加载项目" @retry="loadProjects" />
      <template v-else-if="snapshot">
        <p class="about-source">来自 {{ snapshot.owner }} 的公开仓库 · 资料同步于 {{ dateText(snapshot.syncedAt) }} {{ timeText(snapshot.syncedAt) }}（北京时间）</p>
        <PageState v-if="!projects.length" title="暂时没有公开项目" message="有新的公开作品时，会更新在这里。" />
        <template v-else>
          <div v-if="languages.length" class="about-languages" aria-label="公开项目使用的主要语言"><span v-for="language in languages" :key="language">{{ language }}</span></div>
          <div class="about-grid">
            <article v-for="project in projects" :key="project.id" class="about-project">
              <div class="about-project-meta"><span>{{ project.language || '公开仓库' }}</span><span v-if="project.fork">Fork</span><span v-if="project.archived">已归档</span></div>
              <h3><a v-if="externalLink(project.url)" :href="externalLink(project.url)" target="_blank" rel="noopener noreferrer">{{ project.name }} ↗</a><span v-else>{{ project.name }}</span></h3>
              <p>{{ project.description || '项目说明见仓库 README。' }}</p>
              <footer><small v-if="project.pushedAt">最近推送 {{ dateText(project.pushedAt) }}</small><button type="button" @click="showUpdates(project.fullName)">查看更新 →</button></footer>
            </article>
          </div>
        </template>
      </template>
    </section>

    <section v-if="snapshot" class="about-section" aria-labelledby="project-updates">
      <p class="v2-eyebrow">CHANGELOG / A CONTINUING STORY</p><h2 id="project-updates" ref="updatesTitle" tabindex="-1">项目更新链</h2>
      <p class="about-source">推送与 Release 使用各自事件日期；历史提交按提交记录日期排列，不等同于推送时间。日期均为北京时间。</p>
      <details class="about-history-note"><summary>更新记录的范围</summary><p>每个仓库的公开推送活动仅提供近30天、最多300条；同步过的推送会继续保留。Release来自当前公开发布记录，历史提交覆盖当前可见分支；fork仓库仅收录本人提交，避免混入上游历史。此处不代表所有历史推送均可恢复。</p></details>
      <div class="about-update-filters">
        <label>项目<select v-model="repository" @change="resetPage"><option value="">全部项目</option><option v-for="project in projects" :key="project.id" :value="project.fullName">{{ project.name }}</option></select></label>
        <label>记录类型<select v-model="kind" @change="resetPage"><option value="">全部记录</option><option value="PUSH">推送</option><option value="RELEASE">Release</option><option value="COMMIT">历史提交</option></select></label>
        <p role="status">共 {{ filteredEvents.length }} 条记录</p>
      </div>
      <PageState v-if="!filteredEvents.length" title="当前条件下暂无更新记录" message="可以切换项目或记录类型，继续看看。" />
      <ol v-else class="about-updates">
        <li v-for="event in visibleEvents" :key="event.id">
          <time :datetime="event.date">{{ dateText(event.date) }}<small>{{ timeText(event.date) }}</small></time>
          <div><div class="about-update-meta"><span>{{ eventNames[event.kind] }}</span><span>{{ event.repo }}</span><span v-if="event.prerelease">预发布</span></div>
            <h3><a v-if="externalLink(event.url)" :href="externalLink(event.url)" target="_blank" rel="noopener noreferrer">{{ event.title }} ↗</a><span v-else>{{ event.title }}</span></h3></div>
        </li>
      </ol>
      <ArticlePagination aria-label="更新分页" :page="page" :total-pages="totalPages" :loading="false" @change="changePage" />
    </section>

    <section v-if="experiences.length" class="about-section" aria-labelledby="about-experiences">
      <p class="v2-eyebrow">MILESTONES / ALONG THE WAY</p><h2 id="about-experiences">一些经历</h2>
      <div class="about-grid"><article v-for="item in experiences" :key="item.title" class="about-info-card"><small v-if="item.period">{{ item.period }}</small><h3>{{ item.title }}</h3><p v-if="item.description">{{ item.description }}</p></article></div>
    </section>
    <section v-if="contacts.length" class="about-section" aria-labelledby="about-contact"><h2 id="about-contact">保持联系</h2><div class="about-actions"><a v-for="item in contacts" :key="item.label" :href="externalLink(item.url)" target="_blank" rel="noopener noreferrer">{{ item.label }} ↗</a></div></section>
    <nav class="about-explore" aria-label="继续探索"><RouterLink to="/">阅读笔记 →</RouterLink><RouterLink to="/archive">沿时间回看 →</RouterLink><RouterLink to="/resources">资源收藏 →</RouterLink></nav>
  </div>
</template>
