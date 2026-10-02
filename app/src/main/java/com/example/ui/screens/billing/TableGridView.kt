package com.example.ui.screens.billing

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.CafeTableEntity
import com.example.ui.MainViewModel
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.RechartsDailySalesSummaryCard
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun TableGridView(
    tables: List<CafeTableEntity>,
    activeBills: List<BillEntity>,
    viewModel: MainViewModel,
    onSelectVacantTable: (CafeTableEntity) -> Unit,
    onSelectOccupiedTable: (CafeTableEntity, BillEntity) -> Unit,
    onCashOutTable: (CafeTableEntity, BillEntity) -> Unit
) {
    val context = LocalContext.current
    val isTablesLoading by viewModel.isTablesLoading.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, OCCUPIED, VACANT
    var showAddTableDialog by remember { mutableStateOf(false) }
    var tableToTransfer by remember { mutableStateOf<Pair<CafeTableEntity, BillEntity>?>(null) }
    var tableToDelete by remember { mutableStateOf<CafeTableEntity?>(null) }

    // Map table ID to its active bill (strictly non-cancelled, non-voided, non-settled)
    val tableBillsMap = remember(activeBills) {
        activeBills.filter { it.tableId != null && !it.isCancelled && !it.isVoided && !it.isSettled }.associateBy { it.tableId!! }
    }

    val filteredTables = remember(tables, selectedFilter, tableBillsMap) {
        when (selectedFilter) {
            "OCCUPIED" -> tables.filter { tableBillsMap.containsKey(it.id) }
            "VACANT" -> tables.filter { !tableBillsMap.containsKey(it.id) }
            else -> tables
        }
    }

    val occupiedCount = tables.count { tableBillsMap.containsKey(it.id) }
    val vacantCount = tables.size - occupiedCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Toolbar: Status Counters & Add Table Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dine In Tables",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$occupiedCount Occupied • $vacantCount Vacant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showAddTableDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Table", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Recharts-based 7-Day Daily Sales Summary Chart
        val allBills by viewModel.allBills.collectAsState()
        RechartsDailySalesSummaryCard(
            bills = allBills,
            initialExpanded = false,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("All (${tables.size})") },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = selectedFilter == "OCCUPIED",
                onClick = { selectedFilter = "OCCUPIED" },
                label = { Text("Occupied ($occupiedCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TableOccupiedRed.copy(alpha = 0.15f),
                    selectedLabelColor = TableOccupiedRed
                ),
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = selectedFilter == "VACANT",
                onClick = { selectedFilter = "VACANT" },
                label = { Text("Vacant ($vacantCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SuccessGreen.copy(alpha = 0.15f),
                    selectedLabelColor = SuccessGreen
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Table Grid
        if (filteredTables.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isTablesLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = CaramelWarm
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TableRestaurant,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedFilter == "OCCUPIED") "No occupied tables currently." else "No tables found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredTables, key = { it.id }) { table ->
                    val activeBill = tableBillsMap[table.id]
                    val isOccupied = activeBill != null

                    TableCard(
                        table = table,
                        isOccupied = isOccupied,
                        activeBill = activeBill,
                        onClick = {
                            if (isOccupied && activeBill != null) {
                                onSelectOccupiedTable(table, activeBill)
                            } else {
                                onSelectVacantTable(table)
                            }
                        },
                        onTransferClick = {
                            if (activeBill != null) {
                                tableToTransfer = Pair(table, activeBill)
                            }
                        },
                        onCashOutClick = {
                            if (activeBill != null) {
                                onCashOutTable(table, activeBill)
                            }
                        },
                        onDeleteClick = {
                            if (!isOccupied) {
                                tableToDelete = table
                            }
                        }
                    )
                }
            }
        }
    }

    // Add Table Dialog
    if (showAddTableDialog) {
        AddTableDialog(
            onDismiss = { showAddTableDialog = false },
            onAdd = { name, capacity ->
                viewModel.addCustomTable(name, capacity)
                showAddTableDialog = false
                Toast.makeText(context, "Added $name", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Table Transfer Dialog
    tableToTransfer?.let { (srcTable, bill) ->
        val vacantTables = tables.filter { !it.isOccupied && !tableBillsMap.containsKey(it.id) && it.id != srcTable.id }
        TableTransferDialog(
            sourceTable = srcTable,
            vacantTables = vacantTables,
            bill = bill,
            onDismiss = { tableToTransfer = null },
            onConfirmTransfer = { destTable ->
                viewModel.transferTable(srcTable.id, destTable.id, bill.id) { success ->
                    if (success) {
                        Toast.makeText(context, "Shifted order to ${destTable.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Transfer failed", Toast.LENGTH_SHORT).show()
                    }
                    tableToTransfer = null
                }
            }
        )
    }

    // Delete Table Dialog (FIX 4)
    tableToDelete?.let { table ->
        DeleteConfirmationDialog(
            title = "Delete Table?",
            itemName = table.name,
            onDismiss = { tableToDelete = null },
            onConfirm = {
                viewModel.deleteCustomTable(table.id)
                tableToDelete = null
                Toast.makeText(context, "Deleted ${table.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun TableCard(
    table: CafeTableEntity,
    isOccupied: Boolean,
    activeBill: BillEntity?,
    onClick: () -> Unit,
    onTransferClick: () -> Unit,
    onCashOutClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val durationText = remember(activeBill?.billTimestamp) {
        if (activeBill != null) {
            val diffMinutes = ((System.currentTimeMillis() - activeBill.billTimestamp) / (1000 * 60)).coerceAtLeast(0)
            if (diffMinutes < 60) {
                "${diffMinutes}m"
            } else {
                val hours = diffMinutes / 60
                val mins = diffMinutes % 60
                "${hours}h ${mins}m"
            }
        } else ""
    }

    val cardBg = if (isOccupied) Color(0xFFFFF0F2) else Color(0xFFF1F8E9)
    val borderColor = if (isOccupied) Color(0xFFE57373) else Color(0xFF81C784)
    val badgeColor = if (isOccupied) TableOccupiedRed else SuccessGreen
    val badgeText = if (isOccupied) "OCCUPIED" else "VACANT"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOccupied) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Status Badge & Seat Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                // Capacity / Delete option
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${table.capacity} Seats",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isOccupied) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete Table",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Table Name
            Text(
                text = table.name,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isOccupied && activeBill != null) {
                Spacer(modifier = Modifier.height(6.dp))

                // Duration & Customer info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⏱️ $durationText",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TableOccupiedRed
                    )
                    Text(
                        text = "${activeBill.items.sumOf { it.quantity }} items",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (activeBill.customerName.isNotBlank()) {
                    Text(
                        text = activeBill.customerName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = EspressoBrown,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Running Total
                Text(
                    text = "₹${String.format(Locale.US, "%.2f", activeBill.totalAmount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = EspressoDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons for Occupied Table
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onTransferClick,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Shift", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onCashOutClick,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Cash Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tap to Take Order",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SuccessGreen
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SuccessGreen
                    )
                }
            }
        }
    }
}

@Composable
fun AddTableDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, capacity: Int) -> Unit
) {
    var tableName by remember { mutableStateOf("") }
    var capacityText by remember { mutableStateOf("4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Table", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = { Text("Table Name") },
                    placeholder = { Text("e.g. Table 9, Window Seat, VIP-2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Seating Capacity") },
                    placeholder = { Text("4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tableName.isNotBlank()) {
                        val cap = capacityText.toIntOrNull() ?: 4
                        onAdd(tableName.trim(), cap)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
            ) {
                Text("Add Table")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TableTransferDialog(
    sourceTable: CafeTableEntity,
    vacantTables: List<CafeTableEntity>,
    bill: BillEntity,
    onDismiss: () -> Unit,
    onConfirmTransfer: (destTable: CafeTableEntity) -> Unit
) {
    var selectedDestTable by remember { mutableStateOf<CafeTableEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Transfer Order / Shift Table", fontWeight = FontWeight.Bold)
                Text(
                    "Moving ${sourceTable.name} (${bill.billNumber}) to another table",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (vacantTables.isEmpty()) {
                    Text(
                        "No other vacant tables available currently. Please free up a table first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Select Destination Table:",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(vacantTables) { table ->
                            val isSelected = selectedDestTable?.id == table.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CaramelWarm else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDestTable = table }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        table.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${table.capacity} Seats",
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDestTable?.let { onConfirmTransfer(it) }
                },
                enabled = selectedDestTable != null,
                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
            ) {
                Text("Confirm Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
