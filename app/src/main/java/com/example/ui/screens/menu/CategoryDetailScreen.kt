package com.example.ui.screens.menu

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.AddonDefinitionEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.RecipeEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.CaramelWarm
import com.example.ui.theme.CreamSurfaceVariant
import com.example.ui.theme.EspressoDark
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: CategoryEntity,
    menuItems: List<MenuItemEntity>,
    allInventory: List<InventoryItemEntity>,
    recipesMap: Map<String, RecipeEntity>,
    categories: List<CategoryEntity>,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Dishes assigned to this category
    val categoryDishes = remember(menuItems, category.id) {
        menuItems.filter { it.categoryId == category.id }
    }

    // Category Addons flow
    val allAddonDefinitions by viewModel.allAddonDefinitions.collectAsState()
    val categoryAddons = remember(allAddonDefinitions, category.id) {
        allAddonDefinitions.filter { it.categoryId == category.id }
    }

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Dishes, 1 = Category Add-ons

    var showAddDishDialog by remember { mutableStateOf(false) }
    var dishToEdit by remember { mutableStateOf<MenuItemEntity?>(null) }
    var showAssignExistingDialog by remember { mutableStateOf(false) }
    var dishToRemoveFromCategory by remember { mutableStateOf<MenuItemEntity?>(null) }

    var showAddAddonDialog by remember { mutableStateOf(false) }
    var addonToEdit by remember { mutableStateOf<AddonDefinitionEntity?>(null) }
    var addonToDelete by remember { mutableStateOf<AddonDefinitionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = category.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (selectedSubTab == 0) "${categoryDishes.size} dishes assigned" else "${categoryAddons.size} add-ons configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Categories"
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            if (selectedSubTab == 0) showAddDishDialog = true
                            else showAddAddonDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedSubTab == 0) "+ Add Item" else "+ Add Add-on",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedSubTab == 0) showAddDishDialog = true
                    else showAddAddonDialog = true
                },
                containerColor = CaramelWarm,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        if (selectedSubTab == 0) "Add Item to ${category.name}" else "Add Add-on to ${category.name}",
                        fontWeight = FontWeight.Bold
                    )
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category header card with status and assign existing option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant)
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
                            Text(
                                text = "Category Overview",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (category.isDiscountEligible) Color(0xFFE8F5E9) else Color(0xFFEEEEEE)
                            ) {
                                Text(
                                    text = if (category.isDiscountEligible) "Discount Eligible" else "Discount Excluded",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (category.isDiscountEligible) Color(0xFF2E7D32) else Color(0xFF616161),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Dishes here appear under ${category.name} in billing menu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Assign existing dish button
                    OutlinedButton(
                        onClick = { showAssignExistingDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Assign Existing", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = CaramelWarm,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Dishes (${categoryDishes.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Fastfood, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Add-ons (${categoryAddons.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedSubTab == 0) {
                if (categoryDishes.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestaurantMenu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No dishes in ${category.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add Item' to create a new dish for this category or 'Assign Existing' to link one from your menu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDishDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Item", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categoryDishes, key = { it.id }) { dish ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dish image or icon
                                if (!dish.imageUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = dish.imageUri,
                                        contentDescription = dish.name,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CreamSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fastfood,
                                            contentDescription = null,
                                            tint = CaramelWarm,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dish.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.2f", dish.price)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = CaramelWarm
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (dish.isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                        ) {
                                            Text(
                                                text = if (dish.isAvailable) "Available" else "Unavailable",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (dish.isAvailable) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (dish.isDiscountEligible) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFE3F2FD)
                                            ) {
                                                Text(
                                                    text = "Discount ON",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1565C0),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Actions: Edit and Unassign/Remove from Category
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { dishToEdit = dish },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Dish",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Remove/Unassign from Category button with Confirmation
                                    OutlinedButton(
                                        onClick = {
                                            dishToRemoveFromCategory = dish
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LinkOff,
                                            contentDescription = "Remove from category",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (categoryAddons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No add-ons in ${category.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add toppings, extra cheese, or options that customers can choose for dishes in this category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddAddonDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Category Add-on", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val grouped = categoryAddons.groupBy { it.groupName }
                    grouped.forEach { (group, list) ->
                        item {
                            Surface(
                                color = CreamSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(
                                    text = group,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = EspressoDark,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        items(list, key = { it.id }) { addon ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = addon.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (addon.price > 0.0) "₹${String.format(Locale.US, "%.2f", addon.price)}" else "Free",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = CaramelWarm
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFE3F2FD)
                                            ) {
                                                Text(
                                                    text = addon.selectionType,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1565C0),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (!addon.inventoryItemName.isNullOrBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFE8F5E9)
                                                ) {
                                                    Text(
                                                        text = "Stock: ${addon.inventoryItemName} (${addon.inventoryQty} ${addon.usageUnit})",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { addonToEdit = addon },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Addon",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { addonToDelete = addon },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Addon",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
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

    // Add Dish Dialog with Category PRE-SELECTED and locked (Requirement 2)
    if (showAddDishDialog) {
        DishFormDialog(
            dish = null,
            categories = categories,
            allInventory = allInventory,
            existingRecipe = null,
            preselectedCategoryId = category.id,
            isCategoryLocked = true,
            allDishes = menuItems,
            allRecipes = recipesMap,
            onDismiss = { showAddDishDialog = false },
            onSave = { newDish, recipe ->
                viewModel.saveMenuItem(newDish)
                if (recipe != null) {
                    viewModel.saveRecipe(recipe)
                }
                showAddDishDialog = false
                Toast.makeText(context, "Added \"${newDish.name}\" to ${category.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Dish Dialog
    dishToEdit?.let { dish ->
        DishFormDialog(
            dish = dish,
            categories = categories,
            allInventory = allInventory,
            existingRecipe = recipesMap[dish.id],
            preselectedCategoryId = category.id,
            isCategoryLocked = false,
            allDishes = menuItems,
            allRecipes = recipesMap,
            onDismiss = { dishToEdit = null },
            onSave = { updatedDish, recipe ->
                viewModel.saveMenuItem(updatedDish)
                if (recipe != null) {
                    viewModel.saveRecipe(recipe)
                }
                dishToEdit = null
                Toast.makeText(context, "Updated \"${updatedDish.name}\"", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Assign Existing Dish Dialog (Strictly UNASSIGNED dishes only)
    if (showAssignExistingDialog) {
        val unassignedDishes = remember(menuItems) {
            menuItems.filter {
                it.categoryId.isBlank() ||
                it.categoryId.equals("uncategorized", ignoreCase = true) ||
                it.categoryName.equals("Uncategorized", ignoreCase = true)
            }
        }
        var searchQuery by remember { mutableStateOf("") }
        val filteredUnassignedDishes = remember(unassignedDishes, searchQuery) {
            if (searchQuery.isBlank()) unassignedDishes
            else unassignedDishes.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showAssignExistingDialog = false },
            title = {
                Column {
                    Text(
                        text = "Assign Unassigned Dish to ${category.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Only dishes not belonging to any other category are shown.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search unassigned dishes...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredUnassignedDishes.isEmpty()) {
                        Text(
                            text = if (searchQuery.isBlank()) "No unassigned dishes found. (To move a dish from another category, first remove it from that category)." else "No matching unassigned dishes found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredUnassignedDishes, key = { it.id }) { unassignedDish ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(unassignedDish.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "₹${String.format(Locale.US, "%.2f", unassignedDish.price)} • Status: Unassigned",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val assigned = unassignedDish.copy(
                                                    categoryId = category.id,
                                                    categoryName = category.name,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                                viewModel.saveMenuItem(assigned)
                                                Toast.makeText(context, "Assigned \"${unassignedDish.name}\" to ${category.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Assign", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssignExistingDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Confirmation Dialog for removing dish from category
    dishToRemoveFromCategory?.let { dish ->
        AlertDialog(
            onDismissRequest = { dishToRemoveFromCategory = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove from Category?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Are you sure you want to remove \"${dish.name}\" from \"${category.name}\"?\n\n(Note: The dish will not be deleted from your menu; it will be marked as Uncategorized).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val unassignedDish = dish.copy(
                            categoryId = "",
                            categoryName = "Uncategorized",
                            updatedAt = System.currentTimeMillis()
                        )
                        viewModel.saveMenuItem(unassignedDish)
                        dishToRemoveFromCategory = null
                        Toast.makeText(
                            context,
                            "Removed \"${dish.name}\" from ${category.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Remove", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { dishToRemoveFromCategory = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Add / Edit Add-on Dialog for Category
    if (showAddAddonDialog || addonToEdit != null) {
        val groups = remember(categoryAddons) { categoryAddons.map { it.groupName }.distinct() }
        AddonDefinitionDialog(
            addon = addonToEdit,
            allInventory = allInventory,
            existingGroups = groups.ifEmpty { listOf("Extras", "Toppings", "Sauces") },
            onDismiss = {
                showAddAddonDialog = false
                addonToEdit = null
            },
            onSave = { addon ->
                viewModel.saveAddonDefinition(addon.copy(categoryId = category.id))
                showAddAddonDialog = false
                addonToEdit = null
                Toast.makeText(context, "Add-on saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Add-on Confirmation Dialog
    addonToDelete?.let { addon ->
        AlertDialog(
            onDismissRequest = { addonToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Add-on?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Are you sure you want to delete \"${addon.name}\"?\nThis cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAddonDefinition(addon.id)
                        addonToDelete = null
                        Toast.makeText(context, "Add-on deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { addonToDelete = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
