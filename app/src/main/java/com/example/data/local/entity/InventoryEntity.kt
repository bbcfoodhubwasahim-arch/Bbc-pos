package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey
    val id: String,
    val restaurantId: String,
    val name: String,
    val unit: String, // KG, Nos, Ltr
    val purchasePrice: Double = 0.0, // Base purchase price per unit (e.g. per KG, per Nos, per Ltr)
    val openingStock: Double,
    val currentStock: Double,
    val lowStockThreshold: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_additions")
data class StockAdditionEntity(
    @PrimaryKey
    val id: String,
    val inventoryItemId: String,
    val itemName: String,
    val quantityAdded: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Audit ledger for all inventory movements.
 * Supported types: OPENING_STOCK, ADD, REMOVE, CORRECTION, DAMAGE, WASTAGE, SALE, REFUND.
 */
@Entity(tableName = "stock_transactions")
data class StockTransactionEntity(
    @PrimaryKey
    val id: String,
    val restaurantId: String = "",
    val inventoryItemId: String,
    val itemName: String = "",
    val transactionType: String, // OPENING_STOCK, ADD, REMOVE, CORRECTION, DAMAGE, WASTAGE, SALE, REFUND
    val quantity: Double,
    val unitRate: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val batchId: String? = null,
    val billId: String? = null,
    val referenceId: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
