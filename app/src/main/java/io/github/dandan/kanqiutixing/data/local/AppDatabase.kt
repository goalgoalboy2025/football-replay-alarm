package io.github.dandan.kanqiutixing.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TeamEntity::class, MatchEntity::class, NotificationEntity::class, HolidayEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun matchDao(): MatchDao
    abstract fun notificationDao(): NotificationDao
    abstract fun holidayDao(): HolidayDao
}

/**
 * v1 → v2：match 表新增 reminderEnabled 列（用户勾选的提醒选择）。
 * 用正式迁移而不是 destructiveMigration，避免抹掉用户已有的球队/赛程数据。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `match` ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0"
        )
    }
}
