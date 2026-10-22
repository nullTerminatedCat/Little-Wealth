package com.lincx.licai.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

fun formatDayTitle(day: LocalDate, today: LocalDate): String {
    val body = day.format(dayFormatter)
    return when (day) {
        today -> "今天 · $body"
        today.minusDays(1) -> "昨天 · $body"
        else -> body
    }
}
