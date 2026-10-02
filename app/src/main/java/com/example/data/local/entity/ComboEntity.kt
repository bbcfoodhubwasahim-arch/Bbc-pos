package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A slot inside a combo (e.g., Any 1 Pizza from [Classic Pizza, Supreme Pizza]).
 */
data class ComboSlot(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val categoryIds: List<String> = emptyList(),
    val quantity: Int = 1
)

/**
 * Combo Entity representing a bundle with configurable item slots.
 */
@Entity(tableName = "combos")
data class ComboEntity(
    @PrimaryKey
    val id: String,
    val restaurantId: String,
    val name: String,
    val price: Double,
    val badge: String = "",
    val description: String = "",
    val isActive: Boolean = true,
    val slots: List<ComboSlot> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
