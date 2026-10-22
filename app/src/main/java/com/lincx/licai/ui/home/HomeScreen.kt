package com.lincx.licai.ui.home

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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lincx.licai.domain.formatPercent
import com.lincx.licai.domain.formatYuan
import com.lincx.licai.ui.AssetRowModel
import com.lincx.licai.ui.GoalCardModel
import com.lincx.licai.ui.HomeUi
import com.lincx.licai.ui.HomeViewModel
import com.lincx.licai.ui.RepoViewModelFactory
import com.lincx.licai.ui.components.DriftChip
import com.lincx.licai.ui.components.EmptyHint
import com.lincx.licai.ui.components.GoalProgress
import com.lincx.licai.ui.components.MoneyText
import com.lincx.licai.ui.components.SectionTitle
import com.lincx.licai.ui.components.SoftCard
import com.lincx.licai.ui.components.StatBlock
import com.lincx.licai.ui.components.TargetActualBar
import com.lincx.licai.ui.repo
import com.lincx.licai.ui.theme.LocalFinanceColors

@Composable
fun HomeScreen(
    onOpenLedger: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenGoal: (Long) -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = RepoViewModelFactory(repo()))
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (val state = ui) {
        HomeUi.Loading -> Column(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
        is HomeUi.Ready -> HomeContent(state.data, onOpenLedger, onOpenGoals, onOpenAssets, onOpenGoal)
    }
}

@Composable
private fun HomeContent(
    state: com.lincx.licai.ui.HomeReady,
    onOpenLedger: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenGoal: (Long) -> Unit,
) {
    val colors = LocalFinanceColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("厘财", style = MaterialTheme.typography.headlineMedium)
            Text(
                "花销、目标和配置，记在这台手机上。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SoftCard(color = colors.balance) {
                Text(state.monthLabel, color = colors.balanceMuted, style = MaterialTheme.typography.labelLarge)
                Text("本月结余", color = colors.balanceMuted, style = MaterialTheme.typography.labelLarge)
                MoneyText(state.balanceCents, colors.onBalance, MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 12.dp)) {
                    StatBlock("收入", state.incomeCents, colors.onBalance)
                    StatBlock("支出", state.expenseCents, colors.onBalance)
                }
                if (state.topExpenseLabel != null) {
                    Text(
                        "支出最多的是${state.topExpenseLabel}，占 ${state.topExpensePercent}%",
                        color = colors.balanceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                TextButton(onClick = onOpenLedger) {
                    Text("去记账", color = colors.onBalance, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            SectionTitle(
                title = "存钱目标",
                action = if (state.goals.isNotEmpty()) "全部" else null,
                onAction = onOpenGoals,
            )
        }
        if (state.goals.isEmpty()) {
            item {
                EmptyHint(
                    icon = Icons.Outlined.Flag,
                    title = "还没有存钱目标",
                    body = "应急金、旅行、房租，给一件想做成的事设一个金额。",
                    action = "新建目标",
                    onAction = onOpenGoals,
                )
            }
        } else {
            items(state.goals, key = { it.id }) { goal ->
                GoalPreview(goal, onClick = { onOpenGoal(goal.id) })
            }
        }
        item {
            SectionTitle(
                title = "资产配置",
                action = if (state.assets.isNotEmpty()) "查看" else null,
                onAction = onOpenAssets,
            )
        }
        if (state.assets.isEmpty()) {
            item {
                EmptyHint(
                    icon = Icons.Outlined.PieChart,
                    title = "还没有配置计划",
                    body = "写下各类资产的目标比例，再填实际金额。偏离达到 5 个百分点会提醒你。",
                    action = "去配置",
                    onAction = onOpenAssets,
                )
            }
        } else {
            item { AssetPreview(state.assets, state.assetTotalCents, state.driftCount) }
        }
    }
}

@Composable
private fun GoalPreview(goal: GoalCardModel, onClick: () -> Unit) {
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium)
                Text(goal.headline, style = MaterialTheme.typography.bodyLarge)
                Text(goal.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GoalProgress(goal.fraction)
                Text(
                    "已存 ${formatYuan(goal.savedCents)} / ${formatYuan(goal.targetCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onClick) { Text("查看") }
    }
}

@Composable
private fun AssetPreview(assets: List<AssetRowModel>, total: Long, driftCount: Int) {
    val colors = LocalFinanceColors.current
    SoftCard {
        Text("实际总额", color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(total, MaterialTheme.colorScheme.onSurface)
        if (total <= 0) {
            Text("填入实际金额后，才能对比目标和持仓。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (driftCount > 0) {
            Text("$driftCount 项偏离", color = colors.caution, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "竖线是目标比例。这里只做记录，不构成投资建议。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        assets.forEach { row ->
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(row.name, style = MaterialTheme.typography.titleSmall)
                    DriftChip(row.drift)
                }
                TargetActualBar(row.targetPercent, row.actualPercent)
                Text(
                    "目标 ${formatPercent(row.targetPercent)} · 实际 ${formatPercent(row.actualPercent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
