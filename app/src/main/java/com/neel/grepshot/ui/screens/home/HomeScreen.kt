package com.neel.grepshot.ui.screens.home

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.repository.ScreenshotRepository
import com.neel.grepshot.service.ScreenshotProcessingService
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
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Service connection states
    var processingService by remember { mutableStateOf<ScreenshotProcessingService?>(null) }
    var serviceConnected by remember { mutableStateOf(false) }
    
    // Track processing progress from service only
    var serviceProcessingState by remember { mutableStateOf(ScreenshotProcessingService.ProcessingState(0, 0, false)) }
    
    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as ScreenshotProcessingService.LocalBinder
                processingService = binder.getService()
                serviceConnected = true
                
                // Start collecting progress updates
                coroutineScope.launch {
                    processingService?.processingProgress?.collect { state ->
                        serviceProcessingState = state
                    }
                }
            }
            
            override fun onServiceDisconnected(name: ComponentName?) {
                processingService = null
                serviceConnected = false
            }
        }
    }
    
    // Connect to service when the screen is active
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                // Bind to service
                Intent(context, ScreenshotProcessingService::class.java).also { intent ->
                    context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                // Unbind from service
                if (serviceConnected) {
                    context.unbindService(serviceConnection)
                    serviceConnected = false
                }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (serviceConnected) {
                context.unbindService(serviceConnection)
            }
        }
    }
    
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
                
                // Removed automatic processing on launch - will only process when button is clicked
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
            // Development testing FAB - only keeping this one for clearing the database
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
                    // Service processing state display
                    if (serviceProcessingState.total > 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Background processing", 
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("${serviceProcessingState.processed}/${serviceProcessingState.total}", 
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            
                            LinearProgressIndicator(
                                progress = (serviceProcessingState.processed.toFloat() / serviceProcessingState.total).coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                    
                    // Buttons for background processing control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Button to start background processing service
                        Button(
                            onClick = {
                                try {
                                    // Start the foreground service
                                    val intent = Intent(context, ScreenshotProcessingService::class.java).apply {
                                        action = "START_PROCESSING"
                                    }
                                    Log.d("HomeScreen", "Starting foreground service")
                                    ContextCompat.startForegroundService(context, intent)
                                    
                                    Toast.makeText(
                                        context,
                                        "Started background processing (limited to 20 screenshots for dev)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Log.e("HomeScreen", "Error starting service", e)
                                    Toast.makeText(
                                        context,
                                        "Error starting background processing: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Process in Background")
                        }
                        
                        // Button to stop processing
                        Button(
                            onClick = {
                                try {
                                    // Stop the processing
                                    val intent = Intent(context, ScreenshotProcessingService::class.java).apply {
                                        action = "STOP_PROCESSING"
                                    }
                                    context.startService(intent)
                                    
                                    // Also call stopProcessing on the bound service if available
                                    processingService?.stopProcessing()
                                    
                                    Toast.makeText(
                                        context,
                                        "Pausing background processing...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Log.e("HomeScreen", "Error stopping service", e)
                                    Toast.makeText(
                                        context,
                                        "Error stopping background processing: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("Pause Processing")
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