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
                // Start as foreground service immediately
                startForeground(NOTIFICATION_ID, createInitialNotification())
                
                // Reset the active flag and start processing
                isProcessingActive = true
                serviceScope.launch {
                    try {
                        processScreenshots()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in processing", e)
                        updateNotification("Processing failed: ${e.message}", 0, 0, false)
                        stopSelf()
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
    
    private fun createInitialNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screenshot Processing")
            .setContentText("Initializing...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
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
            
            // Record start time for timing
            val startTime = System.currentTimeMillis()
            
            // Get unprocessed screenshots - use applicationContext for stability
            val unprocessedScreenshots = try {
                repository.checkForNewScreenshots(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for new screenshots", e)
                updateNotification("Error checking screenshots: ${e.message}", 0, 0, false)
                return
            }
            
            if (unprocessedScreenshots.isEmpty()) {
                Log.d(TAG, "No unprocessed screenshots found")
                updateNotification("No new screenshots to process", 0, 0, false)
                delay(2000) // Show message briefly
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
                repository.processNewScreenshots(applicationContext, unprocessedScreenshots) { processed, total ->
                    try {
                        // Check if processing is still active before updating
                        if (isProcessingActive) {
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in progress callback", e)
                        // Don't rethrow - just log and continue
                    }
                }
                
                Log.d(TAG, "Repository.processNewScreenshots completed")
                
                // Calculate processing duration
                val endTime = System.currentTimeMillis()
                val durationMs = endTime - startTime
                val durationText = formatDuration(durationMs)
                
                // Update final state
                _processingProgress.value = ProcessingState(
                    total = unprocessedScreenshots.size,
                    processed = unprocessedScreenshots.size,
                    isProcessing = false
                )
                
                if (isProcessingActive) {
                    updateNotification(
                        "Processing complete! Processed ${unprocessedScreenshots.size} screenshots in $durationText",
                        unprocessedScreenshots.size,
                        unprocessedScreenshots.size,
                        false,
                        "Processed ${unprocessedScreenshots.size} images in $durationText"
                    )
                    
                    Log.d(TAG, "Processing completed successfully. Processed ${unprocessedScreenshots.size} screenshots in $durationText")
                    
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
            // Clean up - check if service is still running
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping foreground", e)
            }
            stopSelf()
        }
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    private fun updateNotification(contentText: String, processed: Int, total: Int, ongoing: Boolean, title: String = "Screenshot Processing") {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (notificationManager == null) {
                Log.e(TAG, "NotificationManager is null")
                return
            }
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_camera) // Use system icon to avoid missing resource
                .setOngoing(ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply {
                    if (total > 0) {
                        setProgress(total, processed, false)
                    } else if (ongoing) {
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