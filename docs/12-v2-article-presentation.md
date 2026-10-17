# 第二版第二轮 B1：文章展示字段与公开统计

日期：2026-10-16。接续已验收的第二轮A。

**2026-10-17 实际验收已通过。** 前端14项、独立库后端15项测试及两端构建通过；按用户授权清理两处误写标记，备份恢复验证后完成个人库005迁移，原有数据保持一致。迁移后个人环境7组只读接口及浏览器首页/详情复核通过。范围见 [验收记录](V2-ARTICLE-PRESENTATION-ACCEPTANCE.md)，下一节B2。

## 1. 本节目标与规则

B分两次手敲：B1打通文章展示数据和后台维护；B2接着做筛选网址、页码与返回列表位置恢复。本节完成后B仍未全部完成。

本节完成后的效果：

- 后台文章表单增加“封面地址”和“置顶这篇文章”，沿用现有保存、发布、认证与CSRF。
- 首页卡片优先显示自定义封面；未填写或加载失败使用栏目默认图。首篇大卡片仍只是版式强调，只有pinned为true才显示“置顶”。
- 普通公开列表先置顶，再按首次发布时间、编号倒序，排序发生在分页前。筛选条件继续生效；归档保持时间顺序。
- 新建、保存和首次发布记录updatedAt；重复调用发布接口保留原时间。已发布文章编辑后不改变首次发布时间。
- 作者卡显示公开文章、已被公开文章使用的分类、标签，以及公开资源数量。空分类和仅用于草稿的分类/标签不计入；筛选文章不会改变全站统计。

封面暂时使用完整HTTP/HTTPS地址，不增加上传接口或图片库。地址不能包含账号密码；浏览器直接加载图片，后端不会下载它。图片地址格式合法不代表图片必然存在。建议使用自己控制的HTTPS地址；这里只验证显示所需格式，不保证任意外站都允许引用。

更新时间旧值没有可靠来源，所以旧文章保持NULL，在首次新保存时记录真实时间。它表示“最近保存或首次发布”，不是逐字符编辑历史。

## 2. 修改前与文件顺序

先删除 `E:\PersonalWebsite2\frontend\src\components\SiteHeader.vue` 文件末尾、`</template>` 后多出的三个反引号。只删除那一行。

按照已有备份指南备份个人库，再停掉后端，执行数据库升级和Java修改。前端可以在Java修改完成后一起重启。不要在只执行一半时判断接口是否可用。

路径均以 `E:\PersonalWebsite2` 为根目录：

| 顺序 | 文件 | 操作 |
| --- | --- | --- |
| 1 | database/005-article-presentation.sql | 新建并在确认库名后执行一次 |
| 2 | personalwebsite/src/main/java/com/firefly/personalwebsite/AdminArticleController.java | 整个文件替换 |
| 3 | personalwebsite/src/main/java/com/firefly/personalwebsite/ArticleController.java | 整个文件替换 |
| 4 | personalwebsite/src/main/java/com/firefly/personalwebsite/SiteStatsController.java | 新建 |
| 5 | frontend/src/components/ArticleCard.vue | 整个文件替换 |
| 6 | frontend/src/components/SiteStats.vue | 新建 |
| 7 | frontend/src/views/AdminView.vue | 按4处明确位置修改 |
| 8 | frontend/src/views/ArticleListView.vue | 增加1个import和1处组件引用 |
| 9 | frontend/src/v2.css | 文件末尾追加样式 |

不改pom.xml、package.json、安全配置或原有数据库连接配置，不安装新依赖。SQL字段使用下划线，JSON字段使用驼峰：cover_url→coverUrl、updated_at→updatedAt。

**代码块外围的三个反引号和 `<!-- file: … -->` 文件定位标记都不是代码，不要输入IDEA。SQL文件第一行应以 `--` 开始。** Java文件给出完整版本，是为了避免record参数、SQL字段、占位符数量和构造方法之间出现漏改；不要将它们追加到旧类末尾。

## 3. 数据库：新增三列

在IDEA数据库控制台先执行：

```sql
SELECT DATABASE();
SHOW COLUMNS FROM article;
```

