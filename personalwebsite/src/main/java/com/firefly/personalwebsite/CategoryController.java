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
public class CategoryController {

    private final JdbcTemplate jdbcTemplate;

    public CategoryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CategoryItem(long id, String name) {}

    public record CategoryRequest(
            @NotBlank @Size(max = 50) String name
    ) {}

    // 分类名称是公开信息。
    // 管理端和前台使用相同的数据格式。
    @GetMapping({"/api/categories", "/api/admin/categories"})
    public List<CategoryItem> list() {
        return jdbcTemplate.query(
                "SELECT id, name FROM category ORDER BY id ASC",
                (rs, rowNum) -> new CategoryItem(
                        rs.getLong("id"),
                        rs.getString("name")
                )
        );
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CategoryItem create(
            @Valid @RequestBody CategoryRequest request
    ) {
        String name = request.name().strip();
        var keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO category (name) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS
                );

                statement.setString(1, name);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            // 唯一索引检测到重名，将数据库异常转为明确的 HTTP 状态。
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "分类名称已存在"
            );
        }

        Number id = keyHolder.getKey();

        if (id == null) {
            throw new IllegalStateException("新增分类后未取得编号");
        }

        return new CategoryItem(id.longValue(), name);
    }

    @PutMapping("/api/admin/categories/{id}")
    @Transactional
    public CategoryItem rename(
            @PathVariable("id") long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        String name = request.name().strip();

        try {
            jdbcTemplate.update(
                    "UPDATE category SET name = ? WHERE id = ?",
                    name,
                    id
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "分类名称已存在"
            );
        }

        // 再查询一次，既确认存在，也允许保存相同名称。
        List<CategoryItem> rows = jdbcTemplate.query(
                "SELECT id, name FROM category WHERE id = ?",
                (rs, rowNum) -> new CategoryItem(
                        rs.getLong("id"),
                        rs.getString("name")
                ),
                id
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "分类不存在"
            );
        }

        return rows.get(0);
    }
}