# 第九轮：暗色技术风格与移动端布局

本轮目标：按你选择的暗色技术风格，统一前台、管理端、表单和 Markdown 阅读区，并处理手机与平板上的布局。

**本轮已于 2026-10-15 完成实际源码 review、测试和浏览器验收。以下保留手敲指南，已完成的步骤无需重复。详细记录见 STYLE-MOBILE-ACCEPTANCE.md；按你的安排继续本地开发，暂不发布。**

## 1. 页面效果与修改顺序

页面采用深蓝灰背景、稍亮的卡片、浅色正文和蓝色链接。代码区使用等宽字体和更深的背景；长代码和宽表格在各自区域内横向滚动。手机上的导航自然换行，筛选和写作区域改为单列。

| 顺序 | 文件 | 修改范围 |
| --- | --- | --- |
| 1 | `frontend/src/style.css` | 替换整个文件：颜色变量、通用控件、导航、卡片、Markdown、响应式规则 |
| 2 | `frontend/src/views/ArticleListView.vue` | 替换文件末尾的 `<style scoped>` 块 |
| 3 | `frontend/src/views/AboutView.vue` | 替换文件末尾的 `<style scoped>` 块 |
| 4 | `frontend/src/views/ArchiveView.vue` | 替换文件末尾的 `<style scoped>` 块 |
| 5 | `frontend/src/views/ResourceView.vue` | 替换文件末尾的 `<style scoped>` 块 |
| 6 | `frontend/src/views/AdminView.vue` | 替换文件末尾的 `<style scoped>` 块 |

颜色由 `style.css` 定义，再由各页面引用。组件内的 `scoped` 样式优先级通常高于全局样式，因此需要把旧的白色背景和旧颜色一起替换。

**第二步到第六步：从原有的 `<style scoped>` 开始，直到对应的 `</style>` 结束，整块替换。上面的 `<script setup>` 和 `<template>` 保留。** 不要再追加第二个旧样式块。代码块外面的三个反引号不要写入文件。

## 2. 替换全局样式

文件：`E:\PersonalWebsite2\frontend\src\style.css`。

将该文件全部内容替换为下面的 CSS。

