package io.github.dandan.kanqiutixing.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记录最近一次同步的结果，供设置页的「提醒健康检查」显示。
 *
 * 为什么要有这个东西：提醒出问题的时候，最关键的三个问题是
 *   1. 赛程到底同步成功了吗？（WorkManager 在国产 ROM 上会被压后，可能根本没跑）
 *   2. 同步到了几场比赛？排了几个闹钟？
 *   3. 上次失败是什么原因？
 * 这三个问题原来只能连电脑抓 logcat 才能回答，用户自己在手机上无从判断，
 * 于是所有故障都表现为同一句话——"提醒没响"。
 * 吧嗒清单（com.bada.app.RemindSync）用 SharedPreferences 存这套状态、
 * 在 App 内直接显示，排查成本从"连电脑抓日志"降到"打开设置页看一眼"，照搬过来。
 */
@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(matchCount: Int, alarmCount: Int, error: String?) {
        prefs.edit()
            .putString(KEY_AT, fmt.format(Date()))
            .putString(KEY_ERR, error ?: "")
            .putInt(KEY_MATCHES, matchCount)
            .putInt(KEY_ALARMS, alarmCount)
            .apply()
    }

    /** 上次同步时间；"从未同步" 表示装上后一次都没成功过 */
    fun lastAt(): String = prefs.getString(KEY_AT, null) ?: "从未同步"

    /** 上次同步的错误，空串表示成功 */
    fun lastError(): String = prefs.getString(KEY_ERR, "") ?: ""

    fun lastMatchCount(): Int = prefs.getInt(KEY_MATCHES, 0)
    fun lastAlarmCount(): Int = prefs.getInt(KEY_ALARMS, 0)

    companion object {
        private const val PREFS = "kanqiu_sync_status"
        private const val KEY_AT = "last_at"
        private const val KEY_ERR = "last_err"
        private const val KEY_MATCHES = "last_matches"
        private const val KEY_ALARMS = "last_alarms"
        private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    }
}
