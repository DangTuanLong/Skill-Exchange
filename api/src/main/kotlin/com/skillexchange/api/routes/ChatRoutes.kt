package com.skillexchange.api.routes

import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.exchange.ExchangeStatus
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.chat.AttachmentTypeDetector
import com.skillexchange.api.services.chat.IChatAttachmentStorageService
import com.skillexchange.api.services.exchange.ExchangeForbiddenException
import com.skillexchange.api.services.exchange.ExchangeService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class ChatAttachmentResponse(
    val url: String,
    val type: String, // "IMAGE" hoặc "FILE"
    val fileName: String,
    val fileSize: Long
)

class ChatUploadRateLimiter(
    private val maxPerMinute: Int = 20,
    private val maxPerDay: Int = 50
) {
    private val minuteMap = ConcurrentHashMap<String, MutableList<Long>>()
    private val dailyMap = ConcurrentHashMap<String, Pair<LocalDate, AtomicInteger>>()

    fun tryAcquire(userId: String): Boolean {
        val now = System.currentTimeMillis()
        val today = LocalDate.now(ZoneOffset.UTC)

        // 1. Kiểm tra giới hạn theo ngày (50 files/ngày)
        val dailyPair = dailyMap.compute(userId) { _, current ->
            if (current == null || current.first != today) {
                Pair(today, AtomicInteger(0))
            } else {
                current
            }
        }!!

        if (dailyPair.second.get() >= maxPerDay) {
            return false
        }

        // 2. Kiểm tra giới hạn theo phút (20 requests/phút)
        val timestamps = minuteMap.computeIfAbsent(userId) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { it < now - 60_000L }
            if (timestamps.size >= maxPerMinute) {
                return false
            }
            timestamps.add(now)
        }

        dailyPair.second.incrementAndGet()
        return true
    }

    fun reset() {
        minuteMap.clear()
        dailyMap.clear()
    }
}

