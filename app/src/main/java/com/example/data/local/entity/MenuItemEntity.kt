package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MenuItemVariant(
    val name: String, // e.g., "Small", "Medium", "Large"
    val price: Double
)

data class MenuItemAddon(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "Extra Cheese", "Ice Cream Scoop"
    val price: Double,
    val inventoryItemId: String? = null, // Optional linked raw ingredient ID for stock deduction
    val inventoryItemName: String? = null,
    val inventoryQty: Double = 1.0,
    val usageUnit: String = "Nos"
)

data class SelectedAddon(
    val id: String = UUID.randomUUID().toString(),
    val addonId: String = UUID.randomUUID().toString(),
    val name: String,
    val unitPrice: Double,
    val quantity: Int = 1,
    val groupName: String = "Add-ons",
    val inventoryItemId: String? = null,
    val inventoryQty: Double = 1.0,
    val usageUnit: String = "Nos"
) {
    val totalPrice: Double get() = unitPrice * quantity
}

fun parseVariants(json: String?): List<MenuItemVariant> {
    if (json.isNullOrBlank()) return emptyList()
    val list = mutableListOf<MenuItemVariant>()
    try {
        val array = JSONArray(json.trim())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", "")
            if (name.isNotBlank()) {
                list.add(
                    MenuItemVariant(
                        name = name,
                        price = obj.optDouble("price", 0.0)
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun formatVariants(list: List<MenuItemVariant>): String {
    val array = JSONArray()
    for (v in list) {
        val obj = JSONObject()
        obj.put("name", v.name)
        obj.put("price", v.price)
        array.put(obj)
    }
    return array.toString()
}

fun parseAddons(json: String?): List<MenuItemAddon> {
    if (json.isNullOrBlank()) return emptyList()
    val list = mutableListOf<MenuItemAddon>()
    try {
        val array = JSONArray(json.trim())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", "")
            if (name.isNotBlank()) {
                list.add(
                    MenuItemAddon(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = name,
                        price = obj.optDouble("price", 0.0),
                        inventoryItemId = obj.optString("inventoryItemId").takeIf { it.isNotBlank() },
                        inventoryItemName = obj.optString("inventoryItemName").takeIf { it.isNotBlank() },
                        inventoryQty = obj.optDouble("inventoryQty", 1.0),
                        usageUnit = obj.optString("usageUnit", "Nos")
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun formatAddons(list: List<MenuItemAddon>): String {
    val array = JSONArray()
    for (a in list) {
        val obj = JSONObject()
        obj.put("id", a.id)
        obj.put("name", a.name)
        obj.put("price", a.price)
        obj.put("inventoryItemId", a.inventoryItemId ?: "")
        obj.put("inventoryItemName", a.inventoryItemName ?: "")
        obj.put("inventoryQty", a.inventoryQty)
        obj.put("usageUnit", a.usageUnit)
        array.put(obj)
    }
    return array.toString()
}

fun parseSelectedAddons(json: String?): List<SelectedAddon> {
    if (json.isNullOrBlank()) return emptyList()
    val list = mutableListOf<SelectedAddon>()
    try {
        val array = JSONArray(json.trim())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", "")
            if (name.isNotBlank()) {
                val qty = obj.optInt("quantity", obj.optInt("qty", 1)).coerceAtLeast(1)
                val unitPrice = if (obj.has("unitPrice")) obj.optDouble("unitPrice", 0.0) else obj.optDouble("price", 0.0)
                list.add(
                    SelectedAddon(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        addonId = obj.optString("addonId", UUID.randomUUID().toString()),
                        name = name,
                        unitPrice = unitPrice,
                        quantity = qty,
                        groupName = obj.optString("groupName", "Add-ons"),
                        inventoryItemId = obj.optString("inventoryItemId").takeIf { it.isNotBlank() },
                        inventoryQty = obj.optDouble("inventoryQty", 1.0),
                        usageUnit = obj.optString("usageUnit", "Nos")
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun formatSelectedAddons(list: List<SelectedAddon>): String {
    val array = JSONArray()
    for (sa in list) {
        val obj = JSONObject()
        obj.put("id", sa.id)
        obj.put("addonId", sa.addonId)
        obj.put("name", sa.name)
        obj.put("unitPrice", sa.unitPrice)
        obj.put("quantity", sa.quantity)
        obj.put("groupName", sa.groupName)
        obj.put("inventoryItemId", sa.inventoryItemId ?: "")
        obj.put("inventoryQty", sa.inventoryQty)
        obj.put("usageUnit", sa.usageUnit)
        array.put(obj)
    }
    return array.toString()
}

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val name: String,
    val price: Double,
    val isAvailable: Boolean = true,
    val isDiscountEligible: Boolean = true,
    val imageUri: String? = null,
    val variantsJson: String = "[]",
    val addonsJson: String = "[]",
    val excludedAddonIdsJson: String = "[]",
    val itemAddonIdsJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
)


