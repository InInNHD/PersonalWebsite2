# 第二版第二轮 A：站点框架与真实首页

日期：2026-10-16。用户已确认第一轮视觉样稿，本轮进入业务实现。

**当前状态：2026-10-16实际业务源码已完成手敲并通过功能验收，留有SiteHeader末尾多余Markdown标记待清理。** 详见[实际验收记录](V2-HOME-FOUNDATION-ACCEPTANCE.md)。下一节为[第二轮B1](12-v2-article-presentation.md)。

## 1. 本节目标与完成效果

把已确认的炭黑、薄荷绿和原创插画设计接到现有接口。完成独立访客页头页脚、手机菜单、首页介绍、作者侧栏、图文文章卡片和分页组件。分类、标签、标题摘要搜索及过期响应保护继续生效。

第二轮分成两个可运行的小节。A 是本文的前端框架与首页；B 将补封面字段、置顶排序、更新时间、仅公开内容的作者统计，以及筛选网址与返回位置恢复。A 完成后仍需继续 B，才算第二轮整体完成。

本节无需修改 Java、MySQL 或安装 npm 依赖。文章仍来自真实 `/api/articles`，卡片先使用固定栏目配图，不显示样稿中的示例阅读时长和统计。首篇较大的卡片是版式强调，不代表文章置顶；后台仍使用现有登录与表单，仅换成独立外框。导航中的日记通过首页「浏览日记」进入，独立日记导航随网址状态一起完善。

与样稿的关系：本节实现首页和全站外框；详情正文、归档、资源、关于、后台编辑区的完整重构按计划后续完成。

## 2. 数据流与文件顺序

浏览器进入首页 → ArticleListView 请求文章、分类和标签 → 后端返回分页对象 → ArticleCard 展示每篇文章。切换栏目、标签或分类 → 统一调用 applyFilters → 回到第一页 → 使用相同筛选重新请求。搜索框只有点击「搜索」后才更新已提交关键词，翻页不会带上未提交的输入。请求编号继续阻止慢的旧请求覆盖新结果。

组件职责：App 决定访客/管理外框；SiteHeader 和 SiteFooter 管公共入口；ArticleListView 管请求与筛选；ArticleCard 只展示数据并发出选标签事件；ArticlePagination 只发出翻页事件。真正的草稿隔离仍由后端控制。

按下面顺序操作。所有路径均以 `E:\PersonalWebsite2` 为根目录。

| 顺序 | 文件或目录 | 动作 |
| --- | --- | --- |
| 1 | `frontend/src/assets/` | 复制4个已提供的SVG素材 |
| 2 | `frontend/src/siteProfile.js` | 整个文件替换 |
| 3 | `frontend/src/components/SiteHeader.vue` | 新建 |
| 4 | `frontend/src/components/SiteFooter.vue` | 新建 |
| 5 | `frontend/src/components/ArticleCard.vue` | 新建 |
| 6 | `frontend/src/components/ArticlePagination.vue` | 新建 |
| 7 | `frontend/src/v2.css` | 新建；原style.css保留 |
| 8 | `frontend/src/App.vue` | 整个文件替换 |
| 9 | `frontend/src/views/ArticleListView.vue` | 整个文件替换，包括原style scoped块 |
| 10 | `frontend/src/main.js` | 整个文件替换 |
| 11 | `frontend/index.html` | 只改title标签中的站名 |

每个新Vue文件均包含下方完整代码，已有文件标注“整个文件替换”时，不要将第二套 script/template 追加到原文件末尾。Markdown代码块外侧的三个反引号不要输入IDEA。

## 3. 放入原创素材

素材已经准备在 `E:\PersonalWebsite2\docs\design-v2\assets`。在 IDEA 新建 `frontend/src/assets` 目录（若已有则直接使用），把以下四个文件复制进去，保持文件名不变。这些是图片资源，业务代码仍按后续章节手敲。

| 源文件 | 目标文件 | 用途 |
| --- | --- | --- |
| `docs/design-v2/assets/hero.svg` | `frontend/src/assets/hero.svg` | 首页主视觉 |
| `docs/design-v2/assets/note.svg` | `frontend/src/assets/note.svg` | 笔记栏目配图 |
| `docs/design-v2/assets/thought.svg` | `frontend/src/assets/thought.svg` | 心得栏目配图 |
| `docs/design-v2/assets/diary.svg` | `frontend/src/assets/diary.svg` | 日记栏目配图 |

这四张图由第一轮原创SVG导出，不使用外部图床。Vite 的 import 会处理生产构建后的资源路径，组件里不要改成磁盘绝对路径。

## 4. 统一公开文案

文件：`E:\PersonalWebsite2\frontend\src\siteProfile.js`。

整个文件替换。新增首页标题、介绍和作者卡文案；原关于页段落、昵称及 GitHub 链接保留。

```javascript
// 这里只放愿意在网站上公开的文字；由三个页面共同引用。
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
    githubUrl: 'https://github.com/InInNHD/PersonalWebsite2',
}
```

## 5. 新建访客页头

