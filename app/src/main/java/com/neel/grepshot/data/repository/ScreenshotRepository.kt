package com.neel.grepshot.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.model.ScreenshotWithText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ScreenshotRepository {
    // Map URI string to ScreenshotWithText object
    private val screenshotTextMap = mutableStateOf<Map<String, ScreenshotWithText>>(emptyMap())
    
    // Add a new processed screenshot
    fun addScreenshotWithText(uri: Uri, name: String, text: String) {
        val uriString = uri.toString()
        val updatedMap = screenshotTextMap.value.toMutableMap()
        updatedMap[uriString] = ScreenshotWithText(uri, name, text)
        screenshotTextMap.value = updatedMap
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
    fun searchScreenshots(query: String): List<ScreenshotWithText> {
        if (query.isEmpty()) return emptyList()
        
        return screenshotTextMap.value.values.filter {
            it.extractedText.contains(query, ignoreCase = true)
        }
    }
    
    // Get all processed screenshots
    fun getAllScreenshots(): List<ScreenshotWithText> {
        return screenshotTextMap.value.values.toList()
    }
    
    // Check if a screenshot has been processed
    fun isScreenshotProcessed(uri: Uri): Boolean {
        return screenshotTextMap.value.containsKey(uri.toString())
    }
    
    // Get a specific screenshot
    fun getScreenshot(uri: Uri): ScreenshotWithText? {
        return screenshotTextMap.value[uri.toString()]
    }
}