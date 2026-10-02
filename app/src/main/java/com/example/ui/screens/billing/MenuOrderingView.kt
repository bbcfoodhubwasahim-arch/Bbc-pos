package com.example.ui.screens.billing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.BillItem
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ComboEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.parseVariants
import com.example.data.local.entity.parseAddons
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuOrderingView(
    categories: List<CategoryEntity>,
    menuItems: List<MenuItemEntity>,
    viewModel: MainViewModel,
    onBackToTables: () -> Unit,
    onSendToKitchen: () -> Unit,
    onOpenCashOut: () -> Unit
) {
    val cartState by viewModel.cartState.collectAsState()
    val activeCombos by viewModel.activeCombos.collectAsState()
    val allAddonDefinitions by viewModel.allAddonDefinitions.collectAsState()
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isCombosTabSelected by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isConfirmOrderScreenOpen by remember { mutableStateOf(false) }
    var isCartExpanded by remember { mutableStateOf(false) }
    var selectedComboForCustomization by remember { mutableStateOf<ComboEntity?>(null) }
    var dishToCustomize by remember { mutableStateOf<MenuItemEntity?>(null) }
    var editingCartItem by remember { mutableStateOf<BillItem?>(null) }

    val filteredItems = remember(menuItems, selectedCategoryId, searchQuery) {
        menuItems.filter { item ->
            val matchCategory = selectedCategoryId == null || item.categoryId == selectedCategoryId
            val matchSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    val filteredCombos = remember(activeCombos, searchQuery) {
        activeCombos.filter { combo ->
            searchQuery.isBlank() ||
                combo.name.contains(searchQuery, ignoreCase = true) ||
                combo.description.contains(searchQuery, ignoreCase = true) ||
                combo.badge.contains(searchQuery, ignoreCase = true)
        }
    }

    // Map dish ID to current quantity in cart for quick badge display
    val cartQtyMap = remember(cartState.items) {
        cartState.items.associate { it.dishId to it.quantity }
    }

    if (isConfirmOrderScreenOpen) {
        OrderConfirmationView(
            cartState = cartState,
            menuItems = menuItems,
            allAddonDefinitions = allAddonDefinitions,
            viewModel = viewModel,
            onBackToMenu = { isConfirmOrderScreenOpen = false },
            onSendToKitchen = {
                isConfirmOrderScreenOpen = false
                onSendToKitchen()
            },
            onOpenCashOut = {
                isConfirmOrderScreenOpen = false
                onOpenCashOut()
            },
            onEditItem = { dish, cartItem ->
                editingCartItem = cartItem
                dishToCustomize = dish
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Order Context & Compact Search Toggle
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearchExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSearchExpanded = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search", tint = EspressoDark)
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search coffee, croissant, snacks...", fontSize = 14.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBackToTables) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Tables", tint = EspressoDark)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (cartState.orderType == "DINE_IN") {
                                            cartState.selectedTable?.name ?: "Dine In"
                                        } else {
                                            "Takeaway Order"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = EspressoDark
                                    )
                                    if (cartState.activeBillId != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = TableOccupiedRed.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "RUNNING ORDER",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TableOccupiedRed,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Compact Search Button (frees massive space on main screen)
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search Menu", tint = EspressoDark)
                            }

                            // Quick Cart Pill
                            if (cartState.items.isNotEmpty()) {
                                Surface(
                                    color = CaramelWarm.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.clickable { isConfirmOrderScreenOpen = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = CaramelWarm, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${cartState.items.sumOf { it.quantity }} • ₹${String.format(Locale.US, "%.0f", cartState.totalAmount)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CaramelWarm
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category Horizontal Scroll (Large chips with signature Gold accent, NO permanent large search bar)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = !isCombosTabSelected && selectedCategoryId == null,
                        onClick = {
                            isCombosTabSelected = false
                            selectedCategoryId = null
                        },
                        label = {
                            Text(
                                "All Items",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccent,
                            selectedLabelColor = GoldTextDark
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = isCombosTabSelected,
                        onClick = {
                            isCombosTabSelected = true
                            selectedCategoryId = null
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Combos (${activeCombos.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccent,
                            selectedLabelColor = GoldTextDark,
                            selectedLeadingIconColor = GoldTextDark
                        )
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = !isCombosTabSelected && selectedCategoryId == cat.id,
                        onClick = {
                            isCombosTabSelected = false
                            selectedCategoryId = cat.id
                        },
                        label = {
                            Text(
                                cat.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccent,
                            selectedLabelColor = GoldTextDark
                        )
                    )
                }
            }

        if (isCombosTabSelected) {
            // Combos Grid
            if (filteredCombos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Celebration,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No active combos available",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = EspressoDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Create and activate combos in Menu Management",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 135.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                ) {
                    items(filteredCombos, key = { it.id }) { combo ->
                        ComboBillingCard(
                            combo = combo,
                            onTap = { selectedComboForCustomization = combo }
                        )
                    }
                }
            }
        } else {
            // Menu Items Grid (Super-Compact POS Grid: 1 tap = 1, 2 taps = 2, minimal scrolling)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 115.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val currentQty = cartQtyMap[item.id] ?: 0
                    val hasVariants = remember(item.variantsJson) { parseVariants(item.variantsJson).isNotEmpty() }
                    val hasDishAddons = remember(item.addonsJson) { parseAddons(item.addonsJson).isNotEmpty() }
                    val hasCategoryAddons = remember(allAddonDefinitions, item.categoryId) {
                        allAddonDefinitions.any { it.categoryId == item.categoryId }
                    }
                    val hasCustomOptions = hasVariants || hasDishAddons || hasCategoryAddons

                    MenuItemCard(
                        item = item,
                        currentQty = currentQty,
                        hasCustomOptions = hasCustomOptions,
                        onTapIncrement = {
                            viewModel.addItemToCart(item)
                        },
                        onCustomizeClick = {
                            dishToCustomize = item
                        },
                        onDecrement = {
                            viewModel.decreaseCartItem(item.id)
                        }
                    )
                }
            }
        }

        // Bottom Floating Confirm Order Bar with Live Cart Strip
        if (cartState.items.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = BorderStroke(1.dp, CaramelWarm.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // LIVE CART ITEMS PREVIEW STRIP (Always visible on menu screen)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CaramelWarm.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = CaramelWarm,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(cartState.items) { billItem ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, CaramelWarm.copy(alpha = 0.35f)),
                                        shadowElevation = 0.5.dp
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = billItem.dishName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = EspressoDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 100.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = CaramelWarm,
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = "x${billItem.quantity}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Toggle expand mini-cart list
                        IconButton(
                            onClick = { isCartExpanded = !isCartExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isCartExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isCartExpanded) "Collapse Cart" else "Expand Cart",
                                tint = CaramelWarm,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Optional Mini-Cart Expanded List (directly on menu screen without leaving)
                    AnimatedVisibility(visible = isCartExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cartState.items) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.dishName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = EspressoDark)
                                            if (!item.selectedVariant.isNullOrBlank()) {
                                                Text(item.selectedVariant, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.decreaseCartItem(item.dishId) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(Icons.Default.RemoveCircle, contentDescription = "Minus", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                            }
                                            Text("${item.quantity}", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                            IconButton(
                                                onClick = { viewModel.increaseCartItem(item.dishId) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(Icons.Default.AddCircle, contentDescription = "Plus", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "₹${String.format(Locale.US, "%.0f", item.totalPrice)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = GoldAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Action Row: Total & Confirm Order Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${cartState.items.sumOf { it.quantity }} items in cart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", cartState.totalAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = EspressoDark
                            )
                        }

                        Button(
                            onClick = { isConfirmOrderScreenOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("confirm_order_button")
                        ) {
                            Text("Confirm Order", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (selectedComboForCustomization != null) {
        ComboSelectionDialog(
            combo = selectedComboForCustomization!!,
            menuItems = menuItems,
            onDismiss = { selectedComboForCustomization = null },
            onConfirm = { selectedNames, selectedDishMap ->
                viewModel.addComboToCart(selectedComboForCustomization!!, selectedNames, selectedDishMap)
                selectedComboForCustomization = null
            }
        )
    }

    val customDish = dishToCustomize
    if (customDish != null) {
        val categoryAddons = remember(allAddonDefinitions, customDish.categoryId) {
            allAddonDefinitions.filter { it.categoryId == customDish.categoryId }
        }
        val currentEditItem = editingCartItem
        ItemCustomizationDialog(
            dish = customDish,
            categoryAddons = categoryAddons,
            initialVariantName = currentEditItem?.selectedVariant,
            initialSelectedAddons = currentEditItem?.getSelectedAddonsList() ?: emptyList(),
            initialNotes = currentEditItem?.cookingNotes ?: "",
            initialQuantity = currentEditItem?.quantity ?: 1,
            initialIsComplimentary = currentEditItem?.isFree ?: false,
            initialComplimentaryReason = currentEditItem?.notes?.removePrefix("Complimentary: ") ?: "",
            isEditMode = currentEditItem != null,
            onDismiss = {
                dishToCustomize = null
                editingCartItem = null
            },
            onConfirm = { selectedVariant, selectedAddons, cookingNotes, itemQty, isComplimentary, complimentaryReason ->
                if (currentEditItem != null) {
                    viewModel.updateCartItem(
                        oldItem = currentEditItem,
                        dish = customDish,
                        selectedVariant = selectedVariant,
                        selectedAddons = selectedAddons,
                        cookingNotes = cookingNotes,
                        quantity = itemQty,
                        isComplimentary = isComplimentary,
                        complimentaryReason = complimentaryReason
                    )
                } else {
                    viewModel.addItemToCartWithCustomization(
                        dish = customDish,
                        selectedVariant = selectedVariant,
                        selectedAddons = selectedAddons,
                        cookingNotes = cookingNotes,
                        quantity = itemQty,
                        isComplimentary = isComplimentary,
                        complimentaryReason = complimentaryReason
                    )
                }
                dishToCustomize = null
                editingCartItem = null
            }
        )
    }
}

@Composable
fun ComboBillingCard(
    combo: ComboEntity,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.2.dp,
                color = CaramelWarm.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onTap() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (combo.badge.isNotBlank()) CaramelWarm else CreamSurfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (combo.badge.isNotBlank()) combo.badge else "COMBO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (combo.badge.isNotBlank()) Color.White else CaramelWarm,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }

                Text(
                    text = "${combo.slots.size} slots",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = combo.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = EspressoDark
            )

            if (combo.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = combo.description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹${String.format(Locale.US, "%.0f", combo.price)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GoldAccent
                )

                Surface(
                    color = GoldAccent,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = GoldTextDark, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Pick", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldTextDark)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuItemCard(
    item: MenuItemEntity,
    currentQty: Int,
    hasCustomOptions: Boolean,
    onTapIncrement: () -> Unit,
    onCustomizeClick: () -> Unit,
    onDecrement: () -> Unit
) {
    val isSelected = currentQty > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) GoldAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = { onTapIncrement() },
                onLongClick = { onCustomizeClick() }
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GoldAccent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Category tag and Quick Quantity Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFFEF3C7), // Warm amber background
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.categoryName.ifBlank { "General" },
                            fontSize = 9.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 70.dp)
                        )
                        if (hasCustomOptions) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Add-ons Available",
                                tint = Color(0xFFC2410C),
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }

                if (isSelected) {
                    Surface(
                        color = GoldAccent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "x$currentQty",
                            color = GoldTextDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Dish Image (if available, compact height 44.dp)
            if (!item.imageUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = item.imageUri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Dish Name (Compact & High Contrast)
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = EspressoDark,
                lineHeight = 15.sp,
                modifier = Modifier.heightIn(min = 28.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Price & Quick Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹${String.format(Locale.US, "%.0f", item.price)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.5.sp,
                    color = GoldAccent
                )

                if (isSelected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onDecrement() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "Minus",
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onTapIncrement() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = "Add",
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = GoldAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onTapIncrement() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = EspressoDark, modifier = Modifier.size(13.dp))
                            Text("ADD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                        }
                    }
                }
            }
        }
    }
}
