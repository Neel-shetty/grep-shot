package com.neel.grepshot.util

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    fun hasReadExternalStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            getRequiredStoragePermission()
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    fun getRequiredStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}