package com.sih.sif.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CorsConfig.java
 *
 * Cross-Origin Resource Sharing (CORS) Configuration.
 *
 * In web development, browsers block web applications from making HTTP requests
 * to a different domain/port (e.g. React frontend on port 5173 calling Spring Boot on port 8080)
 * unless the server explicitly sends HTTP headers allowing it.
 *
 * This configuration registers a global CORS policy allowing requests from any origin
 * for all standard HTTP methods (GET, POST, etc.).
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Apply this policy to all URL paths in the Spring Boot application
                registry.addMapping("/**")
                        // Allow requests coming from any client origin (including localhost:5173)
                        .allowedOrigins("*")
                        // Allow standard REST HTTP methods
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        // Allow all custom and standard request headers
                        .allowedHeaders("*");
            }
        };
    }
}
