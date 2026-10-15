# 第八轮：关于页与首页个人简介

本轮目标：新增“关于”页面，在首页文章列表上方展示简短的个人介绍，并统一网站名称和页脚文案。

**本轮已于 2026-10-14 完成实际源码测试、review 和浏览器验收。以下保留手敲指南，已完成的步骤无需重复。按用户安排继续本地开发，暂不发布。**

编写指南时 GitHub 信息尚未确定，因此下面先使用可替换的样稿。实际源码现已填写昵称 firefly1v、GitHub 名称 InInNHD 和项目仓库链接，已通过验收。下面的介绍文字仍是可替换的样稿，不代表已经确认的个人经历。

## 1. 本轮结构与效果

- 首页仍为 `/`，保留文章搜索、筛选和分页，上方增加作者昵称、网站名、一句话介绍和“了解本站与作者”链接。
- 新增 `/about`，展示作者介绍、网站内容说明及文章、归档、资源入口。
- 顶部增加“关于”导航。
- 网站名、昵称、介绍和 GitHub 信息集中在 `siteProfile.js`，修改一次即可供首页、关于页和网站外层使用。

这是前端展示内容，不需要 Java 接口、数据库表或后台配置页面，也不需要安装依赖。

## 2. 新建个人内容文件

在 `frontend/src` 下新建 JavaScript 文件：

`E:\PersonalWebsite2\frontend\src\siteProfile.js`。

写入下面代码。可以先使用样稿，页面跑通后再修改文案。

```js
// 这里只放愿意在网站上公开的文字；由三个页面共同引用。
// 普通对象足够，内容修改后 Vite 会重新加载，不需要 ref 或后台接口。
export const siteProfile = {
  siteName: '我的个人网站',
  nickname: '本站作者',
  tagline: '记录学习、思考与生活。',
  aboutParagraphs: [
    '我在这里整理学习过程中的笔记，记录思考与心得，也保留一些愿意分享的生活片段。',
    '希望这些记录能帮助我回顾自己的成长，也为偶然来到这里的人提供一点参考。',
  ],
  // 暂未确定的信息留空，不生成占位链接。
  githubName: '',
  githubUrl: '',
}
```

以后填写 GitHub 时只修改最后两个字段，例如昵称填写自己的 GitHub 用户名，链接使用完整的 `https://github.com/你的用户名`。链接应从你自己的 GitHub 主页复制；不要将示例地址原样用于公开展示。

昵称可以是希望公开的网名，无需真实姓名。正文按普通文字显示，不使用 `v-html`；无需在字符串中写 HTML 标签。

## 3. 新建关于页

在 `frontend/src/views` 下新建：

`E:\PersonalWebsite2\frontend\src\views\AboutView.vue`。

写入完整代码。**代码块外面的三个反引号不要写入文件。**

```vue
<script setup>
import { siteProfile } from '../siteProfile.js'
</script>

<template>
  <section class="about-page">
    <header class="page-heading">
      <div>
        <h1>关于</h1>
        <p>{{ siteProfile.tagline }}</p>
      </div>
    </header>

    <article class="about-card" aria-labelledby="about-author">
      <h2 id="about-author">{{ siteProfile.nickname }}</h2>
      <!-- 静态段落按原顺序展示；修改文案不会改变其他页面的数据。 -->
      <p v-for="(paragraph, index) in siteProfile.aboutParagraphs" :key="index">
        {{ paragraph }}
      </p>
      <p v-if="siteProfile.githubUrl">
        <a :href="siteProfile.githubUrl" target="_blank" rel="noopener noreferrer">
          {{ siteProfile.githubName || 'GitHub' }} ↗
        </a>
      </p>
    </article>

    <section class="about-card" aria-labelledby="about-content">
      <h2 id="about-content">这里有什么</h2>
      <dl>
        <div>
          <dt>笔记</dt>
          <dd>整理学习中的知识、问题与解决过程。</dd>
        </div>
        <div>
          <dt>心得</dt>
          <dd>记录阅读、实践与思考后的理解。</dd>
        </div>
        <div>
          <dt>日记</dt>
          <dd>分享愿意公开的生活片段。</dd>
        </div>
        <div>
          <dt>资源</dt>
          <dd>收集有用的网站、工具和学习资料。</dd>
        </div>
      </dl>
    </section>

    <nav class="about-links" aria-label="站内内容入口">
      <RouterLink to="/">阅读文章</RouterLink>
      <RouterLink to="/archive">按年月归档</RouterLink>
      <RouterLink to="/resources">查看资源</RouterLink>
    </nav>
  </section>
</template>

<style scoped>
.about-card {
  padding: 24px;
  margin-bottom: 20px;
  background: white;
  border: 1px solid #e5e9ec;
  border-radius: 12px;
}
.about-card h2 { margin-top: 0; }
.about-page p, .about-page dd { line-height: 1.8; color: #607078; overflow-wrap: anywhere; }
.about-page h2 { overflow-wrap: anywhere; }
.about-card dl { margin-bottom: 0; }
.about-card dl > div + div { margin-top: 16px; }
.about-card dt { font-weight: 700; }
.about-card dd { margin: 4px 0 0; }
.about-card a { color: #216187; overflow-wrap: anywhere; }
.about-links { display: flex; flex-wrap: wrap; gap: 12px; }
.about-links a {
  padding: 10px 14px;
  border: 1px solid #dce2e6;
  border-radius: 6px;
  background: white;
  color: #216187;
}
</style>
```

