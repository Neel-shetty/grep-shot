package com.neel.grepshot.ui.screens.home

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.model.ScreenshotWithText
import com.neel.grepshot.data.repository.ScreenshotRepository
import com.neel.grepshot.service.ScreenshotProcessingService
import com.neel.grepshot.util.PermissionUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onScreenshotsLoaded: (List<ScreenshotItem>) -> Unit,
    onScreenshotClick: (ScreenshotItem) -> Unit,
    repository: ScreenshotRepository,
    onNavigateToSettings: () -> Unit,  // New parameter for settings navigation
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // Service connection states
    var processingService by remember { mutableStateOf<ScreenshotProcessingService?>(null) }
    var serviceConnected by remember { mutableStateOf(false) }
    
    // Track processing progress from service only
    var serviceProcessingState by remember { mutableStateOf(ScreenshotProcessingService.ProcessingState(0, 0, false)) }
    
    // Database-loaded screenshots
    var dbScreenshots by remember { mutableStateOf<List<ScreenshotWithText>>(emptyList()) }
    // Loading state for screenshots
    var isLoading by remember { mutableStateOf(true) }
    
    // Track whether auto-processing has been launched to prevent multiple launches
    var autoProcessingLaunched by remember { mutableStateOf(false) }
    
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
    
    var hasNotificationPermission by remember {
        mutableStateOf(PermissionUtils.hasNotificationPermission(context))
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }
    
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            // Start processing after notification permission is granted
            coroutineScope.launch {
                try {
                    val intent = Intent(context, ScreenshotProcessingService::class.java).apply {
                        action = "START_PROCESSING"
                    }
                    ContextCompat.startForegroundService(context, intent)
                    
                    Toast.makeText(
                        context,
                        "Started processing available screenshots",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Error starting service after permission grant", e)
                }
            }
        }
    }

    // Search states
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ScreenshotWithText>>(emptyList()) }
    var processedCount by remember { mutableIntStateOf(0) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    
    // Get processed count and load screenshots from database
    LaunchedEffect(Unit) {
        isLoading = true
        processedCount = repository.getProcessedScreenshotCount()
        dbScreenshots = repository.getAllScreenshots()
        isLoading = false
        // Convert ScreenshotWithText to ScreenshotItem for compatibility with existing code
        val screenshotItems = dbScreenshots.map { 
            ScreenshotItem(it.uri, it.name) 
        }
        onScreenshotsLoaded(screenshotItems)
    }
    
    // Refresh database screenshots when processing state changes
    LaunchedEffect(serviceProcessingState) {
        if (!serviceProcessingState.isProcessing && serviceProcessingState.processed > 0) {
            // Refresh data after processing completes
            Log.d("HomeScreen", "Processing completed, refreshing UI data...")
            delay(2000) // Longer delay to ensure all database writes are complete
            
            try {
                isLoading = true
                processedCount = repository.getProcessedScreenshotCount()
                dbScreenshots = repository.getAllScreenshots()
                isLoading = false
                
                Log.d("HomeScreen", "After refresh: processedCount=$processedCount, dbScreenshots.size=${dbScreenshots.size}")
                
                // Update the screenshots list
                val screenshotItems = dbScreenshots.map { 
                    ScreenshotItem(it.uri, it.name) 
                }
                onScreenshotsLoaded(screenshotItems)
                Log.d("HomeScreen", "Refreshed after processing complete: ${dbScreenshots.size} screenshots")
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error refreshing after processing complete", e)
                isLoading = false
            }
        }
    }
    
    // Refresh UI images every time the progress bar moves (when processed count changes)
    LaunchedEffect(serviceProcessingState.processed) {
        if (serviceProcessingState.isProcessing && serviceProcessingState.processed > 0) {
            // Refresh data immediately when a new screenshot is processed
            Log.d("HomeScreen", "Progress updated: ${serviceProcessingState.processed}/${serviceProcessingState.total}, refreshing UI...")
            
            try {
                processedCount = repository.getProcessedScreenshotCount()
                dbScreenshots = repository.getAllScreenshots()
                
                // Update the screenshots list
                val screenshotItems = dbScreenshots.map { 
                    ScreenshotItem(it.uri, it.name) 
                }
                onScreenshotsLoaded(screenshotItems)
                Log.d("HomeScreen", "UI refreshed on progress update: ${dbScreenshots.size} screenshots")
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error refreshing UI on progress update", e)
            }
        }
    }
    
    // Periodic refresh while processing is happening
    LaunchedEffect(serviceProcessingState.isProcessing) {
        if (serviceProcessingState.isProcessing) {
            while (serviceProcessingState.isProcessing) {
                delay(5000) // Refresh every 5 seconds
                processedCount = repository.getProcessedScreenshotCount()
                dbScreenshots = repository.getAllScreenshots()
                
                val screenshotItems = dbScreenshots.map { 
                    ScreenshotItem(it.uri, it.name) 
                }
                onScreenshotsLoaded(screenshotItems)
            }
        }
    }
    
    // Search when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty() && isSearchActive) {
            searchResults = repository.searchScreenshots(searchQuery)
        } else {
            searchResults = emptyList()
            isSearchActive = false
        }
    }

    // Check for unprocessed screenshots in the background
    LaunchedEffect(hasPermission, serviceConnected, hasNotificationPermission) {
        if (hasPermission && hasNotificationPermission && !autoProcessingLaunched && serviceConnected) {
            coroutineScope.launch {
                val newScreenshots = repository.checkForNewScreenshots(context)
                if (newScreenshots.isNotEmpty()) {
                    // Start the background service to process new screenshots
                    try {
                        val intent = Intent(context, ScreenshotProcessingService::class.java).apply {
                            action = "START_PROCESSING"
                        }
                        Log.d("HomeScreen", "Auto-starting foreground service for ${newScreenshots.size} new screenshots")
                        ContextCompat.startForegroundService(context, intent)
                        
                        Toast.makeText(
                            context,
                            "Found ${newScreenshots.size} new screenshots. Processing in background.",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error auto-starting service", e)
                    }
                } else {
                    Log.d("HomeScreen", "No new screenshots found during launch check")
                }
                autoProcessingLaunched = true
            }
        }
    }

    // Refresh data periodically when processing is active
    LaunchedEffect(serviceProcessingState.isProcessing) {
        if (serviceProcessingState.isProcessing) {
            // Refresh data every 5 seconds while processing
            while (serviceProcessingState.isProcessing) {
                delay(5000)
                try {
                    isLoading = true
                    processedCount = repository.getProcessedScreenshotCount()
                    dbScreenshots = repository.getAllScreenshots()
                    isLoading = false
                    
                    val screenshotItems = dbScreenshots.map { 
                        ScreenshotItem(it.uri, it.name) 
                    }
                    onScreenshotsLoaded(screenshotItems)
                    Log.d("HomeScreen", "Refreshed data during processing: ${dbScreenshots.size} screenshots")
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Error refreshing data during processing", e)
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GrepShot") },
                actions = {
                    // Add settings button to the top bar
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Empty bottom bar
        },
        floatingActionButton = {
            // Development testing FAB - only keeping this one for clearing the database
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        repository.clearAllScreenshots()
                        // Update UI state immediately after clearing database
                        dbScreenshots = emptyList()
                        processedCount = 0
                        // Update parent component state
                        onScreenshotsLoaded(emptyList())
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
            // Add search bar at the top of the main content area
            if (hasPermission) {
                TextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        // Only set search active if query has content
                        isSearchActive = it.isNotEmpty()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search $processedCount screenshots for text") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { 
                                    searchQuery = ""
                                    isSearchActive = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotEmpty()) {
                                // Maintain search active state
                                isSearchActive = true
                                coroutineScope.launch {
                                    searchResults = repository.searchScreenshots(searchQuery)
                                }
                                
                                // Hide keyboard using the current window's token
                                val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    inputMethodManager.hideSoftInputFromWindow(activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken, 0)
                                }
                            }
                        }
                    )
                )
                // Show number of results after search
                if (isSearchActive && searchQuery.isNotEmpty()) {
                    Text(
                        text = "${searchResults.size} result${if (searchResults.size == 1) "" else "s"} found",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

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
                // Service processing state display - only show if processing is active
                if (serviceProcessingState.total > 0 && serviceProcessingState.isProcessing) {
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
                            progress = { (serviceProcessingState.processed.toFloat() / serviceProcessingState.total).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                    }
                }
                
                // Display either search results or screenshots list
                if (isSearchActive && searchQuery.isNotEmpty()) {
                    if (searchResults.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No matching results found")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { screenshot ->
                                SearchResultCard(
                                    screenshot = screenshot,
                                    onClick = { 
                                        // Convert ScreenshotWithText to ScreenshotItem for navigation
                                        onScreenshotClick(ScreenshotItem(screenshot.uri, screenshot.name))
                                    },
                                    searchQuery = searchQuery
                                )
                            }
                        }
                    }
                } else if (isLoading) {
                    // Show loading indicator while loading screenshots
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LinearProgressIndicator(modifier = Modifier.padding(32.dp))
                        Text("Loading screenshots...")
                    }
                } else if (dbScreenshots.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No processed screenshots found in database")
                        
                        // Show information about notification permission if needed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            Text(
                                text = "Notification permission is required for background processing",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                        }
                        
                        // Add processing button if there are no screenshots and not loading
                        Button(
                            onClick = {
                                // Check for notification permission first
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    // Request notification permission
                                    notificationPermissionLauncher.launch(PermissionUtils.getNotificationPermission())
                                } else {
                                    // Start processing directly
                                    try {
                                        val intent = Intent(context, ScreenshotProcessingService::class.java).apply {
                                            action = "START_PROCESSING"
                                        }
                                        ContextCompat.startForegroundService(context, intent)
                                        
                                        Toast.makeText(
                                            context,
                                            "Started processing available screenshots",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Error starting service", e)
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    "Grant Notification Permission & Process"
                                } else {
                                    "Find and Process Screenshots"
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            count = dbScreenshots.size,
                            key = { index -> dbScreenshots[index].uri }
                        ) { index ->
                            val screenshot = dbScreenshots[index]
                            SearchResultCard(
                                screenshot = screenshot,
                                onClick = { 
                                    onScreenshotClick(ScreenshotItem(screenshot.uri, screenshot.name))
                                },
                                searchQuery = searchQuery,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    screenshot: ScreenshotWithText,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = screenshot.uri,
                    contentDescription = screenshot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Show extracted text with search query highlighting
                val displayText = if (searchQuery.isNotEmpty() && screenshot.extractedText.contains(searchQuery, ignoreCase = true)) {
                    // Find the line containing the search query
                    val lines = screenshot.extractedText.split('\n')
                    val matchingLine = lines.find { it.contains(searchQuery, ignoreCase = true) }
                    
                    if (matchingLine != null) {
                        // Show the matching line and the next line if available
                        val matchingLineIndex = lines.indexOf(matchingLine)
                        val nextLine = if (matchingLineIndex + 1 < lines.size) lines[matchingLineIndex + 1] else ""
                        if (nextLine.isNotEmpty()) {
                            "$matchingLine\n$nextLine"
                        } else {
                            matchingLine
                        }
                    } else {
                        // Fallback to truncated text
                        if (screenshot.extractedText.length > 100) {
                            "${screenshot.extractedText.take(100)}..."
                        } else {
                            screenshot.extractedText
                        }
                    }
                } else {
                    // Show truncated text when no search query
                    if (screenshot.extractedText.length > 100) {
                        "${screenshot.extractedText.take(100)}..."
                    } else {
                        screenshot.extractedText
                    }
                }
                
                Text(
                    text = displayText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