```css
:root {
  /* 告诉浏览器：原生下拉框、输入控件和滚动条使用暗色外观。 */
  color-scheme: dark;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI",
    "Microsoft YaHei", sans-serif;
  font-size: 16px;
  line-height: 1.6;

  /* CSS 变量可以跨组件使用；以后调整配色只需改这里。 */
  --color-bg: #0b1120;
  --color-surface: #131e30;
  --color-surface-raised: #1b2a40;
  --color-border: #334155;
  --color-text: #e5edf6;
  --color-muted: #aab8cc;
  --color-accent: #7dd3fc;
  --color-accent-soft: #132b3d;
  --color-error: #fda4af;
  --color-input: #0b1424;
  --color-code: #080e1a;
  --radius-card: 12px;

  color: var(--color-text);
  background: var(--color-bg);
}

/* padding 和 border 都计入元素宽度，避免 100% 的输入框撑出容器。 */
* { box-sizing: border-box; }
body { margin: 0; }
h1, h2, h3 { line-height: 1.35; overflow-wrap: anywhere; }
p { overflow-wrap: anywhere; }
small { color: var(--color-muted); }

a { color: var(--color-accent); text-decoration: none; }
a:hover { text-decoration: underline; }

/* Tab 键移动到控件时有明确边框；保留浏览器的键盘操作方式。 */
a:focus-visible,
button:focus-visible,
input:focus-visible,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

button, input, select, textarea { font: inherit; color: inherit; }
label { min-width: 0; }

/* 复选框另设尺寸，避免跟着文本框变成整行宽度。 */
input:not([type="checkbox"]), select, textarea {
  display: block;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  min-height: 44px;
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-input);
}

input::placeholder, textarea::placeholder { color: var(--color-muted); }
textarea { resize: vertical; }
input[type="checkbox"] {
  width: 18px;
  height: 18px;
  margin: 0;
  accent-color: var(--color-accent);
  flex-shrink: 0;
}

button {
  min-height: 44px;
  max-width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-raised);
  font-weight: 600;
  cursor: pointer;
  overflow-wrap: anywhere;
}

button:hover:not(:disabled) { border-color: var(--color-accent); }
button[type="submit"], .login button {
  color: var(--color-accent);
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}
button:disabled { cursor: not-allowed; opacity: 0.55; }
input:disabled, select:disabled, textarea:disabled { opacity: 0.65; }

.site { min-height: 100vh; display: flex; flex-direction: column; }
.site-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px max(24px, calc((100% - 1040px) / 2));
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}
.brand { min-width: 0; color: var(--color-text); font-size: 20px; font-weight: 700; }
.site-header nav { display: flex; flex-wrap: wrap; gap: 4px; min-width: 0; }
.site-header nav a {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 8px 12px;
  border-radius: 8px;
  color: var(--color-muted);
  text-decoration: none;
}
.site-header nav a:hover { background: var(--color-surface-raised); color: var(--color-text); }

/* Vue Router 自带的精确匹配类，直接用来标记当前导航。 */
.site-header nav a.router-link-exact-active {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

.container {
  width: min(1040px, calc(100% - 48px));
  min-width: 0;
  margin: 48px auto;
  flex: 1;
}
.page-heading {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: end;
  gap: 24px;
  margin-bottom: 28px;
}
.page-heading > * { min-width: 0; }
.page-heading h1, .article-header h1 { margin: 0 0 12px; font-size: clamp(1.7rem, 4vw, 2.25rem); }
.page-heading p, .article-header p, .article-card p { color: var(--color-muted); line-height: 1.8; }
.page-heading p { margin-bottom: 0; }

.article-grid { display: grid; gap: 16px; }
.article-card {
  display: block;
  min-width: 0;
  padding: clamp(20px, 3vw, 28px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
  color: var(--color-text);
  overflow-wrap: anywhere;
}
a.article-card:hover { border-color: var(--color-accent); text-decoration: none; }
.article-card h2 { margin: 10px 0; font-size: 1.3rem; }
.article-card > span, .back-link { color: var(--color-accent); }
.back-link { display: inline-flex; align-items: center; min-height: 44px; margin-bottom: 20px; }
.error { color: var(--color-error); }

.article-header { padding-bottom: 24px; border-bottom: 1px solid var(--color-border); }
.article-tags { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
.tag-badge {
  display: inline-block;
  max-width: 100%;
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-size: 13px;
  overflow-wrap: anywhere;
}

/* 同时服务文章详情和后台预览；v-html 生成的标签使用全局样式。 */
.markdown-body {
  max-width: 78ch;
  line-height: 1.85;
  overflow-wrap: anywhere;
}
.markdown-body h2, .markdown-body h3 { margin-top: 1.7em; }
.markdown-body a { text-decoration: underline; text-underline-offset: 4px; }
.markdown-body ul, .markdown-body ol { padding-left: 24px; }
.markdown-body code {
  font-family: "Cascadia Code", Consolas, "Courier New", monospace;
  font-size: 0.92em;
}
.markdown-body :not(pre) > code {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--color-surface-raised);
  color: var(--color-accent);
}
.markdown-body pre {
  max-width: 100%;
  margin: 24px 0;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-code);
  line-height: 1.65;
  overflow-x: auto;
  /* 保留代码缩进和长行；横向滚动只发生在代码框内。 */
  overflow-wrap: normal;
}
.markdown-body pre code { padding: 0; background: transparent; color: var(--color-text); }
.markdown-body blockquote {
  margin: 20px 0;
  padding: 12px 16px;
  border-left: 3px solid var(--color-accent);
  background: var(--color-surface);
  color: var(--color-muted);
}
.markdown-body blockquote > :last-child { margin-bottom: 0; }
.markdown-body img { display: block; max-width: 100%; height: auto; border-radius: 8px; }

/* 多列表格保留可读列宽；滚动表格，不把整张页面撑宽。 */
.markdown-body table {
  display: block;
  width: 100%;
  max-width: 100%;
  margin: 24px 0;
  border-collapse: collapse;
  overflow-x: auto;
}
.markdown-body th, .markdown-body td {
  min-width: 120px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  text-align: left;
  vertical-align: top;
}
.markdown-body th { background: var(--color-surface-raised); }
.markdown-body hr { border: 0; border-top: 1px solid var(--color-border); margin: 28px 0; }

.site-footer { padding: 24px 16px; border-top: 1px solid var(--color-border); color: var(--color-muted); text-align: center; }

@media (max-width: 600px) {
  .container { width: calc(100% - 32px); margin: 28px auto; }
  .page-heading { align-items: stretch; flex-direction: column; gap: 16px; }
  .site-header { padding: 16px; }
  .site-header nav { width: 100%; }
  .site-header nav a { padding: 8px 10px; }
}
```

