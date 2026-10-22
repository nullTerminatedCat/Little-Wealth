package com.lincx.licai.domain

data class CategoryShare(
    val key: String,
    val label: String,
    val cents: Long,
    val fraction: Float,
)

fun categoryBreakdown(amountsByKey: Map<String, Long>): List<CategoryShare> {
    val total = amountsByKey.values.sum()
    return amountsByKey
        .filterValues { it > 0 }
        .map { (key, cents) ->
            CategoryShare(
                key = key,
                label = Categories.label(key),
                cents = cents,
                fraction = if (total == 0L) 0f else cents.toFloat() / total,
            )
        }
        .sortedByDescending { it.cents }
}
