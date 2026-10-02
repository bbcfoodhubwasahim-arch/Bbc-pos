package com.example.ui.screens.billing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.BillEntity
import com.example.ui.theme.*
import com.example.util.DateUtils

@Composable
fun KotDialog(
    bill: BillEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CaramelWarm.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "KITCHEN ORDER TICKET (KOT)",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = CaramelWarm,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Table & Order Type Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
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
                            Text(
                                text = if (bill.orderType == "DINE_IN") "TABLE: ${bill.tableName ?: "Table"}" else "ORDER: TAKEAWAY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (bill.customerName.isNotBlank()) {
                                Text(
                                    text = "Customer: ${bill.customerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = bill.billNumber,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = DateUtils.formatTime(bill.billTimestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Items list header (Items and Qty ONLY, STRICTLY NO PRICES)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ITEM DESCRIPTION", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text("QTY", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }

                HorizontalDivider()

                // Items list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(bill.items) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.getFormattedDisplayName(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${item.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // Show selected addons if present
                            val selectedAddons = item.getSelectedAddonsList()
                            if (selectedAddons.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Add-on:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CaramelWarm,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                selectedAddons.forEach { addon ->
                                    Text(
                                        text = "• ${addon.name} (${addon.quantity}x)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CaramelWarm,
                                        modifier = Modifier.padding(start = 14.dp, top = 1.dp)
                                    )
                                }
                            }

                            // Show cooking notes if present
                            if (item.cookingNotes.isNotBlank()) {
                                Text(
                                    text = "Note: ${item.cookingNotes}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }

                            // Show selected combo items indented underneath
                            val visibleNotes = item.notes.substringBefore("[DISHES:").trim()
                            if (visibleNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                val selections = visibleNotes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                selections.forEach { sel ->
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("•", fontSize = 12.sp, color = CaramelWarm, fontWeight = FontWeight.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = sel,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                Text(
                    text = "Total Items: ${bill.items.sumOf { it.quantity }}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val kotText = buildString {
                                appendLine("--- KITCHEN ORDER TICKET (KOT) ---")
                                appendLine(if (bill.orderType == "DINE_IN") "TABLE: ${bill.tableName ?: "Table"}" else "TAKEAWAY")
                                appendLine("Order: ${bill.billNumber} | Time: ${DateUtils.formatTime(bill.billTimestamp)}")
                                if (bill.customerName.isNotBlank()) appendLine("Customer: ${bill.customerName}")
                                appendLine("----------------------------------")
                                bill.items.forEach {
                                    appendLine("${it.dishName} x${it.quantity}")
                                    if (it.notes.isNotBlank()) {
                                        val selections = it.notes.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                                        selections.forEach { sel ->
                                            appendLine("   ↳ $sel")
                                        }
                                    }
                                }
                                appendLine("----------------------------------")
                                appendLine("Total Items: ${bill.items.sumOf { it.quantity }}")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, kotText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share/Print KOT"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share KOT")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Done")
                    }
                }
            }
        }
    }
}
