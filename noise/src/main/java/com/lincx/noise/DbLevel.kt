package com.lincx.noise

import kotlin.math.abs
import kotlin.math.log10

/**
 * 把数字幅度映射成「参考分贝」，并划分仅供浏览的吵闹档。
 *
 * 0 dBFS 固定当成 [FULL_SCALE_REFERENCE_DB]。手机麦克风没有计量校准，
 * 这个锚点只是为了让数字落在熟悉的量级里。安静房间偏移是在此之上的平移。
 */
object DbLevel {
    const val FULL_SCALE_REFERENCE_DB = 90.0
    const val QUIET_ROOM_REFERENCE_DB = 30.0
    const val QUIET_ROOM_MIN_SECONDS = 1.95
    const val MAX_OFFSET_DB = 60.0
    const val MAX_DISPLAY_DB = 140.0
    const val QUIET_BELOW = 45.0
    const val LOUDISH_BELOW = 70.0

    private const val PCM_FULL_SCALE = 32768.0
    private const val MIN_CALIBRATION_LEVEL_DB = 5.0

    enum class Advice {
        QUIET,
        LOUDISH,
        VERY_LOUD,
    }

    enum class CalibrationKind {
        TOO_SHORT,
        SIGNAL_TOO_LOW,
        OFFSET_OUT_OF_RANGE,
        APPLIED,
    }

    data class CalibrationDecision(
        val kind: CalibrationKind,
        val offsetDb: Double? = null,
        val measuredDb: Double? = null,
    )

    fun amplitudeToReferenceDb(amplitude: Double): Double {
        if (amplitude < 1.0) return 0.0
        val dbFs = 20.0 * log10(amplitude / PCM_FULL_SCALE)
        return (dbFs + FULL_SCALE_REFERENCE_DB).coerceIn(0.0, MAX_DISPLAY_DB)
    }

    fun fromMeanSquare(meanSquare: Double, offsetDb: Double = 0.0): Double {
        if (meanSquare <= 1e-20) return 0.0
        return displayDb(unclampedMeanSquareDb(meanSquare) + offsetDb)
    }

    fun fromAmplitude(amplitude: Double, offsetDb: Double = 0.0): Double {
        if (amplitude <= 0.0) return 0.0
        val db = 20.0 * log10(amplitude / PCM_FULL_SCALE) + FULL_SCALE_REFERENCE_DB + offsetDb
        return displayDb(db)
    }

    /** 不做显示钳位，供安静房间偏移估计真实差距。过小的能量记为很低的值。 */
    fun unclampedMeanSquareDb(meanSquare: Double): Double {
        if (meanSquare <= 1e-20) return -120.0
        return 10.0 * log10(meanSquare / (PCM_FULL_SCALE * PCM_FULL_SCALE)) + FULL_SCALE_REFERENCE_DB
    }

    fun displayDb(db: Double): Double = db.coerceIn(0.0, MAX_DISPLAY_DB)

    fun advice(instantDb: Double): Advice = when {
        instantDb < QUIET_BELOW -> Advice.QUIET
        instantDb < LOUDISH_BELOW -> Advice.LOUDISH
        else -> Advice.VERY_LOUD
    }

    /**
     * 用最近一段未加偏移的等效声级，求把该段平移到 [QUIET_ROOM_REFERENCE_DB] 所需的偏移。
     * 调用方需要先确认用户是在自认为安静的房间里测的。
     */
    fun decideQuietRoomCalibration(recentSeconds: Double, measuredDb: Double?): CalibrationDecision {
        if (measuredDb == null || recentSeconds < QUIET_ROOM_MIN_SECONDS) {
            return CalibrationDecision(CalibrationKind.TOO_SHORT)
        }
        if (measuredDb < MIN_CALIBRATION_LEVEL_DB) {
            return CalibrationDecision(CalibrationKind.SIGNAL_TOO_LOW, measuredDb = measuredDb)
        }
        val offset = QUIET_ROOM_REFERENCE_DB - measuredDb
        if (abs(offset) > MAX_OFFSET_DB) {
            return CalibrationDecision(CalibrationKind.OFFSET_OUT_OF_RANGE, measuredDb = measuredDb)
        }
        return CalibrationDecision(CalibrationKind.APPLIED, offsetDb = offset, measuredDb = measuredDb)
    }
}
