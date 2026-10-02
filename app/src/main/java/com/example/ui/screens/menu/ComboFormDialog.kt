package com.example.ui.screens.menu

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ComboEntity
import com.example.data.local.entity.ComboSlot
import com.example.ui.theme.CaramelWarm
import com.example.ui.theme.CreamSurfaceVariant
import com.example.ui.theme.EspressoDark
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComboFormDialog(
    comboToEdit: ComboEntity?,
    categories: List<CategoryEntity>,
    restaurantId: String,
    onDismiss: () -> Unit,
    onSave: (ComboEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(comboToEdit?.name ?: "") }
    var priceStr by remember { mutableStateOf(comboToEdit?.price?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var badge by remember { mutableStateOf(comboToEdit?.badge ?: "") }
    var description by remember { mutableStateOf(comboToEdit?.description ?: "") }
    var isActive by remember { mutableStateOf(comboToEdit?.isActive ?: true) }

    // Slots state list
    var slots by remember {
        mutableStateOf(
            comboToEdit?.slots?.map { it.copy() } ?: listOf(
                ComboSlot(
                    id = UUID.randomUUID().toString(),
                    label = "Slot 1",
                    categoryIds = if (categories.isNotEmpty()) listOf(categories.first().id) else emptyList(),
                    quantity = 1
                )
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Header
                Surface(
                    color = CreamSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CaramelWarm, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Celebration,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (comboToEdit == null) "Add New Combo" else "Edit Combo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = EspressoDark
                                )
                                Text(
                                    text = "Bundle meals with customized item choices",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Combo Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Combo Name *") },
                        placeholder = { Text("e.g. Single Life Combo, Couple Meal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 2. Combo Fixed Price
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Combo Fixed Price (₹) *") },
                        placeholder = { Text("e.g. 349") },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 3. Badge / Tagline
                    OutlinedTextField(
                        value = badge,
                        onValueChange = { badge = it },
                        label = { Text("Badge / Tagline (Optional)") },
                        placeholder = { Text("e.g. Treat Yourself, Best Value, Weekend Special") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 4. Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("e.g. Any One Pizza + One Fries + One Cold Coffee") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 5. Active Toggle
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isActive) "Active in Billing Menu" else "Inactive / Hidden",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isActive) CaramelWarm else MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = if (isActive) "Cashiers can see and order this combo" else "Will not appear in Billing screen",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isActive,
                                onCheckedChange = { isActive = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = CaramelWarm, checkedTrackColor = CaramelWarm.copy(alpha = 0.4f))
                            )
                        }
                    }

                    HorizontalDivider()

                    // Quick Category Selection Chips for instant 1-tap slot generation
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = CaramelWarm, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Quick Category Slots (1-Tap Auto Slot)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = EspressoDark
                                    )
                                }
                            }
                            Text(
                                "Tap any category to auto-generate a selectable slot (e.g. Any 1 Pizza, Any 1 Coffee)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )

                            if (categories.isEmpty()) {
                                Text("No categories found in menu", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    categories.forEach { cat ->
                                        val isAlreadySlot = slots.any { it.categoryIds.contains(cat.id) && it.categoryIds.size == 1 }
                                        FilterChip(
                                            selected = isAlreadySlot,
                                            onClick = {
                                                if (isAlreadySlot) {
                                                    // Remove slot that was created solely for this category
                                                    slots = slots.filterNot { it.categoryIds.contains(cat.id) && it.categoryIds.size == 1 }
                                                } else {
                                                    // Add auto slot for this category
                                                    val newSlot = ComboSlot(
                                                        id = UUID.randomUUID().toString(),
                                                        label = "Any 1 ${cat.name}",
                                                        categoryIds = listOf(cat.id),
                                                        quantity = 1
                                                    )
                                                    slots = slots + newSlot
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = if (isAlreadySlot) "✓ ${cat.name}" else "+ ${cat.name}",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isAlreadySlot) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = CaramelWarm,
                                                selectedLabelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Slots Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Configured Slots (${slots.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = EspressoDark
                            )
                            Text(
                                text = "Define required items & allowed categories for each choice",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                val newSlotNum = slots.size + 1
                                slots = slots + ComboSlot(
                                    id = UUID.randomUUID().toString(),
                                    label = "Slot $newSlotNum",
                                    categoryIds = if (categories.isNotEmpty()) listOf(categories.first().id) else emptyList(),
                                    quantity = 1
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Custom Slot", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (slots.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No slots added yet. Tap '+ Add Slot' to configure items.",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        slots.forEachIndexed { index, slot ->
                            SlotEditorCard(
                                index = index,
                                totalSlots = slots.size,
                                slot = slot,
                                allCategories = categories,
                                onUpdate = { updatedSlot ->
                                    val updated = slots.toMutableList()
                                    updated[index] = updatedSlot
                                    slots = updated
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        val updated = slots.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index - 1]
                                        updated[index - 1] = temp
                                        slots = updated
                                    }
                                },
                                onMoveDown = {
                                    if (index < slots.size - 1) {
                                        val updated = slots.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index + 1]
                                        updated[index + 1] = temp
                                        slots = updated
                                    }
                                },
                                onDelete = {
                                    slots = slots.filterIndexed { i, _ -> i != index }
                                }
                            )
                        }
                    }
                }

                // Bottom Actions
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    Toast.makeText(context, "Please enter a combo name", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val price = priceStr.toDoubleOrNull()
                                if (price == null || price <= 0.0) {
                                    Toast.makeText(context, "Please enter a valid price", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (slots.isEmpty()) {
                                    Toast.makeText(context, "Please add at least one slot", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                for ((idx, sl) in slots.withIndex()) {
                                    if (sl.categoryIds.isEmpty()) {
                                        Toast.makeText(context, "Slot ${idx + 1} must have at least one category selected", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    if (sl.quantity < 1) {
                                        Toast.makeText(context, "Slot ${idx + 1} quantity must be at least 1", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                }

                                val finalCombo = ComboEntity(
                                    id = comboToEdit?.id ?: UUID.randomUUID().toString(),
                                    restaurantId = restaurantId,
                                    name = name.trim(),
                                    price = price,
                                    badge = badge.trim(),
                                    description = description.trim(),
                                    isActive = isActive,
                                    slots = slots,
                                    createdAt = comboToEdit?.createdAt ?: System.currentTimeMillis()
                                )
                                onSave(finalCombo)
                            },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (comboToEdit == null) "Create Combo" else "Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotEditorCard(
    index: Int,
    totalSlots: Int,
    slot: ComboSlot,
    allCategories: List<CategoryEntity>,
    onUpdate: (ComboSlot) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Slot number, Move buttons, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CaramelWarm.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Slot ${index + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = CaramelWarm
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Move Up
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            modifier = Modifier.size(18.dp),
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }

                    // Move Down
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalSlots - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            modifier = Modifier.size(18.dp),
                            tint = if (index < totalSlots - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Delete Slot
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Slot",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Slot Label & Auto-Suggest
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = slot.label,
                    onValueChange = { onUpdate(slot.copy(label = it)) },
                    label = { Text("Slot Label *") },
                    placeholder = { Text("e.g. Any One Pizza, Two Cold Drinks") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Auto suggest button
                IconButton(
                    onClick = {
                        val selectedNames = allCategories.filter { it.id in slot.categoryIds }.map { it.name }
                        val suggestedLabel = when {
                            selectedNames.isEmpty() -> "Slot ${index + 1}"
                            slot.quantity == 1 && selectedNames.size == 1 -> "Any One ${selectedNames.first()}"
                            slot.quantity == 1 -> "Choice of ${selectedNames.take(2).joinToString(" or ")}"
                            else -> "${slot.quantity}x ${selectedNames.take(2).joinToString(" / ")}"
                        }
                        onUpdate(slot.copy(label = suggestedLabel))
                    }
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = "Auto Suggest Label",
                        tint = CaramelWarm,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quantity selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Customer Must Pick",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Exact quantity of items from this slot",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (slot.quantity > 1) {
                                onUpdate(slot.copy(quantity = slot.quantity - 1))
                            }
                        },
                        enabled = slot.quantity > 1,
                        modifier = Modifier
                            .size(32.dp)
                            .background(CreamSurfaceVariant, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "${slot.quantity} ${if (slot.quantity == 1) "item" else "items"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    IconButton(
                        onClick = {
                            onUpdate(slot.copy(quantity = slot.quantity + 1))
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(CreamSurfaceVariant, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Categories multi-select checklist
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Allowed Categories (multi-select)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Menu items from checked categories will be available in this slot",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (allCategories.isEmpty()) {
                    Text(
                        "No categories found in Menu Management.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allCategories.forEach { category ->
                            val isChecked = category.id in slot.categoryIds
                            FilterChip(
                                selected = isChecked,
                                onClick = {
                                    val newCatIds = if (isChecked) {
                                        slot.categoryIds - category.id
                                    } else {
                                        slot.categoryIds + category.id
                                    }
                                    onUpdate(slot.copy(categoryIds = newCatIds))
                                },
                                label = { Text(category.name, fontSize = 12.sp) },
                                leadingIcon = {
                                    if (isChecked) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CaramelWarm.copy(alpha = 0.2f),
                                    selectedLabelColor = CaramelWarm,
                                    selectedLeadingIconColor = CaramelWarm
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
