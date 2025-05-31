package com.neel.grepshot.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.neel.grepshot.data.database.AppDatabase
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.model.ScreenshotWithText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenshotRepository(private val context: Context) {
    private val screenshotDao = AppDatabase.getDatabase(context).screenshotDao()
    
    // Get the total count of processed screenshots
    suspend fun getProcessedScreenshotCount(): Int {
        return screenshotDao.getScreenshotCount()
    }
    
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
    
    // Clear all screenshots from the database
    suspend fun clearAllScreenshots() {
        screenshotDao.clearAllScreenshots()
    }
    
    // Get count of processed screenshots from a specific list
    suspend fun getProcessedCount(screenshots: List<ScreenshotItem>): Int {
        var count = 0
        for (screenshot in screenshots) {
            if (isScreenshotProcessed(screenshot.uri)) {
                count++
            }
        }
        return count
    }
    
    // Find unprocessed screenshots from a list
    suspend fun findUnprocessedScreenshots(screenshots: List<ScreenshotItem>): List<ScreenshotItem> {
        val processedUris = screenshotDao.getAllProcessedUris()
        return screenshots.filter { screenshot -> 
            !processedUris.contains(screenshot.uri.toString()) 
        }
    }
    
    // Process only unprocessed screenshots
    suspend fun processNewScreenshots(context: Context, screenshots: List<ScreenshotItem>) {
        val unprocessedScreenshots = findUnprocessedScreenshots(screenshots)
        if (unprocessedScreenshots.isNotEmpty()) {
            processScreenshots(context, unprocessedScreenshots)
            Log.d("ScreenshotRepo", "Processed ${unprocessedScreenshots.size} new screenshots")
        }
    }
    
    // Get all processed URIs from the database
    suspend fun getAllProcessedUris(): List<String> {
        return screenshotDao.getAllProcessedUris()
    }
    
    // Check for new screenshots and return them without loading all into memory
    suspend fun checkForNewScreenshots(context: Context, limit: Int = 20): List<ScreenshotItem> {
        Log.d("ScreenshotRepo", "Checking for new screenshots")
        
        val processedUris = screenshotDao.getAllProcessedUris().toSet()
        val newScreenshots = mutableListOf<ScreenshotItem>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
                arrayOf("%screenshot%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                
                while (cursor.moveToNext() && newScreenshots.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    // Check if this screenshot is already processed
                    if (!processedUris.contains(contentUri.toString())) {
                        newScreenshots.add(ScreenshotItem(contentUri, name))
                    }
                }
            }
            
            Log.d("ScreenshotRepo", "Found ${newScreenshots.size} new screenshots")
            return newScreenshots
            
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Error checking for new screenshots", e)
            return emptyList()
        }
    }
    
    // Export screenshots data as JSON using Storage Access Framework
    suspend fun exportScreenshotsData(directoryUri: Uri): ExportResult = withContext(Dispatchers.IO) {
        // Get all screenshots from database
        val screenshots = getAllScreenshots()
        
        // Create JSON structure
        val jsonArray = JSONArray()
        
        screenshots.forEach { screenshot ->
            val jsonObject = JSONObject().apply {
                put("path", screenshot.uri.toString())
                put("name", screenshot.name)
                put("text", screenshot.extractedText)
            }
            jsonArray.put(jsonObject)
        }
        
        // Create a timestamp for the filename
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "grepshot_export_$timestamp.json"
        
        try {
            // Create DocumentFile from the user-selected directory
            val directory = DocumentFile.fromTreeUri(context, directoryUri)
                ?: throw IllegalArgumentException("Invalid directory URI")
            
            // Create the export file in the selected directory
            val exportFile = directory.createFile("application/json", fileName)
                ?: throw IllegalStateException("Failed to create export file")
            
            // Write JSON data to the file using content resolver
            context.contentResolver.openOutputStream(exportFile.uri)?.use { outputStream ->
                outputStream.write(jsonArray.toString(2).toByteArray())
            } ?: throw IllegalStateException("Failed to open output stream")
            
            Log.d("ScreenshotRepo", "Exported ${screenshots.size} items to ${exportFile.name}")
            
            return@withContext ExportResult(
                absolutePath = exportFile.name ?: fileName,
                itemCount = screenshots.size,
                uri = exportFile.uri
            )
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Export error: ${e.message}", e)
            throw e
        }
    }
    
    // Legacy export method for backwards compatibility (deprecated)
    @Deprecated("Use exportScreenshotsData(Uri) instead")
    suspend fun exportScreenshotsData(): ExportResult = withContext(Dispatchers.IO) {
        // Get all screenshots from database
        val screenshots = getAllScreenshots()
        
        // Create JSON structure
        val jsonArray = JSONArray()
        
        screenshots.forEach { screenshot ->
            val jsonObject = JSONObject().apply {
                put("path", screenshot.uri.toString())
                put("name", screenshot.name)
                put("text", screenshot.extractedText)
            }
            jsonArray.put(jsonObject)
        }
        
        // Create a timestamp for the filename
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        
        // Get the Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        
        // Create the export file
        val exportFile = File(downloadsDir, "grepshot_export_$timestamp.json")
        
        try {
            // Write the JSON data to the file
            exportFile.writeText(jsonArray.toString(2))
            
            // Create content URI for the file to make it available to other apps
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider", 
                exportFile
            )
            
            // Grant temporary permissions for the URI
            context.grantUriPermission(
                context.packageName, 
                fileUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            Log.d("ScreenshotRepo", "Exported ${screenshots.size} items to ${exportFile.absolutePath}")
            
            return@withContext ExportResult(exportFile.absolutePath, screenshots.size, fileUri)
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Export error: ${e.message}", e)
            throw e
        }
    }
    
    data class ExportResult(
        val absolutePath: String,
        val itemCount: Int,
        val uri: Uri
    )
}
