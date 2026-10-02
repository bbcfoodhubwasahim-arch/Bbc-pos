package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "addons")
data class AddonDefinitionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double = 0.0,
    val selectionType: String = "MULTI", // "SINGLE" or "MULTI"
    val groupName: String = "Add-ons",
    val isRequired: Boolean = false,
    val minCount: Int = 0,
    val maxCount: Int = Int.MAX_VALUE,
    val isActive: Boolean = true,
    val inventoryItemId: String? = null,
    val inventoryItemName: String? = null,
    val inventoryQty: Double = 1.0,
    val usageUnit: String = "Nos",
    val categoryId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