第一条应返回 `personal_website`。如果不是，先执行 `USE personal_website;` 再核对。若已经存在cover_url、pinned、updated_at，先核查是否完整应用过本节，不能重复执行ALTER TABLE，也不要删除原表。

新建 `database/005-article-presentation.sql`，写入以下代码，再在已确认的数据库中执行一次。MySQL的DDL会隐式提交，不能把“ROLLBACK”当作本次结构升级的撤销方法；先确认备份完成。

<!-- file: database/005-article-presentation.sql -->
```sql
-- 第二轮B1升级。先备份 personal_website；本脚本只执行一次。
-- IDEA控制台先执行 SELECT DATABASE(); 确认当前库为 personal_website。
-- 旧文章没有可追溯的修改时间，因此保留NULL，不伪造历史更新时间。
ALTER TABLE article
    ADD COLUMN cover_url VARCHAR(2048) NULL COMMENT '可选HTTP/HTTPS封面地址',
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否置顶',
    ADD COLUMN updated_at DATETIME(6) NULL COMMENT '最近保存或首次发布的时间；旧记录未知';
```

执行后检查：

```sql
SHOW COLUMNS FROM article;
SELECT id, title, cover_url, pinned, updated_at FROM article ORDER BY id;
```

旧文章应显示cover_url=NULL、pinned=0、updated_at=NULL，原有标题、正文、状态和发布时间保持原值。新安装仍先按README执行000～004，再执行005；本轮实际源码验收时助手会同步README正式安装步骤。

## 4. 替换后台文章控制器

文件：`personalwebsite/src/main/java/com/firefly/personalwebsite/AdminArticleController.java`。

从package第一行到最后一个右花括号整体替换。重点看三处：请求record的可选字段；INSERT/UPDATE占位符；validateCoverUrl方法。保存正文、标签和新增字段仍在同一事务里，标签校验失败会一起回滚。

