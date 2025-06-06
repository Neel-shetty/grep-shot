package com.neel.grepshot.ui.navigation

import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neel.grepshot.data.model.ScreenshotItem
import com.neel.grepshot.data.repository.ScreenshotRepository
import com.neel.grepshot.ui.screens.fullscreen.FullScreenImageScreen
import com.neel.grepshot.ui.screens.home.HomeScreen
import com.neel.grepshot.ui.screens.settings.SettingsScreen
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    var screenshots by remember { mutableStateOf<List<ScreenshotItem>>(emptyList()) }
    
    // Create shared repository instance
    val screenshotRepository = remember { ScreenshotRepository(context) }
    
    NavHost(
        navController = navController, 
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onScreenshotsLoaded = { screenshots = it },
                onScreenshotClick = { screenshot ->
                    val encodedUri = Uri.encode(screenshot.uri.toString())
                    navController.navigate("fullscreen/$encodedUri/${screenshot.name}")
                },
                repository = screenshotRepository,
                onNavigateToSettings = { navController.navigate("settings") } // Add navigation to settings
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
            
            val uri = Uri.decode(encodedUri).toUri()
            val screenshotItem = ScreenshotItem(uri, name)
            
            FullScreenImageScreen(
                screenshotItem = screenshotItem,
                onNavigateBack = { navController.popBackStack() },
                repository = screenshotRepository
            )
        }
        
        // Add new settings route
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
