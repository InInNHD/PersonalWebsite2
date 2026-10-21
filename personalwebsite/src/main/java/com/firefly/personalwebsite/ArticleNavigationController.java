package com.firefly.personalwebsite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleNavigationController {
    private final JdbcTemplate jdbcTemplate;

    public ArticleNavigationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 导航只返回链接所需的信息，不加载正文、草稿标题或标签。
    public record ArticleLink(long id, String title, LocalDateTime publishedAt) {}
    public record ArticleNeighbors(ArticleLink previous, ArticleLink next) {}

    private static final RowMapper<ArticleLink> LINK_MAPPER = (rs, rowNum) -> new ArticleLink(
            rs.getLong("id"), rs.getString("title"),
            rs.getTimestamp("published_at").toLocalDateTime());

    @GetMapping("/{id}/neighbors")
    @Transactional(readOnly = true)
    public ArticleNeighbors neighbors(@PathVariable("id") long id) {
        // 先验证当前文章。草稿、已下线和不存在的编号使用同一种404。
        List<ArticleLink> currentRows = jdbcTemplate.query("""
                SELECT id, title, published_at FROM article
                WHERE id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL AND published_at IS NOT NULL
                """, LINK_MAPPER, id);
        if (currentRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
        }
        ArticleLink current = currentRows.get(0);

        // 上一篇=较早发布；同一秒发布时，较小编号排在前面。
        // LIMIT在数据库内取最近一篇，不下载全站文章后在Java中查找。
        List<ArticleLink> previous = jdbcTemplate.query("""
                SELECT id, title, published_at FROM article
                WHERE status = 'PUBLISHED' AND deleted_at IS NULL AND published_at IS NOT NULL
                  AND (published_at < ? OR (published_at = ? AND id < ?))
                ORDER BY published_at DESC, id DESC LIMIT 1
                """, LINK_MAPPER, current.publishedAt(), current.publishedAt(), id);

        // 下一篇=较新发布；不按置顶排序，也不受列表的分类、标签等筛选限制。
        List<ArticleLink> next = jdbcTemplate.query("""
                SELECT id, title, published_at FROM article
                WHERE status = 'PUBLISHED' AND deleted_at IS NULL AND published_at IS NOT NULL
                  AND (published_at > ? OR (published_at = ? AND id > ?))
                ORDER BY published_at ASC, id ASC LIMIT 1
                """, LINK_MAPPER, current.publishedAt(), current.publishedAt(), id);

        // 首尾没有相邻文章时返回null，前端显示边界提示，不生成空链接。
        return new ArticleNeighbors(
                previous.isEmpty() ? null : previous.get(0),
                next.isEmpty() ? null : next.get(0));
    }
}