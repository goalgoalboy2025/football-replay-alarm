package io.github.dandan.kanqiutixing.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.dandan.kanqiutixing.data.local.MatchDao
import io.github.dandan.kanqiutixing.data.local.NotificationDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MatchAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var helper: NotificationHelper
    @Inject lateinit var matchDao: MatchDao
    @Inject lateinit var notificationDao: NotificationDao

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_TYPE = "type"
        const val EXTRA_TEST = "is_test"
        private const val TAG = "KanqiuAlarm"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive 触发 action=${intent.action} extras=${intent.extras?.keySet()}")

        // 测试闹钟：不查数据库，直接弹测试通知
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            Log.i(TAG, "  → 测试闹钟分支")
            if (!helper.hasNotificationPermission()) {
                Log.w(TAG, "  ⚠️ POST_NOTIFICATIONS 未授权，notify 会被系统静默丢弃")
            }
            helper.notifyTest(delayed = true)
            return
        }

        val matchId = intent.getLongExtra(EXTRA_MATCH_ID, -1)
        val typeStr = intent.getStringExtra(EXTRA_TYPE)
        if (typeStr == null) {
            Log.w(TAG, "  ✗ 缺少 EXTRA_TYPE，丢弃")
            return
        }
        if (matchId < 0) {
            Log.w(TAG, "  ✗ matchId 非法 ($matchId)，丢弃")
            return
        }

        val type = try {
            NotificationType.valueOf(typeStr)
        } catch (e: Exception) {
            Log.w(TAG, "  ✗ 未知 NotificationType=$typeStr，丢弃", e)
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pendingResult = goAsync()
        scope.launch {
            try {
                val match = matchDao.getById(matchId)
                if (match == null) {
                    Log.w(TAG, "  ✗ matchId=$matchId 在 DB 里找不到（可能被清理或 id 变了）")
                    return@launch
                }
                if (!helper.hasNotificationPermission()) {
                    Log.w(TAG, "  ⚠️ POST_NOTIFICATIONS 未授权，notify 会被系统静默丢弃；" +
                        "match=${match.homeTeamName} vs ${match.awayTeamName}")
                }
                helper.notifyMatchReminder(match, type)
                notificationDao.markFired(matchId, typeStr)
                Log.i(TAG, "  ✓ 已发出 $type 通知：${match.homeTeamName} vs ${match.awayTeamName}")
            } catch (t: Throwable) {
                Log.e(TAG, "  ✗ 处理 alarm 异常", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
