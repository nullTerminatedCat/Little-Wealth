package com.lincx.noise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF1C2430)
private val Paper = Color(0xFFF4F1EA)
private val Card = Color(0xFFFFFCF7)
private val Muted = Color(0xFF5C6570)
private val Line = Color(0xFFE4DDD2)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Color(0xFFE7E1D6),
    onPrimaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE6DC),
    onSurfaceVariant = Muted,
    outline = Line,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE7E1D6),
    onPrimary = Ink,
    primaryContainer = Color(0xFF2A3342),
    onPrimaryContainer = Color(0xFFE7E1D6),
    background = Color(0xFF12171F),
    onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF1C2430),
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF2A3342),
    onSurfaceVariant = Color(0xFFC5CBD3),
    outline = Color(0xFF3D4654),
)

@Composable
fun NoiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
