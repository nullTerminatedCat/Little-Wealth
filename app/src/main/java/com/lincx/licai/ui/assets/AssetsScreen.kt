package com.lincx.licai.ui.assets

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lincx.licai.domain.centsToInput
import com.lincx.licai.domain.formatPercent
import com.lincx.licai.domain.formatYuan
import com.lincx.licai.domain.parsePercent
import com.lincx.licai.domain.parseYuanToCents
import com.lincx.licai.domain.percentToInput
import com.lincx.licai.ui.AssetRowModel
import com.lincx.licai.ui.AssetsUi
import com.lincx.licai.ui.AssetsViewModel
import com.lincx.licai.ui.RepoViewModelFactory
import com.lincx.licai.ui.components.ConfirmDialog
import com.lincx.licai.ui.components.DriftChip
import com.lincx.licai.ui.components.EmptyHint
import com.lincx.licai.ui.components.MoneyText
import com.lincx.licai.ui.components.PercentField
import com.lincx.licai.ui.components.PlainField
import com.lincx.licai.ui.components.SoftCard
import com.lincx.licai.ui.components.TargetActualBar
import com.lincx.licai.ui.components.YuanField
import com.lincx.licai.ui.repo
import com.lincx.licai.ui.theme.LocalFinanceColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(vm: AssetsViewModel = viewModel(factory = RepoViewModelFactory(repo()))) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AssetRowModel?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AssetRowModel?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (ui is AssetsUi.Ready) {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "加一类")
                }
            }
        },
    ) { padding ->
        when (val state = ui) {
            AssetsUi.Loading -> Column(Modifier.padding(padding).padding(24.dp)) { CircularProgressIndicator() }
            is AssetsUi.Ready -> AssetsBody(
                padding = padding,
                data = state.data,
                onEdit = { editing = it },
                onTemplate = vm::applyTemplate,
            )
        }
    }
    if (creating || editing != null) {
        AssetEditorSheet(
            row = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { id, name, percent, cents, note, order ->
                vm.save(id, name, percent, cents, note, order)
                creating = false
                editing = null
            },
            onDelete = {
                deleting = editing
                editing = null
            },
        )
    }
    deleting?.let { row ->
        ConfirmDialog(
            title = "删除「${row.name}」？",
            body = "只删除这一类的记录，不会影响别的资产。",
            confirm = "删除",
            onConfirm = {
                vm.delete(row.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun AssetsBody(
    padding: PaddingValues,
    data: com.lincx.licai.ui.AssetsReady,
    onEdit: (AssetRowModel) -> Unit,
    onTemplate: () -> Unit,
) {
    val colors = LocalFinanceColors.current
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("资产配置", style = MaterialTheme.typography.headlineMedium)
            Text(
                "记录你自己的持仓和目标比例。不连接券商，也不提供买卖建议。实际占比相差达到 5 个百分点时会标成偏离。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data.items.isEmpty()) {
            item {
                EmptyHint(
                    icon = Icons.Outlined.PieChart,
                    title = "先放进几类资产",
                    body = "可以用常见模板：现金、债券、股票基金、黄金和其他。比例之后都能改。",
                    action = "填入常见模板",
                    onAction = onTemplate,
                )
            }
        } else {
            item {
                SoftCard {
                    Text("实际总额", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(data.totalCents, MaterialTheme.colorScheme.onSurface)
                    if (data.totalCents <= 0L) {
                        Text("填入实际金额后，才能对比目标和持仓。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (data.driftCount > 0) {
                        Text("有 ${data.driftCount} 类资产偏离目标达到 5 个百分点。", color = colors.caution)
                    } else {
                        Text("各类资产都在目标附近。", color = colors.income)
                    }
                    data.targetWarning?.let { Text(it, color = colors.caution) }
                    Text(
                        "竖线表示目标比例，色条表示实际占比。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(data.items, key = { it.id }) { row ->
                SoftCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.name, style = MaterialTheme.typography.titleMedium)
                        DriftChip(row.drift)
                    }
                    if (row.note.isNotBlank()) {
                        Text(row.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TargetActualBar(row.targetPercent, row.actualPercent)
                    Text(
                        "目标 ${formatPercent(row.targetPercent)} · 实际 ${formatPercent(row.actualPercent)} · ${formatYuan(row.actualCents)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onEdit(row) }) { Text("编辑资产") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetEditorSheet(
    row: AssetRowModel?,
    onDismiss: () -> Unit,
    onSave: (Long, String, Double, Long, String, Int?) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(row?.name ?: "") }
    var percent by remember { mutableStateOf(row?.let { percentToInput(it.targetPercent) } ?: "") }
    var actual by remember { mutableStateOf(row?.let { centsToInput(it.actualCents, blankIfZero = true) } ?: "") }
    var note by remember { mutableStateOf(row?.note ?: "") }
    val percentValue = if (percent.isBlank()) null else parsePercent(percent)
    val actualCents = if (actual.isBlank()) 0L else parseYuanToCents(actual)
    val nameError = if (name.isNotBlank()) null else if (name.isEmpty()) null else "写一个名字"
    val percentError = when {
        percent.isBlank() -> null
        percentValue == null -> "填写 0 到 100，最多一位小数"
        else -> null
    }
    val actualError = if (actual.isNotBlank() && actualCents == null) "金额最多两位小数" else null
    val canSave = name.isNotBlank() && percentValue != null && actualCents != null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (row == null) "加一类资产" else "编辑资产", style = MaterialTheme.typography.titleLarge)
            PlainField(name, { name = it }, "名称，例如股票基金", nameError)
            PercentField(percent, { percent = it }, "目标比例", percentError)
            YuanField(actual, { actual = it }, "实际金额，可先填 0", actualError)
            PlainField(note, { note = it }, "备注，可不填")
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(row?.id ?: 0L, name, percentValue!!, actualCents!!, note, row?.sortOrder)
                },
            ) { Text("保存") }
            if (row != null) {
                TextButton(onClick = onDelete) { Text("删除这类", color = LocalFinanceColors.current.expense) }
            }
        }
    }
}
