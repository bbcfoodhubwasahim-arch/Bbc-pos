package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single item inside a Bill.
 * Contains dish name, rate, quantity, and item total.
 * Strictly no tax/GST.
 */
data class BillItem(
    val dishId: String,
    val dishName: String,
    val unitPrice: Double,
    val quantity: Int,
    val totalPrice: Double = unitPrice * quantity,
    val notes: String = "",
    val isFree: Boolean = false,
    val selectedVariant: String = "",
    val selectedAddonsJson: String = "[]",
    val cookingNotes: String = ""
) {
    fun getFormattedDisplayName(): String {
        return if (selectedVariant.isNotBlank()) {
            "$dishName ($selectedVariant)"
        } else {
            dishName
        }
    }

    fun getAddonsList(): List<MenuItemAddon> {
        return parseAddons(selectedAddonsJson)
    }

    fun getSelectedAddonsList(): List<SelectedAddon> {
        return parseSelectedAddons(selectedAddonsJson)
    }

    fun calculateAddonsTotal(): Double {
        return getSelectedAddonsList().sumOf { it.totalPrice }
    }

    fun getAddonsSummary(): String {
        val selectedAddons = getSelectedAddonsList()
        if (selectedAddons.isNotEmpty()) {
            return selectedAddons.joinToString(", ") { "+${it.name} (${it.quantity}x ₹${String.format(java.util.Locale.US, "%.0f", it.unitPrice)})" }
        }
        val addons = getAddonsList()
        if (addons.isEmpty()) return ""
        return addons.joinToString(", ") { "+${it.name}" }
    }
}

/**
 * Bill entity representing a cafe sale.
 * - Order types: DINE_IN, TAKEAWAY (strictly only these two)
 * - Payment methods: CASH, UPI (strictly only these two)
 * - Discount: NONE, FLAT, PERCENT
 * - Strictly NO GST / tax fields anywhere.
 * - Supports backdated bill generation & editing.
 */
@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey
    val id: String,
    val billNumber: String,
    val restaurantId: String,
    val restaurantName: String,
    val orderType: String, // "DINE_IN" or "TAKEAWAY"
    val tableId: String? = null,
    val tableName: String? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val items: List<BillItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discountType: String = "NONE", // "NONE", "FLAT", "PERCENT"
    val discountValue: Double = 0.0,
    val discountAmount: Double = 0.0,
    val totalAmount: Double = 0.0, // Subtotal - discountAmount (Zero GST anywhere)
    val paymentMethod: String = "CASH", // "CASH", "UPI", "CREDIT", "SPLIT"
    val cashAmount: Double = 0.0, // Amount paid in cash (for CASH or SPLIT)
    val upiAmount: Double = 0.0, // Amount paid in UPI (for UPI or SPLIT)
    val isSettled: Boolean = true,
    val isStockDeducted: Boolean = false, // Automatically deducted from inventory on KOT/Settle
    val totalFoodCost: Double = 0.0, // Dish-level food cost based on linked recipes
    val appliedRewardType: String = "NONE", // "NONE", "VISIT_REWARD", "MANUAL_OFFER", "POINTS_REDEMPTION"
    val appliedOfferId: String? = null,
    val appliedOfferName: String = "",
    val rewardPointsEarned: Int = 0,
    val pointsRedeemed: Int = 0,
    val rewardPointsRedeemed: Int = 0,
    val giftPointsRedeemed: Int = 0,
    val isVoided: Boolean = false,
    val status: String = "SETTLED", // "SETTLED", "CANCELLED", "VOIDED"
    val isCancelled: Boolean = false,
    val cancellationReason: String = "",
    val cancelledAt: Long? = null,
    val billTimestamp: Long = System.currentTimeMillis(), // Custom timestamp for backdated bills
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
