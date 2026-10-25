package com.firefly.personalwebsite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="app.admin.password=editing-test-only")
@AutoConfigureMockMvc
@Transactional
class ArticleEditingFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    MockHttpSession session;
    String token,header;
    final String auth="Basic "+Base64.getEncoder().encodeToString("admin:editing-test-only".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @BeforeEach void login() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("pw_v2admin_acceptance_"));
        var response=mvc.perform(get("/api/admin/csrf").header("Authorization",auth)).andExpect(status().isOk()).andReturn();
        session=(MockHttpSession)response.getRequest().getSession(false);
        var csrf=json.readTree(response.getResponse().getContentAsString());header=csrf.get("headerName").asText();token=csrf.get("token").asText();
    }
    Map<String,Object> payload(String text) {return new LinkedHashMap<>(Map.of("title",text,"summary",text,"contentMarkdown",text,"type","NOTE","tagIds",List.of(),"bodyMediaUrls",List.of()));}
    JsonNode call(String method,String path,Object body,String version,int expected) throws Exception {
        var request=request(HttpMethod.valueOf(method),path).header("Authorization",auth).header(header,token).session(session);
        if(version!=null)request.header("If-Match",version);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String text=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return text.isBlank()?null:json.readTree(text);
    }
    JsonNode read(String path) throws Exception {return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    String version(JsonNode row) {return "\""+row.get("version").asLong()+"\"";}

    @Test void editingIsPrivateUntilExplicitAtomicPublish() throws Exception {
        var created=call("POST","/api/admin/articles",payload("公开旧标题"),null,201);long id=created.get("id").asLong();String path="/api/admin/articles/"+id;
        var published=call("POST",path+"/publish",null,version(created),200);
        var before=read("/api/articles/"+id);
        long category=call("POST","/api/admin/categories",Map.of("name","edit-"+UUID.randomUUID()),null,201).get("id").asLong();
        long tag=call("POST","/api/admin/tags",Map.of("name","edit-"+UUID.randomUUID()),null,201).get("id").asLong();
        var body=payload("私有待发布词");body.put("categoryId",category);body.put("tagIds",List.of(tag));body.put("coverUrl","https://example.com/new.png");body.put("pinned",true);body.put("type","DIARY");
        var edited=call("PUT",path,body,version(published),200);
        assertTrue(edited.get("hasUnpublishedChanges").asBoolean());assertEquals(before,read("/api/articles/"+id));
        assertEquals(0,read("/api/articles?keyword=私有待发布词").get("total").asInt());
        assertEquals(0,read("/api/articles?categoryId="+category+"&tagId="+tag).get("total").asInt());
        var admin=call("GET","/api/admin/articles?type=DIARY&categoryId="+category,null,null,200);
        assertEquals(id,admin.get("items").get(0).get("id").asLong());assertTrue(admin.get("items").get(0).get("hasUnpublishedChanges").asBoolean());
        var latest=call("POST",path+"/publish",null,version(edited),200);
        var after=read("/api/articles/"+id);assertEquals("私有待发布词",after.get("contentMarkdown").asText());assertEquals(category,after.get("categoryId").asLong());assertEquals(tag,after.get("tags").get(0).get("id").asLong());assertTrue(after.get("pinned").asBoolean());assertEquals("DIARY",after.get("type").asText());assertEquals("https://example.com/new.png",after.get("coverUrl").asText());assertEquals(before.get("publishedAt"),after.get("publishedAt"));
        assertEquals(1,read("/api/articles?keyword=私有待发布词").get("total").asInt());
        assertFalse(call("GET",path,null,null,200).get("hasUnpublishedChanges").asBoolean());
        assertEquals(edited.get("version").asLong()+1,latest.get("version").asLong());
    }

    @Test void oldOrMissingVersionsCannotSavePublishOrChangeState() throws Exception {
        var first=call("POST","/api/admin/articles",payload("初稿"),null,201);String path="/api/admin/articles/"+first.get("id").asLong();
        var second=call("PUT",path,payload("另一窗口的新稿"),version(first),200);
        call("PUT",path,payload("旧窗口覆盖"),version(first),412);
        for(String action:List.of("publish","unpublish","restore"))call("POST",path+"/"+action,null,version(first),412);
        call("DELETE",path,null,version(first),412);
        call("PUT",path,payload("缺版本"),null,428);call("POST",path+"/publish",null,"*",400);
        call("POST",path+"/publish",null,"\"-1\"",400);
        var actual=call("GET",path,null,null,200);assertEquals("另一窗口的新稿",actual.get("title").asText());assertEquals(second.get("version"),actual.get("version"));assertEquals("DRAFT",actual.get("status").asText());
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void invalidMediaRollsBackWorkingCopyAndVersionAtHttpBoundary() throws Exception {
        var first=call("POST","/api/admin/articles",payload("回滚基线"),null,201);long id=first.get("id").asLong();String path="/api/admin/articles/"+id;
        try {
            var good=call("PUT",path,payload("已保存编辑稿"),version(first),200);
            var invalid=payload("不能留下的半成品");invalid.put("bodyMediaUrls",List.of("/api/media/00000000-0000-0000-0000-000000000000.png"));
            call("PUT",path,invalid,version(good),400);
            assertEquals(good,call("GET",path,null,null,200));
        } finally {jdbc.update("DELETE FROM article_edit WHERE article_id=?",id);jdbc.update("DELETE FROM article WHERE id=?",id);}
    }

    @Test void recycleRestoreKeepsWorkingCopyPrivateAndInvalidatesOldWindows() throws Exception {
        var first=call("POST","/api/admin/articles",payload("公开稿"),null,201);long id=first.get("id").asLong();String path="/api/admin/articles/"+id;
        var published=call("POST",path+"/publish",null,version(first),200);
        var edited=call("PUT",path,payload("回收后仍保留的编辑稿"),version(published),200);
        var recycled=call("DELETE",path,null,version(edited),200);
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isNotFound());
        call("PUT",path,payload("回收不能编辑"),version(recycled),409);
        var restored=call("POST",path+"/restore",null,version(recycled),200);
        assertEquals("DRAFT",restored.get("status").asText());
        assertEquals("回收后仍保留的编辑稿",call("GET",path,null,null,200).get("title").asText());
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isNotFound());
        call("POST",path+"/publish",null,version(edited),412);
        call("POST",path+"/publish",null,version(restored),200);
        assertEquals("回收后仍保留的编辑稿",read("/api/articles/"+id).get("title").asText());
    }
}
