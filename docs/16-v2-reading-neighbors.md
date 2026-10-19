# 第三轮 3C：公开文章前后篇与整轮验收

日期：2026-10-19。接续已验收的3A和3B。**本节为IDEA手敲指南。2026-10-19实际3C及第三轮本地功能验收通过：前端34项、后端18项、两端构建、整轮浏览器流程和个人库只读核对通过。** 详见[第三轮实际验收记录](V2-READING-NAVIGATION-ACCEPTANCE.md)。下文保留实现步骤。

## 1. 规则、效果与数据流

- 正文末尾增加“继续阅读”，桌面两栏、手机一栏，显示相邻文章标题和首次发布时间。
- **上一篇=较早发布，下一篇=较新发布**；同一发布时间以编号区分，较小编号在前。标题中写出“较早/较新”，避免与首页倒序列表混淆。
- 按全站公开文章的时间顺序导航，不受置顶、更新时间、栏目、分类、标签、关键词或页码限制；返回列表的原条件和位置继续保留。
- 当前文章、上一篇和下一篇都必须为PUBLISHED且有首次发布时间。草稿/已下线/不存在的当前文章返回404；相邻项永远不返回草稿标题或正文。
- 最早文章的previous为null，最新文章的next为null；仅一篇公开文章时两边均为null，页面显示边界提示而非空链接。
- 导航单独请求、单独重试。出错不遮盖正文；换文或离开时旧响应失效。某篇链接在取得后又被撤下，进入详情仍由现有公开接口返回404。

数据流：详情请求现有正文 → ArticleNeighbors读取当前编号 → 请求`GET /api/articles/{id}/neighbors` → 后端先验证当前公开状态，再用参数化SQL各取最近的较早/较新一篇 → 返回最小链接数据 → RouterLink跳到相邻详情，并带上经过B2白名单处理的原列表来源。

不改原ArticleController，不修改SecurityConfig，不新增依赖，不修改表结构，**不执行SQL迁移**。三次只读查询使用现有JdbcTemplate和只读事务；MySQL采用项目原有事务配置。个人站点先沿用现有表，后续数据量增大且查询确实变慢时再根据执行计划评估索引。

## 2. 文件顺序

根目录：`E:\PersonalWebsite2`。只输入代码框中的代码，文件说明、三个反引号不要写入源码。

| 顺序 | 文件 | 操作 |
| --- | --- | --- |
| 1 | personalwebsite/src/main/java/com/firefly/personalwebsite/ArticleNavigationController.java | 新建完整文件 |
| 2 | frontend/src/components/ArticleNeighbors.vue | 新建完整文件 |
| 3 | frontend/src/views/ArticleDetailView.vue | 两处增加：import与组件标签 |
| 4 | frontend/src/v2.css | 末尾追加一次CSS |

测试仍由助手维护和执行，你不用手敲tests文件。保留原数据库和管理员配置。

## 3. 新建 ArticleNavigationController.java

文件：`personalwebsite/src/main/java/com/firefly/personalwebsite/ArticleNavigationController.java`。

与ArticleController、AdminArticleController位于同一个包；不要写到它们的类内部，也不要覆盖旧控制器。

