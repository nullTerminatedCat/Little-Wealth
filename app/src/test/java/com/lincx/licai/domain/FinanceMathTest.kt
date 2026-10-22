package com.lincx.licai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FinanceMathTest {
    @Test
    fun parseYuanKeepsTwoDecimalsAndRejectsOverflow() {
        assertEquals(12345L, parseYuanToCents("123.45"))
        assertEquals(100L, parseYuanToCents("¥1"))
        assertEquals(100050L, parseYuanToCents("1,000.50"))
        assertNull(parseYuanToCents("1.234"))
        assertNull(parseYuanToCents("-1"))
        assertNull(parseYuanToCents("1000000001"))
    }

    @Test
    fun formatYuanUsesChinaGrouping() {
        assertEquals("¥1,234.50", formatYuan(123450))
        assertEquals("-¥0.08", formatYuan(-8))
    }

    @Test
    fun driftUsesFivePointThreshold() {
        assertEquals(Drift.OnTarget, driftOf(20.0, 24.9).drift)
        assertEquals(Drift.High, driftOf(20.0, 25.0).drift)
        assertEquals(Drift.Low, driftOf(40.0, 34.0).drift)
        assertNull(presentDrift(0))
    }

    @Test
    fun estimateRoundsPartialMonthsUp() {
        val today = LocalDate.of(2026, 10, 22)
        val estimate = estimateCompletion(
            targetCents = 3100,
            savedCents = 0,
            monthlyPlanCents = 3000,
            paceCentsPerMonth = null,
            deadline = LocalDate.of(2026, 11, 1),
            today = today,
        )
        assertEquals(EstimateStatus.Dated, estimate.status)
        assertEquals("预计 2026年12月完成", estimate.headline)
        assertEquals(true, estimate.detail.contains("晚于目标日"))
    }

    @Test
    fun estimateDoneTooFarAndNeedPlan() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals(
            EstimateStatus.Done,
            estimateCompletion(100, 100, 0, null, null, today).status,
        )
        assertEquals(
            EstimateStatus.TooFar,
            estimateCompletion(601, 0, 1, null, null, today).status,
        )
        assertEquals(
            EstimateStatus.NeedPlan,
            estimateCompletion(100, 0, 0, null, today.minusDays(1), today).status,
        )
    }

    @Test
    fun paceNeedsTwoWeeksAndPositiveNet() {
        val today = LocalDate.of(2026, 10, 22)
        assertNull(paceCentsPerMonth(3000, today.minusDays(13), today))
        assertEquals(3000L, paceCentsPerMonth(3000, today.minusDays(30), today))
        assertNull(paceCentsPerMonth(0, today.minusDays(30), today))
    }

    @Test
    fun breakdownAndTargetSum() {
        val shares = categoryBreakdown(mapOf("food" to 75, "unknown" to 25))
        assertEquals("餐饮", shares[0].label)
        assertEquals("未分类", shares[1].label)
        assertEquals("目标比例合计 90.0%，还差 10.0%", targetSumWarning(90.0))
        assertNull(targetSumWarning(100.0))
    }

    private fun presentDrift(total: Long): DriftResult? {
        return if (total > 0) driftOf(20.0, actualPercent(20, total)) else null
    }
}
