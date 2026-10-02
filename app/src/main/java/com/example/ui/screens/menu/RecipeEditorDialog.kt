package com.example.ui.screens.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredient
import com.example.ui.theme.CaramelWarm
import com.example.ui.theme.EspressoDark
import com.example.util.FoodCostCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorDialog(
    menuItem: MenuItemEntity,
    currentRecipe: RecipeEntity?,
    allInventory: List<InventoryItemEntity>,
    onDismiss: () -> Unit,
    onSaveRecipe: (RecipeEntity) -> Unit
) {
    val inventoryMap = remember(allInventory) { allInventory.associateBy { it.id } }
    var ingredients by remember(currentRecipe) {
        mutableStateOf(currentRecipe?.ingredients ?: emptyList())
    }
    var recipeNotes by remember(currentRecipe) {
        mutableStateOf(currentRecipe?.notes ?: "")
    }

    // Add ingredient form states
    var selectedInventoryId by remember {
        mutableStateOf(allInventory.firstOrNull()?.id ?: "")
    }
    var quantityText by remember { mutableStateOf("") }
    val selectedItem = inventoryMap[selectedInventoryId]
    val baseUnit = selectedItem?.unit ?: "Nos"

    val availableUnits = remember(baseUnit) {
        FoodCostCalculator.getAvailableUsageUnits(baseUnit)
    }
    var selectedUsageUnit by remember(baseUnit) {
        mutableStateOf(FoodCostCalculator.getDefaultUsageUnit(baseUnit))
    }
    var recipeApplyMode by remember { mutableStateOf("BOTH") } // "BOTH", "DINE_IN_ONLY", "TAKEAWAY_ONLY"
    var invDropdownExpanded by remember { mutableStateOf(false) }

    // Dialog State Flags
    var showMultiSelectDialog by remember { mutableStateOf(false) }
    var showCopyRecipeDialog by remember { mutableStateOf(false) }
    var ingredientToDelete by remember { mutableStateOf<RecipeIngredient?>(null) }

    // Live Line Cost for the ingredient being added
    val currentQty = quantityText.toDoubleOrNull() ?: 0.0
    val liveLineCost = remember(currentQty, selectedUsageUnit, selectedItem) {
        FoodCostCalculator.calculateLineCost(currentQty, selectedUsageUnit, selectedItem)
    }

    // Dynamic cost & margin calculation of current recipe ingredients
    val previewRecipe = RecipeEntity(
        menuItemId = menuItem.id,
        restaurantId = menuItem.categoryId,
        menuItemName = menuItem.name,
        ingredients = ingredients
    )
    val costSummary = remember(ingredients, inventoryMap) {
        FoodCostCalculator.calculateRecipeCost(previewRecipe, inventoryMap)
    }
    val dineInMargin = remember(costSummary, menuItem.price) {
        costSummary.marginPercent(menuItem.price, isTakeaway = false)
    }
    val takeawayMargin = remember(costSummary, menuItem.price) {
        costSummary.marginPercent(menuItem.price, isTakeaway = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("recipe_editor_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RestaurantMenu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Recipe: ${menuItem.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Selling Price: ₹${"%.2f".format(menuItem.price)} • Auto-linked to Inventory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Food Cost & Margin Summary Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Dine-In Summary Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "DINE-IN (Common Items)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Cost: ₹${"%.2f".format(costSummary.dineInCost)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Margin: ${"%.1f".format(dineInMargin)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dineInMargin >= 50) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Takeaway Summary Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "TAKEAWAY (Common + Extra)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Cost: ₹${"%.2f".format(costSummary.takeawayTotalCost)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Margin: ${"%.1f".format(takeawayMargin)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (takeawayMargin >= 50) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Form to Add New Ingredient
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Add Ingredient from Inventory",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { showMultiSelectDialog = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Bulk Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Inventory item dropdown
                                ExposedDropdownMenuBox(
                                    expanded = invDropdownExpanded,
                                    onExpandedChange = { invDropdownExpanded = !invDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedItem?.let { "${it.name} (₹${it.purchasePrice}/${it.unit})" } ?: "Select Inventory Item",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Inventory Item") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = invDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                            .testTag("recipe_ingredient_dropdown")
                                    )
                                    ExposedDropdownMenu(
                                        expanded = invDropdownExpanded,
                                        onDismissRequest = { invDropdownExpanded = false }
                                    ) {
                                        allInventory.forEach { item ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(item.name, fontWeight = FontWeight.Medium)
                                                        Text(
                                                            "Stock: ${item.currentStock} ${item.unit} • Price: ₹${item.purchasePrice}/${item.unit}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedInventoryId = item.id
                                                    selectedUsageUnit = FoodCostCalculator.getDefaultUsageUnit(item.unit)
                                                    invDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Quantity & Usage Unit Selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = quantityText,
                                        onValueChange = { quantityText = it },
                                        label = { Text("Quantity Used") },
                                        placeholder = { Text(if (selectedUsageUnit == "gm") "e.g. 18" else "e.g. 150") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("recipe_quantity_input")
                                    )

                                    // Usage Unit Selector
                                    Column(modifier = Modifier.width(110.dp)) {
                                        Text(
                                            "Usage Unit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            availableUnits.forEach { u ->
                                                val isSelected = u == selectedUsageUnit
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                        .clickable { selectedUsageUnit = u }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = u,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Dynamic Line Cost calculation & Auto-fetched Price preview
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Inventory Rate: ₹${selectedItem?.purchasePrice ?: 0.0} / ${selectedItem?.unit ?: "Nos"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                "Line Cost: ₹${"%.2f".format(liveLineCost)}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }

                                // 3-Mode Apply Selector: Both, Dine-In Only, Takeaway Only
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = recipeApplyMode == "BOTH",
                                        onClick = { recipeApplyMode = "BOTH" },
                                        label = { Text("🟢 Both", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = recipeApplyMode == "DINE_IN_ONLY",
                                        onClick = { recipeApplyMode = "DINE_IN_ONLY" },
                                        label = { Text("🍽️ Dine-In", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = recipeApplyMode == "TAKEAWAY_ONLY",
                                        onClick = { recipeApplyMode = "TAKEAWAY_ONLY" },
                                        label = { Text("📦 Parcel", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Add Button
                                Button(
                                    onClick = {
                                        val qty = quantityText.toDoubleOrNull() ?: return@Button
                                        if (qty <= 0.0 || selectedItem == null) return@Button

                                        val newIngredient = RecipeIngredient(
                                            inventoryItemId = selectedItem.id,
                                            inventoryItemName = selectedItem.name,
                                            quantity = qty,
                                            usageUnit = selectedUsageUnit,
                                            isTakeawayExtra = (recipeApplyMode == "TAKEAWAY_ONLY"),
                                            applyMode = recipeApplyMode
                                        )
                                        ingredients = ingredients + newIngredient
                                        quantityText = ""
                                    },
                                    enabled = currentQty > 0.0 && selectedItem != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("recipe_add_ingredient_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Ingredient to Recipe")
                                }
                            }
                        }
                    }

                    // Section: Common Items
                    val commonItems = ingredients.filter { !it.isTakeawayExtra }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Common Ingredients (${commonItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Subtotal: ₹${"%.2f".format(costSummary.dineInCost)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (commonItems.isEmpty()) {
                        item {
                            Text(
                                "No common ingredients added yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        itemsIndexed(commonItems) { idx, ing ->
                            IngredientRowItem(
                                ingredient = ing,
                                inventoryMap = inventoryMap,
                                onUpdate = { updated ->
                                    ingredients = ingredients.map { if (it.inventoryItemId == updated.inventoryItemId) updated else it }
                                },
                                onDelete = {
                                    ingredientToDelete = ing
                                }
                            )
                        }
                    }

                    // Section: Takeaway Extra Items
                    val takeawayItems = ingredients.filter { it.isTakeawayExtra }
                    item {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Takeaway Extra Items (${takeawayItems.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                "Packaging Extra: ₹${"%.2f".format(costSummary.takeawayExtraCost)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (takeawayItems.isEmpty()) {
                        item {
                            Text(
                                "No takeaway extras (e.g. paper cups, boxes) added.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        itemsIndexed(takeawayItems) { idx, ing ->
                            IngredientRowItem(
                                ingredient = ing,
                                inventoryMap = inventoryMap,
                                onUpdate = { updated ->
                                    ingredients = ingredients.map { if (it.inventoryItemId == updated.inventoryItemId) updated else it }
                                },
                                onDelete = {
                                    ingredientToDelete = ing
                                }
                            )
                        }
                    }

                    // Recipe Notes
                    item {
                        OutlinedTextField(
                            value = recipeNotes,
                            onValueChange = { recipeNotes = it },
                            label = { Text("Preparation / Recipe Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val saved = RecipeEntity(
                                menuItemId = menuItem.id,
                                restaurantId = menuItem.categoryId,
                                menuItemName = menuItem.name,
                                ingredients = ingredients,
                                notes = recipeNotes.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSaveRecipe(saved)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recipe_save_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Recipe")
                    }
                }
            }
        }
    }

    // Bulk Multi-Select Inventory Dialog
    if (showMultiSelectDialog) {
        DishBulkIngredientPickerDialog(
            allInventory = allInventory,
            existingIngredients = ingredients,
            onDismiss = { showMultiSelectDialog = false },
            onAddSelected = { newlySelected ->
                var merged = ingredients
                for (newIng in newlySelected) {
                    merged = merged.filter { !(it.inventoryItemId == newIng.inventoryItemId && it.applyMode == newIng.applyMode) } + newIng
                }
                ingredients = merged
                showMultiSelectDialog = false
            }
        )
    }

    // Delete Ingredient Confirmation Alert (Requirement 5)
    ingredientToDelete?.let { ing ->
        AlertDialog(
            onDismissRequest = { ingredientToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove Ingredient?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Are you sure you want to remove \"${ing.inventoryItemName}\" from this recipe?", fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        ingredients = ingredients.filter { it.inventoryItemId != ing.inventoryItemId }
                        ingredientToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { ingredientToDelete = null }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun MultiSelectIngredientsDialog(
    allInventory: List<InventoryItemEntity>,
    existingIngredients: List<RecipeIngredient>,
    onDismiss: () -> Unit,
    onAddSelected: (List<InventoryItemEntity>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val existingIds = remember(existingIngredients) { existingIngredients.map { it.inventoryItemId }.toSet() }
    val selectedIds = remember { mutableStateListOf<String>() }

    val filteredInventory = remember(allInventory, searchQuery) {
        if (searchQuery.isBlank()) allInventory
        else allInventory.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text("Bulk Select Ingredients", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Tick multiple items to add to recipe at once", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Button(
                                onClick = {
                                    val picked = allInventory.filter { it.id in selectedIds }
                                    onAddSelected(picked)
                                },
                                enabled = selectedIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
                            ) {
                                Text("Add Selected (${selectedIds.size})", fontWeight = FontWeight.Bold)
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
                                    val picked = allInventory.filter { it.id in selectedIds }
                                    onAddSelected(picked)
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Add Selected (${selectedIds.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search raw materials (e.g. Cheese, Mayo, Bun)...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    if (filteredInventory.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No inventory items found.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredInventory, key = { it.id }) { item ->
                                val isAlreadyAdded = item.id in existingIds
                                val isSelected = item.id in selectedIds

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAlreadyAdded) {
                                            if (isSelected) selectedIds.remove(item.id)
                                            else selectedIds.add(item.id)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isAlreadyAdded -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected || isAlreadyAdded,
                                            enabled = !isAlreadyAdded,
                                            onCheckedChange = { checked ->
                                                if (checked) selectedIds.add(item.id)
                                                else selectedIds.remove(item.id)
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "Rate: ₹${item.purchasePrice}/${item.unit} • Stock: ${item.currentStock} ${item.unit}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isAlreadyAdded) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    "IN RECIPE",
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

@Composable
private fun IngredientRowItem(
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
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rate: ₹${"%.2f".format(currentRate)}/${invItem?.unit ?: "Nos"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "₹${"%.2f".format(lineCost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remove Ingredient",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity text field
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { text ->
                        qtyText = text
                        val d = text.toDoubleOrNull()
                        if (d != null && d >= 0.0) {
                            onUpdate(ingredient.copy(quantity = d))
                        }
                    },
                    label = { Text("Deduction Qty", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(115.dp),
                    singleLine = true
                )

                // Usage Unit chips selector
                Column(modifier = Modifier.weight(1f)) {
                    Text("Unit:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableUnits.forEach { unit ->
                            val isSelected = ingredient.usageUnit.equals(unit, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onUpdate(ingredient.copy(usageUnit = unit))
                                },
                                label = { Text(unit, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                // Checkbox for Takeaway Extra
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Takeaway Extra?", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Checkbox(
                        checked = ingredient.isTakeawayExtra,
                        onCheckedChange = { checked ->
                            onUpdate(
                                ingredient.copy(
                                    isTakeawayExtra = checked,
                                    applyMode = if (checked) "TAKEAWAY_ONLY" else "BOTH"
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