文件：`E:\PersonalWebsite2\frontend\src\components\SiteHeader.vue`。

新建整个文件。menuOpen 控制手机菜单；watch 在路由变化时收起菜单。首页与文章入口共享路径，但 hash 不同，因此分别标记高亮。搜索入口是首页筛选区的锚点。

```vue
<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { siteProfile } from '../siteProfile.js'

const route = useRoute()
const menuOpen = ref(false)
// 切换页面后收起手机菜单；点击当前页入口也通过 click 收起。
watch(() => route.fullPath, () => { menuOpen.value = false })
</script>

<template>
  <header class="v2-header v2-wrap">
    <RouterLink class="v2-brand" to="/" aria-label="返回首页">
      <span class="v2-mark" aria-hidden="true">f</span>
      <span><strong>{{ siteProfile.siteName }}</strong><small>FIREFLY / FIELD NOTES</small></span>
    </RouterLink>
    <nav id="visitor-nav" class="v2-nav" :class="{ open: menuOpen }" aria-label="网站导航"
         @click="menuOpen = false" @keydown.esc="menuOpen = false">
      <RouterLink to="/" exact-active-class="v2-exact" :class="{ 'is-active': route.path === '/' && route.hash !== '#entries' }"
                  :aria-current="route.path === '/' && route.hash !== '#entries' ? 'page' : null">首页</RouterLink>
      <RouterLink :to="{ path: '/', hash: '#entries' }" exact-active-class="v2-exact"
                  :class="{ 'is-active': route.hash === '#entries' || route.path.startsWith('/articles/') }"
                  :aria-current="route.hash === '#entries' ? 'location' : null">文章</RouterLink>
      <RouterLink to="/archive">归档</RouterLink>
      <RouterLink to="/resources">资源</RouterLink>
      <RouterLink to="/about">关于</RouterLink>
    </nav>
    <div class="v2-header-tools">
      <RouterLink class="v2-icon-button" :to="{ path: '/', hash: '#article-search' }" aria-label="搜索文章">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/></svg>
      </RouterLink>
      <button class="v2-icon-button v2-menu" type="button" aria-controls="visitor-nav"
              :aria-expanded="menuOpen" :aria-label="menuOpen ? '收起导航菜单' : '展开导航菜单'"
              @click="menuOpen = !menuOpen" @keydown.esc="menuOpen = false">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="menuOpen ? 'm6 6 12 12M18 6 6 18' : 'M4 6h16M4 12h16M4 18h16'"/></svg>
      </button>
    </div>
  </header>
</template>
```

## 6. 新建访客页脚

文件：`E:\PersonalWebsite2\frontend\src\components\SiteFooter.vue`。

新建整个文件。页脚从同一份公开文案读取站名；外部链接使用新标签页并隔离 opener。管理入口放在页脚。

```vue
<script setup>
import { siteProfile } from '../siteProfile.js'
const year = new Date().getFullYear()
</script>

<template>
  <footer class="v2-footer v2-wrap">
    <div><strong>{{ siteProfile.siteName }} · {{ siteProfile.nickname }}</strong><p>{{ siteProfile.tagline }}</p></div>
    <nav aria-label="页脚导航">
      <span>© {{ year }}</span>
      <a v-if="siteProfile.githubUrl" :href="siteProfile.githubUrl" target="_blank" rel="noopener noreferrer">GitHub ↗</a>
      <RouterLink to="/admin">内容管理</RouterLink>
    </nav>
  </footer>
</template>
```

## 7. 新建文章卡片

文件：`E:\PersonalWebsite2\frontend\src\components\ArticleCard.vue`。

新建整个文件。props 是父组件传入的数据，computed 随文章变化选择栏目图；emit 将标签编号交回首页。卡片外层使用 article，避免链接内部嵌套链接或按钮。

```vue
<script setup>
import { computed } from 'vue'
import noteCover from '../assets/note.svg'
import thoughtCover from '../assets/thought.svg'
import diaryCover from '../assets/diary.svg'

const props = defineProps({
  article: { type: Object, required: true },
  // 仅用于首篇的版式强调，不表示后端已经支持置顶。
  featured: { type: Boolean, default: false },
})
const emit = defineEmits(['select-tag'])
const names = { NOTE: '笔记', THOUGHT: '心得', DIARY: '日记' }
const covers = { NOTE: noteCover, THOUGHT: thoughtCover, DIARY: diaryCover }
const cover = computed(() => covers[props.article.type] ?? noteCover)
const path = computed(() => `/articles/${props.article.id}`)
</script>

<template>
  <article class="v2-post" :class="{ featured }">
    <RouterLink class="v2-cover" :to="path" :aria-label="`阅读：${article.title}`">
      <!-- 固定栏目图是装饰；文章标题已由链接的无障碍名称提供。 -->
      <img :src="cover" alt="" width="480" height="300" :loading="featured ? 'eager' : 'lazy'">
    </RouterLink>
    <div class="v2-post-body">
      <div class="v2-meta"><span class="v2-badge">{{ names[article.type] ?? '文章' }}</span><span>{{ article.categoryName ?? '未分类' }}</span></div>
      <h3><RouterLink :to="path">{{ article.title }}</RouterLink></h3>
      <p class="v2-summary">{{ article.summary || '打开文章，继续阅读。' }}</p>
      <time class="v2-meta" :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) ?? '发布时间未记录' }}</time>
      <div class="v2-post-bottom">
        <div class="v2-tags" aria-label="文章标签">
          <!-- 卡片不是整块链接，因此标签按钮不会嵌套在文章链接里。 -->
          <button v-for="tag in article.tags ?? []" :key="tag.id" type="button"
                  @click="emit('select-tag', tag.id)">#{{ tag.name }}</button>
        </div>
        <RouterLink :to="path" class="v2-read-more" :aria-label="`阅读全文：${article.title}`">→</RouterLink>
      </div>
    </div>
  </article>
</template>
```

