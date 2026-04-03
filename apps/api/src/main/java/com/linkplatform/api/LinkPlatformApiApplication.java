package com.linkplatform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LinkPlatformApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkPlatformApiApplication.class, args);
    }
}
