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
            List<ArticleTags.TagItem> tags
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
            List<ArticleTags.TagItem> tags
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<ArticleSummary> list(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "tagId", required = false) Long tagId
    ) {
        // LEFT JOIN 保留未分类的文章。
        // 如果使用普通 JOIN，category_id 为空的文章就会被排除。
        String sql = """
                SELECT a.id, a.title, a.summary, a.type,
                       a.published_at, a.category_id,
                       c.name AS category_name
                FROM article a
                LEFT JOIN category c ON c.id = a.category_id
                WHERE a.status = 'PUBLISHED'
                """;

        // 参数的添加顺序与 SQL 中问号的顺序一致。
        List<Object> parameters = new ArrayList<>();

        if (type != null) {
            if (!Set.of("NOTE", "THOUGHT", "DIARY").contains(type)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "不支持的文章类型"
                );
            }

            sql += " AND a.type = ?";
            parameters.add(type);
        }

        if (categoryId != null) {
            if (categoryId <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "分类编号必须大于 0"
                );
            }

            sql += " AND a.category_id = ?";
            parameters.add(categoryId);
        }
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
        // 延续现有列表规则：显示满足筛选条件的最新 20 篇。
        sql += " ORDER BY a.published_at DESC, a.id DESC LIMIT 20";

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
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ArticleDetail detail(@PathVariable("id") long id) {
        String sql = """
                SELECT a.id, a.title, a.summary, a.content_markdown,
                       a.type, a.published_at, a.category_id,
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
                        articleTags.read(id)
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