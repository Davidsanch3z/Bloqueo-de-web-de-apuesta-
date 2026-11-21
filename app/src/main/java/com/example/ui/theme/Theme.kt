package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PromesaDarkColorScheme = darkColorScheme(
    primary = CrimsonAccent,
    onPrimary = TextPrimary,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = CrimsonSoft,
    secondary = EmeraldShield,
    onSecondary = MidnightBg,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldShield,
    tertiary = AmberPause,
    onTertiary = MidnightBg,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberPause,
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = MidnightOutline,
    outlineVariant = MidnightOutline.copy(alpha = 0.5f),
    error = CrimsonAccent,
    onError = TextPrimary,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonSoft
)

val PromesaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PromesaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PromesaDarkColorScheme,
        typography = PromesaTypography,
        shapes = PromesaShapes,
        content = content
    )
}