### 这几个规则的作用

- `color-scheme: dark` 负责浏览器原生控件的外观；颜色变量负责网页自己绘制的区域。
- `min-width: 0` 允许 flex/grid 子元素在窄屏缩小。输入框和下拉框再通过 `width: 100%` 跟随父容器。
- `overflow-wrap: anywhere` 用于标题、标签和普通文字。代码框保留长行和缩进，使用自己的滚动条。
- `78ch` 限制正文行长；它是字符宽度单位，实际宽度会随字体变化。
- `:focus-visible` 让键盘用户看清焦点。按钮和文本控件的高度至少 44px，复选框通过外面的标签扩大点击区域。

## 3. 替换文章列表页的样式块

文件：`E:\PersonalWebsite2\frontend\src\views\ArticleListView.vue`。

找到文件末尾 `<style scoped>`，整块替换为：

```vue
<style scoped>
/* 三个筛选框等宽；过长的选项也受控件宽度限制。 */
.article-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  flex: 1 1 400px;
  max-width: 520px;
  gap: 12px;
}
.article-filters label { min-width: 0; }
.article-search, .article-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
  margin: 24px 0;
}
.article-search label { flex: 1 1 240px; min-width: 0; }
.article-pagination { align-items: center; padding-top: 20px; border-top: 1px solid var(--color-border); }
.home-intro {
  padding: clamp(20px, 3vw, 36px);
  margin-bottom: 32px;
  border: 1px solid var(--color-border);
  border-top: 3px solid var(--color-accent);
  border-radius: var(--radius-card);
  background: var(--color-surface);
  overflow-wrap: anywhere;
}
.home-intro h1 { margin: 8px 0 12px; font-size: clamp(1.7rem, 4vw, 2.4rem); }
.home-intro p { line-height: 1.8; color: var(--color-muted); }
.home-intro .home-author { margin: 0; color: var(--color-accent); }
.home-intro a { display: inline-flex; align-items: center; min-height: 44px; }
.page-heading h2 { margin: 0 0 8px; }
@media (max-width: 600px) {
  /* flex-basis 在竖向父容器中会变成高度，因此手机上重置 flex。 */
  .article-filters { flex: none; max-width: none; grid-template-columns: 1fr; }
}
</style>
```

原来的输入框、按钮和禁用样式现在由全局 CSS 提供；旧的浅色首页介绍卡片也已经替换。

## 4. 替换关于页的样式块

文件：`E:\PersonalWebsite2\frontend\src\views\AboutView.vue`。

找到文件末尾 `<style scoped>`，整块替换为：

