package com.neel.grepshot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.neel.grepshot.MainActivity
import com.neel.grepshot.R
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.repository.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScreenshotProcessingService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: ScreenshotRepository
    
    // Track if processing is currently active and can be stopped
    private var isProcessingActive = true
    
    // Flow for processing progress
    private val _processingProgress = MutableStateFlow(ProcessingState(0, 0, false))
    val processingProgress: StateFlow<ProcessingState> = _processingProgress
    
    // Binder for client communication
    private val binder = LocalBinder()
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screenshot_processing_channel"
        private const val TAG = "ScreenshotService"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): ScreenshotProcessingService = this@ScreenshotProcessingService
    }
    
    override fun onCreate() {
        super.onCreate()
        repository = ScreenshotRepository(applicationContext)
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        // Create and show notification immediately to avoid ANR
        val initialNotification = createNotification(0, 0)
        
        // Use the proper foreground service type for Android 14+ (SDK 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC // Using DATA_SYNC as we're processing data
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
        
        when (intent?.action) {
            "START_PROCESSING" -> {
                // Reset the active flag and start processing
                isProcessingActive = true
                serviceScope.launch {
                    try {
                        processAllScreenshots()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in processing", e)
                    }
                }
            }
            "STOP_PROCESSING" -> {
                // Set the flag to stop processing
                isProcessingActive = false
                // Don't stop the service immediately, let the processing loop handle it
                updateNotification(
                    _processingProgress.value.processed,
                    _processingProgress.value.total
                )
            }
            "CANCEL_PROCESSING" -> {
                // Just stop the service completely
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    // Public method for the UI to stop processing
    fun stopProcessing() {
        isProcessingActive = false
        _processingProgress.value = ProcessingState(
            _processingProgress.value.processed,
            _processingProgress.value.total,
            false,
            "Processing paused"
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screenshot Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for processing screenshots in the background"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
    
    private fun createNotification(processed: Int, total: Int): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        // Use our custom notification icon
        val iconResId = R.drawable.ic_notification
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing Screenshots")
            .setContentText("$processed of $total screenshots processed")
            .setSmallIcon(iconResId)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        if (total > 0) {
            builder.setProgress(total, processed, false)
        } else {
            builder.setProgress(0, 0, true) // Indeterminate progress
        }
        
        return builder.build()
    }
    
    private suspend fun processAllScreenshots() {
        try {
            Log.d(TAG, "Starting screenshot processing")
            _processingProgress.value = ProcessingState(0, 0, true)
            
            // Get screenshots
            val screenshots = loadAllScreenshots()
            val total = screenshots.size
            Log.d(TAG, "Found $total screenshots to process")
            
            _processingProgress.value = ProcessingState(0, total, true)
            updateNotification(0, total)
            
            if (total == 0) {
                _processingProgress.value = ProcessingState(0, 0, false)
                updateNotification(0, 0)
                Log.d(TAG, "No screenshots to process")
                stopSelf()
                return
            }
            
            // Process them in batches of 5 to avoid memory issues
            val batchSize = 5
            var processed = 0
            
            for (i in screenshots.indices step batchSize) {
                // Check if processing has been cancelled
                if (!isProcessingActive) {
                    Log.d(TAG, "Processing cancelled at $processed/$total")
                    _processingProgress.value = ProcessingState(
                        processed, 
                        total, 
                        false,
                        "Processing paused"
                    )
                    updateNotification(processed, total)
                    return
                }
                
                val endIndex = minOf(i + batchSize, screenshots.size)
                val batch = screenshots.subList(i, endIndex)
                
                Log.d(TAG, "Processing batch ${i/batchSize + 1}, size: ${batch.size}")
                repository.processScreenshots(applicationContext, batch)
                
                processed += batch.size
                _processingProgress.value = ProcessingState(processed, total, true)
                updateNotification(processed, total)
                
                Log.d(TAG, "Processed batch: $processed/$total")
            }
            
            // All done
            _processingProgress.value = ProcessingState(total, total, false)
            updateNotification(total, total)
            Log.d(TAG, "All screenshots processed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing screenshots", e)
            _processingProgress.value = ProcessingState(
                _processingProgress.value.processed, 
                _processingProgress.value.total, 
                false,
                e.message ?: "Unknown error"
            )
        } finally {
            // Stop the service when done
            stopSelf()
        }
    }
    
    private fun updateNotification(processed: Int, total: Int) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val notification = createNotification(processed, total)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated: $processed/$total")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
    
    private fun loadAllScreenshots(): List<ScreenshotItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        
        val screenshots = mutableListOf<ScreenshotItem>()
        
        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
                arrayOf("%screenshot%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    screenshots.add(ScreenshotItem(contentUri, name))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading screenshots", e)
        }
        
        // Limit to 20 screenshots for development purposes
        return screenshots.take(20)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel() // Cancel all coroutines when service is destroyed
        super.onDestroy()
    }
    
    // State class for progress tracking
    data class ProcessingState(
        val processed: Int,
        val total: Int,
        val isProcessing: Boolean,
        val error: String? = null
    )
}