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

@SpringBootTest(properties = "app.admin.password=topics-guide-test-only")
@AutoConfigureMockMvc
@Transactional
class TopicGuideTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void requireIsolatedSchema() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("pw_v2topics_preflight_"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM article",Long.class));
    }

    @Test
    void countsOnlyPublicUseAndDoesNotDuplicateMultiTagArticles() throws Exception {
        long category = category("public-category"), draftCategory = category("draft-category");
        category("unused-category");
        long tag = tag("shared-tag"), other = tag("other-tag"), secret = tag("draft-tag");
        tag("unused-tag");
        long first = article(category,"PUBLISHED"), second = article(category,"PUBLISHED");
        long unclassified = article(null,"PUBLISHED"), draft = article(draftCategory,"DRAFT");
        for(long id : new long[]{first,second,unclassified,draft}) link(id,tag);
        link(first,other); link(draft,secret);
        var result = topics();
        assertEquals(1,result.get("categories").size());
        assertEquals(String.valueOf(category),result.get("categories").get(0).get("id").asText());
        assertTrue(result.get("categories").get(0).get("id").isString());
        assertEquals(2,result.get("categories").get(0).get("total").asLong());
        assertEquals(2,result.get("tags").size());
        assertEquals("shared-tag",result.get("tags").get(0).get("name").asText());
        assertEquals(3,result.get("tags").get(0).get("total").asLong());
        assertEquals(1,result.get("tags").get(1).get("total").asLong());
        assertFalse(result.toString().contains("draft-"));
        assertFalse(result.toString().contains("unused-"));
    }

    @Test
    void emptyAndDraftOnlyDirectoriesHaveNoNamesOrCounts() throws Exception {
        assertEquals(0,topics().get("categories").size());
        long category = category("private-topic"), tag = tag("private-tag");
        long draft = article(category,"DRAFT"); link(draft,tag);
        jdbc.update("UPDATE article SET published_at='2050-01-01 00:00:00' WHERE id=?",draft);
        var result = topics();
        assertEquals(0,result.get("categories").size());
        assertEquals(0,result.get("tags").size());
        // 原维护接口仍包含未使用的元数据，不改变后台选择及新建流程。
        mvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void publishRenameDetachAndUnpublishUpdateDirectoryImmediately() throws Exception {
        long category = category("before-name"), tag = tag("before-tag");
        long id = article(category,"DRAFT"); link(id,tag);
        assertEquals(0,topics().get("tags").size());
        jdbc.update("UPDATE article SET status='PUBLISHED' WHERE id=?",id);
        assertEquals(1,topics().get("tags").get(0).get("total").asLong());
        jdbc.update("UPDATE category SET name='renamed' WHERE id=?",category);
        jdbc.update("UPDATE tag SET name='renamed-tag' WHERE id=?",tag);
        assertEquals("renamed",topics().get("categories").get(0).get("name").asText());
        assertEquals("renamed-tag",topics().get("tags").get(0).get("name").asText());
        jdbc.update("DELETE FROM article_tag WHERE article_id=?",id);
        assertEquals(0,topics().get("tags").size());
        jdbc.update("UPDATE article SET status='DRAFT' WHERE id=?",id);
        assertEquals(0,topics().get("categories").size());
    }

    private long category(String name) { jdbc.update("INSERT INTO category(name) VALUES (?)",name); return last(); }
    private long tag(String name) { jdbc.update("INSERT INTO tag(name) VALUES (?)",name); return last(); }
    private long article(Long category,String state) {
        jdbc.update("INSERT INTO article(title,summary,content_markdown,type,status,published_at,category_id) VALUES ('topic-test','summary','# body','NOTE',?,'2050-01-01 00:00:00',?)",state,category);
        return last();
    }
    private long last() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); }
    private void link(long article,long tag) { jdbc.update("INSERT INTO article_tag(article_id,tag_id) VALUES (?,?)",article,tag); }
    private JsonNode topics() throws Exception {
        var result = mvc.perform(get("/api/topics")).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
