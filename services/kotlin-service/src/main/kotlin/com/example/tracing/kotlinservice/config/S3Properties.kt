package com.example.tracing.kotlinservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.s3")
data class S3Properties(
    val endpoint: String = "http://localhost:9000",
    val region: String = "us-east-1",
    val accessKey: String = "minioadmin",
    val secretKey: String = "minioadmin",
    val pathStyleAccessEnabled: Boolean = true
)
