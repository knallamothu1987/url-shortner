package com.shortener.orchestration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    /** Virtual-thread executor used for pipeline advancement and parallel stage execution. */
    @Bean(destroyMethod = "shutdown")
    ExecutorService orchestrationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
