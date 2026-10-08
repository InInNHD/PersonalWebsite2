package com.firefly.personalwebsite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Statement;
import java.util.List;

@RestController
public class TagController {

    private final JdbcTemplate jdbcTemplate;

    public TagController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 返回给前端的标签数据。
    public record TagItem(long id, String name) {}

    // 新建和重命名都只需要提交名称。
    public record TagRequest(
            @NotBlank @Size(max = 50) String name
    ) {}

    // 标签名称是公开信息，前台和管理端共用查询逻辑。
    @GetMapping({"/api/tags", "/api/admin/tags"})
    public List<TagItem> list() {
        return jdbcTemplate.query(
                "SELECT id, name FROM tag ORDER BY id ASC",
                (rs, rowNum) -> new TagItem(
                        rs.getLong("id"),
                        rs.getString("name")
                )
        );
    }

    @PostMapping("/api/admin/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TagItem create(
            @Valid @RequestBody TagRequest request
    ) {
        // 去掉名称两端的空白，避免意外保存多余空格。
        String name = request.name().strip();
        var keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO tag (name) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS
                );

                statement.setString(1, name);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            // 数据库唯一索引负责最终的重名检查。
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "标签名称已存在"
            );
        }

        Number id = keyHolder.getKey();

        if (id == null) {
            // 抛出异常后，事务回滚本次新增。
            throw new IllegalStateException("新增标签后未取得编号");
        }

        return new TagItem(id.longValue(), name);
    }

    @PutMapping("/api/admin/tags/{id}")
    @Transactional
    public TagItem rename(
            @PathVariable("id") long id,
            @Valid @RequestBody TagRequest request
    ) {
        String name = request.name().strip();

        try {
            jdbcTemplate.update(
                    "UPDATE tag SET name = ? WHERE id = ?",
                    name,
                    id
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "标签名称已存在"
            );
        }

        // 查询保存后的结果，同时判断标签是否存在。
        // 保存相同名称时也应当成功。
        List<TagItem> rows = jdbcTemplate.query(
                "SELECT id, name FROM tag WHERE id = ?",
                (rs, rowNum) -> new TagItem(
                        rs.getLong("id"),
                        rs.getString("name")
                ),
                id
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "标签不存在"
            );
        }

        return rows.get(0);
    }
}