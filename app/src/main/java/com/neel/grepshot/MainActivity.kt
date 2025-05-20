@file:OptIn(ExperimentalMaterial3Api::class)

package com.neel.grepshot

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.neel.grepshot.ui.theme.GrepShotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrepShotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

data class ScreenshotItem(
    val uri: Uri,
    val name: String
)

// New data class to store screenshot with extracted text
data class ScreenshotWithText(
    val uri: Uri,
    val name: String,
    val extractedText: String
)

// Repository to manage screenshot text data
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

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var screenshots by remember { mutableStateOf<List<ScreenshotItem>>(emptyList()) }
    
    // Create shared repository instance
    val screenshotRepository = remember { ScreenshotRepository() }
    
    NavHost(
        navController = navController, 
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            ScreenshotsApp(
                screenshots = screenshots,
                onScreenshotsLoaded = { screenshots = it },
                onScreenshotClick = { screenshot ->
                    val encodedUri = Uri.encode(screenshot.uri.toString())
                    navController.navigate("fullscreen/$encodedUri/${screenshot.name}")
                },
                repository = screenshotRepository,
                onNavigateToSearch = {
                    navController.navigate("search")
                }
            )
        }
        
        composable(
            route = "fullscreen/{uri}/{name}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("uri") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            
            val uri = Uri.parse(Uri.decode(encodedUri))
            val screenshotItem = ScreenshotItem(uri, name)
            
            FullScreenImageScreen(
                screenshotItem = screenshotItem,
                onNavigateBack = { navController.popBackStack() },
                repository = screenshotRepository
            )
        }
        
        // Add search screen
        composable("search") {
            SearchScreen(
                repository = screenshotRepository,
                onScreenshotClick = { screenshot ->
                    val encodedUri = Uri.encode(screenshot.uri.toString())
                    navController.navigate("fullscreen/$encodedUri/${screenshot.name}")
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun ScreenshotsApp(
    screenshots: List<ScreenshotItem>,
    onScreenshotsLoaded: (List<ScreenshotItem>) -> Unit,
    onScreenshotClick: (ScreenshotItem) -> Unit,
    repository: ScreenshotRepository,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            ) == PackageManager.PERMISSION_GRANTED
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
            }
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotCard(
    screenshot: ScreenshotItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = screenshot.uri,
                contentDescription = screenshot.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun FullScreenImageScreen(
    screenshotItem: ScreenshotItem,
    onNavigateBack: () -> Unit,
    repository: ScreenshotRepository
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf<String?>(null) }
    
    // Handle system back button
    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }
    
    // Check if already processed or process image with ML Kit
    LaunchedEffect(screenshotItem.uri) {
        if (!repository.isScreenshotProcessed(screenshotItem.uri)) {
            isProcessing = true
            try {
                val inputImage = InputImage.fromFilePath(context, screenshotItem.uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                
                val result = withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine<String> { continuation ->
                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val text = visionText.text
                                if (continuation.isActive) {
                                    continuation.resume(text) { 
                                        // Handle cancellation if needed
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("TextRecognition", "Error processing image", e)
                                if (continuation.isActive) {
                                    continuation.resume("") {
                                        // Handle cancellation if needed
                                    }
                                }
                            }
                    }
                }
                
                extractedText = result
                repository.addScreenshotWithText(screenshotItem.uri, screenshotItem.name, result)
                
            } catch (e: Exception) {
                Log.e("TextRecognition", "Error creating InputImage", e)
                extractedText = ""
                repository.addScreenshotWithText(screenshotItem.uri, screenshotItem.name, "")
            } finally {
                isProcessing = false
            }
        } else {
            extractedText = repository.getScreenshot(screenshotItem.uri)?.extractedText
        }
    }
    
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            AsyncImage(
                model = screenshotItem.uri,
                contentDescription = "Full Screen Screenshot",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text(
                    text = screenshotItem.name,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Show status about text recognition
                if (isProcessing) {
                    Text(
                        text = "Processing text...",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                } else if (extractedText != null && extractedText!!.isNotEmpty()) {
                    Text(
                        text = "Text detected and saved",
                        color = Color.Green,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                } else if (extractedText != null) {
                    Text(
                        text = "No text detected in image",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    repository: ScreenshotRepository,
    onScreenshotClick: (ScreenshotWithText) -> Unit,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ScreenshotWithText>>(emptyList()) }
    
    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Search Screenshots") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search field
            androidx.compose.material3.TextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    searchResults = if (it.isNotEmpty()) {
                        repository.searchScreenshots(it)
                    } else {
                        emptyList()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search for text in screenshots") },
                leadingIcon = {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        searchResults = repository.searchScreenshots(searchQuery)
                    }
                )
            )
            
            // Show processing status
            val processedCount = repository.getAllScreenshots().size
            Text(
                text = "$processedCount screenshots processed for text",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                color = androidx.compose.ui.graphics.Color.Gray
            )
            
            // Results grid
            if (searchQuery.isNotEmpty()) {
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching results found")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(searchResults) { screenshot ->
                            SearchResultCard(
                                screenshot = screenshot,
                                onClick = { onScreenshotClick(screenshot) }
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Enter text to search in your screenshots")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultCard(
    screenshot: ScreenshotWithText,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
                Text(
                    text = screenshot.name,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Show a snippet of the matched text
                val snippetText = if (screenshot.extractedText.length > 50) {
                    "${screenshot.extractedText.take(50)}..."
                } else {
                    screenshot.extractedText
                }
                
                Text(
                    text = snippetText,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScreenshotsAppPreview() {
    GrepShotTheme {
        AppNavigation()
    }
}