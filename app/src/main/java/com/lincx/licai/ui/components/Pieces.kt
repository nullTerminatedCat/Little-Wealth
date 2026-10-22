package com.lincx.licai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lincx.licai.domain.Drift
import com.lincx.licai.domain.DriftResult
import com.lincx.licai.domain.driftLabel
import com.lincx.licai.domain.formatYuan
import com.lincx.licai.domain.sanitizeAmountInput
import com.lincx.licai.domain.sanitizePercentInput
import com.lincx.licai.ui.theme.LocalFinanceColors

@Composable
fun MoneyText(cents: Long, color: Color, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge) {
    Text(
        text = formatYuan(cents),
        color = color,
        style = style,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), content = { content() })
    }
}

@Composable
fun EmptyHint(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun TargetActualBar(targetPercent: Double, actualPercent: Double, modifier: Modifier = Modifier) {
    val colors = LocalFinanceColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth((actualPercent / 100.0).toFloat().coerceIn(0f, 1f))
                .height(14.dp)
                .background(colors.income.copy(alpha = 0.85f)),
        )
        Box(
            Modifier
                .fillMaxWidth((targetPercent / 100.0).toFloat().coerceIn(0f, 1f))
                .wrapContentWidth(Alignment.End),
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
fun DriftChip(drift: DriftResult?) {
    val colors = LocalFinanceColors.current
    if (drift == null) {
        Text("待填写", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        return
    }
    val color = when (drift.drift) {
        Drift.OnTarget -> colors.income
        Drift.High, Drift.Low -> colors.caution
    }
    Text(driftLabel(drift), color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
fun GoalProgress(fraction: Float, modifier: Modifier = Modifier) {
    ShareBar(fraction, LocalFinanceColors.current.income, modifier)
}

@Composable
fun YuanField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            val clean = sanitizeAmountInput(next)
            if (clean != null) onValueChange(clean)
        },
        label = { Text(label) },
        supportingText = { if (error != null) Text(error) },
        isError = error != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        prefix = { Text("¥") },
    )
}

@Composable
fun PercentField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            val clean = sanitizePercentInput(next)
            if (clean != null) onValueChange(clean)
        },
        label = { Text(label) },
        supportingText = { if (error != null) Text(error) },
        isError = error != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        suffix = { Text("%") },
    )
}

@Composable
fun PlainField(value: String, onValueChange: (String) -> Unit, label: String, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { if (error != null) Text(error) },
        isError = error != null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun DateButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun StatBlock(label: String, cents: Long, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        MoneyText(cents, color, MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ChoiceChips(options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (label, selected) ->
            FilterChip(selected = selected, onClick = { onPick(index) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}