## 8. 新建分页组件

文件：`E:\PersonalWebsite2\frontend\src\components\ArticlePagination.vue`。

新建整个文件。父组件掌握实际页码和总页数；本组件只发出 change，不自行发请求。父组件仍会再次检查加载状态和页码边界。

```vue
<script setup>
defineProps({ page: Number, totalPages: Number, loading: Boolean })
const emit = defineEmits(['change'])
</script>

<template>
  <nav v-if="totalPages > 0" class="v2-pagination" aria-label="文章分页">
    <button type="button" :disabled="loading || page <= 1" @click="emit('change', page - 1)">上一页</button>
    <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
    <button type="button" :disabled="loading || page >= totalPages" @click="emit('change', page + 1)">下一页</button>
  </nav>
</template>
```

## 9. 新建第二版样式

文件：`E:\PersonalWebsite2\frontend\src\v2.css`。

新建整个文件。保留现有 style.css，继续服务旧页面的 Markdown 和表单。v2.css 使用独立类名，并覆盖颜色变量；main.js 必须先引入 style.css，再引入 v2.css。媒体查询负责重排布局，不能只缩小字体。

```css
/* 本节的新设计样式。旧页面仍复用 style.css 的 Markdown 与表单样式。 */
:root {
  --color-bg: #101619;
  --color-surface: #172024;
  --color-surface-raised: #1d292d;
  --color-border: #2a383c;
  --color-text: #edf1ed;
  --color-muted: #a2b0ae;
  --color-accent: #b7d8bd;
  --color-accent-soft: #192d24;
  --color-error: #f5aaa4;
  --color-input: #101619;
  --color-code: #10191c;
  --radius-card: 16px;
}
.v2-site { min-height: 100vh; display: flex; flex-direction: column; }
.v2-wrap { width: min(1200px, calc(100% - 80px)); margin-inline: auto; }
.v2-main { flex: 1; min-width: 0; padding-top: 40px; }
.v2-main.is-home { padding-top: 0; }
.v2-header { position: relative; min-height: 82px; display: flex; align-items: center; justify-content: space-between; gap: 20px; border-bottom: 1px solid var(--color-border); }
.v2-brand { display: flex; align-items: center; gap: 12px; color: var(--color-text); min-width: 0; }
.v2-brand:hover { text-decoration: none; }
.v2-brand strong { display: block; letter-spacing: 3px; font-size: 18px; }
.v2-brand small { display: block; font-size: 9px; letter-spacing: 2px; }
.v2-mark { width: 34px; height: 34px; flex: none; border: 1px solid #577367; border-radius: 10px; display: grid; place-items: center; color: var(--color-accent); font: 27px Georgia, serif; }
.v2-nav { display: flex; gap: 24px; align-items: center; }
.v2-nav a { color: var(--color-muted); font-size: 14px; padding: 12px 0; }
.v2-nav a.router-link-exact-active, .v2-nav a.is-active { color: var(--color-accent); text-decoration: underline; text-underline-offset: 10px; }
.v2-header-tools { display: flex; gap: 4px; }
.v2-icon-button { width: 44px; height: 44px; min-height: 44px; display: inline-flex; align-items: center; justify-content: center; padding: 10px; color: var(--color-text); border: 0; background: transparent; border-radius: 8px; }
.v2-icon-button:hover { background: var(--color-surface-raised); }
.v2-icon-button svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 1.5; }
.v2-menu { display: none; }
.v2-footer { margin-top: 64px; padding: 28px 0; border-top: 1px solid var(--color-border); display: flex; align-items: center; justify-content: space-between; gap: 24px; font-size: 12px; color: var(--color-muted); }
.v2-footer p { margin: 6px 0 0; }
.v2-footer nav { display: flex; flex-wrap: wrap; align-items: center; gap: 20px; }
.v2-footer a { display: inline-flex; align-items: center; min-height: 44px; }
.v2-admin-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 24px; border-bottom: 1px solid var(--color-border); }
.v2-admin-header a { min-height: 44px; display: inline-flex; align-items: center; }
.v2-hero { display: grid; grid-template-columns: 1.12fr 1fr; gap: 36px; align-items: center; min-height: 432px; padding: 46px 0 42px; }
.v2-eyebrow { font: 11px Consolas, monospace; letter-spacing: 2px; color: var(--color-accent); }
.v2-hero h1 { font-size: clamp(34px, 4vw, 53px); margin: 19px 0 20px; line-height: 1.38; font-weight: 600; }
.v2-hero h1 span { color: var(--color-accent); }
.v2-hero-copy { color: var(--color-muted); line-height: 1.95; font-size: 14px; max-width: 440px; }
.v2-hero-actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 28px; }
.v2-action { display: inline-flex; align-items: center; justify-content: center; min-height: 44px; padding: 10px 17px; border: 1px solid var(--color-border); border-radius: 9px; font-size: 13px; font-weight: 600; color: var(--color-text); }
.v2-action.primary { background: var(--color-accent); color: #17241c; border-color: var(--color-accent); }
.v2-action:hover { text-decoration: none; border-color: var(--color-accent); }
.v2-hero-sign { margin-top: 25px; font: 10px Consolas, monospace; letter-spacing: 1px; color: var(--color-muted); }
.v2-hero-art img { width: 100%; height: auto; display: block; }
.v2-hero-art figcaption { text-align: right; font: 9px Consolas, monospace; letter-spacing: 1.5px; color: var(--color-muted); }
.v2-hero-art { margin: 0; }
.v2-content { display: grid; grid-template-columns: minmax(0, 1fr) 264px; gap: 32px; padding-top: 30px; border-top: 1px solid var(--color-border); }
.v2-entry-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.v2-entry-heading h2 { margin: 0; font-size: 25px; }
.v2-entry-heading h2 span { color: var(--color-accent); }
.v2-entry-heading small { font-size: 12px; }
.v2-content-tools { display: flex; align-items: center; gap: 4px; margin: 20px 0; flex-wrap: wrap; }
.v2-type { font-size: 13px; background: transparent; border: 1px solid transparent; font-weight: 400; padding: 8px 13px; }
.v2-type[aria-pressed="true"] { background: var(--color-accent-soft); color: var(--color-accent); }
.v2-filter-toggle { margin-left: auto; font-size: 12px; background: transparent; }
.v2-filter-panel { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 20px; padding: 16px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); }
.v2-filter-panel label { font-size: 12px; color: var(--color-muted); }
.v2-search { grid-column: 1 / -1; display: flex; flex-wrap: wrap; align-items: end; gap: 10px; }
.v2-search label { flex: 1 1 220px; }
.v2-search button { font-size: 12px; }
.v2-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px; }
.v2-post { min-width: 0; display: flex; flex-direction: column; border: 1px solid var(--color-border); border-radius: 16px; overflow: hidden; background: var(--color-surface); transition: border-color .15s, transform .15s; }
.v2-post:hover { border-color: #587366; transform: translateY(-2px); }
.v2-cover { display: block; aspect-ratio: 16 / 9; background: var(--color-surface-raised); overflow: hidden; }
.v2-cover img { width: 100%; height: 100%; display: block; object-fit: cover; }
.v2-post-body { padding: 20px; display: flex; flex-direction: column; gap: 12px; flex: 1; min-width: 0; }
.v2-post h3 { margin: 0; font-size: 19px; font-weight: 550; line-height: 1.55; }
.v2-post h3 a { color: var(--color-text); }
.v2-summary { margin: 0; font-size: 13px; line-height: 1.85; color: var(--color-muted); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.v2-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; color: var(--color-muted); font-size: 11px; }
.v2-badge { background: var(--color-accent-soft); color: var(--color-accent); border-radius: 5px; padding: 3px 7px; }
.v2-post-bottom { display: flex; gap: 10px; justify-content: space-between; align-items: center; margin-top: auto; padding-top: 8px; border-top: 1px solid var(--color-border); }
.v2-tags { display: flex; flex-wrap: wrap; min-width: 0; }
.v2-tags button { font-size: 12px; padding: 4px 8px 4px 0; border: 0; background: transparent; color: var(--color-muted); font-weight: 400; }
.v2-read-more { flex: none; display: inline-grid; place-items: center; width: 44px; height: 44px; }
.v2-post.featured { grid-column: 1 / -1; display: grid; grid-template-columns: 43% minmax(0, 1fr); }
.v2-post.featured .v2-cover { height: 100%; min-height: 250px; aspect-ratio: auto; }
.v2-post.featured h3 { font-size: 23px; }
.v2-pagination { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 12px; margin-top: 28px; font-size: 13px; }
.v2-pagination button { font-size: 12px; }
.v2-state { border: 1px dashed var(--color-border); border-radius: 12px; padding: 32px 24px; text-align: center; color: var(--color-muted); }
.v2-state button { margin: 8px; }
.v2-profile { border: 1px solid var(--color-border); border-radius: 16px; background: var(--color-surface); padding: 24px 22px; }
.v2-avatar { display: grid; place-items: center; width: 62px; height: 62px; border: 1px solid #4a6758; border-radius: 50%; background: #263e34; color: var(--color-accent); font: italic 43px Georgia, serif; }
.v2-profile h2 { font-size: 20px; margin: 16px 0 8px; }
.v2-profile p { font-size: 13px; line-height: 1.9; color: var(--color-muted); }
.v2-profile-links { display: flex; gap: 14px; flex-wrap: wrap; font-size: 12px; }
.v2-profile-links a { display: inline-flex; align-items: center; min-height: 44px; }
.v2-side-note { margin: 24px 0; padding: 24px 0; border-bottom: 1px solid var(--color-border); font-size: 14px; color: var(--color-muted); line-height: 2; }
.v2-side-links { display: grid; gap: 8px; padding: 16px 0; }
.v2-side-links h3 { font-size: 13px; color: var(--color-muted); }
.v2-side-links a { min-height: 44px; display: flex; align-items: center; justify-content: space-between; font-size: 13px; }
#entries, #article-search { scroll-margin-top: 24px; }
@media (max-width: 1100px) {
  .v2-wrap { width: calc(100% - 56px); }
  .v2-nav { gap: 16px; }
  .v2-content { grid-template-columns: minmax(0, 1fr) 235px; gap: 24px; }
  .v2-post.featured { grid-template-columns: 1fr; }
  .v2-post.featured .v2-cover { min-height: 0; height: auto; aspect-ratio: 2.6; }
}
@media (max-width: 850px) {
  .v2-content { grid-template-columns: minmax(0, 1fr); }
  .v2-sidebar { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; align-items: start; }
  .v2-profile { grid-row: span 2; }
  .v2-hero { gap: 14px; }
}
@media (max-width: 700px) {
  .v2-wrap { width: calc(100% - 36px); }
  .v2-header { min-height: 70px; gap: 8px; }
  .v2-brand strong { font-size: 17px; }
  .v2-brand small { font-size: 8px; }
  .v2-menu { display: inline-flex; }
  .v2-nav { display: none; position: absolute; top: 100%; left: 0; right: 0; z-index: 10; padding: 14px 18px; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: 0 0 12px 12px; }
  .v2-nav.open { display: flex; flex-wrap: wrap; gap: 8px 22px; }
  .v2-hero { display: block; position: relative; overflow: hidden; min-height: 0; padding: 35px 0 30px; }
  .v2-hero-text { position: relative; z-index: 1; }
  .v2-hero h1 { font-size: 34px; }
  .v2-hero-copy { font-size: 13px; max-width: 280px; }
  .v2-eyebrow { font-size: 9px; }
  .v2-hero-sign { font-size: 9px; }
  .v2-hero-art { position: absolute; width: 260px; right: -130px; top: 7px; opacity: .16; pointer-events: none; }
  .v2-hero-art figcaption { display: none; }
  .v2-grid { grid-template-columns: minmax(0, 1fr); gap: 19px; }
  .v2-post.featured { display: flex; }
  .v2-post .v2-cover, .v2-post.featured .v2-cover { aspect-ratio: 1.95; min-height: 0; height: auto; }
  .v2-post h3, .v2-post.featured h3 { font-size: 20px; }
  .v2-entry-heading h2 { font-size: 22px; }
  .v2-content-tools { gap: 0; }
  .v2-type { padding: 8px 10px; }
  .v2-filter-toggle { padding: 8px; }
  .v2-filter-panel { grid-template-columns: minmax(0, 1fr); }
  .v2-filter-panel input, .v2-filter-panel select { font-size: 16px; }
  .v2-sidebar { display: block; }
  .v2-footer { display: block; text-align: center; margin-top: 40px; }
  .v2-footer nav { justify-content: center; margin-top: 16px; }
}
@media (prefers-reduced-motion: reduce) {
  .v2-post { transition: none; }
}
```

