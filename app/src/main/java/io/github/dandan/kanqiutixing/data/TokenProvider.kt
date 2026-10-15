package io.github.dandan.kanqiutixing.data

import io.github.dandan.kanqiutixing.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** token 来源，设置页用它告诉用户"当前生效的是哪一个" */
enum class TokenSource {
    /** 用户在 App 内设置页填写的（只存本机 DataStore） */
    USER,

    /** 构建期从 local.properties 注入的 BuildConfig 值（会随 dex 分发） */
    BUILD,

    /** 两处都没有 —— 请求必然 403，设置页应提示去填 */
    NONE
}

data class ResolvedToken(
    val value: String,
    val source: TokenSource
) {
    val isBlank: Boolean get() = value.isBlank()
}

/**
 * 决定每次请求用哪个 football-data.org token —— **双轨制**。
 *
 * | 场景 | 用哪个 | 目的 |
 * | --- | --- | --- |
 * | 开发者本地 / CI | `local.properties` → `BuildConfig.FOOTBALL_DATA_TOKEN` | clone 下来就能跑，不用先注册账号 |
 * | 公开发布的 APK | 用户在设置页填自己的 | token 不进仓库、不进 dex，配额归用户自己 |
 *
 * 优先级：**用户填写的 > 构建注入的**。
 *
 * ⚠️ 打对外分发的 release 包时，`local.properties` 里的 `FOOTBALL_DATA_TOKEN`
 * 必须留空，否则 token 会明文留在 dex 里（app/build.gradle.kts 对此有告警）。
 * 本 App 自己填 token 的入口在 设置页 → 数据源。
 */
@Singleton
class TokenProvider @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    /** 实际用于请求的 token；两者都为空时返回空串（调用方应给出明确提示而不是盲目发请求） */
    suspend fun token(): String = resolve().value

    suspend fun resolve(): ResolvedToken {
        val user = settingsRepository.getApiToken()
        if (user.isNotBlank()) return ResolvedToken(user, TokenSource.USER)
        val build = BuildConfig.FOOTBALL_DATA_TOKEN.trim()
        if (build.isNotBlank()) return ResolvedToken(build, TokenSource.BUILD)
        return ResolvedToken("", TokenSource.NONE)
    }
}

/**
 * 两个来源都没有 token 时抛出。
 *
 * 单独定义而不是直接抛 IllegalStateException，是为了让调度方能把它和「网络异常」区分开：
 * 空 token 重试一万次也不会成功，应该立即失败并提示用户去填，而不是走 WorkManager 的重试退避。
 */
class MissingTokenException :
    IllegalStateException("未配置 football-data.org token：请在 设置 → 数据源 填入你自己的 token")
