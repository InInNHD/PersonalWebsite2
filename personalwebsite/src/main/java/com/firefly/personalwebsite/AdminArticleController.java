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
import java.util.ArrayList;
import java.util.Set;

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

    // 所有状态写入先锁定同一行，避免保存/发布与回收并发时越过隔离规则。
    private AdminDetail lockArticle(long id) {
        var ids = jdbcTemplate.queryForList("SELECT id FROM article WHERE id = ? FOR UPDATE", Long.class, id);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
        return detail(id);
    }

    private void requireActive(AdminDetail article) {
        if (article.deletedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先从回收站恢复文章");
    }

    @PostMapping("/{id}/publish")
    @Transactional
    public ArticleResult publishDraft(@PathVariable("id") long id) {
        var article = lockArticle(id);
        requireActive(article);
        jdbcTemplate.update("""
                UPDATE article SET updated_at = CASE WHEN status = 'DRAFT' THEN CURRENT_TIMESTAMP(6) ELSE updated_at END,
                    published_at = COALESCE(published_at, CURRENT_TIMESTAMP), status = 'PUBLISHED'
                WHERE id = ? AND deleted_at IS NULL
                """, id);
        return new ArticleResult(id, "PUBLISHED");
    }

    @PostMapping("/{id}/unpublish")
    @Transactional
    public ArticleResult unpublish(@PathVariable("id") long id) {
        requireActive(lockArticle(id));
        jdbcTemplate.update("UPDATE article SET status='DRAFT', updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL", id);
        return new ArticleResult(id, "DRAFT");
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ArticleResult recycle(@PathVariable("id") long id) {
        var article = lockArticle(id);
        jdbcTemplate.update("UPDATE article SET deleted_at=CURRENT_TIMESTAMP(6), updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND deleted_at IS NULL", id);
        return new ArticleResult(id, article.status());
    }

    @PostMapping("/{id}/restore")
    @Transactional
    public ArticleResult restore(@PathVariable("id") long id) {
        var article = lockArticle(id);
        // 重复恢复不改变已恢复文章的后续状态，避免第二次请求意外撤回公开文章。
        if (article.deletedAt() != null) {
            jdbcTemplate.update("UPDATE article SET deleted_at=NULL, status='DRAFT', updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
            return new ArticleResult(id, "DRAFT");
        }
        return new ArticleResult(id, article.status());
    }

    // 列表不返回完整正文，减少传输的数据量。
    public record AdminSummary(long id, String title, String type, String status,
                               boolean pinned, Long categoryId, String categoryName,
                               LocalDateTime updatedAt, LocalDateTime deletedAt) {}
    public record AdminPage(List<AdminSummary> items, long total, int page, int size, long totalPages) {}

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
            LocalDateTime updatedAt,
            LocalDateTime deletedAt
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public AdminPage list(
            @RequestParam(name="keyword", defaultValue="") String keyword,
            @RequestParam(name="status", required=false) String status,
            @RequestParam(name="type", required=false) String type,
            @RequestParam(name="categoryId", required=false) Long categoryId,
            @RequestParam(name="recycled", defaultValue="false") boolean recycled,
            @RequestParam(name="page", defaultValue="1") int page,
            @RequestParam(name="size", defaultValue="10") int size) {
        String search = keyword.strip();
        if (page < 1 || page > 1_000_000 || size < 1 || size > 50 || search.length() > 100
                || (status != null && !Set.of("DRAFT", "PUBLISHED").contains(status))
                || (type != null && !Set.of("NOTE", "THOUGHT", "DIARY").contains(type))
                || (categoryId != null && categoryId <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文章筛选或分页参数不合法");
        }
        String from = " FROM article a LEFT JOIN category c ON c.id=a.category_id WHERE a.deleted_at IS " + (recycled ? "NOT NULL" : "NULL");
        var parameters = new ArrayList<Object>();
        if (!search.isEmpty()) { from += " AND (LOCATE(?, a.title)>0 OR LOCATE(?, a.summary)>0)"; parameters.add(search); parameters.add(search); }
        if (status != null) { from += " AND a.status=?"; parameters.add(status); }
        if (type != null) { from += " AND a.type=?"; parameters.add(type); }
        if (categoryId != null) { from += " AND a.category_id=?"; parameters.add(categoryId); }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from, Long.class, parameters.toArray());
        long pages = (total + size - 1) / size;
        int actualPage = (int) Math.min(page, Math.max(1, pages));
        parameters.add(size); parameters.add((long) (actualPage - 1) * size);
        String sql = "SELECT a.id,a.title,a.type,a.status,a.pinned,a.category_id,c.name category_name,a.updated_at,a.deleted_at" + from
                + (recycled ? " ORDER BY a.deleted_at DESC,a.id DESC" : " ORDER BY a.id DESC") + " LIMIT ? OFFSET ?";
        var items = jdbcTemplate.query(sql, (rs, rowNum) -> new AdminSummary(rs.getLong("id"), rs.getString("title"),
                rs.getString("type"), rs.getString("status"), rs.getBoolean("pinned"), rs.getObject("category_id",Long.class),
                rs.getString("category_name"), rs.getObject("updated_at",LocalDateTime.class), rs.getObject("deleted_at",LocalDateTime.class)), parameters.toArray());
        return new AdminPage(items,total,actualPage,size,pages);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminDetail detail(@PathVariable("id") long id) {
        String sql = """
        SELECT id, title, summary, content_markdown,
               type, status, category_id, cover_url, pinned, updated_at, deleted_at
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
                    rs.getObject("updated_at", LocalDateTime.class),
                    rs.getObject("deleted_at", LocalDateTime.class)
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
        requireActive(lockArticle(id));
        checkCategory(request.categoryId());
        String coverUrl = validateCoverUrl(request.coverUrl());

        String sql = """
            UPDATE article
            SET title = ?, summary = ?, content_markdown = ?,
                type = ?, category_id = ?, cover_url = ?, pinned = ?,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = ? AND deleted_at IS NULL
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