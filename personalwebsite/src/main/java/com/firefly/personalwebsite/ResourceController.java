package com.firefly.personalwebsite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 返回 JSON 数据，供 Vue 页面使用。
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final JdbcTemplate jdbcTemplate;

    // Spring 自动提供已经配置好数据库连接的 JdbcTemplate。
    public ResourceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 只返回展示页需要的字段。
    // 是否公开和排序值由后端处理，前端无需自行判断。
    public record ResourceSummary(
            long id,
            String title,
            String url,
            String description, Long groupId, String groupName, int groupSortOrder, String iconKey, String recommendation
    ) {}

    @GetMapping
    public List<ResourceSummary> list() {
        String sql = """
                SELECT r.id,r.title,r.url,r.description,r.group_id,g.name group_name,
                    COALESCE(g.sort_order,999999) group_sort_order,r.icon_key,r.recommendation
                FROM resource_link r LEFT JOIN resource_group g ON g.id=r.group_id
                WHERE r.published = TRUE
                ORDER BY (r.group_id IS NULL),g.sort_order ASC,g.id ASC,r.sort_order ASC,r.id DESC
                """;

        // 每行数据库记录转换为一个 ResourceSummary。
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new ResourceSummary(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("url"),
                        rs.getString("description"),rs.getObject("group_id",Long.class),rs.getString("group_name"),rs.getInt("group_sort_order"),rs.getString("icon_key"),rs.getString("recommendation")
                )
        );
    }
}