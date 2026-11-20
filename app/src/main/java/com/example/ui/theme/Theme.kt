package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NibrasDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = ObsidianCanvas,
    primaryContainer = DeepBronze,
    onPrimaryContainer = ChampagneGold,
    secondary = EmeraldMint,
    onSecondary = ObsidianCanvas,
    secondaryContainer = DeepEmerald,
    onSecondaryContainer = EmeraldMint,
    tertiary = RoyalSapphire,
    onTertiary = ObsidianCanvas,
    background = ObsidianCanvas,
    onBackground = WarmAlabaster,
    surface = ObsidianSurface,
    onSurface = WarmAlabaster,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = MutedSand,
    outline = ObsidianCardBorder,
    outlineVariant = Color(0xFF212B3F),
    error = CrimsonRose,
    onError = ObsidianCanvas,
    errorContainer = DeepCrimson,
    onErrorContainer = CrimsonRose
)

private val NibrasLightColorScheme = lightColorScheme(
    primary = BurnishedGold,
    onPrimary = Color.White,
    primaryContainer = LightGoldContainer,
    onPrimaryContainer = Color(0xFF3E2B04),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF0D4E48),
    tertiary = Color(0xFF2B59C3),
    onTertiary = Color.White,
    background = IvoryCanvas,
    onBackground = InkCharcoal,
    surface = IvorySurface,
    onSurface = InkCharcoal,
    surfaceVariant = IvoryElevated,
    onSurfaceVariant = InkMuted,
    outline = IvoryBorder,
    outlineVariant = Color(0xFFEBE4D7),
    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val NibrasShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NibrasTheme(
    darkTheme: Boolean = true,
    followSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = if (followSystemTheme) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (useDark) NibrasDarkColorScheme else NibrasLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NibrasTypography,
        shapes = NibrasShapes,
        content = content
    )
}
