package id.nextfl.app.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class ToastTone { INFO, SUCCESS, ERROR }

class ToastAction(val label: String, val run: () -> Unit)

data class Toast(val id: Long, val message: String, val tone: ToastTone, val action: ToastAction? = null)

data class BusyState(val message: String, val progress: Float? = null)

class UiMessages(private val scope: CoroutineScope) {
    private val ids = AtomicLong()
    private val mutableToasts = MutableStateFlow<List<Toast>>(emptyList())
    private val mutableBusy = MutableStateFlow<BusyState?>(null)
    val toasts: StateFlow<List<Toast>> = mutableToasts.asStateFlow()
    val busy: StateFlow<BusyState?> = mutableBusy.asStateFlow()

    fun toast(message: String, tone: ToastTone = ToastTone.INFO, action: ToastAction? = null) {
        val toast = Toast(ids.incrementAndGet(), message, tone, action)
        mutableToasts.update { (it + toast).takeLast(MAX_TOASTS) }
        scope.launch {
            delay(if (action != null) ACTION_DURATION_MS else DURATION_MS)
            dismiss(toast.id)
        }
    }

    fun dismiss(id: Long) = mutableToasts.update { list -> list.filterNot { it.id == id } }

    // Skips sub-percent progress updates
    fun busy(message: String, progress: Float? = null) {
        val current = mutableBusy.value
        val sameStep = current?.message == message && current.progress != null && progress != null &&
            kotlin.math.abs(current.progress - progress) < PROGRESS_STEP
        if (!sameStep) mutableBusy.value = BusyState(message, progress)
    }

    fun idle() {
        mutableBusy.value = null
    }

    private companion object {
        const val MAX_TOASTS = 3
        const val PROGRESS_STEP = 0.01f
        const val DURATION_MS = 3500L
        const val ACTION_DURATION_MS = 7000L
    }
}
