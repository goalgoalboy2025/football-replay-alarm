package io.github.dandan.kanqiutixing.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * football-data.org v4 API
 *
 *  baseUrl: https://api.football-data.org/
 *  auth:    Header X-Auth-Token: <token>
 *  free 档：10 req/min，无日上限
 *
 *  关键 endpoint：GET /v4/competitions/{code}/matches?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD
 *  一次返回整个联赛在日期范围内的所有比赛，再在客户端筛出关注球队的场次。
 *  按联赛查 6 个请求即可覆盖 v1 关注的 18 支俱乐部。
 *
 *  ⚠️ 免费档已知不覆盖：
 *    - 各国域内杯赛（足总杯 / 英联杯 / 意大利杯 / 国王杯 / 德国杯 / 法国杯）
 *    - 欧联杯、欧协联（仅欧冠覆盖）
 *    - 南美洲赛事、国家队友谊赛
 *  README "已知限制"小节必须告知用户这些杯赛比赛抓不到。
 *
 *  ⚠️ FOOTBALL_DATA_TOKEN 烧在 BuildConfig 里随 APK 分发。
 *    v1 阶段为了让朋友们直接装就能用，是有意为之。
 *    TODO(v2)：让用户自带 token，否则一旦 APK 流出去免费档配额会被陌生人耗光。
 */
interface FootballDataOrgApi {
    @GET("v4/competitions/{competition}/matches")
    suspend fun getCompetitionMatches(
        @Path("competition") competitionCode: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Header("X-Auth-Token") token: String
    ): MatchesResponse
}

/**
 * v1 公开版只关注五大联赛俱乐部 + 欧冠。
 *   PL  英超   PD  西甲   BL1 德甲   SA  意甲   FL1 法甲   CL  欧冠
 * 一次同步 6 个请求，远在免费档 10 次/分钟限速内。
 * 未来若要加回国家队赛事，把 "EC" / "WC" 补进列表即可。
 */
val FREE_TIER_COMPETITIONS = listOf("PL", "PD", "BL1", "SA", "FL1", "CL")

@JsonClass(generateAdapter = true)
data class MatchesResponse(
    val resultSet: ResultSetDto? = null,
    val matches: List<MatchDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ResultSetDto(
    val count: Int = 0,
    val first: String? = null,
    val last: String? = null
)

@JsonClass(generateAdapter = true)
data class MatchDto(
    val id: Long,
    /** ISO 8601 UTC，如 "2026-05-10T15:30:00Z" */
    val utcDate: String,
    /** SCHEDULED / LIVE / IN_PLAY / PAUSED / FINISHED / POSTPONED / SUSPENDED / CANCELLED */
    val status: String,
    val competition: CompetitionDto,
    val homeTeam: TeamSideDto,
    val awayTeam: TeamSideDto,
    val score: ScoreDto? = null
)

@JsonClass(generateAdapter = true)
data class CompetitionDto(
    val id: Int,
    val name: String,
    val code: String? = null,
    /** LEAGUE / CUP */
    val type: String? = null,
    val emblem: String? = null
)

@JsonClass(generateAdapter = true)
data class TeamSideDto(
    val id: Int?,
    val name: String?,
    val shortName: String? = null,
    val tla: String? = null,
    val crest: String? = null
)

@JsonClass(generateAdapter = true)
data class ScoreDto(
    val winner: String? = null,
    val duration: String? = null,
    val fullTime: ScoreHalfDto? = null,
    val halfTime: ScoreHalfDto? = null
)

@JsonClass(generateAdapter = true)
data class ScoreHalfDto(
    val home: Int? = null,
    val away: Int? = null
)

/**
 * 把 football-data.org 的 competition.type 归一化为 App 内部的三种类型，
 * 用于"正赛 / 资格赛 / 友谊赛"开关过滤。
 *
 * 注意：football-data.org 免费档只覆盖五大联赛 + 欧冠 + 欧洲杯 + 世界杯，
 * 它们的 competition.type 只会是 LEAGUE / CUP，因此在当前数据源下 classify
 * 实际上总会返回 OFFICIAL_CUP——"资格赛 / 友谊赛"开关暂时不会有比赛命中。
 * FRIENDLY / QUALIFICATION 两个分支为将来接入付费档（会返回这些 type）预留。
 */
object CompetitionTypeMapper {
    enum class CompetitionType { OFFICIAL_CUP, QUALIFIERS, FRIENDLY }

    fun classify(type: String?): CompetitionType {
        return when {
            type.equals("FRIENDLY", ignoreCase = true) -> CompetitionType.FRIENDLY
            type.equals("QUALIFICATION", ignoreCase = true) -> CompetitionType.QUALIFIERS
            else -> CompetitionType.OFFICIAL_CUP
        }
    }
}
