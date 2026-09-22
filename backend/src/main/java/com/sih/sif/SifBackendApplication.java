package com.sih.sif;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SifBackendApplication.java
 *
 * The main entry point for the Spring Boot application.
 *
 * The @SpringBootApplication annotation is a convenience annotation that wraps:
 * 1. @Configuration: Tags the class as a source of bean definitions.
 * 2. @EnableAutoConfiguration: Tells Spring Boot to start adding beans based on classpath settings.
 * 3. @ComponentScan: Tells Spring to look for other components, configurations, and services in the package.
 */
@SpringBootApplication
public class SifBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SifBackendApplication.class, args);
	}

}
