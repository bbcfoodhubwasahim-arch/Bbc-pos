package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a specific purchase or addition batch of an inventory ingredient.
 * Supports FIFO (First In, First Out) inventory costing.
 */
@Entity(tableName = "inventory_batches")
data class InventoryBatchEntity(
    @PrimaryKey
    val id: String, // e.g. batch_12345678
    val restaurantId: String,
    val inventoryItemId: String,
    val itemName: String,
    val initialQuantity: Double,
    val remainingQuantity: Double,
    val purchaseRate: Double, // Price per unit at purchase time
    val unit: String, // KG, Nos, Ltr
    val batchType: String = "PURCHASE", // PURCHASE, OPENING_STOCK, ADJUSTMENT, RESTORATION
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isConsumed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = timestamp,
    val createdByUserId: String = "",
    val referenceTransactionId: String = "",
    val status: String = "ACTIVE" // ACTIVE, CONSUMED, ARCHIVED
) {
    // User requested FIFO naming properties
    val quantityAdded: Double get() = initialQuantity
    val ratePerUnit: Double get() = purchaseRate
    val addedAt: Long get() = timestamp

    // GRN Helper properties parsed safely from notes to avoid database migrations and cloud sync errors
    val paymentStatus: String get() = extractFromNotes("GRN_PAY_STATUS", "PAID")
    val vendorName: String get() = extractFromNotes("GRN_VENDOR", "")
    val invoiceNo: String get() = extractFromNotes("GRN_INVOICE", "")
    val paymentMethod: String get() = extractFromNotes("GRN_METHOD", "CASH")
    val userNotes: String get() = extractFromNotes("GRN_USER_NOTES", notes)

    private fun extractFromNotes(key: String, default: String): String {
        if (!notes.contains(key + ":")) return default
        val parts = notes.split(" | ")
        for (part in parts) {
            val trimPart = part.trim()
            if (trimPart.startsWith(key + ":")) {
                return trimPart.substring(key.length + 1).trim()
            }
        }
        return default
    }

    fun isAvailable(): Boolean = remainingQuantity > 0.000001 && status != "ARCHIVED" && !isConsumed
}

/**
 * Tracks batch-level ingredient deductions for a bill.
 * Enables accurate FIFO food costing and precise stock restoration if an order is cancelled/refunded.
 */
@Entity(tableName = "bill_batch_deductions")
data class BillBatchDeductionEntity(
    @PrimaryKey
    val id: String,
    val billId: String,
    val inventoryItemId: String,
    val batchId: String? = null, // null if drawn from negative deficit
    val quantityDeducted: Double,
    val rate: Double,
    val cost: Double,
    val isNegativeStock: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val menuItemId: String = "",
    val createdAt: Long = timestamp,
    val status: String = "ACTIVE", // ACTIVE, REFUNDED
    val referenceId: String = ""
)