```java
package com.firefly.personalwebsite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleNavigationController {
    private final JdbcTemplate jdbcTemplate;

    public ArticleNavigationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 导航只返回链接所需的信息，不加载正文、草稿标题或标签。
    public record ArticleLink(long id, String title, LocalDateTime publishedAt) {}
    public record ArticleNeighbors(ArticleLink previous, ArticleLink next) {}

    private static final RowMapper<ArticleLink> LINK_MAPPER = (rs, rowNum) -> new ArticleLink(
            rs.getLong("id"), rs.getString("title"),
            rs.getTimestamp("published_at").toLocalDateTime());

    @GetMapping("/{id}/neighbors")
    @Transactional(readOnly = true)
    public ArticleNeighbors neighbors(@PathVariable("id") long id) {
        // 先验证当前文章。草稿、已下线和不存在的编号使用同一种404。
        List<ArticleLink> currentRows = jdbcTemplate.query("""
                SELECT id, title, published_at FROM article
                WHERE id = ? AND status = 'PUBLISHED' AND published_at IS NOT NULL
                """, LINK_MAPPER, id);
        if (currentRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
        }
        ArticleLink current = currentRows.get(0);

        // 上一篇=较早发布；同一秒发布时，较小编号排在前面。
        // LIMIT在数据库内取最近一篇，不下载全站文章后在Java中查找。
        List<ArticleLink> previous = jdbcTemplate.query("""
                SELECT id, title, published_at FROM article
                WHERE status = 'PUBLISHED' AND published_at IS NOT NULL
                  AND (published_at < ? OR (published_at = ? AND id < ?))
                ORDER BY published_at DESC, id DESC LIMIT 1
                """, LINK_MAPPER, current.publishedAt(), current.publishedAt(), id);

        // 下一篇=较新发布；不按置顶排序，也不受列表的分类、标签等筛选限制。
        List<ArticleLink> next = jdbcTemplate.query("""
                SELECT id, title, published_at FROM article
                WHERE status = 'PUBLISHED' AND published_at IS NOT NULL
                  AND (published_at > ? OR (published_at = ? AND id > ?))
                ORDER BY published_at ASC, id ASC LIMIT 1
                """, LINK_MAPPER, current.publishedAt(), current.publishedAt(), id);

        // 首尾没有相邻文章时返回null，前端显示边界提示，不生成空链接。
        return new ArticleNeighbors(
                previous.isEmpty() ? null : previous.get(0),
                next.isEmpty() ? null : next.get(0));
    }
}
```

### 接口响应

请求：`http://localhost:8080/api/articles/1/neighbors`，其中1换成实际已发布文章编号。不需要管理员登录。

下面是结构示例，编号、标题、日期由你自己的内容决定，不要求与示例相同：

```json
{
  "previous": {"id": 2, "title": "更早的记录", "publishedAt": "2026-10-01T10:00:00"},
  "next": {"id": 5, "title": "较新的记录", "publishedAt": "2026-10-19T10:00:00"}
}
```

仅一篇公开文章时应返回：

```json
{"previous":null,"next":null}
```

这里不返回当前文章，也不返回contentMarkdown、summary、标签、分类或草稿信息。非法数字格式会由Spring返回400；有效数字但不存在、未发布、没有发布时间时为404。

## 4. 新建 ArticleNeighbors.vue

文件：`frontend/src/components/ArticleNeighbors.vue`。

```vue
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { listReturn } from '../listNavigation.js'

const props = defineProps({
  articleId: { type: [String, Number], required: true },
  from: { default: null },
})
const previous = ref(null)
const next = ref(null)
const loading = ref(false)
const error = ref('')
let latestRequestId = 0

// 沿用B2的白名单：from只能指向本站首页及合法筛选条件。
// 同一个来源跟随前后篇传递，连续阅读后仍能回原列表。
const returnPath = computed(() => {
  const origin = listReturn(props.from)
  const query = new URLSearchParams(origin.query ?? {}).toString()
  return '/' + (query ? `?${query}` : '') + (origin.hash ?? '')
})
function articleLocation(item) {
  return { path: `/articles/${item.id}`, query: { from: returnPath.value } }
}

async function loadNeighbors() {
  const requestId = ++latestRequestId
  loading.value = true
  error.value = ''
  previous.value = null
  next.value = null
  try {
    const response = await fetch(`/api/articles/${encodeURIComponent(props.articleId)}/neighbors`)
    if (response.status === 404) throw new Error('当前文章已下线或不存在')
    if (!response.ok) throw new Error(`前后篇加载失败：HTTP ${response.status}`)
    const data = await response.json()
    if (requestId !== latestRequestId) return
    previous.value = data.previous
    next.value = data.next
  } catch (cause) {
    if (requestId !== latestRequestId) return
    error.value = cause instanceof Error ? cause.message : '前后篇加载失败'
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

watch(() => props.articleId, loadNeighbors, { immediate: true })
onBeforeUnmount(() => { latestRequestId += 1 })
</script>

<template>
  <section class="reading-neighbors" aria-label="继续阅读">
    <div class="reading-neighbors-heading">
      <h2>继续阅读</h2><span>按发布时间浏览公开文章</span>
    </div>
    <p v-if="loading" role="status">正在查找前后篇…</p>
    <div v-else-if="error" class="reading-neighbors-error" role="alert">
      <p>{{ error }}，正文仍可阅读。</p>
      <button type="button" @click="loadNeighbors">重新加载前后篇</button>
    </div>
    <nav v-else class="reading-neighbor-grid" aria-label="前后篇文章">
      <RouterLink v-if="previous" :to="articleLocation(previous)" class="reading-neighbor">
        <small>← 上一篇 · 较早</small><strong>{{ previous.title }}</strong>
        <time :datetime="previous.publishedAt">{{ previous.publishedAt?.slice(0, 10) }}</time>
      </RouterLink>
      <div v-else class="reading-neighbor is-boundary">已经是最早一篇公开文章</div>
      <RouterLink v-if="next" :to="articleLocation(next)" class="reading-neighbor">
        <small>下一篇 · 较新 →</small><strong>{{ next.title }}</strong>
        <time :datetime="next.publishedAt">{{ next.publishedAt?.slice(0, 10) }}</time>
      </RouterLink>
      <div v-else class="reading-neighbor is-boundary">已经是最新一篇公开文章</div>
    </nav>
  </section>
</template>
```