## 10. 替换应用外框

文件：`E:\PersonalWebsite2\frontend\src\App.vue`。

整个文件替换。管理路由使用简洁管理外框，访客路由使用新的页头页脚。RouterView 仍承载原页面，不缓存管理员登录信息。

```vue
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteHeader from './components/SiteHeader.vue'
import SiteFooter from './components/SiteFooter.vue'

const route = useRoute()
// 访客与管理员使用不同外框；管理页面的登录和表单逻辑保持原组件负责。
const isAdmin = computed(() => route.path === '/admin' || route.path.startsWith('/admin/'))
</script>

<template>
  <div class="v2-site">
    <header v-if="isAdmin" class="v2-admin-header">
      <strong>内容管理</strong><RouterLink to="/">← 返回网站</RouterLink>
    </header>
    <SiteHeader v-else />
    <main :class="[isAdmin ? 'container' : 'v2-main v2-wrap', { 'is-home': route.path === '/' }]">
      <RouterView />
    </main>
    <SiteFooter v-if="!isAdmin" />
  </div>
</template>
```

## 11. 替换首页

文件：`E:\PersonalWebsite2\frontend\src\views\ArticleListView.vue`。

整个文件替换，包含旧 style scoped 块。请求逻辑沿用现有实现，新增加栏目/分类/标签入口和重置操作。showFilters 处理页头搜索锚点；收起搜索面板时移除锚点，下次仍可通过页头搜索展开。

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { siteProfile } from '../siteProfile.js'
import { useRoute, useRouter } from 'vue-router'
import ArticleCard from '../components/ArticleCard.vue'
import ArticlePagination from '../components/ArticlePagination.vue'
import heroArt from '../assets/hero.svg'

