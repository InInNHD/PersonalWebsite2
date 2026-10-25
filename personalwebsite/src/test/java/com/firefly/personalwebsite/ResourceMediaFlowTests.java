package com.firefly.personalwebsite;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.admin.password=media-flow-test")
@AutoConfigureMockMvc
@Transactional
class ResourceMediaFlowTests {
    static final Path directory;
    static { try { directory=Files.createTempDirectory("pw-media-test-"); } catch(Exception ex) { throw new ExceptionInInitializerError(ex); } }
    @DynamicPropertySource static void media(DynamicPropertyRegistry registry) { registry.add("app.media.directory",()->directory.toString()); }
    @AfterAll static void cleanup() throws Exception {
        try(var paths=Files.list(directory)) { for(Path path:paths.toList()) Files.delete(path); }
        Files.delete(directory);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    MockHttpSession session;
    String token,header,prefix;
    final String auth="Basic "+Base64.getEncoder().encodeToString("admin:media-flow-test".getBytes(StandardCharsets.UTF_8));
    @BeforeEach void login() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("pw_v2admin_acceptance_"),"Only use dedicated acceptance schema");
        prefix="media-"+UUID.randomUUID();
        var result=mvc.perform(get("/api/admin/csrf").header("Authorization",auth)).andExpect(status().isOk()).andReturn();
        session=(MockHttpSession)result.getRequest().getSession(false);
        var body=json.readTree(result.getResponse().getContentAsString());token=body.get("token").asText();header=body.get("headerName").asText();
    }
    JsonNode write(String method,String path,Object body,int expected) throws Exception {
        var builder=request(org.springframework.http.HttpMethod.valueOf(method),path).header("Authorization",auth).header(header,token).session(session);
        if(body!=null)builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        ArticleVersionTestSupport.current(builder,jdbc,method,path);
        return json.readTree(mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    byte[] png(int w,int h) throws Exception {
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();
    }
    JsonNode upload(String name,String mime,byte[] bytes,int expected) throws Exception {
        var result=mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file",name,mime,bytes)).header("Authorization",auth).header(header,token).session(session)).andExpect(status().is(expected)).andReturn();
        return expected==201?json.readTree(result.getResponse().getContentAsString()):null;
    }
    Map<String,Object> article(String cover,String content) { return new HashMap<>(Map.of("title",prefix,"summary","","contentMarkdown",content,"type","DIARY","coverUrl",cover,"tagIds",List.of())); }
    @Test void groupsSortingRenameAndOnlyPublishedResources() throws Exception {
        long group=write("POST","/api/admin/resource-groups",Map.of("name",prefix,"sortOrder",7),201).get("id").asLong();
        write("POST","/api/admin/resource-groups",Map.of("name",prefix,"sortOrder",0),409);
        var resource=new HashMap<String,Object>(Map.of("title",prefix,"url","https://example.com/docs","description","说明","published",false,"sortOrder",3,"groupId",group,"iconKey","book","recommendation","适合入门"));
        long id=write("POST","/api/admin/resources",resource,201).get("id").asLong();
        String hidden=mvc.perform(get("/api/resources")).andReturn().getResponse().getContentAsString();assertFalse(hidden.contains(prefix));
        resource.put("published",true);write("PUT","/api/admin/resources/"+id,resource,200);
        write("PUT","/api/admin/resource-groups/"+group,Map.of("name",prefix+"-renamed","sortOrder",0),200);
        var publicRows=json.readTree(mvc.perform(get("/api/resources")).andReturn().getResponse().getContentAsString());
        boolean found=false;for(var row:publicRows)if(row.get("id").asLong()==id){found=true;assertEquals(prefix+"-renamed",row.get("groupName").asText());assertEquals("book",row.get("iconKey").asText());assertEquals("适合入门",row.get("recommendation").asText());}
        assertTrue(found);
        resource.put("groupId",Long.MAX_VALUE);write("PUT","/api/admin/resources/"+id,resource,400);
        resource.put("groupId",group);resource.put("iconKey","<script>");write("PUT","/api/admin/resources/"+id,resource,400);
        resource.put("iconKey","link");resource.put("url","https://user:pass@example.com");write("PUT","/api/admin/resources/"+id,resource,400);
    }
    @Test void actualContentSizeDimensionsAndReencoding() throws Exception {
        var bytes=png(20,12);var appended=Arrays.copyOf(bytes,bytes.length+30);System.arraycopy("<script>bad()</script>".getBytes(),0,appended,bytes.length,22);
        var asset=upload("../../user-cover.png","image/png",appended,201);String filename=asset.get("url").asText().substring("/api/media/".length());
        assertTrue(filename.matches("[0-9a-f-]{36}\\.png"));assertEquals(20,asset.get("width").asInt());
        assertFalse(new String(Files.readAllBytes(directory.resolve(filename)),StandardCharsets.ISO_8859_1).contains("<script>"));
        upload("evil.svg","image/svg+xml","<svg/>".getBytes(),400);
        upload("evil.png","image/png","<script>evil</script>".getBytes(),400);
        upload("wrong.jpg","image/jpeg",bytes,400);upload("wrong.png","image/jpeg",bytes,400);
        upload("empty.png","image/png",new byte[0],400);upload("large.png","image/png",new byte[5*1024*1024+1],400);
        upload("wide.png","image/png",png(6001,1),400);
    }
    @Test void draftPrivatePublishWithdrawRecycleRestoreAndReferenceReplacement() throws Exception {
        String url=upload("cover.png","image/png",png(16,16),201).get("url").asText();
        String filename=url.substring("/api/media/".length());
        var example=article("","```text\n"+url+"\n```\n![外部](https://example.com/image?src="+url+")");
        long exampleId=write("POST","/api/admin/articles",example,201).get("id").asLong();
        write("POST","/api/admin/articles/"+exampleId+"/publish",null,200);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_media WHERE article_id=?",Integer.class,exampleId));
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/media/"+filename+"/content").header("Authorization",auth)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        var payload=article(url+"#cover","# 日记\n![图片]("+url+"?v=1)");payload.put("bodyMediaUrls",List.of(url+"?v=1"));long id=write("POST","/api/admin/articles",payload,201).get("id").asLong();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM article_media WHERE article_id=?",Integer.class,id));
        write("POST","/api/admin/articles/"+id+"/publish",null,200);mvc.perform(get(url)).andExpect(status().isOk());
        write("POST","/api/admin/articles/"+id+"/unpublish",null,200);mvc.perform(get(url)).andExpect(status().isNotFound());
        write("POST","/api/admin/articles/"+id+"/publish",null,200);write("DELETE","/api/admin/articles/"+id,null,200);mvc.perform(get(url)).andExpect(status().isNotFound());
        write("POST","/api/admin/articles/"+id+"/restore",null,200);mvc.perform(get(url)).andExpect(status().isNotFound());
        payload.put("coverUrl","");payload.put("contentMarkdown","正文不再包含图片");payload.put("bodyMediaUrls",List.of());write("PUT","/api/admin/articles/"+id,payload,200);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_edit_media WHERE article_id=?",Integer.class,id));
        write("POST","/api/admin/articles/"+id+"/publish",null,200);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM article_media WHERE article_id=?",Integer.class,id));
    }
    @Test void pendingCoverAndBodyMediaStayPrivateUntilPublish() throws Exception {
        String oldUrl=upload("old.png","image/png",png(16,16),201).get("url").asText();
        String newUrl=upload("new.png","image/png",png(18,18),201).get("url").asText();
        var body=article(oldUrl,"旧正文");long id=write("POST","/api/admin/articles",body,201).get("id").asLong();String path="/api/admin/articles/"+id;
        write("POST",path+"/publish",null,200);
        body.put("coverUrl",newUrl);body.put("contentMarkdown","![新图]("+newUrl+")");body.put("bodyMediaUrls",List.of(newUrl));
        write("PUT",path,body,200);
        mvc.perform(get(oldUrl)).andExpect(status().isOk());mvc.perform(get(newUrl)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/media/"+newUrl.substring("/api/media/".length())+"/content").header("Authorization",auth)).andExpect(status().isOk());
        write("POST",path+"/publish",null,200);
        mvc.perform(get(oldUrl)).andExpect(status().isNotFound());mvc.perform(get(newUrl)).andExpect(status().isOk());
        write("POST",path+"/unpublish",null,200);mvc.perform(get(newUrl)).andExpect(status().isNotFound());
    }
    // 单独使用真实HTTP事务边界，确认400响应后新增行已经回滚；其他测试由外层事务回滚。
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void unknownMediaRollsBackArticleAndUnsafePathsRejected() throws Exception {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM article",Long.class);
        write("POST","/api/admin/articles",article("/api/media/00000000-0000-0000-0000-000000000000.png","正文"),400);
        var missing=article("","![x](/api/media/00000000-0000-0000-0000-000000000000.png)");
        missing.put("bodyMediaUrls",List.of("/api/media/00000000-0000-0000-0000-000000000000.png"));
        write("POST","/api/admin/articles",missing,400);
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM article",Long.class));
        mvc.perform(get("/api/media/no-file.svg")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/media")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file","x.png","image/png",png(2,2))).header("Authorization",auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/media?page=0").header("Authorization",auth)).andExpect(status().isBadRequest());
    }
}
