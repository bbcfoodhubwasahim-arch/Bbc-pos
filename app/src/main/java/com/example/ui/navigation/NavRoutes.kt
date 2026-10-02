package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Billing : Screen("billing", "Billing (POS)")
    object BillsHistory : Screen("bills_history", "Bills")
    object MenuManagement : Screen("menu_management", "Menu & Tables")
    object InventoryExpenses : Screen("inventory_expenses", "Inventory & Cash")
    object Reports : Screen("reports", "Reports & P&L")
    object Settings : Screen("settings", "Settings & Cloud")
    object Login : Screen("login", "Google Login")
    object RestaurantSetup : Screen("restaurant_setup", "Restaurant Setup")
}
