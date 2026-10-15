package io.github.dandan.kanqiutixing.ui.setup

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.dandan.kanqiutixing.data.local.TeamEntity

/**
 * 首启动 4 步向导。
 *
 *  Step 0  通知权限（Android 13+ 才需要）
 *  Step 1  精确闹钟权限（Android 12+ 才需要）
 *  Step 2  球队选择（至少 1 支才能下一步）
 *  Step 3  早晨录播提醒时间自定义（工作日 / 节假日各一个滑杆，范围 5–12 点）
 *
 *  权限步即使被拒也允许进入下一步——App 还能用，只是会降级（通知发不出 /
 *  闹钟可能被 Doze 延迟）。设置页里还能再去补。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onComplete: () -> Unit,
    vm: SetupWizardViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("欢迎使用 看球不熬夜闹钟") })
                StepIndicator(current = state.step, total = state.totalSteps)
            }
        },
        bottomBar = {
            WizardNavBar(
                isFirstStep = state.step == 0,
                isLastStep = state.isLastStep,
                canProceed = state.canProceed,
                onPrev = vm::previous,
                onNext = vm::next,
                onFinish = { vm.finish(onComplete) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (state.step) {
                0 -> NotificationPermissionStep()
                1 -> ExactAlarmStep()
                2 -> TeamSelectionStep(
                    teams = state.allTeams.filter { it.type == "CLUB" },
                    selectedIds = state.selectedTeamIds,
                    onToggle = vm::toggleTeam
                )
                3 -> TimePickerStep(
                    workdayHour = state.workdayHour,
                    onWorkdayChange = vm::setWorkdayHour,
                    holidayHour = state.holidayHour,
                    onHolidayChange = vm::setHolidayHour
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun WizardNavBar(
    isFirstStep: Boolean,
    isLastStep: Boolean,
    canProceed: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrev, enabled = !isFirstStep) {
                Text("上一步")
            }
            Button(
                onClick = { if (isLastStep) onFinish() else onNext() },
                enabled = canProceed
            ) {
                Text(if (isLastStep) "完成，开始用" else "下一步")
            }
        }
    }
}

// ---------- Step 0: 通知权限 ----------

@Composable
private fun NotificationPermissionStep() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasNotificationPermission(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    StepTitle("第一步：开启通知权限")
    StepBody(
        "比赛开球前 15 分钟、深夜比赛次日早晨的录播提醒，都靠系统通知弹出来。" +
            "Android 13 之后必须授权 App 才能发通知。"
    )
    Spacer(Modifier.height(16.dp))
    StatusChip(granted = granted, grantedText = "通知权限已开启", deniedText = "尚未授权")
    Spacer(Modifier.height(16.dp))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) {
        FilledTonalButton(onClick = {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text("授予通知权限") }
        Spacer(Modifier.height(8.dp))
        Text(
            "如果系统弹窗里点了\"不允许\"，可以稍后到 系统设置 → 应用 → 看球不熬夜闹钟 → 通知 里再开",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Text(
            "你的 Android 版本 < 13，通知权限默认就有，可以直接下一步。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------- Step 1: 精确闹钟权限 ----------

@Composable
private fun ExactAlarmStep() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasExactAlarmPermission(ctx)) }
    // 用户去系统设置开完权限再回 App，得重新查一次状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasExactAlarmPermission(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    StepTitle("第二步：开启精确闹钟权限")
    StepBody(
        "Android 12 之后系统默认禁止 App 排\"按秒触发\"的精确闹钟。不开的话，" +
            "提醒可能被系统省电策略拖延几分钟到几十分钟，深夜比赛的录播提醒尤其容易迟到。"
    )
    Spacer(Modifier.height(16.dp))
    StatusChip(granted = granted, grantedText = "精确闹钟权限已开启", deniedText = "尚未开启")
    Spacer(Modifier.height(16.dp))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !granted) {
        FilledTonalButton(onClick = {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            }
        }) { Text("打开系统设置开启") }
        Spacer(Modifier.height(8.dp))
        Text(
            "在系统设置页里找到\"闹钟和提醒\"开关，打开后返回本 App",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Text(
            "你的 Android 版本 < 12，精确闹钟默认就能用，直接下一步。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------- Step 2: 球队选择 ----------

@Composable
private fun TeamSelectionStep(
    teams: List<TeamEntity>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit
) {
    StepTitle("第三步：选择关注的球队")
    StepBody("至少选 1 支才能继续。之后还可以在设置页里随时增减。")
    Spacer(Modifier.height(16.dp))
    if (teams.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("球队列表加载中…", style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    // 按 league 分组（"英超" / "西甲" / "德甲" / "意甲" / "法甲"），保证渲染顺序稳定
    val leagueOrder = listOf("英超", "西甲", "德甲", "意甲", "法甲")
    val grouped = teams.groupBy { it.league }
    leagueOrder.forEach { league ->
        val list = grouped[league] ?: return@forEach
        Text(
            text = "$league（${list.size}）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        list.forEach { team ->
            TeamSelectableRow(
                team = team,
                checked = team.id in selectedIds,
                onToggle = { onToggle(team.id) }
            )
        }
    }
    // 兜底：如果出现没归类到上面 5 个联赛的球队，也展示
    grouped.filter { it.key !in leagueOrder }.forEach { (league, list) ->
        Text("$league（${list.size}）", style = MaterialTheme.typography.titleSmall)
        list.forEach { team ->
            TeamSelectableRow(team, team.id in selectedIds) { onToggle(team.id) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "已选 ${selectedIds.size} 支",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TeamSelectableRow(
    team: TeamEntity,
    checked: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    team.nameZh.take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        headlineContent = { Text(team.nameZh) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
        modifier = Modifier.clickable(onClick = onToggle)
    )
}

// ---------- Step 3: 时间选择 ----------

@Composable
private fun TimePickerStep(
    workdayHour: Int,
    onWorkdayChange: (Int) -> Unit,
    holidayHour: Int,
    onHolidayChange: (Int) -> Unit
) {
    StepTitle("第四步：录播提醒时间")
    StepBody(
        "深夜 / 凌晨开球的比赛，会在第二天早晨这个时间提醒你看录播。" +
            "工作日和节假日可以分别设置，范围 5:00 — 12:00。"
    )
    Spacer(Modifier.height(16.dp))
    HourSlider(
        label = "工作日早晨",
        hour = workdayHour,
        onChange = onWorkdayChange
    )
    Spacer(Modifier.height(16.dp))
    HourSlider(
        label = "节假日 / 周末早晨",
        hour = holidayHour,
        onChange = onHolidayChange
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "提示：开球时间在 09:00–22:00 之间的比赛，会按\"开球前 15 分钟\"提醒，" +
            "不受这个早晨时间影响。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HourSlider(
    label: String,
    hour: Int,
    onChange: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(
                "%02d:00".format(hour),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = hour.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 5f..12f,
            steps = 6
        )
    }
}

// ---------- 共享小组件 ----------

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StepBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StatusChip(granted: Boolean, grantedText: String, deniedText: String) {
    AssistChip(
        onClick = {},
        label = { Text(if (granted) grantedText else deniedText) },
        leadingIcon = if (granted) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

// ---------- 工具函数 ----------

private fun hasNotificationPermission(ctx: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun hasExactAlarmPermission(ctx: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = ctx.getSystemService<AlarmManager>() ?: return false
    return am.canScheduleExactAlarms()
}

// ---------- @Preview ----------
// 在 Android Studio 的 Compose Preview 面板（编辑器右侧"Split"或"Design"模式）
// 里可以直接看到下面每个步骤的视觉效果，秒级刷新，不用 Build+Install。
// 这些函数也不会进 release APK——@Preview 注解的代码只在 debug 构建里有效。

@Preview(name = "Step 1 通知权限", showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun PreviewNotificationStep() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                NotificationPermissionStep()
            }
        }
    }
}

@Preview(name = "Step 2 精确闹钟", showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun PreviewExactAlarmStep() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ExactAlarmStep()
            }
        }
    }
}

@Preview(name = "Step 3 球队选择", showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun PreviewTeamSelectionStep() {
    val fakeTeams = listOf(
        TeamEntity(57, "CLUB", "阿森纳", "Arsenal FC", "England", "英超", null, false),
        TeamEntity(65, "CLUB", "曼城", "Manchester City FC", "England", "英超", null, false),
        TeamEntity(64, "CLUB", "利物浦", "Liverpool FC", "England", "英超", null, false),
        TeamEntity(73, "CLUB", "热刺", "Tottenham Hotspur FC", "England", "英超", null, false),
        TeamEntity(81, "CLUB", "巴萨", "FC Barcelona", "Spain", "西甲", null, false),
        TeamEntity(86, "CLUB", "皇马", "Real Madrid CF", "Spain", "西甲", null, false),
        TeamEntity(5, "CLUB", "拜仁慕尼黑", "FC Bayern München", "Germany", "德甲", null, false),
        TeamEntity(4, "CLUB", "多特蒙德", "Borussia Dortmund", "Germany", "德甲", null, false),
        TeamEntity(98, "CLUB", "AC米兰", "AC Milan", "Italy", "意甲", null, false),
        TeamEntity(524, "CLUB", "巴黎圣日耳曼", "Paris Saint-Germain FC", "France", "法甲", null, false),
    )
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TeamSelectionStep(
                    teams = fakeTeams,
                    selectedIds = setOf(57, 64, 81, 86),
                    onToggle = {}
                )
            }
        }
    }
}

@Preview(name = "Step 4 时间选择", showBackground = true, widthDp = 360, heightDp = 740)
@Composable
private fun PreviewTimePickerStep() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                TimePickerStep(
                    workdayHour = 7,
                    onWorkdayChange = {},
                    holidayHour = 8,
                    onHolidayChange = {}
                )
            }
        }
    }
}
