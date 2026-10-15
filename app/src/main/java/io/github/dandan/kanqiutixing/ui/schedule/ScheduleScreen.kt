package io.github.dandan.kanqiutixing.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dandan.kanqiutixing.data.local.MatchEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenSettings: () -> Unit,
    vm: ScheduleViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val syncState by vm.syncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 本地勾选状态：key 含每场比赛的 id + reminderEnabled，
    // 用户勾选（仅本地）时 key 不变、选择保留；同步或确认写库后 key 变，重新按数据库 seed
    val checkedKey = state.matches.joinToString(",") { "${it.id}:${it.reminderEnabled}" }
    var checkedIds by remember(checkedKey) {
        mutableStateOf(state.matches.filter { it.reminderEnabled }.map { it.id }.toSet())
    }

    // 只在"本次界面里确实看到过同步开始"之后，才对成功/失败弹提示，
    // 避免 WorkManager 残留的 SUCCEEDED 状态在进页面时误弹
    var observedSyncing by remember { mutableStateOf(false) }
    LaunchedEffect(syncState) {
        when (syncState) {
            SyncState.Syncing -> observedSyncing = true
            SyncState.Success -> if (observedSyncing) {
                observedSyncing = false
                snackbarHostState.showSnackbar("赛程已更新")
            }
            SyncState.Failed -> if (observedSyncing) {
                observedSyncing = false
                snackbarHostState.showSnackbar("同步失败，请检查网络或稍后再试")
            }
            SyncState.Idle -> {}
        }
    }

    // "确认提醒选择"的结果反馈
    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本周赛程") },
                actions = {
                    if (syncState == SyncState.Syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { vm.syncNow() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "立即同步")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.matches.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = { vm.confirmSelection(checkedIds) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("确认提醒选择（已选 ${checkedIds.size} 场）")
                    }
                }
            }
        }
    ) { padding ->
        if (state.matches.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                syncing = syncState == SyncState.Syncing
            )
        } else {
            val zone = ZoneId.of("Asia/Shanghai")
            val grouped = state.matches.groupBy {
                Instant.ofEpochMilli(it.utcKickoff).atZone(zone).toLocalDate()
            }
            LazyColumn(modifier = Modifier.padding(padding)) {
                grouped.forEach { (date, matches) ->
                    val title = date.format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA))
                    item {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(matches, key = { it.id }) { m ->
                        MatchCard(
                            match = m,
                            zone = zone,
                            checked = m.id in checkedIds,
                            onCheckedChange = { isChecked ->
                                checkedIds = if (isChecked) checkedIds + m.id
                                else checkedIds - m.id
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, syncing: Boolean) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (syncing) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("正在加载本周赛程…", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "首次同步可能需要 30 秒左右",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("暂无未来 7 天比赛", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "点击右上角刷新按钮立即同步",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: MatchEntity,
    zone: ZoneId,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val time = Instant.ofEpochMilli(match.utcKickoff).atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    val reminderHint = computeReminderHint(match, zone)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                hourLabel(match.utcKickoff, zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            CompetitionChip(match.competitionName, match.competitionType)
            Spacer(Modifier.height(2.dp))
            Text(match.homeTeamName, style = MaterialTheme.typography.bodyMedium)
            Text(match.awayTeamName, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = {},
            label = { Text(reminderHint, style = MaterialTheme.typography.labelSmall) }
        )
    }
}

@Composable
private fun CompetitionChip(name: String, type: String) {
    val container = when (type) {
        "QUALIFIERS" -> MaterialTheme.colorScheme.tertiary
        "FRIENDLY" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = container,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun hourLabel(utcMillis: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(utcMillis).atZone(zone).hour
    return when {
        t in 0..8 -> "凌晨"
        t in 9..21 -> "直播"
        else -> "深夜"
    }
}

private fun computeReminderHint(match: MatchEntity, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(match.utcKickoff).atZone(zone).toLocalTime()
    val hour = t.hour
    return when {
        hour in 9..21 || (hour == 22 && t.minute == 0) -> {
            val minus15 = Instant.ofEpochMilli(match.utcKickoff).atZone(zone).minusMinutes(15)
            "开球前 ${minus15.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        }
        hour > 22 || (hour == 22 && t.minute > 0) -> "次日早晨"
        else -> "当日早晨"
    }
}
