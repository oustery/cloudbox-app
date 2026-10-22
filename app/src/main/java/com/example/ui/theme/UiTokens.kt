package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp

/** Shared spacing scale for layout gaps and insets. Component sizes remain separate. */
object UiSpacing {
    val micro = 2.dp
    val extraSmall = 4.dp
    val iconGap = 6.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val doubleExtraLarge = 32.dp
}

/** Shared corner radius scale. Pills always resolve to a fully rounded stadium. */
object UiRadius {
    val small = 12.dp
    val medium = 16.dp
    val large = 20.dp
    val sheet = 28.dp
    val pill = 999.dp
}

/** Shared motion durations and easing curves for short, standard, and emphasized transitions. */
object UiMotionTokens {
    const val shortDurationMillis = 150
    const val standardDurationMillis = 250
    const val emphasizedDurationMillis = 350

    val standardEasing: Easing = FastOutSlowInEasing
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
