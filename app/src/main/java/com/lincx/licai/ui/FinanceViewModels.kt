package com.lincx.licai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lincx.licai.data.AssetEntity
import com.lincx.licai.data.DepositPace
import com.lincx.licai.data.FinanceRepository
import com.lincx.licai.data.GoalDepositEntity
import com.lincx.licai.data.GoalWithSaved
import com.lincx.licai.data.TransactionEntity
import com.lincx.licai.domain.Categories
import com.lincx.licai.domain.CategoryShare
import com.lincx.licai.domain.CompletionEstimate
import com.lincx.licai.domain.Drift
import com.lincx.licai.domain.DriftResult
import com.lincx.licai.domain.TxType
import com.lincx.licai.domain.actualPercent
import com.lincx.licai.domain.categoryBreakdown
import com.lincx.licai.domain.driftOf
import com.lincx.licai.domain.estimateCompletion
import com.lincx.licai.domain.formatDayTitle
import com.lincx.licai.domain.formatMonth
import com.lincx.licai.domain.monthRange
import com.lincx.licai.domain.paceCentsPerMonth
import com.lincx.licai.domain.targetSumWarning
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class TypeFilter { ALL, EXPENSE, INCOME }

data class TxItem(
    val id: Long,
    val type: TxType,
    val categoryKey: String,
    val categoryLabel: String,
    val note: String,
    val amountCents: Long,
    val occurredOn: LocalDate,
    val createdAt: Long,
)

data class DayGroup(val title: String, val items: List<TxItem>)

data class GoalCardModel(
    val id: Long,
    val name: String,
    val savedCents: Long,
    val targetCents: Long,
    val fraction: Float,
    val headline: String,
    val detail: String,
    val done: Boolean,
)

data class AssetRowModel(
    val id: Long,
    val name: String,
    val note: String,
    val targetPercent: Double,
    val actualPercent: Double,
    val actualCents: Long,
    val sortOrder: Int,
    val drift: DriftResult?,
)

data class DepositItem(
    val id: Long,
    val amountCents: Long,
    val note: String,
    val dayTitle: String,
)

sealed interface HomeUi {
    data object Loading : HomeUi
    data class Ready(val data: HomeReady) : HomeUi
}

data class HomeReady(
    val monthLabel: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val balanceCents: Long,
    val topExpenseLabel: String?,
    val topExpensePercent: Int,
    val goals: List<GoalCardModel>,
    val assets: List<AssetRowModel>,
    val assetTotalCents: Long,
    val driftCount: Int,
)

sealed interface LedgerUi {
    data object Loading : LedgerUi
    data class Ready(val data: LedgerReady) : LedgerUi
}

data class LedgerReady(
    val monthLabel: String,
    val canGoNext: Boolean,
    val incomeCents: Long,
    val expenseCents: Long,
    val balanceCents: Long,
    val typeFilter: TypeFilter,
    val categoryKey: String?,
    val breakdown: List<CategoryShare>,
    val breakdownCaption: String,
    val groups: List<DayGroup>,
    val hasAny: Boolean,
)

sealed interface GoalsUi {
    data object Loading : GoalsUi
    data class Ready(val cards: List<GoalCardModel>) : GoalsUi
}

sealed interface GoalDetailUi {
    data object Loading : GoalDetailUi
    data object Missing : GoalDetailUi
    data class Ready(val data: GoalDetailReady) : GoalDetailUi
}

data class GoalDetailReady(
    val id: Long,
    val name: String,
    val note: String,
    val savedCents: Long,
    val targetCents: Long,
    val monthlyPlanCents: Long,
    val deadline: LocalDate?,
    val fraction: Float,
    val estimate: CompletionEstimate,
    val createdAt: Long,
    val deposits: List<DepositItem>,
)

sealed interface AssetsUi {
    data object Loading : AssetsUi
    data class Ready(val data: AssetsReady) : AssetsUi
}

data class AssetsReady(
    val items: List<AssetRowModel>,
    val totalCents: Long,
    val driftCount: Int,
    val targetWarning: String?,
)

