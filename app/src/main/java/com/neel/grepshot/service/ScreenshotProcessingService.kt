package com.neel.grepshot.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.neel.grepshot.R
import com.neel.grepshot.data.repository.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        when (intent?.action) {
            "START_PROCESSING" -> {
                // Reset the active flag and start processing
                isProcessingActive = true
                serviceScope.launch {
                    try {
                        processScreenshots()
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
                    "Processing paused",
                    _processingProgress.value.processed,
                    _processingProgress.value.total,
                    false
                )
            }
            "CANCEL_PROCESSING" -> {
                // Just stop the service completely
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    // Public method for the UI to stop processing
    fun stopProcessing() {
        // ...existing code...
    }
    
    private fun createNotificationChannel() {
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
    
    private suspend fun processScreenshots() {
        try {
            Log.d(TAG, "Starting screenshot processing")
            
            // Get unprocessed screenshots
            val unprocessedScreenshots = repository.checkForNewScreenshots(this)
            
            if (unprocessedScreenshots.isEmpty()) {
                Log.d(TAG, "No unprocessed screenshots found")
                updateNotification("No new screenshots to process", 0, 0, false)
                stopProcessing()
                return
            }

            Log.d(TAG, "Found ${unprocessedScreenshots.size} unprocessed screenshots")
            
            // Update processing state
            _processingProgress.value = ProcessingState(
                total = unprocessedScreenshots.size,
                processed = 0,
                isProcessing = true
            )
            
            updateNotification("Processing screenshots...", 0, unprocessedScreenshots.size, true)
            
            // Process screenshots using the repository method with progress callback
            try {
                Log.d(TAG, "About to call repository.processNewScreenshots with ${unprocessedScreenshots.size} screenshots")
                
                // Process screenshots with incremental progress updates
                repository.processNewScreenshots(this, unprocessedScreenshots) { processed, total ->
                    // Update processing state for each screenshot completed
                    _processingProgress.value = ProcessingState(
                        total = total,
                        processed = processed,
                        isProcessing = true
                    )
                    
                    // Update notification with current progress
                    updateNotification(
                        "Processing screenshots... ($processed/$total)",
                        processed,
                        total,
                        true
                    )
                    
                    Log.d(TAG, "Progress update: $processed/$total screenshots processed")
                }
                
                Log.d(TAG, "Repository.processNewScreenshots completed")
                
                // Update final state
                _processingProgress.value = ProcessingState(
                    total = unprocessedScreenshots.size,
                    processed = unprocessedScreenshots.size,
                    isProcessing = false
                )
                
                if (isProcessingActive) {
                    updateNotification(
                        "Processing complete! Processed ${unprocessedScreenshots.size} screenshots",
                        unprocessedScreenshots.size,
                        unprocessedScreenshots.size,
                        false
                    )
                    
                    Log.d(TAG, "Processing completed successfully. Processed ${unprocessedScreenshots.size} screenshots")
                    
                    // Keep notification for a few seconds then remove it
                    delay(3000)
                } else {
                    updateNotification(
                        "Processing paused",
                        unprocessedScreenshots.size,
                        unprocessedScreenshots.size,
                        false
                    )
                    
                    Log.d(TAG, "Processing was paused by user")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing screenshots", e)
                updateNotification("Processing failed: ${e.message}", 0, unprocessedScreenshots.size, false)
                
                // Update state to indicate processing stopped due to error
                _processingProgress.value = ProcessingState(
                    total = unprocessedScreenshots.size,
                    processed = 0,
                    isProcessing = false
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in screenshot processing", e)
            updateNotification("Processing failed: ${e.message}", 0, 0, false)
            
            // Update state to indicate processing stopped due to error
            _processingProgress.value = ProcessingState(
                total = 0,
                processed = 0,
                isProcessing = false
            )
        } finally {
            // Clean up
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    
    private fun updateNotification(contentText: String, processed: Int, total: Int, ongoing: Boolean) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screenshot Processing")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply {
                    if (total > 0) {
                        setProgress(total, processed, false)
                    } else {
                        setProgress(0, 0, true)
                    }
                }
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated: $processed/$total")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
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