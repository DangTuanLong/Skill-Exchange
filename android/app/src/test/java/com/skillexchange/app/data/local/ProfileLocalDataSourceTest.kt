package com.skillexchange.app.data.local

import com.skillexchange.app.data.local.dao.ProfileDao
import com.skillexchange.app.data.local.entity.ProfileEntity
import com.skillexchange.app.domain.model.AvailabilityWindow
import com.skillexchange.app.domain.model.Profile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProfileLocalDataSourceTest {

    private class FakeProfileDao : ProfileDao {
        var entity: ProfileEntity? = null
        override suspend fun getProfile(): ProfileEntity? = entity
        override suspend fun getProfileByUserId(userId: String): ProfileEntity? =
            if (entity?.userId == userId) entity else null

        override suspend fun insertOrUpdateProfile(profile: ProfileEntity) {
            entity = profile
        }

        override suspend fun clearProfile() {
            entity = null
        }
    }

    @Test
    fun saveAndGetProfile_Success() = runTest {
        val fakeDao = FakeProfileDao()
        val dataSource = ProfileLocalDataSource(fakeDao)

        val profile = Profile(
            id = "prof-1",
            userId = "user-1",
            fullName = "Người dùng Local",
            bio = "Bio local",
            city = "Hà Nội",
            avatarUrl = "https://example.com/avatar.png",
            availability = listOf(AvailabilityWindow("MON", "19:00", "21:00"))
        )

        dataSource.saveProfile(profile)

        val retrieved = dataSource.getProfile()
        assertNotNull(retrieved)
        assertEquals("user-1", retrieved?.userId)
        assertEquals("Người dùng Local", retrieved?.fullName)
        assertEquals("Hà Nội", retrieved?.city)
        assertEquals(1, retrieved?.availability?.size)
        assertEquals("MON", retrieved?.availability?.first()?.day)
    }

    @Test
    fun clearProfile_RemovesStoredProfile() = runTest {
        val fakeDao = FakeProfileDao()
        val dataSource = ProfileLocalDataSource(fakeDao)

        val profile = Profile(
            id = "prof-1",
            userId = "user-1",
            fullName = "Người dùng Local",
            bio = null,
            city = null,
            avatarUrl = null,
            availability = emptyList()
        )
        dataSource.saveProfile(profile)
        assertNotNull(dataSource.getProfile())

        dataSource.clearProfile()
        assertNull(dataSource.getProfile())
    }
}
