package com.example.ui.screens.menu

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.*
import com.example.ui.theme.CaramelWarm
import com.example.ui.theme.CreamSurfaceVariant
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.SuccessGreen
import com.example.util.FoodCostCalculator
import java.util.Locale
import java.util.UUID

/**
 * Dialog to Create or Edit an Add-on Definition.
 * Validation rules enforced:
 * - Name cannot be blank
 * - Price >= 0
 * - Min count <= Max count
 * - Valid inventory reference
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonDefinitionDialog(
    addon: AddonDefinitionEntity?,
    allInventory: List<InventoryItemEntity>,
    existingGroups: List<String>,
    onDismiss: () -> Unit,
    onSave: (AddonDefinitionEntity) -> Unit
) {
    var name by remember { mutableStateOf(addon?.name ?: "") }
    var priceStr by remember { mutableStateOf(addon?.price?.takeIf { it > 0 }?.toString() ?: "0") }
    var selectionType by remember { mutableStateOf(addon?.selectionType ?: "MULTI") } // "SINGLE" or "MULTI"
    var groupName by remember { mutableStateOf(addon?.groupName ?: "Add-ons") }
    var isRequired by remember { mutableStateOf(addon?.isRequired ?: false) }
    var minCountStr by remember { mutableStateOf((addon?.minCount ?: 0).toString()) }
    var maxCountStr by remember { mutableStateOf(if (addon?.maxCount == null || addon.maxCount == Int.MAX_VALUE) "" else addon.maxCount.toString()) }
    var isActive by remember { mutableStateOf(addon?.isActive ?: true) }

    var inventoryItemId by remember { mutableStateOf(addon?.inventoryItemId) }
    var inventoryItemName by remember { mutableStateOf(addon?.inventoryItemName) }
    var inventoryQtyStr by remember { mutableStateOf((addon?.inventoryQty ?: 1.0).toString()) }
    var usageUnit by remember { mutableStateOf(addon?.usageUnit ?: "Nos") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGroupDropdownExpanded by remember { mutableStateOf(false) }
    var isInvDropdownExpanded by remember { mutableStateOf(false) }

    val inventoryMap = remember(allInventory) { allInventory.associateBy { it.id } }
    val selectedInvItem = inventoryItemId?.let { inventoryMap[it] }

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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EspressoDark)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (addon != null) "Edit Add-on Definition" else "Create Add-on Definition",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = EspressoDark
                                    )
                                    Text("Set price, selection group & optional stock link", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Button(
                                onClick = {
                                    errorMessage = null
                                    val trimmedName = name.trim()
                                    if (trimmedName.isBlank()) {
                                        errorMessage = "Please enter an add-on name"
                                        return@Button
                                    }
                                    val p = priceStr.toDoubleOrNull()
                                    if (p == null || p < 0.0) {
                                        errorMessage = "Price must be 0 or a positive number"
                                        return@Button
                                    }
                                    val minC = minCountStr.toIntOrNull() ?: 0
                                    val maxC = if (maxCountStr.isBlank()) Int.MAX_VALUE else (maxCountStr.toIntOrNull() ?: Int.MAX_VALUE)
                                    if (minC < 0) {
                                        errorMessage = "Minimum count cannot be negative"
                                        return@Button
                                    }
                                    if (maxC < minC) {
                                        errorMessage = "Minimum count cannot exceed Maximum count"
                                        return@Button
                                    }

                                    val invQty = inventoryQtyStr.toDoubleOrNull() ?: 1.0

                                    val finalAddon = AddonDefinitionEntity(
                                        id = addon?.id ?: "addon_${UUID.randomUUID()}",
                                        name = trimmedName,
                                        price = p,
                                        selectionType = selectionType,
                                        groupName = groupName.trim().ifBlank { "Add-ons" },
                                        isRequired = isRequired,
                                        minCount = if (selectionType == "SINGLE") 0 else minC,
                                        maxCount = if (selectionType == "SINGLE") 1 else maxC,
                                        isActive = isActive,
                                        inventoryItemId = inventoryItemId,
                                        inventoryItemName = selectedInvItem?.name ?: inventoryItemName,
                                        inventoryQty = invQty,
                                        usageUnit = usageUnit,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    onSave(finalAddon)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm)
                            ) {
                                Text("Save Add-on", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (errorMessage != null) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Add-on Name *", fontWeight = FontWeight.Bold, color = EspressoDark) },
                            placeholder = { Text("e.g. Extra Mozzarella Cheese, Choice of Dip") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = priceStr,
                                onValueChange = { priceStr = it },
                                label = { Text("Price (₹) *", fontWeight = FontWeight.Bold, color = EspressoDark) },
                                placeholder = { Text("0 for free") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            // Group Name Selector / Entry
                            Box(modifier = Modifier.weight(1.3f)) {
                                ExposedDropdownMenuBox(
                                    expanded = isGroupDropdownExpanded,
                                    onExpandedChange = { isGroupDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = groupName,
                                        onValueChange = { groupName = it },
                                        label = { Text("Group Name *", fontWeight = FontWeight.Bold, color = EspressoDark) },
                                        placeholder = { Text("e.g. Extra Toppings") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGroupDropdownExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        singleLine = true
                                    )

                                    if (existingGroups.isNotEmpty()) {
                                        ExposedDropdownMenu(
                                            expanded = isGroupDropdownExpanded,
                                            onDismissRequest = { isGroupDropdownExpanded = false }
                                        ) {
                                            existingGroups.distinct().forEach { grp ->
                                                DropdownMenuItem(
                                                    text = { Text(grp, fontWeight = FontWeight.Bold) },
                                                    onClick = {
                                                        groupName = grp
                                                        isGroupDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Selection Type: Radio (Single) vs Checkbox (Multi)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Selection Type *", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EspressoDark)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FilterChip(
                                        selected = selectionType == "MULTI",
                                        onClick = { selectionType = "MULTI" },
                                        label = { Text("Multi-Select (Checkboxes)", fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.CheckBox, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = selectionType == "SINGLE",
                                        onClick = { selectionType = "SINGLE" },
                                        label = { Text("Single-Select (Radio)", fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Multi-select rules (Min / Max / Required)
                    if (selectionType == "MULTI") {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Required Selection", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EspressoDark)
                                            Text("Customer must pick at least 1 option", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Switch(
                                            checked = isRequired,
                                            onCheckedChange = {
                                                isRequired = it
                                                if (it && (minCountStr.toIntOrNull() ?: 0) < 1) {
                                                    minCountStr = "1"
                                                }
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = minCountStr,
                                            onValueChange = { minCountStr = it },
                                            label = { Text("Min Selection", fontWeight = FontWeight.Bold) },
                                            placeholder = { Text("0") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = maxCountStr,
                                            onValueChange = { maxCountStr = it },
                                            label = { Text("Max Selection", fontWeight = FontWeight.Bold) },
                                            placeholder = { Text("Unlimited") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Optional Inventory Raw Material Link
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Link Inventory Raw Material (Optional)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Selecting this add-on N times deducts N × quantity via FIFO stock batches.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                ExposedDropdownMenuBox(
                                    expanded = isInvDropdownExpanded,
                                    onExpandedChange = { isInvDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedInvItem?.let { "${it.name} (${it.currentStock} ${it.unit})" } ?: "None (Price Only - No Stock Deduction)",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Raw Material", fontWeight = FontWeight.Bold) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isInvDropdownExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        singleLine = true
                                    )

                                    ExposedDropdownMenu(
                                        expanded = isInvDropdownExpanded,
                                        onDismissRequest = { isInvDropdownExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("None (Price Only - No Stock Deduction)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                            onClick = {
                                                inventoryItemId = null
                                                inventoryItemName = null
                                                isInvDropdownExpanded = false
                                            }
                                        )
                                        allInventory.forEach { inv ->
                                            DropdownMenuItem(
                                                text = { Text("${inv.name} (${inv.currentStock} ${inv.unit})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                onClick = {
                                                    inventoryItemId = inv.id
                                                    inventoryItemName = inv.name
                                                    usageUnit = FoodCostCalculator.getDefaultUsageUnit(inv.unit)
                                                    isInvDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (selectedInvItem != null) {
                                    val availableUnits = remember(selectedInvItem.unit) {
                                        FoodCostCalculator.getAvailableUsageUnits(selectedInvItem.unit)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = inventoryQtyStr,
                                            onValueChange = { inventoryQtyStr = it },
                                            label = { Text("Deduction Qty", fontWeight = FontWeight.Bold) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )

                                        Column {
                                            Text("Usage Unit:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EspressoDark)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                availableUnits.forEach { u ->
                                                    FilterChip(
                                                        selected = usageUnit.equals(u, ignoreCase = true),
                                                        onClick = { usageUnit = u },
                                                        label = { Text(u, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Add-on Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EspressoDark)
                            FilterChip(
                                selected = isActive,
                                onClick = { isActive = !isActive },
                                label = { Text(if (isActive) "Active" else "Deactivated", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SuccessGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = SuccessGreen
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bulk Multi-Select Dialog to assign default Add-ons to a Menu Category.
 * "select several -> Add Selected"
 */
