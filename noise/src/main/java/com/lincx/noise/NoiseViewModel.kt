package com.lincx.noise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NoiseReading(
    val id: Long,
    val timeMillis: Long,
    val instantDb: Double,
    val peakDb: Double,
)

data class NoiseUiState(
    val wantsRunning: Boolean = false,
    val running: Boolean = false,
    val instantDb: Double? = null,
    val windowPeakDb: Double? = null,
    val sessionPeakDb: Double = 0.0,
    val readings: List<NoiseReading> = emptyList(),
    val silenceHint: String? = null,
    val error: String? = null,
)

class NoiseViewModel : ViewModel() {
    private val _ui = MutableStateFlow(NoiseUiState())
    val ui: StateFlow<NoiseUiState> = _ui.asStateFlow()

    private var capture: Job? = null
    private var sessionPeak = 0.0
    private var nextId = 1L
    private var zeroStreak = 0

    fun userStart() {
        sessionPeak = 0.0
        zeroStreak = 0
        _ui.update {
            it.copy(
                wantsRunning = true,
                instantDb = null,
                windowPeakDb = null,
                sessionPeakDb = 0.0,
                error = null,
                silenceHint = null,
            )
        }
        capture()
    }

    fun userStop() {
        _ui.update { it.copy(wantsRunning = false, running = false) }
        stopCapture()
    }

    fun onForeground() {
        if (_ui.value.wantsRunning) capture()
    }

    fun onBackground() {
        _ui.update { it.copy(running = false) }
        stopCapture()
    }

    fun resetPeak() {
        sessionPeak = _ui.value.instantDb ?: 0.0
        _ui.update { it.copy(sessionPeakDb = sessionPeak) }
    }

    private fun capture() {
        if (capture?.isActive == true) return
        capture = viewModelScope.launch {
            _ui.update { it.copy(running = true, error = null) }
            try {
                noiseReadings().collect { window ->
                    if (!isActive || !_ui.value.wantsRunning) return@collect
                    val silent = window.instantDb == 0.0 && window.peakDb == 0.0
                    zeroStreak = if (silent) zeroStreak + 1 else 0
                    if (window.instantDb > sessionPeak) sessionPeak = window.instantDb
                    val reading = NoiseReading(
                        id = nextId++,
                        timeMillis = System.currentTimeMillis(),
                        instantDb = window.instantDb,
                        peakDb = window.peakDb,
                    )
                    _ui.update { state ->
                        if (!state.wantsRunning) {
                            state
                        } else {
                            state.copy(
                                running = true,
                                instantDb = window.instantDb,
                                windowPeakDb = window.peakDb,
                                sessionPeakDb = sessionPeak,
                                readings = (listOf(reading) + state.readings).take(MAX_READINGS),
                                silenceHint = if (zeroStreak >= 8) {
                                    "持续读到静音。请确认系统允许本应用使用麦克风，且没有被其他应用占用。"
                                } else {
                                    null
                                },
                                error = null,
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _ui.update {
                    it.copy(
                        wantsRunning = false,
                        running = false,
                        error = t.message?.takeIf { msg -> msg.isNotBlank() } ?: "测量中断",
                    )
                }
            }
        }
    }

    private fun stopCapture() {
        capture?.cancel()
        capture = null
    }

    private companion object {
        const val MAX_READINGS = 40
    }
}
