package com.skillexchange.api.routes

import com.skillexchange.api.models.profile.AvatarUploadResponse
import com.skillexchange.api.models.profile.ImageTypeDetector
import com.skillexchange.api.models.profile.UpdateProfileRequest
import com.skillexchange.api.plugins.getUserId
import com.skillexchange.api.services.IAvatarStorageService
import com.skillexchange.api.services.InMemoryRateLimiter
import com.skillexchange.api.services.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream

fun Route.profileRoutes(
    profileService: ProfileService,
    avatarStorageService: IAvatarStorageService? = null,
    rateLimiter: InMemoryRateLimiter? = null
) {
    route("/api/profile") {

        // Yêu cầu JWT cho me, put profile, và upload avatar
        authenticate("auth-jwt") {

            // GET /api/profile/me - lấy profile của mình
            get("/me") {
                val userId = call.getUserId()
                val profile = profileService.getProfile(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, RouteApiError(message = "Profile chưa được tạo", code = 404))
                call.respond(HttpStatusCode.OK, ApiSuccess(data = profile))
            }

            // PUT /api/profile - tạo hoặc cập nhật profile
            put {
                val userId = call.getUserId()
                val req = call.receive<UpdateProfileRequest>()
                if (req.fullName.isBlank()) {
                    return@put call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        RouteApiError(message = "Họ tên không được để trống", code = 422)
                    )
                }
                if (req.bio != null && req.bio.length > 500) {
                    return@put call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        RouteApiError(message = "Tiểu sử không được vượt quá 500 ký tự", code = 422)
                    )
                }
                if (req.availability != null) {
                    val availabilityError = com.skillexchange.api.models.profile.AvailabilityValidator.validate(req.availability)
                    if (availabilityError != null) {
                        return@put call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            RouteApiError(message = availabilityError, code = 422)
                        )
                    }
                }
                val profile = profileService.upsertProfile(userId, req)
                call.respond(HttpStatusCode.OK, ApiSuccess(data = profile))
            }

            // POST /api/profile/avatar - upload avatar lên Cloudflare R2
            post("/avatar") {
                val userId = call.getUserId()

                // 1. Kiểm tra rate limit: 5 lần/phút/user
                if (rateLimiter != null && !rateLimiter.tryAcquire(userId)) {
                    return@post call.respond(
                        HttpStatusCode.TooManyRequests,
                        RouteApiError(
                            message = "Bạn đã thực hiện quá nhiều yêu cầu tải lên. Vui lòng thử lại sau 1 phút.",
                            code = 429
                        )
                    )
                }

                if (avatarStorageService == null) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        RouteApiError(message = "Dịch vụ lưu trữ chưa được cấu hình", code = 500)
                    )
                }

                val multipart = try {
                    call.receiveMultipart()
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Dữ liệu tải lên không hợp lệ", code = 400)
                    )
                }

                // 2. Đệm tối đa 2MB trong bộ nhớ sau khi kiểm tra từng chunk, trả 413 ngay khi vượt
                val maxBytes = 2 * 1024 * 1024
                var fileBytes: ByteArray? = null
                var exceededLimit = false

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem && fileBytes == null && !exceededLimit) {
                        val outputStream = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var totalRead = 0
                        val channel = part.provider()
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead <= 0) break
                            totalRead += bytesRead
                            if (totalRead > maxBytes) {
                                exceededLimit = true
                                break
                            }
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        if (!exceededLimit) {
                            fileBytes = outputStream.toByteArray()
                        }
                    }
                    part.dispose()
                }

                if (exceededLimit) {
                    return@post call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        RouteApiError(message = "Kích thước ảnh vượt quá giới hạn 2MB", code = 413)
                    )
                }

                val rawBytes = fileBytes
                if (rawBytes == null || rawBytes.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        RouteApiError(message = "Không tìm thấy file ảnh trong yêu cầu", code = 400)
                    )
                }

                // 3. Kiểm tra magic bytes xác thực định dạng ảnh (JPEG, PNG, WebP)
                val detectedType = ImageTypeDetector.detect(rawBytes)
                if (detectedType == null) {
                    return@post call.respond(
                        HttpStatusCode.UnsupportedMediaType,
                        RouteApiError(
                            message = "Định dạng ảnh không được hỗ trợ (chỉ chấp nhận JPEG, PNG, WebP)",
                            code = 415
                        )
                    )
                }

                // 4. Upload ảnh mới lên R2 với Content-Type xác thực, không tin client header
                val uploadResult = avatarStorageService.uploadAvatar(
                    userId = userId,
                    bytes = rawBytes,
                    extension = detectedType.extension,
                    mimeType = detectedType.mimeType
                )

                if (uploadResult.isFailure) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        RouteApiError(message = "Lỗi khi lưu ảnh lên dịch vụ lưu trữ", code = 500)
                    )
                }

                val newAvatarUrl = uploadResult.getOrThrow()

                // 5. Cập nhật URL mới vào DB
                val oldAvatarUrl = profileService.updateAvatarUrl(userId, newAvatarUrl)

                // 6. Xóa ảnh cũ trên R2 nếu có và thuộc về user này
                if (!oldAvatarUrl.isNullOrBlank()) {
                    val userPrefix = "avatars/${userId}_"
                    val keyIndex = oldAvatarUrl.indexOf(userPrefix)
                    if (keyIndex != -1) {
                        val oldKey = oldAvatarUrl.substring(keyIndex).takeWhile { it != '?' && it != '#' }
                        avatarStorageService.deleteAvatar(oldKey)
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiSuccess(data = AvatarUploadResponse(avatarUrl = newAvatarUrl))
                )
            }

        }

        // GET /api/profile/{userId} - public
        get("/{userId}") {
            val userId = call.parameters["userId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, RouteApiError(message = "Missing userId", code = 400))
            val profile = profileService.getProfile(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, RouteApiError(message = "Profile not found", code = 404))
            call.respond(HttpStatusCode.OK, ApiSuccess(data = profile))
        }
    }
}
