package com.theroom.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TheRoomApplication {
    public static void main(String[] args) {
        SpringApplication.run(TheRoomApplication.class, args);
    }
}
