package com.lincx.noise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DbLevelTest {
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

    @Test
    fun designCurveIsZeroAtOneKilohertz() {
        assertEquals(0.0, AWeightingFilter.designDb(1_000.0), 0.0001)
    }

    @Test
    fun designCurveMatchesKnownHundredHertz() {
        assertEquals(-19.1, AWeightingFilter.designDb(100.0), 0.05)
    }

    @Test
    fun quietRoomOffsetCentersOnThirty() {
        val decision = DbLevel.decideQuietRoomCalibration(2.0, 42.0)
        assertEquals(DbLevel.CalibrationKind.APPLIED, decision.kind)
        assertEquals(-12.0, decision.offsetDb!!, 0.0001)
    }

    @Test
    fun quietRoomRejectsShortOrDeadOrExtreme() {
        assertEquals(
            DbLevel.CalibrationKind.TOO_SHORT,
            DbLevel.decideQuietRoomCalibration(1.5, 42.0).kind,
        )
        assertNull(DbLevel.decideQuietRoomCalibration(1.5, 42.0).offsetDb)
        assertEquals(
            DbLevel.CalibrationKind.SIGNAL_TOO_LOW,
            DbLevel.decideQuietRoomCalibration(2.0, 4.9).kind,
        )
        assertEquals(
            DbLevel.CalibrationKind.OFFSET_OUT_OF_RANGE,
            DbLevel.decideQuietRoomCalibration(2.0, 91.0).kind,
        )
        assertNull(DbLevel.decideQuietRoomCalibration(2.0, 91.0).offsetDb)
    }
}
