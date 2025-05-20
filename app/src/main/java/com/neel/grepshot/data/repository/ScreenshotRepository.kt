package com.neel.grepshot.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.neel.grepshot.data.database.AppDatabase
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.model.ScreenshotWithText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ScreenshotRepository(context: Context) {
    private val screenshotDao = AppDatabase.getDatabase(context).screenshotDao()
    
    // Add a new processed screenshot
    suspend fun addScreenshotWithText(uri: Uri, name: String, text: String) {
        val screenshot = ScreenshotWithText(uri, name, text)
        screenshotDao.insertScreenshot(screenshot)
        Log.d("ScreenshotRepo", "Added text for $name: ${text.take(50)}...")
    }
    
    // Process multiple screenshots at once
    suspend fun processScreenshots(context: Context, screenshots: List<ScreenshotItem>) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        screenshots.forEach { screenshot ->
            if (!isScreenshotProcessed(screenshot.uri)) {
                try {
                    val inputImage = InputImage.fromFilePath(context, screenshot.uri)
                    
                    val text = withContext(Dispatchers.IO) {
                        suspendCancellableCoroutine<String> { continuation ->
                            recognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    if (continuation.isActive) {
                                        continuation.resume(visionText.text) {}
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("TextRecognition", "Error processing batch image", e)
                                    if (continuation.isActive) {
                                        continuation.resume("") {}
                                    }
                                }
                        }
                    }
                    
                    addScreenshotWithText(screenshot.uri, screenshot.name, text)
                    Log.d("ScreenshotRepo", "Batch processed: ${screenshot.name}")
                } catch (e: Exception) {
                    Log.e("TextRecognition", "Error creating InputImage for ${screenshot.name}", e)
                    addScreenshotWithText(screenshot.uri, screenshot.name, "")
                }
            }
        }
    }
    
    // Search for screenshots containing the query text
    suspend fun searchScreenshots(query: String): List<ScreenshotWithText> {
        if (query.isEmpty()) return emptyList()
        return screenshotDao.searchScreenshots(query)
    }
    
    // Get all processed screenshots
    suspend fun getAllScreenshots(): List<ScreenshotWithText> {
        return screenshotDao.getAllScreenshots()
    }
    
    // Check if a screenshot has been processed
    suspend fun isScreenshotProcessed(uri: Uri): Boolean {
        return screenshotDao.isScreenshotProcessed(uri.toString())
    }
    
    // Get a specific screenshot
    suspend fun getScreenshot(uri: Uri): ScreenshotWithText? {
        return screenshotDao.getScreenshot(uri.toString())
    }
}