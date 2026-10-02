package com.example.ui.screens.inventory

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CashRegisterEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.InventoryBatchEntity
import com.example.ui.MultiItemPurchaseInput
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.theme.*
import com.example.util.DateUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val ForestGreen = Color(0xFF2E7D32)
val SugarCaneGreen = Color(0xFFE8F5E9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryExpensesScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Inventory, 1 = Inventory tab, 2 = Stock Book, 3 = Stock Audit, 4 = Cash Register

    val allInventory by viewModel.allInventory.collectAsState()
    val lowStockCount by viewModel.liveLowStockCount.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allBatches by viewModel.allBatches.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val allRegisters by viewModel.allRegisters.collectAsState()
    val allStockCounts by viewModel.allStockCounts.collectAsState()
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()

    var showLowStockOnly by remember { mutableStateOf(false) }
    var showAddInventoryDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var itemToAddStockTo by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var itemToRecordWastageFor by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddExpenseCatDialog by remember { mutableStateOf(false) }
    var showOpenRegisterDialog by remember { mutableStateOf(false) }
    var registerToClose by remember { mutableStateOf<CashRegisterEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var itemForBatchDetail by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var showMultiItemPurchaseScreen by remember { mutableStateOf(false) }
    var showVendorLedgerScreen by remember { mutableStateOf(false) }
    var showExpenseTypeSelectionDialog by remember { mutableStateOf(false) }
    var showRecordWastageDialog by remember { mutableStateOf(false) }

    val todayDateStr = remember { DateUtils.getTodayDateString() }
    val todayRegister = remember(allRegisters, todayDateStr) {
        allRegisters.find { it.dateString == todayDateStr }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory & Expenses Hub", fontWeight = FontWeight.Bold) },
                actions = {
                    when (selectedTab) {
                        0 -> {
                            IconButton(onClick = { showRecordWastageDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Record Wastage", tint = Color(0xFFDC2626))
                            }

                            IconButton(onClick = { showVendorLedgerScreen = true }) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Vendor Ledger", tint = CaramelWarm)
                            }

                            IconButton(onClick = { showMultiItemPurchaseScreen = true }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Bulk Stock Purchase", tint = CaramelWarm)
                            }

                            IconButton(onClick = { showAddInventoryDialog = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add New Item", tint = CaramelWarm)
                            }
                        }
                        1 -> {
                            IconButton(onClick = { showExpenseTypeSelectionDialog = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Record Expense", tint = CaramelWarm)
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Inventory", fontWeight = FontWeight.SemiBold)
                            if (lowStockCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(containerColor = ErrorRed) {
                                    Text("$lowStockCount", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Expenses", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Stock Book 📖", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Stock Audit", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Cash Register", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Inventory Tab
                    val displayedInventory = if (showLowStockOnly) {
                        allInventory.filter { it.currentStock <= it.lowStockThreshold }
                    } else {
                        allInventory
                    }

                    val todayStart = remember { DateUtils.getStartOfDay() }
                    val todayBatches = remember(allBatches, todayStart) { allBatches.filter { it.timestamp >= todayStart } }
                    val todayTotalPurchase = remember(todayBatches) { todayBatches.sumOf { it.initialQuantity * it.purchaseRate } }
                    val totalInventoryValuation = remember(allInventory, allBatches) {
                        allBatches.filter { it.remainingQuantity > 0.000001 && it.status != "ARCHIVED" }
                            .sumOf { it.remainingQuantity * it.purchaseRate }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Summary Dashboard Widget
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Today's Purchase", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("₹${String.format(Locale.US, "%.2f", todayTotalPurchase).replace(".00", "")}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${todayBatches.size} Batches Added", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = SugarCaneGreen.copy(alpha = 0.15f)),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ForestGreen.copy(alpha = 0.3f))),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Stock Valuation", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("₹${String.format(Locale.US, "%.2f", totalInventoryValuation).replace(".00", "")}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ForestGreen)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Live Valuation", fontSize = 10.sp, color = ForestGreen.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }

                        // HIGHLY VISIBLE QUICK ACTIONS PANEL
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Quick Actions & Reports", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                    var exportedFile by remember { mutableStateOf<Pair<File, String>?>(null) }
                                    var showExportDialog by remember { mutableStateOf(false) }

                                    // ... inside Composable InventoryExpensesScreen ...
                                        Button(
                                            onClick = {
                                                try {
                                                    val rest = activeRestaurant ?: com.example.data.local.entity.RestaurantEntity("", "BBC Food Hub", "Washim", "917087314118")
                                                    val pdfFile = com.example.util.ReportExportUtil.generateInventoryReportPdf(context, allInventory, allBatches, rest)
                                                    exportedFile = Pair(pdfFile, "application/pdf")
                                                    showExportDialog = true
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("PDF Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                try {
                                                    val rest = activeRestaurant ?: com.example.data.local.entity.RestaurantEntity("", "BBC Food Hub", "Washim", "917087314118")
                                                    val csvFile = com.example.util.ReportExportUtil.generateInventoryReportCsv(context, allInventory, allBatches, rest)
                                                    exportedFile = Pair(csvFile, "text/csv")
                                                    showExportDialog = true
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Excel Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
    // Add the dialog at the end of the Composable
    if (showExportDialog && exportedFile != null) {
        val (file, mimeType) = exportedFile!!
        val isPdf = mimeType == "application/pdf"
        val fileTypeName = if (isPdf) "PDF Report Document" else "CSV Spreadsheet"

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.TableChart, contentDescription = null, tint = CaramelWarm)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$fileTypeName Ready", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Report file '${file.name}' was created successfully.", style = MaterialTheme.typography.bodySmall)
                    
                    // Open / View File
                    Button(
                        onClick = {
                            com.example.util.ReportExportUtil.openReportFile(context, file, mimeType)
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("👁️ Open & View $fileTypeName", fontWeight = FontWeight.Bold)
                    }

                    // Save to Downloads
                    OutlinedButton(
                        onClick = {
                            val (success, msg) = com.example.util.ReportExportUtil.saveToDownloads(context, file, mimeType)
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📥 Save to Downloads", fontWeight = FontWeight.Bold)
                    }

                    // Share File
                    OutlinedButton(
                        onClick = {
                            com.example.util.ReportExportUtil.shareReportFile(context, file, mimeType, "Inventory Report")
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📤 Share via WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Close") } }
        )
    }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showVendorLedgerScreen = true },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Vendor Ledger", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { showMultiItemPurchaseScreen = true },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = DeepAmber),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Bulk Purchase", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Low Stock Alert Banner
                        if (lowStockCount > 0) {
                            item {
                                Surface(
                                    color = TableOccupiedRed.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TableOccupiedRed.copy(alpha = 0.4f)))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = TableOccupiedRed)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "$lowStockCount Items Running Low!",
                                                fontWeight = FontWeight.Bold,
                                                color = TableOccupiedRed,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Replenish stock to avoid service disruptions",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        FilterChip(
                                            selected = showLowStockOnly,
                                            onClick = { showLowStockOnly = !showLowStockOnly },
                                            label = { Text(if (showLowStockOnly) "Show All" else "Filter", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Top Action Bar
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Stock Items (${displayedInventory.size})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                OutlinedButton(
                                    onClick = { showAddInventoryDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Item", fontSize = 12.sp)
                                }
                            }
                        }

                        // Inventory Items List
                        items(displayedInventory) { item ->
                            val isLowStock = item.currentStock <= item.lowStockThreshold
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { itemForBatchDetail = item },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            if (isLowStock) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = TableOccupiedRed.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "LOW STOCK",
                                                        color = TableOccupiedRed,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Stock: ${String.format(Locale.US, "%.2f", item.currentStock).replace(".00", "")} ${item.unit}  •  Threshold: ${String.format(Locale.US, "%.2f", item.lowStockThreshold).replace(".00", "")} ${item.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLowStock) TableOccupiedRed else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val activeBatches = remember(allBatches, item.id) {
                                            allBatches.filter { it.inventoryItemId == item.id && it.remainingQuantity > 0.000001 && it.status != "ARCHIVED" }
                                                .sortedWith(compareBy({ it.timestamp }, { it.createdAt }, { it.id }))
                                        }
                                        Text(
                                            text = "Current Rate: ₹${String.format(Locale.US, "%.2f", item.purchasePrice)} / ${item.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = DeepAmber
                                        )
                                        if (activeBatches.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Batches (${activeBatches.size}): " + activeBatches.take(3).joinToString(" | ") {
                                                    "${String.format(Locale.US, "%.2f", it.remainingQuantity).replace(".00", "")} ${it.unit} @ ₹${String.format(Locale.US, "%.1f", it.purchaseRate)}"
                                                } + if (activeBatches.size > 3) " (+${activeBatches.size - 3} more)" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { itemToAddStockTo = item },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DeepAmber,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+ Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(onClick = { itemToRecordWastageFor = item }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Wastage", tint = TableOccupiedRed, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { itemToEdit = item }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { itemToDelete = item }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Expenses Tab with Date Filters and Visual Breakdown
                    val todayStart = remember { DateUtils.getStartOfDay() }
                    val yesterdayStart = todayStart - 86400000L
                    val sevenDaysAgo = todayStart - (7 * 86400000L)
                    val thirtyDaysAgo = todayStart - (30 * 86400000L)

                    var expenseFilter by remember { mutableStateOf("TODAY") } // TODAY, YESTERDAY, WEEK, MONTH, ALL

                    val filteredExpenses = remember(allExpenses, expenseFilter, todayStart) {
                        when (expenseFilter) {
                            "TODAY" -> allExpenses.filter { it.timestamp >= todayStart }
                            "YESTERDAY" -> allExpenses.filter { it.timestamp >= yesterdayStart && it.timestamp < todayStart }
                            "WEEK" -> allExpenses.filter { it.timestamp >= sevenDaysAgo }
                            "MONTH" -> allExpenses.filter { it.timestamp >= thirtyDaysAgo }
                            else -> allExpenses
                        }
                    }.sortedByDescending { it.timestamp }

                    val totalFilteredAmount = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }

                    val categoryBreakdown = remember(filteredExpenses) {
                        filteredExpenses.groupBy { it.categoryName }
                            .mapValues { entry -> entry.value.sumOf { it.amount } }
                            .toList()
                            .sortedByDescending { it.second }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Date Filter Chips
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = expenseFilter == "TODAY",
                                    onClick = { expenseFilter = "TODAY" },
                                    label = { Text("Today") }
                                )
                                FilterChip(
                                    selected = expenseFilter == "YESTERDAY",
                                    onClick = { expenseFilter = "YESTERDAY" },
                                    label = { Text("Yesterday") }
                                )
                                FilterChip(
                                    selected = expenseFilter == "WEEK",
                                    onClick = { expenseFilter = "WEEK" },
                                    label = { Text("This Week") }
                                )
                                FilterChip(
                                    selected = expenseFilter == "MONTH",
                                    onClick = { expenseFilter = "MONTH" },
                                    label = { Text("This Month") }
                                )
                                FilterChip(
                                    selected = expenseFilter == "ALL",
                                    onClick = { expenseFilter = "ALL" },
                                    label = { Text("All") }
                                )
                            }
                        }

                        // Summary Statistics Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = when (expenseFilter) {
                                                "TODAY" -> "Today's Total Expenses"
                                                "YESTERDAY" -> "Yesterday's Expenses"
                                                "WEEK" -> "Weekly Total Expenses"
                                                "MONTH" -> "Monthly Total Expenses"
                                                else -> "Total Recorded Expenses"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "₹${String.format(Locale.US, "%.2f", totalFilteredAmount)}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 24.sp,
                                            color = ErrorRed
                                        )
                                    }
                                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(32.dp))
                                }
                            }
                        }

                        // Expense Visual Category Breakdown Summary (Category Progress Bars)
                        if (categoryBreakdown.isNotEmpty() && totalFilteredAmount > 0.0) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Expenses Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        categoryBreakdown.forEach { (catName, amt) ->
                                            val pct = (amt / totalFilteredAmount).toFloat()
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(catName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                    Text("₹${String.format(Locale.US, "%.2f", amt)} (${String.format(Locale.US, "%.0f", pct * 100)}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                LinearProgressIndicator(
                                                    progress = pct,
                                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = CaramelWarm,
                                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Recent Expenses Actions Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recorded Expenses (${filteredExpenses.size})", fontWeight = FontWeight.Bold)
                                Row {
                                    TextButton(onClick = { showAddExpenseCatDialog = true }) {
                                        Text("+ Category", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { showExpenseTypeSelectionDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Expense", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Expenses List
                        if (filteredExpenses.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No expenses recorded for this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(filteredExpenses) { exp ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(exp.categoryName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            if (exp.description.isNotBlank()) {
                                                Text(exp.description, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                "${DateUtils.formatDateTime(exp.timestamp)} • Paid via ${exp.paymentMethod}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "₹${String.format(Locale.US, "%.2f", exp.amount)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = ErrorRed
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { expenseToEdit = exp },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit Expense",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { expenseToDelete = exp },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Expense",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Stock Book Tab
                    val allBills by viewModel.allBills.collectAsState()
                    val rest = activeRestaurant ?: com.example.data.local.entity.RestaurantEntity("", "BBC Food Hub", "Washim", "917087314118")
                    InventoryStockBookTab(
                        viewModel = viewModel,
                        allInventory = allInventory,
                        allBatches = allBatches,
                        allStockCounts = allStockCounts,
                        allBills = allBills,
                        allExpenses = allExpenses,
                        restaurant = rest
                    )
                }
                3 -> {
                    // Stock Audit Tab
                    StockCountTabContent(
                        viewModel = viewModel,
                        allInventory = allInventory,
                        allBatches = allBatches,
                        allStockCounts = allStockCounts
                    )
                }
                4 -> {
                    // Cash Register Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("Daily Cash Drawer & Register", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Track physical cash float, cash sales, and closing drawer count.", style = MaterialTheme.typography.bodySmall)
                        }

                        if (todayRegister == null) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(48.dp), tint = WarningOrange)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Register Not Opened Today", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Open today's register with opening float cash to track daily drawer", style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = { showOpenRegisterDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open Daily Register", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                val isClosed = todayRegister.status == "CLOSED"
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = if (isClosed) MaterialTheme.colorScheme.surfaceVariant else CreamSurfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Today's Register (${todayRegister.dateString})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text(
                                                    "Opened at ${DateUtils.formatTime(todayRegister.openedAt)}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Surface(
                                                color = if (isClosed) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = todayRegister.status,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isClosed) MaterialTheme.colorScheme.outline else SuccessGreen,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                        // Register breakdown metrics
                                        RegisterMetricRow("Opening Cash Float:", todayRegister.openingCash)
                                        RegisterMetricRow("+ Cash Sales:", todayRegister.cashSales, color = ForestGreen)
                                        RegisterMetricRow("- Cash Expenses:", todayRegister.cashExpenses, color = ErrorRed)
                                        if (todayRegister.cashAdded > 0) {
                                            RegisterMetricRow("+ Cash Added:", todayRegister.cashAdded, color = ForestGreen)
                                        }
                                        if (todayRegister.cashWithdrawn > 0) {
                                            RegisterMetricRow("- Cash Withdrawn:", todayRegister.cashWithdrawn, color = ErrorRed)
                                        }

                                        val expectedCash = todayRegister.openingCash + todayRegister.cashSales - todayRegister.cashExpenses + todayRegister.cashAdded - todayRegister.cashWithdrawn
                                        RegisterMetricRow("Expected Cash Balance:", expectedCash, color = MaterialTheme.colorScheme.primary)

                                        if (isClosed) {
                                            RegisterMetricRow("Closing Actual Cash Entered:", todayRegister.closingCashActual ?: 0.0)
                                            val difference = (todayRegister.closingCashActual ?: 0.0) - expectedCash
                                            RegisterMetricRow(
                                                label = if (difference >= 0) "Cash Surplus (Over):" else "Cash Shortage (Discrepancy):",
                                                value = difference,
                                                color = if (difference >= 0) ForestGreen else ErrorRed
                                            )
                                            if ((todayRegister.closedAt ?: 0L) > 0L) {
                                                Text("Closed at ${DateUtils.formatTime(todayRegister.closedAt!!)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (!isClosed) {
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Button(
                                                onClick = { registerToClose = todayRegister },
                                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Cancel, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Close Drawer & Register", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Stock Count Tab (Audit logs for inventory balance revisions)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text("Audit Logs & Stock Adjustments", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Review history of additions, usage count deductions, and batch actions.", style = MaterialTheme.typography.bodySmall)
                        }

                        if (allStockCounts.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text("No stock counts or adjustments logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(allStockCounts.sortedByDescending { it.countDate }) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(log.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(log.countDate))
                                            Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Total Items Checked: ${log.totalItemsChecked}", fontSize = 12.sp)
                                            Text("Total Variance Cost: ₹${String.format(Locale.US, "%.1f", log.totalVarianceCost)}", fontSize = 12.sp, color = if (log.totalVarianceCost >= 0) ForestGreen else ErrorRed, fontWeight = FontWeight.Bold)
                                        }
                                        if (log.notes.isNotBlank()) {
                                            Text("Notes: ${log.notes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // EXPENSE TYPE SELECTION DIALOG (FIX 2)
    if (showExpenseTypeSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showExpenseTypeSelectionDialog = false },
            title = { Text("Select Expense Type", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExpenseTypeSelectionDialog = false
                                showMultiItemPurchaseScreen = true
                            },
                        colors = CardDefaults.cardColors(containerColor = DeepAmber.copy(alpha = 0.12f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DeepAmber.copy(alpha = 0.4f)))
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DeepAmber, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Bulk Inventory Purchase", fontWeight = FontWeight.Bold, color = DeepAmber)
                                Text("Add stock to multiple items, record purchase rate, quantity & auto-adds to stock.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExpenseTypeSelectionDialog = false
                                showAddExpenseDialog = true
                            },
                        colors = CardDefaults.cardColors(containerColor = CaramelWarm.copy(alpha = 0.12f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CaramelWarm.copy(alpha = 0.4f)))
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = CaramelWarm, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("General Expense", fontWeight = FontWeight.Bold, color = CaramelWarm)
                                Text("Record simple expenses like electric bills, rent, shop maintenance, or salaries.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExpenseTypeSelectionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // EXPENSE EDIT DIALOG (FIX 3)
    expenseToEdit?.let { exp ->
        var editAmountStr by remember { mutableStateOf(exp.amount.toString()) }
        var editDescription by remember { mutableStateOf(exp.description) }
        var editPaymentMethod by remember { mutableStateOf(exp.paymentMethod) }
        val selectedCatId = exp.categoryId
        val selectedCatName = exp.categoryName

        AlertDialog(
            onDismissRequest = { expenseToEdit = null },
            title = { Text("Edit Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editAmountStr,
                        onValueChange = { editAmountStr = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Payment Method:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editPaymentMethod == "CASH",
                            onClick = { editPaymentMethod = "CASH" },
                            label = { Text("CASH") }
                        )
                        FilterChip(
                            selected = editPaymentMethod == "UPI",
                            onClick = { editPaymentMethod = "UPI" },
                            label = { Text("UPI") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = editAmountStr.toDoubleOrNull() ?: exp.amount
                    viewModel.editExpense(
                        id = exp.id,
                        categoryId = selectedCatId,
                        categoryName = selectedCatName,
                        amount = amt,
                        paymentMethod = editPaymentMethod,
                        description = editDescription,
                        timestamp = exp.timestamp
                    )
                    Toast.makeText(context, "Expense updated successfully!", Toast.LENGTH_SHORT).show()
                    expenseToEdit = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Stock To Item Dialog
    itemToAddStockTo?.let { item ->
        var qtyStr by remember { mutableStateOf("") }
        var rateStr by remember { mutableStateOf(if (item.purchasePrice > 0.0) item.purchasePrice.toString() else "") }
        var paymentMethodStock by remember { mutableStateOf("CASH") }
        var recordExpenseCheckbox by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { itemToAddStockTo = null },
            title = { Text("Add Stock for ${item.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Quantity to Add (${item.unit})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rateStr,
                        onValueChange = { rateStr = it },
                        label = { Text("Rate per ${item.unit} (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = recordExpenseCheckbox,
                            onCheckedChange = { recordExpenseCheckbox = it }
                        )
                        Text("Record as Expense", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (recordExpenseCheckbox) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Payment Mode:")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = paymentMethodStock == "CASH",
                                    onClick = { paymentMethodStock = "CASH" },
                                    label = { Text("CASH") }
                                )
                                FilterChip(
                                    selected = paymentMethodStock == "UPI",
                                    onClick = { paymentMethodStock = "UPI" },
                                    label = { Text("UPI") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val qty = qtyStr.toDoubleOrNull() ?: 0.0
                    val rate = rateStr.toDoubleOrNull() ?: item.purchasePrice
                    if (qty <= 0.0) return@Button

                    val referenceId = "grn_${UUID.randomUUID()}"
                    val payStatus = if (recordExpenseCheckbox) "FULLY_PAID" else "UNPAID_CREDIT"

                    val grnNotes = listOf(
                        "GRN_PAY_STATUS:$payStatus",
                        "GRN_METHOD:$paymentMethodStock",
                        "GRN_USER_NOTES:Quick Stock Add",
                        "Items: ${item.name} (${String.format(Locale.US, "%.2f", qty).replace(".00", "")} ${item.unit} @ ₹$rate)"
                    ).filter { it.isNotBlank() }.joinToString(" | ")

                    if (recordExpenseCheckbox && paymentMethodStock == "CASH") {
                        val dateStr = com.example.util.DateUtils.formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
                        coroutineScope.launch {
                            val reg = viewModel.repository.cashRegisterDao.getRegisterForDate(activeRestaurant?.id ?: "", dateStr)
                            if (reg != null) {
                                val updatedReg = reg.copy(
                                    cashExpenses = reg.cashExpenses + (qty * rate),
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.repository.cashRegisterDao.insertOrUpdate(updatedReg)
                                viewModel.syncManager.uploadCashRegister(viewModel.repository.currentUserId.value, updatedReg)
                            }
                        }
                    }

                    viewModel.addStockToItem(
                        itemId = item.id,
                        itemName = item.name,
                        quantity = qty,
                        notes = grnNotes,
                        purchaseRate = rate
                    )

                    Toast.makeText(context, "Added $qty ${item.unit} of ${item.name} to stock!", Toast.LENGTH_SHORT).show()
                    itemToAddStockTo = null
                }) {
                    Text("Add Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToAddStockTo = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quick Wastage Log Dialog
    itemToRecordWastageFor?.let { item ->
        var qtyStr by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("SPOILAGE") } // SPOILAGE, LEAKAGE, EXPIRED, OTHER
        var notesWastage by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { itemToRecordWastageFor = null },
            title = { Text("Log Wastage for ${item.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Wastage Quantity (${item.unit})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notesWastage,
                        onValueChange = { notesWastage = it },
                        label = { Text("Notes & Reason details") },
                        placeholder = { Text("e.g. Expired buns / Spilled milk") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val qty = qtyStr.toDoubleOrNull() ?: 0.0
                    if (qty <= 0.0) return@Button
                    viewModel.recordWastage(
                        inventoryItemId = item.id,
                        quantity = qty,
                        reason = reason,
                        notes = if (notesWastage.isNotBlank()) notesWastage else "Quick logged wastage"
                    )
                    Toast.makeText(context, "Recorded wastage of $qty ${item.unit} for ${item.name}!", Toast.LENGTH_SHORT).show()
                    itemToRecordWastageFor = null
                }) {
                    Text("Log Wastage", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRecordWastageFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add New Inventory Item Dialog
    if (showRecordWastageDialog) {
        RecordWastageDialog(
            inventoryItems = allInventory,
            onDismiss = { showRecordWastageDialog = false },
            onConfirm = { itemId, qty, reason, notes ->
                viewModel.recordWastage(
                    inventoryItemId = itemId,
                    quantity = qty,
                    reason = reason,
                    notes = notes
                )
                Toast.makeText(context, "Wastage recorded & stock updated!", Toast.LENGTH_SHORT).show()
                showRecordWastageDialog = false
            }
        )
    }

    if (showAddInventoryDialog) {
        var name by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("KG") }
        var thresholdStr by remember { mutableStateOf("5") }
        var priceStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddInventoryDialog = false },
            title = { Text("Create New Inventory Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Measurement Unit (e.g. KG, Ltr, Nos)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = thresholdStr,
                        onValueChange = { thresholdStr = it },
                        label = { Text("Low Stock Alert Threshold Limit") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Default Purchase Price / Rate (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isBlank()) return@Button
                    val thresh = thresholdStr.toDoubleOrNull() ?: 5.0
                    val prc = priceStr.toDoubleOrNull() ?: 0.0
                    val newItem = InventoryItemEntity(
                        id = "inv_${name.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}",
                        restaurantId = activeRestaurant?.id ?: "",
                        name = name.trim(),
                        unit = unit.trim(),
                        purchasePrice = prc,
                        openingStock = 0.0,
                        currentStock = 0.0,
                        lowStockThreshold = thresh
                    )
                    viewModel.saveInventoryItem(newItem)
                    Toast.makeText(context, "Item \"$name\" created successfully!", Toast.LENGTH_SHORT).show()
                    showAddInventoryDialog = false
                }) {
                    Text("Create Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddInventoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Inventory Item Dialog
    itemToEdit?.let { item ->
        var name by remember { mutableStateOf(item.name) }
        var unit by remember { mutableStateOf(item.unit) }
        var thresholdStr by remember { mutableStateOf(item.lowStockThreshold.toString()) }
        var priceStr by remember { mutableStateOf(item.purchasePrice.toString()) }

        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = { Text("Edit Inventory Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Measurement Unit") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = thresholdStr,
                        onValueChange = { thresholdStr = it },
                        label = { Text("Low Stock Alert Threshold Limit") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Default Purchase Price / Rate (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isBlank()) return@Button
                    val thresh = thresholdStr.toDoubleOrNull() ?: item.lowStockThreshold
                    val prc = priceStr.toDoubleOrNull() ?: item.purchasePrice
                    val updated = item.copy(
                        name = name.trim(),
                        unit = unit.trim(),
                        purchasePrice = prc,
                        lowStockThreshold = thresh,
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.saveInventoryItem(updated)
                    Toast.makeText(context, "Item updated successfully!", Toast.LENGTH_SHORT).show()
                    itemToEdit = null
                }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Record Custom Expense Dialog
    if (showAddExpenseDialog) {
        var categoryId by remember { mutableStateOf(expenseCategories.firstOrNull()?.id ?: "") }
        var categoryName by remember { mutableStateOf(expenseCategories.firstOrNull()?.name ?: "") }
        var amountStr by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var paymentMethod by remember { mutableStateOf("CASH") }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Record Simple Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (expenseCategories.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = categoryName,
                                onValueChange = {},
                                label = { Text("Expense Category") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { expanded = !expanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                expenseCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            categoryId = cat.id
                                            categoryName = cat.name
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount Paid (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description & Notes") },
                        placeholder = { Text("e.g. Electricity bill for September") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Payment Method:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paymentMethod == "CASH",
                                onClick = { paymentMethod = "CASH" },
                                label = { Text("CASH") }
                            )
                            FilterChip(
                                selected = paymentMethod == "UPI",
                                onClick = { paymentMethod = "UPI" },
                                label = { Text("UPI") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0 || categoryId.isBlank()) return@Button

                    viewModel.saveExpense(
                        restaurantId = activeRestaurant?.id ?: "",
                        categoryId = categoryId,
                        categoryName = categoryName,
                        amount = amt,
                        paymentMethod = paymentMethod,
                        description = description.trim(),
                        timestamp = System.currentTimeMillis()
                    )

                    Toast.makeText(context, "Expense of ₹$amt recorded successfully! 🥳", Toast.LENGTH_SHORT).show()
                    showAddExpenseDialog = false
                }) {
                    Text("Save Expense")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Custom Expense Category Dialog
    if (showAddExpenseCatDialog) {
        var catName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddExpenseCatDialog = false },
            title = { Text("Create Expense Category", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Rent, Salaries, Electricity") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (catName.isBlank()) return@Button
                    viewModel.saveExpenseCategory(catName.trim())
                    Toast.makeText(context, "Category \"$catName\" created successfully!", Toast.LENGTH_SHORT).show()
                    showAddExpenseCatDialog = false
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseCatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Open Daily Cash Register Dialog
    if (showOpenRegisterDialog) {
        var openingCashStr by remember { mutableStateOf("0") }

        AlertDialog(
            onDismissRequest = { showOpenRegisterDialog = false },
            title = { Text("Open Register & Add Cash Float", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Enter opening physical cash present in the drawer to open register.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = openingCashStr,
                        onValueChange = { openingCashStr = it },
                        label = { Text("Opening Cash Float (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cash = openingCashStr.toDoubleOrNull() ?: 0.0
                    viewModel.openCashRegister(openingFloat = cash, notes = "Opened for today")
                    Toast.makeText(context, "Daily register opened successfully with ₹$cash!", Toast.LENGTH_SHORT).show()
                    showOpenRegisterDialog = false
                }) {
                    Text("Open Register")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenRegisterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Close Daily Cash Register Dialog
    registerToClose?.let { reg ->
        var closingCashStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { registerToClose = null },
            title = { Text("Close Register & Enter Cash Count", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Enter final physical cash count in drawer to close register and calculate shortage or overage.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = closingCashStr,
                        onValueChange = { closingCashStr = it },
                        label = { Text("Actual Cash in Drawer (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cash = closingCashStr.toDoubleOrNull() ?: 0.0
                    viewModel.closeCashRegister(reg.id, cash, notes = "Closed register")
                    Toast.makeText(context, "Daily register closed successfully!", Toast.LENGTH_SHORT).show()
                    registerToClose = null
                }) {
                    Text("Close Register")
                }
            },
            dismissButton = {
                TextButton(onClick = { registerToClose = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialogs
    itemToDelete?.let { item ->
        DeleteConfirmationDialog(
            title = "Delete Inventory Item?",
            itemName = item.name,
            onDismiss = { itemToDelete = null },
            onConfirm = {
                viewModel.deleteInventoryItem(item.id)
                itemToDelete = null
                Toast.makeText(context, "Deleted \"${item.name}\"", Toast.LENGTH_SHORT).show()
            }
        )
    }

    expenseToDelete?.let { exp ->
        DeleteConfirmationDialog(
            title = "Delete Expense?",
            itemName = "${exp.categoryName} (₹${String.format(Locale.US, "%.2f", exp.amount)})",
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                viewModel.deleteExpense(exp.id)
                expenseToDelete = null
                Toast.makeText(context, "Expense deleted and stock reverted!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Multi-Item Purchase full screen overlay
    if (showMultiItemPurchaseScreen) {
        MultiItemPurchaseScreen(
            allInventory = allInventory,
            viewModel = viewModel,
            restaurantId = activeRestaurant?.id ?: "",
            onDismiss = { showMultiItemPurchaseScreen = false }
        )
    }

    // Batch details full screen overlay
    itemForBatchDetail?.let { item ->
        BatchDetailScreen(
            item = item,
            allBatches = allBatches,
            viewModel = viewModel,
            onDismiss = { itemForBatchDetail = null }
        )
    }

    // Vendor Ledger full screen overlay
    if (showVendorLedgerScreen) {
        VendorLedgerScreen(
            allBatches = allBatches,
            allExpenses = allExpenses,
            viewModel = viewModel,
            restaurantId = activeRestaurant?.id ?: "",
            onDismiss = { showVendorLedgerScreen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiItemPurchaseScreen(
    allInventory: List<InventoryItemEntity>,
    viewModel: MainViewModel,
    restaurantId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var vendorName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") } // CASH, UPI
    var paymentStatus by remember { mutableStateOf("FULLY_PAID") } // FULLY_PAID, UNPAID_CREDIT
    var grnTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var showGrnDatePicker by remember { mutableStateOf(false) }

    // Map to track inputs per item ID
    val quantitiesMap = remember { mutableStateMapOf<String, String>() }
    val ratesMap = remember { mutableStateMapOf<String, String>() }

    // Quick add new item state
    var showQuickAddDialog by remember { mutableStateOf(false) }

    val filteredInventory = remember(allInventory, searchQuery) {
        if (searchQuery.isBlank()) allInventory
        else allInventory.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Calculate total bill amount dynamically based on entered items
    val activePurchases = allInventory.mapNotNull { item ->
        val qtyRaw = quantitiesMap[item.id] ?: ""
        val rateRaw = ratesMap[item.id] ?: ""
        val qty = qtyRaw.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
        val rate = rateRaw.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
        if (qty > 0.0 && rate > 0.0) {
            MultiItemPurchaseInput(
                itemId = item.id,
                itemName = item.name,
                quantity = qty,
                rate = rate,
                unit = item.unit
            )
        } else null
    }
    val totalBillAmount = activePurchases.sumOf { it.quantity * it.rate }

    val paidAmount = when (paymentStatus) {
        "FULLY_PAID" -> totalBillAmount
        "UNPAID_CREDIT" -> 0.0
        else -> totalBillAmount
    }
    val dueAmount = (totalBillAmount - paidAmount).coerceAtLeast(0.0)

    // Pre-populate rate hint if user hasn't typed anything
    LaunchedEffect(allInventory) {
        allInventory.forEach { item ->
            if (!ratesMap.containsKey(item.id) && item.purchasePrice > 0.0) {
                ratesMap[item.id] = String.format(Locale.US, "%.2f", item.purchasePrice).replace(".00", "")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Record Bulk Stock Purchase", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = { showQuickAddDialog = true },
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CaramelWarm)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = CaramelWarm)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Item (➕)", fontSize = 11.sp, color = CaramelWarm, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            },
            bottomBar = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Selected Items: ${activePurchases.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Total Amount: ₹${String.format(Locale.US, "%.2f", totalBillAmount).replace(".00", "")}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = ForestGreen
                            )
                        }

                        if (dueAmount > 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Outstanding Dues:", fontSize = 12.sp, color = TableOccupiedRed, fontWeight = FontWeight.Bold)
                                Text("₹${String.format(Locale.US, "%.2f", dueAmount).replace(".00", "")}", fontSize = 12.sp, color = TableOccupiedRed, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (activePurchases.isEmpty()) {
                                    Toast.makeText(context, "Please enter quantity and rate for at least one item!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                viewModel.recordMultiItemPurchase(
                                    restaurantId = restaurantId,
                                    purchasedItems = activePurchases,
                                    paymentMethod = paymentMethod,
                                    paymentStatus = paymentStatus,
                                    paidAmount = paidAmount,
                                    dueAmount = dueAmount,
                                    vendorName = vendorName.trim(),
                                    invoiceNo = invoiceNo.trim(),
                                    notes = notes.trim(),
                                    timestamp = grnTimestamp
                                )
                                Toast.makeText(context, "Purchases and stock levels recorded successfully!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepAmber)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Record Purchases", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        ) { paddingValues ->
            // Native Date Picker Popup
            if (showGrnDatePicker) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = grnTimestamp
                android.app.DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val selectedCal = Calendar.getInstance()
                        selectedCal.set(Calendar.YEAR, year)
                        selectedCal.set(Calendar.MONTH, month)
                        selectedCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        grnTimestamp = selectedCal.timeInMillis
                        showGrnDatePicker = false
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setOnDismissListener { showGrnDatePicker = false }
                    show()
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Invoice & Vendor Details Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Invoice & Vendor Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GRN Date (तारीख):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                OutlinedButton(
                                    onClick = { showGrnDatePicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(grnTimestamp)), fontSize = 12.sp)
                                }
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = vendorName,
                                    onValueChange = { vendorName = it },
                                    label = { Text("Vendor Name") },
                                    placeholder = { Text("e.g. Krishna Dairy") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = invoiceNo,
                                    onValueChange = { invoiceNo = it },
                                    label = { Text("Invoice No") },
                                    placeholder = { Text("e.g. GST-452") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Payment Status:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = paymentStatus == "FULLY_PAID",
                                        onClick = { paymentStatus = "FULLY_PAID" },
                                        label = { Text("Paid") }
                                    )
                                    FilterChip(
                                        selected = paymentStatus == "UNPAID_CREDIT",
                                        onClick = { paymentStatus = "UNPAID_CREDIT" },
                                        label = { Text("Credit (On Dues)") }
                                    )
                                }
                            }

                            if (paymentStatus != "UNPAID_CREDIT") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Payment Method:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = paymentMethod == "CASH",
                                            onClick = { paymentMethod = "CASH" },
                                            label = { Text("CASH") }
                                        )
                                        FilterChip(
                                            selected = paymentMethod == "UPI",
                                            onClick = { paymentMethod = "UPI" },
                                            label = { Text("UPI") }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes & Comments") },
                                placeholder = { Text("e.g. Bulk purchase dairy supplies") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                // Select Items Section
                item {
                    Text("Select Items to Purchase:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Item...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (filteredInventory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No items match your search!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filteredInventory) { item ->
                        val qty = quantitiesMap[item.id] ?: ""
                        val rate = ratesMap[item.id] ?: ""
                        val isLowStock = item.currentStock <= item.lowStockThreshold

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = if (qty.isNotBlank()) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DeepAmber)) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            if (isLowStock) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(
                                                    color = TableOccupiedRed.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("LOW", color = TableOccupiedRed, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                        }
                                        Text("Current Stock: ${String.format(Locale.US, "%.1f", item.currentStock).replace(".00", "")} ${item.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    val itemTotal = (qty.toDoubleOrNull() ?: 0.0) * (rate.toDoubleOrNull() ?: 0.0)
                                    if (itemTotal > 0.0) {
                                        Text("₹${String.format(Locale.US, "%.1f", itemTotal)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ForestGreen)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = qty,
                                        onValueChange = { quantitiesMap[item.id] = it },
                                        label = { Text("Qty (${item.unit})") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = rate,
                                        onValueChange = { ratesMap[item.id] = it },
                                        label = { Text("Rate (₹)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Add New Item Dialog
    if (showQuickAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newUnit by remember { mutableStateOf("KG") }
        var newThresholdStr by remember { mutableStateOf("5") }
        var newPriceStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("Create New Inventory Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUnit,
                        onValueChange = { newUnit = it },
                        label = { Text("Measurement Unit (e.g. KG, Ltr, Nos)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newThresholdStr,
                        onValueChange = { newThresholdStr = it },
                        label = { Text("Low Stock Alert Threshold limit") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPriceStr,
                        onValueChange = { newPriceStr = it },
                        label = { Text("Default Purchase Price / Rate (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isBlank()) return@Button
                    val thresh = newThresholdStr.toDoubleOrNull() ?: 5.0
                    val prc = newPriceStr.toDoubleOrNull() ?: 0.0
                    val newItem = InventoryItemEntity(
                        id = "inv_${newName.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}",
                        restaurantId = restaurantId,
                        name = newName.trim(),
                        unit = newUnit.trim(),
                        purchasePrice = prc,
                        openingStock = 0.0,
                        currentStock = 0.0,
                        lowStockThreshold = thresh
                    )
                    viewModel.saveInventoryItem(newItem)
                    Toast.makeText(context, "Item \"$newName\" created successfully!", Toast.LENGTH_SHORT).show()
                    showQuickAddDialog = false
                }) {
                    Text("Create Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    item: InventoryItemEntity,
    allBatches: List<InventoryBatchEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Active, 1 = Consumed

    val itemBatches = remember(allBatches, item.id) {
        allBatches.filter { it.inventoryItemId == item.id }
    }

    val activeBatches = remember(itemBatches) {
        itemBatches.filter { it.remainingQuantity > 0.000001 && it.status != "ARCHIVED" && !it.isConsumed }
            .sortedBy { it.timestamp }
    }

    val consumedBatches = remember(itemBatches) {
        itemBatches.filter { it.remainingQuantity <= 0.000001 || it.status == "ARCHIVED" || it.isConsumed }
            .sortedByDescending { it.timestamp }
    }

    val totalValuation = remember(activeBatches) {
        activeBatches.sumOf { it.remainingQuantity * it.purchaseRate }
    }

    var batchToEdit by remember { mutableStateOf<InventoryBatchEntity?>(null) }
    var batchToDelete by remember { mutableStateOf<InventoryBatchEntity?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(item.name, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total Stock Balance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${String.format(Locale.US, "%.2f", item.currentStock).replace(".00", "")} ${item.unit}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (item.currentStock <= item.lowStockThreshold) TableOccupiedRed else MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Stock Valuation", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                Text(
                                    "₹${String.format(Locale.US, "%.2f", totalValuation).replace(".00", "")}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ForestGreen
                                )
                            }
                        }
                    }
                }

                TabRow(selectedTabIndex = selectedSubTab) {
                    Tab(
                        selected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        text = { Text("Active Stock (${activeBatches.size})", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        text = { Text("Consumed History (${consumedBatches.size})", fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedSubTab == 0) {
                        if (activeBatches.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text("No active stock batches available!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(activeBatches) { batch ->
                                val isFirstBatch = activeBatches.firstOrNull()?.id == batch.id
                                val daysOld = ((System.currentTimeMillis() - batch.timestamp) / (1000 * 60 * 60 * 24)).toInt()

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${String.format(Locale.US, "%.2f", batch.remainingQuantity).replace(".00", "")} ${batch.unit} Remaining",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = DeepAmber
                                                )
                                                Text(" (of ${String.format(Locale.US, "%.1f", batch.initialQuantity).replace(".00", "")} ${batch.unit})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            if (isFirstBatch) {
                                                Surface(
                                                    color = ForestGreen.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "📦 Currently Using (FIFO)",
                                                        color = ForestGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Purchase Rate: ₹${String.format(Locale.US, "%.2f", batch.purchaseRate)} / ${batch.unit}", fontSize = 12.sp)
                                            Text("Total Value: ₹${String.format(Locale.US, "%.1f", batch.remainingQuantity * batch.purchaseRate)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                        }

                                        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(batch.timestamp))
                                        Text("Purchase Date: $dateStr (${if (daysOld == 0) "Today" else "$daysOld days ago"})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (batch.notes.isNotBlank()) {
                                            Text("Notes: ${batch.notes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            IconButton(onClick = { batchToEdit = batch }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Batch", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(onClick = { batchToDelete = batch }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Batch", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (consumedBatches.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text("No consumed history recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(consumedBatches) { batch ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${String.format(Locale.US, "%.1f", batch.initialQuantity).replace(".00", "")} ${batch.unit} Fully Consumed",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Surface(
                                                color = Color.LightGray.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("CONSUMED", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(batch.timestamp))
                                        Text("Purchase Rate: ₹${batch.purchaseRate}  •  Date: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (batch.notes.isNotBlank()) {
                                            Text("Notes: ${batch.notes}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    batchToEdit?.let { b ->
        var editQtyStr by remember { mutableStateOf(b.initialQuantity.toString()) }
        var editRateStr by remember { mutableStateOf(b.purchaseRate.toString()) }

        AlertDialog(
            onDismissRequest = { batchToEdit = null },
            title = { Text("Edit Purchase Batch", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Batch ID: ${b.id}", fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = editQtyStr,
                        onValueChange = { editQtyStr = it },
                        label = { Text("Purchase Quantity") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editRateStr,
                        onValueChange = { editRateStr = it },
                        label = { Text("Purchase Rate / Unit (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val q = editQtyStr.toDoubleOrNull() ?: b.initialQuantity
                    val r = editRateStr.toDoubleOrNull() ?: b.purchaseRate
                    viewModel.editInventoryBatch(b.id, q, r)
                    Toast.makeText(context, "Batch and stock balance updated!", Toast.LENGTH_SHORT).show()
                    batchToEdit = null
                    onDismiss()
                }) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { batchToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    batchToDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { batchToDelete = null },
            title = { Text("⚠️ Delete Purchase Batch?", fontWeight = FontWeight.Bold, color = TableOccupiedRed) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Are you sure you want to delete this purchase batch?")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("The remaining quantity of this batch (${String.format(Locale.US, "%.2f", b.remainingQuantity).replace(".00", "")} ${b.unit}) will be subtracted from the inventory balance.", fontWeight = FontWeight.Medium, color = TableOccupiedRed, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInventoryBatch(b.id)
                        Toast.makeText(context, "Purchase batch deleted successfully.", Toast.LENGTH_SHORT).show()
                        batchToDelete = null
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TableOccupiedRed)
                ) {
                    Text("Yes, Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { batchToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorLedgerScreen(
    allBatches: List<InventoryBatchEntity>,
    allExpenses: List<ExpenseEntity>,
    viewModel: MainViewModel,
    restaurantId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vendorRegex = "Vendor:\\s*([^|]+)".toRegex()
    
    val vendorCreditMap = remember(allBatches) {
        val map = mutableMapOf<String, Double>()
        allBatches.forEach { batch ->
            val notes = batch.notes
            if (notes.contains("On Credit", ignoreCase = true) || notes.contains("Credit", ignoreCase = true) || notes.contains("Unpaid", ignoreCase = true)) {
                val match = vendorRegex.find(notes)
                val vendor = match?.groupValues?.get(1)?.trim() ?: "Unspecified Vendor"
                val remValue = batch.remainingQuantity * batch.purchaseRate
                if (remValue > 0.0) {
                    map[vendor] = (map[vendor] ?: 0.0) + remValue
                }
            }
        }
        map
    }

    val totalDues = remember(vendorCreditMap) { vendorCreditMap.values.sum() }

    var vendorToPay by remember { mutableStateOf<String?>(null) }
    var payAmountStr by remember { mutableStateOf("") }
    var payMethod by remember { mutableStateOf("CASH") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Vendor Ledger & Credit Account", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = TableOccupiedRed.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TableOccupiedRed.copy(alpha = 0.4f)))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Outstanding Vendor Dues", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TableOccupiedRed)
                            Text(
                                "₹${String.format(Locale.US, "%.2f", totalDues).replace(".00", "")}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = TableOccupiedRed
                            )
                        }
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = TableOccupiedRed, modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Active Vendor Accounts:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (vendorCreditMap.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text("All dues are cleared! No outstanding balance. 👍", fontWeight = FontWeight.Bold, color = ForestGreen)
                            }
                        }
                    } else {
                        items(vendorCreditMap.keys.toList()) { vendor ->
                            val dues = vendorCreditMap[vendor] ?: 0.0
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(vendor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Outstanding: ₹${String.format(Locale.US, "%.2f", dues).replace(".00", "")}", fontSize = 12.sp, color = TableOccupiedRed, fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(
                                        onClick = {
                                            vendorToPay = vendor
                                            payAmountStr = String.format(Locale.US, "%.2f", dues).replace(".00", "")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear Dues", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    vendorToPay?.let { vendor ->
        AlertDialog(
            onDismissRequest = { vendorToPay = null },
            title = { Text("Clear Vendor Dues", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vendor: $vendor", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = payAmountStr,
                        onValueChange = { payAmountStr = it },
                        label = { Text("Payment Amount (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Payment Method:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = payMethod == "CASH",
                                onClick = { payMethod = "CASH" },
                                label = { Text("CASH") }
                            )
                            FilterChip(
                                selected = payMethod == "UPI",
                                onClick = { payMethod = "UPI" },
                                label = { Text("UPI") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = payAmountStr.toDoubleOrNull() ?: 0.0
                    if (amount <= 0.0) return@Button
                    
                    viewModel.saveExpense(
                        restaurantId = restaurantId,
                        categoryId = "expcat_inventory_purchase",
                        categoryName = "Inventory Purchase",
                        amount = amount,
                        paymentMethod = payMethod,
                        description = "Cleared credit of ₹$amount to Vendor: $vendor",
                        timestamp = System.currentTimeMillis()
                    )

                    Toast.makeText(context, "Vendor payment of ₹$amount recorded successfully!", Toast.LENGTH_SHORT).show()
                    vendorToPay = null
                    onDismiss()
                }) {
                    Text("Confirm Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { vendorToPay = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RegisterMetricRow(label: String, value: Double, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            "₹${String.format(Locale.US, "%.2f", value)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordWastageDialog(
    inventoryItems: List<InventoryItemEntity>,
    onDismiss: () -> Unit,
    onConfirm: (itemId: String, quantity: Double, reason: String, notes: String) -> Unit
) {
    var selectedItem by remember { mutableStateOf(inventoryItems.firstOrNull()) }
    var itemMenuExpanded by remember { mutableStateOf(false) }
    var quantityStr by remember { mutableStateOf("") }
    val reasons = listOf("Expired / शिळे", "Burnt / जळालेले", "Spilled / सांडलेले", "Tasting / QC", "Customer Return", "Other Damage")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var notes by remember { mutableStateOf("") }

    val qty = quantityStr.toDoubleOrNull() ?: 0.0
    val itemRate = selectedItem?.purchasePrice ?: 0.0
    val estimatedLoss = qty * itemRate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFDC2626))
                Spacer(Modifier.width(8.dp))
                Text("Record Wastage & Loss", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Deduct damaged, expired, or spoiled ingredients directly from stock and track financial loss.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Item Selector
                ExposedDropdownMenuBox(
                    expanded = itemMenuExpanded,
                    onExpandedChange = { itemMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedItem?.name ?: "Select Raw Material",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Inventory Item") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = itemMenuExpanded,
                        onDismissRequest = { itemMenuExpanded = false }
                    ) {
                        inventoryItems.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Stock: ${item.currentStock} ${item.unit} • ₹${item.purchasePrice}/${item.unit}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedItem = item
                                    itemMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Quantity & Unit
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Quantity Wasted (${selectedItem?.unit ?: "Qty"})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        selectedItem?.let {
                            Text("Current in stock: ${it.currentStock} ${it.unit}")
                        }
                    }
                )

                // Reason selector chips
                Text("Reason for Wastage:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val chunked = reasons.chunked(2)
                    chunked.forEach { rowReasons ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowReasons.forEach { reason ->
                                FilterChip(
                                    selected = selectedReason == reason,
                                    onClick = { selectedReason = reason },
                                    label = { Text(reason, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFEE2E2),
                                        selectedLabelColor = Color(0xFF991B1B)
                                    )
                                )
                            }
                        }
                    }
                }

                // Additional Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Optional Notes (Chef/Staff)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Estimated Loss Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Estimated Cost Loss:", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF991B1B))
                        Text(
                            "₹${String.format(Locale.US, "%.2f", estimatedLoss)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val item = selectedItem
                    if (item != null && qty > 0.0) {
                        onConfirm(item.id, qty, selectedReason, notes)
                    }
                },
                enabled = selectedItem != null && qty > 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Confirm & Deduct Stock", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
