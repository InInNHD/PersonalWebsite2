package com.firefly.personalwebsite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 既有流程测试模拟读取当前版本后写入；冲突测试单独显式发送旧版本。 */
final class ArticleVersionTestSupport {
    static void current(MockHttpServletRequestBuilder request, JdbcTemplate jdbc, String method, String path) {
        if (method.equals("GET") || !path.matches("/api/admin/articles/[0-9]+(?:/(?:publish|unpublish|restore))?")) return;
        long id=Long.parseLong(path.split("/")[4]);
        var rows=jdbc.queryForList("SELECT write_version FROM article WHERE id=?",Long.class,id);
        request.header("If-Match", "\""+(rows.isEmpty()?0:rows.get(0))+"\"");
    }
}
