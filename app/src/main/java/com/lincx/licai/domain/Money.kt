package com.lincx.licai.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_CENTS = 100_000_000_000L

fun parseYuanToCents(raw: String): Long? {
    val text = raw.trim()
        .replace("￥", "")
        .replace("¥", "")
        .replace(",", "")
        .replace("，", "")
        .replace(" ", "")
    val normalized = text.removeSuffix(".")
    if (normalized.isEmpty() || normalized == "-") return null
    return try {
        val value = BigDecimal(normalized)
        if (value < BigDecimal.ZERO || value.stripTrailingZeros().scale() > 2) return null
        val cents = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        if (cents > BigDecimal.valueOf(MAX_CENTS)) return null
        cents.longValueExact()
    } catch (_: RuntimeException) {
        null
    }
}

fun centsToInput(cents: Long, blankIfZero: Boolean = false): String {
    if (cents == 0L && blankIfZero) return ""
    val absolute = kotlin.math.abs(cents)
    val yuan = absolute / 100
    val frac = (absolute % 100).toString().padStart(2, '0')
    val body = if (frac == "00") {
        yuan.toString()
    } else {
        "$yuan.$frac".trimEnd('0').trimEnd('.')
    }
    return if (cents < 0) "-$body" else body
}

fun percentToInput(value: Double): String {
    val asTenths = (value * 10).roundToInt()
    return if (asTenths % 10 == 0) {
        (asTenths / 10).toString()
    } else {
        "${asTenths / 10}.${asTenths % 10}"
    }
}

fun formatYuan(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absolute = kotlin.math.abs(cents)
    val yuan = absolute / 100
    val frac = absolute % 100
    val grouped = String.format(Locale.CHINA, "%,d", yuan)
    return sign + "¥" + grouped + "." + frac.toString().padStart(2, '0')
}

fun formatPercent(value: Double): String {
    return String.format(Locale.CHINA, "%.1f%%", value)
}

fun sanitizeAmountInput(next: String): String? {
    if (next.isEmpty()) return ""
    if (next.length > 16) return null
    if (next.any { it != '.' && !it.isDigit() }) return null
    if (next.count { it == '.' } > 1) return null
    val dot = next.indexOf('.')
    if (dot >= 0 && next.length - dot - 1 > 2) return null
    return next
}

fun sanitizePercentInput(next: String): String? {
    if (next.isEmpty()) return ""
    if (next.length > 6) return null
    if (next.any { it != '.' && !it.isDigit() }) return null
    if (next.count { it == '.' } > 1) return null
    val dot = next.indexOf('.')
    if (dot >= 0 && next.length - dot - 1 > 1) return null
    return next
}

fun parsePercent(raw: String): Double? {
    val text = raw.trim().removeSuffix("%").replace(" ", "").removeSuffix(".")
    if (text.isEmpty()) return null
    val value = text.toDoubleOrNull() ?: return null
    if (value < 0.0 || value > 100.0) return null
    return (value * 10).roundToInt() / 10.0
}
