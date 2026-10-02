package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val displayOrder: Int = 0,
    val isDiscountEligible: Boolean = true,
    val defaultAddonIdsJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
)

fun parseAddonIds(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    val list = mutableListOf<String>()
    try {
        val array = JSONArray(json.trim())
        for (i in 0 until array.length()) {
            val id = array.optString(i)
            if (!id.isNullOrBlank()) list.add(id)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun formatAddonIds(list: List<String>): String {
    val array = JSONArray()
    for (id in list) {
        if (id.isNotBlank()) array.put(id)
    }
    return array.toString()
}