<!-- file: personalwebsite/src/main/java/com/firefly/personalwebsite/AdminArticleController.java -->
```java
package com.firefly.personalwebsite;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.sql.Types;
import java.net.URI;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.sql.Statement;
import java.util.List;

@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final JdbcTemplate jdbcTemplate;
    private final ArticleTags articleTags;

    // Spring 会自动提供数据库操作对象和文章标签对象。
    public AdminArticleController(
            JdbcTemplate jdbcTemplate, ArticleTags articleTags) {
        this.jdbcTemplate = jdbcTemplate;
        this.articleTags = articleTags;
    }

    //// 请求只包含文章内容，草稿状态和发布时间由后端决定。
    public record DraftRequest(
            @NotBlank @Size(max = 200)
            String title,

            @NotNull @Size(max = 500)
            String summary,

            @NotBlank
            String contentMarkdown,

            @NotBlank @Pattern(regexp = "NOTE|THOUGHT|DIARY")
            String type,

            // null 表示未分类；填写编号时必须大于 0。
            @Positive
            Long categoryId,
            // 每篇最多选择 20 个标签；数组中的每个编号都必须非空且大于 0。
            @Size(max = 20)
            List<@NotNull @Positive Long> tagIds,
            // 封面可留空；后端仍需校验协议，不能只依赖浏览器的type="url"。
            @Size(max = 2048) String coverUrl,
            // 兼容旧请求：未传置顶字段时按false处理。
            Boolean pinned
    ) {}

    // 新建和发布都返回文章编号与当前状态。
    public record ArticleResult(long id, String status) {}

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ArticleResult createDraft(
            @Valid @RequestBody DraftRequest request
    ) {
        checkCategory(request.categoryId());
        String coverUrl = validateCoverUrl(request.coverUrl());
        String sql = """
        INSERT INTO article
            (title, summary, content_markdown, type,
             category_id, status, published_at, cover_url, pinned, updated_at)
        VALUES (?, ?, ?, ?, ?, 'DRAFT', NULL, ?, ?, CURRENT_TIMESTAMP(6))
        """;
        // 保存 MySQL 返回的自增编号。
        // 编号与 INSERT 来自同一次操作，避免从连接池取到另一条连接。

        var keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection ->{
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            // 问号占位符的编号从 1 开始
            statement.setString(1, request.title().strip());
            statement.setString(2, request.summary().strip());
            statement.setString(3, request.contentMarkdown());
            statement.setString(4, request.type());

            // SQL 的第五个占位符对应 category_id。
            if (request.categoryId() == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, request.categoryId());
            }

            statement.setString(6, coverUrl);
            statement.setBoolean(7, Boolean.TRUE.equals(request.pinned()));
            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            // 抛出异常时，事务会回滚刚才的 INSERT
            throw new IllegalStateException("数据库未返回文章编号");
        }

        // 保存刚创建的文章与所选标签之间的关联。
        articleTags.replace(id.longValue(), request.tagIds());
        return new ArticleResult(id.longValue(), "DRAFT");

    }

    @PostMapping("/{id}/publish")
    public ArticleResult publishDraft(@PathVariable("id") long id) {

        String sql = """
                UPDATE article
                SET updated_at = CASE WHEN status = 'DRAFT'
                                      THEN CURRENT_TIMESTAMP(6) ELSE updated_at END,
                    published_at = COALESCE(published_at, CURRENT_TIMESTAMP),
                    status = 'PUBLISHED'
                WHERE id = ?
                """;
        // COALESCE 在原发布时间为空时设置时间。
        // 重复点击发布会保留第一次发布和最近修改的时间。
        // updated_at先判断旧status，再修改status，避免MySQL赋值顺序影响判断。
        int updated = jdbcTemplate.update(sql, id);

        if (updated == 0) {
            // 某些数据库配置下，更新相同值也可能返回 0。
            // 再确认记录是否存在，避免误报“文章不存在”。
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM article WHERE id = ?",
                    Integer.class,
                    id);

            if (count == null || count == 0) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "文章不存在");
            }
        }
        return new ArticleResult(id, "PUBLISHED");
    }

    // 列表不返回完整正文，减少传输的数据量。
    public record AdminSummary(
            long id,
            String title,
            String type,
            String status,
            boolean pinned
    ){}

    public record AdminDetail(
            long id,
            String title,
            String summary,
            String contentMarkdown,
            String type,
            String status,
            Long categoryId,
            List<Long> tagIds,
            String coverUrl,
            boolean pinned,
            LocalDateTime updatedAt
    ) {}

    @GetMapping
    public List<AdminSummary> list() {
        // 管理员可以看到草稿和已发布文章。
        String sql = """
                SELECT id, title, type, status, pinned
                FROM article
                ORDER BY id DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getBoolean("pinned")
        ));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminDetail detail(@PathVariable("id") long id) {
        String sql = """
        SELECT id, title, summary, content_markdown,
               type, status, category_id, cover_url, pinned, updated_at
        FROM article
        WHERE id = ?
        """;

        AdminDetail article = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }

            return new AdminDetail(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("content_markdown"),
                    rs.getString("type"),
                    rs.getString("status"),

                    // getObject 保留 SQL NULL；不能用 getLong 将其读成 0。
                    rs.getObject("category_id", Long.class),
                    articleTags.read(id).stream()
                            .map(ArticleTags.TagItem::id)
                            .toList(),
                    rs.getString("cover_url"),
                    rs.getBoolean("pinned"),
                    // 旧文章的更新时间允许为空，getObject保留null。
                    rs.getObject("updated_at", LocalDateTime.class)
            );
        }, id);

        if (article == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @Transactional
    public AdminDetail update(
            @PathVariable("id") long id,
            @Valid @RequestBody DraftRequest request) {

        // 先确认文章存在；不存在时返回 404，避免插入无效关联。
        detail(id);
        checkCategory(request.categoryId());
        String coverUrl = validateCoverUrl(request.coverUrl());

        String sql = """
            UPDATE article
            SET title = ?, summary = ?, content_markdown = ?,
                type = ?, category_id = ?, cover_url = ?, pinned = ?,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = ?
            """;

        // 保存内容时保留文章状态和第一次发布时间。
        jdbcTemplate.update(sql,
                request.title().strip(),
                request.summary().strip(),
                request.contentMarkdown(),
                request.type(),
                request.categoryId(),
                coverUrl,
                Boolean.TRUE.equals(request.pinned()),
                id);

        // 标签无效时抛出异常，刚才的文章内容更新也会回滚。
        articleTags.replace(id, request.tagIds());
        return detail(id);
    }

    // 后端只保存地址，不下载图片；真正显示时由访客浏览器请求图片。
    // 空字符串统一存NULL，前端使用栏目默认图；不要求URL以.jpg/.png结尾。
    private String validateCoverUrl(String input) {
        if (input == null || input.isBlank()) return null;
        String value = input.strip();
        try {
            URI uri = URI.create(value);
            boolean supported = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            // 拒绝相对路径、无主机、带账号密码和其他协议的地址。
            if (supported && uri.getHost() != null && uri.getRawUserInfo() == null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // 地址解析失败，按同一种输入错误返回。
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "封面必须为空或不含账号密码的完整HTTP/HTTPS地址");
    }

    private void checkCategory(Long categoryId) {
        // 允许文章不设置分类。
        if (categoryId == null) return;

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = ?",
                Integer.class,
                categoryId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "所选分类不存在"
            );
        }
    }
}
```

