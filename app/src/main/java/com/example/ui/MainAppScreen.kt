package com.example.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.Screen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.billing.BillingScreen
import com.example.ui.screens.bills.BillsHistoryScreen
import com.example.ui.screens.inventory.InventoryExpensesScreen
import com.example.ui.screens.menu.MenuManagementScreen
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.setup.RestaurantSetupScreen
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

@Composable
fun MainAppScreen(
    viewModel: MainViewModel = viewModel()
) {
    val userState by viewModel.userState.collectAsState()
    val isDarkThemePref by viewModel.isDarkTheme.collectAsState()
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()
    val isRestaurantLoaded by viewModel.isRestaurantLoaded.collectAsState()
    val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkTheme = isDarkThemePref ?: systemInDark

    MyApplicationTheme(darkTheme = darkTheme) {
        if (!userState.isLoggedIn) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    // Logged in
                }
            )
        } else {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Proper BackHandler navigation: popBackStack returns to previous screen/tab
            androidx.activity.compose.BackHandler(enabled = currentRoute != null && currentRoute != Screen.Billing.route) {
                if (!navController.popBackStack()) {
                    navController.navigate(Screen.Billing.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

            val bottomNavItems = listOf(
                BottomNavItem(Screen.Billing, Icons.Default.PointOfSale, "Billing"),
                BottomNavItem(Screen.BillsHistory, Icons.Default.ReceiptLong, "Bills"),
                BottomNavItem(Screen.MenuManagement, Icons.Default.RestaurantMenu, "Menu"),
                BottomNavItem(Screen.InventoryExpenses, Icons.Default.Inventory2, "Inventory"),
                BottomNavItem(Screen.Reports, Icons.Default.Insights, "Reports"),
                BottomNavItem(Screen.Settings, Icons.Default.Settings, "Settings")
            )

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    val navBarContainerColor = if (darkTheme) Color(0xFF0F172A) else FoodHubCharcoal
                    NavigationBar(
                        containerColor = navBarContainerColor,
                        contentColor = LightGreenWhiteText
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        item.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GoldTextDark,
                                    selectedTextColor = GoldAccent,
                                    indicatorColor = GoldAccent,
                                    unselectedIconColor = LightGreenWhiteText.copy(alpha = 0.7f),
                                    unselectedTextColor = LightGreenWhiteText.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Billing.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Billing.route) {
                        BillingScreen(viewModel = viewModel)
                    }
                    composable(Screen.BillsHistory.route) {
                        BillsHistoryScreen(viewModel = viewModel)
                    }
                    composable(Screen.MenuManagement.route) {
                        MenuManagementScreen(viewModel = viewModel)
                    }
                    composable(Screen.InventoryExpenses.route) {
                        InventoryExpensesScreen(viewModel = viewModel)
                    }
                    composable(Screen.Reports.route) {
                        ReportsScreen(viewModel = viewModel)
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
