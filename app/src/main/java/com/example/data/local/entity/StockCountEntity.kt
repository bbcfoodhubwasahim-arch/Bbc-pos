package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Detailed record of an individual batch adjustment made during a stock count.
 * Tracks batch ID, previous remaining, new remaining, adjusted quantity, and rate.
 */
data class BatchAdjustmentRecord(
    val batchId: String,
    val initialQuantity: Double = 0.0,
    val previousRemainingQty: Double = 0.0,
    val newRemainingQty: Double = 0.0,
    val adjustedQty: Double = 0.0, // negative for shortage deduction, positive for excess refill
    val unitRate: Double = 0.0,
    val batchNotes: String = ""
)

/**
 * A single item within a physical stock count session.
 */
data class StockCountItem(
    val inventoryItemId: String,
    val itemName: String,
    val unit: String,
    val systemStock: Double,
    val actualStock: Double,
    val varianceQty: Double, // actualStock - systemStock
    val unitRate: Double,
    val varianceCost: Double, // varianceQty * unitRate
    val batchAdjustments: List<BatchAdjustmentRecord> = emptyList()
)

/**
 * Entity storing a physical stock count session and its variance report.
 * Usable any day/anytime with auto-captured timestamp.
 */
@Entity(tableName = "stock_counts")
data class StockCountEntity(
    @PrimaryKey
    val id: String, // e.g. sc_12345678
    val restaurantId: String,
    val countDate: Long = System.currentTimeMillis(),
    val title: String = "", // e.g. "Stock Count - 15 Sep 2026"
    val totalItemsChecked: Int = 0,
    val totalVarianceCost: Double = 0.0, // Net cost impact (+ or -)
    val isAdjusted: Boolean = false, // Whether "Accept & Adjust Stock" was applied
    val notes: String = "",
    val items: List<StockCountItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
