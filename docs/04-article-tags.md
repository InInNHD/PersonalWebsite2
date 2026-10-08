# 第四轮：文章关联多个标签

本轮目标：一篇文章可以选择 0～20 个标签；保存草稿、编辑和发布后保留标签；前台文章列表和正文显示标签。标签改名后，文章自动显示新名称。

数据流：后台复选框 → 请求中的 `tagIds` → 后端校验标签编号 → 在同一事务中保存文章和关联表 → 文章接口返回标签 → 前台展示。

下面按当前工作区代码编写。本文是手敲指南，没有把这些修改写入业务源码。按顺序完成，再交给我测试。

**1. 新建数据库脚本。**

文件：`E:\PersonalWebsite2\database\004-article-tag.sql`。

```sql
USE personal_website;

-- 一行表示“一篇文章使用了一个标签”。
-- 已有文章无需修改：没有关联记录就表示没有标签。
CREATE TABLE article_tag (
    article_id BIGINT NOT NULL COMMENT '文章编号',
    tag_id BIGINT NOT NULL COMMENT '标签编号',

    -- 联合主键防止同一文章重复关联同一标签。
    PRIMARY KEY (article_id, tag_id),

    -- 为按标签查询以及外键检查提供索引。
    INDEX idx_article_tag_tag_id (tag_id),

    -- 将来删除文章时，只删除该文章的关联记录。
    -- 标签本身仍保留，其他文章可以继续使用。
    CONSTRAINT fk_article_tag_article
        FOREIGN KEY (article_id) REFERENCES article (id)
        ON DELETE CASCADE,

    -- 不允许删除仍被文章引用的标签。
    CONSTRAINT fk_article_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

在 IDEA 数据库控制台中执行一次，再检查：

```sql
SHOW CREATE TABLE article_tag;
SELECT COUNT(*) FROM article_tag;
```

本机已经核对：`article.id` 和 `tag.id` 都是有符号 BIGINT，与本表字段类型匹配。

**2. 新建共享的文章标签类。**

文件：`E:\PersonalWebsite2\personalwebsite\src\main\java\com\firefly\personalwebsite\ArticleTags.java`。

前台和后台都要读取文章标签，因此用一个类共用 SQL。沿用 JdbcTemplate，不需要新增依赖。

```java
package com.firefly.personalwebsite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleTags {
    private final JdbcTemplate jdbcTemplate;

    public ArticleTags(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 前台需要编号和名称；后台可以从这里提取编号。
    public record TagItem(long id, String name) {}

    public List<TagItem> read(long articleId) {
        return readFor(List.of(articleId))
                .getOrDefault(articleId, List.of());
    }

    public Map<Long, List<TagItem>> readFor(List<Long> articleIds) {
        // 空列表不能生成 IN ()；也不必访问数据库。
        if (articleIds.isEmpty()) return Map.of();

        // 只拼接问号；文章编号仍作为参数绑定，避免 SQL 注入。
        String placeholders = String.join(",",
                Collections.nCopies(articleIds.size(), "?"));

        String sql = """
                SELECT link.article_id, t.id, t.name
                FROM article_tag link
                JOIN tag t ON t.id = link.tag_id
                WHERE link.article_id IN (%s)
                ORDER BY link.article_id, t.id
                """.formatted(placeholders);

        Map<Long, List<TagItem>> result = new HashMap<>();

        // 一次读取整批文章的标签，避免列表里的每篇文章分别发查询。
        jdbcTemplate.query(sql, (RowCallbackHandler) rs -> {
            long articleId = rs.getLong("article_id");
            result.computeIfAbsent(articleId, ignored -> new ArrayList<>())
                    .add(new TagItem(rs.getLong("id"), rs.getString("name")));
        }, articleIds.toArray());

        return result;
    }

    @Transactional
    public void replace(long articleId, List<Long> requestedIds) {
        // 兼容没有 tagIds 的旧请求，将其视为“不选择标签”。
        // 新前端会始终发送数组；[] 明确表示清空全部标签。
        List<Long> ids = requestedIds == null
                ? List.of()
                : requestedIds.stream().distinct().sorted().toList();

        if (!ids.isEmpty()) {
            String placeholders = String.join(",",
                    Collections.nCopies(ids.size(), "?"));

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tag WHERE id IN ("
                            + placeholders + ")",
                    Integer.class,
                    ids.toArray());

            // 前端传来的编号也可能被篡改，必须核对是否实际存在。
            if (count == null || count != ids.size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "所选标签不存在");
            }
        }

        // 先检查，再替换。这里只删除文章与标签的关联。
        jdbcTemplate.update(
                "DELETE FROM article_tag WHERE article_id = ?", articleId);

        for (Long tagId : ids) {
            jdbcTemplate.update(
                    "INSERT INTO article_tag (article_id, tag_id) VALUES (?, ?)",
                    articleId, tagId);
        }
    }
}
```

`replace()` 会加入调用方的事务。文章保存或标签保存出现异常时，两者一起回滚。请求里的数量、正数和非空元素校验在下一步的 `DraftRequest` 上声明。

**3. 修改后台文章接口。**

文件：`E:\PersonalWebsite2\personalwebsite\src\main\java\com\firefly\personalwebsite\AdminArticleController.java`。

把现有字段和构造方法改为：

```java
private final JdbcTemplate jdbcTemplate;
private final ArticleTags articleTags;

