package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cafe_tables")
data class CafeTableEntity(
    @PrimaryKey
    val id: String,
    val restaurantId: String,
    val name: String,
    val capacity: Int = 4,
    val isOccupied: Boolean = false,
    val activeBillId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
