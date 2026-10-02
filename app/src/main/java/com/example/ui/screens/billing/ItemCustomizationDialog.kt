package com.example.ui.screens.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
import com.example.data.local.entity.MenuItemAddon
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.MenuItemVariant
import com.example.data.local.entity.SelectedAddon
import com.example.data.local.entity.parseAddons
import com.example.data.local.entity.parseVariants
import com.example.ui.theme.CaramelWarm
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldTextDark
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemCustomizationDialog(
    dish: MenuItemEntity,
    categoryAddons: List<com.example.data.local.entity.AddonDefinitionEntity> = emptyList(),
    initialVariantName: String? = null,
    initialSelectedAddons: List<SelectedAddon> = emptyList(),
    initialNotes: String = "",
    initialQuantity: Int = 1,
    initialIsComplimentary: Boolean = false,
    initialComplimentaryReason: String = "",
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (MenuItemVariant?, List<SelectedAddon>, String, Int, Boolean, String) -> Unit
) {
    val variants = remember(dish.variantsJson) { parseVariants(dish.variantsJson) }
    
    // Map Category-level addons to MenuItemAddon and combine with item addons
    val categoryMenuItemAddons = remember(categoryAddons) {
        categoryAddons.map {
            MenuItemAddon(
                id = it.id,
                name = it.name,
                price = it.price,
                inventoryItemId = it.inventoryItemId,
                inventoryItemName = it.inventoryItemName,
                inventoryQty = it.inventoryQty,
                usageUnit = it.usageUnit
            )
        }
    }
    
    val addons = remember(dish.addonsJson, categoryMenuItemAddons) {
        val dishAddons = parseAddons(dish.addonsJson)
        (categoryMenuItemAddons + dishAddons).distinctBy { it.name.lowercase() }
    }

    var selectedVariant by remember(variants, initialVariantName) {
        mutableStateOf(variants.find { it.name == initialVariantName } ?: variants.firstOrNull())
    }
    // Map addon.id -> SelectedAddon
    var selectedAddonsMap by remember(initialSelectedAddons, addons) {
        val initialMap = mutableMapOf<String, SelectedAddon>()
        initialSelectedAddons.forEach { sa ->
            val matchingAddon = addons.find { it.id == sa.id || it.name.equals(sa.name, ignoreCase = true) }
            val key = matchingAddon?.id ?: sa.id
            initialMap[key] = sa
        }
        mutableStateOf(initialMap.toMap())
    }
    var cookingNotes by remember(initialNotes) { mutableStateOf(initialNotes) }
    var mainItemQuantity by remember(initialQuantity) { mutableIntStateOf(initialQuantity.coerceAtLeast(1)) }
    var isComplimentary by remember(initialIsComplimentary) { mutableStateOf(initialIsComplimentary) }
    var complimentaryReason by remember(initialComplimentaryReason) { 
        mutableStateOf(initialComplimentaryReason.ifBlank { "Owner Treat" }) 
    }

    val basePrice = selectedVariant?.price ?: dish.price
    val addonsPrice = selectedAddonsMap.values.sumOf { it.totalPrice }
    val singleItemPrice = if (isComplimentary) 0.0 else (basePrice + addonsPrice)
    val totalPrice = if (isComplimentary) 0.0 else (singleItemPrice * mainItemQuantity)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dish.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = EspressoDark
                    )
                    Text(
                        text = "Customize portion, add-ons & notes",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Portion Size / Variants
                if (variants.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Choose Portion / Size",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                variants.forEach { variant ->
                                    val isSelected = selectedVariant?.name == variant.name
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedVariant = variant },
                                        label = {
                                            Text(
                                                text = "${variant.name} (₹${String.format(Locale.US, "%.0f", variant.price)})",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldAccent,
                                            selectedLabelColor = GoldTextDark
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Add-ons / Extras (Supports Qty and Custom Price editing!)
                if (addons.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Add-ons & Extras (Select Qty & Edit Amount)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            addons.forEach { addon ->
                                val selectedAddon = selectedAddonsMap[addon.id]
                                val isChecked = selectedAddon != null

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) GoldAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isChecked) 1.5.dp else 1.dp,
                                        color = if (isChecked) GoldAccent else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (isChecked) {
                                                            selectedAddonsMap = selectedAddonsMap - addon.id
                                                        } else {
                                                            selectedAddonsMap = selectedAddonsMap + (
                                                                    addon.id to SelectedAddon(
                                                                        addonId = addon.id,
                                                                        name = addon.name,
                                                                        unitPrice = addon.price,
                                                                        quantity = 1,
                                                                        inventoryItemId = addon.inventoryItemId,
                                                                        inventoryQty = addon.inventoryQty,
                                                                        usageUnit = addon.usageUnit
                                                                    )
                                                                    )
                                                        }
                                                    }
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            selectedAddonsMap = selectedAddonsMap + (
                                                                    addon.id to SelectedAddon(
                                                                        addonId = addon.id,
                                                                        name = addon.name,
                                                                        unitPrice = addon.price,
                                                                        quantity = 1,
                                                                        inventoryItemId = addon.inventoryItemId,
                                                                        inventoryQty = addon.inventoryQty,
                                                                        usageUnit = addon.usageUnit
                                                                    )
                                                                    )
                                                        } else {
                                                            selectedAddonsMap = selectedAddonsMap - addon.id
                                                        }
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Column {
                                                    Text(text = addon.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    if (!addon.inventoryItemName.isNullOrBlank()) {
                                                        Text(
                                                            text = "Uses: ${addon.inventoryItemName} (${addon.inventoryQty} ${addon.usageUnit})",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            if (!isChecked) {
                                                Text(
                                                    text = "+₹${String.format(Locale.US, "%.0f", addon.price)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        // Expanded options when checked: Quantity (+/-) and Editable Price (Amount)
                                        if (isChecked && selectedAddon != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Quantity Selector
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Qty: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    IconButton(
                                                        onClick = {
                                                            if (selectedAddon.quantity > 1) {
                                                                selectedAddonsMap = selectedAddonsMap + (
                                                                        addon.id to selectedAddon.copy(quantity = selectedAddon.quantity - 1)
                                                                        )
                                                            } else {
                                                                selectedAddonsMap = selectedAddonsMap - addon.id
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease Addon Qty", tint = CaramelWarm)
                                                    }

                                                    Text(
                                                        text = "${selectedAddon.quantity}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp)
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            selectedAddonsMap = selectedAddonsMap + (
                                                                    addon.id to selectedAddon.copy(quantity = selectedAddon.quantity + 1)
                                                                    )
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase Addon Qty", tint = CaramelWarm)
                                                    }
                                                }

                                                // Editable Price / Amount Field
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Amount ₹: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    var priceText by remember(selectedAddon.unitPrice) {
                                                        mutableStateOf(selectedAddon.unitPrice.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() })
                                                    }
                                                    OutlinedTextField(
                                                        value = priceText,
                                                        onValueChange = { newTxt ->
                                                            priceText = newTxt
                                                            val parsed = newTxt.toDoubleOrNull() ?: 0.0
                                                            selectedAddonsMap = selectedAddonsMap + (
                                                                    addon.id to selectedAddon.copy(unitPrice = parsed)
                                                                    )
                                                        },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.width(75.dp),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "Total Addon: ₹${String.format(Locale.US, "%.2f", selectedAddon.totalPrice)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Special Cooking Instructions / Notes
                item {
                    Column {
                        Text(
                            text = "Special Cooking Request (Optional)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick preset buttons for common instructions
                        val quickNotes = listOf("Less Spicy", "Extra Spicy", "No Mayo", "No Onion", "Less Ice")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickNotes.forEach { note ->
                                Surface(
                                    onClick = {
                                        cookingNotes = if (cookingNotes.isBlank()) note else "$cookingNotes, $note"
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = note,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cookingNotes,
                            onValueChange = { cookingNotes = it },
                            placeholder = { Text("e.g. Extra hot, Less sugar...", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // 4. Main Item Quantity Selector
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Item Quantity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = { if (mainItemQuantity > 1) mainItemQuantity-- },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Decrease Quantity"
                                )
                            }
                            Text(
                                text = mainItemQuantity.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = EspressoDark
                            )
                            IconButton(
                                onClick = { mainItemQuantity++ },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = "Increase Quantity"
                                )
                            }
                        }
                        Text(
                            text = if (isComplimentary) "100% Free • ₹0.00 each" else "₹${String.format(Locale.US, "%.2f", singleItemPrice)} each",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isComplimentary) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isComplimentary) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // 5. Complimentary (100% Free / On the House) Section
                item {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isComplimentary) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isComplimentary) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🎁 Complimentary Item",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isComplimentary) Color(0xFF15803D) else EspressoDark
                                        )
                                    }
                                    Text(
                                        text = "Mark 100% Free (On the house / VIP / Testing)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isComplimentary,
                                    onCheckedChange = { isComplimentary = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF16A34A)
                                    )
                                )
                            }

                            if (isComplimentary) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Owner Treat", "VIP Guest", "Tasting", "Courtesy").forEach { reason ->
                                        FilterChip(
                                            selected = complimentaryReason == reason,
                                            onClick = { complimentaryReason = reason },
                                            label = { Text(reason, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF16A34A),
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedVariant,
                        selectedAddonsMap.values.toList(),
                        cookingNotes.trim(),
                        mainItemQuantity,
                        isComplimentary,
                        complimentaryReason
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isComplimentary) Color(0xFF16A34A) else CaramelWarm
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isComplimentary) "Add as Complimentary (FREE)" else (if (isEditMode) "Update Item" else "Add to Cart"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        if (isComplimentary) "₹0.00" else "₹${String.format(Locale.US, "%.2f", totalPrice)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        },
        dismissButton = null
    )
}
