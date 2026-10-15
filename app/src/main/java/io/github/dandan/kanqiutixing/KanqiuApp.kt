package io.github.dandan.kanqiutixing

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import io.github.dandan.kanqiutixing.data.config.TeamConfigLoader
import io.github.dandan.kanqiutixing.notification.NotificationHelper
import io.github.dandan.kanqiutixing.work.WeeklyScheduleWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KanqiuApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var teamConfigLoader: TeamConfigLoader
    @Inject lateinit var notificationHelper: NotificationHelper

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // 创建通知 channel（必须在任何 notify 之前）
        notificationHelper.ensureChannels()

        // 异步同步球队列表
        appScope.launch {
            teamConfigLoader.syncTeams()
        }

        // 排周更新任务
        WeeklyScheduleWorker.enqueue(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
