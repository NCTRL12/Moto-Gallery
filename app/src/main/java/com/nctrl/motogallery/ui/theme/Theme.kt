package com.nctrl.motogallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Purple = Color(0xFF6C4CE0)
private val PurpleLight = Color(0xFFCFBCFF)
private val Amber = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Color(0xFF615B71),
    tertiary = Amber,
    background = Color(0xFFFCFAFF),
    surface = Color(0xFFFCFAFF),
)

private val DarkColors = darkColorScheme(
    primary = PurpleLight,
    secondary = Color(0xFFCBC2DB),
    tertiary = Amber,
    background = Color(0xFF101014),
    surface = Color(0xFF16161C),
)

@Composable
fun MotoGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Android 12+ tiene colores dinámicos: la galería combina con el fondo de pantalla.
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
