package com.firefly.personalwebsite;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                // 后台接口只允许管理员访问；现有文章阅读接口继续公开。
                .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().permitAll())
        .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable());

        // 保留 Spring Security 默认的 CSRF 保护；
        // 下一步写 POST/PUT 接口时会一并处理 CSRF token。
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(
            @Value("${app.admin.password}") String rawPassword,
            PasswordEncoder encoder
    ) {
        if (rawPassword.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD 不能为空");
        }

        // 首版只有一位管理员；密码在程序启动时编码后保存于内存。
        var admin = User.builder()
                .username("admin")
                .password(encoder.encode(rawPassword))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
        }
    }
