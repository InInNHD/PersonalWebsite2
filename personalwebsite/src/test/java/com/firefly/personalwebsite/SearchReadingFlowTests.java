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
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="app.admin.password=search-flow-test-only")
@AutoConfigureMockMvc
@Transactional
class SearchReadingFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach void guard() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("pw_v2admin_acceptance_"));
    }
    void article(long id,String title,String summary,String body,String state,boolean deleted) {
        jdbc.update("""
            INSERT INTO article(id,title,summary,content_markdown,type,status,published_at,deleted_at)
            VALUES (?,?,?,?,'NOTE',?,'2026-10-01 10:00:00',?)
            """,id,title,summary,body,state,deleted?"2026-10-02 00:00:00":null);
    }
    JsonNode search(String keyword,int page,int size) throws Exception {
        return json.readTree(mvc.perform(get("/api/articles").param("keyword",keyword)
                .param("page",String.valueOf(page)).param("size",String.valueOf(size)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void bodyOnlySearchReturnsBoundedUnicodeSnippetAndNoFullBody() throws Exception {
        article(9200001,"普通标题","普通摘要","前".repeat(100)+"🌱并发一致性🌱"+"后".repeat(100),"PUBLISHED",false);
        var result=search("并发一致性",1,10);
        assertEquals(1,result.get("total").asInt());
        var hit=result.at("/matches/9200001");
        assertEquals("body",hit.get("field").asText());
        assertEquals("并发一致性",hit.get("match").asText());
        assertEquals(60,hit.get("before").asText().codePointCount(0,hit.get("before").asText().length()));
        assertTrue(hit.get("leading").asBoolean());assertTrue(hit.get("trailing").asBoolean());
        assertFalse(result.toString().contains("contentMarkdown"));
    }
    @Test void titleThenSummaryThenBodyAndStablePagination() throws Exception {
        article(9200010,"排序验证needle","摘要","正文","PUBLISHED",false);
        article(9200011,"普通标题","排序验证needle","正文","PUBLISHED",false);
        article(9200012,"普通标题","摘要","排序验证needle","PUBLISHED",false);
        article(9200013,"普通标题","摘要","排序验证needle","PUBLISHED",false);
        List<Long> ids=new ArrayList<>();
        for(int page=1;page<=4;page++) {
            var result=search("排序验证needle",page,1);assertEquals(4,result.get("total").asInt());
            ids.add(result.at("/items/0/id").asLong());assertEquals(1,result.get("matches").size());
        }
        assertEquals(List.of(9200010L,9200011L,9200013L,9200012L),ids);
        assertEquals("title",search("排序验证needle",1,1).at("/matches/9200010/field").asText());
        assertEquals("summary",search("排序验证needle",2,1).at("/matches/9200011/field").asText());
    }
    @Test void draftsRecycleAndWithdrawalNeverEnterResultsOrSnippets() throws Exception {
        article(9200020,"公开","摘要","隐私边界验证","PUBLISHED",false);
        article(9200021,"SECRET-DRAFT","摘要","隐私边界验证","DRAFT",false);
        article(9200022,"SECRET-RECYCLED","摘要","隐私边界验证","PUBLISHED",true);
        var result=search("隐私边界验证",1,10);
        assertEquals(1,result.get("total").asInt());assertFalse(result.toString().contains("SECRET"));
        jdbc.update("UPDATE article SET status='DRAFT' WHERE id=9200020");
        result=search("隐私边界验证",1,10);assertEquals(0,result.get("total").asInt());assertEquals(0,result.get("matches").size());
    }
    @Test void literalSymbolsEnglishCaseEmojiAndHtmlRemainOrdinarySearchText() throws Exception {
        article(9200030,"符号测试","摘要","C++ value_%! Java 🌱 <img src=x onerror=alert(1)>","PUBLISHED",false);
        for(String word:List.of("C++","value_%!","java","🌱","<img src=x")) {
            var result=search(word,1,10);assertEquals(1,result.get("total").asInt(),word);
            assertEquals(word.toLowerCase(Locale.ROOT),result.at("/matches/9200030/match").asText().toLowerCase(Locale.ROOT));
        }
        assertEquals(0,search("%' OR 1=1 --",1,10).get("total").asInt());
    }
    @Test void categoryTagAndMonthFiltersStillApplyToBodyMatches() throws Exception {
        article(9200040,"组合筛选","摘要","正文组合关键词","PUBLISHED",false);
        article(9200041,"组合筛选","摘要","正文组合关键词","PUBLISHED",false);
        jdbc.update("INSERT INTO category(id,name) VALUES (9200040,'搜索测试分类')");
        jdbc.update("INSERT INTO tag(id,name) VALUES (9200040,'搜索测试标签')");
        jdbc.update("UPDATE article SET category_id=9200040 WHERE id=9200040");
        jdbc.update("INSERT INTO article_tag(article_id,tag_id) VALUES(9200040,9200040)");
        var result=json.readTree(mvc.perform(get("/api/articles").param("keyword","正文组合关键词")
                .param("categoryId","9200040").param("tagId","9200040").param("month","2026-10").param("type","NOTE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,result.get("total").asInt());assertEquals(9200040,result.at("/items/0/id").asLong());
    }
    @Test void emptySearchHasNoSnippetsAndExistingBoundsRemain() throws Exception {
        assertEquals(0,search(" ",1,10).get("matches").size());
        mvc.perform(get("/api/articles").param("keyword","x".repeat(101))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("keyword","词").param("size","51")).andExpect(status().isBadRequest());
    }
}
