package com.example.tracing.kotlinservice.health

import com.example.tracing.kotlinservice.config.AppStorageProperties
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException

@Component
class S3HealthIndicator(
    private val s3Client: S3Client,
    private val storageProperties: AppStorageProperties
) : HealthIndicator {

    override fun health(): Health =
        try {
            s3Client.headBucket { it.bucket(storageProperties.bucket) }
            Health.up()
                .withDetail("bucket", storageProperties.bucket)
                .build()
        } catch (_: NoSuchBucketException) {
            Health.down()
                .withDetail("bucket", storageProperties.bucket)
                .withDetail("reason", "Bucket does not exist")
                .build()
        } catch (ex: Exception) {
            Health.down(ex)
                .withDetail("bucket", storageProperties.bucket)
                .build()
        }
}
