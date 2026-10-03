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
class CategoryArticleFlowTests {
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
    void categoryCreateRenameDuplicateAndValidation() throws Exception {
        var first = send(HttpMethod.POST, "/api/admin/categories",
                Map.of("name", "  " + prefix + "  "), 201);
        long id = first.get("id").asLong();
        assertEquals(prefix, first.get("name").asText());
        assertTrue(containsId(publicGet("/api/categories", 200), id));

        send(HttpMethod.POST, "/api/admin/categories", Map.of("name", prefix), 409);
        send(HttpMethod.POST, "/api/admin/categories", Map.of("name", "  "), 400);
        send(HttpMethod.POST, "/api/admin/categories", Map.of("name", "x".repeat(51)), 400);

        var other = send(HttpMethod.POST, "/api/admin/categories",
                Map.of("name", prefix + "-other"), 201);
        send(HttpMethod.PUT, "/api/admin/categories/" + other.get("id").asLong(),
                Map.of("name", prefix), 409);

        String renamed = prefix + "-renamed";
        assertEquals(renamed, send(HttpMethod.PUT, "/api/admin/categories/" + id,
                Map.of("name", renamed), 200).get("name").asText());
        send(HttpMethod.PUT, "/api/admin/categories/" + id, Map.of("name", renamed), 200);
        send(HttpMethod.PUT, "/api/admin/categories/" + missingCategoryId(),
                Map.of("name", prefix + "-missing"), 404);
    }

    @Test
    void draftPublishEditRenameFilterAndClearCategory() throws Exception {
        long first = createCategory("-one");
        long second = createCategory("-two");
        var payload = article(first);
        var created = send(HttpMethod.POST, "/api/admin/articles", payload, 201);
        long id = created.get("id").asLong();
        String adminPath = "/api/admin/articles/" + id;
        String publicPath = "/api/articles/" + id;
        assertEquals("DRAFT", created.get("status").asText());
        assertEquals(first, send(HttpMethod.GET, adminPath, null, 200).get("categoryId").asLong());
        publicGet(publicPath, 404);
        assertFalse(containsId(publicGet("/api/articles?categoryId=" + first, 200), id));

        send(HttpMethod.POST, adminPath + "/publish", null, 200);
        var published = publicGet(publicPath, 200);
        assertEquals(prefix + "-one", published.get("categoryName").asText());
        String timestamp = published.get("publishedAt").asText();
        assertTrue(containsId(publicGet("/api/articles?type=NOTE&categoryId=" + first, 200), id));
        assertFalse(containsId(publicGet("/api/articles?type=DIARY&categoryId=" + first, 200), id));

        payload.put("categoryId", second);
        payload.put("title", prefix + "-edited");
        payload.put("contentMarkdown", "# Edited body");
        var edited = send(HttpMethod.PUT, adminPath, payload, 200);
        assertEquals("PUBLISHED", edited.get("status").asText());
        assertEquals(second, edited.get("categoryId").asLong());
        send(HttpMethod.PUT, adminPath, payload, 200);
        assertFalse(containsId(publicGet("/api/articles?categoryId=" + first, 200), id));
        assertTrue(containsId(publicGet("/api/articles?categoryId=" + second, 200), id));
        assertEquals("# Edited body", publicGet(publicPath, 200).get("contentMarkdown").asText());

        send(HttpMethod.PUT, "/api/admin/categories/" + second,
                Map.of("name", prefix + "-renamed"), 200);
        assertEquals(prefix + "-renamed", publicGet(publicPath, 200).get("categoryName").asText());

        payload.put("categoryId", null);
        assertTrue(send(HttpMethod.PUT, adminPath, payload, 200).get("categoryId").isNull());
        var cleared = publicGet(publicPath, 200);
        assertTrue(cleared.get("categoryId").isNull());
        assertTrue(cleared.get("categoryName").isNull());
        assertEquals(timestamp, cleared.get("publishedAt").asText());
        assertTrue(containsId(publicGet("/api/articles", 200), id));
        assertFalse(containsId(publicGet("/api/articles?categoryId=" + second, 200), id));
        send(HttpMethod.POST, adminPath + "/publish", null, 200);
        assertEquals(timestamp, publicGet(publicPath, 200).get("publishedAt").asText());
    }

    @Test
    void unclassifiedDraftAndInvalidCategoryNeverLeakOrOverwrite() throws Exception {
        var payload = article(null);
        long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
        String path = "/api/admin/articles/" + id;
        assertTrue(send(HttpMethod.GET, path, null, 200).get("categoryId").isNull());
        publicGet("/api/articles/" + id, 404);
        for (long invalid : new long[]{-1, 0, missingCategoryId()}) {
            payload.put("categoryId", invalid);
            send(HttpMethod.POST, "/api/admin/articles", payload, 400);
            send(HttpMethod.PUT, path, payload, 400);
        }
        assertTrue(send(HttpMethod.GET, path, null, 200).get("categoryId").isNull());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM article WHERE title = ?", Long.class, prefix));
        publicGet("/api/articles?categoryId=-1", 400);
        publicGet("/api/articles?categoryId=0", 400);
        publicGet("/api/articles?categoryId=invalid", 400);
        publicGet("/api/articles?type=INVALID", 400);
    }

    @Test
    void administratorAuthenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/articles")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/categories").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", prefix))))
                .andExpect(status().isForbidden());
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM category WHERE name = ?", Long.class, prefix));
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

    private JsonNode publicGet(String path, int expected) throws Exception {
        var result = mvc.perform(get(path)).andExpect(status().is(expected)).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? null : json.readTree(content);
    }

    private boolean containsId(JsonNode rows, long id) {
        for (var row : rows) if (row.get("id").asLong() == id) return true;
        return false;
    }
}
