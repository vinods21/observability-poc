package com.example.tracing.kotlinservice.service

import com.example.tracing.kotlinservice.config.AppStorageProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.NoSuchBucketException

@Component
class BucketInitializer(
    private val s3Client: S3Client,
    private val storageProperties: AppStorageProperties
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!storageProperties.autoCreateBucket) {
            logger.info("Skipping S3 bucket initialization because app.storage.auto-create-bucket=false")
            return
        }

        try {
            s3Client.headBucket { it.bucket(storageProperties.bucket) }
            logger.info("Verified S3 bucket {}", storageProperties.bucket)
        } catch (_: NoSuchBucketException) {
            createBucket()
        } catch (ex: Exception) {
            logger.warn("Unable to verify S3 bucket {} on startup: {}", storageProperties.bucket, ex.message)
        }
    }

    private fun createBucket() {
        try {
            s3Client.createBucket { it.bucket(storageProperties.bucket) }
            logger.info("Created S3 bucket {}", storageProperties.bucket)
        } catch (_: BucketAlreadyOwnedByYouException) {
            logger.info("S3 bucket {} already exists", storageProperties.bucket)
        } catch (_: BucketAlreadyExistsException) {
            logger.info("S3 bucket {} already exists", storageProperties.bucket)
        }
    }
}
