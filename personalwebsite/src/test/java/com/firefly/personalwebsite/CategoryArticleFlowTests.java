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
        assertTrue(containsId(publicGet("/api/articles?keyword=" + prefix, 200), id));
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

    @Test
    void tagCreateRenameDuplicateAndValidation() throws Exception {
        String name = prefix + "-tag";
        var created = send(HttpMethod.POST, "/api/admin/tags", Map.of("name", "  " + name + "  "), 201);
        long id = created.get("id").asLong();
        assertEquals(name, created.get("name").asText());
        assertTrue(containsId(publicGet("/api/tags", 200), id));
        assertTrue(containsId(send(HttpMethod.GET, "/api/admin/tags", null, 200), id));
        send(HttpMethod.POST, "/api/admin/tags", Map.of("name", name), 409);
        send(HttpMethod.POST, "/api/admin/tags", Map.of("name", "   "), 400);
        send(HttpMethod.POST, "/api/admin/tags", Map.of("name", "x".repeat(51)), 400);
        var other = send(HttpMethod.POST, "/api/admin/tags", Map.of("name", name + "-other"), 201);
        send(HttpMethod.PUT, "/api/admin/tags/" + other.get("id").asLong(), Map.of("name", name), 409);
        String renamed = name + "-renamed";
        var updated = send(HttpMethod.PUT, "/api/admin/tags/" + id, Map.of("name", renamed), 200);
        assertEquals(id, updated.get("id").asLong());
        assertEquals(renamed, updated.get("name").asText());
        send(HttpMethod.PUT, "/api/admin/tags/" + id, Map.of("name", renamed), 200);
        long missing = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM tag", Long.class);
        send(HttpMethod.PUT, "/api/admin/tags/" + missing, Map.of("name", prefix), 404);
        mvc.perform(get("/api/admin/tags")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/tags").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", prefix + "-blocked"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void articleTagsSavePublishRenameAndClear() throws Exception {
        long first = send(HttpMethod.POST, "/api/admin/tags",
                Map.of("name", prefix + "-tag-one"), 201).get("id").asLong();
        long second = send(HttpMethod.POST, "/api/admin/tags",
                Map.of("name", prefix + "-tag-two"), 201).get("id").asLong();

        var payload = article(null);
        payload.put("tagIds", java.util.List.of(first, second, first));
        long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
                .get("id").asLong();
        String adminPath = "/api/admin/articles/" + id;
        String publicPath = "/api/articles/" + id;

        // 重复编号去重；草稿的公开详情和列表都不可见。
        assertEquals(2, send(HttpMethod.GET, adminPath, null, 200)
                .get("tagIds").size());
        publicGet(publicPath, 404);
        assertFalse(containsId(publicGet("/api/articles", 200), id));

        send(HttpMethod.POST, adminPath + "/publish", null, 200);
        var published = publicGet(publicPath, 200);
        assertEquals(2, published.get("tags").size());
        assertEquals(first, published.get("tags").get(0).get("id").asLong());
        String publishedAt = published.get("publishedAt").asText();

        var list = publicGet("/api/articles?keyword=" + prefix, 200);
        boolean found = false;
        for (var item : list) {
            if (item.get("id").asLong() == id) {
                assertEquals(2, item.get("tags").size());
                found = true;
            }
        }
        assertTrue(found);

        send(HttpMethod.PUT, "/api/admin/tags/" + first,
                Map.of("name", prefix + "-renamed"), 200);
        assertEquals(prefix + "-renamed", publicGet(publicPath, 200)
                .get("tags").get(0).get("name").asText());

        // 无效标签返回 400，校验失败时不替换已有标签。
        long missing = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000 FROM tag", Long.class);
        payload.put("title", prefix + "-invalid-edit");
        payload.put("tagIds", java.util.List.of(missing));
        send(HttpMethod.PUT, adminPath, payload, 400);
        assertEquals(2, publicGet(publicPath, 200).get("tags").size());

        payload.put("title", prefix);
        payload.put("tagIds", java.util.List.of(second));
        assertEquals(1, send(HttpMethod.PUT, adminPath, payload, 200)
                .get("tagIds").size());
        assertEquals(second, publicGet(publicPath, 200)
                .get("tags").get(0).get("id").asLong());

        payload.put("tagIds", java.util.List.of());
        send(HttpMethod.PUT, adminPath, payload, 200);
        var cleared = publicGet(publicPath, 200);
        assertEquals(0, cleared.get("tags").size());
        assertEquals(publishedAt, cleared.get("publishedAt").asText());
        assertTrue(containsId(publicGet("/api/tags", 200), first));
        assertTrue(containsId(publicGet("/api/tags", 200), second));
    }

    @Test
    void tagFilterCombinesWithTypeAndCategoryWithoutLeakingDrafts() throws Exception {
        long category = createCategory("-filter-category");
        long otherCategory = createCategory("-filter-other");
        long firstTag = send(HttpMethod.POST, "/api/admin/tags",
                Map.of("name", prefix + "-filter-one"), 201).get("id").asLong();
        long secondTag = send(HttpMethod.POST, "/api/admin/tags",
                Map.of("name", prefix + "-filter-two"), 201).get("id").asLong();

        var payload = article(category);
        payload.put("tagIds", java.util.List.of(firstTag, secondTag));
        long note = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
                .get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + note + "/publish", null, 200);

        payload.put("title", prefix + "-thought");
        payload.put("type", "THOUGHT");
        payload.put("tagIds", java.util.List.of(firstTag));
        long thought = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
                .get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + thought + "/publish", null, 200);

        payload.put("title", prefix + "-other-category");
        payload.put("type", "NOTE");
        payload.put("categoryId", otherCategory);
        long other = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
                .get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + other + "/publish", null, 200);

        payload.put("title", prefix + "-diary-draft");
        payload.put("type", "DIARY");
        payload.put("categoryId", category);
        long draft = send(HttpMethod.POST, "/api/admin/articles", payload, 201)
                .get("id").asLong();

        var byTag = publicGet("/api/articles?tagId=" + firstTag, 200);
        assertEquals(3, byTag.size());
        assertTrue(containsId(byTag, note));
        assertTrue(containsId(byTag, thought));
        assertTrue(containsId(byTag, other));
        assertFalse(containsId(byTag, draft));

        String combined = "/api/articles?type=NOTE&categoryId="
                + category + "&tagId=" + firstTag;
        var filtered = publicGet(combined, 200);
        assertEquals(1, filtered.size());
        assertEquals(note, filtered.get(0).get("id").asLong());
        assertEquals(2, filtered.get(0).get("tags").size());

        assertEquals(1, publicGet("/api/articles?tagId=" + secondTag, 200).size());
        assertEquals(0, publicGet("/api/articles?type=DIARY&tagId=" + firstTag, 200).size());
        long missingTag = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000 FROM tag", Long.class);
        assertEquals(0, publicGet("/api/articles?tagId=" + missingTag, 200).size());
        publicGet("/api/articles?tagId=0", 400);
        publicGet("/api/articles?tagId=-1", 400);
        publicGet("/api/articles?tagId=invalid", 400);
    }
