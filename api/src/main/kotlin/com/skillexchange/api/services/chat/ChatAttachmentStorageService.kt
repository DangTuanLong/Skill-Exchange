package com.skillexchange.api.services.chat

import com.skillexchange.api.config.EnvLoader
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.util.UUID

data class DetectedAttachmentType(
    val type: String, // "IMAGE" hoặc "FILE"
    val extension: String,
    val mimeType: String
)

object AttachmentTypeDetector {
    fun detect(bytes: ByteArray): DetectedAttachmentType? {
        if (bytes.size < 5) return null

        // 1. PDF: %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
        if (bytes[0] == 0x25.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[4] == 0x2D.toByte()
        ) {
            return DetectedAttachmentType(type = "FILE", extension = "pdf", mimeType = "application/pdf")
        }

        if (bytes.size < 12) return null

        // 2. JPEG: FF D8 FF
        if ((bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8 &&
            (bytes[2].toInt() and 0xFF) == 0xFF
        ) {
            return DetectedAttachmentType(type = "IMAGE", extension = "jpg", mimeType = "image/jpeg")
        }

        // 3. PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            (bytes[0].toInt() and 0xFF) == 0x89 &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return DetectedAttachmentType(type = "IMAGE", extension = "png", mimeType = "image/png")
        }

        // 4. WebP: RIFF at 0..3 and WEBP at 8..11
        if (bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return DetectedAttachmentType(type = "IMAGE", extension = "webp", mimeType = "image/webp")
        }

        return null
    }
}

interface IChatAttachmentStorageService {
    suspend fun uploadAttachment(
        chatId: String,
        userId: String,
        bytes: ByteArray,
        extension: String,
        mimeType: String
    ): Result<String>
}

class R2ChatAttachmentStorageService(
    private val accountId: String = EnvLoader.get("R2_ACCOUNT_ID"),
    private val accessKeyId: String = EnvLoader.get("R2_ACCESS_KEY_ID"),
    private val secretAccessKey: String = EnvLoader.get("R2_SECRET_ACCESS_KEY"),
    private val bucketName: String = EnvLoader.get("R2_BUCKET_NAME"),
    private val publicUrl: String = EnvLoader.get("R2_PUBLIC_URL").trimEnd('/')
) : IChatAttachmentStorageService {

    private val logger = LoggerFactory.getLogger(R2ChatAttachmentStorageService::class.java)

    private val s3Client: S3Client by lazy {
        if (accountId.isBlank() || accessKeyId.isBlank() || secretAccessKey.isBlank() || bucketName.isBlank()) {
            logger.error("Cloudflare R2 configuration missing for chat attachments! R2_ACCOUNT_ID blank: ${accountId.isBlank()}, R2_ACCESS_KEY_ID blank: ${accessKeyId.isBlank()}, R2_SECRET_ACCESS_KEY blank: ${secretAccessKey.isBlank()}, R2_BUCKET_NAME blank: ${bucketName.isBlank()}")
        }
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
                    .chunkedEncodingEnabled(false) // REQUIRED by Cloudflare R2 to prevent HTTP 403 SignatureDoesNotMatch
                    .build()
            )
            .build()
    }

    override suspend fun uploadAttachment(
        chatId: String,
        userId: String,
        bytes: ByteArray,
        extension: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "")
            val timestamp = System.currentTimeMillis()
            val objectKey = "chats/$chatId/${timestamp}_$randomSuffix.$extension"

            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(mimeType)
                .build()

            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes))

            val baseUrl = if (publicUrl.isNotBlank()) publicUrl else "https://$accountId.r2.cloudflarestorage.com/$bucketName"
            "$baseUrl/$objectKey"
        }.onFailure { e ->
            logger.error("Failed to upload chat attachment to Cloudflare R2 for chatId $chatId, user $userId: [${e.javaClass.simpleName}] ${e.message}", e)
        }
    }
}
