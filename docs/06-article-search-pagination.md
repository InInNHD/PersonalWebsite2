# 第六轮：文章搜索与分页

本轮目标：访客按标题或摘要搜索已发布文章，并翻页阅读历史内容；搜索与栏目、分类、标签同时生效。本指南基于 2026-10-08 的标签筛选完成状态。**本轮现已实现并完成测试和浏览器验收（2026-10-10）。以下保留手敲步骤，已完成的步骤不需要重复。**

无需改数据库、引入依赖或修改管理员接口。搜索首版不搜索 Markdown 正文；列表每页默认 10 篇，接口允许 1～50 篇。输入关键词后点击“搜索”或按回车才提交，翻页沿用已提交的关键词；切换任意筛选条件回到第一页。

## 1. 明确接口变化

示例：`GET /api/articles?type=NOTE&categoryId=1&tagId=2&keyword=Java&page=2&size=10`。

原列表接口返回数组，本轮改为对象（下面是结构示例，实际文章还包含摘要、栏目、分类和标签等现有字段）：

```json
{
  "items": [{ "id": 42, "title": "Java 笔记" }],
  "total": 12,
  "page": 2,
  "size": 10,
  "totalPages": 2
}
```

- `total` 是满足全部条件的已发布文章数，不是当前页数量。
- `page` 从 1 开始；超过最后一页时后端返回最后一页，并在响应中给出实际页码。没有结果时 `page=1`、`totalPages=0`、`items=[]`。
- `page` 限制 1～1000000，`size` 限制 1～50，非数字或越界返回 400。
- `keyword` 去掉两端空白后最多 100 个字符；空白字符串相当于不搜索。`%`、`_`、`!` 按普通字符搜索，不允许输入通配符改变搜索范围。
- 继续只返回 `PUBLISHED`，按发布时间降序、编号降序排列。同一时间发布的文章也有确定的排序。
- `GET /api/articles/{id}` 以及 `/api/admin/articles` 保持原来的返回格式。

这是列表接口的格式变更：后端、前台和测试都改完以后再验收，中途出现列表无法展示属于尚未同步完成。

## 2. 后端：增加分页结果类型

文件：`E:\PersonalWebsite2\personalwebsite\src\main\java\com\firefly\personalwebsite\ArticleController.java`。

找到 `public record ArticleDetail(...) {}` 的完整结束位置。在它下面、现有 `@GetMapping` 上面插入：

```java
// items 是本页文章；total 是满足相同筛选条件的文章总数。
public record ArticlePage(
        List<ArticleSummary> items,
        long total,
        int page,
        int size,
        long totalPages
) {}
```

不需要新增 import。接着将现有列表方法 **从它上方的 `@GetMapping` 开始，到 `list()` 方法结束的大括号为止** 整段替换为下面代码。下一段 `@GetMapping("/{id}")` 和 `detail()` 不要删。

