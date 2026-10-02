package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Points Batch representing an earned (Reward Points) or gifted (Gift Points) batch.
 * Expiring points first (FIFO/Earliest-Expiry) logic is applied when redeeming.
 * Every batch expires 60 days from its own earn/gift date.
 */
@Entity(tableName = "points_batches")
data class PointsBatchEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val customerPhone: String = "",
    val customerName: String = "",
    val batchType: String, // "REWARD" or "GIFT"
    val initialPoints: Int,
    val remainingPoints: Int,
    val earnDate: Long = System.currentTimeMillis(),
    val expiryDate: Long = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000L, // 60 days validity
    val billId: String? = null,
    val billNumber: String? = null,
    val notes: String = "",
    val isExpired: Boolean = false,
    val reminderSent: Boolean = false,
    val reminderSentDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