public AdminArticleController(
        JdbcTemplate jdbcTemplate, ArticleTags articleTags) {
    this.jdbcTemplate = jdbcTemplate;
    this.articleTags = articleTags;
}
```

在 `DraftRequest` 的最后一个字段 `Long categoryId` 后加逗号，并增加：

```java
// 每篇最多选择 20 个标签；数组中的每个编号都必须非空且大于 0。
@Size(max = 20)
List<@NotNull @Positive Long> tagIds
```

当前文件已经导入 `Size`、`NotNull`、`Positive` 和 `List`，无需重复导入。

在 `createDraft()` 中，将最后的返回语句替换为：

```java
// createDraft 已有 @Transactional，新增文章与关联记录一起提交。
articleTags.replace(id.longValue(), request.tagIds());
return new ArticleResult(id.longValue(), "DRAFT");
```

在 `AdminDetail` 的 `Long categoryId` 后加逗号，再增加：

```java
List<Long> tagIds
```

在 `detail()` 的 `new AdminDetail(...)` 中，原最后一个参数 `rs.getObject("category_id", Long.class)` 后加逗号，再增加一个参数：

```java
articleTags.read(id).stream()
        .map(ArticleTags.TagItem::id)
        .toList()
```

用下面的方法替换整个 `update()`：

```java
@PutMapping(value = "/{id}", consumes = "application/json")
@Transactional
public AdminDetail update(
        @PathVariable("id") long id,
        @Valid @RequestBody DraftRequest request) {

    // 先确认文章存在；不存在时返回 404，避免插入无效关联。
    detail(id);
    checkCategory(request.categoryId());

    String sql = """
            UPDATE article
            SET title = ?, summary = ?, content_markdown = ?,
                type = ?, category_id = ?
            WHERE id = ?
            """;

    // 保存内容时保留文章状态和第一次发布时间。
    jdbcTemplate.update(sql,
            request.title().strip(),
            request.summary().strip(),
            request.contentMarkdown(),
            request.type(),
            request.categoryId(),
            id);

    // 标签无效时抛出异常，刚才的文章内容更新也会回滚。
    articleTags.replace(id, request.tagIds());
    return detail(id);
}
```

给后台 `detail()` 增加 `@Transactional(readOnly = true)`，与它原来的 `@GetMapping("/{id}")` 并列。读取文章和标签会使用同一个事务。

`publishDraft()` 无需改动：发布操作更新状态和时间，标签已经在保存文章时写入。

**4. 修改前台文章接口。**

文件：`E:\PersonalWebsite2\personalwebsite\src\main\java\com\firefly\personalwebsite\ArticleController.java`。

增加 import：

```java
import org.springframework.transaction.annotation.Transactional;
```

把字段和构造方法改为：

```java
private final JdbcTemplate jdbcTemplate;
private final ArticleTags articleTags;

public ArticleController(
        JdbcTemplate jdbcTemplate, ArticleTags articleTags) {
    this.jdbcTemplate = jdbcTemplate;
    this.articleTags = articleTags;
}
```

在 `ArticleSummary` 和 `ArticleDetail` 两个 record 的最后字段 `String categoryName` 后加逗号，各自增加：

```java
List<ArticleTags.TagItem> tags
```

给 `list()` 和 `detail()` 各增加 `@Transactional(readOnly = true)`，与各自的 `@GetMapping` 并列。

在 `list()` 中，将最后的 `return jdbcTemplate.query(...)` 整段替换为以下代码。前面的筛选 SQL 和排序保持现有写法：

```java
List<ArticleSummary> articles = jdbcTemplate.query(
        sql,
        (rs, rowNum) -> new ArticleSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("type"),
                rs.getTimestamp("published_at").toLocalDateTime(),
                rs.getObject("category_id", Long.class),
                rs.getString("category_name"),
                List.of() // 先读取文章，下一次查询批量读取标签。
        ),
        parameters.toArray());

var tagsByArticle = articleTags.readFor(
        articles.stream().map(ArticleSummary::id).toList());

