package com.example.data.local.database

import androidx.room.TypeConverter
import com.example.data.local.entity.BillItem
import com.example.data.local.entity.ComboSlot
import com.example.data.local.entity.RecipeIngredient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class Converters {

    @TypeConverter
    fun fromBillItemList(items: List<BillItem>?): String {
        if (items.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("dishId", item.dishId)
            obj.put("dishName", item.dishName)
            obj.put("unitPrice", item.unitPrice)
            obj.put("quantity", item.quantity)
            obj.put("totalPrice", item.totalPrice)
            obj.put("notes", item.notes)
            obj.put("isFree", item.isFree)
            obj.put("selectedVariant", item.selectedVariant)
            obj.put("selectedAddonsJson", item.selectedAddonsJson)
            obj.put("cookingNotes", item.cookingNotes)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toBillItemList(data: String?): List<BillItem> {
        if (data.isNullOrBlank()) return emptyList()
        val list = mutableListOf<BillItem>()
        try {
            val cleanData = data.trim()
            val array = if (cleanData.startsWith("[")) {
                JSONArray(cleanData)
            } else if (cleanData.startsWith("{")) {
                val wrapper = JSONObject(cleanData)
                wrapper.optJSONArray("items") ?: wrapper.optJSONArray("itemsJson") ?: JSONArray()
            } else {
                JSONArray()
            }
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val dishId = obj.optString("dishId").ifBlank {
                    obj.optString("itemId", obj.optString("id", UUID.randomUUID().toString()))
                }
                val dishName = obj.optString("dishName").ifBlank {
                    obj.optString("itemName", obj.optString("name", obj.optString("dish", obj.optString("title", "Item ${i + 1}"))))
                }
                val unitPrice = if (obj.has("unitPrice")) obj.optDouble("unitPrice", 0.0)
                else if (obj.has("price")) obj.optDouble("price", 0.0)
                else obj.optDouble("rate", 0.0)

                val quantity = if (obj.has("quantity")) obj.optInt("quantity", 1)
                else if (obj.has("qty")) obj.optInt("qty", 1)
                else obj.optInt("count", 1)

                val rawTotal = if (obj.has("totalPrice")) obj.optDouble("totalPrice", 0.0)
                else if (obj.has("total")) obj.optDouble("total", 0.0)
                else if (obj.has("amount")) obj.optDouble("amount", 0.0)
                else 0.0

                val totalPrice = if (rawTotal > 0.0) rawTotal else (unitPrice * quantity)
                val notes = obj.optString("notes", obj.optString("note", ""))
                val isFree = obj.optBoolean("isFree", obj.optBoolean("free", false))
                val selectedVariant = obj.optString("selectedVariant", "")
                val selectedAddonsJson = obj.optString("selectedAddonsJson", "[]")
                val cookingNotes = obj.optString("cookingNotes", "")

                list.add(
                    BillItem(
                        dishId = dishId,
                        dishName = dishName,
                        unitPrice = unitPrice,
                        quantity = quantity.coerceAtLeast(1),
                        totalPrice = totalPrice,
                        notes = notes,
                        isFree = isFree,
                        selectedVariant = selectedVariant,
                        selectedAddonsJson = selectedAddonsJson,
                        cookingNotes = cookingNotes
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @TypeConverter
    fun fromComboSlotList(slots: List<ComboSlot>?): String {
        if (slots.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        for (slot in slots) {
            val obj = JSONObject()
            obj.put("id", slot.id)
            obj.put("label", slot.label)
            obj.put("quantity", slot.quantity)
            val catArr = JSONArray()
            slot.categoryIds.forEach { catArr.put(it) }
            obj.put("categoryIds", catArr)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toComboSlotList(data: String?): List<ComboSlot> {
        if (data.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ComboSlot>()
        try {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val catIds = mutableListOf<String>()
                val catArr = obj.optJSONArray("categoryIds")
                if (catArr != null) {
                    for (c in 0 until catArr.length()) {
                        catIds.add(catArr.getString(c))
                    }
                }
                list.add(
                    ComboSlot(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        label = obj.optString("label", ""),
                        categoryIds = catIds,
                        quantity = obj.optInt("quantity", 1)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @TypeConverter
    fun fromRecipeIngredientList(ingredients: List<RecipeIngredient>?): String {
        if (ingredients.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        for (ing in ingredients) {
            val obj = JSONObject()
            obj.put("inventoryItemId", ing.inventoryItemId)
            obj.put("inventoryItemName", ing.inventoryItemName)
            obj.put("quantity", ing.quantity)
            obj.put("usageUnit", ing.usageUnit)
            obj.put("isTakeawayExtra", ing.isTakeawayExtra)
            obj.put("applyMode", ing.applyMode)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toRecipeIngredientList(data: String?): List<RecipeIngredient> {
        if (data.isNullOrBlank()) return emptyList()
        val list = mutableListOf<RecipeIngredient>()
        try {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val invId = obj.optString("inventoryItemId").ifBlank { obj.optString("itemId") }
                val invName = obj.optString("inventoryItemName").ifBlank { obj.optString("itemName") }
                val qty = if (obj.has("quantity")) obj.optDouble("quantity", 0.0) else obj.optDouble("qty", 0.0)
                val unit = obj.optString("usageUnit").ifBlank { obj.optString("unit", "Nos") }
                val isTakeaway = if (obj.has("isTakeawayExtra")) obj.optBoolean("isTakeawayExtra", false) else obj.optBoolean("takeawayExtra", false)
                val defaultMode = if (isTakeaway) "TAKEAWAY_ONLY" else "BOTH"
                val applyMode = obj.optString("applyMode", defaultMode)
                list.add(
                    RecipeIngredient(
                        inventoryItemId = invId,
                        inventoryItemName = invName,
                        quantity = qty,
                        usageUnit = unit,
                        isTakeawayExtra = isTakeaway,
                        applyMode = applyMode
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @TypeConverter
    fun fromStockCountItemList(items: List<com.example.data.local.entity.StockCountItem>?): String {
        if (items.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("inventoryItemId", item.inventoryItemId)
            obj.put("itemName", item.itemName)
            obj.put("unit", item.unit)
            obj.put("systemStock", item.systemStock)
            obj.put("actualStock", item.actualStock)
            obj.put("varianceQty", item.varianceQty)
            obj.put("unitRate", item.unitRate)
            obj.put("varianceCost", item.varianceCost)

            val adjArray = JSONArray()
            for (adj in item.batchAdjustments) {
                val adjObj = JSONObject()
                adjObj.put("batchId", adj.batchId)
                adjObj.put("initialQuantity", adj.initialQuantity)
                adjObj.put("previousRemainingQty", adj.previousRemainingQty)
                adjObj.put("newRemainingQty", adj.newRemainingQty)
                adjObj.put("adjustedQty", adj.adjustedQty)
                adjObj.put("unitRate", adj.unitRate)
                adjObj.put("batchNotes", adj.batchNotes)
                adjArray.put(adjObj)
            }
            obj.put("batchAdjustments", adjArray)

            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toStockCountItemList(data: String?): List<com.example.data.local.entity.StockCountItem> {
        if (data.isNullOrBlank()) return emptyList()
        val list = mutableListOf<com.example.data.local.entity.StockCountItem>()
        try {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val adjList = mutableListOf<com.example.data.local.entity.BatchAdjustmentRecord>()
                val adjArray = obj.optJSONArray("batchAdjustments")
                if (adjArray != null) {
                    for (j in 0 until adjArray.length()) {
                        val adjObj = adjArray.getJSONObject(j)
                        adjList.add(
                            com.example.data.local.entity.BatchAdjustmentRecord(
                                batchId = adjObj.optString("batchId"),
                                initialQuantity = adjObj.optDouble("initialQuantity", 0.0),
                                previousRemainingQty = adjObj.optDouble("previousRemainingQty", 0.0),
                                newRemainingQty = adjObj.optDouble("newRemainingQty", 0.0),
                                adjustedQty = adjObj.optDouble("adjustedQty", 0.0),
                                unitRate = adjObj.optDouble("unitRate", 0.0),
                                batchNotes = adjObj.optString("batchNotes")
                            )
                        )
                    }
                }
                list.add(
                    com.example.data.local.entity.StockCountItem(
                        inventoryItemId = obj.optString("inventoryItemId"),
                        itemName = obj.optString("itemName"),
                        unit = obj.optString("unit", "KG"),
                        systemStock = obj.optDouble("systemStock", 0.0),
                        actualStock = obj.optDouble("actualStock", 0.0),
                        varianceQty = obj.optDouble("varianceQty", 0.0),
                        unitRate = obj.optDouble("unitRate", 0.0),
                        varianceCost = obj.optDouble("varianceCost", 0.0),
                        batchAdjustments = adjList
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @TypeConverter
    fun fromLoyaltyHistoryList(list: List<com.example.data.local.entity.LoyaltyHistoryItem>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("eventType", item.eventType)
            obj.put("visitNumber", item.visitNumber)
            obj.put("billId", item.billId ?: "")
            obj.put("billNumber", item.billNumber ?: "")
            obj.put("billAmount", item.billAmount)
            obj.put("offerName", item.offerName ?: "")
            obj.put("rewardGiven", item.rewardGiven ?: "")
            obj.put("timestamp", item.timestamp)
            obj.put("notes", item.notes)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toLoyaltyHistoryList(data: String?): List<com.example.data.local.entity.LoyaltyHistoryItem> {
        if (data.isNullOrBlank()) return emptyList()
        val list = mutableListOf<com.example.data.local.entity.LoyaltyHistoryItem>()
        try {
            val array = JSONArray(data.trim())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.data.local.entity.LoyaltyHistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        eventType = obj.optString("eventType", "VISIT_COMPLETED"),
                        visitNumber = obj.optInt("visitNumber", 0),
                        billId = obj.optString("billId").takeIf { it.isNotBlank() },
                        billNumber = obj.optString("billNumber").takeIf { it.isNotBlank() },
                        billAmount = obj.optDouble("billAmount", 0.0),
                        offerName = obj.optString("offerName").takeIf { it.isNotBlank() },
                        rewardGiven = obj.optString("rewardGiven").takeIf { it.isNotBlank() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
