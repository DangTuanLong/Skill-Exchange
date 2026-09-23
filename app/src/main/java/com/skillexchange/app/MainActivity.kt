package com.skillexchange.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skillexchange.app.core.ui.theme.SkillExchangeTheme
import com.skillexchange.app.navigation.SkillExchangeNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkillExchangeTheme {
                SkillExchangeNavHost()
            }
        }
    }
}
