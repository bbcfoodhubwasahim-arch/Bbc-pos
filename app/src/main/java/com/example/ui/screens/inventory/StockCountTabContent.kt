package com.example.ui.screens.inventory

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.InventoryBatchEntity
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.StockCountEntity
import com.example.data.local.entity.StockCountItem
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCountTabContent(
    viewModel: MainViewModel,
    allInventory: List<InventoryItemEntity>,
    allBatches: List<InventoryBatchEntity>,
    allStockCounts: List<StockCountEntity>
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0 = New Count, 1 = History
    var searchQuery by remember { mutableStateOf("") }

    // Map of itemId -> user-entered actual stock string
    val actualStockInputs = remember { mutableStateMapOf<String, String>() }
    var notesInput by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedHistorySession by remember { mutableStateOf<StockCountEntity?>(null) }

    // Precalculate expected stock for each item from active batches, or currentStock as fallback
    val expectedStockMap = remember(allInventory, allBatches) {
        val batchesByItem = allBatches.groupBy { it.inventoryItemId }
        allInventory.associate { item ->
            val itemBatches = batchesByItem[item.id].orEmpty()
            val activeSum = itemBatches
                .filter { it.status != "ARCHIVED" && it.remainingQuantity > 0.000001 }
                .sumOf { it.remainingQuantity }
            item.id to if (itemBatches.isNotEmpty()) activeSum else item.currentStock
        }
    }

    // Alphabetically sorted inventory items
    val sortedItems = remember(allInventory, searchQuery) {
        allInventory
            .filter {
                searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) ||
                        it.unit.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    // Parsed counts for items that user actually entered
    val validCountedMap = remember(actualStockInputs.toMap()) {
        actualStockInputs.mapNotNull { (id, text) ->
            val parsed = text.toDoubleOrNull()
            if (parsed != null && parsed >= 0.0) id to parsed else null
        }.toMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("stock_count_tab_container")
    ) {
        // Sub-navigation: New Count vs History
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                label = { Text("New Stock Count (${validCountedMap.size} entered)") },
                leadingIcon = {
                    Icon(
                        Icons.Default.EditCalendar,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.weight(1f).testTag("subtab_new_count")
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                label = { Text("Count History (${allStockCounts.size})") },
                leadingIcon = {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.weight(1f).testTag("subtab_history")
            )
        }

        if (subTab == 0) {
            // NEW STOCK COUNT SCREEN
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Info banner explaining optional entry
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Entry is optional for every item. Enter physical count only for items you counted; untouched items remain unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Search Bar and Clear All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stock_count_search_field"),
                        placeholder = { Text("Search items by name...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (validCountedMap.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                actualStockInputs.clear()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                }

                // Items list
                if (sortedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No inventory items found." else "No items matching \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(sortedItems, key = { it.id }) { item ->
                            val expectedStock = expectedStockMap[item.id] ?: item.currentStock
                            val inputVal = actualStockInputs[item.id] ?: ""
                            val actualDouble = inputVal.toDoubleOrNull()
                            val variance = if (actualDouble != null) actualDouble - expectedStock else null

                            StockCountItemRow(
                                item = item,
                                expectedStock = expectedStock,
                                inputValue = inputVal,
                                onValueChange = { newValue ->
                                    if (newValue.isBlank()) {
                                        actualStockInputs.remove(item.id)
                                    } else {
                                        // Allow valid decimal characters
                                        if (newValue.matches(Regex("""^\d*\.?\d*$"""))) {
                                            actualStockInputs[item.id] = newValue
                                        }
                                    }
                                },
                                onClear = {
                                    actualStockInputs.remove(item.id)
                                },
                                variance = variance
                            )
                        }
                    }
                }

                // Bottom bar to Save & Adjust
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${validCountedMap.size} of ${allInventory.size} items counted",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            val totalVarianceQty = validCountedMap.map { (id, actual) ->
                                actual - (expectedStockMap[id] ?: 0.0)
                            }.sum()
                            Text(
                                text = if (validCountedMap.isEmpty()) "Enter actual count to adjust"
                                else "Net Qty Variance: ${if (totalVarianceQty >= 0) "+" else ""}${String.format(Locale.US, "%.2f", totalVarianceQty)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (totalVarianceQty > 0.0001) SuccessGreen else if (totalVarianceQty < -0.0001) ErrorRed else MaterialTheme.colorScheme.outline
                            )
                        }

                        Button(
                            onClick = { showConfirmDialog = true },
                            enabled = validCountedMap.isNotEmpty() && !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_stock_count_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Count", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // COUNT HISTORY SCREEN
            StockCountHistoryView(
                allStockCounts = allStockCounts,
                onSelectSession = { selectedHistorySession = it }
            )
        }
    }

    // Confirmation & Adjustment Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = GoldAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Stock Adjustment")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "You are about to adjust stock for ${validCountedMap.size} item(s). This will execute FIFO batch adjustments:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Summary bullet points explaining FIFO logic
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• Shortages: Deducted from oldest batches first (FIFO)", fontSize = 11.sp)
                        Text("• Excesses: Refilled into active batches up to original purchase quantity", fontSize = 11.sp)
                        Text("• Inventory stock on screen updates immediately", fontSize = 11.sp)
                        Text("• Past sales and recorded COGS remain untouched", fontSize = 11.sp)
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Session Notes (Optional)") },
                        placeholder = { Text("e.g. Weekly physical audit") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        viewModel.performStockCount(
                            countedQuantities = validCountedMap,
                            notes = notesInput
                        ) { result ->
                            isSaving = false
                            showConfirmDialog = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Stock count saved and inventory updated!", Toast.LENGTH_SHORT).show()
                                actualStockInputs.clear()
                                notesInput = ""
                                subTab = 1 // Switch to History tab so user sees the new entry
                            } else {
                                Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    modifier = Modifier.testTag("confirm_stock_adjustment_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Applying...")
                    } else {
                        Text("Confirm & Apply")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detail Dialog for Past Stock Count Session
    if (selectedHistorySession != null) {
        StockCountDetailDialog(
            session = selectedHistorySession!!,
            onDismiss = { selectedHistorySession = null }
        )
    }
}

@Composable
fun StockCountItemRow(
    item: InventoryItemEntity,
    expectedStock: Double,
    inputValue: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    variance: Double?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_item_row_${item.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (variance != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (variance != null) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Unit: ${item.unit} • Expected: ${String.format(Locale.US, "%.2f", expectedStock)} ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Input field for counted stock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .width(110.dp)
                            .testTag("actual_stock_input_${item.id}"),
                        placeholder = { Text("Actual", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (inputValue.isNotEmpty()) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Variance Pill if user entered count
            AnimatedVisibility(visible = variance != null) {
                if (variance != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val isExcess = variance > 0.0001
                    val isShortage = variance < -0.0001
                    val badgeColor = when {
                        isExcess -> SuccessGreen
                        isShortage -> ErrorRed
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val badgeBg = badgeColor.copy(alpha = 0.12f)
                    val labelText = when {
                        isExcess -> "+${String.format(Locale.US, "%.2f", variance)} ${item.unit} (Excess)"
                        isShortage -> "${String.format(Locale.US, "%.2f", variance)} ${item.unit} (Shortage)"
                        else -> "0.00 ${item.unit} (Exact Match)"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Variance: $labelText",
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            fontSize = 12.sp
                        )
                        val estCost = variance * item.purchasePrice
                        Text(
                            text = "Est. Cost: ${if (estCost >= 0) "+" else ""}₹${String.format(Locale.US, "%.2f", estCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = badgeColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StockCountHistoryView(
    allStockCounts: List<StockCountEntity>,
    onSelectSession: (StockCountEntity) -> Unit
) {
    var searchDateQuery by remember { mutableStateOf("") }

    val filteredList = remember(allStockCounts, searchDateQuery) {
        allStockCounts
            .filter {
                if (searchDateQuery.isBlank()) true
                else {
                    val formatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it.countDate))
                    it.title.contains(searchDateQuery, ignoreCase = true) ||
                            formatted.contains(searchDateQuery, ignoreCase = true) ||
                            it.notes.contains(searchDateQuery, ignoreCase = true)
                }
            }
            .sortedByDescending { it.countDate }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = searchDateQuery,
            onValueChange = { searchDateQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Filter past counts by date (e.g. Sep 2026)...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AssignmentLate,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No stock count sessions found.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredList, key = { it.id }) { session ->
                    StockCountSessionCard(
                        session = session,
                        onClick = { onSelectSession(session) }
                    )
                }
            }
        }
    }
}

@Composable
fun StockCountSessionCard(
    session: StockCountEntity,
    onClick: () -> Unit
) {
    val dateStr = remember(session.countDate) {
        SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(session.countDate))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("stock_count_session_${session.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EventNote, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = dateStr,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (session.notes.isNotBlank()) {
                            Text(
                                text = session.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${session.totalItemsChecked} items",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val costColor = when {
                    session.totalVarianceCost > 0.001 -> SuccessGreen
                    session.totalVarianceCost < -0.001 -> ErrorRed
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(
                    text = "Net Variance Cost: ${if (session.totalVarianceCost >= 0) "+" else ""}₹${String.format(Locale.US, "%.2f", session.totalVarianceCost)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = costColor
                )

                TextButton(
                    onClick = onClick,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("View Breakdown & Audit", fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun StockCountDetailDialog(
    session: StockCountEntity,
    onDismiss: () -> Unit
) {
    val dateStr = remember(session.countDate) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(session.countDate))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Stock Count Audit", fontWeight = FontWeight.Bold)
                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                if (session.notes.isNotBlank()) {
                    Text("Notes: ${session.notes}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "Items Counted & Batch Traceability:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(session.items) { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.itemName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    val varColor = if (item.varianceQty > 0) SuccessGreen else if (item.varianceQty < 0) ErrorRed else MaterialTheme.colorScheme.outline
                                    Text(
                                        text = "${if (item.varianceQty >= 0) "+" else ""}${String.format(Locale.US, "%.2f", item.varianceQty)} ${item.unit}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = varColor
                                    )
                                }

                                Text(
                                    text = "Expected: ${String.format(Locale.US, "%.2f", item.systemStock)} • Actual: ${String.format(Locale.US, "%.2f", item.actualStock)} ${item.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                if (item.batchAdjustments.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Batch Adjustments Trace:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoldAccent
                                    )
                                    item.batchAdjustments.forEach { adj ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "• ${adj.batchNotes.ifBlank { adj.batchId.take(8) }}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${if (adj.adjustedQty >= 0) "+" else ""}${String.format(Locale.US, "%.2f", adj.adjustedQty)} @ ₹${String.format(Locale.US, "%.2f", adj.unitRate)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
