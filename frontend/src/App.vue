<template>
  <div class="site">
    <header class="site-header">
      <RouterLink class="brand" to="/">我的个人网站</RouterLink>
      <nav>
        <RouterLink to="/">文章</RouterLink>
      </nav>
    </header>

    <!-- 当前网址对应的页面会显示在这里。 -->
    <main class="container">
      <RouterView />
    </main>

    <footer class="site-footer">记录学习与生活</footer>
  </div>
</template>
