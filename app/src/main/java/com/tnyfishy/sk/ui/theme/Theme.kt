package com.tnyfishy.sk.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Authentic MCU HCT-derived Warm Flame-Rose & Honey-Ember schemes (all 48 M3 roles populated).
// Primary: Flame Coral-Rose (H=11, C=56) | Secondary: Muted Warm Rose-Taupe (H=14, C=11)
// Tertiary: Warm Honey-Amber (H=58, C=28) | Neutral: Warm Alabaster / Espresso-Rose (H=12, C=5)
private val LightFlameColorScheme = lightColorScheme(
    primary = Color(0xFFA3394D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DC),
    onPrimaryContainer = Color(0xFF842137),
    inversePrimary = Color(0xFFFFB2BB),
    secondary = Color(0xFF6F595A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADBDC),
    onSecondaryContainer = Color(0xFF564243),
    tertiary = Color(0xFF81542E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC3),
    onTertiaryContainer = Color(0xFF663D19),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF211A1A),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF211A1A),
    surfaceVariant = Color(0xFFF6DDDE),
    onSurfaceVariant = Color(0xFF534344),
    surfaceTint = Color(0xFFA3394D),
    inverseSurface = Color(0xFF372E2F),
    inverseOnSurface = Color(0xFFFDEDED),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF867273),
    outlineVariant = Color(0xFFD9C1C2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceDim = Color(0xFFE5D7D7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F0),
    surfaceContainer = Color(0xFFFAEAEB),
    surfaceContainerHigh = Color(0xFFF4E5E5),
    surfaceContainerHighest = Color(0xFFEEDFDF),
    primaryFixed = Color(0xFFFFD9DC),
    primaryFixedDim = Color(0xFFFFB2BB),
    onPrimaryFixed = Color(0xFF400011),
    onPrimaryFixedVariant = Color(0xFF842137),
    secondaryFixed = Color(0xFFFADBDC),
    secondaryFixedDim = Color(0xFFDDC0C0),
    onSecondaryFixed = Color(0xFF271718),
    onSecondaryFixedVariant = Color(0xFF564243),
    tertiaryFixed = Color(0xFFFFDCC3),
    tertiaryFixedDim = Color(0xFFF6BA8C),
    onTertiaryFixed = Color(0xFF2F1500),
    onTertiaryFixedVariant = Color(0xFF663D19)
)

private val DarkFlameColorScheme = darkColorScheme(
    primary = Color(0xFFFFB2BB),
    onPrimary = Color(0xFF650622),
    primaryContainer = Color(0xFF842137),
    onPrimaryContainer = Color(0xFFFFD9DC),
    inversePrimary = Color(0xFFA3394D),
    secondary = Color(0xFFDDC0C0),
    onSecondary = Color(0xFF3E2C2D),
    secondaryContainer = Color(0xFF564243),
    onSecondaryContainer = Color(0xFFFADBDC),
    tertiary = Color(0xFFF6BA8C),
    onTertiary = Color(0xFF4B2705),
    tertiaryContainer = Color(0xFF663D19),
    onTertiaryContainer = Color(0xFFFFDCC3),
    background = Color(0xFF191212),
    onBackground = Color(0xFFEEDFDF),
    surface = Color(0xFF191212),
    onSurface = Color(0xFFEEDFDF),
    surfaceVariant = Color(0xFF534344),
    onSurfaceVariant = Color(0xFFD9C1C2),
    surfaceTint = Color(0xFFFFB2BB),
    inverseSurface = Color(0xFFEEDFDF),
    inverseOnSurface = Color(0xFF372E2F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690004),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA18C8D),
    outlineVariant = Color(0xFF534344),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF403737),
    surfaceDim = Color(0xFF191212),
    surfaceContainerLowest = Color(0xFF130C0D),
    surfaceContainerLow = Color(0xFF211A1A),
    surfaceContainer = Color(0xFF251E1E),
    surfaceContainerHigh = Color(0xFF302828),
    surfaceContainerHighest = Color(0xFF3B3333),
    primaryFixed = Color(0xFFFFD9DC),
    primaryFixedDim = Color(0xFFFFB2BB),
    onPrimaryFixed = Color(0xFF400011),
    onPrimaryFixedVariant = Color(0xFF842137),
    secondaryFixed = Color(0xFFFADBDC),
    secondaryFixedDim = Color(0xFFDDC0C0),
    onSecondaryFixed = Color(0xFF271718),
    onSecondaryFixedVariant = Color(0xFF564243),
    tertiaryFixed = Color(0xFFFFDCC3),
    tertiaryFixedDim = Color(0xFFF6BA8C),
    onTertiaryFixed = Color(0xFF2F1500),
    onTertiaryFixedVariant = Color(0xFF663D19)
)

/**
 * Root M3 Expressive theme. Uses the authentic 10-step Material 3 Expressive Shapes()
 * and baseline + emphasized Typography() scales along with MotionScheme.expressive().
 */
@Composable
fun StreakSaverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkFlameColorScheme
        else -> LightFlameColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
