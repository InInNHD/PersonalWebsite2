package com.firefly.personalwebsite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 助手维护的指南预检：只允许空的专用测试库，全部数据随事务回滚。
@SpringBootTest(properties = "app.admin.password=navigation-guide-test-only")
@AutoConfigureMockMvc
@Transactional
class ArticleNavigationGuideTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void requireIsolatedEmptySchema() {
        String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(schema);
        assertTrue(schema.startsWith("pw_v2c_preflight_"), "必须使用助手创建的专用测试库");
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM article", Long.class));
    }

    @Test
    void chronologicalNeighborsExcludeDraftsAndIgnorePinUpdateAndType() throws Exception {
        long old = insert("old", "PUBLISHED", "2050-01-01 00:00:00", "DIARY");
        long sameFirst = insert("same-first", "PUBLISHED", "2050-02-01 00:00:00", "NOTE");
        long draft = insert("secret-diary", "DRAFT", "2050-02-01 00:00:00", "DIARY");
        long sameLast = insert("same-last", "PUBLISHED", "2050-02-01 00:00:00", "THOUGHT");
        long newer = insert("newer", "PUBLISHED", "2050-03-01 00:00:00", "NOTE");
        jdbc.update("UPDATE article SET pinned = TRUE, updated_at = '2099-01-01 00:00:00' WHERE id = ?", old);
        var result = read(sameFirst, 200);
        assertEquals(old, result.get("previous").get("id").asLong());
        assertEquals(sameLast, result.get("next").get("id").asLong());
        assertEquals(3, result.get("previous").size());
        assertEquals("old", result.get("previous").get("title").asText());
        assertTrue(result.get("previous").has("publishedAt"));
        assertFalse(result.toString().contains("secret-diary"));
        assertFalse(result.toString().contains("contentMarkdown"));
        result = read(sameLast, 200);
        assertEquals(sameFirst, result.get("previous").get("id").asLong());
        assertEquals(newer, result.get("next").get("id").asLong());
        read(draft, 404);
        read(newer + 1000, 404);
    }

    @Test
    void singleArticleEdgesAndUnpublishedNeighborAreHandled() throws Exception {
        long one = insert("one", "PUBLISHED", "2050-01-01 00:00:00", "NOTE");
        insert("draft-without-date", "DRAFT", null, "DIARY");
        insert("draft-with-date", "DRAFT", "2050-01-02 00:00:00", "DIARY");
        var alone = read(one, 200);
        assertTrue(alone.get("previous").isNull());
        assertTrue(alone.get("next").isNull());
        long middle = insert("middle", "PUBLISHED", "2050-02-01 00:00:00", "NOTE");
        long last = insert("last", "PUBLISHED", "2050-03-01 00:00:00", "NOTE");
        assertTrue(read(one, 200).get("previous").isNull());
        assertTrue(read(last, 200).get("next").isNull());
        jdbc.update("UPDATE article SET status = 'DRAFT' WHERE id = ?", middle);
        read(middle, 404);
        assertEquals(last, read(one, 200).get("next").get("id").asLong());
        assertEquals(one, read(last, 200).get("previous").get("id").asLong());
    }

    @Test
    void invalidIdsAndPublishedRowsWithoutDateDoNotLeak() throws Exception {
        long valid = insert("valid", "PUBLISHED", "2050-01-01 00:00:00", "NOTE");
        long noDate = insert("invalid-public-row", "PUBLISHED", null, "NOTE");
        read(noDate, 404);
        read(0, 404);
        read(-1, 404);
        mvc.perform(get("/api/articles/not-a-number/neighbors")).andExpect(status().isBadRequest());
        assertTrue(read(valid, 200).get("previous").isNull());
        assertTrue(read(valid, 200).get("next").isNull());
    }

    private long insert(String title, String state, String date, String type) {
        jdbc.update("""
                INSERT INTO article (title, summary, content_markdown, type, status, published_at)
                VALUES (?, 'navigation test', '# body', ?, ?, ?)
                """, title, type, state, date);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private JsonNode read(long id, int expected) throws Exception {
        var result = mvc.perform(get("/api/articles/" + id + "/neighbors"))
                .andExpect(status().is(expected)).andReturn();
        if (expected != 200) return null;
        return json.readTree(result.getResponse().getContentAsString());
    }
}
