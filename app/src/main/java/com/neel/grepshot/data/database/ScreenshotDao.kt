package com.neel.grepshot.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neel.grepshot.data.model.ScreenshotWithText
import android.net.Uri

@Dao
interface ScreenshotDao {
    @Query("SELECT * FROM screenshots ORDER BY created_at DESC")
    suspend fun getAllScreenshots(): List<ScreenshotWithText>
    
    @Query("SELECT COUNT(*) FROM screenshots")
    suspend fun getScreenshotCount(): Int
    
    @Query("SELECT * FROM screenshots WHERE extracted_text LIKE '%' || :query || '%' ORDER BY created_at DESC")
    suspend fun searchScreenshots(query: String): List<ScreenshotWithText>
    
    @Query("SELECT * FROM screenshots WHERE uri = :uriString ORDER BY created_at DESC LIMIT 1")
    suspend fun getScreenshot(uriString: String): ScreenshotWithText?
    
    @Query("SELECT EXISTS(SELECT 1 FROM screenshots WHERE uri = :uriString)")
    suspend fun isScreenshotProcessed(uriString: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenshot(screenshot: ScreenshotWithText)
    
    @Delete
    suspend fun deleteScreenshot(screenshot: ScreenshotWithText)
    
    @Query("DELETE FROM screenshots")
    suspend fun clearAllScreenshots()
    
    @Query("SELECT uri FROM screenshots")
    suspend fun getAllProcessedUris(): List<String>
    
    @Query("SELECT * FROM screenshots ORDER BY created_at DESC LIMIT 1")
    suspend fun getMostRecentScreenshot(): ScreenshotWithText?
}