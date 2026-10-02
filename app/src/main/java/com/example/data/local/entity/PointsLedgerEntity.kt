package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit ledger for all points movements: Earned, Redeemed, Gifted, Expired, Reversed.
 * Historical transactions are never deleted.
 */
@Entity(tableName = "points_ledger")
data class PointsLedgerEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val customerPhone: String = "",
    val customerName: String = "",
    val transactionType: String, // "EARNED", "REDEEMED", "GIFTED", "EXPIRED", "REVERSED"
    val pointsAmount: Int, // Positive for addition/credit, negative for deduction/redemption
    val balanceType: String, // "REWARD", "GIFT", "COMBINED"
    val billId: String? = null,
    val billNumber: String? = null,
    val expiryDate: Long? = null,
    val rewardPointsBalanceAfter: Int = 0,
    val giftPointsBalanceAfter: Int = 0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