private fun toTxType(filter: TypeFilter): TxType? = when (filter) {
    TypeFilter.ALL -> null
    TypeFilter.EXPENSE -> TxType.EXPENSE
    TypeFilter.INCOME -> TxType.INCOME
}

internal fun presentHome(
    month: YearMonth,
    txs: List<TransactionEntity>,
    goals: List<GoalWithSaved>,
    pace: List<DepositPace>,
    assets: List<AssetEntity>,
    today: LocalDate,
): HomeReady {
    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val expenseByKey = txs.filter { it.type == TxType.EXPENSE }
        .groupBy { it.categoryKey }
        .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
    val top = categoryBreakdown(expenseByKey).firstOrNull()
    val paceById = pace.associateBy { it.goalId }
    val assetRows = presentAssetRows(assets)
    return HomeReady(
        monthLabel = formatMonth(month),
        incomeCents = income,
        expenseCents = expense,
        balanceCents = income - expense,
        topExpenseLabel = top?.label,
        topExpensePercent = ((top?.fraction ?: 0f) * 100).toInt(),
        goals = goals.map { goalCard(it, paceById, today) },
        assets = assetRows,
        assetTotalCents = assets.sumOf { it.actualCents },
        driftCount = assetRows.count { it.drift != null && it.drift.drift != Drift.OnTarget },
    )
}

internal fun presentLedger(
    month: YearMonth,
    type: TypeFilter,
    categoryKey: String?,
    txs: List<TransactionEntity>,
    today: LocalDate,
): LedgerReady {
    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val selectedType = toTxType(type)
    val filtered = txs.filter { tx ->
        (selectedType == null || tx.type == selectedType) &&
            (categoryKey == null || tx.categoryKey == categoryKey)
    }
    val breakdownType = if (type == TypeFilter.INCOME) TxType.INCOME else TxType.EXPENSE
    val amounts = txs.filter { it.type == breakdownType }
        .groupBy { it.categoryKey }
        .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
    val groups = filtered.groupBy { it.occurredOn }.map { (day, rows) ->
        val date = LocalDate.ofEpochDay(day)
        DayGroup(formatDayTitle(date, today), rows.map { it.toItem() })
    }
    return LedgerReady(
        monthLabel = formatMonth(month),
        canGoNext = month.isBefore(YearMonth.from(today)),
        incomeCents = income,
        expenseCents = expense,
        balanceCents = income - expense,
        typeFilter = type,
        categoryKey = categoryKey,
        breakdown = categoryBreakdown(amounts),
        breakdownCaption = if (breakdownType == TxType.INCOME) "收入分类占比" else "支出分类占比",
        groups = groups,
        hasAny = txs.isNotEmpty(),
    )
}

internal fun presentDetail(
    goal: GoalWithSaved,
    deposits: List<GoalDepositEntity>,
    today: LocalDate,
): GoalDetailReady {
    val first = deposits.minOfOrNull { it.occurredOn }?.let { LocalDate.ofEpochDay(it) }
    val net = deposits.sumOf { it.amountCents }
    val pace = paceCentsPerMonth(net, first, today)
    val deadline = goal.goal.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) }
    val estimate = estimateCompletion(
        goal.goal.targetCents,
        goal.savedCents,
        goal.goal.monthlyPlanCents,
        pace,
        deadline,
        today,
    )
    return GoalDetailReady(
        id = goal.goal.id,
        name = goal.goal.name,
        note = goal.goal.note,
        savedCents = goal.savedCents,
        targetCents = goal.goal.targetCents,
        monthlyPlanCents = goal.goal.monthlyPlanCents,
        deadline = deadline,
        fraction = fraction(goal.savedCents, goal.goal.targetCents),
        estimate = estimate,
        createdAt = goal.goal.createdAt,
        deposits = deposits.map { deposit ->
            DepositItem(
                id = deposit.id,
                amountCents = deposit.amountCents,
                note = deposit.note,
                dayTitle = formatDayTitle(LocalDate.ofEpochDay(deposit.occurredOn), today),
            )
        },
    )
}

