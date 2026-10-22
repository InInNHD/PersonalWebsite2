package com.firefly.personalwebsite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Statement;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/resource-groups")
public class ResourceGroupController {
    private final JdbcTemplate jdbc;
    public ResourceGroupController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Group(long id, String name, int sortOrder) {}
    public record GroupRequest(@NotBlank @Size(max=50) String name,
                               @NotNull @Min(0) @Max(999999) Integer sortOrder) {}
    @GetMapping
    public List<Group> list() {
        return jdbc.query("SELECT id,name,sort_order FROM resource_group ORDER BY sort_order,id",
                (rs,n) -> new Group(rs.getLong(1),rs.getString(2),rs.getInt(3)));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Group create(@Valid @RequestBody GroupRequest input) {
        var key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement=connection.prepareStatement("INSERT INTO resource_group(name,sort_order) VALUES (?,?)",Statement.RETURN_GENERATED_KEYS);
                statement.setString(1,input.name().strip()); statement.setInt(2,input.sortOrder()); return statement;
            },key);
        } catch (DuplicateKeyException ex) { throw conflict(); }
        if(key.getKey()==null) throw new IllegalStateException("数据库未返回分组编号");
        return new Group(key.getKey().longValue(),input.name().strip(),input.sortOrder());
    }
    @PutMapping("/{id}")
    @Transactional
    public Group update(@PathVariable long id,@Valid @RequestBody GroupRequest input) {
        // 保存相同内容也成功；先确认存在，保留资源关联编号。
        if(jdbc.queryForObject("SELECT COUNT(*) FROM resource_group WHERE id=?",Integer.class,id)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"分组不存在");
        try { jdbc.update("UPDATE resource_group SET name=?,sort_order=? WHERE id=?",input.name().strip(),input.sortOrder(),id); }
        catch(DuplicateKeyException ex) { throw conflict(); }
        return new Group(id,input.name().strip(),input.sortOrder());
    }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"资源分组名称已存在"); }
}
