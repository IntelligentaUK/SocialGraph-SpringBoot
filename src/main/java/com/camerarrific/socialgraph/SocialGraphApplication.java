package com.camerarrific.socialgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main Spring Boot Application for Social Graph API.
 * 
 * This application provides a RESTful API for social networking features including:
 * - User authentication and registration
 * - Social connections (follow/unfollow)
 * - Timeline generation
 * - Social actions (like, love, fav, share)
 * - Media storage integration
 */
@SpringBootApplication
@EnableAsync
public class SocialGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialGraphApplication.class, args);
    }
}

