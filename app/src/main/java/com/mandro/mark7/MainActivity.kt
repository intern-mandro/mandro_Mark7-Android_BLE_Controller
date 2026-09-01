package com.mandro.mark7

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mandro.mark7.presentation.navigation.BOTTOM_NAV_ITEMS
import com.mandro.mark7.presentation.navigation.BOTTOM_NAV_ROUTES
import com.mandro.mark7.presentation.navigation.Screen
import com.mandro.mark7.presentation.theme.Mark7Theme
import com.mandro.mark7.presentation.ui.action.ActionScreen
import com.mandro.mark7.presentation.ui.action.PatternPickerScreen
import com.mandro.mark7.presentation.ui.manual.ManualScreen
import com.mandro.mark7.presentation.ui.monitor.MonitorScreen
import com.mandro.mark7.presentation.ui.scan.ScanScreen
import com.mandro.mark7.presentation.ui.settings.SettingsScreen
import com.mandro.mark7.presentation.ui.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Mark7Theme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentRoute in BOTTOM_NAV_ROUTES) {
                            NavigationBar {
                                BOTTOM_NAV_ITEMS.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentRoute == item.screen.route,
                                        onClick = {
                                            navController.navigate(item.screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = when (item.icon) {
                                                    "monitor" -> Icons.Filled.MonitorHeart
                                                    "gesture" -> Icons.Filled.Gesture
                                                    "tune" -> Icons.Filled.Tune
                                                    else -> Icons.Filled.Settings
                                                },
                                                contentDescription = item.label,
                                            )
                                        },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Splash.route,
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(Screen.Splash.route) {
                            SplashScreen(
                                onReady = { connected ->
                                    val dest = if (connected) Screen.Monitor.route else Screen.Scan.route
                                    navController.navigate(dest) {
                                        popUpTo(Screen.Splash.route) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(Screen.Scan.route) {
                            ScanScreen(
                                onConnected = {
                                    navController.navigate(Screen.Monitor.route) {
                                        popUpTo(Screen.Scan.route) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(Screen.Monitor.route) {
                            MonitorScreen(onDisconnected = { navController.navigate(Screen.Scan.route) })
                        }
                        composable(Screen.Action.route) {
                            ActionScreen(
                                onPickPattern = { action ->
                                    navController.navigate(Screen.PatternPicker.createRoute(action.name))
                                },
                            )
                        }
                        composable(Screen.Manual.route) { ManualScreen() }
                        composable(Screen.Settings.route) { SettingsScreen() }
                        composable(Screen.PatternPicker.route) {
                            PatternPickerScreen(onDone = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
