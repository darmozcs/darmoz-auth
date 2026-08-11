package com.darmoz.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DarmozAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(DarmozAuthApplication.class, args);
    }
}
