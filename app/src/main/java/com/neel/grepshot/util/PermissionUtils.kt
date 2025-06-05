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
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }
    }
    
    fun getNotificationPermission(): String {
        return Manifest.permission.POST_NOTIFICATIONS
    }
}