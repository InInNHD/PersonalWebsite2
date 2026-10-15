# 第七轮：按年、月浏览文章归档

本轮目标：顶部增加“归档”入口；访客选择某年某月，分页查看该月已发布的笔记、心得和公开日记，点击标题进入现有详情页。

基于本地提交 `2d7be4d` 的搜索分页版本编写。**归档已于 2026-10-14 完成实际源码测试和浏览器验收。以下保留手敲指南，已完成的步骤无需重复；测试已由助手补齐并运行。**

## 1. 页面效果与数据流

归档页顶部是按年份分组的月份下拉框，例如：

```text
2026 年
  2026-10（12 篇）
  2026-09（5 篇）
2025 年
  2025-12（3 篇）
```

默认选择最新有文章的月份，每页 10 篇。只有草稿的月份不显示；完全没有公开文章时显示空提示。选择月份回第一页，月内按发布时间降序、编号降序排列。

数据流分两步：

1. `GET /api/articles/archive` 返回月份目录与每月公开文章数。
2. `GET /api/articles?month=2026-10&page=1&size=10` 复用现有分页接口，返回该月文章；点击文章进入 `/articles/{id}`。

归档以 `published_at` 的年月为准，即现有的首次发布时间，不以最后编辑时间归档。首版使用年份分组的月份选择框，不增加独立的“全年所有文章”模式。

## 2. 后端：给现有列表增加月份参数

文件：`E:\PersonalWebsite2\personalwebsite\src\main\java\com\firefly\personalwebsite\ArticleController.java`。

在 `list()` 方法签名中找到这一行：

```java
@RequestParam(name = "keyword", required = false) String keyword,
```

在其下面、`page` 参数上面插入这一行，末尾的逗号必须保留：

```java
@RequestParam(name = "month", required = false) String month,
```

接着找到 `list()` 内的总数查询：

```java
long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) " + fromWhere, Long.class, parameters.toArray());
```

在 `long total` **正上方**，也就是关键词判断块完整结束之后，加入：

```java
if (month != null) {
    // 限制为 MySQL DATETIME 可表示的四位年份以及合法月份。
    // 不接受 2026-1、2026-13、0000-01 或空字符串。
    if (!month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "归档月份格式必须为 YYYY-MM");
    }
    // 总数与文章查询都会复用此条件，避免总数属于其他月份。
    // 月份仍使用 ? 绑定，不能直接拼接用户输入。
    // ponytail: 当前个人站点按年月格式化筛选；数据量增大且查询变慢时改用月份起止范围并评估索引。
    fromWhere += " AND DATE_FORMAT(a.published_at, '%Y-%m') = ?";
    parameters.add(month);
}
```

不需要新增 import。原有搜索、栏目、分类、标签仍然可以与 `month` 同时使用；现有首页不发送月份，所以行为不变。

## 3. 后端：增加月份目录接口

仍在 `ArticleController.java`，找到 `ArticlePage` record 的结束 `) {}`。在它下面、现有列表方法上方的 `@GetMapping` **之前**加入整个片段：

```java
// 一个目录项表示“某年某月有多少篇已发布文章”。
public record ArchiveMonth(String month, long total) {}

@GetMapping("/archive")
@Transactional(readOnly = true)
public List<ArchiveMonth> archive() {
    String sql = """
            SELECT DATE_FORMAT(published_at, '%Y-%m') AS archive_month,
                   COUNT(*) AS article_count
            FROM article
            WHERE status = 'PUBLISHED' AND published_at IS NOT NULL
            GROUP BY DATE_FORMAT(published_at, '%Y-%m')
            ORDER BY archive_month DESC
            """;
    // 数据库先按年月聚合，目录只返回月份和数量，不加载全文。
    return jdbcTemplate.query(sql, (rs, rowNum) -> new ArchiveMonth(
            rs.getString("archive_month"), rs.getLong("article_count")));
}
```

`/archive` 是固定路径，Spring 会优先匹配它；现有 `/{id}` 详情路径继续保留。这里从文章表直接统计，不连接标签表，多标签文章不会被重复计数。

返回示例：

```json
[
  { "month": "2026-10", "total": 12 },
  { "month": "2026-09", "total": 5 }
]
```

没有已发布文章时返回 `[]`。这一接口公开可读，现有 SecurityConfig 已允许公开文章接口访问，无需修改权限配置。

## 4. 前端：新建归档页面

