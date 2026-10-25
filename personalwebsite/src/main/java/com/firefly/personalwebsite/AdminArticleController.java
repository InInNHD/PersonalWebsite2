package com.firefly.personalwebsite;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.sql.Types;
import java.net.URI;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final JdbcTemplate jdbcTemplate;
    private final ArticleTags articleTags;
    private final MediaStore mediaStore;

    // Spring 会自动提供数据库操作对象和文章标签对象。
    public AdminArticleController(
            JdbcTemplate jdbcTemplate, ArticleTags articleTags, MediaStore mediaStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.articleTags = articleTags;
        this.mediaStore = mediaStore;
    }

    //// 请求只包含文章内容，草稿状态和发布时间由后端决定。
    public record DraftRequest(
            @NotBlank @Size(max = 200)
            String title,

            @NotNull @Size(max = 500)
            String summary,

            @NotBlank
            String contentMarkdown,

            @NotBlank @Pattern(regexp = "NOTE|THOUGHT|DIARY")
            String type,

            // null 表示未分类；填写编号时必须大于 0。
            @Positive
            Long categoryId,
            // 每篇最多选择 20 个标签；数组中的每个编号都必须非空且大于 0。
            @Size(max = 20)
            List<@NotNull @Positive Long> tagIds,
            // 封面可留空；后端仍需校验协议，不能只依赖浏览器的type="url"。
            @Size(max = 2048) String coverUrl,
            // 兼容旧请求：未传置顶字段时按false处理。
            Boolean pinned,
            // 由管理端Markdown解析器提取真实图片目标；这份列表明确授权媒体随文章公开。
            @Size(max = 200)
            List<@NotBlank @Size(max = 2048) String> bodyMediaUrls
    ) {}

    // 新建和发布都返回文章编号与当前状态。
    public record ArticleResult(long id, String status, long version) {}

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ArticleResult createDraft(
            @Valid @RequestBody DraftRequest request
    ) {
        checkCategory(request.categoryId());
        String coverUrl = validateCoverUrl(request.coverUrl());
        String sql = """
        INSERT INTO article
            (title, summary, content_markdown, type,
             category_id, status, published_at, cover_url, pinned, updated_at)
        VALUES (?, ?, ?, ?, ?, 'DRAFT', NULL, ?, ?, CURRENT_TIMESTAMP(6))
        """;
        // 保存 MySQL 返回的自增编号。
        // 编号与 INSERT 来自同一次操作，避免从连接池取到另一条连接。

        var keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection ->{
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            // 问号占位符的编号从 1 开始
            statement.setString(1, request.title().strip());
            statement.setString(2, request.summary().strip());
            statement.setString(3, request.contentMarkdown());
            statement.setString(4, request.type());

            // SQL 的第五个占位符对应 category_id。
            if (request.categoryId() == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, request.categoryId());
            }

            statement.setString(6, coverUrl);
            statement.setBoolean(7, Boolean.TRUE.equals(request.pinned()));
            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            // 抛出异常时，事务会回滚刚才的 INSERT
            throw new IllegalStateException("数据库未返回文章编号");
        }

        // 保存刚创建的文章与所选标签之间的关联。
        articleTags.replace(id.longValue(), request.tagIds());
        mediaStore.replaceReferences(id.longValue(), coverUrl, request.bodyMediaUrls());
        return new ArticleResult(id.longValue(), "DRAFT", 0);

    }

    // 所有写入共享同一版本和行锁：旧窗口不能覆盖保存，也不能误发布新窗口的稿件。
    private AdminDetail lockArticle(long id, String expected) {
        if (expected == null) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "缺少文章版本，请重新加载");
        if (!expected.matches("\"[0-9]{1,18}\"")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文章版本格式无效");
        long version = Long.parseLong(expected.substring(1, expected.length()-1));
        var versions = jdbcTemplate.queryForList("SELECT write_version FROM article WHERE id=? FOR UPDATE", Long.class, id);
        if (versions.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在");
        if (versions.get(0) != version) throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "文章已在另一窗口更改，请保留输入并重新核对版本");
        return detail(id);
    }

    private void requireActive(AdminDetail article) {
        if (article.deletedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先从回收站恢复文章");
    }

    @PostMapping("/{id}/publish")
    @Transactional
    public ArticleResult publishDraft(@PathVariable("id") long id, @RequestHeader(value="If-Match", required=false) String version) {
        var article = lockArticle(id, version);
        requireActive(article);
        if (article.hasUnpublishedChanges()) {
            // 内容、分类、标签和图片授权在同一个事务里成为新的公开版本。
            jdbcTemplate.update("""
                UPDATE article a JOIN article_edit e ON e.article_id=a.id
                SET a.title=e.title,a.summary=e.summary,a.content_markdown=e.content_markdown,
                    a.type=e.type,a.category_id=e.category_id,a.cover_url=e.cover_url,a.pinned=e.pinned
                WHERE a.id=?
                """, id);
            jdbcTemplate.update("DELETE FROM article_tag WHERE article_id=?", id);
            jdbcTemplate.update("INSERT INTO article_tag SELECT article_id,tag_id FROM article_edit_tag WHERE article_id=?", id);
            jdbcTemplate.update("DELETE FROM article_media WHERE article_id=?", id);
            jdbcTemplate.update("INSERT INTO article_media SELECT article_id,media_id,usage_role FROM article_edit_media WHERE article_id=?", id);
            // 关联表通过外键级联清理；删除的是已发布的临时编辑稿，不是文章或媒体文件。
            jdbcTemplate.update("DELETE FROM article_edit WHERE article_id=?", id);
        }
        jdbcTemplate.update("""
                UPDATE article SET updated_at=CASE WHEN status='DRAFT' OR ? THEN CURRENT_TIMESTAMP(6) ELSE updated_at END,
                    published_at=COALESCE(published_at,CURRENT_TIMESTAMP),status='PUBLISHED',write_version=write_version+1
                WHERE id=?
                """, article.hasUnpublishedChanges(), id);
        return new ArticleResult(id, "PUBLISHED", article.version()+1);
    }

    @PostMapping("/{id}/unpublish")
    @Transactional
    public ArticleResult unpublish(@PathVariable("id") long id, @RequestHeader(value="If-Match", required=false) String version) {
        var article=lockArticle(id,version); requireActive(article);
        jdbcTemplate.update("UPDATE article SET status='DRAFT',updated_at=CURRENT_TIMESTAMP(6),write_version=write_version+1 WHERE id=?",id);
        return new ArticleResult(id,"DRAFT",article.version()+1);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ArticleResult recycle(@PathVariable("id") long id, @RequestHeader(value="If-Match", required=false) String version) {
        var article=lockArticle(id,version);
        jdbcTemplate.update("UPDATE article SET deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP(6)),write_version=write_version+1 WHERE id=?",id);
        return new ArticleResult(id,article.status(),article.version()+1);
    }

    @PostMapping("/{id}/restore")
    @Transactional
    public ArticleResult restore(@PathVariable("id") long id, @RequestHeader(value="If-Match", required=false) String version) {
        var article=lockArticle(id,version);
        // 恢复保留编辑稿，先回到非公开状态；重复恢复不撤回已重新发布的文章。
        jdbcTemplate.update("UPDATE article SET status=CASE WHEN deleted_at IS NULL THEN status ELSE 'DRAFT' END,deleted_at=NULL,write_version=write_version+1 WHERE id=?",id);
        return new ArticleResult(id,article.deletedAt()==null?article.status():"DRAFT",article.version()+1);
    }

    // 列表不返回完整正文，减少传输的数据量。
    public record AdminSummary(long id, String title, String type, String status,
                               boolean pinned, Long categoryId, String categoryName,
                               LocalDateTime updatedAt, LocalDateTime deletedAt, long version, boolean hasUnpublishedChanges) {}
    public record AdminPage(List<AdminSummary> items, long total, int page, int size, long totalPages) {}

    public record AdminDetail(
            long id,
            String title,
            String summary,
            String contentMarkdown,
            String type,
            String status,
            Long categoryId,
            List<Long> tagIds,
            String coverUrl,
            boolean pinned,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            long version,
            boolean hasUnpublishedChanges
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public AdminPage list(
            @RequestParam(name="keyword", defaultValue="") String keyword,
            @RequestParam(name="status", required=false) String status,
            @RequestParam(name="type", required=false) String type,
            @RequestParam(name="categoryId", required=false) Long categoryId,
            @RequestParam(name="recycled", defaultValue="false") boolean recycled,
            @RequestParam(name="page", defaultValue="1") int page,
            @RequestParam(name="size", defaultValue="10") int size) {
        String search = keyword.strip();
        if (page < 1 || page > 1_000_000 || size < 1 || size > 50 || search.length() > 100
                || (status != null && !Set.of("DRAFT", "PUBLISHED").contains(status))
                || (type != null && !Set.of("NOTE", "THOUGHT", "DIARY").contains(type))
                || (categoryId != null && categoryId <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文章筛选或分页参数不合法");
        }
        String from = " FROM article a LEFT JOIN article_edit e ON e.article_id=a.id LEFT JOIN category c ON c.id=CASE WHEN e.article_id IS NULL THEN a.category_id ELSE e.category_id END WHERE a.deleted_at IS " + (recycled ? "NOT NULL" : "NULL");
        var parameters = new ArrayList<Object>();
        if (!search.isEmpty()) { from += " AND (LOCATE(?, COALESCE(e.title,a.title))>0 OR LOCATE(?, COALESCE(e.summary,a.summary))>0)"; parameters.add(search); parameters.add(search); }
        if (status != null) { from += " AND a.status=?"; parameters.add(status); }
        if (type != null) { from += " AND COALESCE(e.type,a.type)=?"; parameters.add(type); }
        if (categoryId != null) { from += " AND (CASE WHEN e.article_id IS NULL THEN a.category_id ELSE e.category_id END)=?"; parameters.add(categoryId); }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from, Long.class, parameters.toArray());
        long pages = (total + size - 1) / size;
        int actualPage = (int) Math.min(page, Math.max(1, pages));
        parameters.add(size); parameters.add((long) (actualPage - 1) * size);
        String sql = "SELECT a.id,COALESCE(e.title,a.title) title,COALESCE(e.type,a.type) type,a.status,COALESCE(e.pinned,a.pinned) pinned,CASE WHEN e.article_id IS NULL THEN a.category_id ELSE e.category_id END category_id,c.name category_name,COALESCE(e.updated_at,a.updated_at) updated_at,a.deleted_at,a.write_version,e.article_id IS NOT NULL has_changes" + from
                + (recycled ? " ORDER BY a.deleted_at DESC,a.id DESC" : " ORDER BY a.id DESC") + " LIMIT ? OFFSET ?";
        var items = jdbcTemplate.query(sql, (rs, rowNum) -> new AdminSummary(rs.getLong("id"), rs.getString("title"),
                rs.getString("type"), rs.getString("status"), rs.getBoolean("pinned"), rs.getObject("category_id",Long.class),
                rs.getString("category_name"), rs.getObject("updated_at",LocalDateTime.class), rs.getObject("deleted_at",LocalDateTime.class),rs.getLong("write_version"),rs.getBoolean("has_changes")), parameters.toArray());
        return new AdminPage(items,total,actualPage,size,pages);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminDetail detail(@PathVariable("id") long id) {
        String sql = """
        SELECT a.id,COALESCE(e.title,a.title) title,COALESCE(e.summary,a.summary) summary,
               COALESCE(e.content_markdown,a.content_markdown) content_markdown,
               COALESCE(e.type,a.type) type,a.status,
               CASE WHEN e.article_id IS NULL THEN a.category_id ELSE e.category_id END category_id,
               CASE WHEN e.article_id IS NULL THEN a.cover_url ELSE e.cover_url END cover_url,
               COALESCE(e.pinned,a.pinned) pinned,COALESCE(e.updated_at,a.updated_at) updated_at,
               a.deleted_at,a.write_version,e.article_id IS NOT NULL has_changes
        FROM article a LEFT JOIN article_edit e ON e.article_id=a.id WHERE a.id=?
        """;

        AdminDetail article = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }

            return new AdminDetail(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("content_markdown"),
                    rs.getString("type"),
                    rs.getString("status"),

                    // getObject 保留 SQL NULL；不能用 getLong 将其读成 0。
                    rs.getObject("category_id", Long.class),
                    rs.getBoolean("has_changes") ? articleTags.readEditing(id) : articleTags.read(id).stream()
                            .map(ArticleTags.TagItem::id)
                            .toList(),
                    rs.getString("cover_url"),
                    rs.getBoolean("pinned"),
                    // 旧文章的更新时间允许为空，getObject保留null。
                    rs.getObject("updated_at", LocalDateTime.class),
                    rs.getObject("deleted_at", LocalDateTime.class),
                    rs.getLong("write_version"),
                    rs.getBoolean("has_changes")
            );
        }, id);

        if (article == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @Transactional
    public AdminDetail update(
            @PathVariable("id") long id,
            @Valid @RequestBody DraftRequest request,
            @RequestHeader(value="If-Match", required=false) String version) {

        // 先确认文章存在；不存在时返回 404，避免插入无效关联。
        requireActive(lockArticle(id,version));
        checkCategory(request.categoryId());
        String coverUrl = validateCoverUrl(request.coverUrl());

        // article始终保留上次公开内容。草稿独立保存，发布时才替换公开副本。
        String sql = """
            INSERT INTO article_edit(title,summary,content_markdown,type,category_id,cover_url,pinned,article_id)
            VALUES (?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE title=VALUES(title),summary=VALUES(summary),content_markdown=VALUES(content_markdown),
                type=VALUES(type),category_id=VALUES(category_id),cover_url=VALUES(cover_url),pinned=VALUES(pinned),updated_at=CURRENT_TIMESTAMP(6)
            """;

        // 保存内容时保留文章状态和第一次发布时间。
        jdbcTemplate.update(sql,
                request.title().strip(),
                request.summary().strip(),
                request.contentMarkdown(),
                request.type(),
                request.categoryId(),
                coverUrl,
                Boolean.TRUE.equals(request.pinned()),
                id);

        // 标签无效时抛出异常，刚才的文章内容更新也会回滚。
        articleTags.replace(id, request.tagIds(), true);
        mediaStore.replaceReferences(id, coverUrl, request.bodyMediaUrls(), true);
        jdbcTemplate.update("UPDATE article SET write_version=write_version+1 WHERE id=?",id);
        return detail(id);
    }

    // 后端只保存地址，不下载图片；真正显示时由访客浏览器请求图片。
    // 空字符串统一存NULL，前端使用栏目默认图；不要求URL以.jpg/.png结尾。
    private String validateCoverUrl(String input) {
        if (input == null || input.isBlank()) return null;
        String value = input.strip();
        if (mediaStore.localFilename(value) != null) return value;
        try {
            URI uri = URI.create(value);
            boolean supported = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            // 拒绝相对路径、无主机、带账号密码和其他协议的地址。
            if (supported && uri.getHost() != null && uri.getRawUserInfo() == null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // 地址解析失败，按同一种输入错误返回。
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "封面必须为空、本站上传图片地址或不含账号密码的完整HTTP/HTTPS地址");
    }

    private void checkCategory(Long categoryId) {
        // 允许文章不设置分类。
        if (categoryId == null) return;

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = ?",
                Integer.class,
                categoryId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "所选分类不存在"
            );
        }
    }
}
