package com.firefly.personalwebsite;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.sql.Types;
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
            List<@NotNull @Positive Long> tagIds
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
        String sql = """
        INSERT INTO article
            (title, summary, content_markdown, type,
             category_id, status, published_at)
        VALUES (?, ?, ?, ?, ?, 'DRAFT', NULL)
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
                SET status = 'PUBLISHED',
                    published_at = COALESCE(published_at, CURRENT_TIMESTAMP)
                WHERE id = ?
                """;
        // COALESCE 在原发布时间为空时设置时间。
        // 重复点击发布会保留第一次发布的时间。
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
            String status
    ){}

    public record AdminDetail(
            long id,
            String title,
            String summary,
            String contentMarkdown,
            String type,
            String status,
            Long categoryId,
            List<Long> tagIds
    ) {}

    @GetMapping
    public List<AdminSummary> list() {
        // 管理员可以看到草稿和已发布文章。
        String sql = """
                SELECT id, title, type, status
                FROM article
                ORDER BY id DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("status")
        ));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminDetail detail(@PathVariable("id") long id) {
        String sql = """
        SELECT id, title, summary, content_markdown,
               type, status, category_id
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
                            .toList()
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
