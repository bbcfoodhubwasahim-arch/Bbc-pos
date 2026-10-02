package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single ingredient in a recipe.
 * Linked to an InventoryItemEntity by inventoryItemId.
 * isTakeawayExtra = false -> Common Item (used for both Dine-In and Takeaway)
 * isTakeawayExtra = true  -> Takeaway Extra Item (only used when order is Takeaway, e.g. packaging box, sachet)
 */
data class RecipeIngredient(
    val inventoryItemId: String,
    val inventoryItemName: String,
    val quantity: Double, // Quantity in usageUnit (e.g. 15.0 gm, 200.0 ml, 1.0 Nos)
    val usageUnit: String, // "gm", "KG", "ml", "Ltr", "Nos"
    val isTakeawayExtra: Boolean = false,
    val applyMode: String = if (isTakeawayExtra) "TAKEAWAY_ONLY" else "BOTH" // "BOTH", "DINE_IN_ONLY", "TAKEAWAY_ONLY"
)

/**
 * Recipe entity linked 1:1 to a MenuItemEntity.
 * Stores list of ingredients for food costing and automatic inventory deduction.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val menuItemId: String,
    val restaurantId: String,
    val menuItemName: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
