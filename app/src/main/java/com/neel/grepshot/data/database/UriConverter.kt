package com.neel.grepshot.data.database

import android.net.Uri
import androidx.room.TypeConverter
import androidx.core.net.toUri

class UriConverter {
    @TypeConverter
    fun fromString(value: String?): Uri? {
        return value?.toUri()
    }

    @TypeConverter
    fun uriToString(uri: Uri?): String? {
        return uri?.toString()
    }
}