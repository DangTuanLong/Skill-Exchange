package com.skillexchange.app.data.repository

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.local.ProfileLocalDataSource
import com.skillexchange.app.data.remote.profile.ProfileRemoteDataSource
import com.skillexchange.app.data.remote.profile.UpdateProfileDto
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.repository.IProfileRepository

class ProfileRepositoryImpl(
    private val remoteDataSource: ProfileRemoteDataSource,
    private val localDataSource: ProfileLocalDataSource,
    private val tokenManager: TokenManager
) : IProfileRepository {

    override suspend fun getMyProfile(): Result<Profile> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        try {
            val resp = remoteDataSource.getMyProfile(token)
            val data = resp.data ?: error(resp.message ?: "Không tìm thấy profile")
            val profile = Profile(
                id = data.id ?: "",
                userId = data.userId,
                fullName = data.fullName,
                bio = data.bio,
                city = data.city,
                avatarUrl = data.avatarUrl,
                availability = data.availability.map {
                    com.skillexchange.app.domain.model.AvailabilityWindow(it.day, it.from, it.to)
                }
            )
            localDataSource.saveProfile(profile)
            profile
        } catch (e: Exception) {
            localDataSource.getProfile() ?: throw e
        }
    }

    override suspend fun getUserProfile(userId: String): Result<Profile> = runCatching {
        val resp = remoteDataSource.getUserProfile(userId)
        val data = resp.data ?: error(resp.message ?: "Không tìm thấy profile")
        Profile(
            id = data.id ?: "",
            userId = data.userId,
            fullName = data.fullName,
            bio = data.bio,
            city = data.city,
            avatarUrl = data.avatarUrl,
            availability = data.availability.map {
                com.skillexchange.app.domain.model.AvailabilityWindow(it.day, it.from, it.to)
            }
        )
    }

    override suspend fun updateProfile(
        fullName: String,
        bio: String?,
        city: String?,
        avatarUrl: String?,
        availability: List<com.skillexchange.app.domain.model.AvailabilityWindow>?
    ): Result<Profile> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        val availDto = availability?.map {
            com.skillexchange.app.data.remote.profile.AvailabilityWindowDto(it.day, it.from, it.to)
        }
        val resp = remoteDataSource.updateProfile(token, UpdateProfileDto(fullName, bio, city, avatarUrl, availDto))
        val data = resp.data ?: error(resp.message ?: "Cập nhật thất bại")
        val profile = Profile(
            id = data.id ?: "",
            userId = data.userId,
            fullName = data.fullName,
            bio = data.bio,
            city = data.city,
            avatarUrl = data.avatarUrl,
            availability = data.availability.map {
                com.skillexchange.app.domain.model.AvailabilityWindow(it.day, it.from, it.to)
            }
        )
        localDataSource.saveProfile(profile)
        profile
    }

    override suspend fun uploadAvatar(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<String> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        val resp = remoteDataSource.uploadAvatar(token, fileBytes, fileName, mimeType)
        val url = resp.data?.avatarUrl ?: error(resp.message ?: "Tải ảnh đại diện thất bại")
        runCatching {
            val current = localDataSource.getProfile()
            if (current != null) {
                localDataSource.saveProfile(current.copy(avatarUrl = url))
            }
        }
        url
    }

}
