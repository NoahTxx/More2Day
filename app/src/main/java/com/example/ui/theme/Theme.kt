package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.util.LocalAccentColor
import com.example.util.LocalBodyFontFamily
import com.example.util.LocalHeadingFontFamily

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = NeutralDarkBackground,
    primaryContainer = NeutralDarkSurfaceVariant,
    onPrimaryContainer = NeutralDarkTextPrimary,
    secondary = NeutralDarkTextSecondary,
    onSecondary = NeutralDarkTextPrimary,
    secondaryContainer = NeutralDarkSurfaceVariant,
    onSecondaryContainer = NeutralDarkTextPrimary,
    tertiary = AccentMonochrome,
    onTertiary = NeutralDarkBackground,
    tertiaryContainer = Color(0x1AFFFFFF),
    onTertiaryContainer = Color.White,
    background = NeutralDarkBackground,
    onBackground = NeutralDarkTextPrimary,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkTextPrimary,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkTextSecondary,
    outline = NeutralDarkBorder,
    outlineVariant = NeutralDarkBorderSubtle
)

private val LightColorScheme = lightColorScheme(
    primary = NeutralLightTextPrimary,
    onPrimary = Color.White,
    primaryContainer = NeutralLightSurfaceVariant,
    onPrimaryContainer = NeutralLightTextPrimary,
    secondary = NeutralLightTextSecondary,
    onSecondary = NeutralLightTextPrimary,
    secondaryContainer = NeutralLightSurfaceVariant,
    onSecondaryContainer = NeutralLightTextPrimary,
    tertiary = AccentMonochrome,
    onTertiary = Color.White,
    tertiaryContainer = Color(0x1A000000),
    onTertiaryContainer = NeutralLightTextPrimary,
    background = NeutralLightBackground,
    onBackground = NeutralLightTextPrimary,
    surface = NeutralLightSurface,
    onSurface = NeutralLightTextPrimary,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightTextSecondary,
    outline = NeutralLightBorder,
    outlineVariant = NeutralLightBorderSubtle
)

@Composable
fun MyApplicationTheme(
    accentColor: Color = LocalAccentColor.current,
    headingFont: FontFamily = LocalHeadingFontFamily.current,
    bodyFont: FontFamily = LocalBodyFontFamily.current,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicDark = DarkColorScheme.copy(
        tertiary = accentColor,
        tertiaryContainer = accentColor.copy(alpha = 0.15f),
        onTertiaryContainer = accentColor
    )
    val dynamicLight = LightColorScheme.copy(
        tertiary = accentColor,
        tertiaryContainer = accentColor.copy(alpha = 0.15f),
        onTertiaryContainer = accentColor
    )
    val colorScheme = if (darkTheme) dynamicDark else dynamicLight
    MaterialTheme(
        colorScheme = colorScheme,
        typography = createAppTypography(headingFont, bodyFont),
        content = content
    )
}