```java
@GetMapping
@Transactional(readOnly = true)
public ArticlePage list(
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "categoryId", required = false) Long categoryId,
        @RequestParam(name = "tagId", required = false) Long tagId,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", defaultValue = "10") int size
) {
    if (page < 1 || page > 1_000_000 || size < 1 || size > 50) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "页码或每页数量超出范围");
    }

    String search = keyword == null ? "" : keyword.strip();
    if (search.length() > 100) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "搜索关键词不能超过 100 个字符");
    }

    // 总数查询与本页查询共用 FROM、WHERE 和筛选参数，避免条件不一致。
    // LEFT JOIN 继续保留没有分类的文章。
    String fromWhere = """
            FROM article a
            LEFT JOIN category c ON c.id = a.category_id
            WHERE a.status = 'PUBLISHED'
            """;
    List<Object> parameters = new ArrayList<>();

    if (type != null) {
        if (!Set.of("NOTE", "THOUGHT", "DIARY").contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "不支持的文章类型");
        }
        fromWhere += " AND a.type = ?";
        parameters.add(type);
    }

    if (categoryId != null) {
        if (categoryId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "分类编号必须大于 0");
        }
        fromWhere += " AND a.category_id = ?";
        parameters.add(categoryId);
    }

    if (tagId != null) {
        if (tagId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "标签编号必须大于 0");
        }
        // EXISTS 不展开关联表，多标签文章在列表与总数中都只算一次。
        fromWhere += """
                 AND EXISTS (
                     SELECT 1 FROM article_tag link
                     WHERE link.article_id = a.id AND link.tag_id = ?
                 )
                """;
        parameters.add(tagId);
    }

    if (!search.isEmpty()) {
        // LOCATE 查找普通子串：找到时位置大于 0，找不到时为 0。
        // %、_、! 都是普通字符，不需要自己维护 LIKE 转义逻辑。
        // 关键词始终通过 ? 绑定，不能直接拼到 SQL 中。
        // ponytail: 子串搜索会扫描候选文章；文章量增大且查询变慢时再评估全文索引。
        fromWhere += " AND (LOCATE(?, a.title) > 0 OR LOCATE(?, a.summary) > 0)";
        parameters.add(search);
        parameters.add(search);
    }

    long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) " + fromWhere, Long.class, parameters.toArray());
    long totalPages = (total + size - 1) / size;
    // 超出末页时回到最后一页；前端以响应中的 page 为准。
    int actualPage = total == 0 ? 1 : (int) Math.min(page, totalPages);
    long offset = (long) (actualPage - 1) * size;

    String sql = """
            SELECT a.id, a.title, a.summary, a.type,
                   a.published_at, a.category_id,
                   c.name AS category_name
            """ + fromWhere + " ORDER BY a.published_at DESC, a.id DESC LIMIT ? OFFSET ?";
    // 只给本页查询加 LIMIT/OFFSET，不能污染上面的总数查询参数。
    List<Object> pageParameters = new ArrayList<>(parameters);
    pageParameters.add(size);
    pageParameters.add(offset);

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
                    List.of()),
            pageParameters.toArray());

    // 只批量查询本页文章的标签；筛选某个标签时仍返回每篇的全部标签。
    var tagsByArticle = articleTags.readFor(
            articles.stream().map(ArticleSummary::id).toList());
    var items = articles.stream().map(a -> new ArticleSummary(
            a.id(), a.title(), a.summary(), a.type(), a.publishedAt(),
            a.categoryId(), a.categoryName(),
            tagsByArticle.getOrDefault(a.id(), List.of())
    )).toList();
    return new ArticlePage(items, total, actualPage, size, totalPages);
}
```

