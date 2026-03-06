package com.Wealthify.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class WealthifyApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(WealthifyApplication.class)
                .profiles("local")
                .run(args);
    }
}