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
        try {
            val createdAt = getFileCreationTime(uri)
            val screenshot = ScreenshotWithText(
                uri = uri, 
                name = name, 
                extractedText = text,
                createdAt = createdAt
            )
            Log.d("ScreenshotRepo", "Attempting to insert screenshot: $name with URI: $uri")
            screenshotDao.insertScreenshot(screenshot)
            Log.d("ScreenshotRepo", "Successfully inserted screenshot: $name with text length: ${text.length}")
            
            // Verify the insertion worked
            val inserted = screenshotDao.getScreenshot(uri.toString())
            if (inserted != null) {
                Log.d("ScreenshotRepo", "Verified screenshot exists in database: ${inserted.name}")
            } else {
                Log.e("ScreenshotRepo", "Failed to verify screenshot insertion: $name")
            }
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Error inserting screenshot $name into database", e)
            throw e
        }
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
        Log.d("ScreenshotRepo", "processNewScreenshots called with ${screenshots.size} screenshots")
        
        // Since checkForNewScreenshots already filters for unprocessed screenshots,
        // we don't need to filter again here. Just process all the provided screenshots.
        if (screenshots.isNotEmpty()) {
            Log.d("ScreenshotRepo", "Processing ${screenshots.size} new screenshots")
            
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            
            screenshots.forEach { screenshot ->
                try {
                    Log.d("ScreenshotRepo", "Processing screenshot: ${screenshot.name}")
                    val inputImage = InputImage.fromFilePath(context, screenshot.uri)
                    
                    val text = withContext(Dispatchers.IO) {
                        suspendCancellableCoroutine<String> { continuation ->
                            recognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    if (continuation.isActive) {
                                        Log.d("ScreenshotRepo", "OCR completed for ${screenshot.name}, text length: ${visionText.text.length}")
                                        continuation.resume(visionText.text) {}
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ScreenshotRepo", "OCR failed for ${screenshot.name}", e)
                                    if (continuation.isActive) {
                                        continuation.resume("") {}
                                    }
                                }
                        }
                    }
                    
                    // Insert into database
                    try {
                        addScreenshotWithText(screenshot.uri, screenshot.name, text)
                        Log.d("ScreenshotRepo", "Successfully saved ${screenshot.name} to database")
                    } catch (dbError: Exception) {
                        Log.e("ScreenshotRepo", "Database insertion failed for ${screenshot.name}", dbError)
                    }
                    
                } catch (e: Exception) {
                    Log.e("ScreenshotRepo", "Error processing ${screenshot.name}", e)
                    // Still try to save with empty text to mark as processed
                }
            }
            
            Log.d("ScreenshotRepo", "Completed processing ${screenshots.size} screenshots")
        } else {
            Log.d("ScreenshotRepo", "No screenshots provided to process")
        }
    }
    
    // Get all processed URIs from the database
    suspend fun getAllProcessedUris(): List<String> {
        return screenshotDao.getAllProcessedUris()
    }
    
    // Check for new screenshots using optimized comparison strategy
    suspend fun checkForNewScreenshots(context: Context, limit: Int = 20): List<ScreenshotItem> {
        Log.d("ScreenshotRepo", "Checking for new screenshots")
        
        try {
            // Get the most recent screenshot from the database
            val mostRecentProcessed = screenshotDao.getMostRecentScreenshot()
            
            // Get the latest screenshot from device
            val latestFromDevice = getLatestScreenshotFromDevice(context)
            
            if (latestFromDevice == null) {
                Log.d("ScreenshotRepo", "No screenshots found on device")
                return emptyList()
            }
            
            // If no screenshots in database, process the latest ones up to limit
            if (mostRecentProcessed == null) {
                Log.d("ScreenshotRepo", "No screenshots in database, getting latest $limit")
                return getLatestScreenshotsFromDevice(context, limit)
            }
            
            // If the latest screenshot from device matches the most recent in database, no new screenshots
            if (latestFromDevice.uri.toString() == mostRecentProcessed.uri.toString()) {
                Log.d("ScreenshotRepo", "No new screenshots found")
                return emptyList()
            }
            
            // Find where the database screenshot appears in the device list using exponential search
            var batchSize = 10
            var foundMatch = false
            var newScreenshots = mutableListOf<ScreenshotItem>()
            
            while (!foundMatch && batchSize <= 100000) { // Cap at 1000 to prevent infinite loop
                Log.d("ScreenshotRepo", "Searching in batch of $batchSize screenshots")
                
                val recentScreenshots = getLatestScreenshotsFromDevice(context, batchSize)
                
                // Look for the most recent processed screenshot in this batch
                val matchIndex = recentScreenshots.indexOfFirst { 
                    it.uri.toString() == mostRecentProcessed.uri.toString()
                }
                
                if (matchIndex != -1) {
                    // Found the match, get all screenshots before this index (newer ones)
                    newScreenshots = recentScreenshots.take(matchIndex).toMutableList()
                    foundMatch = true
                    Log.d("ScreenshotRepo", "Found match at index $matchIndex, ${newScreenshots.size} new screenshots")
                } else if (recentScreenshots.size < batchSize) {
                    // We've reached the end of available screenshots without finding a match
                    // This means all screenshots on device are new
                    newScreenshots = recentScreenshots.toMutableList()
                    foundMatch = true
                    Log.d("ScreenshotRepo", "Reached end of screenshots, all ${newScreenshots.size} are new")
                } else {
                    // Double the batch size and try again
                    batchSize *= 2
                }
            }
            
            if (!foundMatch) {
                Log.w("ScreenshotRepo", "Could not find database match in device screenshots, returning latest $limit")
                newScreenshots = getLatestScreenshotsFromDevice(context, limit).toMutableList()
            }
            
            // Limit the result to the specified limit
            if (newScreenshots.size > limit) {
                newScreenshots = newScreenshots.take(limit).toMutableList()
            }
            
            Log.d("ScreenshotRepo", "Found ${newScreenshots.size} new screenshots")
            return newScreenshots
            
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Error checking for new screenshots", e)
            return emptyList()
        }
    }
    
    // Helper function to get the latest screenshot from device
    private fun getLatestScreenshotFromDevice(context: Context): ScreenshotItem? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
            arrayOf("%screenshot%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                return ScreenshotItem(contentUri, name)
            }
        }
        
        return null
    }
    
    // Helper function to get latest screenshots from device
    private fun getLatestScreenshotsFromDevice(context: Context, count: Int): List<ScreenshotItem> {
        val screenshots = mutableListOf<ScreenshotItem>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?",
            arrayOf("%screenshot%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            
            var itemCount = 0
            while (cursor.moveToNext() && itemCount < count) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                screenshots.add(ScreenshotItem(contentUri, name))
                itemCount++
            }
        }
        
        return screenshots
    }
    
    // Helper function to get file creation time from URI
    private fun getFileCreationTime(uri: Uri): Long {
        return try {
            val projection = arrayOf(MediaStore.Images.Media.DATE_ADDED)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    cursor.getLong(dateAddedColumn) * 1000 // Convert seconds to milliseconds
                } else {
                    0L // Unix epoch fallback
                }
            } ?: 0L // Unix epoch fallback
        } catch (e: Exception) {
            Log.w("ScreenshotRepo", "Could not get file creation time for $uri, using Unix epoch", e)
            0L // Unix epoch fallback
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
    
    // Import screenshots data from JSON file using Storage Access Framework
    suspend fun importScreenshotsData(fileUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            // Read JSON data from the selected file
            val jsonString = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            } ?: throw IllegalStateException("Failed to read file")
            
            // Parse JSON array
            val jsonArray = JSONArray(jsonString)
            var importedCount = 0
            var skippedCount = 0
            var errorCount = 0
            
            // Process each item in the JSON array
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val uriString = jsonObject.getString("path")
                    val name = jsonObject.getString("name")
                    val extractedText = jsonObject.getString("text")
                    
                    val uri = Uri.parse(uriString)
                    
                    // Check if screenshot already exists in database
                    if (!isScreenshotProcessed(uri)) {
                        // Get the actual file creation time from filesystem
                        val createdAt = getFileCreationTime(uri)
                        
                        val screenshot = ScreenshotWithText(
                            uri = uri,
                            name = name,
                            extractedText = extractedText,
                            createdAt = createdAt
                        )
                        screenshotDao.insertScreenshot(screenshot)
                        importedCount++
                        Log.d("ScreenshotRepo", "Imported: $name")
                    } else {
                        skippedCount++
                        Log.d("ScreenshotRepo", "Skipped (already exists): $name")
                    }
                } catch (e: Exception) {
                    errorCount++
                    Log.e("ScreenshotRepo", "Error importing item $i: ${e.message}", e)
                }
            }
            
            Log.d("ScreenshotRepo", "Import completed: $importedCount imported, $skippedCount skipped, $errorCount errors")
            
            return@withContext ImportResult(
                totalItems = jsonArray.length(),
                importedCount = importedCount,
                skippedCount = skippedCount,
                errorCount = errorCount
            )
        } catch (e: Exception) {
            Log.e("ScreenshotRepo", "Import error: ${e.message}", e)
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
    
    data class ImportResult(
        val totalItems: Int,
        val importedCount: Int,
        val skippedCount: Int,
        val errorCount: Int
    )
}
