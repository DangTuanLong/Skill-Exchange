package com.skillexchange.app.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Dark Color Scheme (mặc định cho app) ────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = Brand500,
    onPrimary        = TextPrimary,
    primaryContainer = Brand700,
    onPrimaryContainer = Brand400,

    secondary        = Accent500,
    onSecondary      = TextPrimary,
    secondaryContainer = Accent600,
    onSecondaryContainer = Accent400,

    tertiary         = Highlight,
    onTertiary       = DarkBackground,

    background       = DarkBackground,
    onBackground     = TextPrimary,

    surface          = DarkSurface,
    onSurface        = TextPrimary,
    surfaceVariant   = DarkSurface2,
    onSurfaceVariant = TextSecondary,

    outline          = DarkSurface3,
    outlineVariant   = DarkSurface2,

    error            = Error,
    onError          = TextPrimary,
)

// ─── Light Color Scheme ───────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary          = Brand600,
    onPrimary        = LightSurface,
    primaryContainer = LightSurface2,
    onPrimaryContainer = Brand700,

    secondary        = Accent600,
    onSecondary      = LightSurface,
    secondaryContainer = LightSurface2,
    onSecondaryContainer = Accent600,

    tertiary         = HighlightDark,
    onTertiary       = LightSurface,

    background       = LightBackground,
    onBackground     = TextPrimaryLight,

    surface          = LightSurface,
    onSurface        = TextPrimaryLight,
    surfaceVariant   = LightSurface2,
    onSurfaceVariant = TextSecondaryLight,

    error            = Error,
    onError          = LightSurface,
)

/**
 * SkillExchange App Theme.
 *
 * - Mặc định: Dark Mode (phù hợp audience Gen Z/Millennial Việt Nam)
 * - Hỗ trợ Dynamic Color trên Android 12+ (Material You)
 * - Tự động điều chỉnh system bar colors
 */
@Composable
fun SkillExchangeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic Color chỉ hoạt động từ Android 12+
    dynamicColor: Boolean = false, // Tắt để đảm bảo brand colors nhất quán
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Cấu hình system bar (status bar + navigation bar)
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