// record 是不可变对象，因此构造带有标签的新返回结果。
return articles.stream().map(a -> new ArticleSummary(
        a.id(), a.title(), a.summary(), a.type(), a.publishedAt(),
        a.categoryId(), a.categoryName(),
        tagsByArticle.getOrDefault(a.id(), List.of())
)).toList();
```

在 `detail()` 的 `new ArticleDetail(...)` 中，原最后一个参数 `rs.getString("category_name")` 后加逗号，再增加：

```java
articleTags.read(id)
```

原查询中的 `a.status = 'PUBLISHED'` 和查不到文章返回 404 的逻辑继续使用。草稿日记仍不能通过公开接口读取。

修改后，公开文章的 JSON 增加：

```json
{
  "tags": [
    { "id": 1, "name": "Spring Boot" },
    { "id": 2, "name": "学习记录" }
  ]
}
```

无标签时返回 `"tags": []`。上面的编号仅作结构示例。

**5. 修改后台编辑表单。**

文件：`E:\PersonalWebsite2\frontend\src\views\AdminView.vue`。

在 `emptyArticle()` 返回的对象中，`categoryId: null,` 后增加：

```js
tagIds: [], // 保存标签编号；复选框会向这个数组添加或移除编号。
```

替换 `fillForm()`：

```js
function fillForm(article) {
  Object.assign(form, emptyArticle(), article)

  // 复制数组，让编辑表单拥有自己的标签选择。
  form.tagIds = [...(article.tagIds ?? [])]
  savedSnapshot.value = JSON.stringify(form)
}
```

在 `persist()` 的请求对象中，`categoryId: form.categoryId,` 后增加：

```js
tagIds: [...form.tagIds],
```

在文章编辑表单中，找到“分类”的整个 `<label>`，在其后、摘要之前插入：

```vue
<div class="article-tag-picker" role="group" aria-labelledby="article-tags-label">
  <p id="article-tags-label">
    文章标签（可选，最多 20 个）
  </p>

  <p v-if="tags.length === 0">
    暂时没有标签，请先在上方的标签管理中创建。
  </p>

  <div class="tag-options">
    <label v-for="tag in tags" :key="tag.id" class="tag-option">
      <!-- :value 绑定数字编号；不带冒号会变成字符串。 -->
      <input
          v-model="form.tagIds"
          type="checkbox"
          :value="tag.id"
          :disabled="form.tagIds.length >= 20 && !form.tagIds.includes(tag.id)"
      />
      <span>{{ tag.name }}</span>
    </label>
  </div>

  <p>已选 {{ form.tagIds.length }} 个；全部取消后保存即可清空标签。</p>
</div>
```

外层已有 `:disabled="busy"` 的 fieldset，保存时这些复选框也会被禁用。

在本文件 `<style scoped>` 末尾增加：

```css
.article-tag-picker { margin-bottom: 16px; }
.tag-options { display: flex; flex-wrap: wrap; gap: 10px 16px; }
.tag-option { display: inline-flex; align-items: center; gap: 6px; margin: 0; }
.tag-option input { width: auto; margin: 0; padding: 0; }
```

最后一行覆盖现有 `input` 的全宽样式，否则复选框会被拉宽。现有 `articleDirty` 比较整个表单，新增 `tagIds` 后，勾选标签也会触发未保存提示。

**6. 在前台显示标签。**

文件：`E:\PersonalWebsite2\frontend\src\views\ArticleListView.vue`。

在文章卡片内，`<p>{{ article.summary }}</p>` 后插入：

```vue
<div v-if="article.tags?.length" class="article-tags" aria-label="文章标签">
  <span v-for="tag in article.tags" :key="tag.id" class="tag-badge">
    #{{ tag.name }}
  </span>
