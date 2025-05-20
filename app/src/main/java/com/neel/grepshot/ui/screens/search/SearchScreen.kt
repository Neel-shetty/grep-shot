package com.neel.grepshot.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neel.grepshot.data.model.ScreenshotWithText
import com.neel.grepshot.data.repository.ScreenshotRepository
import kotlinx.coroutines.launch

@ExperimentalMaterial3Api
@Composable
fun SearchScreen(
    repository: ScreenshotRepository,
    onScreenshotClick: (ScreenshotWithText) -> Unit,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ScreenshotWithText>>(emptyList()) }
    var processedCount by remember { mutableStateOf(0) }
    
    // Create a coroutine scope that follows the lifecycle of the composable
    val coroutineScope = rememberCoroutineScope()
    
    // Get processed count once when screen loads
    LaunchedEffect(Unit) {
        processedCount = repository.getAllScreenshots().size
    }
    
    // Search when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            searchResults = repository.searchScreenshots(searchQuery)
        } else {
            searchResults = emptyList()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
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
            TextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search for text in screenshots") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        coroutineScope.launch {
                            searchResults = repository.searchScreenshots(searchQuery)
                        }
                    }
                )
            )
            
            // Show processing status
            Text(
                text = "$processedCount screenshots processed for text",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                color = Color.Gray
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