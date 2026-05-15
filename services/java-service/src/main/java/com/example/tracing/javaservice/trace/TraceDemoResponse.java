package com.example.tracing.javaservice.trace;

import java.time.Instant;

public record TraceDemoResponse(
        Long requestId,
        String message,
        String status,
        Instant createdAt,
        String traceId,
        KotlinTraceResponse downstream
) {
}
