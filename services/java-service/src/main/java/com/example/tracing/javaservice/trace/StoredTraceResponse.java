package com.example.tracing.javaservice.trace;

import java.time.Instant;

public record StoredTraceResponse(
        Long requestId,
        String message,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
