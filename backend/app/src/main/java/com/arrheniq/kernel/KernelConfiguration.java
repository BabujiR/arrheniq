package com.arrheniq.kernel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Kernel-wide beans. All server time is UTC (ADR 0012); tests may replace the clock. */
@Configuration(proxyBeanMethods = false)
public class KernelConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