## 5. 替换公开文章控制器

文件：`personalwebsite/src/main/java/com/firefly/personalwebsite/ArticleController.java`，整个文件替换。

公开接口只增加字段，已有items/total/page/size/totalPages结构不变。列表查询、标签补齐后的record重建、详情查询都必须传入新字段；漏改任意一处都会导致字段丢失或编译错误。仅返回PUBLISHED的条件保留。

<!-- file: personalwebsite/src/main/java/com/firefly/personalwebsite/ArticleController.java -->
```java
package com.firefly.personalwebsite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final JdbcTemplate jdbcTemplate;
    private final ArticleTags articleTags;

    public ArticleController(
            JdbcTemplate jdbcTemplate, ArticleTags articleTags) {
        this.jdbcTemplate = jdbcTemplate;
        this.articleTags = articleTags;
    }

    public record ArticleSummary(
            long id,
            String title,
            String summary,
            String type,
            LocalDateTime publishedAt,
            Long categoryId,
            String categoryName,
            List<ArticleTags.TagItem> tags,
            String coverUrl,
            boolean pinned,
            LocalDateTime updatedAt
    ) {}

    public record ArticleDetail(
            long id,
            String title,
            String summary,
            String contentMarkdown,
            String type,
            LocalDateTime publishedAt,
            Long categoryId,
            String categoryName,
            List<ArticleTags.TagItem> tags,
            String coverUrl,
            boolean pinned,
            LocalDateTime updatedAt
    ) {}

    // items 是本页文章；total 是满足相同筛选条件的文章总数。
    public record ArticlePage(
            List<ArticleSummary> items,
            long total,
            int page,
            int size,
            long totalPages
    ) {}

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

    @GetMapping
    @Transactional(readOnly = true)
    public ArticlePage list(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "tagId", required = false) Long tagId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "month", required = false) String month,
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

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + fromWhere, Long.class, parameters.toArray());
        long totalPages = (total + size - 1) / size;
        // 超出末页时回到最后一页；前端以响应中的 page 为准。
        int actualPage = total == 0 ? 1 : (int) Math.min(page, totalPages);
        long offset = (long) (actualPage - 1) * size;

        String sql = """
            SELECT a.id, a.title, a.summary, a.type,
                   a.published_at, a.category_id, a.cover_url, a.pinned, a.updated_at,
                   c.name AS category_name
            """ + fromWhere;
        // 先在数据库里排序，再分页；不能只对取回的当前页排序。
        // 归档按发布时间浏览；首页及普通筛选优先显示置顶文章。
        sql += month == null
                ? " ORDER BY a.pinned DESC, a.published_at DESC, a.id DESC"
                : " ORDER BY a.published_at DESC, a.id DESC";
        sql += " LIMIT ? OFFSET ?";
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
                        List.of(),
                        rs.getString("cover_url"),
                        rs.getBoolean("pinned"),
                        rs.getObject("updated_at", LocalDateTime.class)),
                pageParameters.toArray());

        // 只批量查询本页文章的标签；筛选某个标签时仍返回每篇的全部标签。
        var tagsByArticle = articleTags.readFor(
                articles.stream().map(ArticleSummary::id).toList());
        var items = articles.stream().map(a -> new ArticleSummary(
                a.id(), a.title(), a.summary(), a.type(), a.publishedAt(),
                a.categoryId(), a.categoryName(),
                tagsByArticle.getOrDefault(a.id(), List.of()),
                a.coverUrl(), a.pinned(), a.updatedAt()
        )).toList();
        return new ArticlePage(items, total, actualPage, size, totalPages);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ArticleDetail detail(@PathVariable("id") long id) {
        String sql = """
                SELECT a.id, a.title, a.summary, a.content_markdown,
                       a.type, a.published_at, a.category_id, a.cover_url, a.pinned, a.updated_at,
                       c.name AS category_name
                FROM article a
                LEFT JOIN category c ON c.id = a.category_id
                WHERE a.id = ? AND a.status = 'PUBLISHED'
                """;

        List<ArticleDetail> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ArticleDetail(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("summary"),
                        rs.getString("content_markdown"),
                        rs.getString("type"),
                        rs.getTimestamp("published_at").toLocalDateTime(),
                        rs.getObject("category_id", Long.class),
                        rs.getString("category_name"),
                        articleTags.read(id),
                        rs.getString("cover_url"),
                        rs.getBoolean("pinned"),
                        rs.getObject("updated_at", LocalDateTime.class)
                ),
                id
        );

        // 草稿和不存在的编号均返回 404。
        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "文章不存在"
            );
        }

        return rows.get(0);
    }
}
```

