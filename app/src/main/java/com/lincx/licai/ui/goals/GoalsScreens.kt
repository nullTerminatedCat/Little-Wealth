package com.lincx.licai.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lincx.licai.domain.centsToInput
import com.lincx.licai.domain.formatDate
import com.lincx.licai.domain.formatYuan
import com.lincx.licai.domain.parseYuanToCents
import com.lincx.licai.ui.GoalDetailUi
import com.lincx.licai.ui.GoalDetailViewModel
import com.lincx.licai.ui.GoalsUi
import com.lincx.licai.ui.GoalsViewModel
import com.lincx.licai.ui.RepoViewModelFactory
import com.lincx.licai.ui.components.ConfirmDialog
import com.lincx.licai.ui.components.DateButton
import com.lincx.licai.ui.components.EmptyHint
import com.lincx.licai.ui.components.GoalProgress
import com.lincx.licai.ui.components.LicaiDatePicker
import com.lincx.licai.ui.components.MoneyText
import com.lincx.licai.ui.components.PlainField
import com.lincx.licai.ui.components.SoftCard
import com.lincx.licai.ui.components.YuanField
import com.lincx.licai.ui.repo
import com.lincx.licai.ui.theme.LocalFinanceColors
import java.time.LocalDate

private data class GoalSeed(
    val name: String,
    val targetCents: Long,
    val monthlyPlanCents: Long,
    val deadline: LocalDate?,
    val note: String,
    val createdAt: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(onOpenGoal: (Long) -> Unit) {
    val vm: GoalsViewModel = viewModel(factory = RepoViewModelFactory(repo()))
    val ui by vm.ui.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建目标")
            }
        },
    ) { padding ->
        when (val state = ui) {
            GoalsUi.Loading -> Column(Modifier.padding(padding).padding(24.dp)) { CircularProgressIndicator() }
            is GoalsUi.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("存钱目标", style = MaterialTheme.typography.headlineMedium) }
                if (state.cards.isEmpty()) {
                    item {
                        EmptyHint(
                            icon = Icons.Outlined.Flag,
                            title = "给一个目标标上金额",
                            body = "写清要存多少、打算每月存多少。进度靠你手动记入，不会连到银行卡。",
                            action = "新建目标",
                            onAction = { creating = true },
                        )
                    }
                } else {
                    items(state.cards, key = { it.id }) { card ->
                        SoftCard {
                            Text(card.name, style = MaterialTheme.typography.titleMedium)
                            Text(card.headline)
                            Text(card.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            GoalProgress(card.fraction)
                            Text(
                                "已存 ${formatYuan(card.savedCents)} / ${formatYuan(card.targetCents)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { onOpenGoal(card.id) }) { Text("查看") }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        GoalEditorSheet(
            title = "新目标",
            seed = null,
            showInitial = true,
            onDismiss = { creating = false },
            onSave = { name, target, plan, deadline, note, initial, _ ->
                vm.create(name, target, plan, deadline, note, initial)
                creating = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(goalId: Long, onBack: () -> Unit) {
    val vm: GoalDetailViewModel = viewModel(
        key = "goal-$goalId",
        factory = RepoViewModelFactory(repo(), goalId),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf(false) }
    var depositing by remember { mutableStateOf(false) }
    var deleteGoal by remember { mutableStateOf(false) }
    var deleteDeposit by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (ui is GoalDetailUi.Ready) {
                FloatingActionButton(onClick = { depositing = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "记入进度")
                }
            }
        },
    ) { padding ->
        when (val state = ui) {
            GoalDetailUi.Loading -> Column(Modifier.padding(padding).padding(24.dp)) { CircularProgressIndicator() }
            GoalDetailUi.Missing -> Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("这个目标已经不在了。")
                TextButton(onClick = onBack) { Text("返回") }
            }
            is GoalDetailUi.Ready -> {
                val data = state.data
                val colors = LocalFinanceColors.current
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                            }
                            Text(data.name, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    item {
                        SoftCard(color = colors.balance) {
                            Text("已存", color = colors.balanceMuted)
                            MoneyText(data.savedCents, colors.onBalance, MaterialTheme.typography.headlineMedium)
                            Text("目标 ${formatYuan(data.targetCents)}", color = colors.balanceMuted)
                            GoalProgress(data.fraction)
                            if (data.savedCents > data.targetCents) {
                                Text(
                                    "已超出目标 ${formatYuan(data.savedCents - data.targetCents)}",
                                    color = colors.income,
                                )
                            }
                        }
                    }
                    item {
                        SoftCard {
                            Text(data.estimate.headline, style = MaterialTheme.typography.titleMedium)
                            Text(data.estimate.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (data.monthlyPlanCents > 0) "每月计划 ${formatYuan(data.monthlyPlanCents)}" else "还没有每月计划",
                            )
                            Text(
                                data.deadline?.let { "目标日 ${formatDate(it)}" } ?: "没有设目标日期",
                            )
                            if (data.note.isNotBlank()) Text(data.note)
                            TextButton(onClick = { editing = true }) { Text("编辑目标") }
                            TextButton(onClick = { deleteGoal = true }) {
                                Text("删除目标", color = colors.expense)
                            }
                        }
                    }
                    item { Text("进度记录", style = MaterialTheme.typography.titleMedium) }
                    if (data.deposits.isEmpty()) {
                        item {
                            Text(
                                "还没有记入。点右下角，把存进去或取出来的金额写上。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(data.deposits, key = { it.id }) { deposit ->
                            SoftCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(deposit.dayTitle, fontWeight = FontWeight.SemiBold)
                                        if (deposit.note.isNotBlank()) Text(deposit.note)
                                    }
                                    Text(
                                        (if (deposit.amountCents < 0) "-" else "+") + formatYuan(kotlin.math.abs(deposit.amountCents)),
                                        color = if (deposit.amountCents < 0) colors.expense else colors.income,
                                    )
                                }
                                TextButton(onClick = { deleteDeposit = deposit.id }) { Text("删除这条进度") }
                            }
                        }
                    }
                }
                if (editing) {
                    GoalEditorSheet(
                        title = "编辑目标",
                        seed = GoalSeed(data.name, data.targetCents, data.monthlyPlanCents, data.deadline, data.note, data.createdAt),
                        showInitial = false,
                        onDismiss = { editing = false },
                        onSave = { name, target, plan, deadline, note, _, createdAt ->
                            vm.save(name, target, plan, deadline, note, createdAt)
                            editing = false
                        },
                    )
                }
                if (depositing) {
                    DepositSheet(
                        savedCents = data.savedCents,
                        onDismiss = { depositing = false },
                        onSave = { cents, day, note ->
                            vm.addDeposit(cents, day, note)
                            depositing = false
                        },
                    )
                }
            }
        }
    }
    if (deleteGoal) {
        ConfirmDialog(
            title = "删除这个目标？",
            body = "目标和已经记入的进度会一起删掉。",
            confirm = "删除",
            onConfirm = {
                vm.deleteGoal()
                deleteGoal = false
                onBack()
            },
            onDismiss = { deleteGoal = false },
        )
    }
    deleteDeposit?.let { id ->
        ConfirmDialog(
            title = "删除这条进度？",
            body = "已存金额会按这条记录重算。",
            confirm = "删除",
            onConfirm = {
                vm.deleteDeposit(id)
                deleteDeposit = null
            },
            onDismiss = { deleteDeposit = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorSheet(
    title: String,
    seed: GoalSeed?,
    showInitial: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, LocalDate?, String, Long, Long) -> Unit,
) {
    var name by remember { mutableStateOf(seed?.name ?: "") }
    var target by remember { mutableStateOf(seed?.let { centsToInput(it.targetCents) } ?: "") }
    var plan by remember { mutableStateOf(seed?.let { centsToInput(it.monthlyPlanCents, blankIfZero = true) } ?: "") }
    var initial by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(seed?.note ?: "") }
    var deadline by remember { mutableStateOf(seed?.deadline) }
    var picking by remember { mutableStateOf(false) }
    val targetCents = parseYuanToCents(target)
    val planCents = if (plan.isBlank()) 0L else parseYuanToCents(plan)
    val initialCents = if (initial.isBlank()) 0L else parseYuanToCents(initial)
    val nameError = if (name.isNotBlank() || name.isEmpty()) null else "写一个名字"
    val shownNameError = if (name.isBlank() && name.isNotEmpty()) "写一个名字" else if (name.isBlank()) null else null
    val targetError = when {
        target.isBlank() -> null
        targetCents == null -> "金额最多两位小数"
        targetCents == 0L -> "目标金额要大于 0"
        else -> null
    }
    val planError = if (plan.isNotBlank() && planCents == null) "金额最多两位小数" else null
    val initialError = if (initial.isNotBlank() && initialCents == null) "金额最多两位小数" else null
    val canSave = name.isNotBlank() && targetCents != null && targetCents > 0 &&
        planCents != null && initialCents != null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            PlainField(name, { name = it }, "名称，例如应急金", shownNameError ?: nameError)
            YuanField(target, { target = it }, "目标金额", targetError)
            YuanField(plan, { plan = it }, "每月计划，可空", planError)
            if (showInitial) YuanField(initial, { initial = it }, "现在已经有的金额，可空", initialError)
            PlainField(note, { note = it }, "备注，可不填")
            if (deadline == null) {
                DateButton("添加目标日期") { picking = true }
            } else {
                DateButton("目标日 ${formatDate(deadline!!)}") { picking = true }
                TextButton(onClick = { deadline = null }) { Text("清除日期") }
            }
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(name, targetCents!!, planCents!!, deadline, note, initialCents!!, seed?.createdAt ?: 0L)
                },
            ) { Text("保存") }
        }
    }
    if (picking) {
        LicaiDatePicker(deadline ?: LocalDate.now(), onDismiss = { picking = false }) {
            deadline = it
            picking = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositSheet(
    savedCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long, LocalDate, String) -> Unit,
) {
    var withdraw by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val parsed = parseYuanToCents(amount)
    val tooMuch = withdraw && parsed != null && parsed > savedCents
    val error = when {
        amount.isBlank() -> null
        parsed == null -> "金额最多两位小数"
        parsed == 0L -> "金额要大于 0"
        tooMuch -> "不能超过已存 ${formatYuan(savedCents)}"
        else -> null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("记入进度", style = MaterialTheme.typography.titleLarge)
            Text("当前已存 ${formatYuan(savedCents)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !withdraw, onClick = { withdraw = false }, label = { Text("存入") })
                FilterChip(selected = withdraw, onClick = { withdraw = true }, label = { Text("取出") })
            }
            YuanField(amount, { amount = it }, if (withdraw) "取出" else "存入", error)
            PlainField(note, { note = it }, "备注，可不填")
            DateButton(formatDate(day)) { picking = true }
            TextButton(
                enabled = parsed != null && parsed > 0 && !tooMuch,
                onClick = {
                    val signed = if (withdraw) -parsed!! else parsed!!
                    onSave(signed, day, note)
                },
            ) { Text("保存") }
        }
    }
    if (picking) {
        LicaiDatePicker(day, onDismiss = { picking = false }) {
            day = it
            picking = false
        }
    }
}
