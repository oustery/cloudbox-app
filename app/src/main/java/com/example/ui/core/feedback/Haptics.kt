package com.example.ui.core.feedback

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Thin haptics wrapper around the host [View]. Compose's own
 * [androidx.compose.ui.hapticfeedback.HapticFeedback] exposes only
 * LongPress/TextHandleMove on older compose-ui versions, so we speak to the
 * platform constants directly and degrade gracefully below API 30.
 */
class Haptics(private val view: View) {

    /** Light tick for minor confirmations (favorite, enqueue, chip pick). */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Distinct "done" pulse where the platform supports it. */
    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** Distinct "rejected/wrong" pulse where the platform supports it. */
    fun reject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