这里使用 MySQL 的原生子串函数 [LOCATE](https://dev.mysql.com/doc/refman/8.0/en/string-functions.html#function_locate)，无需通配符转义。查询保留现有只读事务；跨请求翻页期间若有文章发布或修改，内容和总数可能变化，这是本轮页码分页的正常边界，不承诺跨请求固定快照。

## 3. 前端：增加搜索与分页状态

文件：`E:\PersonalWebsite2\frontend\src\views\ArticleListView.vue`。

在 `const tagError = ref('')` 正下方插入：

```js
const keywordInput = ref('') // 输入框中的文字，尚未提交时不影响翻页。
const keyword = ref('') // 最近一次提交的关键词。
const page = ref(1)
const pageSize = 10
const total = ref(0)
const totalPages = ref(0)
```

将整个 `async function loadArticles() { ... }` 替换为下面版本。替换范围在 `async function loadCategories()` 之前结束，后面的分类、标签加载函数与 onMounted 不变。

```js
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
```

原来的 `latestRequestId`、`disposed` 和 `onBeforeUnmount` 原样保留。筛选下拉框和搜索仍能在加载期间操作，新请求会取代旧请求。

## 4. 前端：修改事件、增加搜索框和翻页栏

仍在该 Vue 文件中：

1. 找到栏目、分类、标签三个 `<select>`，将它们的 **三个** `@change="loadArticles"` 都改成 `@change="applyFilters"`。
2. 找到文章页顶部 `page-heading` 的结束 `</div>`：它在 `article-filters` 结束之后、分类错误提示 `<p v-if="categoryError" ...>` 之前。在这两个区域之间加入：

```vue
<form class="article-search" @submit.prevent="searchArticles">
  <label>
    搜索标题或摘要
    <input
      v-model="keywordInput"
      type="search"
      maxlength="100"
      placeholder="例如：Java、生活记录"
    >
  </label>
  <button type="submit">搜索</button>
  <button type="button" @click="clearSearch">清空搜索</button>
</form>
```

3. 在文章列表加载状态这行 `<p v-if="loading">` 的**正上方**加入：

```vue
<p v-if="!loading && !error" role="status">共 {{ total }} 篇文章</p>
```

4. 空列表提示改为 `当前搜索或筛选条件下没有已发布的文章。`。给现有错误提示 `<p v-else-if="error" class="error">` 增加 `role="alert"`。
5. 找到模板末尾文章卡片列表的结束 `</div>`，在它下方、最外层 `</section>` 上方增加：

```vue
<nav v-if="!error && totalPages > 0" class="article-pagination" aria-label="文章分页">
  <button type="button" :disabled="loading || page <= 1" @click="changePage(page - 1)">
    上一页
  </button>
  <span aria-live="polite">第 {{ page }} / {{ totalPages }} 页</span>
  <button type="button" :disabled="loading || page >= totalPages" @click="changePage(page + 1)">
    下一页
  </button>
</nav>
```

在已有 `<style scoped>` 内，`.article-filters` 规则结束后、`</style>` 之前追加：

```css
.article-search, .article-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
  margin: 20px 0;
}
.article-search label { flex: 1 1 220px; min-width: 0; }
.article-search input {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 8px;
}
.article-search button, .article-pagination button { padding: 8px 12px; }
.article-pagination { align-items: center; }
button:disabled { cursor: not-allowed; opacity: 0.6; }
```

## 5. 后端测试：适配返回格式，并测试搜索与分页

文件：`E:\PersonalWebsite2\personalwebsite\src\test\java\com\firefly\personalwebsite\CategoryArticleFlowTests.java`。

现有测试把公开文章列表当数组使用，因此必须同步适配，不能把旧断言删掉。找到类末尾的整个 `private JsonNode publicGet(String path, int expected)` 方法，将它替换为以下两个方法。后面的 `containsId()` 保留。

```java
// 统一读取实际接口响应，分页测试需要访问整个对象。
private JsonNode publicResponse(String path, int expected) throws Exception {
    // URI 重载接受已经编码的查询字符，避免 %25 被再次编码。
    var result = mvc.perform(get(java.net.URI.create(path)))
            .andExpect(status().is(expected)).andReturn();
    String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return content.isBlank() ? null : json.readTree(content);
}

// 保留旧流程测试读取数组的语义，仅为公开文章列表取出 items。
// 详情、分类、标签与错误响应均原样返回。
private JsonNode publicGet(String path, int expected) throws Exception {
    var body = publicResponse(path, expected);
    if (expected == 200 && body != null
            && (path.equals("/api/articles") || path.startsWith("/api/articles?"))) {
        assertTrue(body.get("items").isArray());
        return body.get("items");
    }
    return body;
}
```

接着在 `private long createCategory(String suffix)` 正上方、上一个测试结束的大括号之后插入以下测试。无需新增 import。随机前缀隔离已有数据；写入继续通过类上的事务回滚。

```java
@Test
void articleSearchPaginationAndValidation() throws Exception {
    long category = createCategory("-search");
    long tag = send(HttpMethod.POST, "/api/admin/tags",
            Map.of("name", prefix + "-search"), 201).get("id").asLong();
    var ids = new java.util.ArrayList<Long>();

    // 共 23 篇：第一页 10 篇，第二页 10 篇，第三页 3 篇。
    for (int i = 0; i < 23; i++) {
        var payload = article(category);
        // 只有第一篇标题含关键词；其余依靠摘要匹配。
        payload.put("title", i == 0 ? prefix + "-needle" : prefix + "-plain-" + i);
        payload.put("summary", i == 0 ? "没有关键词" : prefix + "-needle");
        payload.put("tagIds", java.util.List.of(tag));
        long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + id + "/publish", null, 200);
        ids.add(id);
    }

    // 固定相同发布时间，验证第二排序键 id 以及跨页不重复。
    for (long id : ids) {
        jdbc.update("UPDATE article SET published_at = '2026-01-01 12:00:00' WHERE id = ?", id);
    }
    var draftPayload = article(category);
    draftPayload.put("title", prefix + "-needle-draft");
    draftPayload.put("tagIds", java.util.List.of(tag));
    long draft = send(HttpMethod.POST, "/api/admin/articles", draftPayload, 201).get("id").asLong();

    String base = "/api/articles?type=NOTE&categoryId=" + category
            + "&tagId=" + tag + "&keyword=" + prefix + "-needle";
    var first = publicResponse(base, 200); // 默认 page=1、size=10。
    assertEquals(23, first.get("total").asLong());
    assertEquals(1, first.get("page").asInt());
    assertEquals(10, first.get("size").asInt());
    assertEquals(3, first.get("totalPages").asLong());
    assertEquals(10, first.get("items").size());
    assertFalse(containsId(first.get("items"), draft));
    assertEquals(tag, first.get("items").get(0).get("tags").get(0).get("id").asLong());
    var second = publicResponse(base + "&page=2", 200);
    var third = publicResponse(base + "&page=3", 200);
    assertEquals(10, second.get("items").size());
    assertEquals(3, third.get("items").size());
    // 三页合起来必须正好是预期的 23 篇，按编号从大到小排列。
    var actualIds = new java.util.ArrayList<Long>();
    for (var result : java.util.List.of(first, second, third)) {
        for (var item : result.get("items")) actualIds.add(item.get("id").asLong());
    }
    var expectedIds = new java.util.ArrayList<>(ids);
    java.util.Collections.reverse(expectedIds);
    assertEquals(expectedIds, actualIds);

    var beyond = publicResponse(base + "&page=999", 200);
    assertEquals(3, beyond.get("page").asInt());
    assertEquals(third.get("items"), beyond.get("items"));
    var empty = publicResponse(base + "-missing", 200);
    assertEquals(0, empty.get("total").asLong());
    assertEquals(0, empty.get("totalPages").asLong());
    assertEquals(1, empty.get("page").asInt());
    assertEquals(0, empty.get("items").size());
    var small = publicResponse(base + "&size=1&page=2", 200);
    assertEquals(23, small.get("totalPages").asLong());
    assertEquals(ids.get(21).longValue(), small.get("items").get(0).get("id").asLong());
    var blank = publicResponse("/api/articles?categoryId=" + category + "&keyword=%20%20", 200);
    assertEquals(23, blank.get("total").asLong());

    for (String invalid : java.util.List.of(
            "page=0", "page=-1", "page=1000001", "page=invalid",
            "size=0", "size=-1", "size=51", "size=invalid")) {
        publicResponse("/api/articles?" + invalid, 400);
    }
    publicResponse("/api/articles?keyword=" + "a".repeat(101), 400);
}

@Test
void searchTreatsSymbolsLiterallyAndDoesNotSearchBody() throws Exception {
    long category = createCategory("-literal");
    for (String title : java.util.List.of(prefix + "100%", prefix + "under_score",
            prefix + "bang!", prefix + "plain")) {
        var payload = article(category);
        payload.put("title", title);
        payload.put("summary", "没有搜索符号");
        payload.put("contentMarkdown", prefix + "-body-only");
        long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + id + "/publish", null, 200);
    }
    String base = "/api/articles?categoryId=" + category + "&keyword=";
    // URL 编码的 %、_、!，只应命中包含对应字符的那一篇。
    for (String symbol : java.util.List.of("%25", "_", "%21")) {
        assertEquals(1, publicResponse(base + symbol, 200).get("total").asLong());
    }
    assertEquals(0, publicResponse(base + prefix + "-body-only", 200).get("total").asLong());
    assertEquals(0, publicResponse(base + "%27%20OR%201%3D1%20--", 200).get("total").asLong());
}
```

旧测试从首页列表定位某篇文章，可能被真实库中更多已发布文章挤出默认前 10 篇。找到原来两处**无筛选的定位断言**，改成按当前测试随机标题搜索：

```java
// draftPublishEditRenameFilterAndClearCategory 测试内原来的：
assertTrue(containsId(publicGet("/api/articles", 200), id));
// 改为：
assertTrue(containsId(publicGet("/api/articles?keyword=" + prefix, 200), id));

// articleTagsSavePublishRenameAndClear 测试内原来的：
var list = publicGet("/api/articles", 200);
// 改为：
var list = publicGet("/api/articles?keyword=" + prefix, 200);
```

保留草稿不在首页出现的其他断言。上述方法名称用于定位：若你本地命名不同，以原语句和周围发布/标签断言为准。

## 6. 前端测试：替换旧列表测试，增加分页行为检查

文件：`E:\PersonalWebsite2\frontend\tests\category-flow.test.mjs`。

保留文件开头的 import、`component()`、`response()` 和 `deferred()`。将从 `test('栏目分类标签同时传入，旧筛选结果不能覆盖最新列表'` 开始，到第三个测试 `test('详情的旧正文和分类不会覆盖新文章'` **之前**的两个列表测试整段替换为下面内容。详情和后台的四个测试不变。

```js
// 本轮列表响应为分页对象，详情与管理员响应仍使用原 response()。
function articlePage(items, total = items.length, page = 1, size = 10) {
  return response({ items, total, page, size, totalPages: Math.ceil(total / size) })
}

test('搜索与全部筛选同时传入，旧结果不能覆盖列表和分页信息', async () => {
  const pending = []
  const app = component('ArticleListView.vue',
    'loadArticles, type, categoryId, tagId, keyword, page, articles, total, totalPages, error, loading', url => {
      const item = { url, ...deferred() }
      pending.push(item)
      return item.promise
    })
  app.type.value = 'NOTE'
  app.categoryId.value = 11
  app.tagId.value = 101
  app.keyword.value = 'Java 学习'
  app.page.value = 2
  const first = app.loadArticles()
  app.tagId.value = 202
  app.page.value = 1
  const second = app.loadArticles()
  const query = new URL(pending[0].url, 'http://localhost').searchParams
  assert.equal(query.get('type'), 'NOTE')
  assert.equal(query.get('categoryId'), '11')
  assert.equal(query.get('tagId'), '101')
  assert.equal(query.get('keyword'), 'Java 学习')
  assert.equal(query.get('page'), '2')
  assert.equal(query.get('size'), '10')
  pending[1].resolve(articlePage([{ id: 2, tags: [{ id: 202 }] }], 11, 1))
  await second
  pending[0].resolve(articlePage([{ id: 1 }], 99, 2))
  await first
  assert.equal(app.articles.value[0].id, 2)
  assert.equal(app.articles.value[0].tags[0].id, 202)
  assert.equal(app.total.value, 11)
  assert.equal(app.totalPages.value, 2)
  assert.equal(app.page.value, 1)
  assert.equal(app.error.value, '')
  assert.equal(app.loading.value, false)

  app.tagId.value = ''
  const allTags = app.loadArticles()
  const cleared = new URL(pending[2].url, 'http://localhost').searchParams
  assert.equal(cleared.has('tagId'), false)
  assert.equal(cleared.get('keyword'), 'Java 学习')
  pending[2].resolve(articlePage([]))
  await allTags
})

test('旧失败不影响新请求，新失败清理分页，卸载不再接受结果', async () => {
  const pending = []
  const app = component('ArticleListView.vue',
    'loadArticles, articles, total, totalPages, error, loading', () => {
      const item = deferred()
      pending.push(item)
      return item.promise
    })
  const first = app.loadArticles()
  const second = app.loadArticles()
  pending[0].reject(new Error('obsolete failure'))
  await first
  assert.equal(app.loading.value, true)
  assert.equal(app.error.value, '')
  pending[1].resolve(articlePage([{ id: 2 }], 21))
  await second
  assert.equal(app.totalPages.value, 3)
  const failed = app.loadArticles()
  pending[2].reject(new Error('network failed'))
  await failed
  assert.equal(app.error.value, 'network failed')
  assert.deepEqual(app.articles.value, [])
  assert.equal(app.total.value, 0)
  assert.equal(app.totalPages.value, 0)
  const leaving = app.loadArticles()
  app.unmount()
  pending[3].resolve(articlePage([{ id: 99 }], 99))
  await leaving
  assert.deepEqual(app.articles.value, [])
  assert.equal(app.total.value, 0)
})

test('搜索提交与清空回第一页，翻页保留已提交关键词，条件变化也重置页码', async () => {
  const urls = []
  const app = component('ArticleListView.vue',
    'searchArticles, clearSearch, applyFilters, changePage, keywordInput, keyword, page, categoryId, loading, totalPages',
    async url => {
      urls.push(url)
      const requested = Number(new URL(url, 'http://localhost').searchParams.get('page'))
      // 模拟文章减少后，后端把第三页纠正为第二页。
      return articlePage([{ id: 1 }], 11, Math.min(requested, 2))
    })
  app.page.value = 3
  app.keywordInput.value = '  Java  '
  await app.searchArticles()
  assert.equal(app.page.value, 1)
  assert.equal(app.keyword.value, 'Java')
  app.keywordInput.value = '尚未提交的新文字'
  await app.changePage(2)
  assert.equal(app.page.value, 2)
  assert.equal(new URL(urls.at(-1), 'http://localhost').searchParams.get('keyword'), 'Java')
  const before = urls.length
  await app.changePage(0)
  await app.changePage(3)
  app.loading.value = true
  await app.changePage(1)
  app.loading.value = false
  assert.equal(urls.length, before)
  app.totalPages.value = 3
  await app.changePage(3)
  assert.equal(app.page.value, 2) // 接受后端返回的实际页码。
  app.categoryId.value = 11
  await app.applyFilters()
  assert.equal(app.page.value, 1)
  assert.equal(new URL(urls.at(-1), 'http://localhost').searchParams.get('categoryId'), '11')
  await app.clearSearch()
  assert.equal(app.keyword.value, '')
  assert.equal(app.keywordInput.value, '')
  assert.equal(app.page.value, 1)
  assert.equal(new URL(urls.at(-1), 'http://localhost').searchParams.has('keyword'), false)
})
```

## 7. 运行顺序与验收

先完成后端和前端所有修改，再重启后端。Vite 会自动重载 Vue；若页面仍是旧状态，刷新浏览器。后端测试继续需要 Java 17、已初始化的真实 MySQL 和本地配置，**不要为了运行测试将默认 Java 8 当作项目 JDK**。

```powershell
cd E:\PersonalWebsite2\personalwebsite
.\mvnw.cmd test
```

```powershell
cd E:\PersonalWebsite2\frontend
node --test tests/category-flow.test.mjs
npm run build
```

按本轮指南新增两项后端测试和一项前端测试，当前测试数量预期由后端 8 项变为 10 项、前端 6 项变为 7 项，以实际报告为准。

| 检查 | 预期结果 |
| --- | --- |
| 浏览器直接访问 `/api/articles` | 返回分页对象；默认 page=1、size=10 |
| 标题含“Java”的已发布文章，搜索 Java | 显示该文章 |
| 只有摘要含关键词 | 同样能找到 |
| 只有正文含关键词 | 本轮不匹配 |
| 搜索词是 `%` 或 `_` | 仅匹配包含该字符的标题或摘要 |
| 同时选择栏目、分类、标签并搜索 | 列表及总数均满足全部条件；文章不重复 |
| 搜索命中的草稿日记 | 列表与总数均不包含草稿；直接详情仍 404 |
| 自动化测试中建立 23 篇文章 | 三页分别 10、10、3 篇，合起来不重复不遗漏 |
| 第二页切换分类或搜索新词 | 回到第一页 |
| 修改输入框但没提交，然后翻页 | 使用上一次提交的搜索词 |
| 清空搜索 | 取消关键词，保留栏目/分类/标签，回第一页 |
| 快速切换筛选和搜索 | 最后一次请求更新列表、总数和页码 |
| 第一页/末页、加载过程中 | 相应翻页按钮禁用 |
| 无结果 | 显示共 0 篇和空提示，不显示翻页栏 |
| 手机上查看搜索框及三个筛选框 | 自动换行，输入框不撑出页面 |

不需要手工在真实库创建 23 篇测试文章，自动化测试负责这项边界验证并回滚。若你已有足够内容，再补充真实浏览器翻页验收。

完成手敲后回复“搜索分页修改完成”，由助手 review、连接真实 MySQL 运行回归，并完成可执行的页面验收。届时再更新 README 和项目完成状态。**本指南准备完成不代表该功能已实现或测试通过。**

## 8. 版本节点

搜索分页完成后，历史内容不再受首页最新 20 篇限制。与已完成的组合筛选一起，构成适合整理 Release 的功能增量；待代码审查和验收通过，助手再提醒具体版本号。当前不创建标签、提交或发布。

## 9. 指南代码预检记录

2026-10-08：助手从本指南提取代码，在临时源码副本中应用后验证：

- Java 17 编译及后端 10 项测试通过，连接真实 MySQL，测试写入通过事务回滚。
- 前端 7 项测试通过，使用真实组件脚本和模拟网络；Vue 模板编译及 Vite 生产构建通过。
- 覆盖 23 篇文章跨页排序、默认参数、末页纠正、组合筛选与总数、草稿隔离、特殊字符和参数校验，以及前端请求顺序和搜索提交行为。

临时副本已清理。预检时业务源码仍是标签筛选版本；2026-10-10 已完成手敲后的实际源码测试与真实浏览器验收，记录见 RELEASE-v0.5.0.md。
