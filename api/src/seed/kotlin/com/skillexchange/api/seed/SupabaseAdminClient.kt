package com.skillexchange.api.seed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String? = null
)

@Serializable
data class AdminUserListResponse(
    val users: List<AdminUserDto> = emptyList()
)

class SupabaseAdminClient(
    private val supabaseUrl: String,
    private val serviceRoleKey: String
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val adminEndpoint = "${supabaseUrl.trimEnd('/')}/auth/v1/admin/users"

    /**
     * Lấy danh sách tất cả người dùng từ Supabase GoTrue qua Admin API.
     */
    fun listAllUsers(): List<AdminUserDto> {
        val allUsers = mutableListOf<AdminUserDto>()
        var page = 1
        val perPage = 1000

        while (true) {
            val uri = URI.create("$adminEndpoint?page=$page&per_page=$perPage")
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer $serviceRoleKey")
                .header("apikey", serviceRoleKey)
                .header("Accept", "application/json")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("Lỗi Supabase Admin API khi list users (HTTP ${response.statusCode()}): ${response.body()}")
            }

            val listResponse = json.decodeFromString<AdminUserListResponse>(response.body())
            if (listResponse.users.isEmpty()) break
            allUsers.addAll(listResponse.users)
            if (listResponse.users.size < perPage) break
            page++
        }

        return allUsers
    }

    /**
     * Tạo một người dùng mới qua Admin API (bỏ qua xác thực email, kích hoạt ngay lập tức).
     * Nếu email đã tồn tại, trả về null hoặc ném ngoại lệ tùy theo tham số.
     */
    fun createUser(email: String, password: String, fullName: String): AdminUserDto {
        val requestBody = buildJsonObject {
            put("email", email)
            put("password", password)
            put("email_confirm", true)
            put("user_metadata", buildJsonObject {
                put("full_name", fullName)
            })
        }.toString()

        val request = HttpRequest.newBuilder(URI.create(adminEndpoint))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $serviceRoleKey")
            .header("apikey", serviceRoleKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() in 200..201) {
            return json.decodeFromString<AdminUserDto>(response.body())
        }

        if (response.statusCode() == 422) {
            // User already registered
            val existing = listAllUsers().firstOrNull { it.email.equals(email, ignoreCase = true) }
            if (existing != null) return existing
        }

        throw RuntimeException("Lỗi tạo user $email qua Admin API (HTTP ${response.statusCode()}): ${response.body()}")
    }

    /**
     * Xóa một người dùng theo userId qua Admin API.
     */
    fun deleteUser(userId: String): Boolean {
        val request = HttpRequest.newBuilder(URI.create("$adminEndpoint/$userId"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $serviceRoleKey")
            .header("apikey", serviceRoleKey)
            .header("Accept", "application/json")
            .DELETE()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return response.statusCode() in 200..204
    }
}
