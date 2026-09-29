package com.skillexchange.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.skillexchange.app.core.common.Constants

/**
 * Token storage sử dụng EncryptedSharedPreferences (AES256).
 * Dùng security-crypto 1.0.0 API (MasterKeys).
 */
class TokenManager(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        "skillexchange_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccessToken(token: String) =
        prefs.edit().putString(Constants.PREF_ACCESS_TOKEN, token).apply()

    fun getAccessToken(): String? =
        prefs.getString(Constants.PREF_ACCESS_TOKEN, null)

    fun saveRefreshToken(token: String) =
        prefs.edit().putString(Constants.PREF_REFRESH_TOKEN, token).apply()

    fun getRefreshToken(): String? =
        prefs.getString(Constants.PREF_REFRESH_TOKEN, null)

    fun saveUserId(userId: String) =
        prefs.edit().putString("user_id", userId).apply()

    fun getUserId(): String? =
        prefs.getString("user_id", null)

    fun saveSession(accessToken: String, refreshToken: String, userId: String) {
        prefs.edit()
            .putString(Constants.PREF_ACCESS_TOKEN, accessToken)
            .putString(Constants.PREF_REFRESH_TOKEN, refreshToken)
            .putString("user_id", userId)
            .putLong("token_saved_at", System.currentTimeMillis())
            .apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    fun isLoggedIn(): Boolean = getAccessToken() != null && getRefreshToken() != null
}
