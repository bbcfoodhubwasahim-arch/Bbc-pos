package com.example.ui.screens.menu

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.entity.CafeTableEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ComboEntity
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.MenuItemVariant
import com.example.data.local.entity.MenuItemAddon
import com.example.data.local.entity.parseVariants
import com.example.data.local.entity.formatVariants
import com.example.data.local.entity.parseAddons
import com.example.data.local.entity.formatAddons
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredient
import com.example.ui.MainViewModel
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.theme.*
import com.example.util.FoodCostCalculator
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuManagementScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Dishes, 1 = Categories, 2 = Combos, 3 = Tables
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val combos by viewModel.combos.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()
    val recipesMap by viewModel.recipesMap.collectAsState()
    val allInventory by viewModel.allInventory.collectAsState()
    val inventoryMap by viewModel.inventoryMap.collectAsState()
    val allAddonDefinitions by viewModel.allAddonDefinitions.collectAsState()

    var showAddDishDialog by remember { mutableStateOf(false) }
    var dishToEdit by remember { mutableStateOf<MenuItemEntity?>(null) }
    var recipeDishToEdit by remember { mutableStateOf<MenuItemEntity?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryForAddonDefaults by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAddComboDialog by remember { mutableStateOf(false) }
    var comboToEdit by remember { mutableStateOf<ComboEntity?>(null) }
    var showAddTableDialog by remember { mutableStateOf(false) }
    var tableToEdit by remember { mutableStateOf<CafeTableEntity?>(null) }

    var dishToDelete by remember { mutableStateOf<MenuItemEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var comboToDelete by remember { mutableStateOf<ComboEntity?>(null) }
    var tableToDelete by remember { mutableStateOf<CafeTableEntity?>(null) }
    var selectedCategoryForDetail by remember { mutableStateOf<CategoryEntity?>(null) }

    // Dedicated Category Detail View (FIX 1)
    selectedCategoryForDetail?.let { cat ->
        val currentCat = categories.find { it.id == cat.id } ?: cat
        CategoryDetailScreen(
            category = currentCat,
            menuItems = menuItems,
            allInventory = allInventory,
            recipesMap = recipesMap,
            categories = categories,
            viewModel = viewModel,
            onBack = { selectedCategoryForDetail = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menu & Tables", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        when (selectedTab) {
                            0 -> showAddDishDialog = true
                            1 -> showAddCategoryDialog = true
                            2 -> showAddComboDialog = true
                            3 -> showAddTableDialog = true
                        }
                    }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = CaramelWarm)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> showAddDishDialog = true
                        1 -> showAddCategoryDialog = true
                        2 -> showAddComboDialog = true
                        3 -> showAddTableDialog = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        when (selectedTab) {
                            0 -> "Add Dish"
                            1 -> "Add Category"
                            2 -> "Add Combo"
                            else -> "Add Table"
                        }
                    )
                },
                containerColor = CaramelWarm,
                contentColor = Color.White
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = CaramelWarm
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Dishes (${menuItems.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Fastfood, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Categories (${categories.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Combos (${combos.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Tables (${tables.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.TableBar, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Dishes List
                    if (menuItems.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.RestaurantMenu,
                            title = "No dishes added yet",
                            subtitle = "Tap + Add Dish to create menu items"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(menuItems) { dish ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Dish photo thumbnail or placeholder
                                        if (!dish.imageUri.isNullOrBlank()) {
                                            AsyncImage(
                                                model = dish.imageUri,
                                                contentDescription = dish.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CreamSurfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.RestaurantMenu,
                                                    contentDescription = null,
                                                    tint = CaramelWarm.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        val recipe = recipesMap[dish.id]
                                        val hasRecipe = recipe != null && recipe.ingredients.isNotEmpty()
                                        val costSummary = if (hasRecipe) FoodCostCalculator.calculateRecipeCost(recipe!!, inventoryMap) else null
                                        val dineInMargin = if (costSummary != null) costSummary.marginPercent(dish.price, isTakeaway = false) else null

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(dish.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(dish.categoryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text("₹${String.format(Locale.US, "%.2f", dish.price)}", fontWeight = FontWeight.Bold, color = CaramelWarm)
                                                if (costSummary != null && dineInMargin != null) {
                                                     Surface(
                                                         shape = RoundedCornerShape(4.dp),
                                                         color = if (dineInMargin >= 50.0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                                     ) {
                                                         Text(
                                                             text = "Cost: ₹${"%.1f".format(costSummary.dineInCost)} (${"%.0f".format(dineInMargin)}% margin)",
                                                             fontSize = 11.sp,
                                                             fontWeight = FontWeight.SemiBold,
                                                             color = if (dineInMargin >= 50.0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                                             modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                         )
                                                     }
                                                }
                                            }

                                            // Discount Eligible Quick Toggle
                                            val dishCat = categories.find { it.id == dish.categoryId }
                                            val catEligible = dishCat?.isDiscountEligible ?: true
                                            val effectiveEligible = dish.isDiscountEligible && catEligible
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (effectiveEligible) Color(0xFFE8F5E9) else Color(0xFFEEEEEE),
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .clickable {
                                                        viewModel.saveMenuItem(dish.copy(isDiscountEligible = !dish.isDiscountEligible, updatedAt = System.currentTimeMillis()))
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (effectiveEligible) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                        contentDescription = null,
                                                        tint = if (effectiveEligible) Color(0xFF2E7D32) else Color(0xFF757575),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = if (effectiveEligible) "Discount: ON" else if (!catEligible) "Discount: OFF (Category OFF)" else "Discount: OFF",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (effectiveEligible) Color(0xFF2E7D32) else Color(0xFF616161)
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilledTonalIconButton(
                                                onClick = { recipeDishToEdit = dish },
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = if (hasRecipe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.RestaurantMenu,
                                                    contentDescription = "Recipe & Food Cost",
                                                    tint = if (hasRecipe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(onClick = { dishToEdit = dish }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { dishToDelete = dish }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Categories List
                    if (categories.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Category,
                            title = "No categories yet",
                            subtitle = "Tap + Add Category to organize your menu"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { cat ->
                                val itemCount = menuItems.count { it.categoryId == cat.id }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCategoryForDetail = cat },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                    contentDescription = "Open Category",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text("$itemCount dishes in this category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (cat.isDiscountEligible) Color(0xFFE8F5E9) else Color(0xFFEEEEEE),
                                                modifier = Modifier.clickable {
                                                    viewModel.saveCategory(cat.copy(isDiscountEligible = !cat.isDiscountEligible, updatedAt = System.currentTimeMillis()))
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (cat.isDiscountEligible) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                        contentDescription = null,
                                                        tint = if (cat.isDiscountEligible) Color(0xFF2E7D32) else Color(0xFF757575),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (cat.isDiscountEligible) "Discount ON" else "Discount OFF",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (cat.isDiscountEligible) Color(0xFF2E7D32) else Color(0xFF616161)
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { categoryForAddonDefaults = cat },
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add-ons", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            IconButton(onClick = { categoryToEdit = cat }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { categoryToDelete = cat }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Combos List
                    if (combos.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Celebration,
                            title = "No combos yet",
                            subtitle = "Tap + Add Combo to create meal bundles with item slots"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(combos, key = { it.id }) { combo ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        // Header: Name, Badge, Active Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = combo.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = EspressoDark
                                                    )
                                                    if (combo.badge.isNotBlank()) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Surface(
                                                            color = CaramelWarm,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = combo.badge,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                if (combo.description.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = combo.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Active / Inactive Switch
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (combo.isActive) "Active" else "Inactive",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (combo.isActive) CaramelWarm else MaterialTheme.colorScheme.outline
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Switch(
                                                    checked = combo.isActive,
                                                    onCheckedChange = { viewModel.toggleComboActive(combo) },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = CaramelWarm,
                                                        checkedTrackColor = CaramelWarm.copy(alpha = 0.4f)
                                                    ),
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Slots summary chips
                                        if (combo.slots.isNotEmpty()) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                combo.slots.forEachIndexed { sIdx, slot ->
                                                    Surface(
                                                        color = CreamSurfaceVariant,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${slot.label.ifBlank { "Slot ${sIdx + 1}" }} (${slot.quantity})",
                                                            fontSize = 11.sp,
                                                            color = EspressoDark,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        // Price and Action buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "₹${String.format(Locale.US, "%.0f", combo.price)}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 17.sp,
                                                color = EspressoBrown
                                            )

                                            Row {
                                                IconButton(onClick = { comboToEdit = combo }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Combo", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(onClick = { comboToDelete = combo }) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Combo", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Tables List
                    if (tables.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.TableBar,
                            title = "No tables added",
                            subtitle = "Tap + Add Table to configure Dine In seating"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tables) { table ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = EspressoBrown)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(table.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Text("Capacity: ${table.capacity} guests", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Toggle Occupancy
                                            FilterChip(
                                                selected = table.isOccupied,
                                                onClick = { viewModel.setTableOccupancy(table.id, !table.isOccupied) },
                                                label = { Text(if (table.isOccupied) "Occupied" else "Available", fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = TableOccupiedRed.copy(alpha = 0.2f),
                                                    selectedLabelColor = TableOccupiedRed
                                                )
                                            )
                                            IconButton(onClick = { tableToEdit = table }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { tableToDelete = table }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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

    // Add / Edit Dish Dialog
    if (showAddDishDialog || dishToEdit != null) {
        DishFormDialog(
            dish = dishToEdit,
            categories = categories,
            allInventory = allInventory,
            existingRecipe = dishToEdit?.let { recipesMap[it.id] },
            allDishes = menuItems,
            allRecipes = recipesMap,
            onDismiss = {
                showAddDishDialog = false
                dishToEdit = null
            },
            onSave = { item, recipe ->
                viewModel.saveMenuItem(item)
                if (recipe != null) {
                    viewModel.saveRecipe(recipe)
                }
                showAddDishDialog = false
                dishToEdit = null
                Toast.makeText(context, "Dish and recipe saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add / Edit Category Dialog
    if (showAddCategoryDialog || categoryToEdit != null) {
        CategoryFormDialog(
            category = categoryToEdit,
            onDismiss = {
                showAddCategoryDialog = false
                categoryToEdit = null
            },
            onSave = { cat ->
                viewModel.saveCategory(cat)
                showAddCategoryDialog = false
                categoryToEdit = null
                Toast.makeText(context, "Category saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Category Default Add-ons Bulk Launcher
    categoryForAddonDefaults?.let { cat ->
        CategoryDefaultAddonsDialog(
            category = cat,
            allAddons = allAddonDefinitions,
            onDismiss = { categoryForAddonDefaults = null },
            onSave = { defaultIds ->
                val json = formatAddonIds(defaultIds)
                val updatedCat = cat.copy(defaultAddonIdsJson = json, updatedAt = System.currentTimeMillis())
                viewModel.saveCategory(updatedCat)
                categoryForAddonDefaults = null
                Toast.makeText(context, "Default add-ons updated for ${cat.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add / Edit Combo Dialog
    if (showAddComboDialog || comboToEdit != null) {
        ComboFormDialog(
            comboToEdit = comboToEdit,
            categories = categories,
            restaurantId = activeRestaurant?.id ?: "rest_default",
            onDismiss = {
                showAddComboDialog = false
                comboToEdit = null
            },
            onSave = { combo ->
                viewModel.saveCombo(combo)
                showAddComboDialog = false
                comboToEdit = null
                Toast.makeText(context, "Combo saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add / Edit Table Dialog
    if (showAddTableDialog || tableToEdit != null) {
        TableFormDialog(
            table = tableToEdit,
            restaurantId = activeRestaurant?.id ?: "rest_default",
            onDismiss = {
                showAddTableDialog = false
                tableToEdit = null
            },
            onSave = { tbl ->
                viewModel.saveTable(tbl)
                showAddTableDialog = false
                tableToEdit = null
                Toast.makeText(context, "Table saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Recipe Editor Dialog
    if (recipeDishToEdit != null) {
        RecipeEditorDialog(
            menuItem = recipeDishToEdit!!,
            currentRecipe = recipesMap[recipeDishToEdit!!.id],
            allInventory = allInventory,
            onDismiss = { recipeDishToEdit = null },
            onSaveRecipe = { recipe ->
                viewModel.saveRecipe(recipe)
                recipeDishToEdit = null
                Toast.makeText(context, "Recipe updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialogs (FIX 4)
    dishToDelete?.let { dish ->
        DeleteConfirmationDialog(
            title = "Delete Dish?",
            itemName = dish.name,
            onDismiss = { dishToDelete = null },
            onConfirm = {
                viewModel.deleteMenuItem(dish.id)
                dishToDelete = null
                Toast.makeText(context, "Deleted \"${dish.name}\"", Toast.LENGTH_SHORT).show()
            }
        )
    }

    categoryToDelete?.let { cat ->
        DeleteConfirmationDialog(
            title = "Delete Category?",
            itemName = cat.name,
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                viewModel.deleteCategory(cat.id)
                categoryToDelete = null
                Toast.makeText(context, "Deleted \"${cat.name}\"", Toast.LENGTH_SHORT).show()
            }
        )
    }

    comboToDelete?.let { combo ->
        DeleteConfirmationDialog(
            title = "Delete Combo?",
            itemName = combo.name,
            onDismiss = { comboToDelete = null },
            onConfirm = {
                viewModel.deleteCombo(combo.id)
                comboToDelete = null
                Toast.makeText(context, "Deleted \"${combo.name}\"", Toast.LENGTH_SHORT).show()
            }
        )
    }

    tableToDelete?.let { tbl ->
        DeleteConfirmationDialog(
            title = "Delete Table?",
            itemName = tbl.name,
            onDismiss = { tableToDelete = null },
            onConfirm = {
                viewModel.deleteTable(tbl.id)
                tableToDelete = null
                Toast.makeText(context, "Deleted \"${tbl.name}\"", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var isDiscountEligible by remember { mutableStateOf(category?.isDiscountEligible ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category != null) "Edit Category" else "Add New Category", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Desserts & Gelato") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Discount Eligible", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            text = if (isDiscountEligible) "Dishes in category are discount-eligible" else "All dishes in category excluded from discount",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDiscountEligible,
                        onCheckedChange = { isDiscountEligible = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                val newCat = CategoryEntity(
                    id = category?.id ?: "cat_${UUID.randomUUID()}",
                    name = name.trim(),
                    isDiscountEligible = isDiscountEligible
                )
                onSave(newCat)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TableFormDialog(
    table: CafeTableEntity?,
    restaurantId: String,
    onDismiss: () -> Unit,
    onSave: (CafeTableEntity) -> Unit
) {
    var name by remember { mutableStateOf(table?.name ?: "") }
    var capacityStr by remember { mutableStateOf(table?.capacity?.toString() ?: "4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (table != null) "Edit Table" else "Add New Table", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Table Name / Number") },
                    placeholder = { Text("e.g. Table 9, Patio 1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = capacityStr,
                    onValueChange = { capacityStr = it },
                    label = { Text("Seating Capacity") },
                    placeholder = { Text("e.g. 4") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cap = capacityStr.toIntOrNull() ?: 4
                if (name.isBlank()) return@Button
                val newTbl = CafeTableEntity(
                    id = table?.id ?: "tbl_${UUID.randomUUID()}",
                    restaurantId = restaurantId,
                    name = name.trim(),
                    capacity = cap,
                    isOccupied = table?.isOccupied ?: false
                )
                onSave(newTbl)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
