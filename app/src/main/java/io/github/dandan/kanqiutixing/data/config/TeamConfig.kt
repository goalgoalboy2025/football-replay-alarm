package io.github.dandan.kanqiutixing.data.config

import android.content.Context
import io.github.dandan.kanqiutixing.BuildConfig
import io.github.dandan.kanqiutixing.data.local.TeamDao
import io.github.dandan.kanqiutixing.data.local.TeamEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class TeamConfig(
    val version: Int,
    val lastUpdated: String,
    val note: String? = null,
    val teams: List<TeamConfigItem>
)

@JsonClass(generateAdapter = true)
data class TeamConfigItem(
    val id: Int,
    val type: String,         // "CLUB" / "NATIONAL"
    val nameZh: String,
    val nameEn: String,
    val country: String,
    val league: String
)

/**
 * 球队配置加载器
 *  1. 启动时优先尝试拉取 BuildConfig.TEAMS_REMOTE_URL（如果非空）
 *  2. 失败则读取 assets/teams.json（保底）
 *  3. 与本地数据库 diff 后同步：增 / 改 / 删
 *
 * 这样实现"不重写代码即可调整球队列表"：
 *   方案A：直接修改 assets/teams.json + 重新打包
 *   方案B：把 teams.json 上传到一个公开 URL，App 自动拉取（需配 TEAMS_REMOTE_URL）
 */
@Singleton
class TeamConfigLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val teamDao: TeamDao,
    private val httpClient: OkHttpClient
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(TeamConfig::class.java)

    /** 在 App 启动时调用一次（KanqiuApp.onCreate 协程里） */
    suspend fun syncTeams(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig()
            val newSet = config.teams.associateBy { it.id }

            val existing = teamDao.getAllOnce().associateBy { it.id }

            // 删除：本地有、新版没有
            val toDelete = existing.keys - newSet.keys
            if (toDelete.isNotEmpty()) teamDao.deleteByIds(toDelete.toList())

            // 增改：upsert。
            // - 已存在的球队：保留用户在设置里设置的 enabled，否则用户关掉的下次同步会被静默打开
            // - 新增的球队：默认 enabled=false，让用户在首启动向导 / 设置页里显式勾选。
            //   v0.1 时这里写的是 ?: true（默认全开），v1 改成 ?: false 是为了配合
            //   "首启动让用户选球队"的产品逻辑，避免装上 APK 就被 18 支球队全部刷屏。
            val entities = newSet.values.map { c ->
                val preservedEnabled = existing[c.id]?.enabled ?: false
                TeamEntity(
                    id = c.id,
                    type = c.type,
                    nameZh = c.nameZh,
                    nameEn = c.nameEn,
                    country = c.country,
                    league = c.league,
                    // football-data.org 没有按队拉队徽的稳定 URL；
                    // 队徽在每场比赛响应的 homeTeam.crest / awayTeam.crest 里返回，
                    // 此处先保留旧值（如果之前同步过），否则 null
                    crestUrl = existing[c.id]?.crestUrl,
                    enabled = preservedEnabled
                )
            }
            teamDao.upsertAll(entities)

            Result.success(entities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadConfig(): TeamConfig {
        val remoteUrl = BuildConfig.TEAMS_REMOTE_URL
        if (remoteUrl.isNotBlank()) {
            try {
                val req = Request.Builder().url(remoteUrl).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            return adapter.fromJson(body)
                                ?: throw IllegalStateException("Remote teams.json parse failed")
                        }
                    }
                }
            } catch (_: Exception) {
                // 远程失败 → 落到本地
            }
        }

        // 读 assets/teams.json
        context.assets.open("teams.json").bufferedReader().use { reader ->
            val text = reader.readText()
            return adapter.fromJson(text)
                ?: throw IllegalStateException("Local teams.json parse failed")
        }
    }
}
