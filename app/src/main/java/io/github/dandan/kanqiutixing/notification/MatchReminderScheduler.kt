package io.github.dandan.kanqiutixing.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import io.github.dandan.kanqiutixing.MainActivity
import io.github.dandan.kanqiutixing.data.SettingsRepository
import io.github.dandan.kanqiutixing.data.local.MatchEntity
import io.github.dandan.kanqiutixing.data.local.NotificationDao
import io.github.dandan.kanqiutixing.data.local.NotificationEntity
import io.github.dandan.kanqiutixing.holiday.ChineseHolidayHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 三段制精确闹钟调度
 *  [09:00, 22:00] → 开球前 15 分钟 LIVE_15MIN
 *  (22:00, 24:00) → 次日早晨 NEXT_DAY_REPLAY
 *  [00:00, 09:00) → 当日早晨 SAME_DAY_REPLAY
 *
 *  早晨时间：节假日用 settings.holidayMorningHour（默认 8），工作日用 workdayMorningHour（默认 7）
 */
@Singleton
class MatchReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val holidayHelper: ChineseHolidayHelper,
    private val settingsRepository: SettingsRepository,
    private val notifier: NotificationHelper
) {
    private val alarmManager = context.getSystemService<AlarmManager>()!!
    private val zone = ZoneId.of("Asia/Shanghai")

    suspend fun schedule(match: MatchEntity) {
        val settings = settingsRepository.current()
        val kickoff = Instant.ofEpochMilli(match.utcKickoff).atZone(zone)
        val kickoffTime = kickoff.toLocalTime()
        val nineAM = LocalTime.of(9, 0)
        val tenPM = LocalTime.of(22, 0)
        val matchLabel = "${match.homeTeamName} vs ${match.awayTeamName} @ ${kickoff.format(LOG_FMT)}"

        val (triggerAt, type) = when {
            !kickoffTime.isBefore(nineAM) && !kickoffTime.isAfter(tenPM) -> {
                if (!settings.enableLiveReminder) {
                    Log.i(TAG, "skip [$matchLabel] 直播提醒被关闭")
                    return
                }
                kickoff.minusMinutes(15) to NotificationType.LIVE_15MIN
            }
            kickoffTime.isAfter(tenPM) -> {
                if (!settings.enableReplayReminder) {
                    Log.i(TAG, "skip [$matchLabel] 录播提醒被关闭")
                    return
                }
                val nextDay = kickoff.toLocalDate().plusDays(1)
                val isHoliday = holidayHelper.isHoliday(nextDay)
                val hour = if (isHoliday) settings.holidayMorningHour else settings.workdayMorningHour
                ZonedDateTime.of(nextDay, LocalTime.of(hour, 0), zone) to
                    NotificationType.NEXT_DAY_REPLAY
            }
            else -> {
                if (!settings.enableReplayReminder) {
                    Log.i(TAG, "skip [$matchLabel] 录播提醒被关闭")
                    return
                }
                val sameDay = kickoff.toLocalDate()
                val isHoliday = holidayHelper.isHoliday(sameDay)
                val hour = if (isHoliday) settings.holidayMorningHour else settings.workdayMorningHour
                ZonedDateTime.of(sameDay, LocalTime.of(hour, 0), zone) to
                    NotificationType.SAME_DAY_REPLAY
            }
        }

        val now = ZonedDateTime.now(zone)
        if (triggerAt.isBefore(now)) {
            // 触发点已经过了。分两种情况：
            // - 直播提醒（开球前 15 分钟）：过了就没意义，跳过。
            // - 录播提醒（早晨叫你看录播）：多半是**同步来晚了**（WorkManager 在国产 ROM
            //   上会被压后到 App 进前台才跑），比赛其实早就结束了。这时候补弹一次，
            //   否则用户会觉得"明明设了提醒却没响"。吧嗒清单对到期未响的提醒也是这么补的。
            //   只补 24 小时内的，且只补一次（countFired == 0），避免一觉醒来被轰炸。
            if (type != NotificationType.LIVE_15MIN &&
                !triggerAt.isBefore(now.minusHours(24)) &&
                notificationDao.countFired(match.id, type.name) == 0
            ) {
                notifier.notifyMatchReminder(match, type)
                notificationDao.markFired(match.id, type.name)
                Log.i(TAG, "补弹 [$matchLabel] $type（触发点 ${triggerAt.format(LOG_FMT)} 已过，同步来晚了）")
            } else {
                Log.i(TAG, "skip [$matchLabel] 触发时间 ${triggerAt.format(LOG_FMT)} 已过")
            }
            return
        }

        val requestCode = generateRequestCode(match.id, type)
        val intent = Intent(context, MatchAlarmReceiver::class.java).apply {
            putExtra(MatchAlarmReceiver.EXTRA_MATCH_ID, match.id)
            putExtra(MatchAlarmReceiver.EXTRA_TYPE, type.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerMillis = triggerAt.toInstant().toEpochMilli()
        val alarmClock = setAlarm(triggerMillis, pendingIntent)
        Log.i(TAG, "set $type @ ${triggerAt.format(LOG_FMT)} (alarmClock=$alarmClock, reqCode=$requestCode) [$matchLabel]")

        notificationDao.insert(
            NotificationEntity(
                matchId = match.id,
                type = type.name,
                triggerAt = triggerMillis,
                status = "PENDING",
                alarmRequestCode = requestCode
            )
        )
    }

    suspend fun cancelAllPending() {
        val pending = notificationDao.getAllPending()
        Log.i(TAG, "cancelAllPending: 取消 ${pending.size} 个待触发闹钟")
        pending.forEach { n ->
            val intent = Intent(context, MatchAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, n.alarmRequestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
        notificationDao.markAllPendingCancelled()
    }

    /**
     * 设置一个测试闹钟，[minutesFromNow] 分钟后触发，走和真实提醒完全相同的
     * AlarmManager → MatchAlarmReceiver 路径，用于验证后台保活配置是否生效。
     * 返回触发的绝对时间戳（毫秒），方便 UI 提示用户大概什么时候会响。
     */
    fun scheduleTestAlarm(minutesFromNow: Int = 2): Long {
        val triggerMillis = System.currentTimeMillis() + minutesFromNow * 60_000L
        val intent = Intent(context, MatchAlarmReceiver::class.java).apply {
            putExtra(MatchAlarmReceiver.EXTRA_TEST, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, TEST_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 走和真实提醒完全相同的 setAlarmClock 路径，测试结果才真正代表真实送达能力
        setAlarm(triggerMillis, pendingIntent)
        return triggerMillis
    }

    /**
     * 统一的闹钟登记入口。真实提醒和测试闹钟都走这里。
     *
     * 关键：用 [AlarmManager.setAlarmClock] 而不是 setExactAndAllowWhileIdle。
     * setAlarmClock 会向系统登记一个"用户闹钟"（带 showIntent，点状态栏闹钟图标可打开 App），
     * 是 Android 里唯一一类在 Doze、应用待机分桶、以及华为/荣耀 MagicOS 等厂商
     * "应用冻结/深度睡眠"下仍被保证按时唤醒送达的闹钟——系统时钟 App 用的就是它。
     * setExactAndAllowWhileIdle 名义上能穿透原生 Doze，但在激进 OEM ROM 上经常被冻掉，
     * 这正是"2 分钟测试响、隔夜的凌晨场录播提醒不响"的根因。
     *
     * @return true 表示用了 setAlarmClock（精确）；false 表示没有精确闹钟权限、退回了不精确闹钟。
     */
    private fun setAlarm(triggerMillis: Long, operation: PendingIntent): Boolean {
        // 顺序：setAlarmClock → setExactAndAllowWhileIdle → setAndAllowWhileIdle。
        // 关键是 setAlarmClock 外面要有 try/catch：少数 ROM 对它有限制会抛异常，
        // 不接住的话闹钟就压根没挂上（而且不会有任何日志，表现为"设了却从来不响"）。
        // 这条降级链是从吧嗒清单（com.bada.app.RemindSync.scheduleExact）搬过来的。
        if (canScheduleExactAlarms()) {
            try {
                val showIntent = PendingIntent.getActivity(
                    context, ALARM_CLOCK_SHOW_REQUEST_CODE,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
                    operation
                )
                return true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ setAlarmClock 失败（${e.javaClass.simpleName}），退回 setExactAndAllowWhileIdle")
            }
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, operation
                )
                return true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ setExactAndAllowWhileIdle 也失败，退回不精确闹钟")
            }
        } else {
            Log.w(TAG, "⚠️ 无精确闹钟权限，退回 setAndAllowWhileIdle（提醒可能延迟送达）")
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
        return false
    }

    /** 当前是否已拿到精确闹钟权限（设置页可据此提示用户） */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun generateRequestCode(matchId: Long, type: NotificationType): Int {
        val low = (matchId and 0x1FFF_FFFFL).toInt()
        val flag = type.ordinal
        return (low shl 2) or flag
    }

    companion object {
        /** 测试闹钟的固定 requestCode，与真实比赛的 requestCode 区间不重叠 */
        private const val TEST_ALARM_REQUEST_CODE = Int.MAX_VALUE
        /** setAlarmClock 的 showIntent（点状态栏闹钟图标打开 App）固定 requestCode */
        private const val ALARM_CLOCK_SHOW_REQUEST_CODE = Int.MAX_VALUE - 1
        private const val TAG = "KanqiuScheduler"
        private val LOG_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    }
}
