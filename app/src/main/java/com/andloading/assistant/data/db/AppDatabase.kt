package com.andloading.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.andloading.assistant.data.model.AgentLogEntity
import com.andloading.assistant.data.model.MemoryEntity
import com.andloading.assistant.data.model.MessageEntity
import com.andloading.assistant.data.model.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        AgentLogEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentLogDao(): AgentLogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shortcuts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phrase` TEXT NOT NULL, " +
                        "`actionType` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shortcuts_phrase` ON `shortcuts` (`phrase`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `auto_rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`sourcePackage` TEXT NOT NULL, " +
                        "`titleKeyword` TEXT NOT NULL, " +
                        "`contentKeyword` TEXT NOT NULL, " +
                        "`promptTemplate` TEXT NOT NULL, " +
                        "`cooldownSeconds` INTEGER NOT NULL, " +
                        "`dailyLimit` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_auto_rules_sourcePackage` ON `auto_rules` (`sourcePackage`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `auto_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ruleId` INTEGER NOT NULL, " +
                        "`ruleName` TEXT NOT NULL, " +
                        "`triggerSummary` TEXT NOT NULL, " +
                        "`detail` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_auto_logs_ruleId` ON `auto_logs` (`ruleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_auto_logs_createdAt` ON `auto_logs` (`createdAt`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `shortcuts`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `memories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_createdAt` ON `memories` (`createdAt`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `auto_rules`")
                db.execSQL("DROP TABLE IF EXISTS `auto_logs`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trigger` TEXT NOT NULL, " +
                        "`sourcePackage` TEXT NOT NULL, " +
                        "`triggerSummary` TEXT NOT NULL, " +
                        "`detail` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_logs_sourcePackage` ON `agent_logs` (`sourcePackage`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_logs_createdAt` ON `agent_logs` (`createdAt`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "Assistant.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
