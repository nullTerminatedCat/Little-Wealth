package com.lincx.noise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class SplMeterTest {
    @Test
    fun aWeightingAt48kTracksDesignCurve() {
        val filter = AWeightingFilter(48_000)
        assertEquals(0.0, filter.responseDb(1_000.0), 0.05)
        val frequencies = doubleArrayOf(63.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0)
        for (frequency in frequencies) {
            val error = abs(filter.responseDb(frequency) - AWeightingFilter.designDb(frequency))
            // 8 kHz 处双线性变换有大约 0.6 dB 的频率弯折，仍落在 1 级声级计该频点的允差里。
            val limit = if (frequency >= 8_000.0) 0.7 else 0.35
            assertTrue("$frequency Hz 偏差 $error dB", error < limit)
        }
    }

    @Test
    fun aWeightingAt16kStillUnityAtOneKilohertz() {
        val filter = AWeightingFilter(16_000)
        assertEquals(0.0, filter.responseDb(1_000.0), 0.05)
        val error = abs(filter.responseDb(100.0) - AWeightingFilter.designDb(100.0))
        assertTrue("100 Hz @ 16 kHz 偏差 $error dB", error < 1.0)
    }

    @Test
    fun oneKilohertzSineLeqMatchesRmsReference() {
        val frame = measure(frequency = 1_000.0, seconds = 1.0, amplitude = 16_000.0)
        val expected = sineRmsDb(16_000.0)
        assertEquals(expected, frame.leqDb, 0.25)
        assertEquals(expected, frame.fastDb, 0.25)
    }

    @Test
    fun hundredHertzIsAboutNineteenDbDown() {
        val high = measure(frequency = 1_000.0, seconds = 1.0, amplitude = 16_000.0)
        val low = measure(frequency = 100.0, seconds = 1.0, amplitude = 16_000.0)
        val drop = high.leqDb - low.leqDb
        assertEquals(19.1, drop, 0.45)
    }

    @Test
    fun directCurrentSettlesNearSilence() {
        val filter = AWeightingFilter(48_000)
        repeat(4_800) { filter.process(16_000.0) }
        var sum = 0.0
        repeat(4_800) {
            val y = filter.process(16_000.0)
            sum += y * y
        }
        val steadyDb = DbLevel.unclampedMeanSquareDb(sum / 4_800.0)
        val toneDb = sineRmsDb(16_000.0)
        assertTrue("直流稳态 $steadyDb dB，1 kHz 为 $toneDb dB", steadyDb < toneDb - 60.0)

        // 快档时间常数 0.125 秒，开机阶跃要几秒才会从读数里褪掉。
        val samples = ShortArray(48_000 * 3) { 16_000 }
        val frame = SplMeter().process(samples, samples.size, 48_000)
        assertTrue("快档仍有 ${frame.fastDb} dB", frame.fastDb < 1.0)
    }

    @Test
    fun peakSitsAboutThreeDbAboveLeqForSine() {
        val frame = measure(frequency = 1_000.0, seconds = 1.0, amplitude = 16_000.0)
        assertEquals(frame.leqDb + 20.0 * log10(sqrt(2.0)), frame.sessionPeakDb, 0.6)
    }

    @Test
    fun fastDoesNotJumpTheFullStep() {
        val meter = SplMeter()
        val rate = 48_000
        meter.process(sine(1_000.0, rate, 0.30, 4_000.0), (0.30 * rate).roundToInt(), rate)
        val before = meter.process(sine(1_000.0, rate, 0.20, 4_000.0), (0.20 * rate).roundToInt(), rate)
        val after = meter.process(sine(1_000.0, rate, 0.05, 16_000.0), (0.05 * rate).roundToInt(), rate)
        val fastRise = after.fastDb - before.fastDb
        val peakRise = after.sessionPeakDb - before.sessionPeakDb
        assertTrue("快档升了 $fastRise dB", fastRise in 4.0..10.5)
        assertTrue("峰值升了 $peakRise dB", peakRise > 10.0)
    }

    @Test
    fun offsetShiftsFastLeqAndPeakTogether() {
        val plain = measure(frequency = 1_000.0, seconds = 0.5, amplitude = 8_000.0)
        val meter = SplMeter()
        meter.setOffsetDb(6.0)
        val shifted = meter.process(sine(1_000.0, 48_000, 0.5, 8_000.0), 24_000, 48_000)
        assertEquals(plain.leqDb + 6.0, shifted.leqDb, 0.05)
        assertEquals(plain.fastDb + 6.0, shifted.fastDb, 0.05)
        assertEquals(plain.sessionPeakDb + 6.0, shifted.sessionPeakDb, 0.05)
    }

    @Test
    fun twoSecondsOfAudioCanBeReadForCalibration() {
        val meter = SplMeter()
        val chunk = sine(1_000.0, 48_000, 0.2, 8_000.0)
        repeat(10) { meter.process(chunk, chunk.size, 48_000) }
        assertTrue(meter.recentSeconds() >= DbLevel.QUIET_ROOM_MIN_SECONDS)
        val measured = meter.recentUncalibratedLeqDb()!!
        val decision = DbLevel.decideQuietRoomCalibration(meter.recentSeconds(), measured)
        assertEquals(DbLevel.CalibrationKind.APPLIED, decision.kind)
        assertEquals(DbLevel.QUIET_ROOM_REFERENCE_DB, measured + decision.offsetDb!!, 0.001)
    }

    @Test
    fun silenceStaysAtTheFloor() {
        val frame = SplMeter().process(ShortArray(9_600), 9_600, 48_000)
        assertEquals(0.0, frame.fastDb, 0.0)
        assertEquals(0.0, frame.leqDb, 0.0)
        assertTrue(frame.rawSilent)
    }

    private fun measure(frequency: Double, seconds: Double, amplitude: Double): SplFrame {
        val rate = 48_000
        val samples = sine(frequency, rate, seconds, amplitude)
        return SplMeter().process(samples, samples.size, rate)
    }

    private fun sine(frequency: Double, sampleRate: Int, seconds: Double, amplitude: Double): ShortArray {
        val count = (seconds * sampleRate).roundToInt()
        return ShortArray(count) { index ->
            (amplitude * sin(2.0 * PI * frequency * index / sampleRate)).toInt().toShort()
        }
    }

    private fun sineRmsDb(amplitude: Double): Double {
        val rms = amplitude / sqrt(2.0)
        return DbLevel.fromAmplitude(rms, 0.0)
    }
}
