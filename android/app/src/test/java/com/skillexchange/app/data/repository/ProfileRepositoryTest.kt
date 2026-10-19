package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.local.ProfileLocalDataSource
import com.skillexchange.app.data.local.dao.ProfileDao
import com.skillexchange.app.data.local.entity.ProfileEntity
import com.skillexchange.app.data.remote.profile.ProfileRemoteDataSource
import com.skillexchange.app.domain.model.AvailabilityWindow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProfileRepositoryTest {

    private class FakeProfileDao : ProfileDao {
        var savedEntity: ProfileEntity? = null
        override suspend fun getProfile(): ProfileEntity? = savedEntity
        override suspend fun getProfileByUserId(userId: String): ProfileEntity? =
            if (savedEntity?.userId == userId) savedEntity else null

        override suspend fun insertOrUpdateProfile(profile: ProfileEntity) {
            savedEntity = profile
        }

        override suspend fun clearProfile() {
            savedEntity = null
        }
    }

    private val sampleProfileJson = """
    {
        "success": true,
        "data": {
            "id": "prof-1",
            "user_id": "user-123",
            "full_name": "Nguyễn Văn Test",
            "bio": "Lập trình viên đam mê học hỏi",
            "city": "Hà Nội",
            "avatar_url": "https://example.com/avatar.jpg",
            "availability": [
                {"day": "MON", "from": "19:00", "to": "21:00"},
                {"day": "WED", "from": "19:00", "to": "21:00"}
            ]
        }
    }
    """.trimIndent()

    private fun createMockClient(statusCode: HttpStatusCode, responseJson: String): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    respond(
                        content = responseJson,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
    }

    @Test
    fun getMyProfile_Success_SavesToLocalAndReturnsProfile() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleProfileJson)
        val fakeDao = FakeProfileDao()
        val localDataSource = ProfileLocalDataSource(fakeDao)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = ProfileRepositoryImpl(ProfileRemoteDataSource(client), localDataSource, tokenManager)

        val result = repo.getMyProfile()

        assertTrue(result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertEquals("user-123", profile?.userId)
        assertEquals("Nguyễn Văn Test", profile?.fullName)
        assertEquals("Hà Nội", profile?.city)
        assertEquals(2, profile?.availability?.size)
        // Verify saved to local cache
        assertNotNull(fakeDao.savedEntity)
        assertEquals("user-123", fakeDao.savedEntity?.userId)
    }

    @Test
    fun getMyProfile_RemoteError_FallbackToLocalCache() = runTest {
        val client = createMockClient(HttpStatusCode.InternalServerError, """{"success":false,"message":"Server error"}""")
        val fakeDao = FakeProfileDao().apply {
            savedEntity = ProfileEntity(
                id = "prof-cached",
                userId = "user-cached",
                fullName = "Cached User",
                bio = "Cached Bio",
                city = "Đà Nẵng",
                avatarUrl = null,
                availabilityJson = "[]",
                updatedAt = System.currentTimeMillis()
            )
        }
        val localDataSource = ProfileLocalDataSource(fakeDao)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = ProfileRepositoryImpl(ProfileRemoteDataSource(client), localDataSource, tokenManager)

        val result = repo.getMyProfile()

        assertTrue(result.isSuccess)
        assertEquals("user-cached", result.getOrNull()?.userId)
        assertEquals("Cached User", result.getOrNull()?.fullName)
    }

    @Test
    fun getUserProfile_Success_ReturnsProfile() = runTest {
        val client = createMockClient(HttpStatusCode.OK, sampleProfileJson)
        val fakeDao = FakeProfileDao()
        val localDataSource = ProfileLocalDataSource(fakeDao)
        val tokenManager = TokenManager(null)
        val repo = ProfileRepositoryImpl(ProfileRemoteDataSource(client), localDataSource, tokenManager)

        val result = repo.getUserProfile("user-123")

        assertTrue(result.isSuccess)
        assertEquals("user-123", result.getOrNull()?.userId)
    }

    @Test
    fun updateProfile_Success_UpdatesLocalAndReturnsProfile() = runTest {
        val updatedJson = sampleProfileJson.replace("Hà Nội", "TP.HCM")
        val client = createMockClient(HttpStatusCode.OK, updatedJson)
        val fakeDao = FakeProfileDao()
        val localDataSource = ProfileLocalDataSource(fakeDao)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = ProfileRepositoryImpl(ProfileRemoteDataSource(client), localDataSource, tokenManager)

        val result = repo.updateProfile(
            fullName = "Nguyễn Văn Test",
            bio = "Lập trình viên đam mê học hỏi",
            city = "TP.HCM",
            avatarUrl = null,
            availability = listOf(AvailabilityWindow("MON", "19:00", "21:00"))
        )

        assertTrue(result.isSuccess)
        assertEquals("TP.HCM", result.getOrNull()?.city)
        assertEquals("TP.HCM", fakeDao.savedEntity?.city)
    }

    @Test
    fun uploadAvatar_Success_ReturnsAvatarUrl() = runTest {
        val uploadJson = """{"success":true,"data":{"avatar_url":"https://r2.example.com/avatars/user-123_abc.jpg"}}"""
        val client = createMockClient(HttpStatusCode.OK, uploadJson)
        val fakeDao = FakeProfileDao().apply {
            savedEntity = ProfileEntity(
                id = "prof-1",
                userId = "user-123",
                fullName = "Test User",
                bio = null,
                city = null,
                avatarUrl = null,
                availabilityJson = "[]",
                updatedAt = System.currentTimeMillis()
            )
        }
        val localDataSource = ProfileLocalDataSource(fakeDao)
        val tokenManager = TokenManager(null).apply { saveAccessToken("test_token") }
        val repo = ProfileRepositoryImpl(ProfileRemoteDataSource(client), localDataSource, tokenManager)

        val result = repo.uploadAvatar(byteArrayOf(1, 2, 3), "avatar.jpg", "image/jpeg")

        assertTrue(result.isSuccess)
        assertEquals("https://r2.example.com/avatars/user-123_abc.jpg", result.getOrNull())
        assertEquals("https://r2.example.com/avatars/user-123_abc.jpg", fakeDao.savedEntity?.avatarUrl)
    }
}