```vue
<style scoped>
.about-card {
  padding: clamp(20px, 3vw, 28px);
  margin-bottom: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}
.about-card h2 { margin-top: 0; }
.about-page p, .about-page dd { line-height: 1.8; color: var(--color-muted); overflow-wrap: anywhere; }
.about-page h2 { overflow-wrap: anywhere; }
.about-card dl { margin-bottom: 0; }
.about-card dl > div + div { margin-top: 16px; }
.about-card dt { font-weight: 700; }
.about-card dd { margin: 4px 0 0; }
.about-card a { color: var(--color-accent); overflow-wrap: anywhere; }
.about-links { display: flex; flex-wrap: wrap; gap: 12px; }
.about-links a {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-raised);
  color: var(--color-accent);
}
.about-links a:hover { border-color: var(--color-accent); }
</style>
```

## 5. 替换归档页的样式块

文件：`E:\PersonalWebsite2\frontend\src\views\ArchiveView.vue`。

找到文件末尾 `<style scoped>`，整块替换为：

```vue
<style scoped>
.archive-list {
  padding: 0;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}
.archive-list li + li { border-top: 1px solid var(--color-border); }
.archive-list a { display: flex; align-items: baseline; gap: 16px; padding: 18px 20px; color: var(--color-text); }
.archive-list a:hover { color: var(--color-accent); }
.archive-list time { flex: 0 0 auto; color: var(--color-muted); font-family: Consolas, monospace; }
.archive-list span { min-width: 0; overflow-wrap: anywhere; }
.archive-pagination { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
@media (max-width: 600px) {
  .archive-list a { flex-direction: column; gap: 6px; }
}
</style>
```

## 6. 替换资源页的样式块

文件：`E:\PersonalWebsite2\frontend\src\views\ResourceView.vue`。

找到文件末尾 `<style scoped>`，整块替换为：

```vue
<style scoped>
.resource-card { overflow-wrap: anywhere; }
.resource-card a {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  max-width: 100%;
  color: var(--color-accent);
  text-decoration: underline;
  text-underline-offset: 4px;
}
.resource-card .invalid-link { color: var(--color-error); }
</style>
```

## 7. 替换管理端的样式块

文件：`E:\PersonalWebsite2\frontend\src\views\AdminView.vue`。

该文件较长，请到最下方找到 `<style scoped>`，整块替换为：

```vue
<style scoped>
fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
label { display: block; margin-bottom: 16px; }
.login {
  max-width: 400px;
  padding: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}
.toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 20px 0; }
.admin-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 24px; }
.admin-layout > * { min-width: 0; }
.admin-layout > aside, .admin-layout > form {
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface);
}
.admin-layout aside h2, .admin-layout aside h3 { margin-top: 0; }
ul { list-style: none; padding: 0; }
li { margin-bottom: 8px; }
li button { width: 100%; text-align: left; overflow-wrap: anywhere; }
small { display: block; margin-top: 6px; color: var(--color-muted); }
.selected { border-color: var(--color-accent); background: var(--color-accent-soft); color: var(--color-accent); }

/* 外层列表/表单和内层编辑器/预览分别控制，避免平板出现嵌套窄列。 */
.writing { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.writing > * { min-width: 0; }
.writing textarea { font-family: "Cascadia Code", Consolas, monospace; line-height: 1.7; }
.preview { padding: 16px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface-raised); }
.resource-admin { margin-top: 40px; padding-top: 24px; border-top: 1px solid var(--color-border); }
.category-admin, .tag-admin { padding-bottom: 24px; margin-bottom: 24px; border-bottom: 1px solid var(--color-border); }
.category-admin ul, .tag-admin ul { display: flex; flex-wrap: wrap; gap: 8px; }
.category-admin li, .tag-admin li { min-width: 0; max-width: 100%; margin: 0; }
.article-tag-picker { margin-bottom: 16px; }
.tag-options { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  max-width: 100%;
  padding: 6px 10px;
  margin: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-raised);
}
.tag-option input { width: 18px; height: 18px; margin: 0; padding: 0; flex-shrink: 0; }
.tag-option span { min-width: 0; overflow-wrap: anywhere; }
@media (max-width: 1000px) {
  .writing { grid-template-columns: 1fr; }
}
@media (max-width: 900px) {
  .admin-layout { grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .admin-layout { gap: 16px; }
  .admin-layout > aside, .admin-layout > form, .login { padding: 16px; }
}
</style>
```

