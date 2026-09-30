package com.firefly.personalwebsite;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.beans.Transient;
import java.sql.Statement;
import java.util.List;

@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final JdbcTemplate jdbcTemplate;

    public AdminArticleController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 请求只包含文章内容，草稿状态和发布时间由后端决定。
    public record DraftRequest(
            @NotBlank @Size(max = 200)
            String title,

            @NotNull @Size(max = 500)
            String summary,

            @NotBlank
            String contentMarkdown,

            @NotBlank @Pattern(regexp = "NOTE|THOUGHT|DIARY")
            String type
    ){}

    // 新建和发布都返回文章编号与当前状态。
    public record ArticleResult(long id, String status) {}

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ArticleResult createDraft(
            @Valid @RequestBody DraftRequest request
    ) {
        String sql = """
                INSERT INTO article
                    (title, summary, content_markdown,type,status,published_at)
                VALUES (?, ?, ?, ?, 'DRAFT', NULL)
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

            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            // 抛出异常时，事务会回滚刚才的 INSERT
            throw new IllegalStateException("数据库未返回文章编号");
        }

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
            String status
    ){}

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
    public AdminDetail detail(@PathVariable("id") long id) {
        String sql = """
                SELECT id, title, summary, content_markdown, type, status
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
                    rs.getString("status")
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
        // 修改内容时保留文章状态和第一次发布时间。
        String sql = """
                UPDATE article
                SET title = ?, summary = ?, content_markdown = ?, type = ?
                WHERE id = ?
                """;
        jdbcTemplate.update(
                sql,
                request.title().strip(),
                request.summary().strip(),
                request.contentMarkdown(),
                request.type(),
                id
        );
        // 返回保存后的内容，同时确认文章存在。
        // 即使保存的内容没有变化，也能正常返回结果。
        return detail(id);
    }
}
