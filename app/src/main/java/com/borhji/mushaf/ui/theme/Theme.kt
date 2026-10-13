package com.borhji.mushaf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E4B3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCE8DE),
    onPrimaryContainer = Color(0xFF05231B),
    secondary = Color(0xFF8A6A1F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6E3B8),
    onSecondaryContainer = Color(0xFF2A1E00),
    tertiary = Color(0xFF3D6470),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF9F7F2),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFF9F7F2),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFDDE5E0),
    onSurfaceVariant = Color(0xFF414945),
    outline = Color(0xFF717974),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD4BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0B5343),
    onPrimaryContainer = Color(0xFFAAEFD8),
    secondary = Color(0xFFDCC27E),
    onSecondary = Color(0xFF3A2E05),
    secondaryContainer = Color(0xFF574418),
    onSecondaryContainer = Color(0xFFF9E3A8),
    tertiary = Color(0xFFA4CDDB),
    onTertiary = Color(0xFF06333E),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF8A938D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun BorhjiMushafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
