package com.nabungah.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Semantic colors flip with the system theme. Values are composable getters so no
// screen needs to know which scheme is active.
val IncomeGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF15803D)
val ExpenseRed: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFB91C1C)
val Muted: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA8B3B0) else Color(0xFF6B7280)
val CanvasBg: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0B1211) else Color(0xFFF6F7F8)
val StatusOrange: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFBBF24) else Color(0xFFD97706)
val CardBorder: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A3A36) else Color(0xFFE6E8EB)

// Dashboard accents
val HeroTop = Color(0xFF0F766E)
val HeroBottom = Color(0xFF0B4F4A)
val OnHeroMuted = Color(0xFFB8DEDA)
val HeroIncome = Color(0xFFBBF7D0)
val HeroExpense = Color(0xFFFECACA)
val IncomeTint: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF10271C) else Color(0xFFECF8F0)
val ExpenseTint: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A1618) else Color(0xFFFDEFEF)
val SoftTeal: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF143A35) else Color(0xFFE6F4F1)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0EC),
    onPrimaryContainer = Color(0xFF073B37),
    secondary = Color(0xFF475569),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE6E8EB),
    error = Color(0xFFB91C1C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF04302C),
    primaryContainer = Color(0xFF115E56),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFCBD5E1),
    background = Color(0xFF0B1211),
    onBackground = Color(0xFFE7EFEC),
    surface = Color(0xFF121C1A),
    onSurface = Color(0xFFE7EFEC),
    surfaceVariant = Color(0xFF1B2926),
    onSurfaceVariant = Color(0xFFB6C5C1),
    outline = Color(0xFF3A4A46),
    outlineVariant = Color(0xFF2A3A36),
    error = Color(0xFFF87171),
)

@Composable
fun NabungTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
