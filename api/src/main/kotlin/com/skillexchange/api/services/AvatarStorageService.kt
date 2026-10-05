package com.skillexchange.api.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.util.UUID

interface IAvatarStorageService {
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, extension: String, mimeType: String): Result<String>
    suspend fun deleteAvatar(objectKey: String): Result<Unit>
}

class R2AvatarStorageService(
    private val accountId: String = System.getenv("R2_ACCOUNT_ID") ?: "",
    private val accessKeyId: String = System.getenv("R2_ACCESS_KEY_ID") ?: "",
    private val secretAccessKey: String = System.getenv("R2_SECRET_ACCESS_KEY") ?: "",
    private val bucketName: String = System.getenv("R2_BUCKET_NAME") ?: "",
    private val publicUrl: String = (System.getenv("R2_PUBLIC_URL") ?: "").trimEnd('/')
) : IAvatarStorageService {

    private val logger = LoggerFactory.getLogger(R2AvatarStorageService::class.java)

    private val s3Client: S3Client by lazy {
        val endpoint = URI.create("https://$accountId.r2.cloudflarestorage.com")
        S3Client.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                )
            )
            .region(Region.of("auto"))
            .httpClient(UrlConnectionHttpClient.create())
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .build()
    }

    override suspend fun uploadAvatar(
        userId: String,
        bytes: ByteArray,
        extension: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(8)
            val timestamp = System.currentTimeMillis()
            val objectKey = "avatars/${userId}_${timestamp}_$randomSuffix.$extension"

            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(mimeType)
                .build()

            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes))
            "$publicUrl/$objectKey"
        }.onFailure { e ->
            logger.error("Failed to upload avatar to Cloudflare R2 for user $userId: ${e.message}", e)
        }
    }

    override suspend fun deleteAvatar(objectKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!objectKey.startsWith("avatars/")) {
                logger.warn("Refusing to delete object key not in avatars/ prefix: $objectKey")
                return@runCatching
            }
            val deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build()

            s3Client.deleteObject(deleteRequest)
            Unit
        }.onFailure { e ->
            logger.warn("Failed to delete avatar object $objectKey from Cloudflare R2: ${e.message}")
        }
    }
}
