# 第四轮4C：个人展示、公开项目与GitHub更新链

日期：2026-10-20。基线为4A/4B已验收的工作区。本节为IDEA手敲指南；**2026-10-21实际源码及第四轮组合验收已通过**。前端54项、隔离MySQL后端24项、两端构建及浏览器检查通过，公开快照已生成。详见[V2-PROFILE-ACCEPTANCE.md](V2-PROFILE-ACCEPTANCE.md)。

## 1. 目标与数据来源

按你的要求，使用GitHub账号`InInNHD`的全部public项目填充公开项目区，并整理有日期及来源链接的更新链。当前读取到6个公开仓库：PersonalWebsite2、dsh-recheck、fireflyiv-blog、starticket、Logistics、Community-healthcare-system。包括fork和归档仓库时如实标注，不冒充原创。

你已确认区分三类记录：

| 类型 | 日期依据 | 范围 |
| --- | --- | --- |
| 推送 | GitHub PushEvent的created_at | 按每个公开仓库读取，包含协作者/工具事件；每个活动目录提供近30天、最多300条；同步过的推送按事件编号保留 |
| Release | Release的published_at | 当前所有公开Release，包括标注为预发布的版本；不展示草稿 |
| 历史提交 | commit的committer日期，缺失时使用author日期 | 当前可见分支的全部可读取提交，跨分支按SHA去重；不把提交日期当作推送日期 |

