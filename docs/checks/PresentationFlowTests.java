package com.firefly.personalwebsite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 使用实际的控制器、安全过滤器和本地 MySQL；所有写入随测试事务回滚。
@SpringBootTest(properties = "app.admin.password=category-flow-test-only")
@AutoConfigureMockMvc
@Transactional
class PresentationFlowTests {
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "admin:category-flow-test-only".getBytes(StandardCharsets.UTF_8));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private MockHttpSession session;
    private String csrfHeader;
    private String csrfToken;
    private String prefix;

    @BeforeEach
    void authenticateAndObtainRealCsrfToken() throws Exception {
        prefix = "flow-" + UUID.randomUUID().toString().substring(0, 12);
        var result = mvc.perform(get("/api/admin/csrf")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) result.getRequest().getSession(false);
        var token = json.readTree(result.getResponse().getContentAsString());
        csrfHeader = token.get("headerName").asText();
        csrfToken = token.get("token").asText();
        assertNotNull(session);
    }

    @Test
    void presentationFieldsValidatePersistAndPreservePublishTime() throws Exception {
        var payload = article(null);
        payload.put("coverUrl", "  https://example.com/image?id=1  ");
        payload.put("pinned", true);
        long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
        String path = "/api/admin/articles/" + id;
        var draft = send(HttpMethod.GET, path, null, 200);
        assertEquals("https://example.com/image?id=1", draft.get("coverUrl").asText());
        assertTrue(draft.get("pinned").asBoolean());
        assertFalse(draft.get("updatedAt").isNull());
        publicGet("/api/articles/" + id, 404);
        send(HttpMethod.POST, path + "/publish", null, 200);
        var published = publicGet("/api/articles/" + id, 200);
        assertTrue(published.get("pinned").asBoolean());
        assertEquals(draft.get("coverUrl"), published.get("coverUrl"));
        var firstPublishedAt = published.get("publishedAt");
        var firstUpdatedAt = published.get("updatedAt");
        send(HttpMethod.POST, path + "/publish", null, 200);
        assertEquals(firstUpdatedAt, publicGet("/api/articles/" + id, 200).get("updatedAt"));
        jdbc.update("UPDATE article SET updated_at = '2001-01-01' WHERE id = ?", id);
        payload.put("coverUrl", "  ");
        payload.put("pinned", false);
        send(HttpMethod.PUT, path, payload, 200);
        var cleared = publicGet("/api/articles/" + id, 200);
        assertTrue(cleared.get("coverUrl").isNull());
        assertFalse(cleared.get("pinned").asBoolean());
        assertFalse(cleared.get("updatedAt").asText().startsWith("2001"));
        assertEquals(firstPublishedAt, cleared.get("publishedAt"));
        for (String invalid : java.util.List.of("ftp://example.com/a", "javascript:alert(1)",
                "data:image/png;base64,a", "/relative.png", "https://", "https://user:pass@example.com/a",
                "https://example.com/" + "a".repeat(2048))) {
            payload.put("coverUrl", invalid);
            send(HttpMethod.PUT, path, payload, 400);
            send(HttpMethod.POST, "/api/admin/articles", payload, 400);
        }
        assertTrue(publicGet("/api/articles/" + id, 200).get("coverUrl").isNull());
        payload.put("coverUrl", "http://localhost:5173/test.svg");
        payload.put("pinned", true);
        payload.put("tagIds", java.util.List.of(Long.MAX_VALUE));
        send(HttpMethod.PUT, path, payload, 400);
        // 测试方法外层事务尚未结束，不能在这里判断失败请求是否已经物理回滚。
        // 原子回滚由独立HTTP预检验证；此处验证接口确实拒绝无效标签。
    }

    @Test
    void pinnedSortHappensBeforePaginationAndArchiveStaysChronological() throws Exception {
        long category = createCategory("-pin");
        var ids = new java.util.ArrayList<Long>();
        for (int i = 0; i < 4; i++) {
            var payload = article(category);
            payload.put("pinned", i == 0 || i == 3);
            long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
            ids.add(id);
            if (i < 3) {
                send(HttpMethod.POST, "/api/admin/articles/" + id + "/publish", null, 200);
                jdbc.update("UPDATE article SET published_at = ? WHERE id = ?", "2041-06-0" + (i + 1), id);
            }
        }
        String base = "/api/articles?categoryId=" + category + "&size=2";
        var first = publicResponse(base, 200);
        var second = publicResponse(base + "&page=2", 200);
        assertEquals(3, first.get("total").asInt());
        assertEquals(ids.get(0).longValue(), first.get("items").get(0).get("id").asLong());
        assertEquals(ids.get(2).longValue(), first.get("items").get(1).get("id").asLong());
        assertEquals(ids.get(1).longValue(), second.get("items").get(0).get("id").asLong());
        assertEquals(ids.get(2).longValue(), publicResponse(base + "&month=2041-06", 200)
                .get("items").get(0).get("id").asLong());
        assertEquals(0, publicResponse(base + "&type=DIARY", 200).get("total").asLong());
        publicGet("/api/articles/" + ids.get(3), 404);
        var payload = article(category);
        payload.put("pinned", false);
        send(HttpMethod.PUT, "/api/admin/articles/" + ids.get(0), payload, 200);
        assertEquals(ids.get(2).longValue(), publicResponse(base, 200).get("items").get(0).get("id").asLong());
    }

    @Test
    void publicStatisticsExcludeDraftOnlyTaxonomyAndHiddenResources() throws Exception {
        var before = publicResponse("/api/site-stats", 200);
        long visibleCategory = createCategory("-visible"), hiddenCategory = createCategory("-hidden");
        long tag = send(HttpMethod.POST, "/api/admin/tags", Map.of("name", prefix), 201).get("id").asLong();
        long hiddenTag = send(HttpMethod.POST, "/api/admin/tags", Map.of("name", prefix + "-hidden"), 201).get("id").asLong();
        var payload = article(hiddenCategory);
        payload.put("tagIds", java.util.List.of(hiddenTag));
        send(HttpMethod.POST, "/api/admin/articles", payload, 201);
        jdbc.update("INSERT INTO resource_link(title,url,description,published,sort_order) VALUES (?,?,'',FALSE,0)", prefix, "https://example.com");
        assertEquals(before, publicResponse("/api/site-stats", 200));
        payload.put("categoryId", visibleCategory);
        payload.put("tagIds", java.util.List.of(tag));
        for (int i = 0; i < 2; i++) {
            long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
            send(HttpMethod.POST, "/api/admin/articles/" + id + "/publish", null, 200);
        }
        jdbc.update("INSERT INTO resource_link(title,url,description,published,sort_order) VALUES (?,?,'',TRUE,0)", prefix, "https://example.com");
        var after = publicResponse("/api/site-stats", 200);
        assertEquals(before.get("articles").asLong() + 2, after.get("articles").asLong());
        assertEquals(before.get("categories").asLong() + 1, after.get("categories").asLong());
        assertEquals(before.get("tags").asLong() + 1, after.get("tags").asLong());
        assertEquals(before.get("resources").asLong() + 1, after.get("resources").asLong());
    }

    @Test
    void legacyArticleWithoutNewFieldsRemainsReadable() throws Exception {
        jdbc.update("INSERT INTO article(title,summary,content_markdown,type,status,published_at) VALUES (?, '', '# Legacy', 'NOTE', 'PUBLISHED', '2020-01-01')", prefix);
        long id = jdbc.queryForObject("SELECT id FROM article WHERE title = ?", Long.class, prefix);
        var detail = publicGet("/api/articles/" + id, 200);
        assertTrue(detail.get("coverUrl").isNull());
        assertTrue(detail.get("updatedAt").isNull());
        assertFalse(detail.get("pinned").asBoolean());
        var payload = article(null); // 旧客户端没有传封面/置顶，仍可新建。
        long newId = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
        assertFalse(send(HttpMethod.GET, "/api/admin/articles/" + newId, null, 200).get("pinned").asBoolean());
    }

    private long createCategory(String suffix) throws Exception {
        return send(HttpMethod.POST, "/api/admin/categories",
                Map.of("name", prefix + suffix), 201).get("id").asLong();
    }

    private Map<String, Object> article(Long categoryId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("title", prefix);
        payload.put("summary", "Isolated category regression test");
        payload.put("contentMarkdown", "# Draft body");
        payload.put("type", "NOTE");
        payload.put("categoryId", categoryId);
        return payload;
    }

    private long missingCategoryId() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM category", Long.class);
    }

    private JsonNode send(HttpMethod method, String path, Object body, int expected) throws Exception {
        var request = request(method, path).session(session).header("Authorization", AUTH)
                .header(csrfHeader, csrfToken);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? null : json.readTree(content);
    }

// 统一读取实际接口响应，分页测试需要访问整个对象。
private JsonNode publicResponse(String path, int expected) throws Exception {
    // URI 重载接受已经编码的查询字符，避免 %25 被再次编码。
    var result = mvc.perform(get(java.net.URI.create(path)))
            .andExpect(status().is(expected)).andReturn();
    String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return content.isBlank() ? null : json.readTree(content);
}

// 保留旧流程测试读取数组的语义，仅为公开文章列表取出 items。
// 详情、分类、标签与错误响应均原样返回。
private JsonNode publicGet(String path, int expected) throws Exception {
    var body = publicResponse(path, expected);
    if (expected == 200 && body != null
            && (path.equals("/api/articles") || path.startsWith("/api/articles?"))) {
        assertTrue(body.get("items").isArray());
        return body.get("items");
    }
    return body;
}

    private boolean containsId(JsonNode rows, long id) {
        for (var row : rows) if (row.get("id").asLong() == id) return true;
        return false;
    }
}
