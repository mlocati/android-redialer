package it.locati.michele.redialer

import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RedialUiState(
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val isRedialing: Boolean = false,
    val statusMessageResId: Int = R.string.idle_status,
    val delaySeconds: Int = 5
)

class RedialViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RedialUiState())
    val uiState: StateFlow<RedialUiState> = _uiState.asStateFlow()

    private val _callRequest = MutableSharedFlow<String>()
    val callRequest = _callRequest.asSharedFlow()

    private var redialJob: Job? = null
    private val callEndedChannel = Channel<Unit>(Channel.CONFLATED)
    private var lastState = TelephonyManager.CALL_STATE_IDLE

    fun updateContact(name: String?, number: String?) {
        _uiState.update {
            it.copy(
                contactName = name,
                phoneNumber = number,
                statusMessageResId = R.string.idle_status
            )
        }
    }

    fun onPhoneNumberChange(newNumber: String) {
        _uiState.update {
            it.copy(
                phoneNumber = newNumber,
                contactName = null,
                statusMessageResId = R.string.idle_status
            )
        }
    }

    fun onDelayChange(seconds: Int) {
        _uiState.update {
            it.copy(delaySeconds = seconds.coerceAtLeast(5))
        }
    }

    fun startRedialing() {
        val number = uiState.value.phoneNumber
        if (number.isNullOrBlank()) return
        if (uiState.value.isRedialing) return

        _uiState.update { it.copy(isRedialing = true) }

        redialJob = viewModelScope.launch {
            try {
                while (uiState.value.isRedialing) {
                    _uiState.update { it.copy(statusMessageResId = R.string.calling_status) }
                    _callRequest.emit(number)

                    // Wait for the call to transition away from IDLE and then back to IDLE
                    waitForCallToEnd()

                    if (uiState.value.isRedialing) {
                        _uiState.update { it.copy(statusMessageResId = R.string.busy_status) }
                        delay(uiState.value.delaySeconds * 1000L) // Dynamic delay
                    }
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isRedialing = false,
                        statusMessageResId = R.string.idle_status
                    )
                }
            }
        }
    }

    private suspend fun waitForCallToEnd() {
        // Clear any previous signals
        while (callEndedChannel.tryReceive().isSuccess) {}
        
        // Wait for signal from onCallStateChanged
        callEndedChannel.receive()
    }

    fun stopRedialing() {
        _uiState.update { it.copy(isRedialing = false) }
        redialJob?.cancel()
    }

    fun onCallStateChanged(state: Int) {
        // We look for a transition back to IDLE from any other state
        if (state == TelephonyManager.CALL_STATE_IDLE && lastState != TelephonyManager.CALL_STATE_IDLE) {
            callEndedChannel.trySend(Unit)
        }
        lastState = state
    }
}
