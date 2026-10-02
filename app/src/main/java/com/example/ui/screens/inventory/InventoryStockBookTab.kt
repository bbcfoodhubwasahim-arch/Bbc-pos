package com.example.ui.screens.inventory

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import com.example.data.local.entity.StockCountEntity
import com.example.ui.MainViewModel
import com.example.ui.components.InAppReportViewerDialog
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.ReportExportUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class StockBookPeriod {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    CUSTOM
}

data class InventoryBookRowItem(
    val itemId: String,
    val itemName: String,
    val unit: String,
    val purchaseRate: Double,
    val openingStock: Double,
    val stockPurchased: Double,
    val stockConsumed: Double,
    val stockWastage: Double,
    val theoreticalClosing: Double,
    val actualClosing: Double,
    val variance: Double,
    val varianceValue: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryStockBookTab(
    viewModel: MainViewModel,
    allInventory: List<InventoryItemEntity>,
    allBatches: List<InventoryBatchEntity>,
    allStockCounts: List<StockCountEntity>,
    allBills: List<BillEntity>,
    allExpenses: List<ExpenseEntity>,
    restaurant: RestaurantEntity
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(StockBookPeriod.THIS_MONTH) }
    var searchQuery by remember { mutableStateOf("") }
    var showInAppPdfDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var stockBookSubTab by remember { mutableStateOf(0) } // 0 = Stock Ledger, 1 = GRN History
    var grnToPay by remember { mutableStateOf<List<InventoryBatchEntity>?>(null) }

    // Custom Date Range State
    var customStartDate by remember { mutableStateOf(DateUtils.getStartOfMonth()) }
    var customEndDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // GRN Calculations
    val pendingGrns = remember(allBatches) {
        allBatches.filter { it.batchType == "PURCHASE" && it.paymentStatus != "PAID" }
    }
    val grnGroups = remember(allBatches) {
        allBatches.filter { it.batchType == "PURCHASE" }.groupBy { it.referenceTransactionId }
    }
    val totalOutstandingDues = remember(grnGroups) {
        grnGroups.values.sumOf { batchList ->
            val first = batchList.firstOrNull()
            if (first?.paymentStatus != "PAID") {
                batchList.sumOf { it.initialQuantity * it.purchaseRate }
            } else 0.0
        }
    }

    // Compute active Start and End Timestamp
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

    // Compute Ledger Items for selected period
    val ledgerRows = remember(
        allInventory,
        allBatches,
        allStockCounts,
        allBills,
        allExpenses,
        startTimestamp,
        endTimestamp,
        searchQuery
    ) {
        val filteredBatches = allBatches.filter { it.timestamp in startTimestamp..endTimestamp }
        val batchesByItem = filteredBatches.groupBy { it.inventoryItemId }

        // Aggregate sales bills in range
        val activeBills = allBills.filter { it.isStockDeducted && it.billTimestamp in startTimestamp..endTimestamp }

        // Latest stock count in range if available
        val countsInRange = allStockCounts.filter { it.countDate in startTimestamp..endTimestamp }
        val latestCountSession = countsInRange.maxByOrNull { it.countDate }
        val actualStockMapFromCount: Map<String, Double> = latestCountSession?.items?.associate { it.inventoryItemId to it.actualStock } ?: emptyMap()

        allInventory
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .map { item ->
                val itemBatches = batchesByItem[item.id].orEmpty()
                val purchasedInPeriod = itemBatches.sumOf { it.initialQuantity }

                // Approximate consumption from bills or batches
                val consumedInPeriod = itemBatches.sumOf { it.initialQuantity - it.remainingQuantity }

                // Approximate wastage
                val wastageInPeriod = 0.0

                val opening = item.openingStock
                val theoretical = opening + purchasedInPeriod - consumedInPeriod - wastageInPeriod
                val actual = actualStockMapFromCount[item.id] ?: item.currentStock
                val variance = actual - theoretical
                val varianceVal = variance * item.purchasePrice

                InventoryBookRowItem(
                    itemId = item.id,
                    itemName = item.name,
                    unit = item.unit,
                    purchaseRate = item.purchasePrice,
                    openingStock = opening,
                    stockPurchased = purchasedInPeriod,
                    stockConsumed = consumedInPeriod,
                    stockWastage = wastageInPeriod,
                    theoreticalClosing = theoretical,
                    actualClosing = actual,
                    variance = variance,
                    varianceValue = varianceVal
                )
            }
            .sortedBy { it.itemName.lowercase(Locale.getDefault()) }
    }

    val totalValuation = remember(ledgerRows) { ledgerRows.sumOf { it.actualClosing * it.purchaseRate } }
    val totalPurchasesVal = remember(ledgerRows) { ledgerRows.sumOf { it.stockPurchased * it.purchaseRate } }
    val totalVarianceValue = remember(ledgerRows) { ledgerRows.sumOf { it.varianceValue } }
    val highLeakageCount = remember(ledgerRows) { ledgerRows.count { it.variance < -0.01 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("inventory_stock_book_container")
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
                modifier = Modifier.testTag("filter_chip_today")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.THIS_WEEK,
                onClick = { selectedPeriod = StockBookPeriod.THIS_WEEK },
                label = { Text("This Week", fontSize = 11.sp) },
                modifier = Modifier.testTag("filter_chip_week")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.THIS_MONTH,
                onClick = { selectedPeriod = StockBookPeriod.THIS_MONTH },
                label = { Text("This Month", fontSize = 11.sp) },
                modifier = Modifier.testTag("filter_chip_month")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.LAST_MONTH,
                onClick = { selectedPeriod = StockBookPeriod.LAST_MONTH },
                label = { Text("Last Month", fontSize = 11.sp) },
                modifier = Modifier.testTag("filter_chip_last_month")
            )
            FilterChip(
                selected = selectedPeriod == StockBookPeriod.CUSTOM,
                onClick = {
                    selectedPeriod = StockBookPeriod.CUSTOM
                    showDatePickerDialog = true
                },
                label = { Text("Custom Date 📅", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.testTag("filter_chip_custom")
            )
        }

        // SUMMARY METRICS CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(periodLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                        Text("Stock Register & Variance Audit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Reset Baseline Button
                        OutlinedButton(
                            onClick = {
                                viewModel.purgeOldTrialBatches(restaurant.id)
                                Toast.makeText(context, "Purged old trial batches! Today's ₹10,585 stock is now your baseline.", Toast.LENGTH_LONG).show()
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Baseline", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }

                        // In-App PDF View Button
                        Button(
                            onClick = { showInAppPdfDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_view_in_app_pdf_book")
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("In-App PDF", fontSize = 11.sp)
                        }

                        // WhatsApp Export Button
                        IconButton(
                            onClick = {
                                try {
                                    val csvFile = ReportExportUtil.generateInventoryReportCsv(
                                        context = context,
                                        inventoryList = allInventory,
                                        batchesList = allBatches,
                                        restaurant = restaurant
                                    )
                                    ReportExportUtil.shareReportFile(
                                        context = context,
                                        file = csvFile,
                                        mimeType = "text/csv",
                                        title = "Share $periodLabel Inventory Book"
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp).testTag("btn_share_whatsapp_book")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "WhatsApp", tint = SuccessGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "Ending Stock Valuation",
                        value = "₹${String.format(Locale.US, "%.0f", totalValuation)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Period Purchases",
                        value = "₹${String.format(Locale.US, "%.0f", totalPurchasesVal)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Variance Loss",
                        value = "₹${String.format(Locale.US, "%.0f", totalVarianceValue)}",
                        valueColor = if (totalVarianceValue < 0) ErrorRed else SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (totalOutstandingDues > 0.0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Total Outstanding Vendor Dues: ₹${String.format(Locale.US, "%.2f", totalOutstandingDues).replace(".00", "")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }
                    }
                }
            }
        }

        // Sub-Tab Row: Stock Ledger vs GRNs History
        TabRow(selectedTabIndex = stockBookSubTab, modifier = Modifier.padding(vertical = 4.dp)) {
            Tab(selected = stockBookSubTab == 0, onClick = { stockBookSubTab = 0 }) {
                Text("Stock Ledger (ताळेबंद)", fontSize = 12.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = stockBookSubTab == 1, onClick = { stockBookSubTab = 1 }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GRN History (खरेदी इतिहास)", fontSize = 12.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                    if (pendingGrns.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Badge(containerColor = ErrorRed) {
                            Text("${grnGroups.values.count { it.firstOrNull()?.paymentStatus != "PAID" }}", color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            }
        }

        // SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("input_search_stock_book"),
            placeholder = { Text("Search raw material...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // LEDGER OR GRN HISTORY TABLE LIST
        if (stockBookSubTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ledgerRows, key = { it.itemId }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ledger_item_${row.itemId}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.itemName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (row.variance < -0.01) {
                                    Badge(containerColor = ErrorRed) {
                                        Text("LEAKAGE LOSS", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(2.dp))
                                    }
                                } else {
                                    Badge(containerColor = SuccessGreen) {
                                        Text("OPTIMAL", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Purchases: +${String.format(Locale.US, "%.1f", row.stockPurchased)} ${row.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Usage: -${String.format(Locale.US, "%.1f", row.stockConsumed)} ${row.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Actual Closing: ${String.format(Locale.US, "%.1f", row.actualClosing)} ${row.unit}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (row.variance != 0.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Theoretical: ${String.format(Locale.US, "%.1f", row.theoreticalClosing)} ${row.unit}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Variance: ${String.format(Locale.US, "%+.1f", row.variance)} ${row.unit} (₹${String.format(Locale.US, "%.0f", row.varianceValue)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (row.variance < 0) ErrorRed else SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // GRN History List grouped by referenceTransactionId
            val grnsList = remember(grnGroups) {
                grnGroups.entries.sortedByDescending { it.value.firstOrNull()?.timestamp ?: 0L }
            }

            if (grnsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No GRN purchases recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(grnsList) { entry ->
                        val grnId = entry.key
                        val batches = entry.value
                        val firstBatch = batches.firstOrNull() ?: return@items
                        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(firstBatch.timestamp))
                        val isPaid = firstBatch.paymentStatus == "PAID"
                        val totalGrnAmount = batches.sumOf { it.initialQuantity * it.purchaseRate }
                        val grnVendor = firstBatch.vendorName.ifBlank { "Direct Purchase" }
                        val grnInvoice = firstBatch.invoiceNo.ifBlank { "N/A" }
                        val grnMethod = firstBatch.paymentMethod

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Vendor: $grnVendor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Inv: $grnInvoice  •  $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Badge(containerColor = if (isPaid) SuccessGreen else ErrorRed) {
                                        Text(if (isPaid) "PAID" else "PENDING", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                // List items in this GRN
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    batches.forEach { b ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("• ${b.itemName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text("${String.format(Locale.US, "%.1f", b.initialQuantity).replace(".00", "")} ${b.unit} @ ₹${String.format(Locale.US, "%.1f", b.purchaseRate)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Total Bill Amount", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹${String.format(Locale.US, "%.2f", totalGrnAmount).replace(".00", "")}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (isPaid) ForestGreen else ErrorRed)
                                    }

                                    if (!isPaid) {
                                        Button(
                                            onClick = { grnToPay = batches },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pay Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    } else {
                                        Text("Paid via $grnMethod", fontSize = 11.sp, color = ForestGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pay GRN Outstanding Dues Dialog
    grnToPay?.let { grnBatches ->
        var payMethod by remember { mutableStateOf("CASH") }
        val grnAmount = grnBatches.sumOf { it.initialQuantity * it.purchaseRate }
        
        AlertDialog(
            onDismissRequest = { grnToPay = null },
            title = { Text("Record GRN Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Total Payment Amount: ₹${String.format(Locale.US, "%.2f", grnAmount).replace(".00", "")}", fontWeight = FontWeight.Bold)
                    Text("Select how this payment was made to fully clear the dues:")
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                    Text("Note: Cash payments will automatically reduce expected cash in today's Register.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    grnBatches.forEach { batch ->
                        viewModel.payGRN(batch, payMethod, batch.initialQuantity * batch.purchaseRate)
                    }
                    Toast.makeText(context, "Dues marked as PAID and register updated!", Toast.LENGTH_SHORT).show()
                    grnToPay = null
                }) {
                    Text("Confirm Paid", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { grnToPay = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // IN-APP PDF VIEWER DIALOG
    if (showInAppPdfDialog) {
        val tableHeaders = listOf("Raw Material", "Opening", "Added", "Consumed", "Closing", "Variance")
        val tableRows = ledgerRows.map { row ->
            listOf(
                row.itemName,
                "${String.format(Locale.US, "%.1f", row.openingStock)} ${row.unit}",
                "+${String.format(Locale.US, "%.1f", row.stockPurchased)}",
                "-${String.format(Locale.US, "%.1f", row.stockConsumed)}",
                "${String.format(Locale.US, "%.1f", row.actualClosing)} ${row.unit}",
                if (row.variance < -0.01) "${String.format(Locale.US, "%.1f", row.variance)} (LEAKAGE)" else "0.0"
            )
        }

        val warningList = ledgerRows.filter { it.variance < -0.01 }.map {
            "${it.itemName}: ${String.format(Locale.US, "%.1f", it.variance)} ${it.unit} missing (Loss ₹${String.format(Locale.US, "%.0f", it.varianceValue)})"
        }

        InAppReportViewerDialog(
            reportTitle = "Master Inventory Stock Book",
            periodSubTitle = periodLabel,
            restaurantName = restaurant.name,
            restaurantPhone = restaurant.phone,
            summaryMetrics = listOf(
                "Total Items" to "${ledgerRows.size}",
                "Stock Valuation" to "₹${String.format(Locale.US, "%.0f", totalValuation)}",
                "Period Purchases" to "₹${String.format(Locale.US, "%.0f", totalPurchasesVal)}",
                "Variance Leakage Loss" to "₹${String.format(Locale.US, "%.0f", totalVarianceValue)}"
            ),
            tableHeaders = tableHeaders,
            tableRows = tableRows,
            warningNotes = warningList,
            pdfFileGenerator = { ctxt ->
                ReportExportUtil.generateInventoryReportPdf(
                    context = ctxt,
                    inventoryList = allInventory,
                    batchesList = allBatches,
                    restaurant = restaurant
                )
            },
            csvFileGenerator = { ctxt ->
                ReportExportUtil.generateInventoryReportCsv(
                    context = ctxt,
                    inventoryList = allInventory,
                    batchesList = allBatches,
                    restaurant = restaurant
                )
            },
            onDismiss = { showInAppPdfDialog = false }
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}
