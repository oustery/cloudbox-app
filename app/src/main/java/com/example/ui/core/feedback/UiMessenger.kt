package com.example.ui.core.feedback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * One-shot UI feedback event delivered to the app-level SnackbarHost.
 *
 * Carries an optional action (e.g. "Отменить" for undo); the callback is
 * invoked by the host when the user taps the snackbar action. Keeping the
 * payload as data (not a composable call) lets any layer — ViewModels,
 * repositories, screens — post feedback without knowing about composition.
 */
data class UiMessage(
    val id: Long,
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * App-wide feedback bus. Emit from anywhere; the single SnackbarHost in
 * [com.example.MainActivity] renders the events. Undo-style messages use a
 * long duration so the action stays reachable.
 */
object UiMessenger {

    private val counter = AtomicLong(0L)

    private val _messages = MutableSharedFlow<UiMessage>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Show a plain informational snackbar (short duration). */
    fun show(text: String) {
        _messages.tryEmit(UiMessage(id = counter.incrementAndGet(), text = text))
    }

    /** Show a snackbar with a trailing action (long duration, e.g. undo). */
    fun showWithAction(text: String, actionLabel: String, onAction: () -> Unit) {
        _messages.tryEmit(
            UiMessage(
                id = counter.incrementAndGet(),
                text = text,
                actionLabel = actionLabel,
                onAction = onAction
            )
        )
    }
}
