package com.skillexchange.app.data.local

import com.skillexchange.app.data.local.dao.ProfileDao
import com.skillexchange.app.data.local.entity.ProfileEntity
import com.skillexchange.app.domain.model.Profile

class ProfileLocalDataSource(private val profileDao: ProfileDao) {
    suspend fun getProfile(): Profile? {
        return profileDao.getProfile()?.toDomain()
    }

    suspend fun saveProfile(profile: Profile) {
        profileDao.insertOrUpdateProfile(ProfileEntity.fromDomain(profile))
    }

    suspend fun clearProfile() {
        profileDao.clearProfile()
    }
}