关于页不请求后端。即使 Spring Boot 未启动，介绍内容和站内导航也能显示；进入文章或资源页面后，它们仍按各自的接口逻辑加载。

## 4. 注册关于页路由

文件：`E:\PersonalWebsite2\frontend\src\main.js`。

找到 `import ArchiveView from './views/ArchiveView.vue'`，在它下面加入：

```js
import AboutView from './views/AboutView.vue'
```

在 `routes: [...]` 中，找到管理员路由：

```js
{ path: '/admin', component: AdminView },
```

在它正上方加入：

```js
{ path: '/about', component: AboutView },
```

保留首页、详情、归档、资源和管理的原路由。

## 5. 网站外层：统一网站名，增加关于入口

文件：`E:\PersonalWebsite2\frontend\src\App.vue`。

当前文件从 `<template>` 开始，在这个标签**上方**增加：

```vue
<script setup>
import { siteProfile } from './siteProfile.js'
</script>
```

找到这一行：

```vue
<RouterLink class="brand" to="/">我的个人网站</RouterLink>
```

替换为：

```vue
<RouterLink class="brand" to="/">{{ siteProfile.siteName }}</RouterLink>
```

在资源链接之后、管理链接之前加入：

```vue
<RouterLink to="/about">关于</RouterLink>
```

找到现有页脚：

```vue
<footer class="site-footer">记录学习与生活</footer>
```

替换为：

```vue
<footer class="site-footer">{{ siteProfile.tagline }}</footer>
```

最后在 `</template>` 下方追加局部样式，处理较长的站名和页脚文案：

```vue
<style scoped>
.brand, .site-footer { overflow-wrap: anywhere; }
</style>
```

不要修改 `<RouterView />` 或外层布局结构。导航本来就支持换行，五个入口在窄屏下仍使用现有样式。

## 6. 首页：在文章列表上方增加简介

文件：`E:\PersonalWebsite2\frontend\src\views\ArticleListView.vue`。

在 `<script setup>` 中，找到原来的 Vue import：

```js
import { onBeforeUnmount, onMounted, ref } from 'vue'
```

在它下面加入：

```js
import { siteProfile } from '../siteProfile.js'
```

这次不修改任何文章加载、搜索或分页函数。

在 `<template>` 内，找到最外层 `<section>`。在它下面、原有 `<div class="page-heading">` 上面增加：

```vue
<header class="home-intro">
  <p class="home-author">{{ siteProfile.nickname }}</p>
  <h1>{{ siteProfile.siteName }}</h1>
  <p>{{ siteProfile.tagline }}</p>
  <RouterLink to="/about">了解本站与作者 →</RouterLink>
</header>
```

首页现在由网站名作为主标题。找到原文章标题 `<h1>文章</h1>`，将这一行改成：

```vue
<h2>文章</h2>
```

原栏目、分类、标签、搜索、文章卡片和翻页区域都保留。

在文件底部已有 `<style scoped>` 中、`</style>` **正上方**追加：

```css
.home-intro {
  padding: 28px;
  margin-bottom: 32px;
  background: white;
  border: 1px solid #e5e9ec;
  border-radius: 12px;
  overflow-wrap: anywhere;
}
.home-intro h1 { margin: 8px 0 12px; font-size: clamp(1.7rem, 4vw, 2.4rem); }
.home-intro p { line-height: 1.8; color: #607078; }
.home-intro .home-author { margin: 0; }
.home-intro a { color: #216187; }
.page-heading h2 { margin: 0 0 8px; }
```

## 7. 修改清单与运行

只需手敲五个文件：

| 文件 | 操作 |
| --- | --- |
| siteProfile.js | 新建共享文案 |
| AboutView.vue | 新建关于页面 |
| main.js | 新增 import 和路由 |
| App.vue | 共享网站名、页脚及关于导航 |
| ArticleListView.vue | 新增个人简介，文章标题调整为 h2 |

不执行数据库脚本。Vite 启动后会重载文件，浏览器访问 `http://localhost:5173/about`（以实际端口为准）。如果已有 Vite 正在运行，不需要再启动一份。

完成后回复“关于页代码已写完”。助手负责检查和验收，包括：

- 网站名、昵称、一句话介绍在首页、关于页及页脚一致。
- GitHub 链接为空时不显示无效入口；填写实际地址后再验收链接和新标签页行为。
- 首页简介和顶部导航都能进入 `/about`，关于页三个站内入口能正常跳转。
- 关于页没有后端请求，页面内容不受数据库状态影响。
- 原搜索、筛选、分页、归档、详情和管理的回归测试继续通过。
- 手机宽度下，五个导航入口、较长文案及页面内容可以换行，没有横向溢出。

**本轮先完成页面结构。GitHub 信息和真实个人介绍可以之后替换；发布依照你“暂不发布”的安排继续保留在本地。**

## 8. 指南预检记录

2026-10-14：助手将本指南代码应用到临时前端副本，现有 9 项回归测试及 Vue/Vite 生产构建均通过。实际业务文件没有修改，临时副本已清理。

这次预检确认代码可编译，原有脚本测试继续通过；不等同于实际手敲源码及页面验收。完成手敲后，助手会检查关于页、共享文案、导航及窄屏实际效果。

手敲后实际验收已完成：前端 9 项回归测试、生产构建和浏览器检查通过，记录见 ABOUT-PROFILE-ACCEPTANCE.md。