## 5. ArticleDetailView.vue 两处增加

文件：`frontend/src/views/ArticleDetailView.vue`。

### 5.1 增加import

找到3B已经加入的这一行：

```js
import ReadingContent from '../components/ReadingContent.vue'
```

在其下一行增加：

```js
import ArticleNeighbors from '../components/ArticleNeighbors.vue'
```

### 5.2 在正文与页尾之间增加组件

找到现有这一段：

```vue
          <div ref="body" class="markdown-body reading-body" @load.capture="scheduleReading"
               @error.capture="scheduleReading">
            <ReadingContent :html="contentHtml" :code-blocks="rendered.codeBlocks" />
          </div>
          <footer class="reading-end">
```

只在`</div>`和`<footer class="reading-end">`之间增加一行，结果为：

```vue
          <div ref="body" class="markdown-body reading-body" @load.capture="scheduleReading"
               @error.capture="scheduleReading">
            <ReadingContent :html="contentHtml" :code-blocks="rendered.codeBlocks" />
          </div>
          <ArticleNeighbors :article-id="article.id" :from="route.query.from" />
          <footer class="reading-end">
```

保留footer内原来的文字、返回链接和结束标签。导航放在正文测量div之外，不计入“正文阅读进度”；章节目录仍只来自Markdown标题。

## 6. 追加CSS

文件：`frontend/src/v2.css`。

原有3A、3B样式均保留，文件末尾追加一次：

```css
/* 第三轮3C：前后篇独立加载，失败不遮住正文。 */
.reading-neighbors { margin-top: 40px; padding-top: 26px; border-top: 1px solid var(--color-border); }
.reading-neighbors-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: 10px; margin-bottom: 18px; }
.reading-neighbors-heading h2 { margin: 0; font-size: 19px; }
.reading-neighbors-heading span, .reading-neighbors > p { font-size: 12px; color: var(--color-muted); }
.reading-neighbor-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.reading-neighbor { min-width: 0; display: flex; flex-direction: column; gap: 12px; padding: 20px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); color: var(--color-text); }
.reading-neighbor small { font-size: 12px; color: var(--color-accent); }
.reading-neighbor strong { font-size: 16px; font-weight: 550; line-height: 1.7; overflow-wrap: anywhere; }
.reading-neighbor time { font-size: 11px; color: var(--color-muted); margin-top: auto; }
a.reading-neighbor:hover { text-decoration: none; border-color: var(--color-accent); }
a.reading-neighbor:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 3px; }
.reading-neighbor.is-boundary { justify-content: center; font-size: 13px; color: var(--color-muted); border-style: dashed; background: transparent; }
.reading-neighbors-error { padding: 16px; border: 1px dashed var(--color-border); border-radius: 12px; font-size: 13px; color: var(--color-muted); }
.reading-neighbors-error p { margin-top: 0; }
.reading-neighbors-error button { font-size: 12px; }
@media (max-width: 600px) {
  .reading-neighbor-grid { grid-template-columns: minmax(0, 1fr); }
  .reading-neighbor { padding: 17px; }
}
```

