package io.github.dandan.kanqiutixing.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM team ORDER BY type DESC, id ASC")
    fun observeAll(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM team WHERE enabled = 1")
    suspend fun getEnabled(): List<TeamEntity>

    @Query("SELECT * FROM team")
    suspend fun getAllOnce(): List<TeamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TeamEntity>)

    @Query("DELETE FROM team WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("UPDATE team SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM match WHERE utcKickoff BETWEEN :from AND :to ORDER BY utcKickoff ASC")
    fun observeRange(from: Long, to: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM match WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MatchEntity?

    @Query("SELECT * FROM match WHERE utcKickoff > :now")
    suspend fun getAllAfter(now: Long): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MatchEntity>)

    @Query("DELETE FROM match WHERE utcKickoff < :before")
    suspend fun deleteOlderThan(before: Long)

    // ---- 提醒选择相关 ----

    /** 用户已勾选要提醒的比赛 */
    @Query("SELECT * FROM match WHERE reminderEnabled = 1 ORDER BY utcKickoff ASC")
    suspend fun getSelectedMatches(): List<MatchEntity>

    /** 当前已勾选的比赛 id，用于同步写库时保留用户的选择 */
    @Query("SELECT id FROM match WHERE reminderEnabled = 1")
    suspend fun getReminderEnabledIds(): List<Long>

    /** 清空所有比赛的勾选状态 */
    @Query("UPDATE match SET reminderEnabled = 0")
    suspend fun clearAllReminderFlags()

    /** 把指定 id 的比赛标记为已选 */
    @Query("UPDATE match SET reminderEnabled = 1 WHERE id IN (:ids)")
    suspend fun markReminderEnabled(ids: List<Long>)
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NotificationEntity): Long

    @Query("SELECT * FROM notification WHERE status = 'PENDING'")
    suspend fun getAllPending(): List<NotificationEntity>

    @Query("UPDATE notification SET status = 'CANCELLED' WHERE status = 'PENDING'")
    suspend fun markAllPendingCancelled()

    @Query("UPDATE notification SET status = 'FIRED' WHERE matchId = :matchId AND type = :type")
    suspend fun markFired(matchId: Long, type: String)

    /** 这条提醒是不是已经响过了（用于判断要不要补弹，避免重复轰炸） */
    @Query("SELECT COUNT(*) FROM notification WHERE matchId = :matchId AND type = :type AND status = 'FIRED'")
    suspend fun countFired(matchId: Long, type: String): Int

    /** 待触发的提醒，按触发时间升序（诊断页要列出最近几个） */
    @Query("SELECT * FROM notification WHERE status = 'PENDING' ORDER BY triggerAt ASC")
    suspend fun getAllPendingSorted(): List<NotificationEntity>
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holiday WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): HolidayEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM holiday WHERE year = :year LIMIT 1)")
    suspend fun hasYear(year: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HolidayEntity>)
}
