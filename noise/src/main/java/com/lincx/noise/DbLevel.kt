package com.lincx.noise

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 把 16-bit PCM 振幅换成「参考分贝」。
 *
 * 手机麦克风没有计量校准，也不能当作声级计。这里把数字满幅（0 dBFS）
 * 固定映射到 [FULL_SCALE_REFERENCE_DB]，只为了让数字落在大家熟悉的量级里。
 * 不同机型、不同增益下，同一个房间会差很多。
 */
object DbLevel {
    const val FULL_SCALE_REFERENCE_DB = 90.0
    const val QUIET_BELOW = 45.0
    const val LOUDISH_BELOW = 70.0

    private const val PCM_FULL_SCALE = 32768.0

    data class Window(
        val instantDb: Double,
        val peakDb: Double,
    )

    enum class Advice {
        QUIET,
        LOUDISH,
        VERY_LOUD,
    }

    fun measure(samples: ShortArray, count: Int): Window {
        if (count <= 0) return Window(0.0, 0.0)
        var sumSquares = 0.0
        var peak = 0
        val n = minOf(count, samples.size)
        for (i in 0 until n) {
            val v = samples[i].toInt()
            sumSquares += v.toDouble() * v.toDouble()
            val a = abs(v)
            if (a > peak) peak = a
        }
        val rms = sqrt(sumSquares / n)
        return Window(
            instantDb = amplitudeToReferenceDb(rms),
            peakDb = amplitudeToReferenceDb(peak.toDouble()),
        )
    }

    fun amplitudeToReferenceDb(amplitude: Double): Double {
        if (amplitude < 1.0) return 0.0
        val dbFs = 20.0 * log10(amplitude / PCM_FULL_SCALE)
        return (dbFs + FULL_SCALE_REFERENCE_DB).coerceIn(0.0, 120.0)
    }

    fun advice(instantDb: Double): Advice = when {
        instantDb < QUIET_BELOW -> Advice.QUIET
        instantDb < LOUDISH_BELOW -> Advice.LOUDISH
        else -> Advice.VERY_LOUD
    }
}
