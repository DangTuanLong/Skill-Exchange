package com.skillexchange.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.core.ui.theme.SkillExchangeTheme
import com.skillexchange.app.navigation.Screen
import com.skillexchange.app.navigation.SkillExchangeNavHost
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    // Inject TokenManager ở Activity level — an toàn, không cần Composable inject
    private val tokenManager: TokenManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Navigation Guard: kiểm tra token trước khi render
        val startDestination = if (tokenManager.isLoggedIn()) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }

        setContent {
            SkillExchangeTheme {
                SkillExchangeNavHost(startDestination = startDestination)
            }
        }
    }
}
