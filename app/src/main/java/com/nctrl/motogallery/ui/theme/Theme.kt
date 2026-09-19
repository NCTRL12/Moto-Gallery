package com.nctrl.motogallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = OneUi.Blue,
    onPrimary = OneUi.LightSurface,
    primaryContainer = OneUi.Blue.copy(alpha = 0.12f),
    onPrimaryContainer = OneUi.BluePressed,
    secondary = OneUi.Blue,
    tertiary = OneUi.Coral,
    background = OneUi.LightBackground,
    onBackground = OneUi.LightOnSurface,
    surface = OneUi.LightSurface,
    onSurface = OneUi.LightOnSurface,
    surfaceVariant = OneUi.LightSurfaceVariant,
    onSurfaceVariant = OneUi.LightOnSurfaceVariant,
    surfaceContainer = OneUi.LightSurface,
    surfaceContainerHigh = OneUi.LightSurfaceVariant,
    outline = OneUi.LightOutline,
    outlineVariant = OneUi.LightOutline,
    error = OneUi.Coral,
)

private val DarkColors = darkColorScheme(
    primary = OneUi.BlueDark,
    onPrimary = OneUi.DarkBackground,
    primaryContainer = OneUi.BlueDark.copy(alpha = 0.18f),
    onPrimaryContainer = OneUi.BlueDark,
    secondary = OneUi.BlueDark,
    tertiary = OneUi.Coral,
    background = OneUi.DarkBackground,
    onBackground = OneUi.DarkOnSurface,
    surface = OneUi.DarkSurface,
    onSurface = OneUi.DarkOnSurface,
    surfaceVariant = OneUi.DarkSurfaceVariant,
    onSurfaceVariant = OneUi.DarkOnSurfaceVariant,
    surfaceContainer = OneUi.DarkSurface,
    surfaceContainerHigh = OneUi.DarkSurfaceVariant,
    outline = OneUi.DarkOutline,
    outlineVariant = OneUi.DarkOutline,
    error = OneUi.Coral,
)

/**
 * Tema fijo a propósito: los colores dinámicos de Material You romperían el
 * aspecto One UI, que siempre usa el azul de Samsung sobre gris claro o negro.
 */
@Composable
fun MotoGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OneUiTypography,
        shapes = OneUiShapes,
        content = content,
    )
}
