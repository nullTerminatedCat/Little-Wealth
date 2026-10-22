package com.lincx.licai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Pine = Color(0xFF1B3330)
private val PineDeep = Color(0xFF122421)
private val Paper = Color(0xFFF3EEE6)
private val Card = Color(0xFFFFFBF6)
private val Ink = Color(0xFF1E1A16)
private val Muted = Color(0xFF6E655C)
private val Line = Color(0xFFE4DCD0)
private val Income = Color(0xFF1B7A4B)
private val Expense = Color(0xFFC2412D)
private val Caution = Color(0xFFB45309)
private val Brass = Color(0xFF8A5A32)

private val LightScheme = lightColorScheme(
    primary = Pine,
    onPrimary = Color(0xFFF6F1E7),
    primaryContainer = Color(0xFFD5E4DF),
    onPrimaryContainer = PineDeep,
    secondary = Brass,
    onSecondary = Color(0xFFFFFBF6),
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D6),
    onSurfaceVariant = Muted,
    outline = Line,
    error = Expense,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB7D4CC),
    onPrimary = PineDeep,
    primaryContainer = Color(0xFF244540),
    onPrimaryContainer = Color(0xFFD5E4DF),
    secondary = Color(0xFFE2C2A4),
    onSecondary = Color(0xFF3A2416),
    background = Color(0xFF141210),
    onBackground = Color(0xFFF3EEE6),
    surface = Color(0xFF1E1B18),
    onSurface = Color(0xFFF3EEE6),
    surfaceVariant = Color(0xFF2C2824),
    onSurfaceVariant = Color(0xFFC9C0B2),
    outline = Color(0xFF4A433C),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690005),
)

@Immutable
data class FinanceColors(
    val balance: Color,
    val onBalance: Color,
    val balanceMuted: Color,
    val income: Color,
    val expense: Color,
    val caution: Color,
)

private val LightFinance = FinanceColors(
    balance = Pine,
    onBalance = Color(0xFFF6F1E7),
    balanceMuted = Color(0xFFC9C0B2),
    income = Income,
    expense = Expense,
    caution = Caution,
)

private val DarkFinance = FinanceColors(
    balance = Color(0xFF243E3A),
    onBalance = Color(0xFFF6F1E7),
    balanceMuted = Color(0xFFC9C0B2),
    income = Color(0xFF7DCEA0),
    expense = Color(0xFFFFB4A8),
    caution = Color(0xFFF5C16C),
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

@Composable
fun LicaiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalFinanceColors provides if (dark) DarkFinance else LightFinance) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            content = content,
        )
    }
}
