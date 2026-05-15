package com.example.tracing.kotlinservice.web

import com.example.tracing.kotlinservice.service.TraceProcessingService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/traces")
class TraceController(
    private val traceProcessingService: TraceProcessingService
) {

    @GetMapping("/ping")
    fun ping(): Map<String, Any> = mapOf(
        "service" to "kotlin-service",
        "status" to "ok",
        "timestamp" to Instant.now().toString()
    )

    @PostMapping("/process")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun processTrace(@Valid @RequestBody request: ProcessTraceRequest): ProcessTraceResponse =
        traceProcessingService.process(request)
}

data class ProcessTraceRequest(
    val requestId: String? = null,
    @field:NotBlank
    val message: String
)

data class ProcessTraceResponse(
    val service: String,
    val status: String,
    val requestId: String,
    val redisKey: String,
    val cachedValue: String,
    val bucket: String,
    val objectKey: String,
    val objectETag: String?,
    val traceId: String,
    val spanId: String,
    val processedAt: Instant
)
