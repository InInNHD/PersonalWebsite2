package com.firefly.personalwebsite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 展示编排只保存顺序；每次读取都重新检查文章的公开状态。 */
@RestController
@RequestMapping("/api")
public class PresentationController {
    public record CollectionConfig(String slug, String title, String description, List<Long> articleIds) {}
    public record Config(List<Long> featuredArticleIds, List<CollectionConfig> collections, List<Long> projectArticleIds) {}
    public record CollectionPage(String slug, String title, String description,
                                 List<ArticleController.ArticleSummary> articles) {}
    public record Presentation(List<ArticleController.ArticleSummary> featured, List<CollectionPage> collections,
                               List<ArticleController.ArticleSummary> projectArticles) {}

    private final JdbcTemplate jdbc;
    private final ArticleTags tags;
    private final Config config;

    public PresentationController(JdbcTemplate jdbc, ArticleTags tags, ObjectMapper mapper,
            @Value("${app.presentation:classpath:presentation.json}") Resource resource) throws IOException {
        this.jdbc = jdbc;
        this.tags = tags;
        try (var input = resource.getInputStream()) {
            this.config = mapper.readValue(input, Config.class);
        }
        validate(config); // 配置有误时启动失败，避免悄悄展示不完整的编排。
    }

    static void validate(Config config) {
        if (config == null || !validIds(config.featuredArticleIds(), 3)
                || config.collections() == null || config.collections().size() > 12 || !validIds(config.projectArticleIds(), 10)) {
            throw new IllegalArgumentException("展示配置无效：精选最多3篇，专题最多12个");
        }
        Set<String> slugs = new HashSet<>();
        for (var item : config.collections()) {
            if (item == null || item.slug() == null || !item.slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || item.slug().length() > 60 || !slugs.add(item.slug())
                    || item.title() == null || item.title().isBlank() || item.title().length() > 80
                    || item.description() == null || item.description().length() > 300
                    || !validIds(item.articleIds(), 30)) {
                throw new IllegalArgumentException("专题配置无效：检查唯一slug、标题、说明和文章编号");
            }
        }
    }

    private static boolean validIds(List<Long> ids, int limit) {
        return ids != null && ids.size() <= limit && ids.stream().allMatch(id -> id != null && id > 0);
    }

    @GetMapping("/presentation")
    @Transactional(readOnly = true)
    public Presentation presentation() {
        // 一次查询全部候选，再按配置顺序组装；不把原始编号或草稿信息发给访客。
        Set<Long> ids = new LinkedHashSet<>(config.featuredArticleIds());
        ids.addAll(config.projectArticleIds());
        config.collections().forEach(item -> ids.addAll(item.articleIds()));
        var available = readPublic(ids);
        var collections = config.collections().stream().map(item -> new CollectionPage(
                item.slug(), item.title(), item.description(), ordered(item.articleIds(), available)))
                .filter(item -> item.articles().size() >= 2).toList();
        return new Presentation(ordered(config.featuredArticleIds(), available), collections,
                ordered(config.projectArticleIds(), available));
    }

    @GetMapping("/collections/{slug}")
    @Transactional(readOnly = true)
    public CollectionPage collection(@PathVariable("slug") String slug) {
        // 少于两篇公开文章的专题和不存在的专题一律404；撤回后立即从阅读路径移除。
        return presentation().collections().stream().filter(item -> item.slug().equals(slug)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "专题暂不可用"));
    }

    private List<ArticleController.ArticleSummary> ordered(List<Long> ids,
            Map<Long, ArticleController.ArticleSummary> available) {
        return ids.stream().distinct().map(available::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, ArticleController.ArticleSummary> readPublic(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        var rows = jdbc.query("""
                SELECT a.id, a.title, a.summary, a.type, a.published_at, a.category_id,
                       c.name category_name, a.cover_url, a.pinned, a.updated_at
                FROM article a LEFT JOIN category c ON c.id = a.category_id
                WHERE a.status = 'PUBLISHED' AND a.deleted_at IS NULL AND a.id IN (
                """ + placeholders + ")", (rs, row) -> new ArticleController.ArticleSummary(
                rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("type"),
                rs.getObject("published_at", LocalDateTime.class), rs.getObject("category_id", Long.class),
                rs.getString("category_name"), List.of(), rs.getString("cover_url"), rs.getBoolean("pinned"),
                rs.getObject("updated_at", LocalDateTime.class)), ids.toArray());
        var allTags = tags.readFor(rows.stream().map(ArticleController.ArticleSummary::id).toList());
        return rows.stream().map(a -> new ArticleController.ArticleSummary(a.id(), a.title(), a.summary(),
                a.type(), a.publishedAt(), a.categoryId(), a.categoryName(), allTags.getOrDefault(a.id(), List.of()),
                a.coverUrl(), a.pinned(), a.updatedAt())).collect(Collectors.toMap(
                        ArticleController.ArticleSummary::id, Function.identity()));
    }
}
