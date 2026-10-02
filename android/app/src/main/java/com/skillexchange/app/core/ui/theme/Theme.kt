package com.skillexchange.app.core.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Light Color Scheme (nền trắng, chuyên nghiệp) ───────────────────
private val LightColorScheme = lightColorScheme(
    primary             = Brand500,           // #0EA5E9 Sky Blue
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFFE0F2FE),  // Light sky
    onPrimaryContainer  = Brand700,

    secondary           = Accent500,          // #14B8A6 Teal
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFCCFBF1),
    onSecondaryContainer= Accent600,

    tertiary            = Highlight,
    onTertiary          = Color.White,

    background          = Color(0xFFF8FAFF),  // Off-white (rất nhẹ)
    onBackground        = TextPrimaryLight,   // #0C1A2E

    surface             = Color.White,
    onSurface           = TextPrimaryLight,
    surfaceVariant      = Color(0xFFF0F6FF),
    onSurfaceVariant    = TextSecondaryLight,

    outline             = Color(0xFFCBD5E1),
    outlineVariant      = Color(0xFFE2E8F0),

    error               = Error,
    onError             = Color.White,
)

/**
 * SkillExchange App Theme — Light Mode mặc định.
 */
@Composable
fun SkillExchangeTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = 0xFFF8FAFF.toInt()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = SkillExchangeTypography,
        content     = content
    )
}
