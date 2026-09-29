package com.firefly.personalwebsite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/articles")

public class ArticleController {

    private final JdbcTemplate jdbcTemplate;

    public ArticleController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

public record ArticleSummary(
        long id,
        String title,
        String summary,
        String type,
        LocalDateTime publishedAt
) {}

    public record ArticleDetail(
            long id,
            String title,
            String summary,
            String contentMarkdown,
            String type,
            LocalDateTime publishedAt
    ) {}

    @GetMapping
    public List<ArticleSummary> list(
            @RequestParam(required = false) String type
    ) {
        String sql = """
                SELECT id, title, summary, type, published_at
                FROM article
                WHERE status = 'PUBLISHED'
                """;

        if (type != null) {
            if (!Set.of("NOTE","THOUGHT","DIARY").contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文章类型");
            }
            sql += " AND type = ?";
        }
        sql += " ORDER BY published_at DESC, id DESC LIMIT 20";

        if(type == null) {
            return jdbcTemplate.query(sql, (rs, rowNum) -> new ArticleSummary(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("type"),
                    rs.getTimestamp("published_at").toLocalDateTime()
            ));
        }
            return jdbcTemplate.query(sql, (rs, rowNum) -> new ArticleSummary(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("type"),
                    rs.getTimestamp("published_at").toLocalDateTime()
            ), type);
        }
    @GetMapping("/{id}")
    public ArticleDetail detail(@PathVariable long id) {
        String sql = """
                SELECT id, title, summary, content_markdown, type, published_at
                FROM article
                WHERE id = ? AND status = 'PUBLISHED'
                """;

        ArticleDetail article = jdbcTemplate.query(sql,rs -> {
            if (!rs.next()) {
                return null;
            }
            return new ArticleDetail(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("content_markdown"),
                    rs.getString("type"),
                    rs.getTimestamp("published_at").toLocalDateTime()
            );
        }, id);
        if (article == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
        }

        return article;
    }


}
