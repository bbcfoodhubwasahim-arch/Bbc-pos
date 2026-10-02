package com.example.ui.screens.bills

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.BillItem
import com.example.data.local.entity.CustomerPaymentEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.MainViewModel
import com.example.ui.components.BillPreviewDialog
import com.example.ui.components.PaymentReceiptDialog
import com.example.ui.components.StatementShareDialog
import com.example.ui.theme.*
import com.example.util.BillShareUtil
import com.example.util.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsHistoryScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val allBills by viewModel.allBills.collectAsState()
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()

    val allCustomers by viewModel.allCustomers.collectAsState()
    val allCustomerPayments by viewModel.allCustomerPayments.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    
    // Date Filter State: "ALL", "TODAY", "CUSTOM"
    var dateFilter by remember { mutableStateOf("ALL") }
    var customStartDate by remember { mutableStateOf(DateUtils.getStartOfDay()) }
    var customEndDate by remember { mutableStateOf(DateUtils.getEndOfDay()) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    // Type Filter State: "ALL", "PARTY_WISE"
    var typeFilter by remember { mutableStateOf("ALL") }
    var selectedPartyCustomer by remember { mutableStateOf<com.example.data.local.entity.CustomerEntity?>(null) }
    var showPartySelectDialog by remember { mutableStateOf(false) }
    var partySubTab by remember { mutableStateOf(0) } // 0: Bills/Orders, 1: Payments Received

    var selectedBillForDetails by remember { mutableStateOf<BillEntity?>(null) }
    var billToEdit by remember { mutableStateOf<BillEntity?>(null) }
    var showCollectDialog by remember { mutableStateOf(false) }
    var collectAmountInput by remember { mutableStateOf("") }
    var collectMode by remember { mutableStateOf("CASH") }
    var receivedPaymentReceipt by remember { mutableStateOf<CustomerPaymentEntity?>(null) }
    var previousDueForReceipt by remember { mutableStateOf(0.0) }
    var remainingDueForReceipt by remember { mutableStateOf(0.0) }
    var showPaymentReceiptDialog by remember { mutableStateOf(false) }
    var showStatementShareDialog by remember { mutableStateOf(false) }

    val todayStart = remember { DateUtils.getStartOfDay() }
    val todayEnd = remember { DateUtils.getEndOfDay() }

    val filteredBills = remember(allBills, searchQuery, dateFilter, customStartDate, customEndDate, typeFilter, selectedPartyCustomer) {
        allBills.filter { bill ->
            // Search query filter
            val matchesSearch = searchQuery.isBlank() ||
                    bill.billNumber.contains(searchQuery, ignoreCase = true) ||
                    bill.customerName.contains(searchQuery, ignoreCase = true) ||
                    bill.customerPhone.contains(searchQuery)

            // Date filter
            val matchesDate = when (dateFilter) {
                "TODAY" -> bill.billTimestamp in todayStart..todayEnd
                "CUSTOM" -> bill.billTimestamp in customStartDate..customEndDate
                else -> true
            }

            // Type / Party filter
            val matchesType = when (typeFilter) {
                "PAID" -> !bill.isCancelled && bill.status != "CANCELLED" && bill.paymentMethod != "CREDIT"
                "DUE" -> !bill.isCancelled && bill.status != "CANCELLED" && bill.paymentMethod == "CREDIT"
                "CANCELLED" -> bill.isCancelled || bill.status == "CANCELLED" || bill.isVoided
                "PARTY_WISE" -> {
                    val party = selectedPartyCustomer
                    if (party != null) {
                        val phoneMatch = party.contactNumber.isNotBlank() && bill.customerPhone.trim() == party.contactNumber.trim()
                        val nameMatch = party.name.isNotBlank() && bill.customerName.trim().equals(party.name.trim(), ignoreCase = true)
                        phoneMatch || nameMatch
                    } else {
                        // All bills that have associated customer name or phone
                        bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()
                    }
                }
                else -> true
            }

            matchesSearch && matchesDate && matchesType
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bills & Invoices", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${allBills.size} bills recorded • Live A4 PDF & Sharing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by Bill #, Customer Name or Phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Two-Row Filter Section
            // Row 1: Date Filters (Today, Custom Date)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Date:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilterChip(
                    selected = dateFilter == "ALL",
                    onClick = { dateFilter = "ALL" },
                    label = { Text("All Dates") }
                )

                FilterChip(
                    selected = dateFilter == "TODAY",
                    onClick = { dateFilter = "TODAY" },
                    label = { Text("Today") },
                    leadingIcon = if (dateFilter == "TODAY") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                FilterChip(
                    selected = dateFilter == "CUSTOM",
                    onClick = {
                        if (dateFilter == "CUSTOM") {
                            showCustomDateDialog = true
                        } else {
                            dateFilter = "CUSTOM"
                            showCustomDateDialog = true
                        }
                    },
                    label = {
                        Text(
                            if (dateFilter == "CUSTOM") {
                                "${DateUtils.formatDate(customStartDate)} - ${DateUtils.formatDate(customEndDate)}"
                            } else {
                                "Custom Date"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            // Row 2: Type Filters (All Bills, Paid, Due, Cancelled, Party Wise)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Type:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilterChip(
                    selected = typeFilter == "ALL",
                    onClick = {
                        typeFilter = "ALL"
                        selectedPartyCustomer = null
                    },
                    label = { Text("All Bills") }
                )

                FilterChip(
                    selected = typeFilter == "PAID",
                    onClick = {
                        typeFilter = "PAID"
                        selectedPartyCustomer = null
                    },
                    label = { Text("Paid") },
                    leadingIcon = if (typeFilter == "PAID") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = SuccessGreen) }
                    } else null
                )

                FilterChip(
                    selected = typeFilter == "DUE",
                    onClick = {
                        typeFilter = "DUE"
                        selectedPartyCustomer = null
                    },
                    label = { Text("Pending Due") },
                    leadingIcon = if (typeFilter == "DUE") {
                        { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp), tint = CreditPurple) }
                    } else null
                )

                FilterChip(
                    selected = typeFilter == "CANCELLED",
                    onClick = {
                        typeFilter = "CANCELLED"
                        selectedPartyCustomer = null
                    },
                    label = { Text("Cancelled") },
                    leadingIcon = if (typeFilter == "CANCELLED") {
                        { Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErrorRed) }
                    } else null
                )

                FilterChip(
                    selected = typeFilter == "PARTY_WISE",
                    onClick = {
                        typeFilter = "PARTY_WISE"
                        showPartySelectDialog = true
                    },
                    label = {
                        Text(
                            if (selectedPartyCustomer != null) {
                                "Party: ${selectedPartyCustomer!!.name}"
                            } else {
                                "Party Wise"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = if (typeFilter == "PARTY_WISE" && selectedPartyCustomer != null) {
                        {
                            IconButton(
                                onClick = {
                                    selectedPartyCustomer = null
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Party Filter", modifier = Modifier.size(14.dp))
                            }
                        }
                    } else null
                )

                if (typeFilter == "PARTY_WISE" && selectedPartyCustomer != null) {
                    val party = selectedPartyCustomer!!
                    val partyOutstanding = remember(party, allBills, allCustomerPayments) {
                        viewModel.getCustomerOutstandingCredit(party.name, party.contactNumber)
                    }
                    if (partyOutstanding > 0.0) {
                        Surface(
                            color = CreditPurple.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Due: ₹${String.format(Locale.US, "%.2f", partyOutstanding)}",
                                color = CreditPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Party Wise Header Banner with Statement sharing
            if (typeFilter == "PARTY_WISE" && selectedPartyCustomer != null) {
                val party = selectedPartyCustomer!!
                val partyOutstanding = remember(party, allBills, allCustomerPayments) {
                    viewModel.getCustomerOutstandingCredit(party.name, party.contactNumber)
                }
                val partyCreditBills = remember(party, allBills) {
                    allBills.filter { b ->
                        val phoneMatch = party.contactNumber.isNotBlank() && b.customerPhone.trim() == party.contactNumber.trim()
                        val nameMatch = party.name.isNotBlank() && b.customerName.trim().equals(party.name.trim(), ignoreCase = true)
                        (phoneMatch || nameMatch) && b.paymentMethod == "CREDIT"
                    }
                }
                val partyPayments = remember(party, allCustomerPayments) {
                    allCustomerPayments.filter { p ->
                        val phoneMatch = party.contactNumber.isNotBlank() && p.customerPhone.trim() == party.contactNumber.trim()
                        val nameMatch = party.name.isNotBlank() && p.customerName.trim().equals(party.name.trim(), ignoreCase = true)
                        phoneMatch || nameMatch
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = party.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Ph: ${party.contactNumber.ifBlank { "N/A" }} • ${filteredBills.size} orders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (partyOutstanding > 0.0) {
                                    FilledTonalButton(
                                        onClick = { showStatementShareDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Statement", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = {
                                            collectAmountInput = partyOutstanding.toString()
                                            showCollectDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("💰 Collect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Surface(
                                        color = SuccessGreen.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "✅ Dues Cleared (₹0.00)",
                                            color = SuccessGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sub-tabs: Orders vs Payments Received
                        TabRow(
                            selectedTabIndex = partySubTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = partySubTab == 0,
                                onClick = { partySubTab = 0 },
                                text = { Text("📦 Orders (${filteredBills.size})", fontSize = 12.sp, fontWeight = if (partySubTab == 0) FontWeight.Bold else FontWeight.Normal) }
                            )
                            Tab(
                                selected = partySubTab == 1,
                                onClick = { partySubTab = 1 },
                                text = { Text("💰 Payments Received (${partyPayments.size})", fontSize = 12.sp, fontWeight = if (partySubTab == 1) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }

            // Party Wise: Show Payments Received Tab Content
            if (typeFilter == "PARTY_WISE" && selectedPartyCustomer != null && partySubTab == 1) {
                val party = selectedPartyCustomer!!
                val partyPayments = remember(party, allCustomerPayments) {
                    allCustomerPayments.filter { p ->
                        val phoneMatch = party.contactNumber.isNotBlank() && p.customerPhone.trim() == party.contactNumber.trim()
                        val nameMatch = party.name.isNotBlank() && p.customerName.trim().equals(party.name.trim(), ignoreCase = true)
                        phoneMatch || nameMatch
                    }.sortedByDescending { it.timestamp }
                }

                if (partyPayments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No payment receipts recorded yet", style = MaterialTheme.typography.titleMedium)
                            Text("When customer pays partial or full due, it will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(partyPayments) { payment ->
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Received: ₹${String.format(Locale.US, "%.2f", payment.amount)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = SuccessGreen
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = if (payment.paymentMode == "CASH") CashAmber.copy(alpha = 0.15f) else UPIBlue.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = payment.paymentMode,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (payment.paymentMode == "CASH") CashAmber else UPIBlue,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = DateUtils.formatDateTime(payment.timestamp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (payment.notes.isNotBlank()) {
                                            Text(
                                                text = "Note: ${payment.notes}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            receivedPaymentReceipt = payment
                                            previousDueForReceipt = viewModel.getCustomerOutstandingCredit(payment.customerName, payment.customerPhone) + payment.amount
                                            remainingDueForReceipt = viewModel.getCustomerOutstandingCredit(payment.customerName, payment.customerPhone)
                                            showPaymentReceiptDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Receipt", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (filteredBills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No bills found", style = MaterialTheme.typography.titleMedium)
                        Text("Bills created in POS will appear here", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredBills) { bill ->
                        BillHistoryCard(
                            bill = bill,
                            viewModel = viewModel,
                            onClick = { selectedBillForDetails = bill }
                        )
                    }
                }
            }
        }
    }

    // Bill Details Dialog
    selectedBillForDetails?.let { bill ->
        BillDetailsDialog(
            bill = bill,
            viewModel = viewModel,
            onDismiss = { selectedBillForDetails = null },
            onEdit = {
                billToEdit = bill
                selectedBillForDetails = null
            }
        )
    }

    // Edit Bill Dialog (For editing backdated or previously settled bills)
    billToEdit?.let { bill ->
        EditBillDialog(
            bill = bill,
            viewModel = viewModel,
            onDismiss = { billToEdit = null },
            onSaved = {
                billToEdit = null
                Toast.makeText(context, "Bill updated successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Collect Payment Dialog
    if (showCollectDialog && selectedPartyCustomer != null) {
        val party = selectedPartyCustomer!!
        val partyOutstanding = viewModel.getCustomerOutstandingCredit(party.name, party.contactNumber)

        AlertDialog(
            onDismissRequest = { showCollectDialog = false },
            title = { Text("Collect Payment - ${party.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Total Pending Due: ₹${String.format(Locale.US, "%.2f", partyOutstanding)}", fontWeight = FontWeight.Bold, color = ErrorRed)
                    
                    OutlinedTextField(
                        value = collectAmountInput,
                        onValueChange = { collectAmountInput = it },
                        label = { Text("Collection Amount (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Payment Mode:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CASH", "UPI", "CARD").forEach { mode ->
                            FilterChip(
                                selected = collectMode == mode,
                                onClick = { collectMode = mode },
                                label = { Text(mode) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = collectAmountInput.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.collectCustomerDue(
                            customerName = party.name,
                            customerPhone = party.contactNumber,
                            amount = amount,
                            paymentMode = collectMode
                        ) { success, payment, prevDue, remDue, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            showCollectDialog = false
                            if (success && payment != null) {
                                receivedPaymentReceipt = payment
                                previousDueForReceipt = prevDue
                                remainingDueForReceipt = remDue
                                showPaymentReceiptDialog = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Confirm Collection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCollectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Payment Receipt WhatsApp Dialog
    if (showPaymentReceiptDialog && receivedPaymentReceipt != null) {
        val safeRest = activeRestaurant ?: RestaurantEntity(
            id = "default",
            name = "Bapu Biryani Center",
            phone = "9876543210",
            address = "Main Road, Near Bus Stand",
            fssaiNumber = "FSSAI: 11521018000123"
        )
        PaymentReceiptDialog(
            payment = receivedPaymentReceipt!!,
            restaurant = safeRest,
            previousDue = previousDueForReceipt,
            remainingDue = remainingDueForReceipt,
            onDismiss = {
                showPaymentReceiptDialog = false
                receivedPaymentReceipt = null
            }
        )
    }

    // Customer Statement Dialog with Custom Dates & Format selection
    if (showStatementShareDialog && selectedPartyCustomer != null) {
        val party = selectedPartyCustomer!!
        val partyOutstanding = viewModel.getCustomerOutstandingCredit(party.name, party.contactNumber)
        val partyCreditBills = allBills.filter { b ->
            val phoneMatch = party.contactNumber.isNotBlank() && b.customerPhone.trim() == party.contactNumber.trim()
            val nameMatch = party.name.isNotBlank() && b.customerName.trim().equals(party.name.trim(), ignoreCase = true)
            (phoneMatch || nameMatch) && b.paymentMethod == "CREDIT"
        }
        val partyPayments = allCustomerPayments.filter { p ->
            val phoneMatch = party.contactNumber.isNotBlank() && p.customerPhone.trim() == party.contactNumber.trim()
            val nameMatch = party.name.isNotBlank() && p.customerName.trim().equals(party.name.trim(), ignoreCase = true)
            phoneMatch || nameMatch
        }
        val safeRest = activeRestaurant ?: RestaurantEntity(
            id = "default",
            name = "BBC FOOD HUB",
            phone = "9130694963",
            address = "Washim",
            fssaiNumber = ""
        )

        StatementShareDialog(
            customerName = party.name,
            customerPhone = party.contactNumber,
            outstandingBalance = partyOutstanding,
            allCustomerBills = partyCreditBills,
            allCustomerPayments = partyPayments,
            restaurant = safeRest,
            onDismiss = { showStatementShareDialog = false }
        )
    }

    // Custom Date Range Dialog
    if (showCustomDateDialog) {
        val startCal = Calendar.getInstance().apply { timeInMillis = customStartDate }
        val endCal = Calendar.getInstance().apply { timeInMillis = customEndDate }

        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            title = { Text("Select Date Range", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Start Date button
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val c = Calendar.getInstance()
                                    c.set(y, m, d, 0, 0, 0)
                                    c.set(Calendar.MILLISECOND, 0)
                                    customStartDate = c.timeInMillis
                                },
                                startCal.get(Calendar.YEAR),
                                startCal.get(Calendar.MONTH),
                                startCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("From: ${DateUtils.formatDate(customStartDate)}")
                    }

                    // End Date button
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val c = Calendar.getInstance()
                                    c.set(y, m, d, 23, 59, 59)
                                    c.set(Calendar.MILLISECOND, 999)
                                    customEndDate = c.timeInMillis
                                },
                                endCal.get(Calendar.YEAR),
                                endCal.get(Calendar.MONTH),
                                endCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("To: ${DateUtils.formatDate(customEndDate)}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (customEndDate < customStartDate) {
                        customEndDate = customStartDate + (24 * 60 * 60 * 1000L - 1)
                    }
                    showCustomDateDialog = false
                }) {
                    Text("Apply Range")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Party / Customer Selection Dialog
    if (showPartySelectDialog) {
        var partySearch by remember { mutableStateOf("") }
        // Unique parties from customers table plus any distinct names/phones in bills, sorted alphabetically A-Z
        val uniqueParties = remember(allCustomers, allBills, partySearch) {
            val fromCustomers = allCustomers.map { c ->
                c to viewModel.getCustomerOutstandingCredit(c.name, c.contactNumber)
            }.sortedBy { it.first.name.lowercase(Locale.getDefault()) }
            if (partySearch.isBlank()) {
                fromCustomers
            } else {
                fromCustomers.filter { (c, _) ->
                    c.name.contains(partySearch, ignoreCase = true) ||
                    c.contactNumber.contains(partySearch)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showPartySelectDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Party / Customer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = partySearch,
                        onValueChange = { partySearch = it },
                        placeholder = { Text("Search party by name/phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uniqueParties.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No customers found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(uniqueParties) { (customer, due) ->
                                val isSelected = selectedPartyCustomer?.id == customer.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPartyCustomer = customer
                                            showPartySelectDialog = false
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            if (customer.contactNumber.isNotBlank()) {
                                                Text(customer.contactNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        if (due > 0.0) {
                                            Surface(
                                                color = CreditPurple.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Due: ₹${String.format(Locale.US, "%.2f", due)}",
                                                    color = CreditPurple,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                TextButton(onClick = {
                    selectedPartyCustomer = null
                    showPartySelectDialog = false
                }) {
                    Text("Show All Parties")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPartySelectDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun BillHistoryCard(
    bill: BillEntity,
    viewModel: MainViewModel? = null,
    onClick: () -> Unit
) {
    val isCancelled = bill.isCancelled || bill.status == "CANCELLED" || bill.isVoided
    val liveDue = if (viewModel != null && bill.paymentMethod == "CREDIT") {
        viewModel.getCustomerOutstandingCredit(bill.customerName, bill.customerPhone)
    } else 0.0
    val isCreditCleared = bill.paymentMethod == "CREDIT" && liveDue <= 0.0001
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCancelled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface),
        border = if (isCancelled) BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bill.billNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isCancelled) ErrorRed else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (bill.orderType == "DINE_IN") CaramelWarm.copy(alpha = 0.15f) else EspressoBrown.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (bill.orderType == "DINE_IN") "Dine In (${bill.tableName ?: "Table"})" else "Takeaway",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (bill.orderType == "DINE_IN") CaramelWarm else EspressoBrown,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (isCancelled) "₹${String.format(Locale.US, "%.2f", bill.totalAmount)} (Cancelled)" else "₹${String.format(Locale.US, "%.2f", bill.totalAmount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCancelled) 14.sp else 17.sp,
                    color = if (isCancelled) ErrorRed else SuccessGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = DateUtils.formatDateTime(bill.billTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isCancelled) {
                        Surface(
                            color = ErrorRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "CANCELLED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = when {
                                isCreditCleared -> SuccessGreen.copy(alpha = 0.15f)
                                bill.paymentMethod == "CASH" -> CashAmber.copy(alpha = 0.15f)
                                bill.paymentMethod == "CREDIT" -> CreditPurple.copy(alpha = 0.15f)
                                else -> UPIBlue.copy(alpha = 0.15f)
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = when {
                                    isCreditCleared -> "CLEARED (CREDIT)"
                                    bill.paymentMethod == "CREDIT" -> "PENDING DUE"
                                    else -> "PAID VIA ${bill.paymentMethod}"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isCreditCleared -> SuccessGreen
                                    bill.paymentMethod == "CASH" -> CashAmber
                                    bill.paymentMethod == "CREDIT" -> CreditPurple
                                    else -> UPIBlue
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Customer: ${bill.customerName.ifBlank { "Customer" }} (${bill.customerPhone})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isCancelled && bill.cancellationReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${bill.cancellationReason}",
                    fontSize = 11.sp,
                    color = ErrorRed,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun BillDetailsDialog(
    bill: BillEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("Wrong Item Punched") }
    var customReasonInput by remember { mutableStateOf("") }
    var restoreInventoryToStock by remember { mutableStateOf(true) }
    var isProcessingAction by remember { mutableStateOf(false) }

    val isCancelled = bill.isCancelled || bill.status == "CANCELLED" || bill.isVoided
    var show8FormatPreview by remember { mutableStateOf(false) }

    val liveDue = remember(bill, viewModel.allBills, viewModel.allCustomerPayments) {
        viewModel.getCustomerOutstandingCredit(bill.customerName, bill.customerPhone)
    }

    if (show8FormatPreview) {
        val safeRest = activeRestaurant ?: RestaurantEntity(
            id = "default",
            name = "BBC FOOD HUB",
            phone = "9130694963",
            address = "Washim",
            fssaiNumber = ""
        )
        BillPreviewDialog(
            bill = bill,
            restaurant = safeRest,
            previousDue = liveDue,
            onDismiss = { show8FormatPreview = false },
            onSetDefaultTemplate = { chosenFormat ->
                viewModel.updateBillFormat(chosenFormat)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Bill ${bill.billNumber}", fontWeight = FontWeight.Bold)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Bill", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Date: ${DateUtils.formatDateTime(bill.billTimestamp)}", style = MaterialTheme.typography.bodySmall)
                Text("Type: ${if (bill.orderType == "DINE_IN") "Dine In (${bill.tableName ?: "Table"})" else "Takeaway"}", style = MaterialTheme.typography.bodySmall)
                Text("Payment: Paid via ${bill.paymentMethod}", style = MaterialTheme.typography.bodySmall)

                if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
                    Text("Customer: ${bill.customerName} | Phone: ${bill.customerPhone}", style = MaterialTheme.typography.bodySmall)
                }

                if (!bill.isStockDeducted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Status: Cancelled / Inventory Restored via FIFO", style = MaterialTheme.typography.bodySmall, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("Items:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                    items(bill.items) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.dishName} x${item.quantity}", fontSize = 12.sp)
                            Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal:", style = MaterialTheme.typography.bodySmall)
                    Text("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", style = MaterialTheme.typography.bodySmall)
                }
                if (bill.discountAmount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discount (${bill.discountType}):", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                        Text("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }
                }
                if (bill.appliedOfferName.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Offer Claimed:", style = MaterialTheme.typography.bodySmall, color = GoldAccent, fontWeight = FontWeight.Bold)
                        Text(bill.appliedOfferName, style = MaterialTheme.typography.bodySmall, color = GoldAccent, fontWeight = FontWeight.Bold)
                    }
                }
                if (bill.pointsRedeemed > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Points Redeemed (${bill.pointsRedeemed} Pts):", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                        Text("-₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}", style = MaterialTheme.typography.bodySmall, color = GoldAccent)
                    }
                }
                val netPaid = (bill.totalAmount - bill.pointsRedeemed).coerceAtLeast(0.0)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Net Paid Amount:", fontWeight = FontWeight.Bold)
                    Text("₹${String.format(Locale.US, "%.2f", netPaid)}", fontWeight = FontWeight.Bold, color = SuccessGreen)
                }
                if (bill.rewardPointsEarned > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Points Earned (on ₹${String.format(Locale.US, "%.0f", netPaid)}):", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                        Text("+${bill.rewardPointsEarned} Pts", style = MaterialTheme.typography.bodySmall, color = SuccessGreen, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 8 Designer Formats Live Preview
                Button(
                    onClick = { show8FormatPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmber, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🎨 Choose Bill Format & Style", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions: Receipt Image, A4 PDF, WhatsApp, SMS
                val currentRest = activeRestaurant ?: RestaurantEntity(id = "default", name = "BBC FOOD HUB", address = "Washim", phone = "9130694963")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val imgFile = viewModel.generateBillImageFile(bill, liveDue)
                            if (imgFile != null) {
                                BillShareUtil.viewReceiptImage(context, imgFile)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Receipt Image", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val pdfFile = viewModel.generateInvoicePdfFile(bill, liveDue)
                            if (pdfFile != null) {
                                BillShareUtil.viewA4Pdf(context, pdfFile)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("A4 PDF", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val imgFile = viewModel.generateBillImageFile(bill, liveDue)
                            if (imgFile != null) {
                                BillShareUtil.sendViaWhatsAppImage(context, bill, currentRest, imgFile, liveDue)
                            } else {
                                val pdfFile = viewModel.generateInvoicePdfFile(bill, liveDue)
                                BillShareUtil.sendViaWhatsApp(context, bill, currentRest, pdfFile, liveDue)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            BillShareUtil.sendViaSms(context, bill, currentRest)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SMS", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons: Cancel vs Permanent Delete
                if (!isCancelled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel Bill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Bill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Cancelled Bill Permanently", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Dialog 1: Cancel Bill with Reason & Inventory choice
                if (showCancelDialog) {
                    val cancelReasons = listOf(
                        "Wrong Item Punched",
                        "Customer Left / Cancelled",
                        "Kitchen Mistake / Food Issue",
                        "Payment Issue / Failed",
                        "Test / Demo Bill"
                    )

                    AlertDialog(
                        onDismissRequest = { if (!isProcessingAction) showCancelDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cancel Bill ${bill.billNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Cancelling this bill will reverse customer points, adjust DSR sales, and update inventory.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Select Reason:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    cancelReasons.forEach { reason ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedReason = reason }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = selectedReason == reason,
                                                onClick = { selectedReason = reason }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(reason, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customReasonInput,
                                    onValueChange = { customReasonInput = it },
                                    placeholder = { Text("Custom notes (optional)...", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Restore Ingredients to Stock", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(
                                            if (restoreInventoryToStock) "Raw materials will be credited back to batches." else "Marked as kitchen wastage (stock will not increase).",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = restoreInventoryToStock,
                                        onCheckedChange = { restoreInventoryToStock = it }
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isProcessingAction = true
                                    val finalReason = if (customReasonInput.isNotBlank()) {
                                        "$selectedReason - $customReasonInput"
                                    } else selectedReason

                                    viewModel.cancelBill(
                                        billId = bill.id,
                                        reason = finalReason,
                                        restoreInventory = restoreInventoryToStock
                                    ) { success ->
                                        isProcessingAction = false
                                        showCancelDialog = false
                                        Toast.makeText(
                                            context,
                                            if (success) "Bill cancelled & DSR/Inventory adjusted!" else "Could not cancel bill",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                enabled = !isProcessingAction
                            ) {
                                Text(if (isProcessingAction) "Processing..." else "Yes, Cancel Bill")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showCancelDialog = false },
                                enabled = !isProcessingAction
                            ) {
                                Text("Dismiss")
                            }
                        }
                    )
                }

                // Dialog 2: Delete Permanently
                if (showDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!isProcessingAction) showDeleteConfirmDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Bill Permanently?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        },
                        text = {
                            Text(
                                "Are you sure you want to permanently delete bill ${bill.billNumber}? This will remove it from both local POS and Cloud Firestore. Any associated stock and customer points will be safely adjusted.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isProcessingAction = true
                                    viewModel.deleteBillPermanently(bill.id, restoreInventory = true) { success ->
                                        isProcessingAction = false
                                        showDeleteConfirmDialog = false
                                        Toast.makeText(
                                            context,
                                            if (success) "Bill permanently deleted from POS & Cloud!" else "Error deleting bill",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                enabled = !isProcessingAction
                            ) {
                                Text(if (isProcessingAction) "Deleting..." else "Delete Permanently")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteConfirmDialog = false },
                                enabled = !isProcessingAction
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog to view & edit backdated or previously settled bills.
 */
@Composable
fun EditBillDialog(
    bill: BillEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var customerName by remember { mutableStateOf(bill.customerName) }
    var customerPhone by remember { mutableStateOf(bill.customerPhone) }
    var paymentMethod by remember { mutableStateOf(bill.paymentMethod) }
    var billTimestamp by remember { mutableStateOf(bill.billTimestamp) }
    var discountType by remember { mutableStateOf(bill.discountType) }
    var discountValueStr by remember { mutableStateOf(if (bill.discountValue > 0) bill.discountValue.toString() else "") }
    var itemsList by remember { mutableStateOf(bill.items) }

    val subtotal = remember(itemsList) { itemsList.sumOf { it.totalPrice } }
    val discountValue = discountValueStr.toDoubleOrNull() ?: 0.0
    val discountAmount = when (discountType) {
        "PERCENT" -> (subtotal * (discountValue.coerceIn(0.0, 100.0) / 100.0))
        "FLAT" -> discountValue.coerceAtMost(subtotal)
        else -> 0.0
    }
    val totalAmount = (subtotal - discountAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Bill ${bill.billNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Update customer details, payment method, discount, or bill date:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))

                // Date & Time picker
                Card(colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Date: ${DateUtils.formatDateTime(billTimestamp)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = billTimestamp }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    cal.set(Calendar.YEAR, y)
                                    cal.set(Calendar.MONTH, m)
                                    cal.set(Calendar.DAY_OF_MONTH, d)
                                    TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            cal.set(Calendar.HOUR_OF_DAY, h)
                                            cal.set(Calendar.MINUTE, min)
                                            billTimestamp = cal.timeInMillis
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Text("Edit Date", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Customer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("Contact Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Payment Method
                Text("Payment Method:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    FilterChip(
                        selected = paymentMethod == "CREDIT",
                        onClick = { paymentMethod = "CREDIT" },
                        label = { Text("CREDIT") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Discount
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = discountType == "NONE", onClick = { discountType = "NONE" }, label = { Text("No Disc") })
                    FilterChip(selected = discountType == "FLAT", onClick = { discountType = "FLAT" }, label = { Text("Flat ₹") })
                    FilterChip(selected = discountType == "PERCENT", onClick = { discountType = "PERCENT" }, label = { Text("%") })
                }
                if (discountType != "NONE") {
                    OutlinedTextField(
                        value = discountValueStr,
                        onValueChange = { discountValueStr = it },
                        label = { Text(if (discountType == "PERCENT") "Discount %" else "Discount ₹") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Updated Total:", fontWeight = FontWeight.Bold)
                    Text("₹${String.format(Locale.US, "%.2f", totalAmount)}", fontWeight = FontWeight.Bold, color = SuccessGreen)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val updated = bill.copy(
                    customerName = customerName.trim(),
                    customerPhone = customerPhone.trim(),
                    paymentMethod = paymentMethod,
                    billTimestamp = billTimestamp,
                    discountType = discountType,
                    discountValue = discountValue,
                    discountAmount = discountAmount,
                    totalAmount = totalAmount,
                    updatedAt = System.currentTimeMillis()
                )
                viewModel.updateBill(updated)
                onSaved()
            }) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
