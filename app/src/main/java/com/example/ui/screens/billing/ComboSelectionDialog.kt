package com.example.ui.screens.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.entity.ComboEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.ui.theme.CaramelWarm
import com.example.ui.theme.CreamSurfaceVariant
import com.example.ui.theme.EspressoDark
import java.util.Locale

@Composable
fun ComboSelectionDialog(
    combo: ComboEntity,
    menuItems: List<MenuItemEntity>,
    onDismiss: () -> Unit,
    onConfirm: (descriptions: List<String>, selectedDishQuantities: Map<String, Int>) -> Unit
) {
    // Map of slotId to (dishId -> quantity selected)
    val slotSelections = remember {
        mutableStateMapOf<String, Map<String, Int>>().apply {
            combo.slots.forEach { slot ->
                put(slot.id, emptyMap())
            }
        }
    }

    var currentSlotIndex by remember { mutableStateOf(0) }
    val currentSlot = combo.slots.getOrNull(currentSlotIndex)

    // Check completion status for each slot
    val isSlotComplete: (String, Int) -> Boolean = { slotId, requiredQty ->
        val selectedQty = slotSelections[slotId]?.values?.sum() ?: 0
        selectedQty == requiredQty
    }

    val allSlotsComplete by remember {
        derivedStateOf {
            combo.slots.all { slot ->
                val totalInSlot = slotSelections[slot.id]?.values?.sum() ?: 0
                totalInSlot == slot.quantity
            }
        }
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
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header with Combo Info
                Surface(
                    color = CreamSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = combo.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
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
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "₹${String.format(Locale.US, "%.0f", combo.price)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = CaramelWarm
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }

                // Slot Tabs Row (Step-by-step indicator)
                if (combo.slots.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = currentSlotIndex,
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = CaramelWarm
                    ) {
                        combo.slots.forEachIndexed { index, slot ->
                            val selectedCount = slotSelections[slot.id]?.values?.sum() ?: 0
                            val isDone = selectedCount == slot.quantity

                            Tab(
                                selected = currentSlotIndex == index,
                                onClick = { currentSlotIndex = index },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = slot.label.ifBlank { "Slot ${index + 1}" },
                                            fontWeight = if (currentSlotIndex == index) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        Surface(
                                            color = if (isDone) Color(0xFF2E7D32) else if (selectedCount > 0) CaramelWarm else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = if (isDone) "✓" else "$selectedCount/${slot.quantity}",
                                                color = if (isDone || selectedCount > 0) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Current Slot Instructions Header
                currentSlot?.let { slot ->
                    val selectedCount = slotSelections[slot.id]?.values?.sum() ?: 0
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Choose ${slot.quantity} ${if (slot.quantity == 1) "item" else "items"} from below:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EspressoDark
                            )

                            Text(
                                text = "$selectedCount of ${slot.quantity} picked",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedCount == slot.quantity) Color(0xFF2E7D32) else CaramelWarm
                            )
                        }
                    }

                    // Dynamically pulled menu items for this slot's selected categories
                    val slotItems = remember(slot, menuItems) {
                        menuItems.filter { it.categoryId in slot.categoryIds }
                    }

                    if (slotItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Fastfood,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No menu items found in the selected categories for this slot.",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        // Display items in a GRID layout (same card size, spacing, and look as Billing screen)
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
                        ) {
                            items(slotItems, key = { it.id }) { item ->
                                val slotMap = slotSelections[slot.id]
                                val itemQty = slotMap?.get(item.id) ?: 0
                                val totalSlotPicked = slotMap?.values?.sum() ?: 0
                                val canAddMore = totalSlotPicked < slot.quantity

                                ComboSlotItemCard(
                                    item = item,
                                    quantity = itemQty,
                                    onIncrement = {
                                        if (canAddMore) {
                                            val map = (slotSelections[slot.id] ?: emptyMap()).toMutableMap()
                                            map[item.id] = (map[item.id] ?: 0) + 1
                                            slotSelections[slot.id] = map
                                        }
                                    },
                                    onDecrement = {
                                        if (itemQty > 0) {
                                            val map = (slotSelections[slot.id] ?: emptyMap()).toMutableMap()
                                            if (itemQty == 1) {
                                                map.remove(item.id)
                                            } else {
                                                map[item.id] = itemQty - 1
                                            }
                                            slotSelections[slot.id] = map
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Bottom Fixed Confirmation Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Next Slot or Confirm Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (currentSlotIndex > 0) {
                                OutlinedButton(
                                    onClick = { currentSlotIndex-- },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Previous Slot")
                                }
                            }

                            if (currentSlotIndex < combo.slots.size - 1) {
                                Button(
                                    onClick = { currentSlotIndex++ },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Next Slot", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (allSlotsComplete) {
                                            // Collect formatted list of items and dish IDs
                                            val selectedDescriptions = mutableListOf<String>()
                                            val selectedDishQuantities = mutableMapOf<String, Int>()
                                            combo.slots.forEach { slot ->
                                                val slotMap = slotSelections[slot.id] ?: emptyMap()
                                                slotMap.forEach { (dishId, qty) ->
                                                    val dish = menuItems.find { it.id == dishId }
                                                    if (dish != null && qty > 0) {
                                                        if (qty > 1) {
                                                            selectedDescriptions.add("${qty}x ${dish.name}")
                                                        } else {
                                                            selectedDescriptions.add(dish.name)
                                                        }
                                                        selectedDishQuantities[dishId] = (selectedDishQuantities[dishId] ?: 0) + qty
                                                    }
                                                }
                                            }
                                            onConfirm(selectedDescriptions, selectedDishQuantities)
                                        }
                                    },
                                    enabled = allSlotsComplete,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2E7D32),
                                        disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (allSlotsComplete) {
                                            "Add to Order • ₹${String.format(Locale.US, "%.0f", combo.price)}"
                                        } else {
                                            "Pick all required items to confirm"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
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

/**
 * Item Card in the Combo Selection Grid — matching the main Billing screen's MenuItemCard look and dimensions.
 */
@Composable
private fun ComboSlotItemCard(
    item: MenuItemEntity,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val isSelected = quantity > 0

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CaramelWarm.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CaramelWarm else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onIncrement() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Category tag and quantity badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.categoryName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CaramelWarm)
                            .size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$quantity",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Dish Photo (if available)
            if (!item.imageUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                AsyncImage(
                    model = item.imageUri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dish Name
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = EspressoDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom action row: Included tag & +/- buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Included",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CaramelWarm
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Decrease",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = onIncrement,
                        modifier = Modifier
                            .size(28.dp)
                            .background(CaramelWarm, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
