package com.neel.grepshot.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.neel.grepshot.data.model.ScreenshotWithText

@Database(entities = [ScreenshotWithText::class], version = 1, exportSchema = false)
@TypeConverters(UriConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screenshot_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}