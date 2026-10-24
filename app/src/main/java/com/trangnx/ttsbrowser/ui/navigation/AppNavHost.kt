package com.trangnx.ttsbrowser.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.ui.browser.BrowserScreen
import com.trangnx.ttsbrowser.ui.library.LibraryScreen
import com.trangnx.ttsbrowser.ui.settings.SettingsScreen

/** Top-level destinations shown in the bottom navigation bar. */
private enum class TopLevelRoute(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Browser("browser", R.string.nav_browser, Icons.Outlined.Public),
    Library("library", R.string.nav_library, Icons.AutoMirrored.Outlined.LibraryBooks),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.Browser.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelRoute.Browser.route) { BrowserScreen() }
            composable(TopLevelRoute.Library.route) { LibraryScreen() }
            composable(TopLevelRoute.Settings.route) { SettingsScreen() }
        }
    }
}