### 管理端宽度变化

| 浏览器宽度 | 文章列表与表单 | Markdown 编辑与预览 |
| --- | --- | --- |
| 大于 1000px | 左右两列 | 左右两列 |
| 901～1000px | 左右两列 | 上下两行 |
| 900px 及以下 | 上下两行 | 上下两行 |

这些断点依据窗口宽度。页面缩放也会改变可用宽度，浏览器会自动选择相应布局。

## 8. 本地查看

1. 保存六个文件。
2. 按平时方式启动 Java 后端。
3. 在 IDEA 的前端终端运行 `npm run dev`，打开输出的本地地址。
4. 如果 Vite 正在运行，保存后通常会自动更新；样式仍旧时刷新一次页面。
5. 先依次打开“文章、归档、资源、关于、管理”，确认能打开，再回复“暗色样式完成，检查与验收”。

表单输入框应为暗色，按钮应有明显边界，后台正文预览也应为暗色。昵称、网站名和个人介绍继续由当前 `siteProfile.js` 提供。

## 9. 助手验收清单

测试和正式验收由助手执行。会先核对手敲后的实际文件，再运行现有前端回归测试和生产构建。

```powershell
cd E:\PersonalWebsite2\frontend
node --test tests/category-flow.test.mjs
npm run build
```

浏览器验收覆盖桌面、768px 平板、390px 和 320px 手机宽度：

| 检查 | 通过标准 |
| --- | --- |
| 文章、归档、资源、关于、管理 | 背景、卡片、分隔线、正文和次要文字使用一致配色 |
| 导航和分页 | 窄屏自然换行，按钮完整可点击，当前导航可辨识 |
| 文章筛选与搜索 | 手机单列，长分类/标签不撑宽；搜索、清空、翻页仍可用 |
| 长标题、长标签、长链接 | 普通文字在容器内换行，页面没有整体横向溢出 |
| Markdown 代码 | 等宽字体、缩进保留、长行仅在代码框内横向滚动 |
| Markdown 表格与图片 | 宽表格在自身滚动；图片不超过正文宽度 |
| 后台文章和资源表单 | 窄屏单列，输入与保存按钮完整展示；选中项、禁用状态可辨识 |
| 后台正文预览 | 与前台正文配色一致，代码和表格不会把表单撑宽 |
| 键盘 Tab | 导航、输入、下拉框、按钮和复选框的焦点清晰可见 |
| 空列表、加载中、请求失败 | 提示可读，错误文字在暗色背景上清晰，重试按钮可操作 |

浏览器检查使用独立验收内容覆盖极端长度和 Markdown。涉及写入时使用专用验收环境，验收后清理。只有指南临时副本通过检查时，应记录为“预检通过”；用户实际文件通过检查后才更新为“已完成并验收”。

## 10. 预检记录

2026-10-14：助手将本文六个代码块提取并应用到独立临时前端副本，现有 9 项回归测试全部通过，Vite 生产构建通过。已对照实际模板复核选择器、颜色覆盖与响应式规则，临时副本已清理。

本次为指南预检，未修改实际业务源码，也未对本轮样式进行浏览器验收。实际文件由用户手敲完成后，再按第 9 节进行验收。

2026-10-15 实际验收：六个样式文件与指南一致；实际前端 9 项测试和生产构建通过。六个页面在 1440、768、390、320px 视口中无整体横向溢出；后台另检查 920px 中间断点。长代码、12 列表格、1600px 图片、长标题/标签、焦点、空状态和错误恢复均通过。独立 MySQL 测试库中的手机创建草稿、保存、发布和资源编辑流程通过。测试库、专用服务和临时配置已清理，详情见 STYLE-MOBILE-ACCEPTANCE.md。
