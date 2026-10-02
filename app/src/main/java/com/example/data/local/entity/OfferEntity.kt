package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offer entity supporting Visit-Based Loyalty and Manual Offers.
 * Offers can reward Free Items, Flat Discounts, or Percentage Discounts.
 */
@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey
    val id: String,
    val restaurantId: String,
    val name: String,
    val offerType: String, // "VISIT_BASED" or "MANUAL"
    val rewardType: String, // "FREE_ITEM", "FLAT_DISCOUNT", "PERCENT_DISCOUNT"
    val rewardValue: Double = 0.0, // Discount value (flat amount or %)
    val minBillAmount: Double = 0.0, // Minimum qualifying bill amount
    val applicableOrderTypes: String = "DINE_IN,TAKEAWAY",
    val isActive: Boolean = true,
    // Visit-Based program fields
    val totalVisitsInProgram: Int = 6, // 6, 8, 10, or custom
    val visitNumber: Int = 1, // 1 to totalVisitsInProgram
    val validityDays: Int = 45, // 45 calendar days cycle validity
    // Free item fields
    val freeItemQuantity: Int = 1, // Number of free items allowed
    val eligibleMenuItemIds: String = "[]", // JSON array of menu item IDs eligible for free reward
    val allowSameItemMultipleTimes: Boolean = true, // If true, customer can pick the same item multiple times
    val freeItemName: String = "",
    val maxDiscountCap: Double? = null,
    val cooldownDays: Int = 0, // 0 = unlimited/every visit, 1 = once per day, 30/60 = re-claim after X days, -1 = lifetime once
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val requiredVisitNumber: Int
        get() = visitNumber

    val minimumBillAmount: Double
        get() = minBillAmount
}
