package com.nctrl.motogallery.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * One UI se mueve con muelles suaves, no con curvas lineales. Estas
 * especificaciones se reutilizan en toda la app para que todo "caiga" igual.
 */
object Motion {
    fun <T> soft() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun offset() = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> quick() = tween<T>(durationMillis = 220)
    fun <T> medium() = tween<T>(durationMillis = 320)
}
