package com.example.ui.screens.billing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BillItem
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.AddonDefinitionEntity
import com.example.data.local.entity.parseVariants
import com.example.data.local.entity.parseAddons
import com.example.ui.CartState
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderConfirmationView(
    cartState: CartState,
    menuItems: List<MenuItemEntity>,
    allAddonDefinitions: List<AddonDefinitionEntity>,
    viewModel: MainViewModel,
    onBackToMenu: () -> Unit,
    onSendToKitchen: () -> Unit,
    onOpenCashOut: () -> Unit,
    onEditItem: (MenuItemEntity, BillItem) -> Unit
) {
    BackHandler {
        onBackToMenu()
    }

    var cookingNoteInput by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Entire Order?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove all items from this order?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCart()
                        showClearConfirmation = false
                        onBackToMenu()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Order", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP BAR
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackToMenu) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Menu",
                            tint = EspressoDark
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Confirm Order",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = EspressoDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (cartState.orderType == "DINE_IN") CaramelWarm.copy(alpha = 0.15f) else Color(0xFF1565C0).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (cartState.orderType == "DINE_IN") {
                                        cartState.selectedTable?.name ?: "Dine In"
                                    } else {
                                        "Takeaway"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cartState.orderType == "DINE_IN") CaramelWarm else Color(0xFF1565C0),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (cartState.customerName.isNotBlank() || cartState.customerPhone.isNotBlank()) {
                            Text(
                                text = listOf(cartState.customerName, cartState.customerPhone).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (cartState.items.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirmation = true }) {
                        Text(
                            "Clear All",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (cartState.items.isEmpty()) {
            // EMPTY STATE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your order is empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add some delicious dishes from the menu to continue.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBackToMenu,
                        colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Dishes from Menu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // FULL SCROLLABLE ORDER ITEMS LIST
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Order Items (${cartState.items.sumOf { it.quantity }})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = EspressoDark
                        )
                        Text(
                            text = "Check items & quantities",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ALL ITEMS LISTED CLEARLY TOP-TO-BOTTOM
                items(cartState.items, key = { it.dishId + it.selectedVariant + it.selectedAddonsJson }) { cartItem ->
                    val originalDish = remember(menuItems, cartItem.dishId) {
                        menuItems.find { it.id == cartItem.dishId }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cartItem.getFormattedDisplayName(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = EspressoDark
                                        )
                                        if (cartItem.isFree) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFF16A34A).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (cartItem.notes.contains("Complimentary", ignoreCase = true)) "🎁 COMPLIMENTARY" else "🎁 FREE",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF16A34A),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (originalDish != null) {
                                            val hasCustom = remember(originalDish, allAddonDefinitions) {
                                                parseVariants(originalDish.variantsJson).isNotEmpty() ||
                                                parseAddons(originalDish.addonsJson).isNotEmpty() ||
                                                allAddonDefinitions.any { it.categoryId == originalDish.categoryId }
                                            }
                                            if (hasCustom) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { onEditItem(originalDish, cartItem) },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "Edit Add-ons",
                                                        tint = CaramelWarm,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val addonsSummary = cartItem.getAddonsSummary()
                                    if (addonsSummary.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = addonsSummary,
                                            fontSize = 12.sp,
                                            color = CaramelWarm,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (cartItem.isFree) "100% Free (₹0.00)" else "₹${String.format(Locale.US, "%.2f", cartItem.unitPrice)} each",
                                        fontSize = 12.sp,
                                        color = if (cartItem.isFree) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (cartItem.isFree) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                Text(
                                    text = if (cartItem.isFree) "₹0.00" else "₹${String.format(Locale.US, "%.2f", cartItem.totalPrice)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (cartItem.isFree) Color(0xFF16A34A) else EspressoDark
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // QUANTITY STEPPER & DELETE BUTTON
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Quantity",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Decrement button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.decreaseCartItem(cartItem) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Remove,
                                                contentDescription = "Decrease",
                                                tint = EspressoDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = cartItem.quantity.toString(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = EspressoDark,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )

                                    // Increment button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CaramelWarm,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.increaseCartItem(cartItem) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Increase",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Complimentary Toggle button
                                    IconButton(
                                        onClick = { viewModel.toggleCartItemComplimentary(cartItem, !cartItem.isFree, "Owner Treat") },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CardGiftcard,
                                            contentDescription = "Toggle Complimentary",
                                            tint = if (cartItem.isFree) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = { viewModel.removeCartItem(cartItem) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Item",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // QUICK "+ ADD MORE DISHES" BUTTON
                item {
                    OutlinedButton(
                        onClick = onBackToMenu,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, CaramelWarm),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CaramelWarm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ Add More Dishes from Menu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                // COOKING / KITCHEN NOTE CARD
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.SoupKitchen,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Kitchen / Cooking Instructions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = EspressoDark
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = cookingNoteInput,
                                onValueChange = { cookingNoteInput = it },
                                placeholder = { Text("e.g. Less spicy, no onion, extra cheese crisp...", fontSize = 13.sp) },
                                singleLine = false,
                                maxLines = 2,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ORDER SUMMARY CARD
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Order Summary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EspressoDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Items", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${cartState.items.sumOf { it.quantity }} items", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Amount", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                                Text(
                                    "₹${String.format(Locale.US, "%.2f", cartState.totalAmount)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = EspressoDark
                                )
                            }
                        }
                    }
                }
            }

            // FIXED BOTTOM ACTION BAR: SEND TO KITCHEN & CASH OUT
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Send to Kitchen Button
                    Button(
                        onClick = onSendToKitchen,
                        enabled = cartState.items.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE65100),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("send_to_kitchen_button")
                    ) {
                        Icon(Icons.Default.SoupKitchen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send to Kitchen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // Cash Out Button
                    Button(
                        onClick = onOpenCashOut,
                        enabled = cartState.items.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B5E20),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("cash_out_button")
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cash Out", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
