package com.example.ui.screens.reports

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.local.entity.*
import com.example.ui.MainViewModel
import com.example.ui.components.VisualSummaryDashboard
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.FoodCostCalculator
import com.example.util.ReportExportUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class ItemSalesMetric(
    val dishName: String,
    val quantity: Int,
    val totalRevenue: Double,
    val averagePrice: Double,
    val revenuePercent: Float,
    val category: String = "General"
) {
    val itemName: String get() = dishName
    val revenue: Double get() = totalRevenue
    val avgPrice: Double get() = averagePrice
    val percentageOfTotal: Float get() = revenuePercent
}

data class DaySalesMetric(
    val dateString: String,
    val billCount: Int,
    val sales: Double,
    val foodCost: Double,
    val grossProfit: Double
)

enum class DateRangePreset(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week (7D)"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    CUSTOM("Custom 📅")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    isEmbeddedInSettings: Boolean = false,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedPreset by remember { mutableStateOf(DateRangePreset.TODAY) }
    var selectedTab by remember { mutableIntStateOf(0) }

    if (isEmbeddedInSettings && onBackClick != null) {
        BackHandler {
            onBackClick()
        }
    }

    val allBills by viewModel.allBills.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allRecipes by viewModel.allRecipes.collectAsState()
    val allInventory by viewModel.allInventory.collectAsState()
    val allBatches by viewModel.allBatches.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val allCustomerPayments by viewModel.allCustomerPayments.collectAsState()
    val allStockTransactions by viewModel.allStockTransactions.collectAsState()
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()

    val safeRestaurant = remember(activeRestaurant) {
        activeRestaurant ?: RestaurantEntity(
            id = "default",
            name = "BBC FOOD HUB",
            address = "Washim",
            phone = ""
        )
    }

    var customStartDate by remember { mutableStateOf(DateUtils.getStartOfDay()) }
    var customEndDate by remember { mutableStateOf(DateUtils.getEndOfDay()) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    // Compute effective date range based on preset
    val (startDate, endDate) = remember(selectedPreset, customStartDate, customEndDate) {
        when (selectedPreset) {
            DateRangePreset.TODAY -> DateUtils.getStartOfDay() to DateUtils.getEndOfDay()
            DateRangePreset.YESTERDAY -> DateUtils.getYesterdayStart() to DateUtils.getYesterdayEnd()
            DateRangePreset.THIS_WEEK -> DateUtils.getStartOfWeek() to DateUtils.getEndOfDay()
            DateRangePreset.THIS_MONTH -> DateUtils.getStartOfMonth() to DateUtils.getEndOfDay()
            DateRangePreset.LAST_MONTH -> DateUtils.getLastMonthStart() to DateUtils.getLastMonthEnd()
            DateRangePreset.CUSTOM -> customStartDate to customEndDate
        }
    }

    // Filter data strictly by selected universal date range
    val filteredBills = remember(allBills, startDate, endDate) {
        allBills.filter { it.isSettled && it.billTimestamp in startDate..endDate }
    }
    val filteredExpenses = remember(allExpenses, startDate, endDate) {
        allExpenses.filter { it.timestamp in startDate..endDate && it.categoryId != "expcat_inventory_purchase" && !it.categoryName.lowercase().contains("inventory") }
    }
    val filteredStockTransactions = remember(allStockTransactions, startDate, endDate) {
        allStockTransactions.filter { it.timestamp in startDate..endDate }
    }
    val filteredWastageTransactions = remember(filteredStockTransactions) {
        filteredStockTransactions.filter { it.transactionType.equals("WASTAGE", ignoreCase = true) }
    }

    // Key Aggregated Metrics
    val totalGrossSales = remember(filteredBills) { filteredBills.sumOf { it.totalAmount } }
    val totalSettledBills = remember(filteredBills) { filteredBills.size }
    val avgTicketValue = if (totalSettledBills > 0) totalGrossSales / totalSettledBills else 0.0
    val totalFoodCost = remember(filteredBills) { filteredBills.sumOf { it.totalFoodCost } }
    val totalWastageLoss = remember(filteredWastageTransactions) {
        filteredWastageTransactions.sumOf { it.quantity * it.unitRate }
    }
    val totalExpensesAmount = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val netOperatingProfit = totalGrossSales - totalFoodCost - totalWastageLoss - totalExpensesAmount

    val cashSales = remember(filteredBills) {
        filteredBills.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount }
    }
    val upiSales = remember(filteredBills) {
        filteredBills.filter { it.paymentMethod == "UPI" }.sumOf { it.totalAmount }
    }
    val khataSales = remember(filteredBills) {
        filteredBills.filter { it.paymentMethod == "CREDIT" || it.paymentMethod == "KHATA" }.sumOf { it.totalAmount }
    }
    val dineInCount = remember(filteredBills) { filteredBills.count { it.orderType == "DINE_IN" } }
    val takeawayCount = remember(filteredBills) { filteredBills.count { it.orderType == "TAKEAWAY" } }

    // Complimentary items in period
    val complimentaryItemsList = remember(filteredBills) {
        val list = mutableListOf<Pair<BillItem, BillEntity>>()
        filteredBills.forEach { bill ->
            bill.items.filter { it.isFree || it.totalPrice == 0.0 }.forEach { item ->
                list.add(item to bill)
            }
        }
        list
    }
    val totalComplimentaryValue = remember(complimentaryItemsList) {
        complimentaryItemsList.sumOf { (item, _) -> item.unitPrice * item.quantity }
    }

    // Top Selling Dishes in period
    val topSellingList = remember(filteredBills) {
        val map = mutableMapOf<String, Int>()
        filteredBills.forEach { b ->
            b.items.forEach { item ->
                map[item.dishName] = (map[item.dishName] ?: 0) + item.quantity
            }
        }
        map.toList().sortedByDescending { it.second }
    }

    // Custom Date Range Dialog
    if (showCustomDateDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            title = { Text("Select Custom Date Range", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // From Date
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("From (Start Date):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(DateUtils.formatDate(customStartDate), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            TextButton(onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = customStartDate }
                                DatePickerDialog(context, { _, y, m, d ->
                                    cal.set(y, m, d, 0, 0, 0)
                                    customStartDate = cal.timeInMillis
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            }) {
                                Text("Change")
                            }
                        }
                    }

                    // To Date
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("To (End Date):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(DateUtils.formatDate(customEndDate), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            TextButton(onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = customEndDate }
                                DatePickerDialog(context, { _, y, m, d ->
                                    cal.set(y, m, d, 23, 59, 59)
                                    customEndDate = cal.timeInMillis
                                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                            }) {
                                Text("Change")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPreset = DateRangePreset.CUSTOM
                        showCustomDateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
                ) {
                    Text("Apply Filter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEmbeddedInSettings) "Master Reports Hub" else "Reports & Intelligence",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${DateUtils.formatDate(startDate)} - ${DateUtils.formatDate(endDate)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (isEmbeddedInSettings && onBackClick != null) {
                        IconButton(onClick = { onBackClick() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Settings",
                                tint = CaramelWarm
                            )
                        }
                    }
                },
                actions = {
                    // 1-Click WhatsApp Summary
                    IconButton(onClick = {
                        shareReportOnWhatsApp(
                            context = context,
                            reportTitle = selectedPreset.label,
                            restaurantName = safeRestaurant.name,
                            startDateStr = DateUtils.formatDate(startDate),
                            endDateStr = DateUtils.formatDate(endDate),
                            totalSales = totalGrossSales,
                            cashSales = cashSales,
                            upiSales = upiSales,
                            khataSales = khataSales,
                            totalBills = totalSettledBills,
                            avgTicket = avgTicketValue,
                            wastageLoss = totalWastageLoss,
                            complimentaryValue = totalComplimentaryValue,
                            totalExpenses = totalExpensesAmount,
                            netProfit = netOperatingProfit,
                            topDishes = topSellingList
                        )
                    }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share via WhatsApp",
                            tint = Color(0xFF16A34A)
                        )
                    }

                    // PDF / CSV Export
                    IconButton(onClick = {
                        try {
                            val file = ReportExportUtil.generateSalesReportPdf(
                                context = context,
                                reportTitle = "${selectedPreset.label} Report",
                                bills = filteredBills,
                                expenses = filteredExpenses,
                                restaurant = safeRestaurant
                            )
                            ReportExportUtil.shareReportFile(
                                context = context,
                                file = file,
                                mimeType = "application/pdf",
                                title = "${safeRestaurant.name} ${selectedPreset.label} Report"
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF",
                            tint = CaramelWarm
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
        ) {
            // 1. Universal Date Filter Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DateRangePreset.values()) { preset ->
                        val isSelected = selectedPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (preset == DateRangePreset.CUSTOM) {
                                    showCustomDateDialog = true
                                } else {
                                    selectedPreset = preset
                                }
                            },
                            label = {
                                Text(
                                    text = preset.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (preset == DateRangePreset.CUSTOM) {
                                { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CaramelWarm,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 2. Master Reports Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = CrispWhite,
                contentColor = WarmAmber
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("📊 Overview") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("🍔 Product Sales") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("👥 Party & Khata") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("🗑️ Wastage & Loss") })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("🎁 Complimentary") })
                Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text("🛵 Orders & Pay") })
                Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 }, text = { Text("🏛️ GST & Tax") })
                Tab(selected = selectedTab == 7, onClick = { selectedTab = 7 }, text = { Text("💰 Net P&L") })
            }

            // 3. Tab Contents
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ExecutiveOverviewTab(
                        bills = filteredBills,
                        expenses = filteredExpenses,
                        totalSales = totalGrossSales,
                        totalBills = totalSettledBills,
                        avgTicket = avgTicketValue,
                        foodCost = totalFoodCost,
                        wastageLoss = totalWastageLoss,
                        netProfit = netOperatingProfit,
                        cashSales = cashSales,
                        upiSales = upiSales,
                        dineInCount = dineInCount,
                        takeawayCount = takeawayCount,
                        topDishes = topSellingList
                    )
                    1 -> ProductSalesTab(
                        bills = filteredBills,
                        menuItems = menuItems
                    )
                    2 -> PartyKhataReportTab(
                        bills = filteredBills,
                        allCustomers = allCustomers,
                        customerPayments = allCustomerPayments,
                        context = context
                    )
                    3 -> WastageLossReportTab(
                        wastageTransactions = filteredWastageTransactions,
                        allInventory = allInventory,
                        viewModel = viewModel
                    )
                    4 -> ComplimentaryDiscountsTab(
                        complimentaryItems = complimentaryItemsList,
                        bills = filteredBills,
                        totalComplimentaryValue = totalComplimentaryValue
                    )
                    5 -> OrderTypePaymentTab(
                        bills = filteredBills,
                        cashSales = cashSales,
                        upiSales = upiSales,
                        khataSales = khataSales,
                        dineInCount = dineInCount,
                        takeawayCount = takeawayCount
                    )
                    6 -> TaxGstSummaryTab(
                        bills = filteredBills,
                        context = context,
                        restaurant = safeRestaurant,
                        dateLabel = selectedPreset.label
                    )
                    7 -> ExecutivePnLReportTab(
                        viewModel = viewModel,
                        allBills = allBills,
                        allExpenses = allExpenses,
                        allInventory = allInventory,
                        allBatches = allBatches,
                        restaurant = safeRestaurant
                    )
                }
            }
        }
    }
}

