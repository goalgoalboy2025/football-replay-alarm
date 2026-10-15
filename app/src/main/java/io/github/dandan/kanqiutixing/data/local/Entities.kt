package io.github.dandan.kanqiutixing.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "team")
data class TeamEntity(
    @PrimaryKey val id: Int,
    val type: String,            // CLUB / NATIONAL
    val nameZh: String,
    val nameEn: String,
    val country: String,
    val league: String,
    val crestUrl: String?,
    val enabled: Boolean
)

@Entity(tableName = "match")
data class MatchEntity(
    @PrimaryKey val id: Long,
    val competitionName: String,
    val competitionType: String,    // OFFICIAL_CUP / QUALIFIERS / FRIENDLY
    val leagueId: Int,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamLogo: String?,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamLogo: String?,
    val utcKickoff: Long,           // 毫秒
    val status: String,             // NS / FT / PST / CANC / LIVE 等
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    /** 关注的本方球队 ID，用于通知文案 */
    val followedTeamId: Int,
    /**
     * 用户是否已勾选"要这场比赛的提醒"。默认 false（不提醒），由用户在赛程页勾选+确认后置 true。
     * defaultValue="0" 必须和 MIGRATION_1_2 里 ADD COLUMN 的 DEFAULT 0 一致，否则 Room 校验 schema 会崩。
     */
    @ColumnInfo(defaultValue = "0")
    val reminderEnabled: Boolean = false
)

@Entity(tableName = "notification")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val matchId: Long,
    val type: String,               // LIVE_15MIN / NEXT_DAY_REPLAY / SAME_DAY_REPLAY
    val triggerAt: Long,
    val status: String,             // PENDING / FIRED / CANCELLED
    val alarmRequestCode: Int
)

@Entity(tableName = "holiday")
data class HolidayEntity(
    @PrimaryKey val date: String,   // yyyy-MM-dd
    val year: Int,
    val isHoliday: Boolean,
    val name: String
)
