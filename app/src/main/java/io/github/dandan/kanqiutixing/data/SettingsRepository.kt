package io.github.dandan.kanqiutixing.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLE_OFFICIAL = booleanPreferencesKey("enable_official")
        val ENABLE_QUALIFIERS = booleanPreferencesKey("enable_qualifiers")
        val ENABLE_FRIENDLY = booleanPreferencesKey("enable_friendly")
        val ENABLE_LIVE_REMINDER = booleanPreferencesKey("enable_live_reminder")
        val ENABLE_REPLAY_REMINDER = booleanPreferencesKey("enable_replay_reminder")
        val WORKDAY_HOUR = intPreferencesKey("workday_hour")
        val HOLIDAY_HOUR = intPreferencesKey("holiday_hour")
        /** v1 新增：首启动向导是否已走完。false 时 MainActivity 把用户引到向导，true 直接进赛程页 */
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        /**
         * 用户在设置页自己填的 football-data.org token。
         * 留空时由 [io.github.dandan.kanqiutixing.data.TokenProvider] 回退到构建期注入的值。
         * 只存本机 DataStore，不上传任何服务器。
         */
        val API_TOKEN = stringPreferencesKey("api_token")
        // 注：v0.1 用过 EXACT_ALARM_PROMPTED key 来记"是否弹过精确闹钟权限"，
        // v1 改成向导显式引导后这个标记不再使用。老 user DataStore 里如果还残留也无所谓——
        // 反正没人读它。
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            enableOfficial = p[Keys.ENABLE_OFFICIAL] ?: true,
            enableQualifiers = p[Keys.ENABLE_QUALIFIERS] ?: true,
            enableFriendly = p[Keys.ENABLE_FRIENDLY] ?: true,
            enableLiveReminder = p[Keys.ENABLE_LIVE_REMINDER] ?: true,
            enableReplayReminder = p[Keys.ENABLE_REPLAY_REMINDER] ?: true,
            workdayMorningHour = p[Keys.WORKDAY_HOUR] ?: 7,
            holidayMorningHour = p[Keys.HOLIDAY_HOUR] ?: 8
        )
    }

    /** 首启动向导是否已经走完 */
    suspend fun hasCompletedOnboarding(): Boolean =
        context.dataStore.data.first()[Keys.ONBOARDING_DONE] ?: false

    suspend fun markOnboardingComplete() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    /** 用户填写的数据源 token；未填写时返回空串 */
    suspend fun getApiToken(): String =
        context.dataStore.data.first()[Keys.API_TOKEN]?.trim().orEmpty()

    suspend fun setApiToken(raw: String) {
        context.dataStore.edit { it[Keys.API_TOKEN] = raw.trim() }
    }

    suspend fun current(): Settings = flow.first()

    suspend fun enabledCompetitionTypes(): Set<String> {
        val s = current()
        val out = mutableSetOf<String>()
        if (s.enableOfficial) out.add("OFFICIAL_CUP")
        if (s.enableQualifiers) out.add("QUALIFIERS")
        if (s.enableFriendly) out.add("FRIENDLY")
        return out
    }

    suspend fun update(transform: (Settings) -> Settings) {
        val current = current()
        val next = transform(current)
        context.dataStore.edit { p ->
            p[Keys.ENABLE_OFFICIAL] = next.enableOfficial
            p[Keys.ENABLE_QUALIFIERS] = next.enableQualifiers
            p[Keys.ENABLE_FRIENDLY] = next.enableFriendly
            p[Keys.ENABLE_LIVE_REMINDER] = next.enableLiveReminder
            p[Keys.ENABLE_REPLAY_REMINDER] = next.enableReplayReminder
            p[Keys.WORKDAY_HOUR] = next.workdayMorningHour
            p[Keys.HOLIDAY_HOUR] = next.holidayMorningHour
        }
    }
}

data class Settings(
    val enableOfficial: Boolean,
    val enableQualifiers: Boolean,
    val enableFriendly: Boolean,
    val enableLiveReminder: Boolean,
    val enableReplayReminder: Boolean,
    val workdayMorningHour: Int,    // 0-23
    val holidayMorningHour: Int     // 0-23
)