</div>
```

文件：`E:\PersonalWebsite2\frontend\src\views\ArticleDetailView.vue`。

在详情页 `<header class="article-header">` 内，摘要段落后插入同一段代码。

文件：`E:\PersonalWebsite2\frontend\src\style.css`，末尾增加：

```css
.article-tags { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
.tag-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  background: #e9eef2;
  color: #216187;
  font-size: 13px;
  overflow-wrap: anywhere;
}
```

标签名称使用 Vue 插值输出，不使用 `v-html`。本轮标签用于显示；按标签筛选可以在关联链路通过验收后接入。

**7. 增加一项后端回归测试。**

文件：`E:\PersonalWebsite2\personalwebsite\src\test\java\com\firefly\personalwebsite\CategoryArticleFlowTests.java`。

在类内、辅助方法之前加入下面的测试。它复用当前测试类的登录、CSRF、真实 MySQL 和事务回滚配置，不需要新增 import。

```java
@Test
void articleTagsSavePublishRenameAndClear() throws Exception {
    long first = send(HttpMethod.POST, "/api/admin/tags",
            Map.of("name", prefix + "-tag-one"), 201).get("id").asLong();
    long second = send(HttpMethod.POST, "/api/admin/tags",
            Map.of("name", prefix + "-tag-two"), 201).get("id").asLong();

    var payload = article(null);
    payload.put("tagIds", java.util.List.of(first, second, first));
    long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
            .get("id").asLong();
    String adminPath = "/api/admin/articles/" + id;
    String publicPath = "/api/articles/" + id;

    // 重复编号去重；草稿的公开详情和列表都不可见。
    assertEquals(2, send(HttpMethod.GET, adminPath, null, 200)
            .get("tagIds").size());
    publicGet(publicPath, 404);
    assertFalse(containsId(publicGet("/api/articles", 200), id));

    send(HttpMethod.POST, adminPath + "/publish", null, 200);
    var published = publicGet(publicPath, 200);
    assertEquals(2, published.get("tags").size());
    assertEquals(first, published.get("tags").get(0).get("id").asLong());
    String publishedAt = published.get("publishedAt").asText();

    var list = publicGet("/api/articles", 200);
    boolean found = false;
    for (var item : list) {
        if (item.get("id").asLong() == id) {
            assertEquals(2, item.get("tags").size());
            found = true;
        }
    }
    assertTrue(found);

    send(HttpMethod.PUT, "/api/admin/tags/" + first,
            Map.of("name", prefix + "-renamed"), 200);
    assertEquals(prefix + "-renamed", publicGet(publicPath, 200)
            .get("tags").get(0).get("name").asText());

    // 无效标签返回 400，校验失败时不替换已有标签。
    long missing = jdbc.queryForObject(
            "SELECT COALESCE(MAX(id), 0) + 1000 FROM tag", Long.class);
    payload.put("title", prefix + "-invalid-edit");
    payload.put("tagIds", java.util.List.of(missing));
    send(HttpMethod.PUT, adminPath, payload, 400);
    assertEquals(2, publicGet(publicPath, 200).get("tags").size());

    payload.put("title", prefix);
    payload.put("tagIds", java.util.List.of(second));
    assertEquals(1, send(HttpMethod.PUT, adminPath, payload, 200)
            .get("tagIds").size());
    assertEquals(second, publicGet(publicPath, 200)
            .get("tags").get(0).get("id").asLong());

    payload.put("tagIds", java.util.List.of());
    send(HttpMethod.PUT, adminPath, payload, 200);
    var cleared = publicGet(publicPath, 200);
    assertEquals(0, cleared.get("tags").size());
    assertEquals(publishedAt, cleared.get("publishedAt").asText());
    assertTrue(containsId(publicGet("/api/tags", 200), first));
    assertTrue(containsId(publicGet("/api/tags", 200), second));
}
```

这个测试类自身带有 `@Transactional`，测试写入会在结束时全部回滚。失败请求的更新回滚需要等请求事务结束后再查，因此不在这个外层测试事务内断言标题回滚。验收时我会另外通过真实 HTTP 请求确认失败更新没有提交文章内容。

**8. 启动与检查顺序。**

先执行建表 SQL，再启动 Java 17 后端，最后启动前端。若后端 record 增加参数后编译报错，检查所有 `new AdminDetail(...)`、`new ArticleSummary(...)` 和 `new ArticleDetail(...)` 是否都补上新参数。

前端终端：

```powershell
cd E:\PersonalWebsite2\frontend
npm run dev
```

| 检查操作 | 预期结果 |
| --- | --- |
| 给旧文章保存一个标签 | 后台重新打开时仍勾选该标签 |
| 新建文章，选择两个标签，保存草稿 | 标签保留，访客仍看不到草稿 |
| 保存并发布 | 主页和正文显示两个标签 |
| 取消一个标签并保存 | 主页、正文和后台只保留另一个标签 |
| 给标签改名，刷新前台 | 所有关联文章显示新名称 |
| 全部取消并保存 | 文章不显示标签，标签管理中的标签仍存在 |
| 新建另一篇文章 | 不继承上一篇文章的勾选状态 |
| 只修改标签后切换文章 | 出现未保存修改提醒 |
| 接口提交不存在、负数、null 元素或超过 20 个标签 | HTTP 400，不覆盖原文章和标签 |
| 无标签的历史文章 | 正常显示，接口返回空标签数组 |

这份新增代码尚未应用或运行。你完成手敲后，告诉我“文章标签关联完成，测试和验收由你完成”，我会检查实际修改并运行本轮验收。