## 6. 新建公开统计控制器

与两个文章控制器放在同一个包内，新建 `SiteStatsController.java`。

`GET /api/site-stats` 是公开只读接口。分类和标签数量按公开文章去重，不使用分类表/标签表的全部行数。返回示例：`{"articles":3,"categories":0,"tags":0,"resources":3}`，实际数字由你的数据决定。

<!-- file: personalwebsite/src/main/java/com/firefly/personalwebsite/SiteStatsController.java -->
```java
package com.firefly.personalwebsite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteStatsController {
    private final JdbcTemplate jdbcTemplate;

    public SiteStatsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SiteStats(long articles, long categories, long tags, long resources) {}

    @GetMapping("/api/site-stats")
    public SiteStats stats() {
        // 四个统计来自同一条SQL；分类/标签只计算关联到公开文章的项目。
        // 草稿、空分类、仅用于草稿的标签、隐藏资源均不计入。
        // 不返回管理员总数后再让前端相减，否则会泄露隐藏内容数量。
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM article WHERE status = 'PUBLISHED') AS articles,
                  (SELECT COUNT(DISTINCT category_id) FROM article
                   WHERE status = 'PUBLISHED') AS categories,
                  (SELECT COUNT(DISTINCT atg.tag_id) FROM article_tag atg
                   JOIN article a ON a.id = atg.article_id
                   WHERE a.status = 'PUBLISHED') AS tags,
                  (SELECT COUNT(*) FROM resource_link WHERE published = TRUE) AS resources
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new SiteStats(
                rs.getLong("articles"), rs.getLong("categories"),
                rs.getLong("tags"), rs.getLong("resources")));
    }
}
```

现在可以先用Java 17启动后端，访问以下地址：

- http://localhost:8080/api/site-stats
- http://localhost:8080/api/articles
- http://localhost:8080/api/articles/1 （没有编号1时换成自己的公开文章编号）

如果报Unknown column，先核对005是否在应用连接的personal_website中执行，不要修改数据库名来绕过错误。旧文章updatedAt=null属于正常结果。

## 7. 替换文章卡片

文件：`frontend/src/components/ArticleCard.vue`，整个文件替换。