@Test
void articleSearchPaginationAndValidation() throws Exception {
    long category = createCategory("-search");
    long tag = send(HttpMethod.POST, "/api/admin/tags",
            Map.of("name", prefix + "-search"), 201).get("id").asLong();
    var ids = new java.util.ArrayList<Long>();

    // 共 23 篇：第一页 10 篇，第二页 10 篇，第三页 3 篇。
    for (int i = 0; i < 23; i++) {
        var payload = article(category);
        // 只有第一篇标题含关键词；其余依靠摘要匹配。
        payload.put("title", i == 0 ? prefix + "-needle" : prefix + "-plain-" + i);
        payload.put("summary", i == 0 ? "没有关键词" : prefix + "-needle");
        payload.put("tagIds", java.util.List.of(tag));
        long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + id + "/publish", null, 200);
        ids.add(id);
    }

    // 固定相同发布时间，验证第二排序键 id 以及跨页不重复。
    for (long id : ids) {
        jdbc.update("UPDATE article SET published_at = '2026-01-01 12:00:00' WHERE id = ?", id);
    }
    var draftPayload = article(category);
    draftPayload.put("title", prefix + "-needle-draft");
    draftPayload.put("tagIds", java.util.List.of(tag));
    long draft = send(HttpMethod.POST, "/api/admin/articles", draftPayload, 201).get("id").asLong();

    String base = "/api/articles?type=NOTE&categoryId=" + category
            + "&tagId=" + tag + "&keyword=" + prefix + "-needle";
    var first = publicResponse(base, 200); // 默认 page=1、size=10。
    assertEquals(23, first.get("total").asLong());
    assertEquals(1, first.get("page").asInt());
    assertEquals(10, first.get("size").asInt());
    assertEquals(3, first.get("totalPages").asLong());
    assertEquals(10, first.get("items").size());
    assertFalse(containsId(first.get("items"), draft));
    assertEquals(tag, first.get("items").get(0).get("tags").get(0).get("id").asLong());
    var second = publicResponse(base + "&page=2", 200);
    var third = publicResponse(base + "&page=3", 200);
    assertEquals(10, second.get("items").size());
    assertEquals(3, third.get("items").size());
    // 标题命中优先；其余摘要命中按时间、编号倒序，跨页不重复。
    var actualIds = new java.util.ArrayList<Long>();
    for (var result : java.util.List.of(first, second, third)) {
        for (var item : result.get("items")) actualIds.add(item.get("id").asLong());
    }
    var expectedIds = new java.util.ArrayList<>(ids);
    java.util.Collections.reverse(expectedIds);
    expectedIds.remove(ids.get(0));
    expectedIds.add(0, ids.get(0));
    assertEquals(expectedIds, actualIds);

    var beyond = publicResponse(base + "&page=999", 200);
    assertEquals(3, beyond.get("page").asInt());
    assertEquals(third.get("items"), beyond.get("items"));
    var empty = publicResponse(base + "-missing", 200);
    assertEquals(0, empty.get("total").asLong());
    assertEquals(0, empty.get("totalPages").asLong());
    assertEquals(1, empty.get("page").asInt());
    assertEquals(0, empty.get("items").size());
    var small = publicResponse(base + "&size=1&page=2", 200);
    assertEquals(23, small.get("totalPages").asLong());
    assertEquals(ids.get(22).longValue(), small.get("items").get(0).get("id").asLong());
    var blank = publicResponse("/api/articles?categoryId=" + category + "&keyword=%20%20", 200);
    assertEquals(23, blank.get("total").asLong());

    for (String invalid : java.util.List.of(
            "page=0", "page=-1", "page=1000001", "page=invalid",
            "size=0", "size=-1", "size=51", "size=invalid")) {
        publicResponse("/api/articles?" + invalid, 400);
    }
    publicResponse("/api/articles?keyword=" + "a".repeat(101), 400);
}

