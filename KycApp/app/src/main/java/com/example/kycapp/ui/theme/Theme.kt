package com.example.kycapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DfsDarkColorScheme = darkColorScheme(
    primary = DfsColors.Primary,
    onPrimary = DfsColors.OnBackground,
    primaryContainer = DfsColors.PrimaryStrong,
    onPrimaryContainer = DfsColors.OnBackground,
    secondary = DfsColors.TealAccent,
    onSecondary = DfsColors.Background,
    secondaryContainer = DfsColors.SurfaceElevated,
    onSecondaryContainer = DfsColors.TealAccent,
    tertiary = DfsColors.TealAccent,
    onTertiary = DfsColors.Background,
    background = DfsColors.Background,
    onBackground = DfsColors.OnBackground,
    surface = DfsColors.SurfaceElevated,
    onSurface = DfsColors.OnBackground,
    surfaceVariant = DfsColors.SurfaceElevated,
    onSurfaceVariant = DfsColors.MutedText,
    outline = DfsColors.Border,
    outlineVariant = DfsColors.BorderStrong,
    error = DfsColors.Danger,
    onError = DfsColors.OnBackground,
    errorContainer = Color(0x33F87171),
    onErrorContainer = DfsColors.Danger
)

/** DFS Corporate dark-only theme. */
@Composable
fun KycAppTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.parseColor("#060A12")
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DfsDarkColorScheme,
        typography = DfsTypography,
        content = content
    )
}
