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