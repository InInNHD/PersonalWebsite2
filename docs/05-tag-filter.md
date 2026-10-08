# 第五轮：按标签筛选文章

本轮目标：访客在首页选择一个标签，只查看带有该标签的已发布文章；栏目、分类和标签可以同时使用。选择“全部标签”时取消标签筛选。文章拥有多个标签时，筛选结果每篇只出现一次。

数据流：标签下拉框 → `/api/articles?type=NOTE&categoryId=1&tagId=2` → 后端检查参数并过滤已发布文章 → 返回文章及其全部标签 → 前台显示。

本轮复用现有 `tag`、`article_tag` 和 `/api/tags`，无需新增表或依赖。下面的代码尚未写入业务源码。

**1. 给文章列表接口增加标签参数。**

打开 `E:\PersonalWebsite2\personalwebsite\src\main\java\com\firefly\personalwebsite\ArticleController.java`。

找到 `list()` 方法的参数部分。当前是：

```java
public List<ArticleSummary> list(
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "categoryId", required = false)
        Long categoryId
) {
```

将以上方法签名替换为：

```java
public List<ArticleSummary> list(
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "categoryId", required = false) Long categoryId,
        @RequestParam(name = "tagId", required = false) Long tagId
) {
```

方法上方原有的 `@GetMapping` 和 `@Transactional(readOnly = true)` 继续保留。

然后在该方法里找到这行注释：

```java
// 延续现有列表规则：显示满足筛选条件的最新 20 篇。
```

在它的正上方，也就是 `if (categoryId != null) { ... }` 完整结束以后，插入：

```java
if (tagId != null) {
    if (tagId <= 0) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "标签编号必须大于 0");
    }

    // EXISTS 只判断“这篇文章是否关联了所选标签”。
    // 不把关联表展开到文章列表中，因此多标签文章不会重复出现。
    sql += """
             AND EXISTS (
                 SELECT 1
                 FROM article_tag link
                 WHERE link.article_id = a.id AND link.tag_id = ?
             )
            """;
    parameters.add(tagId);
}
```

最终顺序为：栏目筛选 → 分类筛选 → 标签筛选 → 排序与 LIMIT → 查询文章 → 批量读取标签。

`tagId` 仍使用 SQL 问号绑定。合法的正数编号若不存在，结果为空数组；0、负数、非数字参数返回 400。`a.status = 'PUBLISHED'` 继续约束全部筛选结果。

**2. 为首页增加标签状态和加载函数。**

打开 `E:\PersonalWebsite2\frontend\src\views\ArticleListView.vue`。

在 `<script setup>` 中，找到：

```js
const categoryError = ref('')
```

在这一行下面插入：

```js
const tags = ref([])
const tagId = ref('') // 空字符串表示全部标签；选择标签后是数字编号。
const tagError = ref('')
```

找到 `loadArticles()` 中的：

```js
const query = params.toString()
```

在这一行正上方插入：

```js
if (tagId.value !== '') {
  params.set('tagId', String(tagId.value))
}
```

现有 `latestRequestId` 检查继续使用，快速切换标签时只接受最后一次请求的结果。

在 `loadCategories()` 方法完整结束之后、`onMounted(loadCategories)` 之前加入：

```js
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
```

在现有的 `onMounted(loadCategories)` 下面再增加：

```js
onMounted(loadTags)
```

保留 `onMounted(loadArticles)`。标签目录加载失败时，文章列表仍可正常加载。

**3. 增加标签下拉框和错误提示。**

仍在 `ArticleListView.vue` 中，找到 `<div class="article-filters">` 内“分类”的整个 `<label>...</label>`。

在分类 label 的结束标签之后、`article-filters` 结束的 `</div>` 之前，插入：

```vue
<label>
  标签
  <select v-model="tagId" @change="loadArticles">
    <option value="">全部标签</option>

    <option v-for="tag in tags" :key="tag.id" :value="tag.id">
      {{ tag.name }}
    </option>
  </select>
</label>
```

首页会并排显示栏目、分类和标签；已有 flex-wrap 会在窄屏自动换行。

找到分类错误提示的整个段落：

```vue
<p v-if="categoryError" class="error" role="alert">
  {{ categoryError }}
</p>
```

在它的结束标签后，增加：

```vue
<p v-if="tagError" class="error" role="alert">
  {{ tagError }}
</p>
```

文章卡片中的标签显示继续保留。即使按一个标签筛选，卡片也显示文章的全部标签。

**4. 增加后端组合筛选测试。**

打开 `E:\PersonalWebsite2\personalwebsite\src\test\java\com\firefly\personalwebsite\CategoryArticleFlowTests.java`。

找到 `private long createCategory(String suffix)`。在它上方、上一个测试结束的大括号之后，插入下面的完整测试方法：

