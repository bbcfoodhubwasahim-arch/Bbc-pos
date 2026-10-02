package com.example.ui.components

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.CustomerPaymentEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.util.BillShareUtil
import java.text.SimpleDateFormat
import java.util.*

enum class StatementDateFilter(val title: String) {
    ALL_TIME("All Time"),
    THIS_MONTH("This Month"),
    LAST_7_DAYS("Last 7 Days"),
    CUSTOM("Custom Dates")
}

@Composable
fun StatementShareDialog(
    customerName: String,
    customerPhone: String,
    outstandingBalance: Double,
    allCustomerBills: List<BillEntity>,
    allCustomerPayments: List<CustomerPaymentEntity>,
    restaurant: RestaurantEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(StatementDateFilter.THIS_MONTH) }
    var isSummaryOnly by remember { mutableStateOf(true) }

    val calendar = Calendar.getInstance()
    var customStartDate by remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        mutableStateOf(cal.timeInMillis)
    }
    var customEndDate by remember { mutableStateOf(System.currentTimeMillis()) }

    val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // Filter bills and payments based on selected range
    val (filteredBills, filteredPayments, dateRangeLabel) = remember(
        selectedFilter,
        customStartDate,
        customEndDate,
        allCustomerBills,
        allCustomerPayments
    ) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        when (selectedFilter) {
            StatementDateFilter.ALL_TIME -> {
                Triple(allCustomerBills, allCustomerPayments, "All Time")
            }
            StatementDateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                val bills = allCustomerBills.filter { it.billTimestamp >= start }
                val payments = allCustomerPayments.filter { it.timestamp >= start }
                Triple(bills, payments, "This Month (${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())})")
            }
            StatementDateFilter.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                val start = cal.timeInMillis
                val bills = allCustomerBills.filter { it.billTimestamp >= start }
                val payments = allCustomerPayments.filter { it.timestamp >= start }
                Triple(bills, payments, "Last 7 Days")
            }
            StatementDateFilter.CUSTOM -> {
                val bills = allCustomerBills.filter { it.billTimestamp in customStartDate..customEndDate }
                val payments = allCustomerPayments.filter { it.timestamp in customStartDate..customEndDate }
                Triple(bills, payments, "${sdfDate.format(Date(customStartDate))} to ${sdfDate.format(Date(customEndDate))}")
            }
        }
    }

    // Build the statement text preview dynamically
    val statementText = remember(
        customerName,
        customerPhone,
        outstandingBalance,
        filteredBills,
        filteredPayments,
        restaurant,
        isSummaryOnly,
        dateRangeLabel
    ) {
        BillShareUtil.buildPendingCreditBillStatementText(
            customerName = customerName,
            customerPhone = customerPhone,
            outstandingBalance = outstandingBalance,
            unsettledBills = filteredBills,
            recentPayments = filteredPayments,
            restaurant = restaurant,
            isSummaryOnly = isSummaryOnly,
            dateRangeLabel = dateRangeLabel
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Send Customer Statement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$customerName • Balance: ₹${String.format(Locale.US, "%.2f", outstandingBalance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Filter Chips: Date Range
                    Text("1. CHOOSE PERIOD / DATE FILTER:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatementDateFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.title, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Custom Date Pickers if CUSTOM selected
                    if (selectedFilter == StatementDateFilter.CUSTOM) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val c = Calendar.getInstance().apply { timeInMillis = customStartDate }
                                        DatePickerDialog(context, { _, y, m, d ->
                                            c.set(y, m, d, 0, 0, 0)
                                            customStartDate = c.timeInMillis
                                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("From: ${sdfDate.format(Date(customStartDate))}", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val c = Calendar.getInstance().apply { timeInMillis = customEndDate }
                                        DatePickerDialog(context, { _, y, m, d ->
                                            c.set(y, m, d, 23, 59, 59)
                                            customEndDate = c.timeInMillis
                                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("To: ${sdfDate.format(Date(customEndDate))}", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Format mode: Quick Summary vs Detailed
                    Text("2. STATEMENT FORMAT:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isSummaryOnly = true }
                                .border(
                                    width = if (isSummaryOnly) 2.dp else 1.dp,
                                    color = if (isSummaryOnly) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSummaryOnly) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isSummaryOnly, onClick = { isSummaryOnly = true })
                                    Text("Quick Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Text("Clean, short & readable. Shows net due & top orders. (Recommended)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isSummaryOnly = false }
                                .border(
                                    width = if (!isSummaryOnly) 2.dp else 1.dp,
                                    color = if (!isSummaryOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (!isSummaryOnly) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = !isSummaryOnly, onClick = { isSummaryOnly = false })
                                    Text("Detailed Items", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Text("Shows every single dish item ordered in this period.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Live Message Preview Box
                    Text("3. LIVE WHATSAPP MESSAGE PREVIEW:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        color = Color(0xFFEFEAE2), // WhatsApp chat background color
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = statementText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Bottom Action Buttons
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Statement", statementText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Statement copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Text", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                BillShareUtil.sharePendingBillViaWhatsApp(
                                    context = context,
                                    customerName = customerName,
                                    customerPhone = customerPhone,
                                    outstandingBalance = outstandingBalance,
                                    unsettledBills = filteredBills,
                                    recentPayments = filteredPayments,
                                    restaurant = restaurant,
                                    isSummaryOnly = isSummaryOnly,
                                    dateRangeLabel = dateRangeLabel
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send on WhatsApp", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
