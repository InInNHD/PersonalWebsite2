package com.firefly.personalwebsite;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleTags {
    private final JdbcTemplate jdbcTemplate;

    public ArticleTags(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 前台需要编号和名称；后台可以从这里提取编号。
    public record TagItem(long id, String name) {}

    public List<TagItem> read(long articleId) {
        return readFor(List.of(articleId))
                .getOrDefault(articleId, List.of());
    }

    public Map<Long, List<TagItem>> readFor(List<Long> articleIds) {
        // 空列表不能生成 IN ()；也不必访问数据库。
        if (articleIds.isEmpty()) return Map.of();

        // 只拼接问号；文章编号仍作为参数绑定，避免 SQL 注入。
        String placeholders = String.join(",",
                Collections.nCopies(articleIds.size(), "?"));

        String sql = """
                SELECT link.article_id, t.id, t.name
                FROM article_tag link
                JOIN tag t ON t.id = link.tag_id
                WHERE link.article_id IN (%s)
                ORDER BY link.article_id, t.id
                """.formatted(placeholders);

        Map<Long, List<TagItem>> result = new HashMap<>();

        // 一次读取整批文章的标签，避免列表里的每篇文章分别发查询。
        jdbcTemplate.query(sql, (RowCallbackHandler) rs -> {
            long articleId = rs.getLong("article_id");
            result.computeIfAbsent(articleId, ignored -> new ArrayList<>())
                    .add(new TagItem(rs.getLong("id"), rs.getString("name")));
        }, articleIds.toArray());

        return result;
    }

    @Transactional
    public void replace(long articleId, List<Long> requestedIds) {
        // 兼容没有 tagIds 的旧请求，将其视为“不选择标签”。
        // 新前端会始终发送数组；[] 明确表示清空全部标签。
        List<Long> ids = requestedIds == null
                ? List.of()
                : requestedIds.stream().distinct().sorted().toList();

        if (!ids.isEmpty()) {
            String placeholders = String.join(",",
                    Collections.nCopies(ids.size(), "?"));

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tag WHERE id IN ("
                            + placeholders + ")",
                    Integer.class,
                    ids.toArray());

            // 前端传来的编号也可能被篡改，必须核对是否实际存在。
            if (count == null || count != ids.size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "所选标签不存在");
            }
        }

        // 先检查，再替换。这里只删除文章与标签的关联。
        jdbcTemplate.update(
                "DELETE FROM article_tag WHERE article_id = ?", articleId);

        for (Long tagId : ids) {
            jdbcTemplate.update(
                    "INSERT INTO article_tag (article_id, tag_id) VALUES (?, ?)",
                    articleId, tagId);
        }
    }
}