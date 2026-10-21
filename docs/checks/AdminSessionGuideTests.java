package com.firefly.personalwebsite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 助手预检专用：不写数据库，仍限制到本轮独立库。
@SpringBootTest(properties = "app.admin.password=admin-session-test-only")
@AutoConfigureMockMvc
class AdminSessionGuideTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void logoutRequiresCsrfInvalidatesSessionAndAllowsFreshLogin() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("pw_v2admina_preflight_"));
        String auth = "Basic " + Base64.getEncoder().encodeToString("admin:admin-session-test-only".getBytes(StandardCharsets.UTF_8));
        var csrf = mvc.perform(get("/api/admin/csrf").header("Authorization", auth)
                .header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrf.getRequest().getSession(false);
        var token = json.readTree(csrf.getResponse().getContentAsString());
        mvc.perform(post("/api/admin/logout").session(session).header("Authorization", auth)
                .header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        mvc.perform(post("/api/admin/logout").session(session).header("Authorization", auth)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/admin/check").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/admin/check").header("Authorization", auth)
                .header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isOk());
    }
}
