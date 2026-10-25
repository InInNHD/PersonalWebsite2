package com.firefly.personalwebsite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.admin.password=workflow-test-only")
@AutoConfigureMockMvc
@Transactional
class AdminWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final String auth="Basic "+Base64.getEncoder().encodeToString("admin:workflow-test-only".getBytes(StandardCharsets.UTF_8));
    private MockHttpSession session;
    private String token, header, prefix;
    @BeforeEach void login() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("pw_v2admin_acceptance_"),"Only run against a dedicated acceptance schema");
        prefix="workflow-"+UUID.randomUUID();
        var result=mvc.perform(get("/api/admin/csrf").header("Authorization",auth)).andExpect(status().isOk()).andReturn();
        session=(MockHttpSession)result.getRequest().getSession(false);
        var csrf=json.readTree(result.getResponse().getContentAsString());
        token=csrf.get("token").asText();header=csrf.get("headerName").asText();
    }
    JsonNode request(String method,String path,Object body,int status) throws Exception {
        var builder=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(path)).header("Authorization",auth).header("X-Requested-With","XMLHttpRequest").session(session);
        if(!method.equals("GET"))builder.header(header,token);
        if(body!=null)builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        ArticleVersionTestSupport.current(builder,jdbc,method,path);
        var text=mvc.perform(builder).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return text.isBlank()?null:json.readTree(text);
    }
    JsonNode publicGet(String path,int status) throws Exception {
        var text=mvc.perform(get(path)).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return text.isBlank()?null:json.readTree(text);
    }
    Map<String,Object> article(long category,long tag) {
        return new LinkedHashMap<>(Map.of("title",prefix,"summary","literal %_!","contentMarkdown","# 私人日记\n正文不能丢失", "type","DIARY","categoryId",category,"tagIds",List.of(tag),"pinned",true));
    }
    long create(Map<String,Object> payload) throws Exception {return request("POST","/api/admin/articles",payload,201).get("id").asLong();}
    long category() throws Exception {return request("POST","/api/admin/categories",Map.of("name",prefix.substring(0,45)),201).get("id").asLong();}
    long tag() throws Exception {return request("POST","/api/admin/tags",Map.of("name",prefix.substring(0,45)),201).get("id").asLong();}

    @Test void completeDiaryLifecycleAndIdempotentRestore() throws Exception {
        long category=category(),tag=tag();var payload=article(category,tag);long id=create(payload);String path="/api/admin/articles/"+id;
        publicGet("/api/articles/"+id,404);
        request("POST",path+"/publish",null,200);
        var first=publicGet("/api/articles/"+id,200);String published=first.get("publishedAt").asText();
        request("POST",path+"/unpublish",null,200);request("POST",path+"/unpublish",null,200);
        publicGet("/api/articles/"+id,404);
        assertEquals(0,publicGet("/api/articles?keyword="+prefix,200).get("total").asInt());
        request("POST",path+"/publish",null,200);assertEquals(published,publicGet("/api/articles/"+id,200).get("publishedAt").asText());
        request("DELETE",path,null,200);String deleted=request("GET",path,null,200).get("deletedAt").asText();
        request("DELETE",path,null,200);assertEquals(deleted,request("GET",path,null,200).get("deletedAt").asText());
        publicGet("/api/articles/"+id,404);publicGet("/api/articles/"+id+"/neighbors",404);
        request("PUT",path,payload,409);request("POST",path+"/publish",null,409);request("POST",path+"/unpublish",null,409);
        assertEquals(0,request("GET","/api/admin/articles?keyword="+prefix,null,200).get("total").asInt());
        assertEquals(1,request("GET","/api/admin/articles?recycled=true&keyword="+prefix,null,200).get("total").asInt());
        request("POST",path+"/restore",null,200);
        var restored=request("GET",path,null,200);assertEquals("DRAFT",restored.get("status").asText());assertTrue(restored.get("deletedAt").isNull());
        assertEquals(payload.get("contentMarkdown"),restored.get("contentMarkdown").asText());assertEquals(tag,restored.get("tagIds").get(0).asLong());assertEquals(category,restored.get("categoryId").asLong());
        publicGet("/api/articles/"+id,404);request("POST",path+"/publish",null,200);
        request("POST",path+"/restore",null,200);assertEquals("PUBLISHED",request("GET",path,null,200).get("status").asText());
    }

    @Test void recycledPublishedRowsDisappearFromEveryPublicProjection() throws Exception {
        var baseline=publicGet("/api/site-stats",200);long c=category(),t=tag();long id=create(article(c,t));String path="/api/admin/articles/"+id;
        request("POST",path+"/publish",null,200);
        jdbc.update("UPDATE article SET published_at='2044-07-03 12:00:00' WHERE id=?",id);
        request("DELETE",path,null,200);
        // 回收时保留PUBLISHED，确保隐藏不是仅依赖status改变的偶然结果。
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM article WHERE id=?",String.class,id));
        assertEquals(0,publicGet("/api/articles?categoryId="+c+"&tagId="+t+"&keyword="+prefix,200).get("total").asInt());
        assertEquals(0,publicGet("/api/articles?type=DIARY&month=2044-07",200).get("total").asInt());
        for(var month:publicGet("/api/articles/archive?type=DIARY",200))assertNotEquals("2044-07",month.get("month").asText());
        var topics=publicGet("/api/topics",200);for(var item:topics.get("categories"))assertNotEquals(String.valueOf(c),item.get("id").asText());for(var item:topics.get("tags"))assertNotEquals(String.valueOf(t),item.get("id").asText());
        assertEquals(baseline,publicGet("/api/site-stats",200));
        publicGet("/api/articles/"+id,404);publicGet("/api/articles/"+id+"/neighbors",404);
        long visible=create(article(c,t));request("POST","/api/admin/articles/"+visible+"/publish",null,200);
        var neighbors=publicGet("/api/articles/"+visible+"/neighbors",200);
        for(String side:List.of("previous","next"))if(!neighbors.get(side).isNull())assertNotEquals(id,neighbors.get(side).get("id").asLong());
    }

    @Test void combinedFiltersPaginationLiteralKeywordAndValidation() throws Exception {
        long c=category(),t=tag();var body=article(c,t);
        for(int i=0;i<12;i++){body.put("title",prefix+"-"+i);create(body);}
        String query="/api/admin/articles?keyword="+prefix+"&type=DIARY&status=DRAFT&categoryId="+c;
        var one=request("GET",query,null,200);var two=request("GET",query+"&page=999",null,200);
        assertEquals(12,one.get("total").asInt());assertEquals(10,one.get("items").size());assertEquals(2,two.get("items").size());assertEquals(2,two.get("page").asInt());
        assertFalse(one.toString().contains("contentMarkdown"));
        assertEquals(12,request("GET","/api/admin/articles?keyword=%25_!&categoryId="+c,null,200).get("total").asInt());
        assertEquals(0,request("GET",query+"&recycled=true",null,200).get("total").asInt());
        for(String bad:List.of("page=0","size=51","status=PRIVATE","type=SECRET","categoryId=0","recycled=invalid"))request("GET","/api/admin/articles?"+bad,null,400);
    }

    @Test void writesNeedCsrfAndLogoutInvalidatesSession() throws Exception {
        long id=create(article(category(),tag()));String path="/api/admin/articles/"+id;
        for(String action:List.of("/unpublish","/restore"))mvc.perform(post(path+action).header("Authorization",auth).session(session)).andExpect(status().isForbidden());
        mvc.perform(delete(path).header("Authorization",auth).session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/logout").header("Authorization",auth).session(session)).andExpect(status().isForbidden());
        request("POST","/api/admin/logout",null,204);assertTrue(session.isInvalid());
        mvc.perform(get("/api/admin/articles").header("X-Requested-With","XMLHttpRequest")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
}
