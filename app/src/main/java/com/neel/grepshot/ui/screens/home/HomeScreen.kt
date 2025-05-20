package com.neel.grepshot.ui.screens.home

import android.Manifest
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.repository.ScreenshotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    screenshots: List<ScreenshotItem>,
    onScreenshotsLoaded: (List<ScreenshotItem>) -> Unit,
    onScreenshotClick: (ScreenshotItem) -> Unit,
    repository: ScreenshotRepository,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) 
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // Track processing progress
    var processedCount by remember { mutableStateOf(0) }
    val totalScreenshots = screenshots.size
    
    // Update processed count periodically for the visible screenshots
    LaunchedEffect(screenshots) {
        while(true) {
            try {
                processedCount = repository.getProcessedCount(screenshots)
                delay(1000) // Update every second
            } catch (e: Exception) {
                Log.e("ScreenshotsApp", "Error getting processed count", e)
                break
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
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
                val screenShotsList = mutableListOf<ScreenshotItem>()
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    screenShotsList.add(ScreenshotItem(contentUri, name))
                    
                    // Limit to 20 images
                    if (screenShotsList.size >= 20) break
                }
                onScreenshotsLoaded(screenShotsList)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GrepShot") },
                actions = {
                    // Add search button to the top bar
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Development testing FAB
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.clearAllScreenshots()
                            Toast.makeText(
                                context,
                                "Database cleared for testing",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Database (Testing)"
                    )
                }
                
                // Main FAB for processing
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.processScreenshots(context, screenshots)
                            Toast.makeText(
                                context,
                                "Started processing screenshots",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Process Screenshots"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Permission needed to access screenshots")
                    Button(
                        modifier = Modifier.padding(top = 16.dp),
                        onClick = {
                            permissionLauncher.launch(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                            )
                        }
                    ) {
                        Text("Request Permission")
                    }
                }
            } else {
                if (screenshots.isEmpty()) {
                    Text(
                        text = "No screenshots found",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    // Always show progress indicator
                    if (totalScreenshots > 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Processing screenshots", 
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("$processedCount/$totalScreenshots", 
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            
                            LinearProgressIndicator(
                                progress = (processedCount.toFloat() / totalScreenshots).coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(screenshots) { screenshot ->
                            ScreenshotCard(
                                screenshot = screenshot,
                                onClick = { onScreenshotClick(screenshot) }
                            )
                        }
                    }
                }
            }
        }
    }
}