package com.like.likesystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LikeSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LikeSystemApplication.class, args);
    }

}