公开活动无法恢复每一次历史push的确切时间，也不能保证找回被删除分支或强推前的提交。页面会说明这一边界，不用仓库updated_at伪造历史日期。官方依据见[GitHub Events](https://docs.github.com/en/rest/activity/events)、[Releases](https://docs.github.com/en/rest/releases/releases)和[Commits](https://docs.github.com/en/rest/commits/commits)。

运行同步命令→读取公开仓库/活动/Release/分支提交→完整成功后原子替换`frontend/public/github-profile.json`→关于页读取本站快照→项目筛选、类型筛选、10条分页及来源外链。日期统一显示北京时间。

以后每次推送或发布后运行一次同步命令，再重新构建页面即可更新；GitHub公开活动可能延迟出现，发现缺失时稍后再同步。连续超过30天未同步，过期推送不能补齐。这里不配置后台定时任务、远程workflow或Webhook。

头像暂沿用字母f，支持以后填本站图片路径，图片失效自动回退；首页作者卡与关于页共享。学习方向、个人经历和额外联系方式没有资料时留空并隐藏，不根据代码语言推断个人熟练度。项目中的主要语言仅作为仓库元数据展示。

本节不改Java、不改MySQL、不执行迁移、不安装依赖。GitHub昵称/主页和原有个人介绍保持集中配置；GitHub外链从原先的网站仓库地址调整为账号主页，仓库入口在项目卡片中展示。

## 2. 手敲文件和顺序

根目录`E:\PersonalWebsite2`。只输入代码框内容，不把反引号或说明写入代码。

| 步骤 | 文件 | 操作 |
| --- | --- | --- |
| 1 | frontend/src/siteProfile.js | 完整替换 |
| 2 | frontend/src/components/ProfileAvatar.vue | 新建 |
| 3 | frontend/src/views/AboutView.vue | 完整替换，删除原scoped样式 |
| 4 | frontend/src/views/ArticleListView.vue | 新增import并替换头像一行 |
| 5 | frontend/src/v2.css | 末尾追加一次 |
| 6 | frontend/scripts/sync-github.mjs | 新建；scripts目录不存在则先创建 |
| 7 | frontend/public/github-profile.json | 执行命令自动生成，不手敲数据 |

## 3. 个人配置

文件：`frontend/src/siteProfile.js`。

完整替换文件。

```js
// 这里只放愿意公开的资料；首页、关于页和页脚共同引用。
// 普通对象足够，内容修改后 Vite 会重新加载，不需要 ref 或后台接口。
export const siteProfile = {
    siteName: '萤火手记',
    nickname: 'firefly1v',
    tagline: '记录学习、思考与生活。',
    // 首页与作者卡共同使用的公开文案。
    heroTitle: '把思考留下，',
    heroAccent: '让知识生长。',
    heroDescription: '在这里整理学习中的发现，也收藏生活里的微光。',
    profileDescription: '把零散的知识连成线，把普通的日子写成篇。',
    aboutParagraphs: [
        '我在这里整理学习过程中的笔记，记录思考与心得，也保留一些愿意分享的生活片段。',
        '希望这些记录能帮助我回顾自己的成长，也为偶然来到这里的人提供一点参考。',
    ],
    // 暂未确定的信息留空，不生成占位链接。
    githubName: 'InInNHD',
    githubUrl: 'https://github.com/InInNHD',
    // 可填public中的相对站点路径，例如/images/avatar.webp；空或加载失败时用字母标识。
    avatarUrl: '',
    // 没有提供真实资料就留空，关于页隐藏对应区块。
    learningDirections: [],
    experiences: [],
    contactLinks: [],
}
```

## 4. 共享头像

文件：`frontend/src/components/ProfileAvatar.vue`。

新建完整文件。

```vue
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
```

## 5. 关于页

文件：`frontend/src/views/AboutView.vue`。

完整替换文件。

```vue
<script setup>
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
    const response = await fetch('/github-profile.json', { cache: 'no-cache' })
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
      <div v-if="loading" class="v2-state" role="status">正在读取项目与更新…</div>
      <div v-else-if="error" class="v2-state" role="alert"><p>{{ error }}</p><button type="button" @click="loadProjects">重新加载项目</button></div>
      <template v-else-if="snapshot">
        <p class="about-source">来自 {{ snapshot.owner }} 的公开仓库 · 资料同步于 {{ dateText(snapshot.syncedAt) }} {{ timeText(snapshot.syncedAt) }}（北京时间）</p>
        <div v-if="!projects.length" class="v2-state">暂时没有公开项目。</div>
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
      <details class="about-history-note"><summary>更新记录的范围</summary><p>每个仓库的公开推送活动仅提供近30天、最多300条；同步过的推送会继续保留。Release来自当前公开发布记录，历史提交覆盖当前可见分支。此处不代表所有历史推送均可恢复。</p></details>
      <div class="about-update-filters">
        <label>项目<select v-model="repository" @change="resetPage"><option value="">全部项目</option><option v-for="project in projects" :key="project.id" :value="project.fullName">{{ project.name }}</option></select></label>
        <label>记录类型<select v-model="kind" @change="resetPage"><option value="">全部记录</option><option value="PUSH">推送</option><option value="RELEASE">Release</option><option value="COMMIT">历史提交</option></select></label>
        <p role="status">共 {{ filteredEvents.length }} 条记录</p>
      </div>
      <div v-if="!filteredEvents.length" class="v2-state">当前条件下暂无更新记录。</div>
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
```

## 6. 首页接入共享头像

文件：`frontend/src/views/ArticleListView.vue`。

在script setup现有这一行：

```js
import SiteStats from '../components/SiteStats.vue'
```

的下一行增加：

```js
import ProfileAvatar from '../components/ProfileAvatar.vue'
```

再到template中`<section class="v2-profile">`下面，将这一整行：

```vue
          <span class="v2-avatar" aria-hidden="true">f</span>
```

替换为：

```vue
          <ProfileAvatar class="v2-avatar" />
```

保留其余首页代码、统计和分类/归档/日记入口。页头的字母f是站点标识，不在本节改为个人照片。

## 7. 追加CSS

文件：`frontend/src/v2.css`。

保留原有全部内容，在末尾追加一次：

```css
/* 第四轮4C：个人介绍、公开项目和有来源的更新链。 */
.profile-avatar { display: inline-grid; place-items: center; overflow: hidden; flex: none; background: #263e34; color: var(--color-accent); }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.about-v2 { min-width: 0; }
.about-intro { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 64px; align-items: center; padding: 18px 0 48px; }
.about-portrait { padding: 38px 30px; border: 1px solid var(--color-border); border-radius: 18px; background: linear-gradient(145deg, #1e332c, var(--color-surface)); text-align: center; }
.about-portrait .profile-avatar { width: 150px; height: 150px; border: 1px solid #577367; border-radius: 50%; font: italic 100px Georgia, serif; }
.about-portrait p { font-size: 10px; letter-spacing: 2px; margin: 28px 0 16px; }
.about-portrait-copy { display: block; font-size: 13px; color: var(--color-muted); line-height: 1.9; }
.about-introduction h1 { margin: 14px 0 22px; font-size: clamp(30px, 3.8vw, 46px); line-height: 1.3; overflow-wrap: anywhere; }
.about-introduction h1 span { color: var(--color-accent); }
.about-introduction > p:not(.v2-eyebrow) { color: var(--color-muted); line-height: 1.9; overflow-wrap: anywhere; }
.about-introduction .about-tagline { font-size: 18px; color: var(--color-text); }
.about-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 24px; margin-top: 24px; }
.about-actions a { display: inline-flex; align-items: center; min-height: 44px; font-size: 13px; overflow-wrap: anywhere; }
.about-section { border-top: 1px solid var(--color-border); padding: 40px 0; min-width: 0; }
.about-section h2 { margin: 10px 0 24px; font-size: 28px; scroll-margin-top: 24px; }
.about-section-heading { display: flex; justify-content: space-between; align-items: end; gap: 18px; }
.about-section-heading > span, .about-source { font-size: 12px; color: var(--color-muted); line-height: 1.9; }
.about-section-heading > span { padding-bottom: 24px; }
.about-languages { display: flex; flex-wrap: wrap; gap: 8px; margin: 20px 0; }
.about-languages span, .about-update-meta > span:first-child { font-size: 11px; padding: 4px 8px; background: var(--color-accent-soft); border-radius: 5px; color: var(--color-accent); }
.about-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.about-project, .about-info-card { min-width: 0; padding: 26px; border: 1px solid var(--color-border); border-radius: 14px; background: var(--color-surface); }
.about-project { display: flex; flex-direction: column; }
.about-project-meta { display: flex; flex-wrap: wrap; gap: 12px; font-size: 11px; color: var(--color-muted); }
.about-project h3, .about-info-card h3 { margin: 16px 0 12px; font-size: 22px; overflow-wrap: anywhere; }
.about-project h3 a { color: var(--color-text); }
.about-project > p, .about-info-card p { font-size: 14px; color: var(--color-muted); line-height: 1.9; overflow-wrap: anywhere; }
.about-project footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin-top: auto; padding-top: 18px; }
.about-project footer small { color: var(--color-muted); font-size: 11px; }
.about-project footer button { font-size: 12px; }
.about-history-note { padding: 14px 18px; margin: 18px 0; border: 1px solid var(--color-border); border-radius: 9px; font-size: 12px; color: var(--color-muted); line-height: 1.9; }
.about-history-note summary { cursor: pointer; }
.about-update-filters { display: flex; align-items: end; flex-wrap: wrap; gap: 20px; margin: 24px 0; }
.about-update-filters label { display: grid; gap: 8px; min-width: 180px; max-width: 100%; font-size: 12px; color: var(--color-muted); }
.about-update-filters select { min-width: 0; max-width: 100%; }
.about-update-filters p { margin: 0 0 10px; color: var(--color-muted); font-size: 12px; }
.about-updates { list-style: none; padding: 0; margin: 30px 0; }
.about-updates li { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 28px; border-bottom: 1px solid var(--color-border); padding: 24px 0; }
.about-updates time { color: var(--color-muted); font-size: 13px; line-height: 1.9; }
.about-updates time small { display: block; font-size: 11px; }
.about-update-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; color: var(--color-muted); font-size: 11px; overflow-wrap: anywhere; }
.about-updates h3 { margin: 12px 0 0; font-size: 17px; line-height: 1.8; overflow-wrap: anywhere; }
.about-updates h3 a { color: var(--color-text); }
.about-explore { display: flex; flex-wrap: wrap; gap: 16px 32px; padding: 24px 0 40px; border-top: 1px solid var(--color-border); }
.about-explore a { min-height: 44px; display: inline-flex; align-items: center; font-size: 14px; }
@media (max-width: 800px) { .about-intro { grid-template-columns: 230px minmax(0, 1fr); gap: 32px; } }
@media (max-width: 600px) {
  .about-intro, .about-grid { grid-template-columns: minmax(0, 1fr); }
  .about-intro { gap: 28px; }
  .about-portrait { padding: 28px; }
  .about-portrait .profile-avatar { width: 110px; height: 110px; font-size: 74px; }
  .about-section-heading { align-items: start; flex-direction: column; gap: 0; }
  .about-update-filters { display: grid; grid-template-columns: minmax(0, 1fr); }
  .about-update-filters label { min-width: 0; }
  .about-updates li { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .about-updates time small { display: inline; margin-left: 12px; }
}
```

## 8. 本地GitHub同步脚本

文件：`frontend/scripts/sync-github.mjs`。

新建完整文件。使用Node自带fetch和文件API，不新增依赖。

```js
import { readFile, writeFile, rename, mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { siteProfile } from '../src/siteProfile.js'

// 只在本机执行。可选GITHUB_TOKEN只由同步进程读取，不写入JSON或前端代码。
// 一次完整成功才替换快照；任一请求失败时保留原文件，避免把失败显示成0个项目。
export async function collectGitHub(owner, previous = null, request = fetch) {
  if (!/^[a-z\d](?:[a-z\d-]{0,37}[a-z\d])?$/i.test(owner)) throw new Error('GitHub用户名格式错误')
  const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'PersonalWebsite2-profile-sync' }
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`
  async function pages(path, maxPages = Infinity) {
    const items = []
    for (let page = 1; page <= maxPages; page++) {
      const url = new URL(`https://api.github.com${path}`)
      url.searchParams.set('per_page', '100')
      url.searchParams.set('page', String(page))
      const response = await request(url, { headers, signal: AbortSignal.timeout(20000) })
      if (!response.ok) throw new Error(`GitHub读取失败：HTTP ${response.status}，${url.pathname}`)
      const data = await response.json()
      if (!Array.isArray(data)) throw new Error('GitHub列表响应格式错误')
      items.push(...data)
      if (!response.headers.get('link')?.includes('rel="next"')) break
    }
    return items
  }

  // owner公开列表包含原创、fork和归档项目；标签如实显示，不把fork当原创。
  const repositories = (await pages(`/users/${owner}/repos?type=owner&sort=full_name`))
    .filter(repo => !repo.private && repo.owner?.login.toLowerCase() === owner.toLowerCase())
  const allowed = new Set(repositories.map(repo => repo.full_name))
  const projects = repositories.map(repo => ({
    id: String(repo.id), fullName: repo.full_name, name: repo.name,
    description: repo.description ?? '', url: `https://github.com/${repo.full_name}`,
    language: repo.language ?? '', fork: repo.fork, archived: repo.archived,
    createdAt: repo.created_at, pushedAt: repo.pushed_at,
  })).sort((a, b) => (b.pushedAt ?? '').localeCompare(a.pushedAt ?? '') || a.fullName.localeCompare(b.fullName))
  // 只累积之前已抓到的推送。现在不再公开/已删除的仓库及其历史不继续展示。
  const pushes = new Map((previous?.owner === owner ? previous.events ?? [] : [])
    .filter(event => event.kind === 'PUSH' && allowed.has(event.repo))
    .map(event => [event.id, { id: event.id, kind: 'PUSH', repo: event.repo,
      date: event.date, title: event.title, url: event.url }]))
  // 按仓库取活动，包含协作者或工具推送，不局限于账号本人触发的事件。
  const activity = []
  for (const repo of repositories) {
    activity.push(...await pages(`/repos/${repo.full_name}/events`, 3))
  }
  for (const event of activity) {
    if (!event.public || event.type !== 'PushEvent' || !allowed.has(event.repo?.name)) continue
    const head = event.payload?.head
    const before = event.payload?.before
    const branch = (event.payload?.ref ?? '').replace(/^refs\/heads\//, '')
    const root = `https://github.com/${event.repo.name}`
    const url = /^[a-f\d]{40}$/i.test(head ?? '')
      ? (/^[a-f\d]{40}$/i.test(before ?? '') && !/^0+$/.test(before)
        ? `${root}/compare/${before}...${head}` : `${root}/commit/${head}`) : root
    pushes.set(`push:${event.id}`, { id: `push:${event.id}`, kind: 'PUSH', repo: event.repo.name,
      date: event.created_at, title: branch ? `推送到 ${branch}` : '公开推送', url })
  }
  const events = [...pushes.values()]
  for (const repo of repositories) {
    const root = `/repos/${repo.full_name}`
    // Release使用published_at，不拿仓库updated_at或标签提交日期冒充发布时间。
    for (const release of await pages(`${root}/releases`)) {
      if (release.draft || !release.published_at) continue
      events.push({ id: `release:${release.id}`, kind: 'RELEASE', repo: repo.full_name,
        date: release.published_at, title: release.name || release.tag_name,
        prerelease: release.prerelease, url: release.html_url })
    }
    // 公共活动无法恢复更早的每次push：当前可见分支的历史明确标为COMMIT。
    // 不包含作者邮箱、原始payload或token，提交标题仅保留首行。
    const commits = new Map()
    for (const branch of await pages(`${root}/branches`)) {
      for (const commit of await pages(`${root}/commits?sha=${encodeURIComponent(branch.name)}`)) {
        commits.set(commit.sha, commit)
      }
    }
    for (const commit of commits.values()) {
      events.push({ id: `commit:${repo.id}:${commit.sha}`, kind: 'COMMIT', repo: repo.full_name,
        date: commit.commit.committer?.date ?? commit.commit.author?.date,
        title: commit.commit.message.split(/\r?\n/)[0], url: commit.html_url })
    }
  }
  return { schemaVersion: 1, owner, syncedAt: new Date().toISOString(), projects,
    events: events.filter(event => Number.isFinite(Date.parse(event.date)))
      .sort((a, b) => b.date.localeCompare(a.date) || a.id.localeCompare(b.id)) }
}

// 从scripts相对定位，不依赖终端当前所在目录。
// 临时文件与目标同目录，写完后rename，读取中的页面不会遇到半份JSON。
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const destination = fileURLToPath(new URL('../public/github-profile.json', import.meta.url))
  let previous = null
  try { previous = JSON.parse(await readFile(destination, 'utf8')) }
  catch (error) { if (error.code !== 'ENOENT') throw error }
  const data = await collectGitHub(siteProfile.githubName, previous)
  await mkdir(dirname(destination), { recursive: true })
  await writeFile(destination + '.tmp', JSON.stringify(data, null, 2) + '\n', 'utf8')
  await rename(destination + '.tmp', destination)
  console.log(`GitHub同步完成：${data.projects.length}个公开项目，${data.events.length}条记录。`)
}
```

## 9. 生成资料并启动

先保存以上文件，在项目根目录执行：

```powershell
cd E:\PersonalWebsite2
node frontend/scripts/sync-github.mjs
```

成功时会输出公开项目和记录数量，并生成`frontend/public/github-profile.json`。该文件只包含公开展示字段，不包含token、作者邮箱或原始权限/payload。它是运行结果，不是让你手敲的业务代码，应随网站源码一起提交；每次同步可能产生JSON更新。

出现超时、403限流或任何分页失败时，脚本失败退出，原快照保持不变。不要为此删除原JSON。如果频繁手动同步触发匿名额度，可仅在运行脚本的终端设置可选`GITHUB_TOKEN`；不要写入siteProfile、JSON、Vite环境变量或提交文件。本节默认使用不带token的公开读取。

开发预览运行：

```powershell
cd E:\PersonalWebsite2\frontend
npm.cmd run dev
```

打开终端给出的地址后进入“关于”。Java停止时个人介绍、项目和更新链仍应可读；它们不调用Java接口。以后每次推送或创建Release后重新运行同步命令，已有生产预览还需重新执行`npm.cmd run build`。

## 10. 可选的真实资料

当前三个列表均为`[]`，对应区块隐藏。以后填写时，每个数组项使用以下字段；仅填愿意公开的真实资料，字符串为空的标题不会生成卡片：

| 字段 | 每项结构 | 说明 |
| --- | --- | --- |
| learningDirections | `{ title: '真实学习方向', description: '真实说明' }` | 不用百分比或虚构熟练度 |
| experiences | `{ period: '真实日期范围', title: '真实事项', description: '真实说明' }` | 不编造工作/教育经历 |
| contactLinks | `{ label: '链接名称', url: 'https://完整网址' }` | 只生成http/https外链 |

头像可放在`frontend/public/images/avatar.webp`，并将avatarUrl填为`/images/avatar.webp`；路径无需包含public。不要把本机绝对路径或IDEA文件路径填成图片URL。

## 11. 验收和版本

| 检查 | 通过标准 |
| --- | --- |
| 个人展示 | 昵称、介绍、GitHub主页与共享配置一致；空资料不产生占位经历 |
| 头像 | 关于页和首页一致；未配置或坏图时字母回退，不残留破图 |
| 全部公开项目 | 6个现有public仓库均可见，名称/描述/主要语言按GitHub原数据，仓库外链正确 |
| 更新链 | 推送/Release/历史提交分开标注，按日期倒序，预发布有标识，点击可核对来源 |
| 筛选与分页 | 按项目和类型筛选；切换条件回第一页；项目卡片“查看更新”定位更新区 |
| 同步完整性 | 分页全部读取；跨分支提交按SHA去重；重复同步不重复push，失败不覆盖原快照 |
| 状态 | 缺失/错误快照有错误提示和重试，空项目/空筛选有独立提示，旧请求不覆盖新结果 |
| 手机 | 320/390/768/1440px布局正常；长仓库名、长提交标题可换行，无整页横向溢出 |
| 回归 | 4A主题、4B归档/日记和原阅读/后台测试仍通过；后端离线不影响关于页 |

2026-10-20候选预检通过：

| 检查 | 结果 |
| --- | --- |
| GitHub数据 | 6个公开项目、19条推送、17个Release、116条历史提交，共152条；按各公开仓库读取推送活动，提交跨分支去重 |
| 指南专项 | 8项通过，覆盖组件编译、真实模板HTML转义/可选区块隐藏、筛选分页及北京时间、错误/重试/旧响应/卸载、头像回退、GitHub完整分页/去重/旧推送保留/私有仓库移除，以及CLI失败保留旧快照 |
| 候选既有回归 | 46项全部通过，未修改实际tests目录 |
| 候选构建 | 通过，79个模块；CSS `index-D9aLYBUu.css` 36.78KB，JS `index-C2z-_rbD.js` 327.85KB（gzip122.85KB） |
| 实际页面 | 六个项目及来源链接、项目卡片跳转/焦点、个人网站8条Release筛选、116条提交的第2/12页、空筛选、503重试、空快照通过 |
| 响应式 | 320/390/768/1440px无整页横向溢出，手机筛选正常；头像版面复核居中 |
| 头像与离线 | 默认字母f、坏图回退、有效本站图片在首页/关于页共享均通过；Java API返回503时关于页仍显示个人资料、项目与更新链 |

头像图片测试使用隔离候选中的临时本地路径，完成后恢复为本指南的空配置；最终构建及截图均为字母f。Release标题UTF-8原数据和页面均正常，没有改动远程Release。

本轮40个实际业务文件SHA256前后一致，未安装实际依赖、未连接或修改个人数据库、未重跑后端测试；4B实际后端24项的证据保留在4B验收记录。本轮只使用公开GitHub读取，不修改远程仓库。预检过程中临时静态服务器曾遇到404响应处理错误，已修正预检工具并恢复页面，未涉及业务代码。临时预检服务已停止，视口已复原；正常预检页已关闭，未保留的临时故障页由应用在会话结束时回收。结果和截图保存在Git忽略的`artifacts/v2-profile-preflight/`。

2026-10-21实际源码review、54项前端、24项隔离MySQL后端、两端构建及第四轮组合验收均通过；个人数据未变，临时环境已清理。本节和第四轮已完成，当前建议创建v0.9.0预发布，尚未执行发布；详见[V2-PROFILE-ACCEPTANCE.md](V2-PROFILE-ACCEPTANCE.md)和[发布草稿](RELEASE-v0.9.0.md)。
