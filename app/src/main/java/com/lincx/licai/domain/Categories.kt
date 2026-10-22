package com.lincx.licai.domain

enum class TxType {
    EXPENSE,
    INCOME,
}

data class Category(
    val key: String,
    val label: String,
    val type: TxType,
)

object Categories {
    val expenses = listOf(
        Category("food", "餐饮", TxType.EXPENSE),
        Category("transport", "交通", TxType.EXPENSE),
        Category("shopping", "购物", TxType.EXPENSE),
        Category("housing", "居住", TxType.EXPENSE),
        Category("daily", "日用", TxType.EXPENSE),
        Category("fun", "娱乐", TxType.EXPENSE),
        Category("medical", "医疗", TxType.EXPENSE),
        Category("edu", "教育", TxType.EXPENSE),
        Category("social", "人情", TxType.EXPENSE),
        Category("other_exp", "其他支出", TxType.EXPENSE),
    )

    val incomes = listOf(
        Category("salary", "工资", TxType.INCOME),
        Category("bonus", "奖金", TxType.INCOME),
        Category("side", "兼职", TxType.INCOME),
        Category("invest_income", "理财收益", TxType.INCOME),
        Category("other_inc", "其他收入", TxType.INCOME),
    )

    fun all(): List<Category> = expenses + incomes

    fun forType(type: TxType): List<Category> = if (type == TxType.EXPENSE) expenses else incomes

    fun label(key: String): String = of(key)?.label ?: "未分类"

    fun of(key: String): Category? = all().firstOrNull { it.key == key }
}

val assetTemplate: List<Pair<String, Double>> = listOf(
    "现金及货币" to 20.0,
    "债券固收" to 30.0,
    "股票基金" to 40.0,
    "黄金商品" to 5.0,
    "其他" to 5.0,
)
