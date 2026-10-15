package io.github.dandan.kanqiutixing.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.github.dandan.kanqiutixing.MainActivity
import io.github.dandan.kanqiutixing.R
import io.github.dandan.kanqiutixing.data.local.MatchEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nm = context.getSystemService<NotificationManager>()!!

    companion object {
        const val CHANNEL_LIVE = "channel_live_reminder"
        const val CHANNEL_REPLAY = "channel_replay_reminder"
        const val CHANNEL_DIGEST = "channel_weekly_digest"
        const val NOTIF_ID_DIGEST = 1001
        const val NOTIF_ID_TEST = 1002
        private const val TAG = "KanqiuNotify"
    }

    /** 诊断用：一个通知渠道的实际状态 */
    data class ChannelState(
        val id: String,
        val label: String,
        val importance: Int,
        val hasSound: Boolean,
        val vibrate: Boolean,
        val bypassDnd: Boolean
    )

    /**
     * 诊断用：读出三个渠道**系统里实际的状态**。
     * 用户或 ROM 可能把渠道关掉（IMPORTANCE_NONE）或掐掉声音，
     * 那种情况下 notify() 照样调用、照样打日志，但用户什么都看不到——
     * 只看日志会误判成"App 没响"，其实是被系统静音了。
     */
    fun channelStates(): List<ChannelState> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        return listOf(
            CHANNEL_LIVE to "直播提醒",
            CHANNEL_REPLAY to "录播提醒",
            CHANNEL_DIGEST to "周赛程汇总"
        ).map { (id, label) ->
            val c = nm.getNotificationChannel(id)
            if (c == null) ChannelState(id, label, -1, false, false, false)
            else ChannelState(id, label, c.importance, c.sound != null, c.shouldVibrate(), c.canBypassDnd())
        }
    }

    /**
     * Android 13+ 需要 POST_NOTIFICATIONS 运行时权限，没授时 notify() 会被系统静默丢弃。
     * 调用方据此决定是否给用户"权限未开"之类的提示，而不是误报"已发送"。
     */
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * 创建（或修补）通知渠道。
     *
     * 关于「为什么要把通知做得这么吵」——这条经验来自吧嗒清单（com.bada.app）的真机排查：
     * 那边曾出现过一次「后台不响」，服务端回执却显示手机其实准时弹了通知，用户以为没响。
     * 也就是说**闹钟是对的，感知不到才是真问题**。国产 ROM 会把没声音、没震动、
     * 锁屏不可见的通知直接吞掉或折叠进「静默通知」。所以下面每一项都是必需的，
     * 不要为了「简洁」删掉：
     * - 显式铃声（不能指望渠道默认值，荣耀上经常是空的）
     * - 震动
     * - 锁屏可见（VISIBILITY_PUBLIC）
     * - CATEGORY_ALARM（让系统按闹钟对待，Doze 下优先级更高）
     * - IMPORTANCE_HIGH + 绕过免打扰
     *
     * 另外：
     * - 录播提醒原来用的是 IMPORTANCE_DEFAULT，是三个渠道里最安静的，而它恰恰是
     *   最容易「不响」的场景（早晨睡梦中）→ 统一提到 IMPORTANCE_HIGH。
     * - 渠道一旦建成就改不了重要度，但声音/震动/免打扰可以改。老版本装的包建的渠道
     *   可能没声音，这里每次启动都补一遍，避免「装了新包还是静默通知」。
     */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        listOf(
            buildChannel(CHANNEL_LIVE, "直播提醒", "比赛开球前 15 分钟弹通知"),
            buildChannel(CHANNEL_REPLAY, "录播提醒", "深夜/凌晨比赛次日（或当日）早晨提醒看录播"),
            buildChannel(CHANNEL_DIGEST, "周赛程汇总", "每周六早晨推送本周关注球队赛程")
        ).forEach { nm.createNotificationChannel(it) }
    }

    private fun buildChannel(id: String, name: String, desc: String): NotificationChannel {
        val existing = nm.getNotificationChannel(id)
        val ch = existing
            ?: NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        ch.description = desc
        if (ch.sound == null) ch.setSound(alarmSoundUri(), alarmAudioAttributes())
        if (!ch.shouldVibrate()) ch.enableVibration(true)
        ch.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        // 免打扰时也放行——提醒就是要打扰到人，否则等于没响
        ch.setBypassDnd(true)
        return ch
    }

    private fun alarmSoundUri() =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun alarmAudioAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** 所有提醒通知的公共部分：响 + 震 + 锁屏可见 + 按闹钟对待 */
    private fun baseBuilder(channelId: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_football)
            .setSound(alarmSoundUri())
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)

    fun notifyMatchReminder(match: MatchEntity, type: NotificationType) {
        val zone = ZoneId.of("Asia/Shanghai")
        val kickoff = Instant.ofEpochMilli(match.utcKickoff).atZone(zone)
        val timeStr = kickoff.format(DateTimeFormatter.ofPattern("HH:mm"))

        val matchTitle = "${match.homeTeamName} vs ${match.awayTeamName}"
        val (channelId, title, body) = when (type) {
            NotificationType.LIVE_15MIN -> Triple(
                CHANNEL_LIVE,
                "15 分钟后开球：$matchTitle",
                "${match.competitionName} · $timeStr 开球"
            )
            NotificationType.NEXT_DAY_REPLAY -> Triple(
                CHANNEL_REPLAY,
                "录播提醒：$matchTitle",
                "${match.competitionName} · 昨晚 $timeStr 开球的比赛已结束，可以看录播啦"
            )
            NotificationType.SAME_DAY_REPLAY -> Triple(
                CHANNEL_REPLAY,
                "录播提醒：$matchTitle",
                "${match.competitionName} · 凌晨 $timeStr 开球的比赛已结束，可以看录播啦"
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("matchId", match.id)
        }
        val pi = PendingIntent.getActivity(
            context, match.id.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = baseBuilder(channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .build()

        if (!hasNotificationPermission()) {
            Log.w(TAG, "notifyMatchReminder: POST_NOTIFICATIONS 未授权，系统会静默丢弃 " +
                "match=${match.homeTeamName} vs ${match.awayTeamName}")
        }
        NotificationManagerCompat.from(context).notify(match.id.toInt(), notif)
        Log.i(TAG, "notifyMatchReminder posted id=${match.id} type=$type")
    }

    fun notifyWeeklyDigest(matchCount: Int) {
        val openIntent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 周汇总不需要吵：去掉震动、降低优先级，但保留锁屏可见
        val notif = baseBuilder(CHANNEL_DIGEST)
            .setContentTitle("本周赛程已更新")
            .setContentText("关注球队本周共有 $matchCount 场比赛")
            .setContentIntent(pi)
            .setVibrate(null)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DIGEST, notif)
    }

    /**
     * 测试通知。
     *  delayed = false：设置页点"立即测试"时直接弹，验证通知权限 + 通知渠道
     *  delayed = true ：由 MatchAlarmReceiver 在 2 分钟后触发，验证 AlarmManager + 后台保活
     */
    fun notifyTest(delayed: Boolean) {
        val title = if (delayed) "✅ 定时提醒测试成功" else "✅ 即时通知测试成功"
        val body = if (delayed) {
            "AlarmManager 和后台保活都正常工作，真实比赛提醒也会准时弹出。"
        } else {
            "通知权限和通知渠道正常。接下来可以试试「2 分钟后提醒测试」验证后台保活。"
        }
        val openIntent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = baseBuilder(CHANNEL_LIVE)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_TEST, notif)
    }
}
