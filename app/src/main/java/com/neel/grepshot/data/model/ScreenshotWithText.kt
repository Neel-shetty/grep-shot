package com.neel.grepshot.data.model

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.neel.grepshot.data.database.UriConverter

@Entity(tableName = "screenshots")
data class ScreenshotWithText(
    @PrimaryKey
    @TypeConverters(UriConverter::class)
    val uri: Uri,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "extracted_text")
    val extractedText: String
)