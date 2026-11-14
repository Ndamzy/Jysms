package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val JyLightColorScheme = lightColorScheme(
    primary = JyNavy,
    onPrimary = Color.White,
    primaryContainer = JyNavyContainer,
    onPrimaryContainer = JyOnNavyContainer,
    secondary = JyEmerald,
    onSecondary = Color.White,
    secondaryContainer = JyEmeraldContainer,
    onSecondaryContainer = JyOnEmeraldContainer,
    tertiary = JyGold,
    onTertiary = Color.White,
    tertiaryContainer = JyGoldContainer,
    onTertiaryContainer = JyOnGoldContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val JyDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7CB2FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = JyNavyLight,
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF4ADEA0),
    onSecondary = Color(0xFF003823),
    secondaryContainer = JyEmeraldDark,
    onSecondaryContainer = JyEmeraldContainer,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF422006),
    tertiaryContainer = JyGoldDark,
    onTertiaryContainer = JyGoldContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

val JyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JySmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> JyDarkColorScheme
        else -> JyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JyTypography,
        shapes = JyShapes,
        content = content
    )
}
