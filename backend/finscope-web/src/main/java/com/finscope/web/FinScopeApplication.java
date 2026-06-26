package com.finscope.web;

import org.springframework.boot.SpringApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.finscope")
@EnableScheduling
@Slf4j
public class FinScopeApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinScopeApplication.class, args);
        log.info(">>>>>>FinScopeApplication is successful!<<<<<<");
    }
}
