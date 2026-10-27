package com.lincx.noise

import org.junit.Assert.assertEquals
import org.junit.Test

class DbLevelTest {
    @Test
    fun silenceIsZero() {
        val window = DbLevel.measure(ShortArray(128), 128)
        assertEquals(0.0, window.instantDb, 0.0)
        assertEquals(0.0, window.peakDb, 0.0)
    }

    @Test
    fun emptyCountIsZero() {
        val window = DbLevel.measure(shortArrayOf(1000), 0)
        assertEquals(0.0, window.instantDb, 0.0)
    }

    @Test
    fun fullScaleConstantIsAboutNinety() {
        val samples = ShortArray(1000) { 32767 }
        val window = DbLevel.measure(samples, samples.size)
        assertEquals(90.0, window.instantDb, 0.02)
        assertEquals(90.0, window.peakDb, 0.02)
    }

    @Test
    fun negativeFullScaleUsesAbsolutePeak() {
        val samples = ShortArray(200) { -32768 }
        val window = DbLevel.measure(samples, samples.size)
        assertEquals(90.0, window.peakDb, 0.02)
        assertEquals(90.0, window.instantDb, 0.02)
    }

    @Test
    fun halfAmplitudeIsAboutSixDbDown() {
        val db = DbLevel.amplitudeToReferenceDb(16384.0)
        assertEquals(84.0, db, 0.05)
    }

    @Test
    fun adviceBands() {
        assertEquals(DbLevel.Advice.QUIET, DbLevel.advice(0.0))
        assertEquals(DbLevel.Advice.QUIET, DbLevel.advice(44.9))
        assertEquals(DbLevel.Advice.LOUDISH, DbLevel.advice(45.0))
        assertEquals(DbLevel.Advice.LOUDISH, DbLevel.advice(69.9))
        assertEquals(DbLevel.Advice.VERY_LOUD, DbLevel.advice(70.0))
        assertEquals(DbLevel.Advice.VERY_LOUD, DbLevel.advice(120.0))
    }
}
