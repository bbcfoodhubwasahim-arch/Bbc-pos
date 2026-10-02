package com.example.ui.screens.billing

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.CafeTableEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.MainViewModel
import com.example.ui.components.RechartsDailySalesSummaryCard
import com.example.ui.theme.*
import com.example.util.BillShareUtil
import com.example.util.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val cartState by viewModel.cartState.collectAsState()
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val settledBill by viewModel.settledBill.collectAsState()
    val activeKotBill by viewModel.activeKotBill.collectAsState()
    val activeUnsettledBills by viewModel.activeUnsettledBills.collectAsState()

    val orderMode by viewModel.orderMode.collectAsState()
    val isOrderingMode by viewModel.isOrderingMode.collectAsState()
    val selectedVacantTableForCustomer by viewModel.selectedVacantTable.collectAsState()
    val showTakeawayCustomerDialog by viewModel.showTakeawayCustomerDialog.collectAsState()
    val isSettlementScreen by viewModel.isSettlementScreen.collectAsState()

    var showDiscardConfirmationDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    val hasUnsavedCartData = remember(cartState) {
        cartState.items.isNotEmpty() ||
            cartState.customerName.isNotBlank() ||
            cartState.customerPhone.isNotBlank() ||
            cartState.deliveryAddress.isNotBlank()
    }

    // Intercept Back Press:
    // 1. Settlement Screen -> Go back to Menu/Cart ordering view
    // 2. Open Customer Dialogs -> Close dialog
    // 3. Ordering Mode with active/unsaved data -> Show confirmation alert dialog
    // 4. Main Dashboard / Billing Screen -> Double tap back to exit with Toast message
    BackHandler(enabled = true) {
        when {
            isSettlementScreen -> {
                viewModel.setSettlementScreen(false)
            }
            selectedVacantTableForCustomer != null -> {
                viewModel.setSelectedVacantTable(null)
            }
            showTakeawayCustomerDialog -> {
                viewModel.setShowTakeawayCustomerDialog(false)
            }
            isOrderingMode -> {
                if (hasUnsavedCartData) {
                    showDiscardConfirmationDialog = true
                } else {
                    viewModel.clearCart()
                }
            }
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Confirmation Alert Dialog when back is pressed with unsaved cart/customer data
    if (showDiscardConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmationDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Discard Unsaved Cart?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to go back? Unsaved cart and customer details will be lost.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmationDialog = false
                        viewModel.clearCart()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Discard & Go Back", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardConfirmationDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Keep Working")
                }
            }
        )
    }

    if (isSettlementScreen) {
        SettlementScreen(
            viewModel = viewModel,
            onBack = { viewModel.setSettlementScreen(false) },
            onSettled = {
                viewModel.setSettlementScreen(false)
                viewModel.setOrderingMode(false)
            }
        )
    } else {
        Scaffold(
        topBar = {
            if (!isOrderingMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = activeRestaurant?.name ?: "BBC POS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Point of Sale • Retail Billing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Order Type Switcher: Dine In & Takeaway
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = orderMode == "DINE_IN",
                                    onClick = {
                                        if (orderMode != "DINE_IN") {
                                            if (isOrderingMode && hasUnsavedCartData) {
                                                showDiscardConfirmationDialog = true
                                            } else {
                                                viewModel.setOrderMode("DINE_IN")
                                            }
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.Default.TableRestaurant,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                ) {
                                    Text("Dine In", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                SegmentedButton(
                                    selected = orderMode == "TAKEAWAY",
                                    onClick = {
                                        if (orderMode != "TAKEAWAY") {
                                            if (isOrderingMode && hasUnsavedCartData) {
                                                showDiscardConfirmationDialog = true
                                            } else {
                                                viewModel.setOrderMode("TAKEAWAY")
                                            }
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    icon = {
                                        Icon(
                                            Icons.Default.TakeoutDining,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                ) {
                                    Text("Takeaway", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (orderMode == "DINE_IN") {
                if (isOrderingMode) {
                    // Step 2 for Dine In: Menu Ordering Grid & Cart
                    MenuOrderingView(
                        categories = categories,
                        menuItems = menuItems,
                        viewModel = viewModel,
                        onBackToTables = {
                            if (hasUnsavedCartData) {
                                showDiscardConfirmationDialog = true
                            } else {
                                viewModel.clearCart()
                            }
                        },
                        onSendToKitchen = {
                            viewModel.sendCurrentOrderToKitchen {
                                Toast.makeText(context, "KOT sent to kitchen!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenCashOut = {
                            viewModel.setSettlementScreen(true)
                        }
                    )
                } else {
                    // Step 1 for Dine In: Table Grid View (Vacant & Occupied colors, duration, transfer, add/delete)
                    TableGridView(
                        tables = tables,
                        activeBills = activeUnsettledBills,
                        viewModel = viewModel,
                        onSelectVacantTable = { table ->
                            // Open Customer Details Dialog first (Requirement 3: Table click -> Customer details -> Menu items)
                            viewModel.setSelectedVacantTable(table)
                        },
                        onSelectOccupiedTable = { table, bill ->
                            // Load existing active order into cart to add more items
                            viewModel.loadActiveOrderForTable(table) {
                                viewModel.setOrderingMode(true)
                            }
                        },
                        onCashOutTable = { table, bill ->
                            // Directly cash out occupied table
                            viewModel.loadActiveOrderForTable(table) {
                                viewModel.setSettlementScreen(true)
                            }
                        }
                    )
                }
            } else {
                // TAKEAWAY MODE
                if (isOrderingMode) {
                    MenuOrderingView(
                        categories = categories,
                        menuItems = menuItems,
                        viewModel = viewModel,
                        onBackToTables = {
                            if (hasUnsavedCartData) {
                                showDiscardConfirmationDialog = true
                            } else {
                                viewModel.clearCart()
                            }
                        },
                        onSendToKitchen = {
                            viewModel.sendCurrentOrderToKitchen {
                                Toast.makeText(context, "Takeaway KOT sent to kitchen!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenCashOut = {
                            viewModel.setSettlementScreen(true)
                        }
                    )
                } else {
                    // Takeaway Home Screen: Start new order or view active running takeaway orders
                    val activeTakeawayBills = remember(activeUnsettledBills) {
                        activeUnsettledBills.filter { it.orderType == "TAKEAWAY" }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(CaramelWarm, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TakeoutDining, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("New Takeaway Order", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "Take customer details and add items for parcel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.setShowTakeawayCustomerDialog(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Takeaway Order", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Recharts-based 7-Day Daily Sales Summary Chart
                        val allBills by viewModel.allBills.collectAsState()
                        RechartsDailySalesSummaryCard(
                            bills = allBills,
                            initialExpanded = false,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Running Takeaway Orders (${activeTakeawayBills.size})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (activeTakeawayBills.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No running takeaway orders", color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(activeTakeawayBills) { bill ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    bill.billNumber,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                if (bill.customerName.isNotBlank()) {
                                                    Text(
                                                        bill.customerName,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                Text(
                                                    "${bill.items.sumOf { it.quantity }} items • ₹${String.format(Locale.US, "%.2f", bill.totalAmount)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = CaramelWarm
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.loadActiveTakeawayOrder(bill) {
                                                            viewModel.setOrderingMode(true)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("Edit")
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.loadActiveTakeawayOrder(bill) {
                                                            viewModel.setSettlementScreen(true)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("Cash Out")
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
        }
    }
    }

    // Step 1 Dialog for Dine In: Customer Details (with phone lookup and history)
    selectedVacantTableForCustomer?.let { table ->
        CustomerDetailsDialog(
            tableName = table.name,
            initialName = cartState.customerName,
            initialPhone = cartState.customerPhone,
            initialAddress = cartState.deliveryAddress,
            viewModel = viewModel,
            onDismiss = { viewModel.setSelectedVacantTable(null) },
            onContinue = { name, phone, address ->
                viewModel.startNewOrderForTable(table, name, phone, address)
            }
        )
    }

    // Step 1 Dialog for Takeaway: Customer Details
    if (showTakeawayCustomerDialog) {
        CustomerDetailsDialog(
            tableName = null,
            initialName = cartState.customerName,
            initialPhone = cartState.customerPhone,
            initialAddress = cartState.deliveryAddress,
            viewModel = viewModel,
            onDismiss = { viewModel.setShowTakeawayCustomerDialog(false) },
            onContinue = { name, phone, address ->
                viewModel.startNewTakeawayOrder(name, phone, address)
            }
        )
    }

    // Kitchen Order Ticket (KOT) Dialog (Requirement 9: items only, no prices)
    activeKotBill?.let { kotBill ->
        KotDialog(
            bill = kotBill,
            onDismiss = { viewModel.dismissKot() }
        )
    }

    // Settled Bill Invoice Dialog (A4 PDF & WhatsApp & SMS)
    settledBill?.let { bill ->
        PostSettleBillDialog(
            bill = bill,
            viewModel = viewModel,
            onDismiss = { viewModel.clearSettledBill() }
        )
    }
}

@Composable
fun BillingCheckoutDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSettled: () -> Unit
) {
    val context = LocalContext.current
    val cartState by viewModel.cartState.collectAsState()
    var paymentMethod by remember(cartState.activeBillId, cartState.paymentMethod) {
        mutableStateOf(cartState.paymentMethod.ifBlank { "CASH" })
    }
    var discountType by remember(cartState.activeBillId, cartState.discountType) {
        mutableStateOf(cartState.discountType.ifBlank { "FLAT" })
    }
    var discountValueStr by remember(cartState.activeBillId, cartState.discountValue) {
        mutableStateOf(if (cartState.discountValue > 0) cartState.discountValue.toString() else "")
    }
    var showDiscountFields by remember { mutableStateOf(false) }

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
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cash Out & Settle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (cartState.orderType == "DINE_IN") {
                                "Table: ${cartState.selectedTable?.name ?: "Dine In"}"
                            } else "Takeaway Order",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Order Items Summary preview
                Text("Order Summary", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                ) {
                    items(cartState.items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.dishName} x${item.quantity}", fontSize = 13.sp)
                            Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Subtotal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal:")
                    Text("₹${String.format(Locale.US, "%.2f", cartState.subtotal)}", fontWeight = FontWeight.SemiBold)
                }

                // Discount section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiscountFields = !showDiscountFields }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Discount", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        Icon(
                            if (showDiscountFields) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (cartState.discountAmount > 0) {
                        Text("-₹${String.format(Locale.US, "%.2f", cartState.discountAmount)}", color = ErrorRed, fontWeight = FontWeight.Bold)
                    } else {
                        Text("₹0.00")
                    }
                }

                if (showDiscountFields) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = discountType == "FLAT",
                            onClick = {
                                discountType = "FLAT"
                                val dVal = discountValueStr.toDoubleOrNull() ?: 0.0
                                viewModel.setDiscount("FLAT", dVal)
                            },
                            label = { Text("Flat ₹") }
                        )
                        FilterChip(
                            selected = discountType == "PERCENT",
                            onClick = {
                                discountType = "PERCENT"
                                val dVal = discountValueStr.toDoubleOrNull() ?: 0.0
                                viewModel.setDiscount("PERCENT", dVal)
                            },
                            label = { Text("Percent %") }
                        )
                        OutlinedTextField(
                            value = discountValueStr,
                            onValueChange = {
                                discountValueStr = it
                                val dVal = it.toDoubleOrNull() ?: 0.0
                                viewModel.setDiscount(discountType, dVal)
                            },
                            placeholder = { Text("Value") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Total Banner (Strictly NO GST)
                Surface(
                    color = EspressoDark,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL AMOUNT", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "₹${String.format(Locale.US, "%.2f", cartState.totalAmount)}",
                            color = AmberGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Payment Method Selector: Cash, UPI, or Credit
                Text("Select Payment Method", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            paymentMethod = "CASH"
                            viewModel.setPaymentMethod("CASH")
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (paymentMethod == "CASH") CashAmber.copy(alpha = 0.15f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = CashAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CASH", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            paymentMethod = "UPI"
                            viewModel.setPaymentMethod("UPI")
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (paymentMethod == "UPI") UPIBlue.copy(alpha = 0.15f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = UPIBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("UPI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (cartState.customerName.isBlank() && cartState.customerPhone.isBlank()) {
                                Toast.makeText(context, "Please enter customer name or phone to use Credit", Toast.LENGTH_SHORT).show()
                            } else {
                                paymentMethod = "CREDIT"
                                viewModel.setPaymentMethod("CREDIT")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (paymentMethod == "CREDIT") CreditPurple.copy(alpha = 0.15f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = CreditPurple, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CREDIT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (paymentMethod == "CREDIT") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bill will be added to ${cartState.customerName.ifBlank { "Customer" }}'s credit balance",
                        color = CreditPurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Settle Button
                Button(
                    onClick = {
                        if (cartState.activeBillId != null) {
                            viewModel.cashOutActiveBill(
                                billId = cartState.activeBillId!!,
                                paymentMethod = paymentMethod,
                                discountType = discountType,
                                discountValue = discountValueStr.toDoubleOrNull() ?: 0.0
                            ) {
                                onSettled()
                            }
                        } else {
                            viewModel.settleCurrentBill(
                                paymentMethod = paymentMethod,
                                discountType = discountType,
                                discountValue = discountValueStr.toDoubleOrNull() ?: 0.0
                            ) {
                                onSettled()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Complete Payment & Settle Bill", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dialog shown after settling a bill:
 * Direct options to:
 * - Generate & View/Print A4 PDF
 * - Share via WhatsApp
 * - Share via direct SMS
 */
@Composable
fun PostSettleBillDialog(
    bill: BillEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bill Settled Successfully!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = CreamSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Invoice No: ${bill.billNumber}", fontWeight = FontWeight.Bold)
                        Text("Amount: ₹${String.format(Locale.US, "%.2f", bill.totalAmount)}  •  Paid via ${bill.paymentMethod}")
                        Text("Type: ${if (bill.orderType == "DINE_IN") "Dine In (${bill.tableName ?: "Table"})" else "Takeaway"}")
                        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
                            Text("Customer: ${bill.customerName} (${bill.customerPhone})")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Direct Invoice Sharing Options:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // View Receipt Image
                Button(
                    onClick = {
                        val rest = activeRestaurant ?: return@Button
                        val imgFile = viewModel.generateBillImageFile(bill)
                        if (imgFile != null) {
                            com.example.util.BillShareUtil.viewReceiptImage(context, imgFile)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View & Print Receipt Image (${com.example.ui.components.BillTemplateDesign.fromId(activeRestaurant?.billFormat).title})", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // View A4 PDF
                OutlinedButton(
                    onClick = {
                        val rest = activeRestaurant ?: return@OutlinedButton
                        val liveDue = viewModel.getCustomerOutstandingCredit(bill.customerName, bill.customerPhone)
                        val pdfFile = viewModel.generateInvoicePdfFile(bill, liveDue)
                        if (pdfFile != null) {
                            BillShareUtil.viewA4Pdf(context, pdfFile)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View & Print A4 Size PDF Invoice")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Share via WhatsApp
                Button(
                    onClick = {
                        val rest = activeRestaurant ?: RestaurantEntity(id = "default", name = "BBC FOOD HUB", address = "Washim", phone = "9130694963")
                        val liveDue = viewModel.getCustomerOutstandingCredit(bill.customerName, bill.customerPhone)
                        val imgFile = viewModel.generateBillImageFile(bill, liveDue)
                        if (imgFile != null) {
                            BillShareUtil.sendViaWhatsAppImage(context, bill, rest, imgFile, liveDue)
                        } else {
                            val pdfFile = viewModel.generateInvoicePdfFile(bill, liveDue)
                            BillShareUtil.sendViaWhatsApp(context, bill, rest, pdfFile, liveDue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send via WhatsApp to Customer")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Send via direct SMS
                OutlinedButton(
                    onClick = {
                        val rest = activeRestaurant ?: return@OutlinedButton
                        BillShareUtil.sendViaSms(context, bill, rest)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, tint = UPIBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send via Direct SMS")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("New Order")
            }
        }
    )
}
