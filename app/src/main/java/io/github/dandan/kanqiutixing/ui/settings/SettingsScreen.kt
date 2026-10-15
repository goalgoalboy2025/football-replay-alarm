package io.github.dandan.kanqiutixing.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dandan.kanqiutixing.BuildConfig
import io.github.dandan.kanqiutixing.data.local.TeamEntity
import io.github.dandan.kanqiutixing.work.WeeklyScheduleWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenOnboarding: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val verdict by vm.verdict.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val clubs = state.teams.filter { it.type == "CLUB" }
            val nationals = state.teams.filter { it.type == "NATIONAL" }

            SectionTitle("俱乐部 (${clubs.size})")
            clubs.forEach { TeamRow(it) { vm.toggleTeamEnabled(it) } }

            SectionTitle("国家队 (${nationals.size})")
            nationals.forEach { TeamRow(it) { vm.toggleTeamEnabled(it) } }

            SectionTitle("比赛类型过滤")
            SwitchRow("正赛 (联赛/欧冠/世界杯/亚洲杯等)", state.settings.enableOfficial,
                onChange = vm::toggleOfficial)
            SwitchRow("资格赛 (世预赛/欧预赛/亚预赛等)", state.settings.enableQualifiers,
                onChange = vm::toggleQualifiers)
            SwitchRow("热身赛/友谊赛", state.settings.enableFriendly,
                onChange = vm::toggleFriendly)

            SectionTitle("提醒设置")
            SwitchRow("开球前 15 分钟提醒 (9:00 — 22:00 比赛)",
                state.settings.enableLiveReminder, onChange = vm::toggleLiveReminder)
            SwitchRow("早晨录播提醒 (深夜/凌晨比赛)",
                state.settings.enableReplayReminder, onChange = vm::toggleReplayReminder)

            HourPickerRow(
                label = "工作日早晨提醒时间",
                hour = state.settings.workdayMorningHour,
                onHourChange = vm::setWorkdayHour
            )
            HourPickerRow(
                label = "节假日早晨提醒时间",
                hour = state.settings.holidayMorningHour,
                onHourChange = vm::setHolidayHour
            )

            // 「提醒没响」的时候先看这里：闹钟挂没挂上、通知渠道有没有被静音、
            // 后台有没有被电池优化冻住——这三项任一不对，表现都是用户说没收到。
            SectionTitle("提醒健康检查")
            ListItem(
                headlineContent = { Text(verdict, fontWeight = FontWeight.Bold) },
                supportingContent = {
                    Text(
                        diagnostics,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = vm::refreshDiagnostics,
                    modifier = Modifier.weight(1f)
                ) { Text("刷新") }
                OutlinedButton(
                    onClick = {
                        WeeklyScheduleWorker.runOnce(ctx)
                        vm.refreshDiagnostics()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("同步") }
                OutlinedButton(
                    onClick = { openBatterySettings(ctx) },
                    modifier = Modifier.weight(1f)
                ) { Text("电池") }
            }

            SectionTitle("测试通知")
            ListItem(
                headlineContent = { Text("立即发送测试通知") },
                supportingContent = { Text("验证通知权限和通知渠道是否正常") },
                modifier = Modifier.clickable { vm.testNotificationNow() }
            )
            ListItem(
                headlineContent = { Text("2 分钟后提醒测试") },
                supportingContent = { Text("验证 AlarmManager + 后台保活：点击后可把 App 划掉，看 2 分钟后是否准时弹") },
                modifier = Modifier.clickable { vm.testNotificationDelayed() }
            )

            SectionTitle("高级")
            ListItem(
                headlineContent = { Text("重新同步球队列表") },
                supportingContent = { Text("立即从 assets 或远程 URL 重新加载") },
                modifier = Modifier.clickable { vm.resyncTeams() }
            )
            ListItem(
                headlineContent = { Text("MagicOS 后台保活引导") },
                supportingContent = { Text("查看荣耀手机配置清单") },
                modifier = Modifier.clickable(onClick = onOpenOnboarding)
            )

            Spacer(Modifier.height(24.dp))
            // 版本标识：装完先看这里，确认手机上跑的是不是刚构建的包
            Surface(
                color = if (BuildConfig.IS_BETA)
                    MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${BuildConfig.APPLICATION_LABEL}  v${BuildConfig.VERSION_NAME}"
                        + "  (code ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "构建时间 ${BuildConfig.BUILD_TIME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (BuildConfig.IS_BETA) {
                        Text(
                            "共存测试包：与正式包相互独立，数据不共享",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "数据由 football-data.org 提供",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * 跳到系统的电池优化设置页。
 * 国产 ROM 不把 App 加进白名单的话，后台进程会被冻结，闹钟到点也唤不醒 App——
 * 这是「后台不响」最常见的原因之一。Manifest 已声明
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS，部分 ROM 支持直接弹授权框；
 * 不支持的会抛 ActivityNotFound，退回系统电池设置列表页。
 */
private fun openBatterySettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            try {
                ctx.startActivity(
                    Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
                return
            } catch (e: Exception) {
                Log.w("KanqiuSettings", "不支持直接授权，退回电池设置页", e)
            }
        }
    }
    try {
        ctx.startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: Exception) {
        Log.w("KanqiuSettings", "无法打开电池设置页", e)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange)
        },
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

@Composable
private fun HourPickerRow(
    label: String,
    hour: Int,
    onHourChange: (Int) -> Unit
) {
    val display = "%02d:00".format(hour)
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = hour.toFloat(),
            onValueChange = { onHourChange(it.toInt()) },
            valueRange = 5f..12f,
            steps = 6
        )
    }
}

@Composable
private fun TeamRow(team: TeamEntity, onToggle: () -> Unit) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    team.nameZh.take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        headlineContent = { Text(team.nameZh) },
        supportingContent = { Text(team.league) },
        trailingContent = {
            Switch(checked = team.enabled, onCheckedChange = { onToggle() })
        },
        modifier = Modifier.clickable { onToggle() }
    )
}
