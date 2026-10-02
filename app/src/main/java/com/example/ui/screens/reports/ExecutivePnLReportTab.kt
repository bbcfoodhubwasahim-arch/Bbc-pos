package com.example.ui.screens.reports

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.InventoryBatchEntity
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.MainViewModel
import com.example.ui.components.InAppReportViewerDialog
import com.example.ui.screens.inventory.StockBookPeriod
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.ReportExportUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val ForestGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutivePnLReportTab(
    viewModel: MainViewModel,
    allBills: List<BillEntity>,
    allExpenses: List<ExpenseEntity>,
    allInventory: List<InventoryItemEntity>,
    allBatches: List<InventoryBatchEntity>,
    restaurant: RestaurantEntity
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(StockBookPeriod.THIS_MONTH) }
    var showInAppPdfDialog by remember { mutableStateOf(false) }

    // Custom Date Range State
    var customStartDate by remember { mutableStateOf(DateUtils.getStartOfMonth()) }
    var customEndDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // Compute active Start and End Timestamps
    val (startTimestamp, endTimestamp, periodLabel) = remember(
        selectedPeriod,
        customStartDate,
        customEndDate
    ) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        when (selectedPeriod) {
            StockBookPeriod.TODAY -> {
                Triple(DateUtils.getStartOfDay(), now, "Today (${DateUtils.getTodayDateString()})")
            }
            StockBookPeriod.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
                Triple(start, end, "Yesterday ($dateStr)")
            }
            StockBookPeriod.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Triple(start, now, "This Week")
            }
            StockBookPeriod.THIS_MONTH -> {
                val start = DateUtils.getStartOfMonth()
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                Triple(start, now, "This Month ($monthName)")
            }
            StockBookPeriod.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                Triple(start, end, "Last Month ($monthName)")
            }
            StockBookPeriod.CUSTOM -> {
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val startStr = fmt.format(Date(customStartDate))
                val endStr = fmt.format(Date(customEndDate))
                Triple(customStartDate, customEndDate, "Custom Range ($startStr - $endStr)")
            }
        }
    }

    // Filter bills and expenses in range
    val periodBills = remember(allBills, startTimestamp, endTimestamp) {
        allBills.filter { it.isStockDeducted && it.billTimestamp in startTimestamp..endTimestamp }
    }

    val periodExpenses = remember(allExpenses, startTimestamp, endTimestamp) {
        allExpenses.filter { it.timestamp in startTimestamp..endTimestamp && it.categoryId != "expcat_inventory_purchase" && !it.categoryName.lowercase().contains("inventory") }
    }

    // P&L Calculations
    val totalGrossSales = remember(periodBills) { periodBills.sumOf { it.totalAmount } }
    val totalActualFoodCost = remember(periodBills) { periodBills.sumOf { it.totalFoodCost } }
    val grossProfit = remember(totalGrossSales, totalActualFoodCost) { totalGrossSales - totalActualFoodCost }

    val foodCostPercentage = remember(totalGrossSales, totalActualFoodCost) {
        if (totalGrossSales > 0) (totalActualFoodCost / totalGrossSales) * 100 else 0.0
    }

    val totalPurchases = remember(allBatches, startTimestamp, endTimestamp) {
        allBatches.filter { it.batchType == "PURCHASE" && it.timestamp in startTimestamp..endTimestamp }.sumOf { it.initialQuantity * it.purchaseRate }
    }
    val closingValuation = remember(allInventory) {
        allInventory.sumOf { it.currentStock * it.purchasePrice }
    }
    val openingValuation = remember(closingValuation, totalActualFoodCost, totalPurchases) {
        (closingValuation + totalActualFoodCost - totalPurchases).coerceAtLeast(0.0)
    }

    val categorizedExpenses = remember(periodExpenses) {
        periodExpenses.groupBy { it.categoryName }
    }

    val totalOperatingExpenses = remember(periodExpenses) { periodExpenses.sumOf { it.amount } }
    val netOperatingProfit = remember(grossProfit, totalOperatingExpenses) { grossProfit - totalOperatingExpenses }
    val netMarginPercentage = remember(totalGrossSales, netOperatingProfit) {
        if (totalGrossSales > 0) (netOperatingProfit / totalGrossSales) * 100 else 0.0
    }

    val totalStaffSalaries = remember(categorizedExpenses) {
        categorizedExpenses
            .filterKeys { it.lowercase(Locale.getDefault()).contains("staff") || it.lowercase(Locale.getDefault()).contains("salary") }
            .values
            .sumOf { list -> list.sumOf { it.amount } }
    }

    val primeCost = remember(totalActualFoodCost, totalStaffSalaries) { totalActualFoodCost + totalStaffSalaries }
    val primeCostPercentage = remember(totalGrossSales, primeCost) {
        if (totalGrossSales > 0) (primeCost / totalGrossSales) * 100 else 0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("executive_pnl_report_container")
    ) {
        // PERIOD FILTER CHIPS ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.TODAY,
                onClick = { selectedPeriod = StockBookPeriod.TODAY },
                label = { Text("Today", fontSize = 11.sp) },
                modifier = Modifier.testTag("pnl_filter_today")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.THIS_WEEK,
                onClick = { selectedPeriod = StockBookPeriod.THIS_WEEK },
                label = { Text("This Week", fontSize = 11.sp) },
                modifier = Modifier.testTag("pnl_filter_week")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.THIS_MONTH,
                onClick = { selectedPeriod = StockBookPeriod.THIS_MONTH },
                label = { Text("This Month", fontSize = 11.sp) },
                modifier = Modifier.testTag("pnl_filter_month")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.LAST_MONTH,
                onClick = { selectedPeriod = StockBookPeriod.LAST_MONTH },
                label = { Text("Last Month", fontSize = 11.sp) },
                modifier = Modifier.testTag("pnl_filter_last_month")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.CUSTOM,
                onClick = { selectedPeriod = StockBookPeriod.CUSTOM },
                label = { Text("Custom 📅", fontSize = 11.sp) },
                modifier = Modifier.testTag("pnl_filter_custom")
            )
        }

        // TOP HEADER ACTION BAR
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(periodLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                    Text("Executive Master P&L Statement", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { showInAppPdfDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_view_in_app_pnl_pdf")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("In-App PDF", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = {
                            try {
                                val csvFile = ReportExportUtil.generateSalesReportCsv(
                                    context = context,
                                    reportTitle = periodLabel,
                                    bills = periodBills,
                                    expenses = periodExpenses,
                                    restaurant = restaurant
                                )
                                ReportExportUtil.shareReportFile(
                                    context = context,
                                    file = csvFile,
                                    mimeType = "text/csv",
                                    title = "Share $periodLabel P&L Statement"
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(32.dp).testTag("btn_share_pnl_whatsapp")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "WhatsApp", tint = SuccessGreen)
                    }
                }
            }
        }

        // P&L SUMMARY CARDS SCROLLABLE COLUMN
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. REVENUE & NET PROFIT HERO CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (netOperatingProfit >= 0) ForestGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, if (netOperatingProfit >= 0) ForestGreen else ErrorRed)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NET OPERATING PROFIT / LOSS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Badge(containerColor = if (netOperatingProfit >= 0) SuccessGreen else ErrorRed) {
                                Text("${String.format(Locale.US, "%.1f", netMarginPercentage)}% MARGIN", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(2.dp))
                            }
                        }

                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", netOperatingProfit)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (netOperatingProfit >= 0) ForestGreen else ErrorRed
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Gross Sales Revenue", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${String.format(Locale.US, "%.0f", totalGrossSales)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Actual COGS (Food Cost)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${String.format(Locale.US, "%.0f", totalActualFoodCost)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                            }
                            Column {
                                Text("Gross Profit Margin", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${String.format(Locale.US, "%.0f", grossProfit)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            }
                        }
                    }
                }
            }

            // 2a. INVENTORY FLOW SUMMARY (TRADING ACCOUNT)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("INVENTORY FLOW SUMMARY (TRADING ACCOUNT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("A) Opening Inventory", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format(Locale.US, "%.2f", openingValuation)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("B) New Purchases (GRNs)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("+ ₹${String.format(Locale.US, "%.2f", totalPurchases)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("C) Closing Inventory", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("- ₹${String.format(Locale.US, "%.2f", closingValuation)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("D) COGS / Food Cost (Used)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Formula: A + B - C", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("₹${String.format(Locale.US, "%.2f", totalActualFoodCost)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ErrorRed)
                        }
                    }
                }
            }

            // 2. FOOD COST & PRIME COST HEALTH INDICATOR
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("FOOD COST & PRIME COST HEALTH ANALYTICS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Food Cost % Box
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Food Cost %", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${String.format(Locale.US, "%.1f", foodCostPercentage)}%", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (foodCostPercentage <= 32.0) "Optimal (<32%)" else if (foodCostPercentage <= 35.0) "Caution (32-35%)" else "High Leakage Risk (>35%)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (foodCostPercentage <= 32.0) SuccessGreen else ErrorRed
                                    )
                                }
                            }

                            // Prime Cost % Box
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Prime Cost % (COGS+Payroll)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${String.format(Locale.US, "%.1f", primeCostPercentage)}%", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (primeCostPercentage <= 60.0) "Healthy (<60%)" else "High Cost (>60%)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (primeCostPercentage <= 60.0) SuccessGreen else ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. EXPENSES BREAKDOWN SECTION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("OPERATING EXPENSES BREAKDOWN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                            Text("Total: ₹${String.format(Locale.US, "%.0f", totalOperatingExpenses)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (categorizedExpenses.isEmpty()) {
                            Text("No expenses recorded in this period.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            categorizedExpenses.entries.forEach { entry ->
                                val catName = entry.key
                                val expenseList = entry.value
                                val catSum = expenseList.sumOf { it.amount }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(catName, fontSize = 12.sp)
                                    Text("₹${String.format(Locale.US, "%.0f", catSum)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }

    // IN-APP PDF VIEWER DIALOG
    if (showInAppPdfDialog) {
        val tableHeaders = listOf("Category / Metric", "Amount (INR)", "% of Revenue")
        val tableRows = mutableListOf(
            listOf("Gross Sales Revenue", "₹${String.format(Locale.US, "%.2f", totalGrossSales)}", "100.0%"),
            listOf("Actual COGS (Raw Materials)", "₹${String.format(Locale.US, "%.2f", totalActualFoodCost)}", "${String.format(Locale.US, "%.1f", foodCostPercentage)}%"),
            listOf("Gross Operating Margin", "₹${String.format(Locale.US, "%.2f", grossProfit)}", "${String.format(Locale.US, "%.1f", 100 - foodCostPercentage)}%")
        )

        categorizedExpenses.entries.forEach { entry ->
            val catName = entry.key
            val list = entry.value
            val sum = list.sumOf { it.amount }
            val pct = if (totalGrossSales > 0) (sum / totalGrossSales) * 100 else 0.0
            tableRows.add(listOf("Expense: $catName", "₹${String.format(Locale.US, "%.2f", sum)}", "${String.format(Locale.US, "%.1f", pct)}%"))
        }

        tableRows.add(listOf("Total Operating Expenses", "₹${String.format(Locale.US, "%.2f", totalOperatingExpenses)}", "${String.format(Locale.US, "%.1f", if (totalGrossSales > 0) (totalOperatingExpenses / totalGrossSales) * 100 else 0.0)}%"))
        tableRows.add(listOf("Net Operating Profit / Loss", "₹${String.format(Locale.US, "%.2f", netOperatingProfit)}", "${String.format(Locale.US, "%.1f", netMarginPercentage)}%"))

        InAppReportViewerDialog(
            reportTitle = "Master Executive P&L Statement",
            periodSubTitle = periodLabel,
            restaurantName = restaurant.name,
            restaurantPhone = restaurant.phone,
            summaryMetrics = listOf(
                "Gross Sales Revenue" to "₹${String.format(Locale.US, "%.0f", totalGrossSales)}",
                "Actual Food Cost" to "₹${String.format(Locale.US, "%.0f", totalActualFoodCost)}",
                "Food Cost %" to "${String.format(Locale.US, "%.1f", foodCostPercentage)}%",
                "Net Operating Profit" to "₹${String.format(Locale.US, "%.0f", netOperatingProfit)}"
            ),
            tableHeaders = tableHeaders,
            tableRows = tableRows,
            warningNotes = if (foodCostPercentage > 35.0) listOf("High Food Cost Leakage Alert: Food Cost is ${String.format(Locale.US, "%.1f", foodCostPercentage)}% (Target: <32%)") else emptyList(),
            pdfFileGenerator = { ctxt ->
                ReportExportUtil.generateSalesReportPdf(
                    context = ctxt,
                    reportTitle = periodLabel,
                    bills = periodBills,
                    expenses = periodExpenses,
                    restaurant = restaurant
                )
            },
            csvFileGenerator = { ctxt ->
                ReportExportUtil.generateSalesReportCsv(
                    context = ctxt,
                    reportTitle = periodLabel,
                    bills = periodBills,
                    expenses = periodExpenses,
                    restaurant = restaurant
                )
            },
            onDismiss = { showInAppPdfDialog = false }
        )
    }
}
