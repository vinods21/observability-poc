package com.example.tracing.kotlinservice.service

import com.example.tracing.kotlinservice.config.AppStorageProperties
import com.example.tracing.kotlinservice.web.ProcessTraceRequest
import com.example.tracing.kotlinservice.web.ProcessTraceResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import java.time.Instant
import java.util.UUID

@Service
class TraceProcessingService(
    private val redisTemplate: StringRedisTemplate,
    private val s3Client: S3Client,
    private val storageProperties: AppStorageProperties,
    private val objectMapper: ObjectMapper,
    private val observationRegistry: ObservationRegistry,
    private val tracer: Tracer
) {

    fun process(request: ProcessTraceRequest): ProcessTraceResponse {
        val requestId = request.requestId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val redisKey = "trace:$requestId"
        val objectKey = "${storageProperties.prefix}/$requestId.json"
        val payloadJson = objectMapper.writeValueAsString(
            mapOf(
                "requestId" to requestId,
                "message" to request.message,
                "receivedAt" to Instant.now().toString()
            )
        )

        withSpan("redis.write", "redis", "SET") {
            redisTemplate.opsForValue().set(redisKey, request.message)
        }

        val cachedValue = withSpan("redis.read", "redis", "GET") {
            redisTemplate.opsForValue().get(redisKey).orEmpty()
        }

        val eTag = withSpan("s3.put-object", "aws.s3", "PutObject") {
            s3Client.putObject(
                { builder ->
                    builder
                        .bucket(storageProperties.bucket)
                        .key(objectKey)
                        .contentType("application/json")
                },
                RequestBody.fromString(payloadJson)
            ).eTag()
        }

        val currentSpan = tracer.currentSpan()
        return ProcessTraceResponse(
            service = "kotlin-service",
            status = "PROCESSED",
            requestId = requestId,
            redisKey = redisKey,
            cachedValue = cachedValue,
            bucket = storageProperties.bucket,
            objectKey = objectKey,
            objectETag = eTag,
            traceId = currentSpan?.context()?.traceId() ?: "",
            spanId = currentSpan?.context()?.spanId() ?: "",
            processedAt = Instant.now()
        )
    }

    private fun <T> withSpan(
        spanName: String,
        system: String,
        operation: String,
        block: () -> T
    ): T =
        Observation.createNotStarted(spanName, observationRegistry)
            .contextualName(spanName)
            .lowCardinalityKeyValue("component", "kotlin-service")
            .lowCardinalityKeyValue("system", system)
            .lowCardinalityKeyValue("operation", operation)
            .observe(block) ?: throw IllegalStateException("Observation '$spanName' returned null")
}
