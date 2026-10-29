package com.shortener.orchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrchestrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestrationApplication.class, args);
    }
}
