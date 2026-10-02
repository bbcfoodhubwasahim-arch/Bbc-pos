package com.example.ui.screens.billing

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.BillEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import com.example.util.DateUtils
import java.util.Locale

@Composable
fun CustomerDetailsDialog(
    initialPhone: String = "",
    initialName: String = "",
    initialAddress: String = "",
    tableName: String? = null,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onContinue: (name: String, phone: String, address: String) -> Unit
) {
    val allCustomers by viewModel.allCustomers.collectAsState()
    var phone by remember { mutableStateOf(initialPhone) }
    var name by remember { mutableStateOf(initialName) }
    var birthday by remember { mutableStateOf("") }
    var address by remember { mutableStateOf(initialAddress) }
    var isExistingCustomer by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Partial matching customer suggestions
    val phoneSuggestions = remember(phone, allCustomers) {
        val clean = phone.trim().filter { it.isDigit() }
        if (clean.length in 2..9) {
            allCustomers.filter {
                val custDigits = it.contactNumber.filter { c -> c.isDigit() }
                custDigits.startsWith(clean) || custDigits.contains(clean)
            }.take(4)
        } else emptyList()
    }

    // Auto-lookup customer when phone number changes
    LaunchedEffect(phone) {
        val cleanPhone = phone.trim()
        if (cleanPhone.length >= 7) {
            viewModel.findCustomerByPhone(cleanPhone) { existing ->
                if (existing != null) {
                    if (name.isBlank() || isExistingCustomer) {
                        name = existing.name
                    }
                    isExistingCustomer = true
                } else {
                    isExistingCustomer = false
                }
            }
        } else {
            isExistingCustomer = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (tableName != null) Icons.Default.TableRestaurant else Icons.Default.TakeoutDining,
                    contentDescription = null,
                    tint = CaramelWarm
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (tableName != null) "Customer for $tableName" else "Takeaway Customer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Enter phone to auto-fetch returning customer details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mobile Number Field
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    placeholder = { Text("e.g. 9876543210") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (phoneSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        phoneSuggestions.forEach { cust ->
                            SuggestionChip(
                                onClick = {
                                    phone = cust.contactNumber
                                    name = cust.name
                                    isExistingCustomer = true
                                },
                                label = {
                                    Text("${cust.name} (${cust.contactNumber.takeLast(4)})", fontSize = 11.sp)
                                },
                                icon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }

                // Customer Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isExistingCustomer = false
                    },
                    label = { Text("Customer Name") },
                    placeholder = { Text("e.g. Rohan Sharma") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Delivery Address / Notes Field
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(if (tableName != null) "Table Notes / Section (Optional)" else "Delivery Address / Notes (Optional)") },
                    placeholder = { Text(if (tableName != null) "e.g. Window side, AC hall" else "e.g. Flat 101, Main Road") },
                    leadingIcon = { Icon(if (tableName != null) Icons.Default.Place else Icons.Default.Home, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Requirement 11: Optional Customer Birthday Field
                OutlinedTextField(
                    value = birthday,
                    onValueChange = { birthday = it },
                    label = { Text("Birthday (Optional e.g. 15 Aug / 15-08)") },
                    placeholder = { Text("e.g. 15/08 or 15 Aug") },
                    leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Returning Customer Indicator & Outstanding Dues Alert
                val currentOutstandingDue = remember(name, phone, viewModel.allBills, viewModel.allCustomerPayments) {
                    viewModel.getCustomerOutstandingCredit(name, phone)
                }

                if (isExistingCustomer || currentOutstandingDue > 0.0) {
                    val matchingCustomer = remember(phone, allCustomers) {
                        val clean = phone.trim().filter { it.isDigit() }
                        allCustomers.firstOrNull {
                            val d = it.contactNumber.filter { c -> c.isDigit() }
                            d.isNotBlank() && (d == clean || (clean.length >= 10 && d.endsWith(clean.takeLast(10))))
                        }
                    }
                    var showGiftPointsDialog by remember { mutableStateOf(false) }

                    Surface(
                        color = if (currentOutstandingDue > 0.0) Color(0xFFFEF3C7) else SuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (currentOutstandingDue > 0.0) Color(0xFFF59E0B) else SuccessGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            imageVector = if (currentOutstandingDue > 0.0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (currentOutstandingDue > 0.0) Color(0xFFD97706) else SuccessGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isExistingCustomer) "Customer: $name" else "Customer Record Found",
                                            fontSize = 12.sp,
                                            color = if (currentOutstandingDue > 0.0) Color(0xFF92400E) else SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (matchingCustomer != null) {
                                            TextButton(
                                                onClick = { showGiftPointsDialog = true },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFB45309))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Gift Points", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                            }
                                        }
                                        TextButton(
                                            onClick = { showHistoryDialog = true },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Past Orders", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                        }
                                    }
                                }
                            }

                            if (matchingCustomer != null) {
                                val rewardPts = matchingCustomer.rewardPointsBalance
                                val giftPts = matchingCustomer.giftPointsBalance
                                val totalPts = rewardPts + giftPts
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "⭐ Points Balance: $totalPts Pts (Reward: $rewardPts | Gift: $giftPts)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF78350F)
                                )
                            }

                            if (currentOutstandingDue > 0.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ Previous Outstanding Due: ₹${String.format(Locale.US, "%.2f", currentOutstandingDue)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }

                    if (showGiftPointsDialog && matchingCustomer != null) {
                        AddGiftPointsDialog(
                            customer = matchingCustomer,
                            viewModel = viewModel,
                            onDismiss = { showGiftPointsDialog = false }
                        )
                    }
                } else if (phone.isNotBlank()) {
                    // Option to check history even if not explicitly matched yet
                    TextButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check Order History", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanP = phone.trim()
                    val cleanN = name.trim()
                    if (cleanP.isNotBlank() || cleanN.isNotBlank()) {
                        viewModel.registerOrUpdateCustomer(
                            name = cleanN.ifBlank { "Customer" },
                            phone = cleanP,
                            birthday = birthday.trim().ifBlank { null }
                        )
                    }
                    onContinue(cleanN, cleanP, address.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Continue to Menu", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = { onContinue("", "", "") }) {
                Text("Skip (Walk-in)")
            }
        }
    )

    // Customer History Dialog
    if (showHistoryDialog && phone.isNotBlank()) {
        CustomerHistoryDialog(
            phone = phone,
            customerName = name,
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@Composable
fun CustomerHistoryDialog(
    phone: String,
    customerName: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val bills by viewModel.getCustomerBillHistory(phone).collectAsState(initial = emptyList())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Customer Order History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${customerName.ifBlank { "Customer" }} • $phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (bills.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No past orders found for this number", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        text = "${bills.size} Past Orders",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bills) { bill ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(bill.billNumber, fontWeight = FontWeight.Bold)
                                        Text(
                                            "₹${String.format(Locale.US, "%.2f", bill.totalAmount)}",
                                            fontWeight = FontWeight.Bold,
                                            color = CaramelWarm
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            DateUtils.formatDateTime(bill.billTimestamp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            if (bill.orderType == "DINE_IN") "Dine In (${bill.tableName ?: "Table"})" else "Takeaway",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = bill.items.joinToString(", ") { "${it.dishName} x${it.quantity}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun AddGiftPointsDialog(
    customer: com.example.data.local.entity.CustomerEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var pointsInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var selectedPresetDays by remember { mutableIntStateOf(60) } // 7, 15, 30, 60, 90, -1 (No Expiry), 0 (Custom)
    var customDaysInput by remember { mutableStateOf("") }

    val effectiveDays = when (selectedPresetDays) {
        -1 -> 0 // 0 means Lifetime / No Expiry
        0 -> customDaysInput.toIntOrNull() ?: 30
        else -> selectedPresetDays
    }

    val expiryPreview = remember(selectedPresetDays, customDaysInput) {
        if (selectedPresetDays == -1) {
            "Lifetime • Never Expires"
        } else {
            val d = if (selectedPresetDays == 0) (customDaysInput.toIntOrNull() ?: 30) else selectedPresetDays
            val expMillis = System.currentTimeMillis() + d.toLong() * 24 * 60 * 60 * 1000L
            "Valid for $d days (Till ${DateUtils.formatDate(expMillis, "dd MMM yyyy")})"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = GoldAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Award Gift Points", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${customer.name} (${customer.contactNumber})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Points Input
                OutlinedTextField(
                    value = pointsInput,
                    onValueChange = { pointsInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Gift Points Amount") },
                    placeholder = { Text("e.g. 50, 100, 200") },
                    leadingIcon = { Icon(Icons.Default.Stars, contentDescription = null, tint = GoldAccent) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Points Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(25, 50, 100, 200).forEach { pt ->
                        SuggestionChip(
                            onClick = { pointsInput = pt.toString() },
                            label = { Text("+$pt", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Text("Validity (Expiry):", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                // Validity Presets Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        7 to "7 Days",
                        15 to "15 Days",
                        30 to "30 Days",
                        60 to "60 Days"
                    ).forEach { (d, label) ->
                        FilterChip(
                            selected = selectedPresetDays == d,
                            onClick = { selectedPresetDays = d },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedPresetDays == 90,
                        onClick = { selectedPresetDays = 90 },
                        label = { Text("90 Days", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedPresetDays == -1,
                        onClick = { selectedPresetDays = -1 },
                        label = { Text("♾️ No Expiry", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = selectedPresetDays == 0,
                        onClick = { selectedPresetDays = 0 },
                        label = { Text("Custom Days", fontSize = 11.sp) }
                    )
                }

                if (selectedPresetDays == 0) {
                    OutlinedTextField(
                        value = customDaysInput,
                        onValueChange = { customDaysInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Enter Number of Days") },
                        placeholder = { Text("e.g. 45, 180") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    color = GoldAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📅 $expiryPreview",
                        color = GoldTextDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // Reason / Note
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Reason / Notes (Optional)") },
                    placeholder = { Text("e.g. Birthday Gift, Festival Offer, VIP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pts = pointsInput.toIntOrNull() ?: 0
                    if (pts > 0) {
                        viewModel.addGiftPoints(
                            customerId = customer.id,
                            points = pts,
                            notes = notesInput.trim(),
                            expiryDays = effectiveDays
                        ) {
                            onDismiss()
                        }
                    }
                },
                enabled = (pointsInput.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Award Points", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