## 7. 启动与自查

1. 保存Java文件，在IDEA重启后端，确认项目SDK和后端运行配置使用Java17。系统终端默认java仍可能是1.8，不能仅凭IDEA的项目语言级别判断。
2. 在浏览器访问一篇已公开文章的`/api/articles/{id}/neighbors`，应得到previous/next对象或null。404先确认编号对应的文章已发布；新接口仍404时确认后端已重启且新类位于扫描包内。
3. 在前端终端运行：

```powershell
cd E:\PersonalWebsite2\frontend
npm.cmd run dev
```

原先`npm run dev`可用也可继续使用；无需再次安装依赖。打开终端实际显示的前端地址，进入正文末尾。

| 检查 | 通过标准 |
| --- | --- |
| 中间文章 | 前后篇分别是最近较早/较新公开文章；点进去正文、标题和网址一致 |
| 首尾或仅一篇 | 对应边界文字正常，没有空链接 |
| 草稿隔离 | 草稿不出现在导航；请求草稿编号的neighbors返回404，不能查到其邻居 |
| 同时发布 | 相同发布时间按编号确定唯一顺序，不跳过或重复当前文章 |
| 置顶与更新 | 修改置顶、保存旧文不改变它基于首次发布时间的相邻位置 |
| 连续阅读 | 从组合筛选列表进入，连续点前后篇后仍能返回原条件/页码及已有位置 |
| 独立失败 | 导航接口失败时正文仍在，出现“重新加载前后篇”，重试可恢复 |
| 快速切换 | 旧导航响应不会出现在新文章下，离开后不写回旧状态 |
| 手机 | 320/390px卡片单列，长标题不会撑宽页面，按钮/链接可用键盘访问 |
| 3A/3B | 目录定位、阅读进度、回顶、高亮、复制和图片关闭继续正常 |

不需要为手工验收更改个人文章的发布时间或插入测试SQL；相同时间、错误和草稿等边界由助手在隔离测试环境覆盖。

## 8. 第三轮整体验收与版本节点

2026-10-19用户手敲后的实际验收已完成，以下项目已执行，范围与证据见整轮验收记录：

- 新接口真实MySQL排序、同时间、首尾、草稿隔离、下线和参数边界测试；既有后台/公开接口回归。
- 实际前端测试和两端构建；浏览器连续阅读、返回位置、导航故障恢复、窄屏布局。
- 3A目录/进度、3B复制/图片查看与3C导航的整轮组合检查，以及真实个人内容只读核对。

**第三轮本地功能验收已通过，达到可提交GitHub和创建阶段性预发布Release的节点。** 尚未执行发布操作；发布仍需用户明确指令。后续第四轮内容导航与个人展示，云部署仍后置。

## 9. 本指南预检

2026-10-19候选副本预检通过：

- Java17专用MySQL库中后端18项测试通过（原15项回归+本节3项）；全部测试数据事务回滚，后端生产打包通过。
- 本指南前端6项专项、候选副本28项既有回归及Vite生产构建通过。
- 8组真实HTTP检查通过：中间/首尾、最小链接字段、中文标题、草稿详情及导航404、不存在404、非法格式400和详情兼容。
- 浏览器连接专用库验证跨栏目连续阅读、原NOTE+关键词来源保留、首尾边界、导航503不遮正文/重试恢复，以及3B复制和图片键盘打开/Escape关闭。320/390/768/1440px无整页横向溢出，手机卡片单列且长标题可换行。
- 个人库五表校验值前后一致；未修改实际业务源码，未安装新依赖。预检只使用专用库样例，不把本次联调说成个人内容验收。
- 临时服务/页面、专用测试库和含连接配置的临时源码/编译产物已清理。截图和结果保存在Git忽略的`artifacts/v2-reading-navigation-preflight/`。

以上为指南阶段的预检记录。其后的实际源码及第三轮功能验收已于2026-10-19完成，结果见本文开头链接；两类验收记录分别保留。
