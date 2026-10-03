package com.skillexchange.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.skillexchange.app.core.common.Constants

/**
 * Token storage sử dụng EncryptedSharedPreferences (AES256).
 * Dùng security-crypto 1.0.0 API (MasterKeys).
 * Hỗ trợ fallback in-memory cho JVM unit tests khi context == null.
 */
open class TokenManager(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.let { ctx ->
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "skillexchange_secure_prefs",
            masterKeyAlias,
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val inMemoryPrefs = mutableMapOf<String, Any?>()

    open fun saveAccessToken(token: String) {
        prefs?.edit()?.putString(Constants.PREF_ACCESS_TOKEN, token)?.apply()
        inMemoryPrefs[Constants.PREF_ACCESS_TOKEN] = token
    }

    open fun getAccessToken(): String? =
        prefs?.getString(Constants.PREF_ACCESS_TOKEN, null) ?: (inMemoryPrefs[Constants.PREF_ACCESS_TOKEN] as? String)

    open fun saveRefreshToken(token: String) {
        prefs?.edit()?.putString(Constants.PREF_REFRESH_TOKEN, token)?.apply()
        inMemoryPrefs[Constants.PREF_REFRESH_TOKEN] = token
    }

    open fun getRefreshToken(): String? =
        prefs?.getString(Constants.PREF_REFRESH_TOKEN, null) ?: (inMemoryPrefs[Constants.PREF_REFRESH_TOKEN] as? String)

    open fun saveUserId(userId: String) {
        prefs?.edit()?.putString("user_id", userId)?.apply()
        inMemoryPrefs["user_id"] = userId
    }

    open fun getUserId(): String? =
        prefs?.getString("user_id", null) ?: (inMemoryPrefs["user_id"] as? String)

    open fun saveOnboardingSeen(seen: Boolean) {
        prefs?.edit()?.putBoolean("onboarding_seen", seen)?.apply()
        inMemoryPrefs["onboarding_seen"] = seen
    }

    open fun isOnboardingSeen(): Boolean =
        prefs?.getBoolean("onboarding_seen", false) ?: (inMemoryPrefs["onboarding_seen"] as? Boolean ?: false)

    open fun saveProfileCompleted(completed: Boolean) {
        prefs?.edit()?.putBoolean("profile_completed", completed)?.apply()
        inMemoryPrefs["profile_completed"] = completed
    }

    open fun isProfileCompleted(): Boolean =
        prefs?.getBoolean("profile_completed", false) ?: (inMemoryPrefs["profile_completed"] as? Boolean ?: false)

    open fun saveSession(accessToken: String, refreshToken: String, userId: String) {
        prefs?.edit()
            ?.putString(Constants.PREF_ACCESS_TOKEN, accessToken)
            ?.putString(Constants.PREF_REFRESH_TOKEN, refreshToken)
            ?.putString("user_id", userId)
            ?.putLong("token_saved_at", System.currentTimeMillis())
            ?.apply()
        inMemoryPrefs[Constants.PREF_ACCESS_TOKEN] = accessToken
        inMemoryPrefs[Constants.PREF_REFRESH_TOKEN] = refreshToken
        inMemoryPrefs["user_id"] = userId
    }

    /**
     * Xóa phiên đăng nhập (tokens, user_id, profile_completed).
     * BẢO TỒN cờ onboarding_seen theo FR-AUTH-6 (đăng xuất không xem lại onboarding).
     */
    open fun clearSession() {
        prefs?.edit()
            ?.remove(Constants.PREF_ACCESS_TOKEN)
            ?.remove(Constants.PREF_REFRESH_TOKEN)
            ?.remove("user_id")
            ?.remove("token_saved_at")
            ?.remove("profile_completed")
            ?.apply()
        inMemoryPrefs.remove(Constants.PREF_ACCESS_TOKEN)
        inMemoryPrefs.remove(Constants.PREF_REFRESH_TOKEN)
        inMemoryPrefs.remove("user_id")
        inMemoryPrefs.remove("profile_completed")
    }

    open fun clearAll() {
        val onboardingSeen = isOnboardingSeen()
        prefs?.edit()?.clear()?.apply()
        inMemoryPrefs.clear()
        if (onboardingSeen) {
            saveOnboardingSeen(true)
        }
    }

    open fun isLoggedIn(): Boolean = getAccessToken() != null && getRefreshToken() != null
}