// =========================================================================
// TAB 0: EXECUTIVE OVERVIEW & VISUAL TRENDS
// =========================================================================
@Composable
fun ExecutiveOverviewTab(
    bills: List<BillEntity>,
    expenses: List<ExpenseEntity>,
    totalSales: Double,
    totalBills: Int,
    avgTicket: Double,
    foodCost: Double,
    wastageLoss: Double,
    netProfit: Double,
    cashSales: Double,
    upiSales: Double,
    dineInCount: Int,
    takeawayCount: Int,
    topDishes: List<Pair<String, Int>>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // KPI Cards Row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard(
                    title = "Total Revenue",
                    value = "₹${String.format(Locale.US, "%.2f", totalSales)}",
                    subtitle = "$totalBills Settled Bills",
                    icon = Icons.Default.CurrencyRupee,
                    color = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Avg. Ticket (ATV)",
                    value = "₹${String.format(Locale.US, "%.2f", avgTicket)}",
                    subtitle = "Per Customer Order",
                    icon = Icons.Default.ShoppingCart,
                    color = CaramelWarm,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard(
                    title = "Net Profit (Est.)",
                    value = "₹${String.format(Locale.US, "%.2f", netProfit)}",
                    subtitle = if (totalSales > 0) "${String.format(Locale.US, "%.1f", (netProfit / totalSales) * 100)}% Margin" else "0% Margin",
                    icon = Icons.Default.TrendingUp,
                    color = if (netProfit >= 0) Color(0xFF15803D) else Color(0xFFDC2626),
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Wastage Loss",
                    value = "₹${String.format(Locale.US, "%.2f", wastageLoss)}",
                    subtitle = "Spoilage / Burnt",
                    icon = Icons.Default.DeleteSweep,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Cash vs UPI Split Bar
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Payment Mode Split", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("💵 Cash: ₹${String.format(Locale.US, "%.2f", cashSales)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("📱 UPI: ₹${String.format(Locale.US, "%.2f", upiSales)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB))
                    }
                    val totalPay = (cashSales + upiSales).coerceAtLeast(1.0)
                    val cashFraction = (cashSales / totalPay).toFloat().coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFE2E8F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(cashFraction.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFF16A34A))
                        )
                        Box(
                            modifier = Modifier
                                .weight((1f - cashFraction).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFF2563EB))
                        )
                    }
                }
            }
        }

        // Visual Recharts-Style Chart
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Sales Performance & Volume", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    VisualSummaryDashboard(bills = bills)
                }
            }
        }

        // Top 5 Star Items
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⭐ Top Selling Star Dishes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (topDishes.isEmpty()) {
                        Text("No sales recorded in this period.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        topDishes.take(5).forEachIndexed { index, (name, qty) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = CaramelWarm.copy(alpha = 0.15f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                Text("$qty ordered", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EspressoDark)
                            }
                            if (index < 4 && index < topDishes.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 1: PRODUCT & CATEGORY SALES REPORT
// =========================================================================
@Composable
fun ProductSalesTab(
    bills: List<BillEntity>,
    menuItems: List<MenuItemEntity>
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("QUANTITY") } // QUANTITY or REVENUE

    val categories = remember(menuItems) {
        listOf("All") + menuItems.map { it.categoryId }.distinct()
    }

    // Aggregate items across all filtered bills
    val itemMetrics = remember(bills, menuItems) {
        val countMap = mutableMapOf<String, Int>()
        val revMap = mutableMapOf<String, Double>()
        val categoryMap = menuItems.associate { it.name.lowercase() to it.categoryId }

        bills.forEach { b ->
            b.items.forEach { item ->
                countMap[item.dishName] = (countMap[item.dishName] ?: 0) + item.quantity
                revMap[item.dishName] = (revMap[item.dishName] ?: 0.0) + item.totalPrice
            }
        }

        val totalRev = revMap.values.sum().coerceAtLeast(1.0)
        countMap.map { (name, qty) ->
            val rev = revMap[name] ?: 0.0
            val avg = if (qty > 0) rev / qty else 0.0
            val pct = ((rev / totalRev) * 100).toFloat()
            val cat = categoryMap[name.lowercase()] ?: "General"
            ItemSalesMetric(name, qty, rev, avg, pct, cat)
        }
    }

    val displayedItems = remember(itemMetrics, searchQuery, selectedCategoryFilter, sortBy) {
        itemMetrics.filter {
            (searchQuery.isBlank() || it.itemName.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategoryFilter == "All" || it.category.equals(selectedCategoryFilter, ignoreCase = true))
        }.let { list ->
            if (sortBy == "REVENUE") list.sortedByDescending { it.revenue }
            else list.sortedByDescending { it.quantity }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search dish name...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        sortBy = if (sortBy == "QUANTITY") "REVENUE" else "QUANTITY"
                    }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = CaramelWarm
                        )
                    }
                }
            }
        }

        // Item List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${displayedItems.size} Dishes Found • Sorted by $sortBy",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Total: ₹${String.format(Locale.US, "%.2f", displayedItems.sumOf { it.revenue })}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A)
                    )
                }
            }

            if (displayedItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No items sold in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(displayedItems) { metric ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(metric.itemName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Category: ${metric.category} • Avg: ₹${String.format(Locale.US, "%.2f", metric.avgPrice)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "₹${String.format(Locale.US, "%.2f", metric.revenue)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF16A34A)
                                )
                                Text(
                                    "${metric.quantity} sold (${String.format(Locale.US, "%.1f", metric.percentageOfTotal)}%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CaramelWarm
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (metric.percentageOfTotal / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = CaramelWarm,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 2: PARTY & CUSTOMER KHATA REPORT
// =========================================================================
@Composable
fun PartyKhataReportTab(
    bills: List<BillEntity>,
    allCustomers: List<CustomerEntity>,
    customerPayments: List<CustomerPaymentEntity>,
    context: Context
) {
    var searchQuery by remember { mutableStateOf("") }

    // Group bills by customer phone or name
    val customerMetrics = remember(bills, allCustomers, customerPayments) {
        val map = mutableMapOf<String, Triple<String, Int, Double>>() // Phone -> (Name, Visits, TotalBilled)

        bills.forEach { b ->
            val phone = b.customerPhone.ifBlank { b.customerName.ifBlank { "Walk-in" } }
            val existing = map[phone] ?: Triple(b.customerName.ifBlank { "Walk-in Guest" }, 0, 0.0)
            map[phone] = Triple(
                if (b.customerName.isNotBlank()) b.customerName else existing.first,
                existing.second + 1,
                existing.third + b.totalAmount
            )
        }

        allCustomers.map { c ->
            val phone = c.contactNumber.ifBlank { c.name }
            val billData = map[phone] ?: Triple(c.name, 0, 0.0)
            val creditBillsTotal = bills.filter { (it.customerPhone == c.contactNumber || it.customerName == c.name) && (it.paymentMethod == "CREDIT" || it.paymentMethod == "KHATA") }.sumOf { it.totalAmount }
            val totalPaid = customerPayments.filter { it.customerPhone == c.contactNumber || it.customerId == c.id }.sumOf { it.amount }
            val calculatedDue = (creditBillsTotal - totalPaid).coerceAtLeast(0.0)

            PartyMetric(
                name = c.name,
                phone = c.contactNumber,
                visitsInPeriod = billData.second,
                totalBilledInPeriod = billData.third,
                currentDueBalance = calculatedDue,
                isCreditCustomer = c.isCreditCustomer
            )
        }
    }

    val filteredList = remember(customerMetrics, searchQuery) {
        customerMetrics.filter {
            searchQuery.isBlank() ||
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }.sortedByDescending { it.totalBilledInPeriod }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search party / customer name or phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                singleLine = true
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Showing ${filteredList.size} Customers / Parties",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            items(filteredList) { party ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(party.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (party.phone.isNotBlank()) "📞 ${party.phone}" else "No phone",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 1-Click WhatsApp Statement
                            IconButton(onClick = {
                                val statement = "Hello ${party.name},\nHere is your billing statement from BBC FOOD HUB:\nTotal Orders in period: ${party.visitsInPeriod}\nTotal Billed: ₹${String.format(Locale.US, "%.2f", party.totalBilledInPeriod)}\nCurrent Outstanding Khata Due: ₹${String.format(Locale.US, "%.2f", party.currentDueBalance)}\nThank you for dining with us!"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, statement)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Send Statement via WhatsApp"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No sharing app found", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF16A34A))
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Orders: ${party.visitsInPeriod} bills", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Billed: ₹${String.format(Locale.US, "%.2f", party.totalBilledInPeriod)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                            Text(
                                "Due: ₹${String.format(Locale.US, "%.2f", party.currentDueBalance)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (party.currentDueBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class PartyMetric(
    val name: String,
    val phone: String,
    val visitsInPeriod: Int,
    val totalBilledInPeriod: Double,
    val currentDueBalance: Double,
    val isCreditCustomer: Boolean
)

// =========================================================================
// TAB 3: WASTAGE & LOSS REPORT
// =========================================================================
@Composable
fun WastageLossReportTab(
    wastageTransactions: List<StockTransactionEntity>,
    allInventory: List<InventoryItemEntity>,
    viewModel: MainViewModel
) {
    var showRecordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val totalLoss = remember(wastageTransactions) {
        wastageTransactions.sumOf { it.quantity * it.unitRate }
    }

    if (showRecordDialog) {
        com.example.ui.screens.inventory.RecordWastageDialog(
            inventoryItems = allInventory,
            onDismiss = { showRecordDialog = false },
            onConfirm = { itemId, qty, reason, notes ->
                viewModel.recordWastage(itemId, qty, reason, notes)
                Toast.makeText(context, "Wastage recorded successfully!", Toast.LENGTH_SHORT).show()
                showRecordDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Wastage Loss", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF991B1B))
                            Text(
                                "₹${String.format(Locale.US, "%.2f", totalLoss)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = Color(0xFFDC2626)
                            )
                        }

                        Button(
                            onClick = { showRecordDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Record Wastage")
                        }
                    }
                    Text(
                        "${wastageTransactions.size} wastage entries recorded in this period.",
                        fontSize = 12.sp,
                        color = Color(0xFF7F1D1D)
                    )
                }
            }
        }

        // List of Wastage Entries
        item {
            Text("Wastage Log History", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (wastageTransactions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text("No wastage or damage recorded in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(wastageTransactions) { txn ->
            val lossVal = txn.quantity * txn.unitRate
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(txn.itemName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Reason: ${txn.notes.ifBlank { "Damage / Spoilage" }}",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            DateUtils.formatDateTime(txn.timestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "-₹${String.format(Locale.US, "%.2f", lossVal)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFDC2626)
                        )
                        Text(
                            "Qty: ${txn.quantity} (₹${txn.unitRate}/unit)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: COMPLIMENTARY & OFFERS REPORT
// =========================================================================
@Composable
fun ComplimentaryDiscountsTab(
    complimentaryItems: List<Pair<BillItem, BillEntity>>,
    bills: List<BillEntity>,
    totalComplimentaryValue: Double
) {
    val totalDiscountGiven = remember(bills) { bills.sumOf { it.discountAmount } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard(
                    title = "Complimentary Items",
                    value = "₹${String.format(Locale.US, "%.2f", totalComplimentaryValue)}",
                    subtitle = "${complimentaryItems.size} Free Items Served",
                    icon = Icons.Default.CardGiftcard,
                    color = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Coupons & Discounts",
                    value = "₹${String.format(Locale.US, "%.2f", totalDiscountGiven)}",
                    subtitle = "Offers & Passes",
                    icon = Icons.Default.LocalOffer,
                    color = CaramelWarm,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text("🎁 Free / On-the-House Items Served", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (complimentaryItems.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text("No complimentary items given in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(complimentaryItems) { (item, bill) ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.dishName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
                                Text("FREE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        val note = item.cookingNotes.ifBlank { item.notes }
                        Text(
                            if (note.isNotBlank()) "Reason: $note" else "Bill #${bill.billNumber} • ${bill.customerName.ifBlank { "Guest" }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "₹0.00",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            "Worth: ₹${String.format(Locale.US, "%.2f", item.unitPrice * item.quantity)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 5: ORDER TYPE & PAYMENT SETTLEMENT
// =========================================================================
@Composable
fun OrderTypePaymentTab(
    bills: List<BillEntity>,
    cashSales: Double,
    upiSales: Double,
    khataSales: Double,
    dineInCount: Int,
    takeawayCount: Int
) {
    val totalCount = (dineInCount + takeawayCount).coerceAtLeast(1)
    val dineInPct = (dineInCount.toFloat() / totalCount) * 100
    val takeawayPct = (takeawayCount.toFloat() / totalCount) * 100

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dine In vs Takeaway Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🍽️ Dine-In vs. 🛵 Takeaway Share", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dine-In: $dineInCount orders (${String.format(Locale.US, "%.1f", dineInPct)}%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Takeaway: $takeawayCount orders (${String.format(Locale.US, "%.1f", takeawayPct)}%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CaramelWarm)
                    }
                    LinearProgressIndicator(
                        progress = { (dineInCount.toFloat() / totalCount).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF16A34A),
                        trackColor = CaramelWarm
                    )
                }
            }
        }

        // Payment Settlement Reconciliation
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("💳 Payment Modes & Soundbox Tally", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    PaymentModeRow(label = "💵 Cash Drawer Sales", amount = cashSales, color = Color(0xFF16A34A))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    PaymentModeRow(label = "📱 UPI (PhonePe / GPay / Paytm)", amount = upiSales, color = Color(0xFF2563EB))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    PaymentModeRow(label = "📑 Khata / Credit Due", amount = khataSales, color = Color(0xFFDC2626))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Settled Payments:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "₹${String.format(Locale.US, "%.2f", cashSales + upiSales + khataSales)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = EspressoDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentModeRow(label: String, amount: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text("₹${String.format(Locale.US, "%.2f", amount)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// =========================================================================
// TAB 6: TAX & GST SUMMARY REPORT
// =========================================================================
@Composable
fun TaxGstSummaryTab(
    bills: List<BillEntity>,
    context: Context,
    restaurant: RestaurantEntity,
    dateLabel: String
) {
    val totalBilled = remember(bills) { bills.sumOf { it.totalAmount } }
    // Standard 5% GST = 2.5% CGST + 2.5% SGST
    val gstRate = restaurant.gstRate.takeIf { it > 0.0 } ?: 5.0
    val taxableValue = if (restaurant.isGstEnabled) totalBilled / (1.0 + (gstRate / 100.0)) else totalBilled
    val totalGst = totalBilled - taxableValue
    val cgst = totalGst / 2.0
    val sgst = totalGst / 2.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🏛️ GST & Tax Compliance Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "GSTIN: ${restaurant.gstNumber.ifBlank { "27AAAAA0000A1Z5 (Composition/Regular)" }} • Rate: $gstRate%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gross Total Billed:")
                        Text("₹${String.format(Locale.US, "%.2f", totalBilled)}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Taxable Turnover:")
                        Text("₹${String.format(Locale.US, "%.2f", taxableValue)}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CGST (${gstRate / 2}%):")
                        Text("₹${String.format(Locale.US, "%.2f", cgst)}", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SGST (${gstRate / 2}%):")
                        Text("₹${String.format(Locale.US, "%.2f", sgst)}", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total GST Payable:", fontWeight = FontWeight.Bold)
                        Text("₹${String.format(Locale.US, "%.2f", totalGst)}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF15803D))
                    }

                    Button(
                        onClick = {
                            val gstMsg = "🏛️ *${restaurant.name} - GST Summary ($dateLabel)*\nGSTIN: ${restaurant.gstNumber}\nTotal Sales: ₹${String.format(Locale.US, "%.2f", totalBilled)}\nTaxable Value: ₹${String.format(Locale.US, "%.2f", taxableValue)}\nCGST: ₹${String.format(Locale.US, "%.2f", cgst)}\nSGST: ₹${String.format(Locale.US, "%.2f", sgst)}\nTotal GST: ₹${String.format(Locale.US, "%.2f", totalGst)}\nTotal Bills: ${bills.size}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, gstMsg)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Send GST Summary to CA via WhatsApp"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No app found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share GST Summary with CA on WhatsApp")
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 7: NET PROFIT & LOSS (P&L) REPORT
// =========================================================================
@Composable
fun NetProfitLossTab(
    totalSales: Double,
    foodCost: Double,
    wastageLoss: Double,
    expenses: List<ExpenseEntity>,
    netProfit: Double
) {
    val totalExpenses = remember(expenses) { expenses.sumOf { it.amount } }
    val grossProfit = totalSales - foodCost - wastageLoss

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("💰 Net Profit & Loss (P&L) Statement", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1. Total Sales Revenue (Gross):", fontWeight = FontWeight.SemiBold)
                        Text("₹${String.format(Locale.US, "%.2f", totalSales)}", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("2. (-) Food Cost / Raw Material (COGS):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-₹${String.format(Locale.US, "%.2f", foodCost)}", fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("3. (-) Wastage & Spoilage Loss:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-₹${String.format(Locale.US, "%.2f", wastageLoss)}", fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gross Operating Profit:", fontWeight = FontWeight.Bold)
                        Text("₹${String.format(Locale.US, "%.2f", grossProfit)}", fontWeight = FontWeight.Bold, color = if (grossProfit >= 0) Color(0xFF15803D) else Color(0xFFDC2626))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("4. (-) Operational Expenses (Rent/Gas/Staff):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("-₹${String.format(Locale.US, "%.2f", totalExpenses)}", fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                    }

                    HorizontalDivider(thickness = 2.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NET OPERATING PROFIT:", fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text(
                                if (totalSales > 0) "Profit Margin: ${String.format(Locale.US, "%.1f", (netProfit / totalSales) * 100)}%" else "0% Margin",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "₹${String.format(Locale.US, "%.2f", netProfit)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = if (netProfit >= 0) Color(0xFF15803D) else Color(0xFFDC2626)
                        )
                    }
                }
            }
        }
    }
}

// Reusable KPI Metric Card
@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// 1-Click WhatsApp Sharing Function
fun shareReportOnWhatsApp(
    context: Context,
    reportTitle: String,
    restaurantName: String,
    startDateStr: String,
    endDateStr: String,
    totalSales: Double,
    cashSales: Double,
    upiSales: Double,
    khataSales: Double,
    totalBills: Int,
    avgTicket: Double,
    wastageLoss: Double,
    complimentaryValue: Double,
    totalExpenses: Double,
    netProfit: Double,
    topDishes: List<Pair<String, Int>>
) {
    val message = buildString {
        appendLine("🏪 *$restaurantName - Business Report*")
        appendLine("📅 *Period:* $startDateStr to $endDateStr ($reportTitle)")
        appendLine("━━━━━━━━━━━━━━━━━━━")
        appendLine("💰 *Total Revenue:* ₹${String.format(Locale.US, "%.2f", totalSales)}")
        appendLine("🧾 *Total Bills:* $totalBills  |  *Avg Bill (ATV):* ₹${String.format(Locale.US, "%.2f", avgTicket)}")
        appendLine("━━━━━━━━━━━━━━━━━━━")
        appendLine("💳 *Payment Split:*")
        appendLine("  💵 Cash: ₹${String.format(Locale.US, "%.2f", cashSales)}")
        appendLine("  📱 UPI (GPay/PhonePe): ₹${String.format(Locale.US, "%.2f", upiSales)}")
        if (khataSales > 0) {
            appendLine("  📑 Khata / Credit: ₹${String.format(Locale.US, "%.2f", khataSales)}")
        }
        appendLine("━━━━━━━━━━━━━━━━━━━")
        appendLine("🗑️ *Wastage Loss:* ₹${String.format(Locale.US, "%.2f", wastageLoss)}")
        appendLine("🎁 *Complimentary / Free:* ₹${String.format(Locale.US, "%.2f", complimentaryValue)}")
        appendLine("💸 *Operating Expenses:* ₹${String.format(Locale.US, "%.2f", totalExpenses)}")
        appendLine("━━━━━━━━━━━━━━━━━━━")
        appendLine("📈 *Est. Net Profit:* *₹${String.format(Locale.US, "%.2f", netProfit)}*")
        if (topDishes.isNotEmpty()) {
            appendLine("━━━━━━━━━━━━━━━━━━━")
            appendLine("⭐ *Top Selling Dishes:*")
            topDishes.take(5).forEachIndexed { i, (name, qty) ->
                appendLine("  ${i + 1}. $name ($qty qty)")
            }
        }
        appendLine("━━━━━━━━━━━━━━━━━━━")
        appendLine("Generated by BBC FOOD HUB POS ✨")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share Report via WhatsApp"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
    }
}
