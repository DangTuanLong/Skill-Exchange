package com.skillexchange.app.presentation.auth

import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.Profile
import com.skillexchange.app.domain.model.SkillCategory
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserSkill
import com.skillexchange.app.domain.model.auth.AuthSession
import com.skillexchange.app.domain.model.auth.RegisterResult
import com.skillexchange.app.domain.repository.IAuthRepository
import com.skillexchange.app.domain.repository.IProfileRepository
import com.skillexchange.app.domain.repository.ISkillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tokenManager = TokenManager()

    private val fakeAuthRepository = object : IAuthRepository {
        override suspend fun register(email: String, password: String, fullName: String): Result<RegisterResult> =
            Result.success(RegisterResult(email, "OK"))

        override suspend fun login(email: String, password: String): Result<AuthSession> =
            Result.success(AuthSession("acc_token", "ref_token", "user_123", email))

        override suspend fun verifyOtp(email: String, token: String, type: String): Result<AuthSession> =
            Result.success(AuthSession("acc_token", "ref_token", "user_123", email))

        override suspend fun refreshToken(refreshToken: String): Result<AuthSession> =
            Result.success(AuthSession("new_acc_token", "new_ref_token", "user_123", "test@example.com"))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tokenManager.clearAll()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login with complete profile saves profileCompleted true and navigates to Home`() = runTest {
        val completeProfileRepo = object : IProfileRepository {
            override suspend fun getMyProfile(): Result<Profile> =
                Result.success(Profile("p1", "u1", fullName = "Nguyễn Văn A"))
            override suspend fun getUserProfile(userId: String): Result<Profile> = Result.success(Profile("p1", userId, fullName = "Nguyễn Văn A"))
            override suspend fun updateProfile(fullName: String, bio: String?, city: String?, avatarUrl: String?): Result<Profile> =
                Result.success(Profile("p1", "u1", fullName = fullName))
        }

        val completeSkillRepo = object : ISkillRepository {
            override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(emptyList())
            override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> =
                Result.success(listOf(UserSkill("us1", 1, "Kotlin", "Lập trình", SkillType.HAVE, 4)))
            override suspend fun addUserSkill(skillId: Int, type: String, proficiencyLevel: Int, note: String?): Result<UserSkill> = TODO()
            override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = TODO()
        }

        val viewModel = AuthViewModel(
            authRepository = fakeAuthRepository,
            tokenManager = tokenManager,
            profileRepository = completeProfileRepo,
            skillRepository = completeSkillRepo
        )

        viewModel.onIntent(AuthIntent.EmailChanged("test@example.com"))
        viewModel.onIntent(AuthIntent.PasswordChanged("123456"))
        viewModel.onIntent(AuthIntent.SubmitLogin)

        advanceUntilIdle()

        assertTrue(tokenManager.isProfileCompleted())
        val effect = viewModel.effect.first()
        assertEquals(AuthEffect.NavigateToHome, effect)
    }

    @Test
    fun `login with incomplete profile saves profileCompleted false and navigates to ProfileSetup`() = runTest {
        val incompleteProfileRepo = object : IProfileRepository {
            override suspend fun getMyProfile(): Result<Profile> =
                Result.success(Profile("p1", "u1", fullName = ""))
            override suspend fun getUserProfile(userId: String): Result<Profile> = Result.success(Profile("p1", userId, fullName = ""))
            override suspend fun updateProfile(fullName: String, bio: String?, city: String?, avatarUrl: String?): Result<Profile> =
                Result.success(Profile("p1", "u1", fullName = fullName))
        }

        val emptySkillRepo = object : ISkillRepository {
            override suspend fun getCategories(): Result<List<SkillCategory>> = Result.success(emptyList())
            override suspend fun getUserSkills(userId: String): Result<List<UserSkill>> =
                Result.success(emptyList())
            override suspend fun addUserSkill(skillId: Int, type: String, proficiencyLevel: Int, note: String?): Result<UserSkill> = TODO()
            override suspend fun removeUserSkill(userSkillId: String): Result<Boolean> = TODO()
        }

        val viewModel = AuthViewModel(
            authRepository = fakeAuthRepository,
            tokenManager = tokenManager,
            profileRepository = incompleteProfileRepo,
            skillRepository = emptySkillRepo
        )

        viewModel.onIntent(AuthIntent.EmailChanged("test@example.com"))
        viewModel.onIntent(AuthIntent.PasswordChanged("123456"))
        viewModel.onIntent(AuthIntent.SubmitLogin)

        advanceUntilIdle()

        assertEquals(false, tokenManager.isProfileCompleted())
        val effect = viewModel.effect.first()
        assertEquals(AuthEffect.NavigateToProfileSetup, effect)
    }

    @Test
    fun `SubmitOtp with non-6-digit OTP sets error`() = runTest {
        val viewModel = AuthViewModel(fakeAuthRepository, tokenManager)

        viewModel.onIntent(AuthIntent.OtpChanged("12345")) // 5 digits
        viewModel.onIntent(AuthIntent.SubmitOtp("test@example.com"))

        assertEquals("Mã OTP phải có đúng 6 chữ số", viewModel.state.value.error)
    }
}