图片error事件只改变展示回退状态，不修改数据库里的封面地址；更换地址后会再次尝试。外站图片请求不带本站Referer。更新时间为空时隐藏“更新于”，不会使用今天的日期代替。

<!-- file: frontend/src/components/ArticleCard.vue -->
```vue
<script setup>
import { computed, ref, watch } from 'vue'
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
// 自定义封面加载失败时回退到栏目图；切换地址后允许重新尝试。
const coverFailed = ref(false)
watch(() => props.article.coverUrl, () => { coverFailed.value = false })
const cover = computed(() =>
  !coverFailed.value && props.article.coverUrl
    ? props.article.coverUrl
    : (covers[props.article.type] ?? noteCover)
)
const path = computed(() => `/articles/${props.article.id}`)
</script>

<template>
  <article class="v2-post" :class="{ featured }">
    <RouterLink class="v2-cover" :to="path" :aria-label="`阅读：${article.title}`">
      <!-- 固定栏目图是装饰；文章标题已由链接的无障碍名称提供。 -->
      <img :src="cover" alt="" width="480" height="300" :loading="featured ? 'eager' : 'lazy'"
           referrerpolicy="no-referrer" @error="coverFailed = true">
    </RouterLink>
    <div class="v2-post-body">
      <div class="v2-meta"><span v-if="article.pinned" class="v2-pin">置顶</span><span class="v2-badge">{{ names[article.type] ?? '文章' }}</span><span>{{ article.categoryName ?? '未分类' }}</span></div>
      <h3><RouterLink :to="path">{{ article.title }}</RouterLink></h3>
      <p class="v2-summary">{{ article.summary || '打开文章，继续阅读。' }}</p>
      <time class="v2-meta" :datetime="article.publishedAt">{{ article.publishedAt?.slice(0, 10) ?? '发布时间未记录' }}</time>
      <time v-if="article.updatedAt" class="v2-meta" :datetime="article.updatedAt">
        更新于 {{ article.updatedAt.slice(0, 10) }}
      </time>
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

## 8. 新建统计组件

文件：`frontend/src/components/SiteStats.vue`。

统计失败不应影响文章列表，也不能把失败误显示成全站0篇。组件只负责自己的加载、失败、重试及离开页面后的请求失效。

<!-- file: frontend/src/components/SiteStats.vue -->
```vue
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const stats = ref(null)
const loading = ref(false)
const error = ref('')
let disposed = false
onBeforeUnmount(() => { disposed = true })

async function loadStats() {
  // 同一时间只发一次请求，重复点击重试不会引起响应竞争。
  if (loading.value || disposed) return
  loading.value = true
  error.value = ''
  try {
    const response = await fetch('/api/site-stats')
    if (!response.ok) throw new Error('统计暂时无法加载')
    const data = await response.json()
    if (!disposed) stats.value = data
  } catch {
    if (!disposed) {
      stats.value = null
      error.value = '统计暂时无法加载'
    }
  } finally {
    if (!disposed) loading.value = false
  }
}
onMounted(loadStats)
</script>

<template>
  <section class="v2-stats" aria-label="公开内容统计" :aria-busy="loading">
    <p v-if="loading" role="status">正在读取公开内容统计…</p>
    <div v-else-if="error" role="alert">
      <p>{{ error }}</p>
      <button type="button" @click="loadStats">重试统计</button>
    </div>
    <!-- 失败不能展示成0；只有成功取得统计对象时才显示数字。 -->
    <dl v-else-if="stats">
      <div><dt>文章</dt><dd>{{ stats.articles }}</dd></div>
      <div><dt>分类</dt><dd>{{ stats.categories }}</dd></div>
      <div><dt>标签</dt><dd>{{ stats.tags }}</dd></div>
      <div><dt>资源</dt><dd>{{ stats.resources }}</dd></div>
    </dl>
  </section>
</template>
```

## 9. 修改后台表单：仅4处

文件：`frontend/src/views/AdminView.vue`。不用整个文件重写。

### 9.1 emptyArticle()的初始字段

在script顶部找到 `function emptyArticle()`，在其返回对象的 `summary: '',` 后面、`contentMarkdown: '',` 前面插入：

```javascript
    coverUrl: '', // 空地址表示使用栏目默认图。
    pinned: false,
