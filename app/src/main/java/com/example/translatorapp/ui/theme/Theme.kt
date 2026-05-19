package com.example.translatorapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = LightGray,
    background = Black,
    surface = DarkSurface,
    onSurface = White,
    onBackground = White,
    outline = DarkGray,
    surfaceVariant = DarkCard,
    onSurfaceVariant = LightGray,
    error = White,
)

@Composable
fun TranslatorAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
