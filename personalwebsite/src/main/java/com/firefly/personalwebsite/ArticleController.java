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

    // items 是本页文章；total 是满足相同筛选条件的文章总数。
    public record ArticlePage(
            List<ArticleSummary> items,
            long total,
            int page,
            int size,
            long totalPages
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ArticlePage list(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "tagId", required = false) Long tagId,
            @RequestParam(name = "keyword", required = false) String keyword,
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

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + fromWhere, Long.class, parameters.toArray());
        long totalPages = (total + size - 1) / size;
        // 超出末页时回到最后一页；前端以响应中的 page 为准。
        int actualPage = total == 0 ? 1 : (int) Math.min(page, totalPages);
        long offset = (long) (actualPage - 1) * size;

        String sql = """
            SELECT a.id, a.title, a.summary, a.type,
                   a.published_at, a.category_id,
                   c.name AS category_name
            """ + fromWhere + " ORDER BY a.published_at DESC, a.id DESC LIMIT ? OFFSET ?";
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
                        List.of()),
                pageParameters.toArray());

        // 只批量查询本页文章的标签；筛选某个标签时仍返回每篇的全部标签。
        var tagsByArticle = articleTags.readFor(
                articles.stream().map(ArticleSummary::id).toList());
        var items = articles.stream().map(a -> new ArticleSummary(
                a.id(), a.title(), a.summary(), a.type(), a.publishedAt(),
                a.categoryId(), a.categoryName(),
                tagsByArticle.getOrDefault(a.id(), List.of())
        )).toList();
        return new ArticlePage(items, total, actualPage, size, totalPages);
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