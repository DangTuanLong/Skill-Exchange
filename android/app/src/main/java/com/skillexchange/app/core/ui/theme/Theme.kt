package com.skillexchange.app.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

// ─── Dark Color Scheme (nền tối, hiện đại) ───────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary             = Brand400,
    onPrimary           = Color(0xFF0C1A2E),
    primaryContainer    = Brand700,
    onPrimaryContainer  = Color(0xFFE0F2FE),

    secondary           = Accent400,
    onSecondary         = Color(0xFF0C1A2E),
    secondaryContainer  = Accent600,
    onSecondaryContainer= Color(0xFFCCFBF1),

    tertiary            = Highlight,
    onTertiary          = Color.White,

    background          = DarkBackground,       // 0xFF070D1A
    onBackground        = TextPrimary,          // 0xFFF1F5F9

    surface             = DarkSurface,          // 0xFF0F1929
    onSurface           = TextPrimary,          // 0xFFF1F5F9
    surfaceVariant      = DarkSurface2,         // 0xFF162236
    onSurfaceVariant    = TextSecondary,        // 0xFF94A3B8

    outline             = DarkSurface3,         // 0xFF1E3045
    outlineVariant      = Color(0xFF1E293B),

    error               = Error,
    onError             = Color.White,
)

/**
 * SkillExchange App Theme — Hỗ trợ cả Light Mode và Dark Mode.
 */
@Composable
fun SkillExchangeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SkillExchangeTypography,
        content     = content
    )
}
