package com.example.tracing.javaservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kotlin-service")
public record KotlinServiceProperties(
        @NotBlank String baseUrl,
        @NotBlank String tracePath
) {
}