internal fun presentAssets(rows: List<AssetEntity>): AssetsReady {
    val mapped = presentAssetRows(rows)
    return AssetsReady(
        items = mapped,
        totalCents = rows.sumOf { it.actualCents },
        driftCount = mapped.count { it.drift != null && it.drift.drift != Drift.OnTarget },
        targetWarning = targetSumWarning(rows.sumOf { it.targetPercent }),
    )
}

private fun presentAssetRows(rows: List<AssetEntity>): List<AssetRowModel> {
    val total = rows.sumOf { it.actualCents }
    return rows.map { row ->
        val actual = actualPercent(row.actualCents, total)
        AssetRowModel(
            id = row.id,
            name = row.name,
            note = row.note,
            targetPercent = row.targetPercent,
            actualPercent = actual,
            actualCents = row.actualCents,
            sortOrder = row.sortOrder,
            drift = if (total > 0) driftOf(row.targetPercent, actual) else null,
        )
    }
}

private fun goalCard(
    item: GoalWithSaved,
    paceById: Map<Long, DepositPace>,
    today: LocalDate,
): GoalCardModel {
    val paceRow = paceById[item.goal.id]
    val pace = paceRow?.let {
        paceCentsPerMonth(it.netCents, LocalDate.ofEpochDay(it.firstDay), today)
    }
    val deadline = item.goal.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) }
    val estimate = estimateCompletion(
        item.goal.targetCents,
        item.savedCents,
        item.goal.monthlyPlanCents,
        pace,
        deadline,
        today,
    )
    return GoalCardModel(
        id = item.goal.id,
        name = item.goal.name,
        savedCents = item.savedCents,
        targetCents = item.goal.targetCents,
        fraction = fraction(item.savedCents, item.goal.targetCents),
        headline = estimate.headline,
        detail = estimate.detail,
        done = item.savedCents >= item.goal.targetCents && item.goal.targetCents > 0,
    )
}

