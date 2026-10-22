package com.lincx.licai.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lincx.licai.domain.Categories
import com.lincx.licai.domain.TxType
import com.lincx.licai.domain.centsToInput
import com.lincx.licai.domain.formatDate
import com.lincx.licai.domain.formatPercent
import com.lincx.licai.domain.formatYuan
import com.lincx.licai.domain.parseYuanToCents
import com.lincx.licai.ui.LedgerUi
import com.lincx.licai.ui.LedgerViewModel
import com.lincx.licai.ui.RepoViewModelFactory
import com.lincx.licai.ui.TxItem
import com.lincx.licai.ui.TypeFilter
import com.lincx.licai.ui.components.ConfirmDialog
import com.lincx.licai.ui.components.DateButton
import com.lincx.licai.ui.components.LicaiDatePicker
import com.lincx.licai.ui.components.PlainField
import com.lincx.licai.ui.components.ShareBar
import com.lincx.licai.ui.components.SoftCard
import com.lincx.licai.ui.components.YuanField
import com.lincx.licai.ui.repo
import com.lincx.licai.ui.theme.LocalFinanceColors
import java.time.LocalDate

private sealed interface EditorMode {
    data object Create : EditorMode
    data class Edit(val item: TxItem) : EditorMode
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerScreen(vm: LedgerViewModel = viewModel(factory = RepoViewModelFactory(repo()))) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<EditorMode?>(null) }
    var pendingDelete by remember { mutableStateOf<TxItem?>(null) }
    val state = (ui as? LedgerUi.Ready)?.data
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = EditorMode.Create }) {
                Icon(Icons.Outlined.Add, contentDescription = "记一笔")
            }
        },
    ) { padding ->
        when (ui) {
            LedgerUi.Loading -> Column(Modifier.padding(padding).padding(24.dp)) { CircularProgressIndicator() }
            is LedgerUi.Ready -> LedgerBody(
                state = state!!,
                padding = padding,
                onShift = vm::shiftMonth,
                onType = vm::setType,
                onCategory = vm::setCategory,
                onEdit = { editor = EditorMode.Edit(it) },
            )
        }
    }
    val mode = editor
    if (mode != null) {
        TransactionSheet(
            mode = mode,
            onDismiss = { editor = null },
            onSave = { item, type, cents, category, note, day ->
                vm.save(item, type, cents, category, note, day)
                editor = null
            },
            onAskDelete = {
                if (mode is EditorMode.Edit) pendingDelete = mode.item
                editor = null
            },
        )
    }
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "删除这笔账？",
            body = "删除后不能恢复。账本只存在这台手机上。",
            confirm = "删除",
            onConfirm = {
                vm.delete(item.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerBody(
    state: com.lincx.licai.ui.LedgerReady,
    padding: PaddingValues,
    onShift: (Int) -> Unit,
    onType: (TypeFilter) -> Unit,
    onCategory: (String?) -> Unit,
    onEdit: (TxItem) -> Unit,
) {
    val colors = LocalFinanceColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("记账", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShift(-1) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "上个月")
                }
                Text(state.monthLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onShift(1) }, enabled = state.canGoNext) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "下个月")
                }
            }
        }
        item {
            SoftCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Summary("收入", state.incomeCents, colors.income)
                    Summary("支出", state.expenseCents, colors.expense)
                    Summary("结余", state.balanceCents, MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TypeFilter.ALL to "全部", TypeFilter.EXPENSE to "支出", TypeFilter.INCOME to "收入").forEach { (type, label) ->
                    FilterChip(selected = state.typeFilter == type, onClick = { onType(type) }, label = { Text(label) })
                }
            }
        }
        if (state.breakdown.isNotEmpty()) {
            item {
                Text(state.breakdownCaption, style = MaterialTheme.typography.titleSmall)
            }
            items(state.breakdown, key = { "share-${it.key}" }) { share ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FilterChip(
                            selected = state.categoryKey == share.key,
                            onClick = { onCategory(share.key) },
                            label = { Text(share.label) },
                        )
                        Text("${formatYuan(share.cents)} · ${formatPercent(share.fraction * 100.0)}")
                    }
                    ShareBar(
                        share.fraction,
                        if (state.typeFilter == TypeFilter.INCOME) colors.income else colors.expense,
                    )
                }
            }
        }
        if (!state.hasAny) {
            item {
                Text("这个月还没有账", style = MaterialTheme.typography.titleMedium)
                Text(
                    "先记一笔今天的花销或收入。金额按人民币保存，精确到分。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.groups.isEmpty()) {
            item { Text("这个月还没有对应记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            state.groups.forEach { group ->
                item { Text(group.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                items(group.items, key = { it.id }) { item ->
                    SoftCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.categoryLabel, style = MaterialTheme.typography.titleSmall)
                                if (item.note.isNotBlank()) {
                                    Text(item.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                (if (item.type == TxType.EXPENSE) "-" else "+") + formatYuan(item.amountCents),
                                color = if (item.type == TxType.EXPENSE) colors.expense else colors.income,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(onClick = { onEdit(item) }) { Text("编辑这笔账") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(label: String, cents: Long, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        Text(formatYuan(cents), color = color, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TransactionSheet(
    mode: EditorMode,
    onDismiss: () -> Unit,
    onSave: (TxItem?, TxType, Long, String, String, LocalDate) -> Unit,
    onAskDelete: () -> Unit,
) {
    val editing = (mode as? EditorMode.Edit)?.item
    var type by remember { mutableStateOf(editing?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(editing?.let { centsToInput(it.amountCents) } ?: "") }
    var category by remember {
        mutableStateOf(editing?.categoryKey ?: Categories.forType(type).first().key)
    }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var day by remember { mutableStateOf(editing?.occurredOn ?: LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val parsed = parseYuanToCents(amount)
    val amountError = when {
        amount.isBlank() -> null
        parsed == null -> "请输入最多两位小数的金额"
        parsed == 0L -> "金额要大于 0"
        else -> null
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (editing == null) "记一笔" else "编辑这笔账", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == TxType.EXPENSE, onClick = {
                    type = TxType.EXPENSE
                    if (Categories.of(category)?.type != TxType.EXPENSE) category = Categories.expenses.first().key
                }, label = { Text("支出") })
                FilterChip(selected = type == TxType.INCOME, onClick = {
                    type = TxType.INCOME
                    if (Categories.of(category)?.type != TxType.INCOME) category = Categories.incomes.first().key
                }, label = { Text("收入") })
            }
            YuanField(amount, { amount = it }, "金额", amountError)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Categories.forType(type).forEach { item ->
                    FilterChip(
                        selected = category == item.key,
                        onClick = { category = item.key },
                        label = { Text(item.label) },
                    )
                }
            }
            PlainField(note, { note = it }, "备注，可不填")
            DateButton(formatDate(day)) { picking = true }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = parsed != null && parsed > 0,
                    onClick = { onSave(editing, type, parsed!!, category, note, day) },
                ) { Text("保存") }
                if (editing != null) {
                    TextButton(onClick = onAskDelete) { Text("删除", color = LocalFinanceColors.current.expense) }
                }
            }
        }
    }
    if (picking) {
        LicaiDatePicker(day, onDismiss = { picking = false }) {
            day = it
            picking = false
        }
    }
}
