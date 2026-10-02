package com.example.ui.screens.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.entity.*
import com.example.ui.theme.*
import com.example.util.FoodCostCalculator
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DishFormDialog(
    dish: MenuItemEntity?,
    categories: List<CategoryEntity>,
    allInventory: List<InventoryItemEntity>,
    existingRecipe: RecipeEntity?,
    preselectedCategoryId: String? = null,
    isCategoryLocked: Boolean = false,
    allDishes: List<MenuItemEntity> = emptyList(),
    allRecipes: Map<String, RecipeEntity> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (MenuItemEntity, RecipeEntity?) -> Unit
) {
    var name by remember { mutableStateOf(dish?.name ?: "") }
    var priceStr by remember { mutableStateOf(dish?.price?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var selectedCatId by remember {
        mutableStateOf(preselectedCategoryId ?: dish?.categoryId ?: categories.firstOrNull()?.id ?: "")
    }
    var imageUriStr by remember { mutableStateOf(dish?.imageUri) }
    var isDiscountEligible by remember { mutableStateOf(dish?.isDiscountEligible ?: true) }

    // Variants (Sizes / Portions)
    var variantsList by remember { mutableStateOf(parseVariants(dish?.variantsJson)) }
    var newVariantName by remember { mutableStateOf("") }
    var newVariantPriceStr by remember { mutableStateOf("") }

    // Addons (Extras / Customizations)
    var addonsList by remember { mutableStateOf(parseAddons(dish?.addonsJson)) }
    var newAddonName by remember { mutableStateOf("") }
    var newAddonPriceStr by remember { mutableStateOf("") }
    var newAddonInvId by remember { mutableStateOf<String?>(null) }
    var newAddonInvQtyStr by remember { mutableStateOf("1.0") }
    var newAddonUsageUnit by remember { mutableStateOf("Nos") }

    // Ingredients for recipe
    val inventoryMap = remember(allInventory) { allInventory.associateBy { it.id } }
    var ingredients by remember(existingRecipe) {
        mutableStateOf(existingRecipe?.ingredients ?: emptyList())
    }

    // Dialog State Flags
    var showBulkSelectDialog by remember { mutableStateOf(false) }
    var showCopyRecipeDialog by remember { mutableStateOf(false) }

    // 1-by-1 quick add ingredient inputs
    var selectedInvId by remember { mutableStateOf(allInventory.firstOrNull()?.id ?: "") }
    var ingQtyStr by remember { mutableStateOf("") }
    val pickedInvItem = inventoryMap[selectedInvId]
    val baseUnit = pickedInvItem?.unit ?: "Nos"
    val availableUsageUnits = remember(baseUnit) {
        FoodCostCalculator.getAvailableUsageUnits(baseUnit)
    }
    var selectedUsageUnit by remember(baseUnit) {
        mutableStateOf(FoodCostCalculator.getDefaultUsageUnit(baseUnit))
    }
    var recipeApplyMode by remember { mutableStateOf("BOTH") } // "BOTH", "DINE_IN_ONLY", "TAKEAWAY_ONLY"

    // Live Line Cost of currently typed ingredient
    val typedQty = ingQtyStr.toDoubleOrNull() ?: 0.0
    val liveLineCost = remember(typedQty, selectedUsageUnit, pickedInvItem) {
        FoodCostCalculator.calculateLineCost(typedQty, selectedUsageUnit, pickedInvItem)
    }

    // Dish live food cost and gross margin preview
    val currentPrice = priceStr.toDoubleOrNull() ?: 0.0
    val tempRecipe = RecipeEntity(
        menuItemId = dish?.id ?: "temp",
        restaurantId = selectedCatId,
        menuItemName = name,
        ingredients = ingredients
    )
    val costSummary = remember(ingredients, inventoryMap) {
        FoodCostCalculator.calculateRecipeCost(tempRecipe, inventoryMap)
    }
    val dineInMargin = remember(costSummary, currentPrice) {
        costSummary.marginPercent(currentPrice, isTakeaway = false)
    }
    val takeawayMargin = remember(costSummary, currentPrice) {
        costSummary.marginPercent(currentPrice, isTakeaway = true)
    }

    // Suggested Selling Price for 65% target margin
    val suggestedPrice = remember(costSummary.dineInCost) {
        if (costSummary.dineInCost > 0.0) {
            val raw = costSummary.dineInCost / (1.0 - 0.65)
            kotlin.math.ceil(raw / 10.0) * 10.0 - 1.0 // e.g. 149, 199, 249
        } else 0.0
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                imageUriStr = uri.toString()
            }
        }
    )

    val saveAction = {
        val price = priceStr.toDoubleOrNull() ?: 0.0
        if (name.isNotBlank() && price > 0.0) {
            val cat = categories.find { it.id == selectedCatId }
            val dishId = dish?.id ?: "dish_${UUID.randomUUID()}"
            val newItem = MenuItemEntity(
                id = dishId,
                categoryId = selectedCatId,
                categoryName = cat?.name ?: "General",
                name = name.trim(),
                price = price,
                isDiscountEligible = isDiscountEligible,
                imageUri = imageUriStr,
                variantsJson = formatVariants(variantsList),
                addonsJson = formatAddons(addonsList)
            )
            val recipe = if (ingredients.isNotEmpty()) {
                RecipeEntity(
                    menuItemId = dishId,
                    restaurantId = selectedCatId,
                    menuItemName = name.trim(),
                    ingredients = ingredients
                )
            } else null

            onSave(newItem, recipe)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = EspressoDark)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (dish != null) "Edit Dish & Recipe" else "Add New Dish & Recipe",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = EspressoDark
                                    )
                                    Text(
                                        text = "Price, recipe ingredients & stock deduction",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (costSummary.dineInCost > 0.0 && currentPrice > 0.0) {
                                    Surface(
                                        color = if (dineInMargin >= 50.0) SuccessGreen.copy(alpha = 0.15f) else CaramelWarm.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${String.format(Locale.US, "%.0f", dineInMargin)}% Margin",
                                            color = if (dineInMargin >= 50.0) SuccessGreen else CaramelWarm,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Button(
                                    onClick = { saveAction() },
                                    enabled = name.isNotBlank() && (priceStr.toDoubleOrNull() ?: 0.0) > 0.0,
                                    colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold, color = EspressoDark)
                            }
                            Button(
                                onClick = { saveAction() },
                                enabled = name.isNotBlank() && (priceStr.toDoubleOrNull() ?: 0.0) > 0.0,
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Dish & Recipe", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // --- SECTION 1: BASIC DETAILS CARD ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Single Clean Photo Picker
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CreamSurfaceVariant)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                            .clickable {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!imageUriStr.isNullOrBlank()) {
                                            AsyncImage(
                                                model = imageUriStr,
                                                contentDescription = "Dish photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = CaramelWarm, modifier = Modifier.size(24.dp))
                                                Spacer(Modifier.height(2.dp))
                                                Text("Photo", fontSize = 10.sp, color = CaramelWarm, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (!imageUriStr.isNullOrBlank()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        photoPickerLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Change", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                TextButton(
                                                    onClick = { imageUriStr = null },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            Text("Dish Photo (Optional)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                                            Text("Tap photo box to select image from gallery", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Dish Name *", fontWeight = FontWeight.Bold, color = EspressoDark) },
                                    placeholder = { Text("e.g. Paneer Tikka Sandwich") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = priceStr,
                                        onValueChange = { priceStr = it },
                                        label = { Text("Selling Price (₹) *", fontWeight = FontWeight.Bold, color = EspressoDark) },
                                        placeholder = { Text("149") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.1f),
                                        singleLine = true
                                    )

                                    // Live Food Cost & Margin Summary Box
                                    Card(
                                        modifier = Modifier.weight(1.3f),
                                        colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Dine-In Cost:", fontSize = 10.sp, color = EspressoDark, fontWeight = FontWeight.Medium)
                                                Text("₹${String.format(Locale.US, "%.2f", costSummary.dineInCost)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Takeaway Cost:", fontSize = 10.sp, color = EspressoDark, fontWeight = FontWeight.Medium)
                                                Text("₹${String.format(Locale.US, "%.2f", costSummary.takeawayTotalCost)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                                            }
                                            if (currentPrice > 0.0 && costSummary.dineInCost > 0.0) {
                                                val grossProfit = currentPrice - costSummary.dineInCost
                                                Text(
                                                    text = "Profit: ₹${String.format(Locale.US, "%.0f", grossProfit)} (${String.format(Locale.US, "%.0f", dineInMargin)}% margin)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (dineInMargin >= 50.0) SuccessGreen else CaramelWarm
                                                )
                                            }
                                        }
                                    }
                                }

                                if (suggestedPrice > 0.0 && currentPrice == 0.0) {
                                    Text(
                                        text = "💡 Suggested Price for ~65% Margin: ₹${suggestedPrice.toInt()}",
                                        fontSize = 11.sp,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Category Selection Chips
                                Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                                if (isCategoryLocked) {
                                    val lockedCat = categories.find { it.id == selectedCatId }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Category Locked",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = lockedCat?.name ?: "Category Pre-Selected",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        categories.forEach { cat ->
                                            FilterChip(
                                                selected = selectedCatId == cat.id,
                                                onClick = { selectedCatId = cat.id },
                                                label = { Text(cat.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                            )
                                        }
                                    }
                                }

                                // Discount Eligible Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CreamSurfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Discount Eligible", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EspressoDark)
                                        Text(
                                            text = if (isDiscountEligible) "Included when bill discount is applied" else "Excluded from discounts",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isDiscountEligible,
                                        onCheckedChange = { isDiscountEligible = it }
                                    )
                                }
                            }
                        }
                    }

                    // --- SECTION 2: RECIPE & RAW MATERIALS (AUTO INVENTORY DEDUCTION) ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Recipe Header with Bulk Select & Copy Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Recipe Ingredients (${ingredients.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            "Auto-deducted from stock on each order",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (allRecipes.isNotEmpty() && allDishes.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { showCopyRecipeDialog = true },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Button(
                                            onClick = { showBulkSelectDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("+ Bulk Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (allInventory.isEmpty()) {
                                    Surface(
                                        color = CreamSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "No inventory raw materials found. Add ingredients in Inventory tab first.",
                                            fontSize = 11.sp,
                                            color = EspressoDark,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    // 1-by-1 Quick Add Box with Proper Spacing & Visibility
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Quick Add Single Ingredient:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EspressoDark)

                                            // Raw Material Dropdown
                                            var invExpanded by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                OutlinedCard(
                                                    onClick = { invExpanded = true },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = pickedInvItem?.name ?: "Select raw material",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = EspressoDark
                                                            )
                                                            if (pickedInvItem != null) {
                                                                Text(
                                                                    text = "Rate: ₹${pickedInvItem.purchasePrice}/${pickedInvItem.unit} • Stock: ${pickedInvItem.currentStock} ${pickedInvItem.unit}",
                                                                    fontSize = 11.sp,
                                                                    color = EspressoDark,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                        }
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = EspressoDark)
                                                    }
                                                }

                                                DropdownMenu(
                                                    expanded = invExpanded,
                                                    onDismissRequest = { invExpanded = false },
                                                    modifier = Modifier.heightIn(max = 240.dp)
                                                ) {
                                                    allInventory.forEach { item ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Column {
                                                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EspressoDark)
                                                                    Text("Rate: ₹${item.purchasePrice}/${item.unit} • Stock: ${item.currentStock} ${item.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                }
                                                            },
                                                            onClick = {
                                                                selectedInvId = item.id
                                                                selectedUsageUnit = FoodCostCalculator.getDefaultUsageUnit(item.unit)
                                                                invExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Quantity & Unit Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = ingQtyStr,
                                                    onValueChange = { ingQtyStr = it },
                                                    label = { Text("Quantity", fontSize = 11.sp) },
                                                    placeholder = { Text(if (selectedUsageUnit == "gm") "50" else "1") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true
                                                )

                                                // Usage Units Chips
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    availableUsageUnits.forEach { unit ->
                                                        FilterChip(
                                                            selected = selectedUsageUnit.equals(unit, ignoreCase = true),
                                                            onClick = { selectedUsageUnit = unit },
                                                            label = { Text(unit, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                                        )
                                                    }
                                                }
                                            }

                                            // Apply Mode (Both, Dine-In Only, Takeaway Only) & Add Button
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    FilterChip(
                                                        selected = recipeApplyMode == "BOTH",
                                                        onClick = { recipeApplyMode = "BOTH" },
                                                        label = { Text("🟢 Both", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                                    )
                                                    FilterChip(
                                                        selected = recipeApplyMode == "DINE_IN_ONLY",
                                                        onClick = { recipeApplyMode = "DINE_IN_ONLY" },
                                                        label = { Text("🍽️ Dine-In", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                                    )
                                                    FilterChip(
                                                        selected = recipeApplyMode == "TAKEAWAY_ONLY",
                                                        onClick = { recipeApplyMode = "TAKEAWAY_ONLY" },
                                                        label = { Text("📦 Parcel", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        val q = ingQtyStr.toDoubleOrNull()
                                                        if (q != null && q > 0.0 && pickedInvItem != null) {
                                                            val newIng = RecipeIngredient(
                                                                inventoryItemId = pickedInvItem.id,
                                                                inventoryItemName = pickedInvItem.name,
                                                                quantity = q,
                                                                usageUnit = selectedUsageUnit,
                                                                isTakeawayExtra = (recipeApplyMode == "TAKEAWAY_ONLY"),
                                                                applyMode = recipeApplyMode
                                                            )
                                                            // If already exists, update it, else append
                                                            val existingIdx = ingredients.indexOfFirst { it.inventoryItemId == pickedInvItem.id && it.applyMode == recipeApplyMode }
                                                            ingredients = if (existingIdx >= 0) {
                                                                ingredients.mapIndexed { i, old -> if (i == existingIdx) newIng else old }
                                                            } else {
                                                                ingredients + newIng
                                                            }
                                                            ingQtyStr = ""
                                                        }
                                                    },
                                                    enabled = typedQty > 0.0 && pickedInvItem != null,
                                                    colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            if (liveLineCost > 0.0) {
                                                Text(
                                                    text = "= ₹${String.format(Locale.US, "%.2f", liveLineCost)} raw cost for $typedQty $selectedUsageUnit",
                                                    fontSize = 11.sp,
                                                    color = SuccessGreen,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // --- THREE CLEAN SECTIONS FOR ADDED INGREDIENTS ---
                                val commonItems = ingredients.filter { it.applyMode == "BOTH" || (!it.isTakeawayExtra && it.applyMode.isBlank()) }
                                val dineInItems = ingredients.filter { it.applyMode == "DINE_IN_ONLY" }
                                val takeawayItems = ingredients.filter { it.applyMode == "TAKEAWAY_ONLY" || (it.isTakeawayExtra && it.applyMode != "DINE_IN_ONLY") }

                                // 1. Common Ingredients Section
                                if (commonItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("🟢 Common (Dine-In & Takeaway):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                                    commonItems.forEach { ing ->
                                        DishIngredientRowCard(
                                            ingredient = ing,
                                            inventoryMap = inventoryMap,
                                            onUpdate = { updated ->
                                                ingredients = ingredients.map { if (it.inventoryItemId == updated.inventoryItemId) updated else it }
                                            },
                                            onDelete = {
                                                ingredients = ingredients.filter { it.inventoryItemId != ing.inventoryItemId }
                                            }
                                        )
                                    }
                                }

                                // 2. Dine-In Only Ingredients Section
                                if (dineInItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("🍽️ Dine-In Only (Table Service / Plating):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    dineInItems.forEach { ing ->
                                        DishIngredientRowCard(
                                            ingredient = ing,
                                            inventoryMap = inventoryMap,
                                            onUpdate = { updated ->
                                                ingredients = ingredients.map { if (it.inventoryItemId == updated.inventoryItemId) updated else it }
                                            },
                                            onDelete = {
                                                ingredients = ingredients.filter { it.inventoryItemId != ing.inventoryItemId }
                                            }
                                        )
                                    }
                                }

                                // 3. Takeaway Only / Packaging Section
                                if (takeawayItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("📦 Takeaway Only (Parcel Boxes, Sachets, Packaging):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CaramelWarm)
                                    takeawayItems.forEach { ing ->
                                        DishIngredientRowCard(
                                            ingredient = ing,
                                            inventoryMap = inventoryMap,
                                            onUpdate = { updated ->
                                                ingredients = ingredients.map { if (it.inventoryItemId == updated.inventoryItemId) updated else it }
                                            },
                                            onDelete = {
                                                ingredients = ingredients.filter { it.inventoryItemId != ing.inventoryItemId }
                                            }
                                        )
                                    }
                                }

                                if (ingredients.isEmpty()) {
                                    Surface(
                                        color = CreamSurfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "No ingredients added yet. Use '+ Bulk Select' to pick multiple items at once!",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- SECTION 3: VARIANTS / SIZES (OPTIONAL) ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Dish Variants / Sizes (e.g., Small, Medium, Large)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newVariantName,
                                        onValueChange = { newVariantName = it },
                                        placeholder = { Text("Size (e.g. Medium)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1.3f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newVariantPriceStr,
                                        onValueChange = { newVariantPriceStr = it },
                                        placeholder = { Text("Price ₹", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = {
                                            val vPrice = newVariantPriceStr.toDoubleOrNull() ?: 0.0
                                            if (newVariantName.isNotBlank() && vPrice > 0.0) {
                                                variantsList = variantsList + MenuItemVariant(newVariantName.trim(), vPrice)
                                                newVariantName = ""
                                                newVariantPriceStr = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CaramelWarm)
                                            .size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Variant", tint = Color.White)
                                    }
                                }

                                if (variantsList.isNotEmpty()) {
                                    variantsList.forEachIndexed { idx, v ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("• ${v.name}: ₹${v.price}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            IconButton(
                                                onClick = { variantsList = variantsList.filterIndexed { i, _ -> i != idx } },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove Variant", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- SECTION 4: ADD-ONS / EXTRAS (OPTIONAL) ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Add-ons / Extras (e.g., Extra Cheese, Ice Cream Scoop)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newAddonName,
                                        onValueChange = { newAddonName = it },
                                        placeholder = { Text("Addon Name (e.g. Extra Cheese)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1.3f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = newAddonPriceStr,
                                        onValueChange = { newAddonPriceStr = it },
                                        placeholder = { Text("Price ₹", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                // Optional Raw Material Linking for Inventory Stock Deduction
                                if (allInventory.isNotEmpty()) {
                                    var addonInvExpanded by remember { mutableStateOf(false) }
                                    val linkedInvItem = inventoryMap[newAddonInvId]

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ExposedDropdownMenuBox(
                                            expanded = addonInvExpanded,
                                            onExpandedChange = { addonInvExpanded = it },
                                            modifier = Modifier.weight(1.2f)
                                        ) {
                                            OutlinedTextField(
                                                value = linkedInvItem?.name ?: "Link Raw Material (Optional)",
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = addonInvExpanded) },
                                                modifier = Modifier.menuAnchor(),
                                                singleLine = true
                                            )
                                            ExposedDropdownMenu(
                                                expanded = addonInvExpanded,
                                                onDismissRequest = { addonInvExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("None (No Stock Deduction)", fontSize = 12.sp) },
                                                    onClick = {
                                                        newAddonInvId = null
                                                        addonInvExpanded = false
                                                    }
                                                )
                                                allInventory.forEach { inv ->
                                                    DropdownMenuItem(
                                                        text = { Text("${inv.name} (${inv.currentStock} ${inv.unit})", fontSize = 12.sp) },
                                                        onClick = {
                                                            newAddonInvId = inv.id
                                                            newAddonUsageUnit = FoodCostCalculator.getDefaultUsageUnit(inv.unit)
                                                            addonInvExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        if (linkedInvItem != null) {
                                            OutlinedTextField(
                                                value = newAddonInvQtyStr,
                                                onValueChange = { newAddonInvQtyStr = it },
                                                placeholder = { Text("Qty") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.width(70.dp),
                                                singleLine = true
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val aPrice = newAddonPriceStr.toDoubleOrNull() ?: 0.0
                                                if (newAddonName.isNotBlank() && aPrice >= 0.0) {
                                                    val invItem = inventoryMap[newAddonInvId]
                                                    addonsList = addonsList + MenuItemAddon(
                                                        name = newAddonName.trim(),
                                                        price = aPrice,
                                                        inventoryItemId = invItem?.id,
                                                        inventoryItemName = invItem?.name,
                                                        inventoryQty = newAddonInvQtyStr.toDoubleOrNull() ?: 1.0,
                                                        usageUnit = newAddonUsageUnit
                                                    )
                                                    newAddonName = ""
                                                    newAddonPriceStr = ""
                                                    newAddonInvId = null
                                                    newAddonInvQtyStr = "1.0"
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Addon", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                val aPrice = newAddonPriceStr.toDoubleOrNull() ?: 0.0
                                                if (newAddonName.isNotBlank() && aPrice >= 0.0) {
                                                    addonsList = addonsList + MenuItemAddon(name = newAddonName.trim(), price = aPrice)
                                                    newAddonName = ""
                                                    newAddonPriceStr = ""
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Addon", tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add Addon")
                                        }
                                    }
                                }

                                if (addonsList.isNotEmpty()) {
                                    addonsList.forEachIndexed { idx, a ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("• ${a.name}: +₹${a.price}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                if (!a.inventoryItemName.isNullOrBlank()) {
                                                    Text(
                                                        text = "Deducts: ${a.inventoryItemName} (${a.inventoryQty} ${a.usageUnit})",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { addonsList = addonsList.filterIndexed { i, _ -> i != idx } },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove Addon", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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

    // --- DIALOG 1: BULK INGREDIENT PICKER WITH 3 TABS (Both, Dine-In Only, Takeaway Only) ---
    if (showBulkSelectDialog) {
        DishBulkIngredientPickerDialog(
            allInventory = allInventory,
            existingIngredients = ingredients,
            onDismiss = { showBulkSelectDialog = false },
            onAddSelected = { newlySelectedIngredients ->
                // Merge without duplicates (replace if existing)
                var merged = ingredients
                for (newIng in newlySelectedIngredients) {
                    merged = merged.filter { !(it.inventoryItemId == newIng.inventoryItemId && it.applyMode == newIng.applyMode) } + newIng
                }
                ingredients = merged
                showBulkSelectDialog = false
            }
        )
    }

    // --- DIALOG 2: COPY RECIPE FROM EXISTING DISH ---
    if (showCopyRecipeDialog) {
        DishCopyRecipeDialog(
            allDishes = allDishes.filter { it.id != dish?.id },
            allRecipes = allRecipes,
            onDismiss = { showCopyRecipeDialog = false },
            onRecipeSelected = { copiedRecipe ->
                ingredients = copiedRecipe.ingredients
                showCopyRecipeDialog = false
            }
        )
    }
}

/**
 * Modern card representing an ingredient inside the dish recipe editor.
 * Allows instant live typing of quantity, unit change, and apply-mode toggle.
 */
@Composable
private fun DishIngredientRowCard(
    ingredient: RecipeIngredient,
    inventoryMap: Map<String, InventoryItemEntity>,
    onUpdate: (RecipeIngredient) -> Unit,
    onDelete: () -> Unit
) {
    val invItem = inventoryMap[ingredient.inventoryItemId]
    val currentRate = invItem?.purchasePrice ?: 0.0
    val lineCost = FoodCostCalculator.calculateIngredientCost(ingredient, inventoryMap)

    var qtyText by remember(ingredient.quantity) {
        mutableStateOf(if (ingredient.quantity % 1.0 == 0.0) ingredient.quantity.toInt().toString() else ingredient.quantity.toString())
    }

    val availableUnits = remember(invItem?.unit) {
        invItem?.unit?.let { FoodCostCalculator.getAvailableUsageUnits(it) } ?: listOf("Nos")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ingredient.inventoryItemName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EspressoDark
                    )
                    Text(
                        text = "Rate: ₹${"%.2f".format(currentRate)}/${invItem?.unit ?: "Nos"} • Stock: ${invItem?.currentStock ?: 0.0} ${invItem?.unit ?: ""}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "₹${"%.2f".format(lineCost)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CaramelWarm
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remove Ingredient",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direct Qty TextField
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { text ->
                        qtyText = text
                        val d = text.toDoubleOrNull()
                        if (d != null && d >= 0.0) {
                            onUpdate(ingredient.copy(quantity = d))
                        }
                    },
                    label = { Text("Qty", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(85.dp),
                    singleLine = true
                )

                // Usage Units
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableUnits.forEach { unit ->
                        val isSelected = ingredient.usageUnit.equals(unit, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onUpdate(ingredient.copy(usageUnit = unit))
                            },
                            label = { Text(unit, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Quick Mode Switcher Chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (ingredient.applyMode) {
                        "DINE_IN_ONLY" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        "TAKEAWAY_ONLY" -> CaramelWarm.copy(alpha = 0.15f)
                        else -> SuccessGreen.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.clickable {
                        val nextMode = when (ingredient.applyMode) {
                            "BOTH" -> "DINE_IN_ONLY"
                            "DINE_IN_ONLY" -> "TAKEAWAY_ONLY"
                            else -> "BOTH"
                        }
                        onUpdate(
                            ingredient.copy(
                                applyMode = nextMode,
                                isTakeawayExtra = (nextMode == "TAKEAWAY_ONLY")
                            )
                        )
                    }
                ) {
                    Text(
                        text = when (ingredient.applyMode) {
                            "DINE_IN_ONLY" -> "🍽️ Dine-In"
                            "TAKEAWAY_ONLY" -> "📦 Parcel"
                            else -> "🟢 Both"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (ingredient.applyMode) {
                            "DINE_IN_ONLY" -> MaterialTheme.colorScheme.primary
                            "TAKEAWAY_ONLY" -> CaramelWarm
                            else -> SuccessGreen
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 3-Tab Multi-Select Bulk Ingredient Picker Dialog
 * Tabs: 1) Both (Common), 2) Dine-In Only, 3) Takeaway Only
 */
@Composable
fun DishBulkIngredientPickerDialog(
    allInventory: List<InventoryItemEntity>,
    existingIngredients: List<RecipeIngredient>,
    onDismiss: () -> Unit,
    onAddSelected: (List<RecipeIngredient>) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Both, 1 = Dine-In, 2 = Takeaway
    var searchQuery by remember { mutableStateOf("") }

    // Maps inventory item id to selected mode ("BOTH", "DINE_IN_ONLY", "TAKEAWAY_ONLY")
    val selectedItemModes = remember {
        mutableStateMapOf<String, String>().apply {
            existingIngredients.forEach { this[it.inventoryItemId] = it.applyMode }
        }
    }

    val filteredInventory = remember(allInventory, searchQuery) {
        if (searchQuery.isBlank()) allInventory
        else allInventory.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val currentModeForTab = when (selectedTab) {
        1 -> "DINE_IN_ONLY"
        2 -> "TAKEAWAY_ONLY"
        else -> "BOTH"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text("Bulk Select Ingredients", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                        Text("Select raw materials & packaging items", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val result = selectedItemModes.mapNotNull { (invId, mode) ->
                                            val item = allInventory.find { it.id == invId } ?: return@mapNotNull null
                                            RecipeIngredient(
                                                inventoryItemId = item.id,
                                                inventoryItemName = item.name,
                                                quantity = when (FoodCostCalculator.getDefaultUsageUnit(item.unit)) {
                                                    "gm" -> 50.0
                                                    "ml" -> 50.0
                                                    else -> 1.0
                                                },
                                                usageUnit = FoodCostCalculator.getDefaultUsageUnit(item.unit),
                                                isTakeawayExtra = (mode == "TAKEAWAY_ONLY"),
                                                applyMode = mode
                                            )
                                        }
                                        onAddSelected(result)
                                    },
                                    enabled = selectedItemModes.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Add Selected (${selectedItemModes.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // 3 Clean Mode Tabs
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = CaramelWarm
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("🟢 Both (Common)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("🍽️ Dine-In Only", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    text = { Text("📦 Parcel (Takeaway)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val result = selectedItemModes.mapNotNull { (invId, mode) ->
                                        val item = allInventory.find { it.id == invId } ?: return@mapNotNull null
                                        RecipeIngredient(
                                            inventoryItemId = item.id,
                                            inventoryItemName = item.name,
                                            quantity = when (FoodCostCalculator.getDefaultUsageUnit(item.unit)) {
                                                "gm" -> 50.0
                                                "ml" -> 50.0
                                                else -> 1.0
                                            },
                                            usageUnit = FoodCostCalculator.getDefaultUsageUnit(item.unit),
                                            isTakeawayExtra = (mode == "TAKEAWAY_ONLY"),
                                            applyMode = mode
                                        )
                                    }
                                    onAddSelected(result)
                                },
                                enabled = selectedItemModes.isNotEmpty(),
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Add Selected (${selectedItemModes.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search raw materials (Cheese, Bread, Box, Sauce)...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    if (filteredInventory.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No inventory items found.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredInventory, key = { it.id }) { item ->
                                val currentAssignedMode = selectedItemModes[item.id]
                                val isSelected = currentAssignedMode != null
                                val isSelectedInThisTab = currentAssignedMode == currentModeForTab

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelectedInThisTab) {
                                                selectedItemModes.remove(item.id)
                                            } else {
                                                selectedItemModes[item.id] = currentModeForTab
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isSelectedInThisTab -> CaramelWarm.copy(alpha = 0.12f)
                                            isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelectedInThisTab) CaramelWarm else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelectedInThisTab,
                                            onCheckedChange = { checked ->
                                                if (checked) selectedItemModes[item.id] = currentModeForTab
                                                else selectedItemModes.remove(item.id)
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EspressoDark)
                                            Text(
                                                "Rate: ₹${item.purchasePrice}/${item.unit} • Stock: ${item.currentStock} ${item.unit}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected && !isSelectedInThisTab) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = when (currentAssignedMode) {
                                                        "DINE_IN_ONLY" -> "Dine-In"
                                                        "TAKEAWAY_ONLY" -> "Parcel"
                                                        else -> "Both"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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
            }
        }
    }
}

/**
 * Copy Recipe from Existing Dish Dialog
 */
@Composable
fun DishCopyRecipeDialog(
    allDishes: List<MenuItemEntity>,
    allRecipes: Map<String, RecipeEntity>,
    onDismiss: () -> Unit,
    onRecipeSelected: (RecipeEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val dishesWithRecipes = remember(allDishes, allRecipes) {
        allDishes.filter { dish ->
            val r = allRecipes[dish.id]
            r != null && r.ingredients.isNotEmpty()
        }
    }

    val filtered = remember(dishesWithRecipes, searchQuery) {
        if (searchQuery.isBlank()) dishesWithRecipes
        else dishesWithRecipes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CaramelWarm)
                Spacer(Modifier.width(8.dp))
                Text("Copy Recipe from Dish", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search dish name...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No dishes with recipes found.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered) { d ->
                            val r = allRecipes[d.id] ?: return@items
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRecipeSelected(r) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(d.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${r.ingredients.size} ingredients • ₹${d.price}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CaramelWarm)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
