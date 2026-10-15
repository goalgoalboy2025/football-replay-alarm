package io.github.dandan.kanqiutixing.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.dandan.kanqiutixing.work.RescheduleAlarmsWorker
import io.github.dandan.kanqiutixing.work.WeeklyScheduleWorker

/**
 * 开机 / 应用更新后恢复任务。
 *
 * 这里不再直接做"重排闹钟"的工作，而是入队到 WorkManager：
 * BroadcastReceiver（即使用 goAsync）也只有约 10 秒的执行窗口，而重排闹钟首次
 * 可能触发节假日数据的网络请求（~20s），容易超时被系统杀掉。交给 WorkManager 更稳。
 *
 * 因为不再需要任何注入，这个 receiver 保持为普通 BroadcastReceiver（不加 @AndroidEntryPoint）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED_ACTIONS) return

        // 系统闹钟在开机、应用更新、**以及用户改了系统时间/时区**之后都会失准：
        // 闹钟登记的是绝对时间戳（UTC 毫秒），时区一变，"早晨 7 点"就不再是原来的那个时刻了。
        // 这三种广播原来一个都没监听，跨时区出差或手动调表后提醒会整体错位。
        // 另外 App 自己不会收到系统"闹钟丢失"的通知，只能靠这些广播兜住。
        Log.i(TAG, "收到 $action → 重排闹钟 + 补排周更新")

        // 重启后只恢复"用户已勾选"的比赛提醒（过期的会被 scheduler.schedule 自动跳过）
        RescheduleAlarmsWorker.enqueue(context)
        WeeklyScheduleWorker.enqueue(context)
    }

    companion object {
        private const val TAG = "KanqiuBoot"
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,      // 用户/网络改了时间
            Intent.ACTION_TIMEZONE_CHANGED,  // 时区变了（出差、跨时区）
            Intent.ACTION_DATE_CHANGED       // 跨天
        )
    }
}