const route = useRoute()
const router = useRouter()
const filtersExpanded = ref(false)
// 从页头搜索入口进入时，自动打开筛选区，保证锚点目标可见。
const showFilters = computed(() => filtersExpanded.value || route.hash === '#article-search')


const articles = ref([])
const type = ref('')
const loading = ref(false)
const error = ref('')
const articleCategories = ref([])
const categoryId = ref('')
const categoryError = ref('')
const tags = ref([])
const tagId = ref('') // 空字符串表示全部标签；选择标签后是数字编号。
const tagError = ref('')
const keywordInput = ref('') // 输入框中的文字，尚未提交时不影响翻页。
const keyword = ref('') // 最近一次提交的关键词。
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)

// 分类只在页面进入时加载一次。
// 离开页面后，不再接受这次加载结果。
let disposed = false

// 只用于判断请求先后，不参与页面渲染，因此不需要 ref。
let latestRequestId = 0

// 离开页面时，让尚未结束的请求失效。
onBeforeUnmount(() => {
  latestRequestId += 1
  disposed = true
})

// 栏目值必须与后端 ArticleController 接受的值相同。
const typeOptions = [
  { value: '', label: '全部' },
  { value: 'NOTE', label: '笔记' },
  { value: 'THOUGHT', label: '心得' },
  { value: 'DIARY', label: '日记' },
]

