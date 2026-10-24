package com.firefly.personalwebsite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin.password=presentation-test-only", "app.presentation=classpath:presentation-test.json"})
@AutoConfigureMockMvc
@Transactional
class ContentOrganizationFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void fixtures() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("pw_v2admin_acceptance_"),
                "Only run against a dedicated acceptance schema");
        for (int i = 1; i <= 4; i++) {
            jdbc.update("""
                INSERT INTO article(id,title,summary,content_markdown,type,status,published_at,deleted_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, 9100000L + i, i == 3 ? "PRIVATE-DRAFT" : "测试文章" + i, "摘要", "正文", "NOTE",
                    i == 3 ? "DRAFT" : "PUBLISHED", "2026-01-01 00:00:00", i == 4 ? "2026-01-02 00:00:00" : null);
        }
    }

    @Test
    void publicPresentationPreservesOrderDeduplicatesAndHidesPrivateEntries() throws Exception {
        var text = mvc.perform(get("/api/presentation")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var result = json.readTree(text);
        assertEquals(2, result.get("featured").size());
        assertEquals(2, result.get("projectArticles").size());
        assertEquals(9100001L, result.at("/projectArticles/0/id").asLong());
        assertEquals(9100002L, result.at("/featured/0/id").asLong());
        assertEquals(1, result.get("collections").size());
        assertEquals(2, result.at("/collections/0/articles").size());
        assertEquals(9100002L, result.at("/collections/0/articles/0/id").asLong());
        assertEquals(9100001L, result.at("/collections/0/articles/1/id").asLong());
        assertFalse(text.contains("PRIVATE-DRAFT"));
        assertFalse(text.contains("9100003"));
        assertFalse(text.contains("9100004"));
        assertFalse(text.contains("not-ready"));
        assertFalse(text.contains("contentMarkdown"));
        assertFalse(text.contains("articleIds"));
    }

    @Test
    void withdrawalImmediatelyRemovesFeaturedAndMakesInsufficientCollection404() throws Exception {
        mvc.perform(get("/api/collections/ordered-path")).andExpect(status().isOk());
        jdbc.update("UPDATE article SET status='DRAFT' WHERE id=9100002");
        mvc.perform(get("/api/collections/ordered-path")).andExpect(status().isNotFound());
        var result = json.readTree(mvc.perform(get("/api/presentation")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, result.get("collections").size());
        assertEquals(1, result.get("featured").size());
        assertEquals(9100001L, result.at("/featured/0/id").asLong());
    }

    @Test
    void recycleAndUnknownCollectionsDoNotExposeHiddenTitles() throws Exception {
        jdbc.update("UPDATE article SET deleted_at=NOW() WHERE id=9100001");
        for (String slug : List.of("ordered-path", "not-ready", "missing")) {
            mvc.perform(get("/api/collections/" + slug)).andExpect(status().isNotFound());
        }
    }

    @Test
    void configurationRejectsInvalidIdsDuplicateSlugsAndExcessiveSize() {
        PresentationController.validate(new PresentationController.Config(List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> PresentationController.validate(null));
        assertThrows(IllegalArgumentException.class, () -> PresentationController.validate(
                new PresentationController.Config(Arrays.asList(1L, null), List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> PresentationController.validate(
                new PresentationController.Config(List.of(1L, 2L, 3L, 4L), List.of(), List.of())));
        var valid = new PresentationController.CollectionConfig("java-path", "Java", "说明", List.of(1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> PresentationController.validate(
                new PresentationController.Config(List.of(), List.of(valid, valid), List.of())));
        var invalid = new PresentationController.CollectionConfig("../admin", "Java", "说明", List.of(1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> PresentationController.validate(
                new PresentationController.Config(List.of(), List.of(invalid), List.of())));
    }
}
