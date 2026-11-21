package com.nabungah.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IncomeGreen = Color(0xFF15803D)
val ExpenseRed = Color(0xFFB91C1C)
val Muted = Color(0xFF6B7280)
val CanvasBg = Color(0xFFF6F7F8)
val StatusOrange = Color(0xFFD97706)
val CardBorder = Color(0xFFE6E8EB)

private val Colors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0EC),
    onPrimaryContainer = Color(0xFF073B37),
    secondary = Color(0xFF475569),
    background = CanvasBg,
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    error = ExpenseRed,
)

@Composable
fun NabungTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
