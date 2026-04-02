package it.locati.michele.redialer

import android.app.Application
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
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

data class ContactNumber(
    val number: String,
    val typeLabel: String,
    val isPrimary: Boolean
)

data class RedialUiState(
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val phoneType: String? = null,
    val isRedialing: Boolean = false,
    val statusMessageResId: Int = R.string.idle_status,
    val delaySeconds: String = RedialViewModel.MIN_DELAY_SECONDS.toString(),
    val stopThresholdSeconds: String = RedialViewModel.MIN_STOP_THRESHOLD_SECONDS.toString(),
    val numbersToSelect: List<ContactNumber>? = null
)

class RedialViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MIN_DELAY_SECONDS = 5
        const val MIN_STOP_THRESHOLD_SECONDS = 1
    }

    private val prefs = RedialPreferences(application)
    private val _uiState = MutableStateFlow(RedialUiState())
    val uiState: StateFlow<RedialUiState> = _uiState.asStateFlow()

    private val _callRequest = MutableSharedFlow<String>()
    val callRequest = _callRequest.asSharedFlow()

    private var redialJob: Job? = null
    private val callEndedChannel = Channel<Unit>(Channel.CONFLATED)
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var callStartTime: Long = 0

    init {
        viewModelScope.launch {
            prefs.delaySeconds.collect { seconds ->
                val safeSeconds = seconds.coerceAtLeast(MIN_DELAY_SECONDS)
                _uiState.update { currentState ->
                    if (currentState.delaySeconds.toIntOrNull() != safeSeconds) {
                        currentState.copy(delaySeconds = safeSeconds.toString())
                    } else {
                        currentState
                    }
                }
            }
        }
        viewModelScope.launch {
            prefs.stopThresholdSeconds.collect { seconds ->
                val safeSeconds = seconds.coerceAtLeast(MIN_STOP_THRESHOLD_SECONDS)
                _uiState.update { currentState ->
                    if (currentState.stopThresholdSeconds.toIntOrNull() != safeSeconds) {
                        currentState.copy(stopThresholdSeconds = safeSeconds.toString())
                    } else {
                        currentState
                    }
                }
            }
        }
    }

    fun onContactSelected(name: String, numbers: List<ContactNumber>) {
        if (numbers.isEmpty()) {
            _uiState.update { it.copy(numbersToSelect = null) }
            return
        }
        
        if (numbers.size == 1) {
            updateContact(name, numbers[0].number, numbers[0].typeLabel)
        } else {
            _uiState.update { 
                it.copy(
                    contactName = name,
                    numbersToSelect = numbers 
                ) 
            }
        }
    }

    fun onNumberChosen(contactNumber: ContactNumber) {
        updateContact(_uiState.value.contactName, contactNumber.number, contactNumber.typeLabel)
    }

    fun dismissNumberSelection() {
        _uiState.update { it.copy(numbersToSelect = null) }
    }

    fun updateContact(name: String?, number: String?, type: String? = null) {
        _uiState.update {
            it.copy(
                contactName = name,
                phoneNumber = number,
                phoneType = type,
                statusMessageResId = R.string.idle_status,
                numbersToSelect = null
            )
        }
    }

    fun onPhoneNumberChange(newNumber: String) {
        _uiState.update {
            it.copy(
                phoneNumber = newNumber,
                contactName = null,
                phoneType = null,
                statusMessageResId = R.string.idle_status
            )
        }
    }

    fun onDelayChange(input: String) {
        _uiState.update { it.copy(delaySeconds = input) }
        input.toIntOrNull()?.let { seconds ->
            if (seconds >= MIN_DELAY_SECONDS) {
                viewModelScope.launch {
                    prefs.saveDelaySeconds(seconds)
                }
            }
        }
    }

    fun onDelayBlur() {
        _uiState.update { 
            it.copy(delaySeconds = effectiveDelaySeconds.toString())
        }
    }

    fun onStopThresholdChange(input: String) {
        _uiState.update { it.copy(stopThresholdSeconds = input) }
        input.toIntOrNull()?.let { seconds ->
            if (seconds >= MIN_STOP_THRESHOLD_SECONDS) {
                viewModelScope.launch {
                    prefs.saveStopThresholdSeconds(seconds)
                }
            }
        }
    }

    fun onStopThresholdBlur() {
        _uiState.update { 
            it.copy(stopThresholdSeconds = effectiveStopThresholdSeconds.toString())
        }
    }

    private val effectiveDelaySeconds: Int
        get() = _uiState.value.delaySeconds.toIntOrNull()?.coerceAtLeast(MIN_DELAY_SECONDS) ?: MIN_DELAY_SECONDS

    private val effectiveStopThresholdSeconds: Int
        get() = _uiState.value.stopThresholdSeconds.toIntOrNull()?.coerceAtLeast(MIN_STOP_THRESHOLD_SECONDS) ?: MIN_STOP_THRESHOLD_SECONDS

    fun startRedialing() {
        val number = uiState.value.phoneNumber
        if (number.isNullOrBlank()) return
        if (uiState.value.isRedialing) return

        _uiState.update { 
            it.copy(
                isRedialing = true,
                delaySeconds = effectiveDelaySeconds.toString(),
                stopThresholdSeconds = effectiveStopThresholdSeconds.toString()
            ) 
        }

        redialJob = viewModelScope.launch {
            try {
                while (uiState.value.isRedialing) {
                    _uiState.update { it.copy(statusMessageResId = R.string.calling_status) }
                    _callRequest.emit(number)
                    waitForCallToEnd()
                    if (uiState.value.isRedialing) {
                        _uiState.update { it.copy(statusMessageResId = R.string.busy_status) }
                        delay(effectiveDelaySeconds * 1000L)
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
        while (callEndedChannel.tryReceive().isSuccess) {}
        callEndedChannel.receive()
    }

    fun stopRedialing() {
        _uiState.update { it.copy(isRedialing = false) }
        redialJob?.cancel()
    }

    fun onCallStateChanged(state: Int) {
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            callStartTime = System.currentTimeMillis()
        }
        if (state == TelephonyManager.CALL_STATE_IDLE && lastState != TelephonyManager.CALL_STATE_IDLE) {
            val duration = System.currentTimeMillis() - callStartTime
            if (duration > effectiveStopThresholdSeconds * 1000L) {
                stopRedialing()
            }
            callEndedChannel.trySend(Unit)
        }
        lastState = state
    }
}
