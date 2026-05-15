package com.example.tracing.javaservice.trace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TraceDemoRequest(
        @NotBlank @Size(max = 255) String message
) {
}
