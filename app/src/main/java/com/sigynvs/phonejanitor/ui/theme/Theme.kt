package com.sigynvs.phonejanitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = SandSecondaryLight,
    secondaryContainer = SandContainerLight,
    tertiary = AmberTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    background = BackgroundLight,
    surface = BackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = SandSecondaryDark,
    secondaryContainer = SandContainerDark,
    tertiary = AmberTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    background = BackgroundDark,
    surface = BackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
)

@Composable
fun PhoneJanitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