```java
@Test
void tagFilterCombinesWithTypeAndCategoryWithoutLeakingDrafts() throws Exception {
    long category = createCategory("-filter-category");
    long otherCategory = createCategory("-filter-other");
    long firstTag = send(HttpMethod.POST, "/api/admin/tags",
            Map.of("name", prefix + "-filter-one"), 201).get("id").asLong();
    long secondTag = send(HttpMethod.POST, "/api/admin/tags",
            Map.of("name", prefix + "-filter-two"), 201).get("id").asLong();

    var payload = article(category);
    payload.put("tagIds", java.util.List.of(firstTag, secondTag));
    long note = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
            .get("id").asLong();
    send(HttpMethod.POST, "/api/admin/articles/" + note + "/publish", null, 200);

    payload.put("title", prefix + "-thought");
    payload.put("type", "THOUGHT");
    payload.put("tagIds", java.util.List.of(firstTag));
    long thought = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
            .get("id").asLong();
    send(HttpMethod.POST, "/api/admin/articles/" + thought + "/publish", null, 200);

    payload.put("title", prefix + "-other-category");
    payload.put("type", "NOTE");
    payload.put("categoryId", otherCategory);
    long other = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
            .get("id").asLong();
    send(HttpMethod.POST, "/api/admin/articles/" + other + "/publish", null, 200);

    payload.put("title", prefix + "-diary-draft");
    payload.put("type", "DIARY");
    payload.put("categoryId", category);
    long draft = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
            .get("id").asLong();

    var byTag = publicGet("/api/articles?tagId=" + firstTag, 200);
    assertEquals(3, byTag.size());
    assertTrue(containsId(byTag, note));
    assertTrue(containsId(byTag, thought));
    assertTrue(containsId(byTag, other));
    assertFalse(containsId(byTag, draft));

    String combined = "/api/articles?type=NOTE&categoryId="
            + category + "&tagId=" + firstTag;
    var filtered = publicGet(combined, 200);
    assertEquals(1, filtered.size());
    assertEquals(note, filtered.get(0).get("id").asLong());
    assertEquals(2, filtered.get(0).get("tags").size());

    assertEquals(1, publicGet("/api/articles?tagId=" + secondTag, 200).size());
    assertEquals(0, publicGet("/api/articles?type=DIARY&tagId=" + firstTag, 200).size());
    long missingTag = jdbc.queryForObject(
            "SELECT COALESCE(MAX(id), 0) + 1000 FROM tag", Long.class);
    assertEquals(0, publicGet("/api/articles?tagId=" + missingTag, 200).size());
    publicGet("/api/articles?tagId=0", 400);
    publicGet("/api/articles?tagId=-1", 400);
    publicGet("/api/articles?tagId=invalid", 400);
}
```

此测试复用已有认证、CSRF、辅助方法和事务回滚。生成的标签与分类只供该测试使用。

**5. 更新前端请求顺序测试。**

打开 `E:\PersonalWebsite2\frontend\tests\category-flow.test.mjs`。

找到靠前的 `test('栏目与分类同时传入，旧筛选结果不能覆盖最新列表', ...)`。将这一个完整测试替换为：

```js
test('栏目分类标签同时传入，旧筛选结果不能覆盖最新列表', async () => {
  const pending = []
  const app = component('ArticleListView.vue',
    'loadArticles, type, categoryId, tagId, articles, error, loading', url => {
      const item = { url, ...deferred() }
      pending.push(item)
      return item.promise
    })

  app.type.value = 'NOTE'
  app.categoryId.value = 11
  app.tagId.value = 101
  const first = app.loadArticles()
  app.tagId.value = 202
  const second = app.loadArticles()

  assert.equal(pending[0].url, '/api/articles?type=NOTE&categoryId=11&tagId=101')
  assert.equal(pending[1].url, '/api/articles?type=NOTE&categoryId=11&tagId=202')

  pending[1].resolve(response([{ id: 2, tags: [{ id: 202, name: '当前标签' }] }]))
  await second
  pending[0].resolve(response([{ id: 1, tags: [{ id: 101, name: '旧标签' }] }]))
  await first

  assert.equal(app.articles.value[0].id, 2)
  assert.equal(app.articles.value[0].tags[0].id, 202)
  assert.equal(app.error.value, '')
  assert.equal(app.loading.value, false)

  app.tagId.value = ''
  const allTags = app.loadArticles()
  assert.equal(pending[2].url, '/api/articles?type=NOTE&categoryId=11')
  pending[2].resolve(response([]))
  await allTags
})
```

这项测试执行实际组件函数，模拟后发请求先返回，确保页面不会被旧标签结果覆盖。其余前端测试保留。

**6. 运行和验收。**

重启后端，启动前端，刷新首页。建议先给三篇文章设置不同的栏目、分类与标签，再通过下拉框检查结果。

| 操作 | 预期 |
| --- | --- |
| 选择一个标签 | 只显示包含该标签的已发布文章 |
| 同时选择栏目、分类和标签 | 返回三种条件的交集 |
| 多标签文章符合筛选 | 只出现一次，卡片仍显示全部标签 |
| 选择全部标签 | 恢复栏目和分类下的文章列表 |
| 选择没有已发布文章的标签 | 显示现有的空列表提示 |
| 快速来回切换标签 | 结果对应最后一次选择 |
| 带标签的未发布日记 | 不出现在访客列表中 |
| 标签目录请求失败 | 显示标签加载错误，文章仍可读取 |

前端检查命令：

```powershell
cd E:\PersonalWebsite2\frontend
node --test tests/category-flow.test.mjs
npm run build
```

你完成手敲后告诉我“标签筛选完成”，我会 review 实际代码并执行测试。当前代码尚未应用，因此本轮没有运行结果。

数据库初始化与版本说明已在 `v0.3.0` 节点补齐并验证。本指南的标签筛选仍待手敲实现；完成并验收后，再按 `PROJECT-PROGRESS.md` 的规则提醒下一次版本发布。
