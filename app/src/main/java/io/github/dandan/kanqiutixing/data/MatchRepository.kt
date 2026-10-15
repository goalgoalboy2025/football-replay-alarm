package io.github.dandan.kanqiutixing.data

import io.github.dandan.kanqiutixing.data.config.TeamNameTranslator
import io.github.dandan.kanqiutixing.data.local.MatchDao
import io.github.dandan.kanqiutixing.data.local.MatchEntity
import io.github.dandan.kanqiutixing.data.local.TeamDao
import io.github.dandan.kanqiutixing.data.local.TeamEntity
import io.github.dandan.kanqiutixing.data.remote.CompetitionTypeMapper
import io.github.dandan.kanqiutixing.data.remote.FootballDataOrgApi
import io.github.dandan.kanqiutixing.data.remote.MatchDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchRepository @Inject constructor(
    private val api: FootballDataOrgApi,
    private val matchDao: MatchDao,
    private val teamDao: TeamDao,
    private val settingsRepository: SettingsRepository,
    private val translator: TeamNameTranslator,
    private val tokenProvider: TokenProvider
) {

    fun observeMatches(fromMillis: Long, toMillis: Long): Flow<List<MatchEntity>> =
        matchDao.observeRange(fromMillis, toMillis)

    suspend fun getEnabledTeams(): List<TeamEntity> = teamDao.getEnabled()

    /** 用户已勾选要提醒的比赛 */
    suspend fun getSelectedMatches(): List<MatchEntity> = matchDao.getSelectedMatches()

    /**
     * 把"用户在赛程页确认的勾选集合"写入数据库：先清空所有勾选，再标记选中的。
     * 注意只改 reminderEnabled 标记，不负责排闹钟——排闹钟由调用方做。
     */
    suspend fun applyReminderSelection(selectedIds: Set<Long>) {
        matchDao.clearAllReminderFlags()
        if (selectedIds.isNotEmpty()) {
            matchDao.markReminderEnabled(selectedIds.toList())
        }
    }

    /**
     * 拉取一个联赛在日期范围内的全部比赛，筛出"含关注球队"的场次并写库。
     *
     * 改用按联赛查（而不是逐支球队查）：8 个联赛请求就能覆盖全部关注球队，
     * 远快于原来 14 个逐队请求。
     *
     * @param competitionCode football-data.org 联赛 code，如 "PL" / "CL"
     * @param followedTeamIds 当前启用的关注球队 id 集合
     * @return 该联赛里命中关注球队、且通过类型过滤的比赛实体列表
     */
    suspend fun fetchAndCacheCompetitionMatches(
        competitionCode: String,
        followedTeamIds: Set<Int>,
        dateFrom: String,
        dateTo: String
    ): List<MatchEntity> {
        // 双轨制取 token：用户在设置页填的优先，没填才回退构建期注入的
        val token = tokenProvider.token()
        if (token.isBlank()) throw MissingTokenException()
        val resp = api.getCompetitionMatches(
            competitionCode = competitionCode,
            dateFrom = dateFrom,
            dateTo = dateTo,
            token = token
        )
        val enabledTypes = settingsRepository.enabledCompetitionTypes()
        // 写库前先取出"已勾选"的比赛 id，写新数据时保留用户的选择
        // （upsert 用 REPLACE，不保留就会把 reminderEnabled 重置成 false）
        val selectedIds = matchDao.getReminderEnabledIds().toSet()
        val entities = resp.matches.mapNotNull { m ->
            val homeId = m.homeTeam.id
            val awayId = m.awayTeam.id
            // 只保留含关注球队的比赛；followedTeamId 记成"这场里被关注的那支队"
            val followed = when {
                homeId != null && homeId in followedTeamIds -> homeId
                awayId != null && awayId in followedTeamIds -> awayId
                else -> return@mapNotNull null
            }
            // 比赛类型过滤（正赛 / 资格赛 / 友谊赛 开关）
            val type = CompetitionTypeMapper.classify(m.competition.type).name
            if (type !in enabledTypes) return@mapNotNull null
            m.toEntity(
                followedTeamId = followed,
                translator = translator,
                reminderEnabled = m.id in selectedIds
            )
        }
        if (entities.isNotEmpty()) {
            matchDao.upsertAll(entities)
        }
        return entities
    }

    suspend fun cleanupOld(beforeMillis: Long) {
        matchDao.deleteOlderThan(beforeMillis)
    }
}

private fun MatchDto.toEntity(
    followedTeamId: Int,
    translator: TeamNameTranslator,
    reminderEnabled: Boolean
): MatchEntity {
    val instant: Long = try {
        // football-data.org 的 utcDate 是 ISO 8601 带 Z 后缀（UTC）
        Instant.parse(utcDate).toEpochMilli()
    } catch (e: Exception) {
        try {
            OffsetDateTime.parse(utcDate).toInstant().toEpochMilli()
        } catch (e2: Exception) {
            0L
        }
    }
    return MatchEntity(
        id = id,
        competitionName = competition.name,
        competitionType = CompetitionTypeMapper.classify(competition.type).name,
        leagueId = competition.id,
        homeTeamId = homeTeam.id ?: 0,
        homeTeamName = translator.toZh(homeTeam.name),
        homeTeamLogo = homeTeam.crest,
        awayTeamId = awayTeam.id ?: 0,
        awayTeamName = translator.toZh(awayTeam.name),
        awayTeamLogo = awayTeam.crest,
        utcKickoff = instant,
        status = status,
        homeScore = score?.fullTime?.home,
        awayScore = score?.fullTime?.away,
        followedTeamId = followedTeamId,
        reminderEnabled = reminderEnabled
    )
}
