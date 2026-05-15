package com.example.tracing.javaservice.trace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KotlinTraceRequest(
        @NotBlank String requestId,
        @NotBlank @Size(max = 255) String message
) {
}
