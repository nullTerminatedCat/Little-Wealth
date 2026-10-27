package com.lincx.noise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    val leqDb: Double? = null,
    val offsetDb: Double = 0.0,
    val recentSeconds: Double = 0.0,
    val sampleRate: Int? = null,
    val readings: List<NoiseReading> = emptyList(),
    val silenceHint: String? = null,
    val clippingHint: String? = null,
    val calibrationMessage: String? = null,
    val error: String? = null,
)

class NoiseViewModel : ViewModel() {
    private val _ui = MutableStateFlow(NoiseUiState())
    val ui: StateFlow<NoiseUiState> = _ui.asStateFlow()

    private val meter = SplMeter()
    private var capture: Job? = null
    private var nextId = 1L
    private var zeroStreak = 0

    fun userStart() {
        stopCapture()
        meter.resetSession()
        zeroStreak = 0
        _ui.update {
            it.copy(
                wantsRunning = true,
                instantDb = null,
                windowPeakDb = null,
                sessionPeakDb = 0.0,
                leqDb = null,
                recentSeconds = 0.0,
                sampleRate = null,
                error = null,
                silenceHint = null,
                clippingHint = null,
                calibrationMessage = null,
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
        meter.rebasePeakToFast()
        val current = _ui.value.instantDb ?: return
        _ui.update { it.copy(sessionPeakDb = current) }
    }

    fun setOffset(offsetDb: Double) {
        val previous = meter.offsetDb()
        val next = offsetDb.coerceIn(-DbLevel.MAX_OFFSET_DB, DbLevel.MAX_OFFSET_DB)
        meter.setOffsetDb(next)
        val delta = next - previous
        _ui.update { state ->
            if (delta == 0.0 || state.instantDb == null) {
                state.copy(offsetDb = next)
            } else {
                state.copy(
                    offsetDb = next,
                    instantDb = shift(state.instantDb, delta),
                    windowPeakDb = state.windowPeakDb?.let { shift(it, delta) },
                    sessionPeakDb = shift(state.sessionPeakDb, delta),
                    leqDb = state.leqDb?.let { shift(it, delta) },
                    readings = state.readings.map { row ->
                        row.copy(
                            instantDb = shift(row.instantDb, delta),
                            peakDb = shift(row.peakDb, delta),
                        )
                    },
                )
            }
        }
    }

    fun calibrateQuietRoom() {
        val decision = DbLevel.decideQuietRoomCalibration(
            meter.recentSeconds(),
            meter.recentUncalibratedLeqDb(),
        )
        if (decision.offsetDb != null) setOffset(decision.offsetDb)
        _ui.update { it.copy(calibrationMessage = calibrationMessage(decision)) }
    }

    fun clearOffset() {
        setOffset(0.0)
        _ui.update {
            it.copy(calibrationMessage = "已清除偏移。读数回到满幅参考，仍然不是计量值。")
        }
    }

    private fun capture() {
        if (capture?.isActive == true) return
        capture = viewModelScope.launch {
            _ui.update { it.copy(running = true, error = null) }
            try {
                microphonePcm().collect { window ->
                    val frame = withContext(Dispatchers.Default) {
                        meter.process(window.samples, window.count, window.sampleRate)
                    }
                    if (!isActive || !_ui.value.wantsRunning) return@collect
                    if (frame.generation != meter.generation()) return@collect
                    val extra = meter.offsetDb() - frame.offsetDb
                    val fast = shift(frame.fastDb, extra)
                    val windowPeak = shift(frame.windowPeakDb, extra)
                    val sessionPeak = shift(frame.sessionPeakDb, extra)
                    val leq = shift(frame.leqDb, extra)
                    zeroStreak = if (frame.rawSilent) zeroStreak + 1 else 0
                    val reading = NoiseReading(
                        id = nextId++,
                        timeMillis = System.currentTimeMillis(),
                        instantDb = fast,
                        peakDb = windowPeak,
                    )
                    _ui.update { state ->
                        if (!state.wantsRunning) {
                            state
                        } else {
                            state.copy(
                                running = true,
                                instantDb = fast,
                                windowPeakDb = windowPeak,
                                sessionPeakDb = sessionPeak,
                                leqDb = leq,
                                recentSeconds = frame.recentSeconds,
                                sampleRate = frame.sampleRate,
                                readings = (listOf(reading) + state.readings).take(MAX_READINGS),
                                silenceHint = if (zeroStreak >= 8) {
                                    "持续读到静音。请确认系统允许本应用使用麦克风，且没有被其他应用占用。"
                                } else {
                                    null
                                },
                                clippingHint = if (frame.clipped) {
                                    "声音已经顶满麦克风，读数会偏低。离声源远一些再测。"
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

    private fun calibrationMessage(decision: DbLevel.CalibrationDecision): String {
        val measured = decision.measuredDb?.let { formatDb(it) }
        return when (decision.kind) {
            DbLevel.CalibrationKind.TOO_SHORT ->
                "还不到约 2 秒有效采样。请在安静房间里保持测量后再设。"
            DbLevel.CalibrationKind.SIGNAL_TOO_LOW ->
                "当前几乎没有声音信号。请确认已允许麦克风，且没有被其他应用占用。"
            DbLevel.CalibrationKind.OFFSET_OUT_OF_RANGE ->
                "算出来的偏移超过 ±${DbLevel.MAX_OFFSET_DB.toInt()} dB，没有套用。请确认房间安静、麦克风正常。"
            DbLevel.CalibrationKind.APPLIED ->
                "已把刚才约 ${measured} dB(A) 平移到 ${DbLevel.QUIET_ROOM_REFERENCE_DB.toInt()}。这只是安静房间偏移，不是计量校准。"
        }
    }

    private fun shift(value: Double, delta: Double): Double =
        DbLevel.displayDb(value + delta)

    private fun formatDb(value: Double): String =
        String.format(Locale.CHINA, "%.1f", value)

    private companion object {
        const val MAX_READINGS = 40
    }
}