在 IDEA 的 `frontend/src/views` 下新建 Vue 文件：

`E:\PersonalWebsite2\frontend\src\views\ArchiveView.vue`。

按下面顺序手敲完整文件。**代码块外围的三个反引号是文档标记，不要写入 Vue 文件。**

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const months = ref([])
const selectedMonth = ref('')
const articles = ref([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')

// 月份目录由后端倒序排列，同一年自然连续，直接按顺序分组。
// computed 会在 months 改变时重新计算，供模板生成 optgroup。
const yearGroups = computed(() => {
  const groups = []
  for (const item of months.value) {
    const year = item.month.slice(0, 4)
    let group = groups.at(-1)
    if (!group || group.year !== year) {
      group = { year, months: [] }
      groups.push(group)
    }
    group.months.push(item)
  }
  return groups
})

// 月份目录与文章请求共同使用这个编号，重试或切换月份使旧请求失效。
let latestRequestId = 0
onBeforeUnmount(() => { latestRequestId += 1 })

async function loadArticles() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({
      month: selectedMonth.value,
      page: String(page.value),
      size: String(pageSize),
    })
    const response = await fetch(`/api/articles?${params.toString()}`)
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    // 等 JSON 解析完成后再判断，旧请求不能覆盖本页内容和页码。
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
    error.value = cause instanceof Error ? cause.message : '加载归档文章失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

async function loadArchive() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''
  // 重试从最新月份开始，先清空旧目录和列表。
  months.value = []
  selectedMonth.value = ''
  articles.value = []
  page.value = 1
  total.value = 0
  totalPages.value = 0
  try {
    const response = await fetch('/api/articles/archive')
    if (!response.ok) throw new Error(`请求失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId) return
    months.value = data
    // 空目录不再发文章请求，避免发送 month= 的非法参数。
    if (data.length === 0) return
    selectedMonth.value = data[0].month
    // 文章加载会取得新编号，并在自己的 finally 中结束 loading。
    await loadArticles()
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '加载归档目录失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

function changeMonth() {
  page.value = 1
  return loadArticles()
}

function changePage(nextPage) {
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  return loadArticles()
}

onMounted(loadArchive)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>文章归档</h1>
        <p>按首次发布时间，回看笔记、心得与公开的生活记录。</p>
      </div>
      <label v-if="months.length">
        归档月份
        <select v-model="selectedMonth" @change="changeMonth">
          <optgroup v-for="group in yearGroups" :key="group.year" :label="`${group.year} 年`">
            <option v-for="item in group.months" :key="item.month" :value="item.month">
              {{ item.month }}（{{ item.total }} 篇）
            </option>
          </optgroup>
        </select>
      </label>
    </div>

    <p v-if="loading" role="status">正在加载归档…</p>
    <div v-else-if="error">
      <p class="error" role="alert">{{ error }}</p>
      <button type="button" @click="loadArchive">重新加载归档</button>
    </div>
    <p v-else-if="months.length === 0">暂时没有已发布的文章。</p>
    <div v-else>
      <p role="status">{{ selectedMonth }} · 共 {{ total }} 篇文章</p>
      <p v-if="articles.length === 0">这个月暂时没有已发布的文章。</p>
      <ul v-else class="archive-list">
        <li v-for="article in articles" :key="article.id">
          <RouterLink :to="`/articles/${article.id}`">
            <time :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) }}</time>
            <span>{{ article.title }}</span>
          </RouterLink>
        </li>
      </ul>
      <nav v-if="totalPages > 0" class="archive-pagination" aria-label="归档分页">
        <button type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
        <button type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </nav>
    </div>
  </section>
</template>

<style scoped>
.archive-list { padding: 0; list-style: none; }
.archive-list li { border-bottom: 1px solid #dce2e6; }
.archive-list a { display: flex; gap: 16px; padding: 18px 0; align-items: baseline; }
.archive-list time { flex: 0 0 auto; color: #607078; }
.archive-list span { overflow-wrap: anywhere; }
.archive-pagination { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
.archive-pagination button { padding: 8px 12px; }
.archive-pagination button:disabled { opacity: 0.6; cursor: not-allowed; }
@media (max-width: 600px) {
  .archive-list a { flex-direction: column; gap: 6px; }
}
</style>
```

归档列表只显示日期和标题；文章正文、分类和完整标签继续由现有详情页展示。选择月份时仍可快速切换，最后一次请求才更新结果；加载时整个分页区域会隐藏，避免重复翻页。

## 5. 注册路由

文件：`E:\PersonalWebsite2\frontend\src\main.js`。

找到：

```js
import ResourceView from './views/ResourceView.vue'
```

在这行下面增加：

```js
import ArchiveView from './views/ArchiveView.vue'
```

在 `routes: [...]` 数组中，找到：

```js
{ path: '/resources', component: ResourceView },
```

在其上方增加：

```js
{ path: '/archive', component: ArchiveView },
```

不要替换首页或删除现有四条路由。

## 6. 增加顶部导航

文件：`E:\PersonalWebsite2\frontend\src\App.vue`。

找到文章和资源两个链接，在它们之间增加归档链接，最终该部分为：

```vue
<RouterLink to="/">文章</RouterLink>
<RouterLink to="/archive">归档</RouterLink>
<RouterLink to="/resources">资源</RouterLink>
```

后面的“管理”链接保留。现有全局导航样式已支持换行，不需要修改 `style.css`。

## 7. 完成后的运行与检查

本轮只手敲四个业务文件：

| 文件 | 修改 |
| --- | --- |
| ArticleController.java | 月份参数、月份过滤、归档目录接口 |
| ArchiveView.vue | 新建完整页面 |
| main.js | 新增 import 和路由 |
| App.vue | 新增顶部链接 |

没有 SQL 升级脚本，不要重复执行旧迁移。后端修改完成后，在 IDEA 使用 Java 17 重启 Spring Boot；前端 Vite 会自动重载，若仍显示旧页面则刷新。

先看这两个地址的响应，帮助理解数据流：

- `http://localhost:8080/api/articles/archive`：月份目录数组。
- `http://localhost:8080/api/articles?month=2026-10`：2026 年 10 月的分页对象；自己的库没有该月文章时，空列表是正常结果。

访问 `http://localhost:5173/archive`（以你当前 Vite 输出端口为准）。

**完成后回复“归档代码已写完”，助手将补齐测试并负责运行、review 和浏览器验收。无需你编写或执行测试，也不需要手工修改真实文章的发布时间来制造样例。**

## 8. 助手负责的验收范围

| 检查 | 预期结果 |
| --- | --- |
| 月份目录 | 按年月倒序，同一月份文章统计准确 |
| 只有草稿的月份 | 不出现在目录中；草稿不进入列表与总数 |
| 年份分组 | 下拉框按年份显示 optgroup，月份归属正确 |
| 月份边界 | 月末最后一秒和次月零点分别属于正确月份，覆盖闰年二月 |
| 月份参数 | 接受 2026-01，拒绝空值、2026-1、2026-00、2026-13、非年月字符串 |
| 月份＋已有筛选/搜索 | 各条件一起生效，总数正确 |
| 月内超过 10 篇 | 正常分页，无重复；超出末页使用已有纠正规则 |
| 切换月份 | 回第一页，日期及文章均属于所选月份 |
| 快速切换月份、请求失败、页面卸载 | 旧结果不能覆盖当前列表或错误状态 |
| 目录失败再重试 | 从最新月份重新加载，目录与文章状态一致 |
| 空目录 | 不发送非法 month= 请求，显示空提示 |
| 详情及原首页 | 归档标题可进入详情，原搜索和组合筛选不受影响 |
| 窄屏 | 日期和标题换行，导航及月份选择框可用 |

完成状态以实际源码验收为准；本轮已达到 v0.6.0 预发布候选节点，发布动作由用户另行指示。

## 9. 指南代码预检记录

2026-10-13：助手从本指南提取业务代码，在临时副本中应用，并编写归档检查后验证：

- Java 17 编译及后端 11 项真实 MySQL 测试通过，包含原有 10 项测试和新增的归档流程测试，写入通过事务回滚。
- 前端 9 项测试通过，包含原有 7 项和新增的 2 项归档测试；Vue 模板编译及 Vite 生产构建通过。
- 新增检查覆盖目录倒序与计数、跨年分组、闰年二月与次月零点、草稿隔离、月份参数、组合筛选、月内分页、重试、空目录、旧请求以及页面卸载。

临时副本和临时连接配置已清理。以上是 2026-10-13 的指南预检；2026-10-14 用户完成手敲后，实际源码的后端 11 项、前端 9 项测试及生产构建均通过，浏览器验收也已通过。完整记录见 RELEASE-v0.6.0.md。