async function loadArticles() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''

  try {
    const params = new URLSearchParams()
    if (type.value) params.set('type', type.value)
    if (categoryId.value !== '') params.set('categoryId', String(categoryId.value))
    if (tagId.value !== '') params.set('tagId', String(tagId.value))
    if (keyword.value) params.set('keyword', keyword.value)
    params.set('page', String(page.value))
    params.set('size', String(pageSize))

    const response = await fetch(`/api/articles?${params.toString()}`)
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    // 列表与总数、页码都必须通过同一个请求编号检查。
    if (requestId !== latestRequestId) return

    articles.value = data.items
    total.value = data.total
    page.value = data.page
    totalPages.value = data.totalPages
  } catch (cause) {
    if (requestId !== latestRequestId) return
    articles.value = []
    total.value = 0
    totalPages.value = 0
    error.value = cause instanceof Error ? cause.message : '加载文章列表失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

// 搜索提交与筛选条件变化都从第一页开始。
function applyFilters() {
  page.value = 1
  return loadArticles()
}

function searchArticles() {
  keyword.value = keywordInput.value.trim()
  return applyFilters()
}

function clearSearch() {
  keywordInput.value = ''
  keyword.value = ''
  return applyFilters()
}

function changePage(nextPage) {
  // 加载期间和边界外不发请求，避免重复点击翻页。
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  return loadArticles()
}

async function loadCategories() {
  try {
    const response = await fetch('/api/categories')

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const data = await response.json()

    if (disposed) return

    articleCategories.value = data
  } catch {
    if (disposed) return

    // 分类加载失败不会阻止文章列表显示。
    categoryError.value = '分类加载失败，请刷新页面重试。'
  }
}

async function loadTags() {
  try {
    const response = await fetch('/api/tags')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const data = await response.json()

    // 页面已卸载时，丢弃尚未完成的标签加载结果。
    if (disposed) return
    tags.value = data
  } catch {
    if (disposed) return
    tagError.value = '标签加载失败，请刷新页面重试。'
  }
}

onMounted(loadCategories)

onMounted(loadTags)

onMounted(loadArticles)

// 所有入口继续调用原有 applyFilters，统一回到第一页并防止旧响应覆盖。
function toggleFilters() {
  if (route.hash === '#article-search') {
    // 移除搜索锚点后才允许收起；下次点击页头搜索仍能重新展开。
    filtersExpanded.value = false
    return router.replace({ path: route.path, query: route.query, hash: '' })
  }
  filtersExpanded.value = !filtersExpanded.value
}

function selectType(value) {
  type.value = value
  return applyFilters()
}

function selectTag(id) {
  tagId.value = id
  filtersExpanded.value = true
  return applyFilters()
}

function selectCategory(id) {
  categoryId.value = id
  filtersExpanded.value = true
  return applyFilters()
}

function clearAll() {
  type.value = ''
  categoryId.value = ''
  tagId.value = ''
  keywordInput.value = ''
  keyword.value = ''
  return applyFilters()
}
</script>

<template>
  <div class="v2-home">
    <section class="v2-hero" aria-labelledby="home-title">
      <div class="v2-hero-text">
        <p class="v2-eyebrow">A PERSONAL NOTEBOOK, ALWAYS GROWING</p>
        <h1 id="home-title">{{ siteProfile.heroTitle }}<br><span>{{ siteProfile.heroAccent }}</span></h1>
        <p class="v2-hero-copy">你好，我是 {{ siteProfile.nickname }}。<br>{{ siteProfile.heroDescription }}</p>
        <div class="v2-hero-actions">
          <a class="v2-action primary" href="#entries" @click="selectType('NOTE')">阅读笔记 →</a>
          <a class="v2-action" href="#entries" @click="selectType('DIARY')">浏览日记</a>
        </div>
        <p class="v2-hero-sign">LEARN SOMETHING. KEEP A LITTLE LIGHT.</p>
      </div>
      <figure class="v2-hero-art">
        <img :src="heroArt" alt="打开的笔记终端与萤火轨迹" width="530" height="400">
        <figcaption>FIREFLY LAB / SKETCH 001</figcaption>
      </figure>
    </section>

    <div class="v2-content">
      <section id="entries" aria-labelledby="entries-title" :aria-busy="loading">
        <header class="v2-entry-heading">
          <h2 id="entries-title">最近的记录<span>.</span></h2>
          <small v-if="!loading && !error" role="status">共 {{ total }} 篇文章</small>
        </header>
        <div class="v2-content-tools" role="group" aria-label="文章栏目">
          <button v-for="option in typeOptions" :key="option.value" type="button" class="v2-type"
                  :aria-pressed="type === option.value" @click="selectType(option.value)">{{ option.label }}</button>
          <button type="button" class="v2-filter-toggle" :aria-expanded="showFilters" aria-controls="article-search"
                  @click="toggleFilters">筛选</button>
        </div>

        <!-- v-show 保留输入内容。筛选面板收起时，已选条件仍然生效。 -->
        <div v-show="showFilters" id="article-search" class="v2-filter-panel">
          <label>栏目
            <select v-model="type" @change="applyFilters">
              <option v-for="option in typeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <label>分类
            <select v-model="categoryId" @change="applyFilters">
              <option value="">全部分类</option>
              <option v-for="category in articleCategories" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
          </label>
          <label>标签
            <select v-model="tagId" @change="applyFilters">
              <option value="">全部标签</option>
              <option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
            </select>
          </label>
          <form class="v2-search" @submit.prevent="searchArticles">
            <label>搜索标题或摘要
              <input v-model="keywordInput" type="search" maxlength="100" placeholder="例如：Java、生活记录">
            </label>
            <button type="submit">搜索</button>
            <button type="button" @click="clearSearch">清空搜索</button>
            <button type="button" @click="clearAll">重置筛选</button>
          </form>
        </div>
        <p v-if="categoryId !== '' || tagId !== '' || keyword" class="v2-meta">
          正在使用筛选条件：{{ keyword ? `关键词「${keyword}」` : '' }}
          {{ categoryId !== '' ? '已选分类' : '' }} {{ tagId !== '' ? '已选标签' : '' }}
          <button type="button" class="v2-type" @click="filtersExpanded = true">查看条件</button>
        </p>
        <p v-if="categoryError" class="error" role="alert">{{ categoryError }}</p>
        <p v-if="tagError" class="error" role="alert">{{ tagError }}</p>

        <div v-if="loading" class="v2-state" role="status">正在整理内容，请稍候…</div>
        <div v-else-if="error" class="v2-state" role="alert">
          <h3>内容暂时没能加载</h3><p>{{ error }}</p>
          <button type="button" @click="loadArticles">重新加载文章</button>
        </div>
        <div v-else-if="articles.length === 0" class="v2-state">
          <h3>暂时没有找到记录</h3><p>当前搜索或筛选条件下没有已发布的文章。</p>
          <button type="button" @click="clearAll">清空全部条件</button>
        </div>
        <div v-else class="v2-grid">
          <ArticleCard v-for="(article, index) in articles" :key="article.id" :article="article"
                       :featured="page === 1 && index === 0" @select-tag="selectTag" />
        </div>
        <ArticlePagination v-if="!error" :page="page" :total-pages="totalPages" :loading="loading" @change="changePage" />
      </section>

      <aside class="v2-sidebar" aria-label="作者与内容导航">
        <section class="v2-profile">
          <span class="v2-avatar" aria-hidden="true">f</span>
          <h2>{{ siteProfile.nickname }}</h2>
          <span class="v2-eyebrow">LEARNING IN PUBLIC</span>
          <p>{{ siteProfile.profileDescription }}</p>
          <div class="v2-profile-links">
            <a v-if="siteProfile.githubUrl" :href="siteProfile.githubUrl" target="_blank" rel="noopener noreferrer">GitHub ↗</a>
            <RouterLink to="/about">关于我 →</RouterLink>
          </div>
        </section>
        <section class="v2-side-links">
          <h3>在这里探索</h3>
          <a v-for="category in articleCategories" :key="category.id" href="#entries"
             @click="selectCategory(category.id)">{{ category.name }} <span aria-hidden="true">→</span></a>
          <RouterLink to="/archive">浏览归档 <span aria-hidden="true">→</span></RouterLink>
          <RouterLink to="/resources">资源收藏 <span aria-hidden="true">→</span></RouterLink>
        </section>
        <p class="v2-side-note">不必一次照亮整条路，<br>记住今天走过的这一段。</p>
      </aside>
    </div>
  </div>
</template>
```

## 12. 接入全局样式与路由滚动

文件：`E:\PersonalWebsite2\frontend\src\main.js`。

整个文件替换。本项目路由本来就在 main.js，不需要另建 router/index.js。保留全部现有路由，新增样式引入与锚点滚动。完整的筛选网址和异步列表返回位置恢复会在B节实现。

```javascript
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ArticleListView from './views/ArticleListView.vue'
import ArticleDetailView from './views/ArticleDetailView.vue'
import './style.css'
// 新配色和布局覆盖基础样式，顺序不能反过来。
import './v2.css'
import AdminView from './views/AdminView.vue'
import ResourceView from './views/ResourceView.vue'
import ArchiveView from './views/ArchiveView.vue'
import AboutView from './views/AboutView.vue'

// 首页显示文章列表；点击文章后通过 id 打开详情。
const router = createRouter({
    history: createWebHistory(),
    // 普通页面切换回到顶部，首页搜索入口定位到筛选面板。
    // 列表筛选URL与异步返回位置恢复将在下一小节一起完善。
    scrollBehavior(to, from, savedPosition) {
        if (to.hash) return { el: to.hash, top: 24 }
        return savedPosition ?? { top: 0 }
    },
    routes: [
        { path: '/', component: ArticleListView },
        { path: '/articles/:id', component: ArticleDetailView },
        { path: '/archive', component: ArchiveView },
        { path: '/resources', component: ResourceView },
        { path: '/about', component: AboutView },
        { path: '/admin', component: AdminView },

    ],
})

createApp(App).use(router).mount('#app')
```

## 13. 更新浏览器标题

文件：`E:\PersonalWebsite2\frontend\index.html`。

找到 `<title>我的个人网站</title>`，替换为：

```html
<title>萤火手记</title>
```

这里只修改title这一行，其余内容保留。

## 14. 运行与自查

1. 在 IDEA 启动现有 Spring Boot 后端，继续使用当前的 personal_website 数据库。
2. 在前端终端进入 `E:\PersonalWebsite2\frontend`，执行 `npm run dev`。如果已有 Vite 正在运行，保存文件后等待热更新即可。
3. 打开终端实际显示的地址。5177是设计样稿，不能用它验收手敲结果；业务开发服务器可能是5173或其他空闲端口。
4. 观察首页首屏、图文文章流和作者侧栏；文章标题、摘要、分类和标签应来自自己的数据库。栏目配图重复是本节设计，单篇自定义封面将在B节接入。
5. 点击栏目、筛选框和文章标签；搜索不存在的关键词应出现空状态，点击清空后恢复。内容超过10篇时可检查分页，内容不足时没有第二页是正常行为。
6. 缩窄窗口，检查手机菜单、单列卡片和折叠筛选。通过关于页的页头搜索回到首页，筛选框应自动展开。
7. 进入内容管理，确认显示管理外框和原登录表单；已有写作功能仍由原AdminView负责。

可以分三次手敲：先完成素材、文案和四个组件；再完成样式与App；最后替换首页和main并修改网页标题。依赖未写齐时出现临时“找不到文件”是正常的，完成本节后再统一运行检查。

若导入SVG失败，先确认扩展名为 `.svg`，不是 `.svg.txt`；若页面没有新样式，检查main.js的两条样式引入及顺序；若文章加载失败，核对后端是否启动和Vite的 `/api` 代理，不要把模拟接口复制进正式项目。

## 15. 助手预检结果与后续验收

指南代码在 `artifacts/v2-home-preflight/frontend` 的隔离副本中构建，没有写进实际 frontend/src。现有9项回归测试和2项新增测试均通过；生产构建通过。新增测试覆盖栏目/标签/分类入口的组合条件，以及搜索锚点的展开、收起和再次进入。实际组件原回归测试的模拟路由工具需要适配新增 useRouter，用户完成手敲后由助手同步测试代码。

浏览器使用构建产物及固定模拟接口，已验证首页搜索、空结果清空、13篇数据的10/3分页、跨页搜索、手机导航切页后收起、管理布局分离。首页含长标题的320/390/768/1440px检查无横向溢出。此结果是指南预检，真实MySQL、个人数据和用户手敲后的代码仍待下一次验收。

本节没有数据库迁移，因此本轮没有执行建表或升级SQL。预检图片及记录位于Git忽略的 `artifacts/v2-home-preflight/`。

另已提供可重复运行的指南检查：在项目根目录执行 `node --test docs/checks/v2-home-guide.test.mjs`。它直接读取本文中的Vue代码，检查组件编译、素材、组合筛选、搜索面板开合和旧响应隔离，4项已通过；它不会把指南代码写入你的业务目录，也不代表手敲后的源码已经验收。

用户只需完成业务代码手敲并告知完成；助手负责实际diff review、测试工具适配、真实接口和浏览器验收。完成A节后继续B节补齐数据字段及导航状态；第二轮整体验收后再判断是否达到GitHub Release节点。