fun Route.chatRoutes(
    chatAttachmentStorageService: IChatAttachmentStorageService,
    exchangeService: ExchangeService,
    rateLimiter: ChatUploadRateLimiter = ChatUploadRateLimiter()
) {
    authenticate("auth-jwt") {
        route("/api/chat") {
            post("/attachment") {
                val callerUserId = call.getUserId()

                // 1. Rate limiting
                if (!rateLimiter.tryAcquire(callerUserId)) {
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        ApiError(
                            status = HttpStatusCode.TooManyRequests.value,
                            code = ErrorCodes.RATE_LIMITED,
                            message = "Bạn đã vượt quá giới hạn tải tệp tin (tối đa 20 lượt/phút và 50 tệp/ngày)"
                        )
                    )
                }

                val multipart = try {
                    call.receiveMultipart()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Dữ liệu tải lên không hợp lệ"
                        )
                    )
                }

                var chatId: String? = null
                var originalFileName: String? = null
                var fileBytes: ByteArray? = null
                val maxAllowedBytes = 10 * 1024 * 1024 // Trần cứng 10MB
                var exceededLimit = false

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "chatId") {
                                chatId = part.value.trim()
                            }
                        }
                        is PartData.FileItem -> {
                            if (fileBytes == null && !exceededLimit) {
                                originalFileName = part.originalFileName
                                val outputStream = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var totalRead = 0
                                val channel = part.provider()
                                while (!channel.isClosedForRead) {
                                    val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                                    if (bytesRead <= 0) break
                                    totalRead += bytesRead
                                    if (totalRead > maxAllowedBytes) {
                                        exceededLimit = true
                                        break
                                    }
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                                if (!exceededLimit) {
                                    fileBytes = outputStream.toByteArray()
                                }
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                // 2. Kiểm tra chatId
                val targetChatId = chatId
                if (targetChatId.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Thiếu thông tin chatId"
                        )
                    )
                }

                // 3. Kiểm tra quyền thành viên và trạng thái ACCEPTED
                val exchange = try {
                    exchangeService.getExchangeRequest(callerUserId, targetChatId)
                } catch (e: ExchangeForbiddenException) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = ErrorCodes.NOT_PARTICIPANT,
                            message = "Bạn không phải thành viên tham gia phòng chat này"
                        )
                    )
                }

                if (exchange == null) {
                    return@post call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(
                            status = HttpStatusCode.NotFound.value,
                            code = ErrorCodes.NOT_FOUND,
                            message = "Không tìm thấy phòng chat tương ứng"
                        )
                    )
                }

                if (exchange.status != ExchangeStatus.ACCEPTED.name) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError(
                            status = HttpStatusCode.Forbidden.value,
                            code = ErrorCodes.FORBIDDEN,
                            message = "Chỉ có thể tải tệp tin khi phòng chat đang ở trạng thái ACCEPTED"
                        )
                    )
                }

                // 4. Kiểm tra trần dung lượng
                if (exceededLimit) {
                    return@post call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ApiError(
                            status = HttpStatusCode.PayloadTooLarge.value,
                            code = ErrorCodes.PAYLOAD_TOO_LARGE,
                            message = "Kích thước tệp tin vượt quá giới hạn tối đa 10MB"
                        )
                    )
                }

                val rawBytes = fileBytes
                if (rawBytes == null || rawBytes.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(
                            status = HttpStatusCode.BadRequest.value,
                            code = ErrorCodes.BAD_REQUEST,
                            message = "Không tìm thấy tệp tin trong yêu cầu tải lên"
                        )
                    )
                }

                // 5. Kiểm tra magic bytes
                val detectedType = AttachmentTypeDetector.detect(rawBytes)
                if (detectedType == null) {
                    return@post call.respond(
                        HttpStatusCode.UnsupportedMediaType,
                        ApiError(
                            status = HttpStatusCode.UnsupportedMediaType.value,
                            code = ErrorCodes.UNSUPPORTED_MEDIA,
                            message = "Định dạng tệp không được hỗ trợ (chỉ chấp nhận JPEG, PNG, WebP hoặc PDF)"
                        )
                    )
                }

                // 6. Kiểm tra giới hạn riêng cho từng loại: Ảnh <= 5MB, PDF <= 10MB
                val maxForType = if (detectedType.type == "IMAGE") 5 * 1024 * 1024 else 10 * 1024 * 1024
                if (rawBytes.size > maxForType) {
                    val limitMsg = if (detectedType.type == "IMAGE") "Kích thước ảnh vượt quá giới hạn 5MB" else "Kích thước tệp tài liệu vượt quá giới hạn 10MB"
                    return@post call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ApiError(
                            status = HttpStatusCode.PayloadTooLarge.value,
                            code = ErrorCodes.PAYLOAD_TOO_LARGE,
                            message = limitMsg
                        )
                    )
                }

                // 7. Chuẩn hóa tên file (tối đa 100 ký tự)
                val sanitizedFileName = (originalFileName?.takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}.${detectedType.extension}")
                    .take(100)

                // 8. Upload lên R2
                val uploadResult = chatAttachmentStorageService.uploadAttachment(
                    chatId = targetChatId,
                    userId = callerUserId,
                    bytes = rawBytes,
                    extension = detectedType.extension,
                    mimeType = detectedType.mimeType
                )

                if (uploadResult.isFailure) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(
                            status = HttpStatusCode.InternalServerError.value,
                            code = ErrorCodes.INTERNAL,
                            message = "Lỗi khi lưu tệp đính kèm lên dịch vụ lưu trữ"
                        )
                    )
                }

                val publicUrl = uploadResult.getOrThrow()
                call.respond(
                    HttpStatusCode.OK,
                    ApiSuccess(
                        data = ChatAttachmentResponse(
                            url = publicUrl,
                            type = detectedType.type,
                            fileName = sanitizedFileName,
                            fileSize = rawBytes.size.toLong()
                        )
                    )
                )
            }
        }
    }
}
