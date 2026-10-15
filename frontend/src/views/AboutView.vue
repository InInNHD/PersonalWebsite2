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