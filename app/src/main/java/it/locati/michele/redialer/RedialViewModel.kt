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
    val delaySeconds: Int = RedialViewModel.DEFAULT_DELAY_SECONDS,
    val stopThresholdSeconds: Int = RedialViewModel.DEFAULT_STOP_THRESHOLD_SECONDS,
    val optionsExpanded: Boolean = RedialPreferences.DEFAULT_OPTIONS_EXPANDED,
    val numbersToSelect: List<ContactNumber>? = null
)

class RedialViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MIN_DELAY_SECONDS = 5
        const val MAX_DELAY_SECONDS = 120
        const val DEFAULT_DELAY_SECONDS = 10

        const val MIN_STOP_THRESHOLD_SECONDS = 1
        const val MAX_STOP_THRESHOLD_SECONDS = 10
        const val DEFAULT_STOP_THRESHOLD_SECONDS = 2
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
                val safeSeconds = seconds.coerceIn(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)
                _uiState.update { it.copy(delaySeconds = safeSeconds) }
            }
        }
        viewModelScope.launch {
            prefs.stopThresholdSeconds.collect { seconds ->
                val safeSeconds = seconds.coerceIn(MIN_STOP_THRESHOLD_SECONDS, MAX_STOP_THRESHOLD_SECONDS)
                _uiState.update { it.copy(stopThresholdSeconds = safeSeconds) }
            }
        }
        viewModelScope.launch {
            prefs.optionsExpanded.collect { expanded ->
                _uiState.update { it.copy(optionsExpanded = expanded) }
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

    fun onDelayChange(seconds: Int) {
        val safeSeconds = seconds.coerceIn(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)
        _uiState.update { it.copy(delaySeconds = safeSeconds) }
        viewModelScope.launch {
            prefs.saveDelaySeconds(safeSeconds)
        }
    }

    fun onStopThresholdChange(seconds: Int) {
        val safeSeconds = seconds.coerceIn(MIN_STOP_THRESHOLD_SECONDS, MAX_STOP_THRESHOLD_SECONDS)
        _uiState.update { it.copy(stopThresholdSeconds = safeSeconds) }
        viewModelScope.launch {
            prefs.saveStopThresholdSeconds(safeSeconds)
        }
    }

    fun toggleOptions() {
        val newExpanded = !uiState.value.optionsExpanded
        _uiState.update { it.copy(optionsExpanded = newExpanded) }
        viewModelScope.launch {
            prefs.saveOptionsExpanded(newExpanded)
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
                    waitForCallToEnd()
                    if (uiState.value.isRedialing) {
                        _uiState.update { it.copy(statusMessageResId = R.string.busy_status) }
                        delay(uiState.value.delaySeconds * 1000L)
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
            if (duration > uiState.value.stopThresholdSeconds * 1000L) {
                stopRedialing()
            }
            callEndedChannel.trySend(Unit)
        }
        lastState = state
    }
}
