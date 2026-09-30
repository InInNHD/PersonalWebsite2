package com.firefly.personalwebsite;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/admin")

public class AdminController {

    @GetMapping("/check")
    public Map<String, Boolean> check() {
        // 能执行到这里，说明管理员认证已经通过。
        return Map.of("ok", true);
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        // 返回请求头名称和当前浏览器会话的 token。
        // 客户端获得后，在写入请求的请求头中带上它。
        return Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", csrfToken.getToken()
        );
    }
}
