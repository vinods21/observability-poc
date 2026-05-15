package com.example.tracing.kotlinservice.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.storage")
data class AppStorageProperties(
    @field:NotBlank
    val bucket: String = "tracing-poc-artifacts",
    val autoCreateBucket: Boolean = true,
    val prefix: String = "kotlin-service"
)
