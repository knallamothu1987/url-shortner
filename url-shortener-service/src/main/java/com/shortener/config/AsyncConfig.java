package com.shortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async click tracking off the redirect hot path (virtual-thread executor). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