@Test
void searchTreatsSymbolsLiterallyAndIncludesBody() throws Exception {
    long category = createCategory("-literal");
    for (String title : java.util.List.of(prefix + "100%", prefix + "under_score",
            prefix + "bang!", prefix + "plain")) {
        var payload = article(category);
        payload.put("title", title);
        payload.put("summary", "没有搜索符号");
        payload.put("contentMarkdown", prefix + "-body-only");
        long id = send(HttpMethod.POST, "/api/admin/articles", payload, 201).get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + id + "/publish", null, 200);
    }
    String base = "/api/articles?categoryId=" + category + "&keyword=";
    // URL 编码的 %、_、!，只应命中包含对应字符的那一篇。
    for (String symbol : java.util.List.of("%25", "_", "%21")) {
        assertEquals(1, publicResponse(base + symbol, 200).get("total").asLong());
    }
    var bodyResults = publicResponse(base + prefix + "-body-only", 200);
    assertEquals(4, bodyResults.get("total").asLong());
    for (var item : bodyResults.get("items")) {
        var match = bodyResults.get("matches").get(item.get("id").asText());
        assertEquals("body", match.get("field").asText());
        assertEquals(prefix + "-body-only", match.get("match").asText());
    }
    assertEquals(0, publicResponse(base + "%27%20OR%201%3D1%20--", 200).get("total").asLong());
}

    @Test
    void archiveCatalogMonthBoundariesDraftIsolationAndPaging() throws Exception {
        // 先记录真实库已有目录，新增测试内容只检查数量增量。
        var before = publicResponse("/api/articles/archive", 200);
        var baseline = new java.util.HashMap<String, Long>();
        for (var item : before) baseline.put(item.get("month").asText(), item.get("total").asLong());
        long category = createCategory("-archive");
        var februaryIds = new java.util.ArrayList<Long>();
        for (int i = 0; i < 12; i++) {
            long id = send(HttpMethod.POST, "/api/admin/articles", article(category), 201).get("id").asLong();
            send(HttpMethod.POST, "/api/admin/articles/" + id + "/publish", null, 200);
            // 2040 是闰年，覆盖月初和闰日最后一秒。
            jdbc.update("UPDATE article SET published_at = ? WHERE id = ?",
                    i == 0 ? "2040-02-01 00:00:00" : "2040-02-29 23:59:59", id);
            februaryIds.add(id);
        }
        long marchId = send(HttpMethod.POST, "/api/admin/articles", article(category), 201).get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + marchId + "/publish", null, 200);
        jdbc.update("UPDATE article SET published_at = '2040-03-01 00:00:00' WHERE id = ?", marchId);
        long previousYear = send(HttpMethod.POST, "/api/admin/articles", article(category), 201).get("id").asLong();
        send(HttpMethod.POST, "/api/admin/articles/" + previousYear + "/publish", null, 200);
        jdbc.update("UPDATE article SET published_at = '2039-12-31 23:59:59' WHERE id = ?", previousYear);
        long draft = send(HttpMethod.POST, "/api/admin/articles", article(category), 201).get("id").asLong();
        // 即使草稿存在日期，也必须按 status 隔离。
        jdbc.update("UPDATE article SET published_at = '2042-01-01 00:00:00' WHERE id = ?", draft);

        var archive = publicResponse("/api/articles/archive", 200);
        var counts = new java.util.HashMap<String, Long>();
        String previous = null;
        for (var item : archive) {
            String month = item.get("month").asText();
            assertTrue(month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"));
            if (previous != null) assertTrue(previous.compareTo(month) > 0);
            assertNull(counts.put(month, item.get("total").asLong()));
            previous = month;
        }
        assertEquals(baseline.getOrDefault("2040-02", 0L) + 12, counts.get("2040-02").longValue());
        assertEquals(baseline.getOrDefault("2040-03", 0L) + 1, counts.get("2040-03").longValue());
        assertEquals(baseline.getOrDefault("2039-12", 0L) + 1, counts.get("2039-12").longValue());
        assertEquals(baseline.get("2042-01"), counts.get("2042-01"));
        String base = "/api/articles?month=2040-02&categoryId=" + category + "&type=NOTE&keyword=" + prefix;
        var first = publicResponse(base, 200);
        var second = publicResponse(base + "&page=2", 200);
        assertEquals(12, first.get("total").asLong());
        assertEquals(2, first.get("totalPages").asLong());
        assertEquals(10, first.get("items").size());
        assertEquals(2, second.get("items").size());
        var actualIds = new java.util.HashSet<Long>();
        for (var result : java.util.List.of(first, second)) {
            for (var item : result.get("items")) {
                assertTrue(item.get("publishedAt").asText().startsWith("2040-02"));
                assertTrue(actualIds.add(item.get("id").asLong()));
            }
        }
        assertEquals(new java.util.HashSet<>(februaryIds), actualIds);
        var march = publicResponse("/api/articles?month=2040-03&categoryId=" + category, 200);
        assertEquals(1, march.get("total").asLong());
        assertEquals(marchId, march.get("items").get(0).get("id").asLong());
        assertEquals(0, publicResponse("/api/articles?month=2042-01&categoryId=" + category, 200).get("total").asLong());
        publicResponse("/api/articles/" + draft, 404);
        assertEquals(2, publicResponse(base + "&page=999", 200).get("page").asInt());
        for (String invalid : java.util.List.of("", "2040-2", "2040-00", "2040-13", "0000-01", "invalid")) {
            publicResponse("/api/articles?month=" + invalid, 400);
        }
        publicResponse("/api/articles?month=1000-01", 200);
        publicResponse("/api/articles?month=9999-12", 200);
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