```

这保证“新文章”会清空上一条文章的封面和置顶状态。不要改到下面的emptyResource函数。

### 9.2 fillForm(article)的回显

在 `function fillForm(article)` 内，找到：

```javascript
  form.tagIds = [...(article.tagIds ?? [])]
```

紧接着插入这一行，必须放在 `savedSnapshot.value = JSON.stringify(form)` 之前：

```javascript
  form.coverUrl = article.coverUrl ?? ''
```

这样数据库NULL会变成输入框可编辑的空字符串，也不会让刚打开的文章误判为未保存。

### 9.3 persist()的请求体

在 `async function persist()` 中找到 `body: JSON.stringify({`，在里面的 `summary: form.summary,` 后面插入：

```javascript
          coverUrl: form.coverUrl.trim() || null,
          pinned: form.pinned,
```

保存草稿、编辑已发布文章、首次发布都会经过persist；只在某一个按钮函数里加字段会漏掉其他路径。

### 9.4 插入输入控件

在template中找到下面这块“摘要”，它位于“文章标签”div结束之后、“Markdown正文”区域之前：

```vue
          <label>
            摘要
            <textarea v-model="form.summary" maxlength="500" rows="3"></textarea>
          </label>
```

**在这块label的前面插入**下方内容。摘要本身保留，插入位置仍在原有文章表单fieldset内，因此保存期间会随其他输入一起禁用。

```vue
          <label>
            封面地址（可选）
            <input v-model="form.coverUrl" type="url" maxlength="2048"
                   placeholder="https://example.com/cover.jpg" />
          </label>
          <p class="hint">留空使用栏目配图；图片无法加载时也会自动回退。建议使用稳定的HTTPS图片地址。</p>
          <label class="presentation-pin">
            <input v-model="form.pinned" type="checkbox" />置顶这篇文章
          </label>
          <p class="hint">置顶只影响公开列表顺序，勾选不会自动发布草稿。</p>
```

“置顶”勾选不会改变草稿状态。封面输入框的type=url只做浏览器基础校验，后端仍拒绝ftp、javascript、data等协议。

## 10. 将统计接入作者卡

文件：`frontend/src/views/ArticleListView.vue`。

在script顶部现有 `import ArticleCard from '../components/ArticleCard.vue'` 后插入：

```javascript
import SiteStats from '../components/SiteStats.vue'
```

在template作者卡中找到 `<p>{{ siteProfile.profileDescription }}</p>`，紧接着插入：

```vue
          <SiteStats />
```

不要放到v-for文章卡片内，否则每篇文章都会重复请求统计。组件不使用当前列表的total，所以搜索、切换栏目不会错误地改变全站数字。

## 11. 追加样式

在 `frontend/src/v2.css` 最后一个右花括号之后追加，不要粘进某个@media块内部：

```css
/* 第二轮B1：真实公开统计、置顶标记和管理表单。 */
.v2-stats { margin: 18px 0; border-block: 1px solid var(--color-border); padding: 14px 0; }
.v2-stats dl { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 0; text-align: center; }
.v2-stats dt { font-size: 12px; color: var(--color-muted); }
.v2-stats dd { margin: 4px 0 0; font-size: 18px; color: var(--color-text); overflow-wrap: anywhere; }
.v2-pin { color: var(--color-accent); border: 1px solid var(--color-accent); border-radius: 5px; padding: 2px 6px; }
.admin-layout .presentation-pin { display: flex; align-items: center; gap: 10px; min-height: 44px; }
.presentation-pin input { width: 18px; min-height: 18px; margin: 0; }
```

## 12. 运行与手动检查

后端在IDEA中使用Java 17启动；前端终端进入frontend后运行 `npm run dev`，以终端实际打印的端口为准。页面仍通过Vite代理请求后端8080。

| 操作 | 预期 |
| --- | --- |
| 打开旧文章 | 栏目默认封面；历史更新时间未知时不显示“更新于” |
| 新建草稿、填封面、勾置顶并保存，再重新打开 | 封面和置顶回显正确；访客仍看不到草稿 |
| 发布该文章 | 首页可见，显示“置顶”、封面和真实更新时间 |
| 将较旧的公开文章置顶 | 普通列表第一页排到非置顶文章之前，分类/标签/搜索筛选仍生效 |
| 取消置顶并保存 | 恢复发布时间顺序；归档始终按时间显示 |
| 清空封面后保存 | 恢复栏目图；数据库该列为NULL |
| 填写合法但不存在的图片地址 | 保存成功，首页图片加载失败后回退；这不是URL格式校验失败 |
| 填写ftp://example.com/cover.png并保存 | 后端400，页面提示输入校验失败；旧保存内容不变 |
| 编辑已发布文章并保存 | publishedAt不变，updatedAt变为本次保存时间 |
| 新增草稿、空分类、仅供草稿的标签、隐藏资源 | 公开统计不增加 |
| 公开两篇使用相同分类/标签的文章 | 文章+2，该分类/标签各只计1项 |
| 搜索缩小列表到1篇 | 作者卡仍显示全站公开总数 |
| 390px、320px屏幕 | 卡片、统计和新增表单无整体横向溢出 |

只保存而不发布的新文章保持DRAFT；修改已发布文章仍是直接更新公开内容，行为与现有后台一致。更新时间只显示日期，同一天保存可能看不出卡片变化，可以查看接口的完整updatedAt。

生产构建检查在frontend目录运行：

```powershell
npm run build
```

测试由助手负责。完成后回复“B1完成，检查验收”，我会检查你实际写入的文件，并在独立测试库复查保存、发布、排序、回退和统计隔离，不需要你手敲测试代码。

## 13. 预检记录与下一步

2026-10-16预检通过，范围如下（均为指南隔离副本，不代表用户实际源码已完成）：

| 检查 | 结果 |
| --- | --- |
| Java 17 / 真实MySQL后端测试 | 15项通过：原11项加展示字段、置顶分页、公开统计、旧文章兼容4项 |
| 原有前端回归 | 11项通过 |
| 指南组件检查 | 3项通过：Vue编译、封面回退/换地址、统计失败/重试/卸载保护 |
| 两端构建 | Maven package、Vite生产构建通过 |
| 独立服务HTTP | 8组检查通过，包括空库统计、12篇10＋2分页、置顶、草稿404、公开统计及失败写入完整回滚 |
| 浏览器流程 | 自定义图、失效图回退、真实置顶标记、翻页、封面/置顶保存回显、FTP拒绝、清空封面/取消置顶后发布通过 |
| 统计故障恢复 | 临时预览代理让统计接口返回503；文章列表仍正常，重试恢复；搜索1篇时全站统计仍为13篇 |
| 响应式 | 首页和登录后后台在320/390/768/1440px共8组无整体横向溢出；已修正旧表单样式覆盖新复选框间距的问题 |

预检使用临时库 `pw_v2b1_preflight_20261016`，以及专用18082/5178服务。测试环境及含连接凭据的临时配置/打包文件已清理；只读确认个人库尚未添加本节三个字段。桌面效果和手机表单截图保留在Git忽略的 `artifacts/v2-metadata-preflight/`，未覆盖实体手机或其他浏览器。

测试中一次回滚断言受到测试方法外层事务影响：测试事务结束前还能看到待回滚的数据。已将这项断言移到真实HTTP独立请求，确认无效标签导致标题、封面、置顶和更新时间全部回滚。没有为了让测试通过而取消业务事务。

用于实际实现后的回归测试保存在 `docs/checks/PresentationFlowTests.java`，届时由助手纳入后端测试目录；现在不要运行未迁移数据库上的新字段测试。指南的组件检查可以独立运行：

```powershell
node --test docs/checks/v2-metadata-guide.test.mjs
```


B1验收后进入B2：让栏目、分类、标签、关键词和页码写入网址，刷新/前进后退恢复条件，从正文返回列表恢复位置。B1不是新Release节点，整个第二轮完成后再评估发布。
