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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.lincx.noise.QuietRoomOffsetStore
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
    val offsetStore = remember { QuietRoomOffsetStore(context.applicationContext) }
    var offsetRestored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.setOffset(offsetStore.read())
        offsetRestored = true
    }
    LaunchedEffect(ui.offsetDb, offsetRestored) {
        if (offsetRestored) offsetStore.write(ui.offsetDb)
    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("噪声粗测", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Text(
                    "估算环境有多吵。大数字是 A 计权快档：能量平滑的时间常数约 0.125 秒。旁边是本次峰值和等效声级 Leq。A 计权按 IEC 61672 的曲线做数字近似。手机麦克风没有实验室校准，这不是计量器具，不能用于执法、职业卫生、听力诊断或验收。不联网，读数只留在这次打开的页面上。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { MeterBlock(ui.instantDb, ui.running, ui.sampleRate) }
            item { AdviceBlock(ui.instantDb) }
            item {
                LevelRow(
                    sessionPeak = if (ui.instantDb == null) null else ui.sessionPeakDb,
                    windowPeak = ui.windowPeakDb,
                    leq = if (ui.instantDb == null) null else ui.leqDb,
                    onReset = vm::resetPeak,
                )
            }
            item {
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
                ui.clippingHint?.let {
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
            }
            item {
                CalibrationCard(
                    offsetDb = ui.offsetDb,
                    recentSeconds = ui.recentSeconds,
                    running = ui.running,
                    message = ui.calibrationMessage,
                    onCalibrate = vm::calibrateQuietRoom,
                    onClear = vm::clearOffset,
                )
            }
            item {
                Text("最近读数", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (ui.readings.isEmpty()) {
                    Text(
                        "开始后大约每 0.2 秒记一行快档和该段峰值。Leq 与本次峰值见上方。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ui.readings, key = { it.id }) { row ->
                ReadingRow(row)
            }
        }
    }
}

@Composable
private fun MeterBlock(instantDb: Double?, running: Boolean, sampleRate: Int?) {
    val color = adviceColor(instantDb)
    val spoken = if (instantDb == null) {
        "尚未测量"
    } else {
        "当前 A 计权快档 ${formatDb(instantDb)} 分贝"
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
            meterCaption(running, instantDb, sampleRate),
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
                "分档只按参考读数粗分：低于 45 为安静，45 至 70 为偏吵，70 及以上为很吵。不是法规限值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelRow(
    sessionPeak: Double?,
    windowPeak: Double?,
    leq: Double?,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "等效 Leq ${formatDb(leq)} dB(A)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "从这次开始测量起的能量平均",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "峰值 ${formatDb(sessionPeak)} dB(A)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "A 计权波形绝对值的最大；本段峰值 ${formatDb(windowPeak)} dB(A)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReset) { Text("重置峰值") }
        }
    }
}

@Composable
private fun CalibrationCard(
    offsetDb: Double,
    recentSeconds: Double,
    running: Boolean,
    message: String?,
    onCalibrate: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "安静房间相对校准",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (kotlin.math.abs(offsetDb) < 0.05) {
                    "未设偏移。0 dBFS 仍按 90 的满幅参考来显示，不同手机会差一截。"
                } else {
                    "当前偏移 ${formatSigned(offsetDb)} dB。读数已整体平移。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "在你认为安静的房间里测满约 2 秒，可以把这段等效声级平移到 ${DbLevel.QUIET_ROOM_REFERENCE_DB.toInt()} dB(A)，用来抵消这台手机的增益差。房间如果并不是这个声级，所有读数会一起偏。相对起伏比绝对值更值得看。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                calibrationProgress(recentSeconds, running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Loudish)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCalibrate) { Text("设为安静房间") }
                if (kotlin.math.abs(offsetDb) >= 0.05) {
                    TextButton(onClick = onClear) { Text("清除偏移") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "仍非计量级。没有声校准器，不能代替声级计。小米等机型已优先请求未处理麦克风，并尝试关闭自动增益、降噪和回声消除；系统若不提供，读数仍受加工影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingRow(row: NoiseReading) {
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
                "快档 ${formatDb(row.instantDb)} dB(A)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "段峰值 ${formatDb(row.peakDb)} dB(A)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun meterCaption(running: Boolean, instantDb: Double?, sampleRate: Int?): String {
    val state = when {
        running -> "快档"
        instantDb != null -> "已暂停"
        else -> "快档"
    }
    val rate = when {
        sampleRate == null -> ""
        sampleRate < 32_000 -> " · ${sampleRate} Hz，高频会偏"
        else -> " · ${sampleRate} Hz"
    }
    return "dB(A) 参考 · $state$rate"
}

private fun calibrationProgress(recentSeconds: Double, running: Boolean): String {
    if (recentSeconds >= DbLevel.QUIET_ROOM_MIN_SECONDS) {
        return "最近约 2 秒的采样已经够用，可以设偏移。"
    }
    if (!running && recentSeconds <= 0.0) {
        return "开始测量后，在安静房间保持约 2 秒，再点设为安静房间。"
    }
    val remain = (2.0 - recentSeconds).coerceAtLeast(0.0)
    return "还要大约 ${String.format(Locale.CHINA, "%.1f", remain)} 秒安静采样。"
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

private fun formatSigned(value: Double): String =
    String.format(Locale.CHINA, "%+.1f", value)

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
