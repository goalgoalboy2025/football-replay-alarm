package io.github.dandan.kanqiutixing.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import io.github.dandan.kanqiutixing.data.SyncStatusStore
import io.github.dandan.kanqiutixing.data.local.NotificationDao
import io.github.dandan.kanqiutixing.data.local.NotificationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 拼一段"提醒健康状况"，给设置页直接显示。
 *
 * 设计来源：吧嗒清单（com.bada.app.RemindSync.statusText）。那边的经验是——
 * 「后台不响」的排查里，真正难的不是修，是**判断到底哪一步断了**。
 * 闹钟挂没挂上、通知渠道有没有被系统静音、后台有没有被电池优化冻住，
 * 这三项只要有一项不对，表现都是同一个：用户说没收到。
 * 把它们摊开摆在屏幕上，无需连电脑就能定位。
 */
@Singleton
class ReminderDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val scheduler: MatchReminderScheduler,
    private val notificationHelper: NotificationHelper,
    private val syncStatus: SyncStatusStore
) {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    suspend fun text(): String = buildString {
        appendLine("【版本】")
        appendLine(versionLine())
        appendLine()
        appendLine("【上次同步】")
        appendLine("时间：${syncStatus.lastAt()}")
        appendLine("拉到 ${syncStatus.lastMatchCount()} 场，排了 ${syncStatus.lastAlarmCount()} 个闹钟")
        val err = syncStatus.lastError()
        if (err.isNotBlank()) appendLine("⚠️ 上次错误：$err")
        appendLine()
        appendLine("【权限与系统设置】")
        appendLine("通知权限：${ok(notificationHelper.hasNotificationPermission())}")
        appendLine("精确闹钟：${ok(scheduler.canScheduleExactAlarms())}")
        appendLine("电池优化白名单：${ok(isIgnoringBatteryOptimizations())}")
        appendLine()
        appendLine("【通知渠道】（被关掉的通知照样打日志，但用户看不到）")
        notificationHelper.channelStates().forEach { c ->
            appendLine("· ${c.label}：${channelVerdict(c)}")
        }
        appendLine()
        appendLine("【已挂闹钟】")
        val pending = notificationDao.getAllPendingSorted()
        if (pending.isEmpty()) {
            appendLine("0 个 ⚠️ 后台不会响")
            appendLine("（要么没同步成功，要么还没勾选要提醒的比赛）")
        } else {
            appendLine("${pending.size} 个，最近 ${minOf(pending.size, 5)} 个：")
            pending.take(5).forEach { appendLine("  · ${describe(it)}") }
        }
    }

    /** 总体判定，放在最顶上，一眼知道要不要处理 */
    suspend fun verdict(): String {
        val problems = mutableListOf<String>()
        if (!notificationHelper.hasNotificationPermission()) problems.add("通知权限未开")
        if (!scheduler.canScheduleExactAlarms()) problems.add("精确闹钟受限")
        if (!isIgnoringBatteryOptimizations()) problems.add("未加电池白名单")
        notificationHelper.channelStates().forEach { c ->
            if (c.importance == NotificationManager.IMPORTANCE_NONE) problems.add("${c.label}渠道被关")
        }
        if (notificationDao.getAllPendingSorted().isEmpty()) problems.add("当前没有待触发闹钟")
        return if (problems.isEmpty()) "✓ 各项正常" else "⚠️ ${problems.joinToString("、")}"
    }

    private fun versionLine(): String = try {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        "${p.versionName}（code ${p.versionCode}）· 构建 ${io.github.dandan.kanqiutixing.BuildConfig.BUILD_TIME}"
    } catch (e: Exception) {
        "未知"
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService<PowerManager>() ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun channelVerdict(c: NotificationHelper.ChannelState): String = when {
        c.importance < 0 -> "未创建"
        c.importance == NotificationManager.IMPORTANCE_NONE -> "已被关闭 ⚠️ 通知不会显示"
        !c.hasSound && !c.vibrate -> "无声音无震动 ⚠️ 很可能被折叠成静默通知"
        !c.hasSound -> "无声音 ⚠️"
        else -> "重要度 ${c.importance} ✓（有声${if (c.vibrate) "有震" else ""}${if (c.bypassDnd) "可绕过免打扰" else ""}）"
    }

    private fun describe(n: NotificationEntity): String {
        val at = Instant.ofEpochMilli(n.triggerAt).atZone(zone)
        val now = System.currentTimeMillis()
        val leftMin = (n.triggerAt - now) / 60_000
        val left = when {
            leftMin <= 0 -> "已过期"
            leftMin < 60 -> "${leftMin} 分钟后"
            leftMin < 60 * 24 -> "${leftMin / 60} 小时后"
            else -> "${leftMin / (60 * 24)} 天后"
        }
        return "${typeLabel(n.type)} @ ${at.format(timeFmt)}（$left）"
    }

    private fun typeLabel(type: String) = when (type) {
        NotificationType.LIVE_15MIN.name -> "直播提醒"
        NotificationType.NEXT_DAY_REPLAY.name -> "次日录播"
        NotificationType.SAME_DAY_REPLAY.name -> "当日录播"
        else -> type
    }

    private fun ok(v: Boolean) = if (v) "已开启 ✓" else "未开启 ⚠️"
}
