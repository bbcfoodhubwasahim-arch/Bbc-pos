package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Customer Credit Payment Entity.
 * Tracks payments made by credit customers against their outstanding balance.
 */
@Entity(tableName = "customer_payments")
data class CustomerPaymentEntity(
    @PrimaryKey
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val amount: Double,
    val paymentMode: String = "CASH", // "CASH" or "UPI"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
