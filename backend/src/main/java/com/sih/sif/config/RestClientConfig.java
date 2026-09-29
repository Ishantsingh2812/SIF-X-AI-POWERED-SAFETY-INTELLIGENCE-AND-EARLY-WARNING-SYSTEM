package com.sih.sif.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestClientConfig.java
 *
 * Configures the RestTemplate bean with configurable connection and read timeouts
 * to prevent threads from hanging when integrating with external microservices.
 */
@Configuration
public class RestClientConfig {

    @Value("${ai.service.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${ai.service.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
