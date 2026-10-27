package com.lincx.noise.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lincx.noise.DbLevel
import com.lincx.noise.NoiseReading
import com.lincx.noise.NoiseViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private val Quiet = Color(0xFF1B7A4B)
private val Loudish = Color(0xFFB45309)
private val VeryLoud = Color(0xFFC2412D)

@Composable
fun NoiseApp(vm: NoiseViewModel = viewModel()) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    var hasPermission by rememberSaveable {
        mutableStateOf(context.hasMicPermission())
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        asked = true
        hasPermission = granted
        if (granted) vm.userStart()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasPermission = context.hasMicPermission()
                    if (hasPermission) vm.onForeground()
                }
                Lifecycle.Event.ON_STOP -> vm.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            hasPermission = context.hasMicPermission()
            if (hasPermission) vm.onForeground()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onBackground()
        }
    }

    val activity = context.findActivity()
    val permanentlyDenied = asked &&
        !hasPermission &&
        activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO,
        )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text("噪声粗测", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "手机麦克风未经校准，数值只作环境嘈杂程度的参考，不是专业声级计读数。不联网，读数只留在这次打开的页面上。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            MeterBlock(ui.instantDb, ui.running)
            Spacer(Modifier.height(12.dp))
            AdviceBlock(ui.instantDb)
            Spacer(Modifier.height(12.dp))
            PeakRow(
                sessionPeak = if (ui.instantDb == null) null else ui.sessionPeakDb,
                windowPeak = ui.windowPeakDb,
                onReset = vm::resetPeak,
            )
            Spacer(Modifier.height(16.dp))
            if (!hasPermission) {
                Text(
                    if (permanentlyDenied) {
                        "需要麦克风权限。系统不再弹出询问时，请到设置里为本应用打开麦克风。小米手机：设置 → 应用设置 → 噪声粗测 → 权限。"
                    } else {
                        "测量需要麦克风权限。权限只用于当场读声级。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            ui.error?.let {
                Text(it, color = VeryLoud, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            ui.silenceHint?.let {
                Text(it, color = Loudish, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    when {
                        ui.wantsRunning -> vm.userStop()
                        !hasPermission && permanentlyDenied -> context.openAppSettings()
                        !hasPermission -> launcher.launch(Manifest.permission.RECORD_AUDIO)
                        else -> vm.userStart()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        ui.wantsRunning -> "停止测量"
                        !hasPermission && permanentlyDenied -> "打开设置"
                        !hasPermission -> "允许麦克风并开始"
                        else -> "开始测量"
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("最近读数", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (ui.readings.isEmpty()) {
                Text(
                    "开始后，大约每 0.2 秒记一行瞬时参考值和该段峰值。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ReadingList(ui.readings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MeterBlock(instantDb: Double?, running: Boolean) {
    val color = adviceColor(instantDb)
    val spoken = if (instantDb == null) {
        "尚未测量"
    } else {
        "当前参考声级 ${formatDb(instantDb)} 分贝"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            formatDb(instantDb),
            color = color,
            fontSize = 84.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 88.sp,
        )
        Text(
            if (running) "参考 dB · 瞬时" else if (instantDb != null) "参考 dB · 已暂停" else "参考 dB · 瞬时",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdviceBlock(instantDb: Double?) {
    val (title, body, color) = adviceCopy(instantDb)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "分档按参考值：低于 45 为安静，45 至 70 为偏吵，70 及以上为很吵。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeakRow(sessionPeak: Double?, windowPeak: Double?, onReset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "峰值 ${formatDb(sessionPeak)} dB",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "本次测量以来瞬时值的最大；本段峰值 ${formatDb(windowPeak)} dB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onReset) { Text("重置峰值") }
    }
}

@Composable
private fun ReadingList(readings: List<NoiseReading>, modifier: Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(readings, key = { it.id }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTime(row.timeMillis), style = MaterialTheme.typography.bodyLarge)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${formatDb(row.instantDb)} dB",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "段峰值 ${formatDb(row.peakDb)} dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun adviceCopy(instantDb: Double?): Triple<String, String, Color> {
    if (instantDb == null) {
        return Triple("尚未测量", "点下面的按钮开始。离开本页会停下并放开麦克风。", Color.Unspecified)
    }
    return when (DbLevel.advice(instantDb)) {
        DbLevel.Advice.QUIET -> Triple("安静", "环境比较安静。", Quiet)
        DbLevel.Advice.LOUDISH -> Triple("偏吵", "有明显环境声，交谈需要稍大声。", Loudish)
        DbLevel.Advice.VERY_LOUD -> Triple("很吵", "声音很大，不宜久留。", VeryLoud)
    }
}

private fun adviceColor(instantDb: Double?): Color {
    if (instantDb == null) return Color.Unspecified
    return when (DbLevel.advice(instantDb)) {
        DbLevel.Advice.QUIET -> Quiet
        DbLevel.Advice.LOUDISH -> Loudish
        DbLevel.Advice.VERY_LOUD -> VeryLoud
    }
}

private fun formatDb(value: Double?): String =
    if (value == null) "—" else String.format(Locale.CHINA, "%.1f", value)

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(clock)

private fun android.content.Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun android.content.Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}
