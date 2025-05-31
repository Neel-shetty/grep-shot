package com.neel.grepshot.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.neel.grepshot.data.repository.ScreenshotRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { ScreenshotRepository(context) }
    
    // Setting states
    var autoProcessingEnabled by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }
    
    // Export-related states
    var isExporting by remember { mutableStateOf(false) }
    var exportSuccess by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var selectedDirectoryUri by remember { mutableStateOf<Uri?>(null) }
    
    // Directory picker launcher for Storage Access Framework
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            selectedDirectoryUri = uri
            // Persist permission for future use
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            // Start export process
            isExporting = true
            exportScreenshotsData(repository, uri, coroutineScope) { success, message ->
                isExporting = false
                if (success) exportSuccess = message else exportError = message
            }
        }
    }
    
    // Export completed dialog
    if (exportSuccess != null) {
        AlertDialog(
            onDismissRequest = { exportSuccess = null },
            title = { Text("Export Successful") },
            text = { Text(exportSuccess!!) },
            confirmButton = {
                TextButton(onClick = { exportSuccess = null }) {
                    Text("OK")
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Export Success"
                )
            }
        )
    }
    
    // Export error dialog
    if (exportError != null) {
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text("Export Failed") },
            text = { Text(exportError!!) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Processing Settings Section
            SettingsSectionTitle(title = "Processing Settings")
            
            SettingsSwitchItem(
                title = "Auto Process Screenshots",
                description = "Automatically process new screenshots in background",
                checked = autoProcessingEnabled,
                onCheckedChange = { autoProcessingEnabled = it }
            )
            
            SettingsSwitchItem(
                title = "Show Notifications",
                description = "Display notifications during background processing",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Appearance Settings
            SettingsSectionTitle(title = "Appearance")
            
            SettingsSwitchItem(
                title = "Dark Theme",
                description = "Enable dark theme for the app",
                checked = darkModeEnabled,
                onCheckedChange = { darkModeEnabled = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Data Management Section
            SettingsSectionTitle(title = "Data Management")
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Export Screenshots Data",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Text(
                        text = "Choose a folder to export all screenshot text data as JSON file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Show selected directory if available
                    selectedDirectoryUri?.let { uri ->
                        val directoryName = DocumentFile.fromTreeUri(context, uri)?.name
                        Text(
                            text = "Selected folder: ${directoryName ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Button(
                        onClick = {
                            // Launch directory picker using Storage Access Framework
                            directoryPickerLauncher.launch(null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text("Exporting...")
                        } else {
                            Text("Choose Folder & Export")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // About Section
            SettingsSectionTitle(title = "About")
            
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "GrepShot",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Version 1.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Search through your screenshots with ease. GrepShot uses OCR technology to extract text from your screenshots, making them searchable.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    
    Divider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

private fun exportScreenshotsData(
    repository: ScreenshotRepository,
    directoryUri: Uri,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onComplete: (Boolean, String) -> Unit
) {
    coroutineScope.launch {
        try {
            // Call repository function for export with the selected directory
            val exportResult = repository.exportScreenshotsData(directoryUri)
            onComplete(true, "Data exported successfully to:\n${exportResult.absolutePath}\n\nTotal items exported: ${exportResult.itemCount}")
        } catch (e: Exception) {
            onComplete(false, "Export failed: ${e.message}")
        }
    }
}
