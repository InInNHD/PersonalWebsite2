package com.firefly.personalwebsite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TopicController {
    private final JdbcTemplate jdbcTemplate;

    public TopicController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 新接口编号使用字符串，避免Java Long在浏览器中丢失精度。
    public record TopicItem(String id, String name, long total) {}
    public record TopicDirectory(List<TopicItem> categories, List<TopicItem> tags) {}

    @GetMapping("/api/topics")
    @Transactional(readOnly = true)
    public TopicDirectory topics() {
        // INNER JOIN只留下被公开文章使用的分类；未分类文章不虚构分类编号。
        var categories = jdbcTemplate.query("""
                SELECT c.id, c.name, COUNT(*) AS total
                FROM category c JOIN article a ON a.category_id = c.id
                WHERE a.status = 'PUBLISHED'
                GROUP BY c.id, c.name ORDER BY total DESC, c.id ASC
                """, (rs, rowNum) -> new TopicItem(
                rs.getString("id"), rs.getString("name"), rs.getLong("total")));

        // 一篇文章有多个标签时，每个标签分别计数，但同一标签内不重复文章。
        // 目录不含空标签和仅被草稿使用的标签，管理端原接口仍可管理所有标签。
        var tags = jdbcTemplate.query("""
                SELECT t.id, t.name, COUNT(DISTINCT a.id) AS total
                FROM tag t JOIN article_tag link ON link.tag_id = t.id
                JOIN article a ON a.id = link.article_id
                WHERE a.status = 'PUBLISHED'
                GROUP BY t.id, t.name ORDER BY total DESC, t.id ASC
                """, (rs, rowNum) -> new TopicItem(
                rs.getString("id"), rs.getString("name"), rs.getLong("total")));
        return new TopicDirectory(categories, tags);
    }
}