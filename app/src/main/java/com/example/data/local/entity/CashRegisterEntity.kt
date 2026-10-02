package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cash_registers")
data class CashRegisterEntity(
    @PrimaryKey
    val id: String, // e.g. "REG-2026-09-11"
    val restaurantId: String,
    val dateString: String, // YYYY-MM-DD
    val openingCash: Double,
    val cashSales: Double = 0.0,
    val cashExpenses: Double = 0.0,
    val cashAdded: Double = 0.0,
    val cashWithdrawn: Double = 0.0,
    val expectedCash: Double = openingCash + cashSales - cashExpenses + cashAdded - cashWithdrawn,
    val closingCashActual: Double? = null,
    val difference: Double? = null, // actual - expected
    val status: String = "OPEN", // "OPEN", "CLOSED"
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