@Composable
fun CategoryDefaultAddonsDialog(
    category: CategoryEntity,
    allAddons: List<AddonDefinitionEntity>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val initialSelected = remember(category.defaultAddonIdsJson) {
        mutableStateListOf(*parseAddonIds(category.defaultAddonIdsJson).toTypedArray())
    }

    val activeAddons = remember(allAddons) { allAddons.filter { it.isActive } }
    val groupedAddons = remember(activeAddons) { activeAddons.groupBy { it.groupName } }

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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EspressoDark)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Bulk Set Default Add-ons",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = EspressoDark
                                    )
                                    Text(
                                        text = "Category: ${category.name}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = { onSave(initialSelected.toList()) },
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Add Selected (${initialSelected.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold, color = EspressoDark)
                            }
                            Button(
                                onClick = { onSave(initialSelected.toList()) },
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save Default Add-ons (${initialSelected.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            ) { padding ->
                if (activeAddons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active add-ons available. Create add-ons in Add-ons tab first.",
                            fontSize = 13.sp,
                            color = EspressoDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Select add-ons that ALL dishes in category '${category.name}' will inherit by default:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EspressoDark
                            )
                        }

                        groupedAddons.forEach { (grpName, list) ->
                            item {
                                Surface(
                                    color = CreamSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = grpName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = EspressoDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            items(list) { addon ->
                                val isChecked = initialSelected.contains(addon.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) initialSelected.remove(addon.id)
                                            else initialSelected.add(addon.id)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                if (it) initialSelected.add(addon.id)
                                                else initialSelected.remove(addon.id)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(addon.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EspressoDark)
                                            Text(
                                                text = "${if (addon.price > 0) "₹${String.format(Locale.US, "%.2f", addon.price)}" else "Free"} • Type: ${addon.selectionType}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = EspressoDark
                                            )
                                            if (!addon.inventoryItemName.isNullOrBlank()) {
                                                Text(
                                                    text = "Deducts stock: ${addon.inventoryItemName} (${addon.inventoryQty} ${addon.usageUnit})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SuccessGreen
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
 * Dish Item-level Add-on Override Dialog (Full Screen).
 * Opens explicitly on tapping "+ Add-ons" on a Dish card.
 * - Shows inherited Category Defaults (each individually excludable).
 * - Shows bulk multi-select list for Item-Specific Extra Add-ons.
 * Effective list = (category defaults - exclusions) + item additions.
 */
@Composable
fun DishAddonOverrideDialog(
    dish: MenuItemEntity,
    category: CategoryEntity?,
    allAddons: List<AddonDefinitionEntity>,
    onDismiss: () -> Unit,
    onSave: (excludedAddonIds: List<String>, itemAddonIds: List<String>) -> Unit
) {
    val activeAddonsMap = remember(allAddons) { allAddons.filter { it.isActive }.associateBy { it.id } }

    val categoryDefaultAddonIds = remember(category?.defaultAddonIdsJson) {
        parseAddonIds(category?.defaultAddonIdsJson)
    }

    val currentExcluded = remember(dish.excludedAddonIdsJson) {
        mutableStateListOf(*parseAddonIds(dish.excludedAddonIdsJson).toTypedArray())
    }

    val currentItemAddons = remember(dish.itemAddonIdsJson) {
        mutableStateListOf(*parseAddonIds(dish.itemAddonIdsJson).toTypedArray())
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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EspressoDark)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Configure Add-ons for Dish",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = EspressoDark
                                    )
                                    Text(
                                        text = dish.name,
                                        fontSize = 13.sp,
                                        color = CaramelWarm,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = { onSave(currentExcluded.toList(), currentItemAddons.toList()) },
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Save Config", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold, color = EspressoDark)
                            }
                            Button(
                                onClick = { onSave(currentExcluded.toList(), currentItemAddons.toList()) },
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Save Add-on Changes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: Category Inherited Defaults
                    item {
                        Column {
                            Text("Category Defaults (Inherited)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Uncheck to exclude an inherited default for this specific dish.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EspressoDark)
                        }
                    }

                    if (categoryDefaultAddonIds.isEmpty()) {
                        item {
                            Surface(
                                color = CreamSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("No category defaults assigned.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EspressoDark, modifier = Modifier.padding(10.dp))
                            }
                        }
                    } else {
                        items(categoryDefaultAddonIds) { defId ->
                            val addon = activeAddonsMap[defId]
                            if (addon != null) {
                                val isIncluded = !currentExcluded.contains(defId)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isIncluded) CreamSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(1.dp, if (isIncluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isIncluded,
                                            onCheckedChange = { checked ->
                                                if (checked) currentExcluded.remove(defId)
                                                else currentExcluded.add(defId)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(addon.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EspressoDark)
                                            Text(
                                                text = "${addon.groupName} • ${if (addon.price > 0) "₹${String.format(Locale.US, "%.2f", addon.price)}" else "Free"}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = EspressoDark
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isIncluded) SuccessGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = if (isIncluded) "INHERITED" else "EXCLUDED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isIncluded) SuccessGreen else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }

                    // Section 2: Dish-Specific Extra Add-ons (Bulk Multi-select)
                    item {
                        Column {
                            Text("Dish-Specific Extra Add-ons", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Select extra add-ons specific to ${dish.name}.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EspressoDark)
                        }
                    }

                    val extraAddonCandidates = allAddons.filter { it.isActive && it.id !in categoryDefaultAddonIds }
                    if (extraAddonCandidates.isEmpty()) {
                        item {
                            Surface(
                                color = CreamSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("No other extra add-ons available.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EspressoDark, modifier = Modifier.padding(10.dp))
                            }
                        }
                    } else {
                        items(extraAddonCandidates) { addon ->
                            val isChecked = currentItemAddons.contains(addon.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) currentItemAddons.remove(addon.id)
                                        else currentItemAddons.add(addon.id)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = {
                                            if (it) currentItemAddons.add(addon.id)
                                            else currentItemAddons.remove(addon.id)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(addon.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EspressoDark)
                                        Text(
                                            text = "${addon.groupName} • ${if (addon.price > 0) "₹${String.format(Locale.US, "%.2f", addon.price)}" else "Free"}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EspressoDark
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

fun parseAddonIds(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun formatAddonIds(ids: List<String>): String {
    val arr = org.json.JSONArray()
    ids.distinct().forEach { arr.put(it) }
    return arr.toString()
}
