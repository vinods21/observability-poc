package com.example.tracing.javaservice.trace;

public record KotlinTraceResponse(
        String service,
        String status,
        String redisKey,
        String objectKey,
        String traceId
) {
}