private fun fraction(saved: Long, target: Long): Float {
    if (target <= 0) return 0f
    return (saved.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

private fun TransactionEntity.toItem(): TxItem = TxItem(
    id = id,
    type = type,
    categoryKey = categoryKey,
    categoryLabel = Categories.label(categoryKey),
    note = note,
    amountCents = amountCents,
    occurredOn = LocalDate.ofEpochDay(occurredOn),
    createdAt = createdAt,
)

private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

class HomeViewModel(repo: FinanceRepository) : ViewModel() {
    private val today: LocalDate = LocalDate.now()
    private val month: YearMonth = YearMonth.from(today)
    private val range = monthRange(month)

    val ui: StateFlow<HomeUi> = combine(
        repo.observeTransactions(range.first, range.second),
        repo.observeGoals(),
        repo.observeDepositPace(),
        repo.observeAssets(),
    ) { txs, goals, pace, assets ->
        HomeUi.Ready(presentHome(month, txs, goals, pace, assets, today))
    }.stateIn(viewModelScope, whileSubscribed, HomeUi.Loading)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(private val repo: FinanceRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val typeFilter = MutableStateFlow(TypeFilter.ALL)
    private val categoryKey = MutableStateFlow<String?>(null)

    val ui: StateFlow<LedgerUi> = combine(month, typeFilter, categoryKey) { month, type, key ->
        Triple(month, type, key)
    }.flatMapLatest { (month, type, key) ->
        val range = monthRange(month)
        repo.observeTransactions(range.first, range.second).map { txs ->
            LedgerUi.Ready(presentLedger(month, type, key, txs, LocalDate.now()))
        }
    }.stateIn(viewModelScope, whileSubscribed, LedgerUi.Loading)

    fun shiftMonth(delta: Int) {
        val next = month.value.plusMonths(delta.toLong())
        if (next.isAfter(YearMonth.now())) return
        month.value = next
    }

    fun setType(type: TypeFilter) {
        if (typeFilter.value != type) categoryKey.value = null
        typeFilter.value = type
    }

    fun setCategory(key: String?) {
        categoryKey.value = if (categoryKey.value == key) null else key
        val picked = key?.let { Categories.of(it) }
        if (picked != null && categoryKey.value != null && typeFilter.value == TypeFilter.ALL) {
            typeFilter.value = if (picked.type == TxType.EXPENSE) TypeFilter.EXPENSE else TypeFilter.INCOME
        }
    }

    fun save(item: TxItem?, type: TxType, amountCents: Long, category: String, note: String, day: LocalDate) {
        viewModelScope.launch {
            repo.saveTransaction(
                id = item?.id ?: 0L,
                type = type,
                amountCents = amountCents,
                categoryKey = category,
                note = note,
                occurredOn = day,
                createdAt = item?.createdAt ?: System.currentTimeMillis(),
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteTransaction(id) }
    }
}

class GoalsViewModel(private val repo: FinanceRepository) : ViewModel() {
    val ui: StateFlow<GoalsUi> = combine(repo.observeGoals(), repo.observeDepositPace()) { goals, pace ->
        val today = LocalDate.now()
        val paceById = pace.associateBy { it.goalId }
        GoalsUi.Ready(goals.map { goalCard(it, paceById, today) })
    }.stateIn(viewModelScope, whileSubscribed, GoalsUi.Loading)

    fun create(
        name: String,
        targetCents: Long,
        monthlyPlanCents: Long,
        deadline: LocalDate?,
        note: String,
        initialCents: Long,
    ) {
        viewModelScope.launch {
            repo.createGoal(name, targetCents, monthlyPlanCents, deadline, note, initialCents, LocalDate.now())
        }
    }
}

class GoalDetailViewModel(
    private val repo: FinanceRepository,
    private val goalId: Long,
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    val ui: StateFlow<GoalDetailUi> = combine(
        repo.observeGoal(goalId),
        repo.observeDeposits(goalId),
    ) { goal, deposits ->
        if (goal == null) {
            GoalDetailUi.Missing
        } else {
            GoalDetailUi.Ready(presentDetail(goal, deposits, LocalDate.now()))
        }
    }.stateIn(viewModelScope, whileSubscribed, GoalDetailUi.Loading)

    fun save(
        name: String,
        targetCents: Long,
        monthlyPlanCents: Long,
        deadline: LocalDate?,
        note: String,
        createdAt: Long,
    ) {
        viewModelScope.launch {
            repo.updateGoal(goalId, name, targetCents, monthlyPlanCents, deadline, note, createdAt)
        }
    }

    fun addDeposit(amountCents: Long, day: LocalDate, note: String) {
        viewModelScope.launch {
            try {
                repo.addDeposit(goalId, amountCents, day, note)
            } catch (error: IllegalStateException) {
                _messages.emit(error.message ?: "取出金额不能超过已存金额")
            }
        }
    }

    fun deleteDeposit(id: Long) {
        viewModelScope.launch { repo.deleteDeposit(id) }
    }

    fun deleteGoal() {
        viewModelScope.launch { repo.deleteGoal(goalId) }
    }
}

class AssetsViewModel(private val repo: FinanceRepository) : ViewModel() {
    val ui: StateFlow<AssetsUi> = repo.observeAssets()
        .map { AssetsUi.Ready(presentAssets(it)) }
        .stateIn(viewModelScope, whileSubscribed, AssetsUi.Loading)

    fun save(id: Long, name: String, targetPercent: Double, actualCents: Long, note: String, sortOrder: Int?) {
        viewModelScope.launch {
            repo.saveAsset(id, name, targetPercent, actualCents, note, sortOrder)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteAsset(id) }
    }

    fun applyTemplate() {
        viewModelScope.launch { repo.applyTemplateIfEmpty() }
    }
}

class RepoViewModelFactory(
    private val repo: FinanceRepository,
    private val goalId: Long? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val model = when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repo)
            modelClass.isAssignableFrom(LedgerViewModel::class.java) -> LedgerViewModel(repo)
            modelClass.isAssignableFrom(GoalsViewModel::class.java) -> GoalsViewModel(repo)
            modelClass.isAssignableFrom(AssetsViewModel::class.java) -> AssetsViewModel(repo)
            modelClass.isAssignableFrom(GoalDetailViewModel::class.java) ->
                GoalDetailViewModel(repo, checkNotNull(goalId))
            else -> error("未知 ViewModel：${modelClass.name}")
        }
        return model as T
    }
}
