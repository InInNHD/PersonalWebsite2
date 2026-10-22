package com.firefly.personalwebsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PersonalwebsiteApplication {

    public static void main(String[] args) {
        var application = new SpringApplication(PersonalwebsiteApplication.class);
        application.setDefaultProperties(java.util.Map.of(
                "spring.servlet.multipart.max-file-size", "5MB",
                "spring.servlet.multipart.max-request-size", "6MB"));
        application.run(args);
    }

}
