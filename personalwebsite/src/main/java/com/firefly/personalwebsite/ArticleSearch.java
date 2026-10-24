package com.firefly.personalwebsite;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** 只为当前搜索页生成短片段，不把整篇正文放进列表响应。 */
final class ArticleSearch {
    record Match(String field, String before, String match, String after, boolean leading, boolean trailing) {}

    static Map<Long, Match> snippets(JdbcTemplate jdbc, List<Long> ids, String keyword) {
        if (keyword.isEmpty() || ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        // 使用与筛选一致的MySQL排序规则定位，不在Java里另做一次大小写/重音匹配。
        // SUBSTRING按字符计数，避免把Emoji的代理对切成两半。前后各最多60字符。
        String sql = """
            SELECT id, field,
                SUBSTRING(source_text, GREATEST(1,pos-60), LEAST(60,pos-1)) AS before_text,
                SUBSTRING(source_text,pos,?) AS match_text,
                SUBSTRING(source_text,pos+?,60) AS after_text,
                pos>61 AS has_prefix, CHAR_LENGTH(source_text)>pos+?+59 AS has_suffix
            FROM (
                SELECT hits.*, LOCATE(?,source_text) AS pos FROM (
                    SELECT id,
                        CASE WHEN LOCATE(?,title)>0 THEN 'title'
                             WHEN LOCATE(?,summary)>0 THEN 'summary' ELSE 'body' END AS field,
                        CASE WHEN LOCATE(?,title)>0 THEN title
                             WHEN LOCATE(?,summary)>0 THEN summary ELSE content_markdown END AS source_text
                    FROM article WHERE status='PUBLISHED' AND deleted_at IS NULL AND id IN (
            """ + placeholders + """
                    )
                ) hits
            ) located WHERE pos>0
            """;
        int length = keyword.codePointCount(0, keyword.length());
        List<Object> parameters = new ArrayList<>(List.of(length, length, length, keyword, keyword, keyword, keyword, keyword));
        parameters.addAll(ids);
        Map<Long, Match> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getLong("id"), new Match(rs.getString("field"), rs.getString("before_text"),
                    rs.getString("match_text"), rs.getString("after_text"), rs.getBoolean("has_prefix"), rs.getBoolean("has_suffix")));
        }, parameters.toArray());
        return result;
    }
}
