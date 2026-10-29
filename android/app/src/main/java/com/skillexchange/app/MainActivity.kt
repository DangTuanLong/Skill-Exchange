package com.skillexchange.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.skillexchange.app.core.notification.DeepLinkParser
import com.skillexchange.app.core.notification.FcmTokenManager
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.core.ui.theme.SkillExchangeTheme
import com.skillexchange.app.navigation.SkillExchangeNavHost
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val tokenManager: TokenManager by inject()
    private val fcmTokenManager: FcmTokenManager by inject()

    private var deepLinkRoute by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Kết quả xin quyền thông báo
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermission()

        // Sync FCM token nếu đã đăng nhập
        if (tokenManager.isLoggedIn()) {
            lifecycleScope.launch {
                try {
                    fcmTokenManager.syncCurrentToken()
                } catch (_: Exception) {}
            }
        }

        deepLinkRoute = parseDeepLink(intent)

        // Navigation Guard: kiểm tra 3 cờ (onboardingSeen, hasToken, profileCompleted)
        val destination = com.skillexchange.app.domain.model.StartDestinationDecision.decide(
            onboardingSeen = tokenManager.isOnboardingSeen(),
            hasToken = tokenManager.isLoggedIn(),
            profileCompleted = tokenManager.isProfileCompleted()
        )
        val startDestination = destination.route

        setContent {
            SkillExchangeTheme {
                SkillExchangeNavHost(
                    startDestination = startDestination,
                    deepLinkRoute = deepLinkRoute
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLink(intent)?.let { route ->
            deepLinkRoute = route
        }
    }

    private fun parseDeepLink(intent: Intent?): String? {
        val type = intent?.getStringExtra("type")
        val entityId = intent?.getStringExtra("entityId")
        val sessionId = intent?.getStringExtra("sessionId")

        // Chỉ cho phép điều hướng vào Screen.Call nếu sessionId đã được cấp quyền nội bộ
        // (từ IncomingCallActivity sau khi bấm Trả lời, hoặc từ CallForegroundService ongoing notification)
        if (type == "CALL" && !entityId.isNullOrBlank() && com.skillexchange.app.core.call.InternalCallAuth.isAuthorized(sessionId) && tokenManager.isLoggedIn()) {
            val isVideo = intent.getBooleanExtra("isVideo", false)
            return com.skillexchange.app.navigation.Screen.Call.createRoute(
                exchangeId = entityId,
                isVideo = isVideo,
                sessionId = sessionId,
                isIncoming = true
            )
        }

        // Mọi Intent từ ngoài chỉ nhận type và entityId qua DeepLinkParser
        return DeepLinkParser.parse(
            type = type,
            entityId = entityId,
            isLoggedIn = tokenManager.isLoggedIn()
        )
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
