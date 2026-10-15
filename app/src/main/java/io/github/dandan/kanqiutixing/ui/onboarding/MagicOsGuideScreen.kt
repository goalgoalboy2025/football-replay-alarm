package io.github.dandan.kanqiutixing.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicOsGuideScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MagicOS 后台保活引导") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "为了让通知准时弹出，需在荣耀 MagicOS 10 上完成以下 5 项配置。"
                    + "若不配置，App 在被系统冷冻后将无法触发提醒。",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            Step(
                index = 1,
                title = "应用启动管理（最关键）",
                desc = "设置 → 应用和服务 → 应用启动管理 → 找到本 App → 关闭「自动管理」→ 全部勾选自启动 / 关联启动 / 后台活动",
                actionText = "打开应用启动管理",
                action = {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}")))
                    }
                }
            )
            Step(
                index = 2,
                title = "电池策略设为不受限",
                desc = "设置 → 应用 → 应用管理 → 看球提醒 → 电池 → 不受限制",
                actionText = "申请加入电池白名单",
                action = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${ctx.packageName}")))
                        }
                    }
                }
            )
            Step(
                index = 3,
                title = "关闭智能省电与应用冷冻",
                desc = "设置 → 电池 → 智能省电：把本 App 移出列表；设置 → 应用 → 看球提醒 → 关闭「应用冷冻」",
                actionText = "打开电池设置",
                action = {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    }
                }
            )
            Step(
                index = 4,
                title = "通知权限与重要级别",
                desc = "设置 → 通知和状态栏 → 应用通知管理 → 看球提醒 → 把「直播提醒」channel 设为重要通知",
                actionText = "打开通知设置",
                action = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        }
                    }
                }
            )
            Step(
                index = 5,
                title = "锁屏与精确闹钟权限",
                desc = "授予「显示在其他应用上层」和「闹钟和提醒」权限，让通知能在锁屏弹出且时间精确",
                actionText = "打开精确闹钟设置",
                action = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${ctx.packageName}")))
                        }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "完成 5 项后，建议做一次 30 分钟的锁屏闹钟测试，确认提醒能在锁屏状态下准时弹出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Step(
    index: Int,
    title: String,
    desc: String,
    actionText: String,
    action: () -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("第 $index 步：$title", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = action) { Text(actionText) }
        }
    }
}
