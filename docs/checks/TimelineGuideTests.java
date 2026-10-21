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

@SpringBootTest(properties = "app.admin.password=timeline-guide-only")
@AutoConfigureMockMvc
@Transactional
class TimelineGuideTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void requireIsolatedSchema() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("pw_v2timeline_preflight_"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM article",Long.class));
    }

    @Test
    void typedMonthsExcludeDraftsAndOtherTypesIncludingDraftsWithDates() throws Exception {
        article("DIARY","PUBLISHED","2040-03-01 12:00:00",false);
        article("DIARY","PUBLISHED","2039-12-01 12:00:00",false);
        article("NOTE","PUBLISHED","2040-03-02 12:00:00",false);
        article("THOUGHT","PUBLISHED","2038-01-01 12:00:00",false);
        article("DIARY","DRAFT","2041-01-01 12:00:00",false);
        var diary=read("/api/articles/archive?type=DIARY");
        assertEquals(2,diary.size());
        assertEquals("2040-03",diary.get(0).get("month").asText());
        assertEquals(1,diary.get(0).get("total").asLong());
        assertEquals("2039-12",diary.get(1).get("month").asText());
        var all=read("/api/articles/archive");
        assertEquals(3,all.size());assertEquals(2,all.get(0).get("total").asLong());
        assertEquals(1,read("/api/articles/archive?type=NOTE").size());
        assertEquals(1,read("/api/articles/archive?type=THOUGHT").size());
        mvc.perform(get("/api/articles/archive?type=PRIVATE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles/archive?type=")).andExpect(status().isBadRequest());
    }

    @Test
    void diaryMonthPaginationUsesChronologyAndStableIdRatherThanPinnedOrUpdatedAt() throws Exception {
        long first=article("DIARY","PUBLISHED","2040-03-01 12:00:00",true);
        long last=0;
        for(int i=2;i<=12;i++) last=article("DIARY","PUBLISHED",String.format("2040-03-%02d 12:00:00",i),false);
        long same=article("DIARY","PUBLISHED","2040-03-12 12:00:00",false);
        article("NOTE","PUBLISHED","2040-03-31 12:00:00",true);
        long draft=article("DIARY","DRAFT","2040-03-31 13:00:00",true);
        jdbc.update("UPDATE article SET updated_at='2049-01-01 00:00:00' WHERE id=?",first);
        var one=read("/api/articles?type=DIARY&month=2040-03&page=1&size=10");
        assertEquals(13,one.get("total").asLong());assertEquals(10,one.get("items").size());
        assertEquals(same,one.get("items").get(0).get("id").asLong());
        assertEquals(last,one.get("items").get(1).get("id").asLong());
        var two=read("/api/articles?type=DIARY&month=2040-03&page=999&size=10");
        assertEquals(2,two.get("page").asInt());assertEquals(3,two.get("items").size());
        assertEquals(first,two.get("items").get(2).get("id").asLong());
        mvc.perform(get("/api/articles/"+draft)).andExpect(status().isNotFound());
        assertEquals(0,read("/api/articles?type=DIARY&month=2039-01").get("total").asLong());
    }

    @Test
    void emptyTypedDirectoryAndPublishWithdrawChangesDoNotLeakDraftMonth() throws Exception {
        assertEquals(0,read("/api/articles/archive?type=DIARY").size());
        long id=article("DIARY","DRAFT","2041-01-01 12:00:00",false);
        assertEquals(0,read("/api/articles/archive?type=DIARY").size());
        jdbc.update("UPDATE article SET status='PUBLISHED' WHERE id=?",id);
        assertEquals(1,read("/api/articles/archive?type=DIARY").size());
        jdbc.update("UPDATE article SET status='DRAFT' WHERE id=?",id);
        assertEquals(0,read("/api/articles/archive?type=DIARY").size());
    }

    private long article(String type,String state,String time,boolean pinned) {
        jdbc.update("INSERT INTO article(title,summary,content_markdown,type,status,published_at,pinned) VALUES ('timeline-test','summary','# body',?,?,?,?)",type,state,time,pinned);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
    private JsonNode read(String path) throws Exception {
        var result=mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
