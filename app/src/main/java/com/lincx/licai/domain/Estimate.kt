package com.lincx.licai.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class EstimateStatus {
    Done,
    Dated,
    NeedPlan,
    TooFar,
}

data class CompletionEstimate(
    val status: EstimateStatus,
    val headline: String,
    val detail: String,
)

fun estimateCompletion(
    targetCents: Long,
    savedCents: Long,
    monthlyPlanCents: Long,
    paceCentsPerMonth: Long?,
    deadline: LocalDate?,
    today: LocalDate,
): CompletionEstimate {
    val remaining = targetCents - savedCents
    if (remaining <= 0) {
        return CompletionEstimate(
            EstimateStatus.Done,
            "已达成目标",
            "已存 ${formatYuan(savedCents)}，目标 ${formatYuan(targetCents)}",
        )
    }
    val usingPlan = monthlyPlanCents > 0
    val rate = when {
        usingPlan -> monthlyPlanCents
        paceCentsPerMonth != null && paceCentsPerMonth > 0 -> paceCentsPerMonth
        else -> {
            val note = deadlineNote(deadline, today) ?: "填写每月计划，或连续记录两周存入"
            return CompletionEstimate(EstimateStatus.NeedPlan, "还没法估算完成时间", note)
        }
    }
    val months = ceil(remaining.toDouble() / rate.toDouble()).toLong().coerceAtLeast(1)
    if (months > 600) {
        return CompletionEstimate(
            EstimateStatus.TooFar,
            "完成时间会超过 50 年",
            "可以把每月计划调高一些",
        )
    }
    val done = today.plusMonths(months)
    val source = if (usingPlan) "按每月 ${formatYuan(monthlyPlanCents)}" else "按最近的存入速度"
    val paceNote = "$source，约 $months 个月"
    val late = deadline != null && projectedIsLate(done, deadline)
    val deadlineText = when {
        deadline == null -> null
        deadline.isBefore(today) -> "目标日 ${formatDate(deadline)} 已过"
        late -> "晚于目标日 ${formatDate(deadline)}"
        else -> "不晚于目标日 ${formatDate(deadline)}"
    }
    val detail = listOfNotNull(paceNote, deadlineText).joinToString("，")
    return CompletionEstimate(
        EstimateStatus.Dated,
        "预计 ${done.year}年${done.monthValue}月完成",
        detail,
    )
}

fun paceCentsPerMonth(netSavedCents: Long, firstDeposit: LocalDate?, today: LocalDate): Long? {
    if (firstDeposit == null || netSavedCents <= 0) return null
    val days = ChronoUnit.DAYS.between(firstDeposit, today)
    if (days < 14) return null
    return (30 * netSavedCents) / days
}

fun monthRange(month: YearMonth): Pair<Long, Long> {
    return month.atDay(1).toEpochDay() to month.atEndOfMonth().toEpochDay()
}

fun formatDate(date: LocalDate): String {
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}

fun formatMonth(month: YearMonth): String {
    return "${month.year}年${month.monthValue}月"
}

private fun deadlineNote(deadline: LocalDate?, today: LocalDate): String? {
    if (deadline == null) return null
    return if (deadline.isBefore(today)) {
        "目标日 ${formatDate(deadline)} 已过"
    } else {
        "目标日 ${formatDate(deadline)}"
    }
}

private fun projectedIsLate(projected: LocalDate, deadline: LocalDate): Boolean {
    return YearMonth.from(projected).isAfter(YearMonth.from(deadline))
}
