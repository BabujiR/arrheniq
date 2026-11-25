package com.arrheniq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ArrheniQ application: Spring Boot 4.1 modular monolith (ADR 0001).
 * <p>
 * Each top-level package below {@code com.arrheniq} is a Spring Modulith application module
 * (docs/architecture/bounded-contexts.md). Module boundaries are verified by {@code ModularityTests}.
 */
@SpringBootApplication
public class ArrheniqApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArrheniqApplication.class, args);
    }
}
