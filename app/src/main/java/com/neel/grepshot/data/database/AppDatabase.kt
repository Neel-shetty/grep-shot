package com.neel.grepshot.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neel.grepshot.data.model.ScreenshotWithText

@Database(entities = [ScreenshotWithText::class], version = 3, exportSchema = false)
@TypeConverters(UriConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create indices for better performance
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_screenshots_uri` ON `screenshots` (`uri`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_screenshots_extracted_text` ON `screenshots` (`extracted_text`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add created_at column with default value of current timestamp
                database.execSQL("ALTER TABLE screenshots ADD COLUMN created_at INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screenshot_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}