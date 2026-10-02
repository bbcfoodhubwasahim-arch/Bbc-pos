package com.example.util

import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeIngredient

data class RecipeCostSummary(
    val dineInCost: Double,
    val takeawayExtraCost: Double,
    val takeawayTotalCost: Double
) {
    fun marginPercent(sellingPrice: Double, isTakeaway: Boolean = false): Double {
        val cost = if (isTakeaway) takeawayTotalCost else dineInCost
        return if (sellingPrice > 0.0) {
            ((sellingPrice - cost) / sellingPrice) * 100.0
        } else 0.0
    }
}

object FoodCostCalculator {

    /**
     * Supported base units for Inventory items: "KG", "Nos", "Ltr"
     */
    val SUPPORTED_BASE_UNITS = listOf("KG", "Nos", "Ltr")

    fun normalizeBaseUnit(unit: String?): String {
        if (unit.isNullOrBlank()) return "Nos"
        return when (unit.trim().lowercase()) {
            "kg", "kgs", "kilogram", "kilograms" -> "KG"
            "ltr", "litre", "liter", "litres", "liters", "lt" -> "Ltr"
            "nos", "no", "pcs", "piece", "pieces", "pack", "packets", "cup", "cups" -> "Nos"
            else -> "Nos"
        }
    }

    /**
     * Allowed recipe usage units for an inventory item based on its base unit.
     */
    fun getAvailableUsageUnits(baseUnit: String): List<String> {
        return when (normalizeBaseUnit(baseUnit)) {
            "KG" -> listOf("gm", "KG")
            "Ltr" -> listOf("ml", "Ltr")
            "Nos" -> listOf("Nos")
            else -> listOf("Nos")
        }
    }

    /**
     * Default usage unit when picking an inventory item.
     */
    fun getDefaultUsageUnit(baseUnit: String): String {
        return when (normalizeBaseUnit(baseUnit)) {
            "KG" -> "gm"
            "Ltr" -> "ml"
            "Nos" -> "Nos"
            else -> "Nos"
        }
    }

    /**
     * Multiplier to convert from usageUnit to base purchase unit.
     * e.g. 100 gm -> 100 * 0.001 = 0.1 KG
     * e.g. 200 ml -> 200 * 0.001 = 0.2 Ltr
     * e.g. 1 Nos  -> 1 * 1.0 = 1 Nos
     */
    fun getConversionFactorToBase(usageUnit: String, baseUnit: String): Double {
        val u = usageUnit.trim().lowercase()
        return when (u) {
            "gm", "g", "grams", "gram" -> 0.001
            "kg", "kgs", "kilogram" -> 1.0
            "ml", "milliliter", "millilitre" -> 0.001
            "ltr", "lt", "liter", "litre" -> 1.0
            "nos", "pcs", "piece" -> 1.0
            else -> 1.0
        }
    }

    /**
     * Calculates line cost for a single recipe ingredient:
     * Line Cost = Quantity in base unit * base unit purchase price.
     */
    fun calculateLineCost(
        quantity: Double,
        usageUnit: String,
        inventoryItem: InventoryItemEntity?
    ): Double {
        if (inventoryItem == null) return 0.0
        val factor = getConversionFactorToBase(usageUnit, inventoryItem.unit)
        val baseQuantity = quantity * factor
        return baseQuantity * inventoryItem.purchasePrice
    }

    /**
     * Calculates line cost directly for a RecipeIngredient.
     */
    fun calculateIngredientCost(
        ingredient: RecipeIngredient,
        inventoryMap: Map<String, InventoryItemEntity>
    ): Double {
        val item = inventoryMap[ingredient.inventoryItemId] ?: return 0.0
        return calculateLineCost(ingredient.quantity, ingredient.usageUnit, item)
    }

    /**
     * Computes Dine-In and Takeaway food cost for a recipe.
     * - Dine-In Cost: sum of Common Items (!isTakeawayExtra)
     * - Takeaway Cost: sum of Common Items + Takeaway Extra Items
     */
    fun calculateRecipeCost(
        recipe: RecipeEntity?,
        inventoryMap: Map<String, InventoryItemEntity>
    ): RecipeCostSummary {
        if (recipe == null || recipe.ingredients.isEmpty()) {
            return RecipeCostSummary(0.0, 0.0, 0.0)
        }

        var dineInTotal = 0.0
        var takeawayExtraTotal = 0.0
        var takeawayTotal = 0.0

        for (ingredient in recipe.ingredients) {
            val cost = calculateIngredientCost(ingredient, inventoryMap)
            val mode = ingredient.applyMode.uppercase()
            when (mode) {
                "DINE_IN_ONLY" -> {
                    dineInTotal += cost
                }
                "TAKEAWAY_ONLY" -> {
                    takeawayExtraTotal += cost
                    takeawayTotal += cost
                }
                else -> { // "BOTH" or common
                    if (ingredient.isTakeawayExtra) {
                        takeawayExtraTotal += cost
                        takeawayTotal += cost
                    } else {
                        dineInTotal += cost
                        takeawayTotal += cost
                    }
                }
            }
        }

        return RecipeCostSummary(
            dineInCost = dineInTotal,
            takeawayExtraCost = takeawayExtraTotal,
            takeawayTotalCost = takeawayTotal
        )
    }

    /**
     * Computes food cost for a combo by summing the recipe food costs
     * of each sub-item chosen by the customer.
     */
    fun calculateComboCost(
        subItemNames: List<String>,
        menuItems: List<MenuItemEntity>,
        recipesMap: Map<String, RecipeEntity>,
        inventoryMap: Map<String, InventoryItemEntity>
    ): RecipeCostSummary {
        var totalDineIn = 0.0
        var totalTakeawayExtra = 0.0

        for (name in subItemNames) {
            val trimmed = name.trim()
            val menuItem = menuItems.find { it.name.equals(trimmed, ignoreCase = true) }
            if (menuItem != null) {
                val recipe = recipesMap[menuItem.id]
                if (recipe != null) {
                    val summary = calculateRecipeCost(recipe, inventoryMap)
                    totalDineIn += summary.dineInCost
                    totalTakeawayExtra += summary.takeawayExtraCost
                }
            }
        }

        return RecipeCostSummary(
            dineInCost = totalDineIn,
            takeawayExtraCost = totalTakeawayExtra,
            takeawayTotalCost = totalDineIn + totalTakeawayExtra
        )
    }

    /**
     * Profit margin percentage = ((Selling Price - Food Cost) / Selling Price) * 100
     */
    fun calculateProfitMargin(sellingPrice: Double, foodCost: Double): Double {
        return if (sellingPrice > 0.0) {
            ((sellingPrice - foodCost) / sellingPrice) * 100.0
        } else 0.0
    }
}
