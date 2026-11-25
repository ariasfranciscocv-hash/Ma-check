package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBF2FC),
    onPrimaryContainer = BrandNavyDark,
    secondary = BrandRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0F1),
    onSecondaryContainer = Color(0xFF8C0811),
    tertiary = BrandGoldDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF8DF),
    onTertiaryContainer = Color(0xFF4D3800),
    background = SurfaceIvory,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFF6F9FD),
    surfaceContainerHigh = Color(0xFFF1F5FB),
    surfaceContainerHighest = Color(0xFFEAF0F8),
    outline = BorderSubtle,
    outlineVariant = Color(0xFFE7EDF6),
    error = BrandRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = BrandNavyDark,
    primaryContainer = Color(0xFF163A82),
    onPrimaryContainer = Color(0xFFDCE6FA),
    secondary = Color(0xFFFF7D85),
    onSecondary = Color(0xFF5A0008),
    secondaryContainer = Color(0xFF78121B),
    onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = BrandGold,
    onTertiary = Color(0xFF332500),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF2B3A5E),
    error = Color(0xFFFF7D85),
    onError = Color(0xFF5A0008)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MetaCheckTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
