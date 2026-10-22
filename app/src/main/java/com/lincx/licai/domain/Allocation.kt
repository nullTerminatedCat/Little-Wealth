package com.lincx.licai.domain

import kotlin.math.abs
import kotlin.math.roundToInt

const val DRIFT_THRESHOLD = 5.0

enum class Drift {
    OnTarget,
    High,
    Low,
}

data class DriftResult(
    val drift: Drift,
    val deltaPoints: Double,
)

fun actualPercent(amountCents: Long, totalCents: Long): Double {
    if (totalCents <= 0) return 0.0
    return (amountCents * 100.0) / totalCents
}

fun driftOf(targetPercent: Double, actualPercent: Double, threshold: Double = DRIFT_THRESHOLD): DriftResult {
    val delta = actualPercent - targetPercent
    val drift = when {
        abs(delta) < threshold -> Drift.OnTarget
        delta > 0.0 -> Drift.High
        else -> Drift.Low
    }
    return DriftResult(drift, delta)
}

fun targetSumWarning(sum: Double): String? {
    if (sum <= 0.0) return null
    val rounded = (sum * 10).roundToInt() / 10.0
    if (abs(rounded - 100.0) < 0.05) return null
    return if (rounded < 100.0) {
        "目标比例合计 ${formatPercent(rounded)}，还差 ${formatPercent(100.0 - rounded)}"
    } else {
        "目标比例合计 ${formatPercent(rounded)}，超出 ${formatPercent(rounded - 100.0)}"
    }
}

fun driftLabel(result: DriftResult): String = when (result.drift) {
    Drift.OnTarget -> "正常"
    Drift.High -> "偏高 ${formatPercent(abs(result.deltaPoints))}"
    Drift.Low -> "偏低 ${formatPercent(abs(result.deltaPoints))}"
}
