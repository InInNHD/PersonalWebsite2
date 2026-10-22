package com.firefly.personalwebsite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

// 现有安全配置已经保护 /api/admin/**，这些接口需要管理员认证。
@RestController
@RequestMapping("/api/admin/resources")
public class AdminResourceController {

    private final JdbcTemplate jdbcTemplate;

    public AdminResourceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 新建与修改使用相同的数据格式。
    // Boolean、Integer 使用包装类型，才能区分“未填写”和 false、0。
    public record ResourceRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2048) String url,
            @NotNull @Size(max = 500) String description,
            @NotNull Boolean published,
            @NotNull @Min(0) @Max(999999) Integer sortOrder,
            @jakarta.validation.constraints.Positive Long groupId,
            @Size(max=20) @jakarta.validation.constraints.Pattern(regexp="link|code|book|tool|course") String iconKey,
            @Size(max=300) String recommendation
    ) {}

    // 管理端需要知道公开状态和排序，因此比公开接口多返回两个字段。
    public record ResourceItem(
            long id,
            String title,
            String url,
            String description,
            boolean published,
            int sortOrder, Long groupId, String iconKey, String recommendation
    ) {}

    // 列表和保存后查询共用同一套字段映射。
    private ResourceItem mapRow(ResultSet rs, int rowNum)
            throws SQLException {
        return new ResourceItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("url"),
                rs.getString("description"),
                rs.getBoolean("published"),
                rs.getInt("sort_order"), rs.getObject("group_id",Long.class), rs.getString("icon_key"), rs.getString("recommendation")
        );
    }

    @GetMapping
    public List<ResourceItem> list() {
        // 管理员能看到公开和隐藏的全部资源。
        String sql = """
                SELECT id, title, url, description, published, sort_order, group_id, icon_key, recommendation
                FROM resource_link
                ORDER BY sort_order ASC, id DESC
                """;

        return jdbcTemplate.query(sql, this::mapRow);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ResourceItem create(
            @Valid @RequestBody ResourceRequest request
    ) {
        String url = validateUrl(request.url());
        checkGroup(request.groupId());

        String sql = """
                INSERT INTO resource_link
                    (title, url, description, published, sort_order, group_id, icon_key, recommendation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        var keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS
            );

            // 使用占位符传值，输入内容不会被当作 SQL 执行。
            statement.setString(1, request.title().strip());
            statement.setString(2, url);
            statement.setString(3, request.description().strip());
            statement.setBoolean(4, request.published());
            statement.setInt(5, request.sortOrder());
            statement.setObject(6,request.groupId(),java.sql.Types.BIGINT);
            statement.setString(7,request.iconKey()==null?"link":request.iconKey());
            statement.setString(8,request.recommendation()==null?"":request.recommendation().strip());

            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            // 事务会回滚，避免留下无法确认编号的新增记录。
            throw new IllegalStateException("新增资源后未取得编号");
        }

        return findById(key.longValue());
    }

    @PutMapping("/{id}")
    @Transactional
    public ResourceItem update(
            @PathVariable("id") long id,
            @Valid @RequestBody ResourceRequest request
    ) {
        String url = validateUrl(request.url());
        checkGroup(request.groupId());

        String sql = """
                UPDATE resource_link
                SET title = ?, url = ?, description = ?,
                    published = ?, sort_order = ?, group_id = ?, icon_key = ?, recommendation = ?
                WHERE id = ?
                """;

        jdbcTemplate.update(
                sql,
                request.title().strip(),
                url,
                request.description().strip(),
                request.published(),
                request.sortOrder(), request.groupId(), request.iconKey()==null?"link":request.iconKey(),
                request.recommendation()==null?"":request.recommendation().strip(),
                id
        );

        // 保存后重新查询，同时判断记录是否存在。
        // 不依赖更新行数，避免“保存相同内容”被误认为不存在。
        return findById(id);
    }

    private ResourceItem findById(long id) {
        String sql = """
                SELECT id, title, url, description, published, sort_order, group_id, icon_key, recommendation
                FROM resource_link
                WHERE id = ?
                """;

        List<ResourceItem> rows =
                jdbcTemplate.query(sql, this::mapRow, id);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "资源不存在"
            );
        }

        return rows.get(0);
    }

    private void checkGroup(Long id) {
        if(id!=null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resource_group WHERE id=?",Integer.class,id)==0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"资源分组不存在");
    }

    private String validateUrl(String input) {
        String value = input.strip();

        try {
            URI uri = URI.create(value);

            boolean supportedScheme =
                    "http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme());

            // 必须是完整网址，不能只填写路径或 javascript: 等其他协议。
            if (supportedScheme && uri.getHost() != null && uri.getRawUserInfo()==null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // 地址无法解析时，统一按下面的方式返回 400。
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "资源链接必须是完整的 HTTP 或 HTTPS 地址"
        );
    }
}