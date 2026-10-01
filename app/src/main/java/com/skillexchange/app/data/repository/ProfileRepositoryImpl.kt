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
                avatarUrl = data.avatarUrl
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
            avatarUrl = data.avatarUrl
        )
    }

    override suspend fun updateProfile(
        fullName: String, bio: String?, city: String?, avatarUrl: String?
    ): Result<Profile> = runCatching {
        val token = tokenManager.getAccessToken() ?: error("Chưa đăng nhập")
        val resp = remoteDataSource.updateProfile(token, UpdateProfileDto(fullName, bio, city, avatarUrl))
        val data = resp.data ?: error(resp.message ?: "Cập nhật thất bại")
        val profile = Profile(
            id = data.id ?: "",
            userId = data.userId,
            fullName = data.fullName,
            bio = data.bio,
            city = data.city,
            avatarUrl = data.avatarUrl
        )
        localDataSource.saveProfile(profile)
        profile
    }
}
