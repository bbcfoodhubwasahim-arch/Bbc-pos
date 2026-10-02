package com.example.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.database.CafePosDatabase
import com.example.data.local.database.Converters
import com.example.data.local.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val error: String) : SyncState()
}

/**
 * Robust Field Parsing Extensions for Firestore DocumentSnapshots.
 * Handles schema variations across v4.4, legacy versions, web, and Flutter exports:
 * - Types: Long, Int, Double, String, Boolean, Timestamp, Date
 * - Keys: Multiple field aliases and key naming conventions
 */
fun DocumentSnapshot.getAnyString(vararg keys: String, default: String = ""): String {
    for (k in keys) {
        val raw = get(k) ?: continue
        when (raw) {
            is String -> if (raw.isNotBlank()) return raw
            is Number -> return raw.toString()
            is Boolean -> return raw.toString()
            else -> {
                val str = raw.toString()
                if (str.isNotBlank() && !str.startsWith("{") && !str.startsWith("[")) return str
            }
        }
    }
    return default
}

fun DocumentSnapshot.getAnyDouble(vararg keys: String, default: Double = 0.0): Double {
    for (k in keys) {
        val raw = get(k) ?: continue
        when (raw) {
            is Number -> return raw.toDouble()
            is String -> {
                val parsed = raw.toDoubleOrNull()
                if (parsed != null) return parsed
            }
            is Boolean -> return if (raw) 1.0 else 0.0
        }
    }
    return default
}

fun DocumentSnapshot.getAnyLong(vararg keys: String, default: Long = System.currentTimeMillis()): Long {
    for (k in keys) {
        val raw = get(k) ?: continue
        when (raw) {
            is Number -> return raw.toLong()
            is com.google.firebase.Timestamp -> return raw.toDate().time
            is java.util.Date -> return raw.time
            is String -> {
                val parsedLong = raw.toLongOrNull()
                if (parsedLong != null) return parsedLong
                val parsedDouble = raw.toDoubleOrNull()
                if (parsedDouble != null) return parsedDouble.toLong()
            }
        }
    }
    return default
}

fun DocumentSnapshot.getAnyInt(vararg keys: String, default: Int = 0): Int {
    for (k in keys) {
        val raw = get(k) ?: continue
        when (raw) {
            is Number -> return raw.toInt()
            is String -> {
                val parsed = raw.toIntOrNull()
                if (parsed != null) return parsed
                val parsedDbl = raw.toDoubleOrNull()
                if (parsedDbl != null) return parsedDbl.toInt()
            }
            is Boolean -> return if (raw) 1 else 0
        }
    }
    return default
}

fun DocumentSnapshot.getAnyBoolean(vararg keys: String, default: Boolean = false): Boolean {
    for (k in keys) {
        val raw = get(k) ?: continue
        when (raw) {
            is Boolean -> return raw
            is Number -> return raw.toInt() != 0
            is String -> {
                val lower = raw.trim().lowercase()
                if (lower == "true" || lower == "1" || lower == "yes" || lower == "settled" || lower == "paid" || lower == "completed" || lower == "active" || lower == "open") return true
                if (lower == "false" || lower == "0" || lower == "no" || lower == "unsettled" || lower == "pending" || lower == "inactive" || lower == "closed") return false
            }
        }
    }
    return default
}

/**
 * Manages Cloud Sync & Data Isolation in Firestore:
 * Stored under path: pos_users/{realFirebaseUid}/...
 * Fallbacks to legacy paths or root collections if data exists in legacy structures.
 */
class FirestoreSyncManager(private val context: Context) {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val converters = Converters()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    fun isOnline(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtains the real Firebase UID directly from FirebaseAuth.currentUser.uid.
     * Guaranteed never to use any fake prefix or custom ID.
     */
    private fun getRealFirebaseUid(userId: String = ""): String {
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: userId.takeIf { it.isNotBlank() && !it.startsWith("pos_uid_") }
            ?: ""
    }

    private fun userRef(userId: String): DocumentReference {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) {
            throw IllegalStateException("No authenticated Firebase user found (FirebaseAuth.currentUser is null)")
        }
        return firestore.collection("pos_users").document(uid)
    }

    /**
     * Queries Firestore across multiple collection name variations and path locations
     * (pos_users/{uid}/col, users/{uid}/col, root /col) to find all existing documents.
     */
    private suspend fun getDocsFromMultipleLocations(realUid: String, vararg collectionNames: String): List<DocumentSnapshot> {
        val results = mutableListOf<DocumentSnapshot>()
        val seenDocIds = mutableSetOf<String>()

        val rootRef = userRef(realUid)

        // 1. Check subcollections under pos_users/{realUid}/
        for (colName in collectionNames) {
            try {
                val snap = rootRef.collection(colName).get().await()
                for (doc in snap.documents) {
                    if (doc.exists() && doc.id !in seenDocIds) {
                        seenDocIds.add(doc.id)
                        results.add(doc)
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreSyncManager", "Subcollection check $colName note: ${e.message}")
            }
        }

        // 2. Check subcollections under users/{realUid}/ (legacy path)
        if (results.isEmpty() && realUid.isNotBlank()) {
            for (colName in collectionNames) {
                try {
                    val snap = firestore.collection("users").document(realUid).collection(colName).get().await()
                    for (doc in snap.documents) {
                        if (doc.exists() && doc.id !in seenDocIds) {
                            seenDocIds.add(doc.id)
                            results.add(doc)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreSyncManager", "Legacy path check $colName note: ${e.message}")
                }
            }
        }

        // 3. Check root collections (e.g. /bills, /orders)
        if (results.isEmpty()) {
            for (colName in collectionNames) {
                try {
                    val snap = firestore.collection(colName).get().await()
                    for (doc in snap.documents) {
                        if (doc.exists() && doc.id !in seenDocIds) {
                            val docUid = doc.getString("userId") ?: doc.getString("uid") ?: doc.getString("user_id") ?: ""
                            if (docUid.isBlank() || docUid == realUid || !docUid.startsWith("pos_uid_")) {
                                seenDocIds.add(doc.id)
                                results.add(doc)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreSyncManager", "Root collection check $colName note: ${e.message}")
                }
            }
        }

        return results
    }

    /**
     * Helper to safely parse bill items from DocumentSnapshot across JSON strings, Maps, and Arrays.
     */
    private fun parseBillItemsFromDoc(doc: DocumentSnapshot): List<BillItem> {
        val itemsJsonStr = doc.getAnyString("itemsJson", "items_json", "billItemsJson", "cartJson")
        if (itemsJsonStr.isNotBlank()) {
            try {
                val parsed = converters.toBillItemList(itemsJsonStr)
                if (parsed.isNotEmpty()) return parsed
            } catch (e: Exception) {
                Log.w("FirestoreSyncManager", "Failed parsing itemsJson", e)
            }
        }

        val rawItems = doc.get("items") ?: doc.get("billItems") ?: doc.get("orderItems") ?: doc.get("dishes") ?: doc.get("cart")
        if (rawItems is String && rawItems.isNotBlank()) {
            try {
                val parsed = converters.toBillItemList(rawItems)
                if (parsed.isNotEmpty()) return parsed
            } catch (e: Exception) {
                Log.w("FirestoreSyncManager", "Failed parsing raw items string", e)
            }
        }

        if (rawItems is List<*>) {
            val parsed = rawItems.mapNotNull { m ->
                if (m is Map<*, *>) {
                    val dishId = (m["dishId"] ?: m["itemId"] ?: m["id"] ?: m["menuItemId"] ?: UUID.randomUUID().toString()).toString()
                    val dishName = (m["dishName"] ?: m["itemName"] ?: m["name"] ?: m["title"] ?: m["dish"] ?: "Item").toString()
                    val uPrice = (m["unitPrice"] ?: m["price"] ?: m["rate"] ?: m["cost"] ?: 0.0).toString().toDoubleOrNull() ?: 0.0
                    val qty = (m["quantity"] ?: m["qty"] ?: m["count"] ?: 1).toString().toDoubleOrNull()?.toInt() ?: 1
                    val tPrice = (m["totalPrice"] ?: m["total"] ?: m["amount"] ?: (uPrice * qty)).toString().toDoubleOrNull() ?: (uPrice * qty)
                    val notes = (m["notes"] ?: m["note"] ?: m["comment"] ?: "").toString()
                    val isFree = when (val f = m["isFree"] ?: m["free"]) {
                        is Boolean -> f
                        is String -> f.lowercase() == "true"
                        else -> false
                    }
                    BillItem(
                        dishId = dishId,
                        dishName = dishName,
                        unitPrice = uPrice,
                        quantity = qty.coerceAtLeast(1),
                        totalPrice = tPrice,
                        notes = notes,
                        isFree = isFree
                    )
                } else null
            }
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    /**
     * Uploads all local database records to Firestore under pos_users/{realFirebaseUid}/...
     */
    suspend fun syncAllToCloud(userId: String, db: CafePosDatabase): Result<String> = withContext(Dispatchers.IO) {
        val realUid = getRealFirebaseUid(userId)
        if (realUid.isBlank()) {
            _syncState.value = SyncState.Error("Firebase user not authenticated. Please sign in with Google.")
            return@withContext Result.failure(Exception("Firebase user not authenticated: FirebaseAuth.currentUser is null"))
        }

        _syncState.value = SyncState.Syncing("Starting cloud sync...")

        try {
            val root = userRef(realUid)

            // 1. Sync Restaurants
            _syncState.value = SyncState.Syncing("Syncing restaurants...")
            val restaurants = db.restaurantDao().getAllRestaurantsDirect()
            for (r in restaurants) {
                if (r.id.contains("bakers_boy", ignoreCase = true) || r.name.contains("Baker's Boy", ignoreCase = true)) {
                    continue
                }
                root.collection("restaurants").document(r.id).set(
                    hashMapOf(
                        "name" to r.name,
                        "address" to r.address,
                        "phone" to r.phone,
                        "footerNote" to r.footerNote,
                        "logoPreset" to r.logoPreset,
                        "customLogoUri" to r.customLogoUri,
                        "customLogoBase64" to r.customLogoBase64,
                        "isActive" to r.isActive,
                        "updatedAt" to r.updatedAt,
                        "fssaiNumber" to r.fssaiNumber,
                        "isFssaiEnabled" to r.isFssaiEnabled,
                        "showFssaiOnBill" to r.showFssaiOnBill,
                        "gstNumber" to r.gstNumber,
                        "gstRate" to r.gstRate,
                        "isGstEnabled" to r.isGstEnabled,
                        "showGstOnBill" to r.showGstOnBill,
                        "billFormat" to r.billFormat,
                        "billPrefix" to r.billPrefix,
                        "billPrefixCountersJson" to r.billPrefixCountersJson
                    ), SetOptions.merge()
                ).await()
            }

            // 2. Sync Categories
            _syncState.value = SyncState.Syncing("Syncing categories...")
            val categories = db.categoryDao().getAllCategoriesDirect()
            for (c in categories) {
                root.collection("categories").document(c.id).set(
                    hashMapOf(
                        "name" to c.name,
                        "displayOrder" to c.displayOrder,
                        "isDiscountEligible" to c.isDiscountEligible,
                        "updatedAt" to c.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 3. Sync Menu Items
            _syncState.value = SyncState.Syncing("Syncing menu dishes...")
            val menuItems = db.menuItemDao().getAllMenuItemsDirect()
            for (m in menuItems) {
                root.collection("menu_items").document(m.id).set(
                    hashMapOf(
                        "categoryId" to m.categoryId,
                        "categoryName" to m.categoryName,
                        "name" to m.name,
                        "price" to m.price,
                        "isAvailable" to m.isAvailable,
                        "isDiscountEligible" to m.isDiscountEligible,
                        "imageUri" to m.imageUri,
                        "variantsJson" to m.variantsJson,
                        "addonsJson" to m.addonsJson,
                        "updatedAt" to m.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 4. Sync Tables
            _syncState.value = SyncState.Syncing("Syncing tables...")
            val tables = db.cafeTableDao().getAllTablesDirect()
            for (t in tables) {
                root.collection("tables").document(t.id).set(
                    hashMapOf(
                        "restaurantId" to t.restaurantId,
                        "name" to t.name,
                        "capacity" to t.capacity,
                        "isOccupied" to t.isOccupied,
                        "activeBillId" to t.activeBillId,
                        "updatedAt" to t.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 5. Sync Customers
            _syncState.value = SyncState.Syncing("Syncing customers...")
            val customers = db.customerDao().getAllCustomersDirect()
            for (c in customers) {
                root.collection("customers").document(c.id).set(
                    hashMapOf(
                        "id" to c.id,
                        "name" to c.name,
                        "contactNumber" to c.contactNumber,
                        "isCreditCustomer" to c.isCreditCustomer,
                        "loyaltyVisitCount" to c.loyaltyVisitCount,
                        "loyaltyStartDate" to c.loyaltyStartDate,
                        "loyaltyExpiryDate" to c.loyaltyExpiryDate,
                        "currentLoyaltyProgramId" to c.currentLoyaltyProgramId,
                        "loyaltyHistoryJson" to c.loyaltyHistoryJson,
                        "rewardPointsBalance" to c.rewardPointsBalance,
                        "giftPointsBalance" to c.giftPointsBalance,
                        "isEnrolledInLoyalty" to c.isEnrolledInLoyalty,
                        "birthday" to (c.birthday ?: ""),
                        "lastVisitTimestamp" to c.lastVisitTimestamp,
                        "updatedAt" to c.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 6. Sync Bills
            _syncState.value = SyncState.Syncing("Syncing bills & invoices...")
            val bills = db.billDao().getAllBillsDirect()
            for (b in bills) {
                val itemsListMap = b.items.map { item ->
                    hashMapOf(
                        "dishId" to item.dishId,
                        "dishName" to item.dishName,
                        "unitPrice" to item.unitPrice,
                        "quantity" to item.quantity,
                        "totalPrice" to item.totalPrice,
                        "notes" to item.notes,
                        "isFree" to item.isFree
                    )
                }
                root.collection("bills").document(b.id).set(
                    hashMapOf(
                        "id" to b.id,
                        "billNumber" to b.billNumber,
                        "restaurantId" to b.restaurantId,
                        "restaurantName" to b.restaurantName,
                        "orderType" to b.orderType,
                        "tableId" to b.tableId,
                        "tableName" to b.tableName,
                        "customerName" to b.customerName,
                        "customerPhone" to b.customerPhone,
                        "itemsJson" to converters.fromBillItemList(b.items),
                        "items" to itemsListMap,
                        "subtotal" to b.subtotal,
                        "discountType" to b.discountType,
                        "discountValue" to b.discountValue,
                        "discountAmount" to b.discountAmount,
                        "totalAmount" to b.totalAmount,
                        "paymentMethod" to b.paymentMethod,
                        "cashAmount" to b.cashAmount,
                        "upiAmount" to b.upiAmount,
                        "isSettled" to b.isSettled,
                        "isStockDeducted" to b.isStockDeducted,
                        "totalFoodCost" to b.totalFoodCost,
                        "appliedRewardType" to b.appliedRewardType,
                        "appliedOfferId" to b.appliedOfferId,
                        "appliedOfferName" to b.appliedOfferName,
                        "rewardPointsEarned" to b.rewardPointsEarned,
                        "pointsRedeemed" to b.pointsRedeemed,
                        "rewardPointsRedeemed" to b.rewardPointsRedeemed,
                        "giftPointsRedeemed" to b.giftPointsRedeemed,
                        "isVoided" to b.isVoided,
                        "status" to b.status,
                        "isCancelled" to b.isCancelled,
                        "cancellationReason" to b.cancellationReason,
                        "cancelledAt" to b.cancelledAt,
                        "billTimestamp" to b.billTimestamp,
                        "createdAt" to b.createdAt,
                        "updatedAt" to b.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 6b. Sync Customer Payments
            _syncState.value = SyncState.Syncing("Syncing customer payments...")
            val customerPayments = db.customerPaymentDao().getAllPaymentsDirect()
            for (cp in customerPayments) {
                root.collection("customer_payments").document(cp.id).set(
                    hashMapOf(
                        "id" to cp.id,
                        "customerId" to cp.customerId,
                        "customerName" to cp.customerName,
                        "customerPhone" to cp.customerPhone,
                        "amount" to cp.amount,
                        "paymentMode" to cp.paymentMode,
                        "notes" to cp.notes,
                        "timestamp" to cp.timestamp,
                        "createdAt" to cp.createdAt
                    ), SetOptions.merge()
                ).await()
            }

            // 7. Sync Inventory
            _syncState.value = SyncState.Syncing("Syncing inventory...")
            val inventory = db.inventoryDao().getAllInventoryDirect()
            for (i in inventory) {
                root.collection("inventory").document(i.id).set(
                    hashMapOf(
                        "restaurantId" to i.restaurantId,
                        "name" to i.name,
                        "unit" to i.unit,
                        "openingStock" to i.openingStock,
                        "currentStock" to i.currentStock,
                        "lowStockThreshold" to i.lowStockThreshold,
                        "purchasePrice" to i.purchasePrice,
                        "baseRate" to i.purchasePrice,
                        "updatedAt" to i.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 7b. Sync Inventory Batches
            _syncState.value = SyncState.Syncing("Syncing inventory batches...")
            val batches = db.inventoryBatchDao().getAllBatchesDirect()
            for (b in batches) {
                root.collection("inventory_batches").document(b.id).set(
                    hashMapOf(
                        "id" to b.id,
                        "restaurantId" to b.restaurantId,
                        "inventoryItemId" to b.inventoryItemId,
                        "itemName" to b.itemName,
                        "quantityAdded" to b.initialQuantity,
                        "initialQuantity" to b.initialQuantity,
                        "remainingQuantity" to b.remainingQuantity,
                        "ratePerUnit" to b.purchaseRate,
                        "purchaseRate" to b.purchaseRate,
                        "unit" to b.unit,
                        "batchType" to b.batchType,
                        "notes" to b.notes,
                        "addedAt" to b.timestamp,
                        "timestamp" to b.timestamp,
                        "isConsumed" to b.isConsumed,
                        "updatedAt" to b.updatedAt,
                        "createdAt" to b.createdAt,
                        "createdByUserId" to b.createdByUserId,
                        "referenceTransactionId" to b.referenceTransactionId,
                        "status" to b.status
                    ), SetOptions.merge()
                ).await()
            }

            // 7c. Sync Bill Batch Deductions
            val deductions = db.inventoryBatchDao().getAllDeductionsDirect()
            for (d in deductions) {
                root.collection("bill_batch_deductions").document(d.id).set(
                    hashMapOf(
                        "billId" to d.billId,
                        "menuItemId" to d.menuItemId,
                        "inventoryItemId" to d.inventoryItemId,
                        "batchId" to d.batchId,
                        "quantityDeducted" to d.quantityDeducted,
                        "rate" to d.rate,
                        "cost" to d.cost,
                        "isNegativeStock" to d.isNegativeStock,
                        "timestamp" to d.timestamp,
                        "createdAt" to d.createdAt,
                        "status" to d.status,
                        "referenceId" to d.referenceId
                    ), SetOptions.merge()
                ).await()
            }

            // 7d. Sync Stock Audit Transactions
            val stockTxns = db.stockTransactionDao().getAllTransactionsDirect()
            for (st in stockTxns) {
                root.collection("stock_transactions").document(st.id).set(
                    hashMapOf(
                        "restaurantId" to st.restaurantId,
                        "inventoryItemId" to st.inventoryItemId,
                        "itemName" to st.itemName,
                        "transactionType" to st.transactionType,
                        "quantity" to st.quantity,
                        "unitRate" to st.unitRate,
                        "balanceAfter" to st.balanceAfter,
                        "batchId" to st.batchId,
                        "billId" to st.billId,
                        "referenceId" to st.referenceId,
                        "notes" to st.notes,
                        "timestamp" to st.timestamp
                    ), SetOptions.merge()
                ).await()
            }

            // 8. Sync Expense Categories & Expenses
            _syncState.value = SyncState.Syncing("Syncing expenses...")
            val expCats = db.expenseDao().getAllCategoriesDirect()
            for (ec in expCats) {
                root.collection("expense_categories").document(ec.id).set(
                    hashMapOf(
                        "name" to ec.name,
                        "updatedAt" to ec.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            val expenses = db.expenseDao().getAllExpensesDirect()
            for (e in expenses) {
                root.collection("expenses").document(e.id).set(
                    hashMapOf(
                        "restaurantId" to e.restaurantId,
                        "categoryId" to e.categoryId,
                        "categoryName" to e.categoryName,
                        "amount" to e.amount,
                        "paymentMethod" to e.paymentMethod,
                        "description" to e.description,
                        "timestamp" to e.timestamp,
                        "updatedAt" to e.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 9. Sync Recipes
            _syncState.value = SyncState.Syncing("Syncing recipes & ingredients...")
            val recipes = db.recipeDao().getAllRecipesDirect()
            for (rec in recipes) {
                val ingredientsList = rec.ingredients.map { ing ->
                    hashMapOf(
                        "inventoryItemId" to ing.inventoryItemId,
                        "inventoryItemName" to ing.inventoryItemName,
                        "quantity" to ing.quantity,
                        "usageUnit" to ing.usageUnit,
                        "isTakeawayExtra" to ing.isTakeawayExtra
                    )
                }
                root.collection("recipes").document(rec.menuItemId).set(
                    hashMapOf(
                        "menuItemId" to rec.menuItemId,
                        "restaurantId" to rec.restaurantId,
                        "menuItemName" to rec.menuItemName,
                        "ingredientsJson" to converters.fromRecipeIngredientList(rec.ingredients),
                        "ingredients" to ingredientsList,
                        "notes" to rec.notes,
                        "updatedAt" to rec.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 10. Sync Combos
            _syncState.value = SyncState.Syncing("Syncing combos...")
            val combos = db.comboDao().getAllCombosDirect()
            for (cmb in combos) {
                root.collection("combos").document(cmb.id).set(
                    hashMapOf(
                        "id" to cmb.id,
                        "restaurantId" to cmb.restaurantId,
                        "name" to cmb.name,
                        "price" to cmb.price,
                        "badge" to cmb.badge,
                        "description" to cmb.description,
                        "isActive" to cmb.isActive,
                        "slotsJson" to converters.fromComboSlotList(cmb.slots),
                        "createdAt" to cmb.createdAt
                    ), SetOptions.merge()
                ).await()
            }

            // 11. Sync Cash Registers
            _syncState.value = SyncState.Syncing("Syncing cash registers...")
            val registers = db.cashRegisterDao().getAllRegistersDirect()
            for (cr in registers) {
                root.collection("cash_registers").document(cr.id).set(
                    hashMapOf(
                        "restaurantId" to cr.restaurantId,
                        "dateString" to cr.dateString,
                        "openingCash" to cr.openingCash,
                        "cashSales" to cr.cashSales,
                        "cashExpenses" to cr.cashExpenses,
                        "cashAdded" to cr.cashAdded,
                        "cashWithdrawn" to cr.cashWithdrawn,
                        "expectedCash" to cr.expectedCash,
                        "closingCashActual" to (cr.closingCashActual ?: 0.0),
                        "difference" to (cr.difference ?: 0.0),
                        "status" to cr.status,
                        "openedAt" to cr.openedAt,
                        "closedAt" to (cr.closedAt ?: 0L),
                        "notes" to cr.notes,
                        "updatedAt" to cr.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 11b. Sync Stock Counts
            _syncState.value = SyncState.Syncing("Syncing stock counts...")
            val stockCounts = db.stockCountDao().getAllStockCountsDirect()
            for (sc in stockCounts) {
                val itemsList = sc.items.map { item ->
                    val adjsList = item.batchAdjustments.map { a ->
                        hashMapOf(
                            "batchId" to a.batchId,
                            "initialQuantity" to a.initialQuantity,
                            "previousRemainingQty" to a.previousRemainingQty,
                            "newRemainingQty" to a.newRemainingQty,
                            "adjustedQty" to a.adjustedQty,
                            "unitRate" to a.unitRate,
                            "batchNotes" to a.batchNotes
                        )
                    }
                    hashMapOf(
                        "inventoryItemId" to item.inventoryItemId,
                        "itemName" to item.itemName,
                        "unit" to item.unit,
                        "systemStock" to item.systemStock,
                        "actualStock" to item.actualStock,
                        "varianceQty" to item.varianceQty,
                        "unitRate" to item.unitRate,
                        "varianceCost" to item.varianceCost,
                        "batchAdjustments" to adjsList
                    )
                }
                root.collection("stock_counts").document(sc.id).set(
                    hashMapOf(
                        "restaurantId" to sc.restaurantId,
                        "countDate" to sc.countDate,
                        "title" to sc.title,
                        "totalItemsChecked" to sc.totalItemsChecked,
                        "totalVarianceCost" to sc.totalVarianceCost,
                        "isAdjusted" to sc.isAdjusted,
                        "notes" to sc.notes,
                        "items" to itemsList,
                        "createdAt" to sc.createdAt,
                        "updatedAt" to sc.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 11c. Sync Offers
            _syncState.value = SyncState.Syncing("Syncing offers...")
            val offers = db.offerDao().getAllOffersDirect()
            for (off in offers) {
                root.collection("offers").document(off.id).set(
                    hashMapOf(
                        "id" to off.id,
                        "restaurantId" to off.restaurantId,
                        "name" to off.name,
                        "offerType" to off.offerType,
                        "rewardType" to off.rewardType,
                        "rewardValue" to off.rewardValue,
                        "minBillAmount" to off.minBillAmount,
                        "applicableOrderTypes" to off.applicableOrderTypes,
                        "isActive" to off.isActive,
                        "totalVisitsInProgram" to off.totalVisitsInProgram,
                        "visitNumber" to off.visitNumber,
                        "validityDays" to off.validityDays,
                        "freeItemQuantity" to off.freeItemQuantity,
                        "eligibleMenuItemIds" to off.eligibleMenuItemIds,
                        "allowSameItemMultipleTimes" to off.allowSameItemMultipleTimes,
                        "createdAt" to off.createdAt,
                        "updatedAt" to off.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 11d. Sync Points Batches
            _syncState.value = SyncState.Syncing("Syncing points batches...")
            val pointsBatches = db.pointsBatchDao().getAllActiveBatchesList()
            for (pb in pointsBatches) {
                root.collection("points_batches").document(pb.id).set(
                    hashMapOf(
                        "id" to pb.id,
                        "customerId" to pb.customerId,
                        "customerPhone" to pb.customerPhone,
                        "customerName" to pb.customerName,
                        "batchType" to pb.batchType,
                        "initialPoints" to pb.initialPoints,
                        "remainingPoints" to pb.remainingPoints,
                        "earnDate" to pb.earnDate,
                        "expiryDate" to pb.expiryDate,
                        "billId" to pb.billId,
                        "billNumber" to pb.billNumber,
                        "notes" to pb.notes,
                        "isExpired" to pb.isExpired,
                        "reminderSent" to pb.reminderSent,
                        "reminderSentDate" to pb.reminderSentDate,
                        "createdAt" to pb.createdAt,
                        "updatedAt" to pb.updatedAt
                    ), SetOptions.merge()
                ).await()
            }

            // 11e. Sync Points Ledger
            _syncState.value = SyncState.Syncing("Syncing points ledger...")
            val pointsLedger = db.pointsLedgerDao().getAllEntriesDirect()
            for (pl in pointsLedger) {
                root.collection("points_ledger").document(pl.id).set(
                    hashMapOf(
                        "id" to pl.id,
                        "customerId" to pl.customerId,
                        "customerPhone" to pl.customerPhone,
                        "customerName" to pl.customerName,
                        "transactionType" to pl.transactionType,
                        "pointsAmount" to pl.pointsAmount,
                        "balanceType" to pl.balanceType,
                        "billId" to pl.billId,
                        "billNumber" to pl.billNumber,
                        "expiryDate" to pl.expiryDate,
                        "rewardPointsBalanceAfter" to pl.rewardPointsBalanceAfter,
                        "giftPointsBalanceAfter" to pl.giftPointsBalanceAfter,
                        "notes" to pl.notes,
                        "timestamp" to pl.timestamp,
                        "createdAt" to pl.createdAt
                    ), SetOptions.merge()
                ).await()
            }

            // Update sync metadata document
            val metadata = hashMapOf(
                "lastSyncAt" to System.currentTimeMillis(),
                "appVersion" to com.example.BuildConfig.VERSION_NAME,
                "deviceInfo" to android.os.Build.MODEL
            )
            root.collection("metadata").document("sync_info").set(metadata, SetOptions.merge()).await()

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncState.value = SyncState.Success("Cloud sync completed successfully")
            Result.success("Cloud sync completed successfully")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Sync error", e)
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync error occurred")
            Result.failure(e)
        }
    }

    /**
     * Restores all data from Firestore across subcollections & root collections into Room DB.
     * Uses resilient parsing so no document is silently lost due to type mismatches.
     */
    suspend fun restoreFromCloud(userId: String, db: CafePosDatabase): Result<String> = withContext(Dispatchers.IO) {
        val realUid = getRealFirebaseUid(userId)
        if (realUid.isBlank()) {
            _syncState.value = SyncState.Error("Firebase user not authenticated. Please sign in with Google.")
            return@withContext Result.failure(Exception("Firebase user not authenticated: FirebaseAuth.currentUser is null"))
        }

        _syncState.value = SyncState.Syncing("Fetching cloud backup from Firestore...")

        try {
            // 1. Restore Restaurants
            _syncState.value = SyncState.Syncing("Restoring restaurants...")
            val restDocs = getDocsFromMultipleLocations(realUid, "restaurants", "cafes", "shops", "branches")
            val restaurants = restDocs.mapNotNull { doc ->
                try {
                    val name = doc.getAnyString("name", "restaurantName", "title", "shopName")
                    if (doc.id.contains("bakers_boy", ignoreCase = true) || name.contains("Baker's Boy", ignoreCase = true)) {
                        return@mapNotNull null
                    }
                    val base64 = doc.getAnyString("customLogoBase64")
                    val restoredUri = if (base64.isNotBlank()) {
                        restoreBase64LogoToFile(context, doc.id, base64)
                    } else null

                    RestaurantEntity(
                        id = doc.id,
                        name = name.ifBlank { "My Cafe" },
                        address = doc.getAnyString("address", "location"),
                        phone = doc.getAnyString("phone", "contact", "mobile"),
                        footerNote = doc.getAnyString("footerNote", "footer", default = "Thank you for visiting! Please visit again."),
                        logoPreset = doc.getAnyString("logoPreset", "logo", default = "cafe_coffee"),
                        customLogoUri = restoredUri ?: doc.getAnyString("customLogoUri", "logoUri").takeIf { it.isNotBlank() },
                        customLogoBase64 = base64,
                        isActive = doc.getAnyBoolean("isActive", "active", default = true),
                        updatedAt = doc.getAnyLong("updatedAt", "timestamp", default = System.currentTimeMillis()),
                        fssaiNumber = doc.getAnyString("fssaiNumber", "fssai"),
                        isFssaiEnabled = doc.getAnyBoolean("isFssaiEnabled", "isFssai"),
                        showFssaiOnBill = doc.getAnyBoolean("showFssaiOnBill"),
                        gstNumber = doc.getAnyString("gstNumber", "gst"),
                        gstRate = doc.getAnyDouble("gstRate", "gstPercent", default = 5.0),
                        isGstEnabled = doc.getAnyBoolean("isGstEnabled", "isGst"),
                        showGstOnBill = doc.getAnyBoolean("showGstOnBill"),
                        billFormat = doc.getAnyString("billFormat", "format", default = "ELEGANT_DINE_IN"),
                        billPrefix = doc.getAnyString("billPrefix", "prefix"),
                        billPrefixCountersJson = doc.getAnyString("billPrefixCountersJson", "countersJson", default = "{}")
                    )
                } catch (e: Exception) {
                    Log.w("FirestoreSyncManager", "Error parsing restaurant ${doc.id}", e)
                    null
                }
            }
            if (restaurants.isNotEmpty()) {
                db.restaurantDao().insertAll(restaurants)
            }

            // 2. Restore Categories
            _syncState.value = SyncState.Syncing("Restoring categories...")
            val catDocs = getDocsFromMultipleLocations(realUid, "categories", "menu_categories", "dish_categories")
            val categories = catDocs.mapNotNull { doc ->
                try {
                    val name = doc.getAnyString("name", "categoryName", "title")
                    if (name.isBlank()) return@mapNotNull null
                    CategoryEntity(
                        id = doc.id,
                        name = name,
                        displayOrder = doc.getAnyInt("displayOrder", "order", "priority", default = 0),
                        isDiscountEligible = doc.getAnyBoolean("isDiscountEligible", "discountEligible", default = true),
                        defaultAddonIdsJson = doc.getAnyString("defaultAddonIdsJson", default = "[]"),
                        updatedAt = doc.getAnyLong("updatedAt", "timestamp", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (categories.isNotEmpty()) {
                db.categoryDao().insertAll(categories)
            }

            // 3. Restore Menu Items
            _syncState.value = SyncState.Syncing("Restoring menu items...")
            val menuDocs = getDocsFromMultipleLocations(realUid, "menu_items", "menu", "dishes", "items")
            val menuItems = menuDocs.mapNotNull { doc ->
                try {
                    val name = doc.getAnyString("name", "dishName", "itemName", "title")
                    if (name.isBlank()) return@mapNotNull null
                    MenuItemEntity(
                        id = doc.id,
                        categoryId = doc.getAnyString("categoryId", "catId", "category_id"),
                        categoryName = doc.getAnyString("categoryName", "category", "catName"),
                        name = name,
                        price = doc.getAnyDouble("price", "rate", "cost", "amount"),
                        isAvailable = doc.getAnyBoolean("isAvailable", "available", "inStock", default = true),
                        isDiscountEligible = doc.getAnyBoolean("isDiscountEligible", "discountEligible", default = true),
                        imageUri = doc.getAnyString("imageUri", "imageUrl", "image").takeIf { it.isNotBlank() },
                        variantsJson = doc.getAnyString("variantsJson", default = "[]"),
                        addonsJson = doc.getAnyString("addonsJson", default = "[]"),
                        excludedAddonIdsJson = doc.getAnyString("excludedAddonIdsJson", default = "[]"),
                        itemAddonIdsJson = doc.getAnyString("itemAddonIdsJson", default = "[]"),
                        updatedAt = doc.getAnyLong("updatedAt", "timestamp", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (menuItems.isNotEmpty()) {
                db.menuItemDao().insertAll(menuItems)
            }

            // 3b. Restore Addon Definitions
            _syncState.value = SyncState.Syncing("Restoring add-ons...")
            val addonDocs = getDocsFromMultipleLocations(realUid, "addons", "addon_definitions")
            val addons = addonDocs.mapNotNull { doc ->
                try {
                    val name = doc.getAnyString("name", "title")
                    if (name.isBlank()) return@mapNotNull null
                    AddonDefinitionEntity(
                        id = doc.id,
                        name = name,
                        price = doc.getAnyDouble("price", default = 0.0),
                        selectionType = doc.getAnyString("selectionType", default = "MULTI"),
                        groupName = doc.getAnyString("groupName", default = "Add-ons"),
                        isRequired = doc.getAnyBoolean("isRequired"),
                        minCount = doc.getAnyInt("minCount", default = 0),
                        maxCount = doc.getAnyInt("maxCount", default = Int.MAX_VALUE),
                        isActive = doc.getAnyBoolean("isActive", default = true),
                        inventoryItemId = doc.getAnyString("inventoryItemId").takeIf { it.isNotBlank() },
                        inventoryItemName = doc.getAnyString("inventoryItemName").takeIf { it.isNotBlank() },
                        inventoryQty = doc.getAnyDouble("inventoryQty", default = 1.0),
                        usageUnit = doc.getAnyString("usageUnit", default = "Nos"),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (addons.isNotEmpty()) {
                db.addonDao().insertOrUpdateAll(addons)
            }

            // 4. Restore Tables
            _syncState.value = SyncState.Syncing("Restoring cafe tables...")
            val tableDocs = getDocsFromMultipleLocations(realUid, "tables", "cafe_tables", "restaurant_tables")
            val tables = tableDocs.mapNotNull { doc ->
                try {
                    val rawActiveBillId = doc.getAnyString("activeBillId", "billId").takeIf { it.isNotBlank() }
                    val rawOccupied = doc.getAnyBoolean("isOccupied", "occupied")
                    val bill = if (rawActiveBillId != null) db.billDao().getBillById(rawActiveBillId) else null
                    val isGenuinelyOccupied = if (bill != null) (!bill.isCancelled && !bill.isVoided && !bill.isSettled) else (rawOccupied && rawActiveBillId != null)
                    val cleanActiveBillId = if (isGenuinelyOccupied) rawActiveBillId else null

                    CafeTableEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId", "restId"),
                        name = doc.getAnyString("name", "tableName", "tableNo", default = "Table"),
                        capacity = doc.getAnyInt("capacity", "seats", default = 4),
                        isOccupied = isGenuinelyOccupied,
                        activeBillId = cleanActiveBillId,
                        updatedAt = doc.getAnyLong("updatedAt", "timestamp", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (tables.isNotEmpty()) {
                db.cafeTableDao().insertAll(tables)
            }

            // 5. Restore Customers
            _syncState.value = SyncState.Syncing("Restoring customers...")
            val custDocs = getDocsFromMultipleLocations(realUid, "customers", "clients", "guests")
            val customers = custDocs.mapNotNull { doc ->
                try {
                    val name = doc.getAnyString("name", "customerName", "clientName", "full_name")
                    val contact = doc.getAnyString("contactNumber", "phone", "mobile", "phoneNumber")
                    if (name.isBlank() && contact.isBlank()) return@mapNotNull null
                    val cloudReward = doc.getAnyInt("rewardPointsBalance", "rewardPoints", "points")
                    val cloudGift = doc.getAnyInt("giftPointsBalance", "giftPoints")
                    val cloudUpdatedAt = doc.getAnyLong("updatedAt", "timestamp", default = System.currentTimeMillis())
                    val existingLocal = db.customerDao().getCustomerById(doc.id)
                        ?: if (contact.isNotBlank()) db.customerDao().getCustomerByContact(contact) else null
                    
                    val isLocalNewer = existingLocal != null && existingLocal.updatedAt > cloudUpdatedAt
                    val finalReward = if (isLocalNewer) existingLocal!!.rewardPointsBalance else cloudReward
                    val finalGift = if (isLocalNewer) existingLocal!!.giftPointsBalance else cloudGift
                    
                    CustomerEntity(
                        id = existingLocal?.id ?: doc.id,
                        name = name.ifBlank { "Customer" },
                        contactNumber = contact,
                        isCreditCustomer = doc.getAnyBoolean("isCreditCustomer", "isCredit"),
                        loyaltyVisitCount = if (isLocalNewer) existingLocal!!.loyaltyVisitCount else doc.getAnyInt("loyaltyVisitCount", "visits"),
                        loyaltyStartDate = doc.getAnyLong("loyaltyStartDate").takeIf { it > 0 } ?: existingLocal?.loyaltyStartDate,
                        loyaltyExpiryDate = doc.getAnyLong("loyaltyExpiryDate").takeIf { it > 0 } ?: existingLocal?.loyaltyExpiryDate,
                        currentLoyaltyProgramId = doc.getAnyString("currentLoyaltyProgramId").takeIf { it.isNotBlank() } ?: existingLocal?.currentLoyaltyProgramId,
                        loyaltyHistoryJson = if (isLocalNewer && existingLocal!!.loyaltyHistoryJson.isNotBlank() && existingLocal.loyaltyHistoryJson != "[]") existingLocal.loyaltyHistoryJson else doc.getAnyString("loyaltyHistoryJson", default = "[]"),
                        rewardPointsBalance = finalReward,
                        giftPointsBalance = finalGift,
                        isEnrolledInLoyalty = doc.getAnyBoolean("isEnrolledInLoyalty", default = true),
                        birthday = doc.getAnyString("birthday", "birthDate", "dob").takeIf { it.isNotBlank() } ?: existingLocal?.birthday,
                        lastVisitTimestamp = doc.getAnyLong("lastVisitTimestamp").takeIf { it > 0 } ?: existingLocal?.lastVisitTimestamp,
                        updatedAt = maxOf(cloudUpdatedAt, existingLocal?.updatedAt ?: 0L)
                    )
                } catch (e: Exception) { null }
            }
            if (customers.isNotEmpty()) {
                db.customerDao().insertAll(customers)
            }

            // 6. Restore Bills & Orders
            _syncState.value = SyncState.Syncing("Restoring bills & invoices...")
            val billDocs = getDocsFromMultipleLocations(realUid, "bills", "orders", "invoices", "sales")
            val bills = billDocs.mapNotNull { doc ->
                try {
                    val parsedItems = parseBillItemsFromDoc(doc)
                    val billNumber = doc.getAnyString("billNumber", "billNo", "orderNumber", "orderNo", "invoiceNo", "number", default = doc.id)
                    BillEntity(
                        id = doc.id,
                        billNumber = billNumber,
                        restaurantId = doc.getAnyString("restaurantId", "restId"),
                        restaurantName = doc.getAnyString("restaurantName", "restName"),
                        orderType = doc.getAnyString("orderType", "type", default = "DINE_IN"),
                        tableId = doc.getAnyString("tableId").takeIf { it.isNotBlank() },
                        tableName = doc.getAnyString("tableName").takeIf { it.isNotBlank() },
                        customerName = doc.getAnyString("customerName", "customer_name", "clientName", "name"),
                        customerPhone = doc.getAnyString("customerPhone", "customer_phone", "clientPhone", "phone"),
                        items = parsedItems,
                        subtotal = doc.getAnyDouble("subtotal", "subTotal", "grossAmount"),
                        discountType = doc.getAnyString("discountType", default = "NONE"),
                        discountValue = doc.getAnyDouble("discountValue"),
                        discountAmount = doc.getAnyDouble("discountAmount", "discount"),
                        totalAmount = doc.getAnyDouble("totalAmount", "total", "grandTotal", "amount"),
                        paymentMethod = doc.getAnyString("paymentMethod", "paymentMode", "mode", default = "CASH"),
                        cashAmount = doc.getAnyDouble("cashAmount"),
                        upiAmount = doc.getAnyDouble("upiAmount"),
                        isSettled = doc.getAnyBoolean("isSettled", "settled", "paid", default = true),
                        isStockDeducted = doc.getAnyBoolean("isStockDeducted"),
                        totalFoodCost = doc.getAnyDouble("totalFoodCost", "foodCost"),
                        appliedRewardType = doc.getAnyString("appliedRewardType", default = "NONE"),
                        appliedOfferId = doc.getAnyString("appliedOfferId").takeIf { it.isNotBlank() },
                        appliedOfferName = doc.getAnyString("appliedOfferName"),
                        rewardPointsEarned = doc.getAnyInt("rewardPointsEarned"),
                        pointsRedeemed = doc.getAnyInt("pointsRedeemed"),
                        rewardPointsRedeemed = doc.getAnyInt("rewardPointsRedeemed"),
                        giftPointsRedeemed = doc.getAnyInt("giftPointsRedeemed"),
                        isVoided = doc.getAnyBoolean("isVoided", "voided"),
                        status = doc.getAnyString("status", default = if (doc.getAnyBoolean("isCancelled", "cancelled")) "CANCELLED" else "SETTLED"),
                        isCancelled = doc.getAnyBoolean("isCancelled", "cancelled"),
                        cancellationReason = doc.getAnyString("cancellationReason", "cancelReason"),
                        cancelledAt = doc.getLong("cancelledAt"),
                        billTimestamp = doc.getAnyLong("billTimestamp", "timestamp", "createdAt", "date", default = System.currentTimeMillis()),
                        createdAt = doc.getAnyLong("createdAt", "timestamp", default = System.currentTimeMillis()),
                        updatedAt = doc.getAnyLong("updatedAt", "timestamp", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) {
                    Log.w("FirestoreSyncManager", "Error parsing bill doc ${doc.id}", e)
                    null
                }
            }
            if (bills.isNotEmpty()) {
                db.billDao().insertAll(bills)
            }

            // 6b. Restore Customer Payments
            _syncState.value = SyncState.Syncing("Restoring customer payments...")
            val cpDocs = getDocsFromMultipleLocations(realUid, "customer_payments", "payments")
            val restoredPayments = cpDocs.mapNotNull { doc ->
                try {
                    CustomerPaymentEntity(
                        id = doc.id,
                        customerId = doc.getAnyString("customerId"),
                        customerName = doc.getAnyString("customerName", "name"),
                        customerPhone = doc.getAnyString("customerPhone", "phone"),
                        amount = doc.getAnyDouble("amount"),
                        paymentMode = doc.getAnyString("paymentMode", "paymentMethod", default = "CASH"),
                        notes = doc.getAnyString("notes"),
                        timestamp = doc.getAnyLong("timestamp", default = System.currentTimeMillis()),
                        createdAt = doc.getAnyLong("createdAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (restoredPayments.isNotEmpty()) {
                db.customerPaymentDao().insertAll(restoredPayments)
            }

            // 7. Restore Inventory
            _syncState.value = SyncState.Syncing("Restoring inventory...")
            val invDocs = getDocsFromMultipleLocations(realUid, "inventory", "inventory_items", "stock")
            val invItems = invDocs.mapNotNull { doc ->
                try {
                    val name = doc.getAnyString("name", "itemName", "title")
                    if (name.isBlank()) return@mapNotNull null
                    InventoryItemEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId"),
                        name = name,
                        unit = doc.getAnyString("unit", default = "pcs"),
                        openingStock = doc.getAnyDouble("openingStock"),
                        currentStock = doc.getAnyDouble("currentStock", "stock", "quantity"),
                        lowStockThreshold = doc.getAnyDouble("lowStockThreshold", default = 5.0),
                        purchasePrice = doc.getAnyDouble("purchasePrice", "purchaseRate", "baseRate", "rate"),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (invItems.isNotEmpty()) {
                db.inventoryDao().insertAllItems(invItems)
            }

            // 7b. Restore Inventory Batches
            _syncState.value = SyncState.Syncing("Restoring inventory batches...")
            val batchDocs = getDocsFromMultipleLocations(realUid, "inventory_batches", "batches")
            val restoredBatches = batchDocs.mapNotNull { doc ->
                try {
                    val isConsumed = doc.getAnyBoolean("isConsumed")
                    val remaining = doc.getAnyDouble("remainingQuantity", "remainingQty")
                    val status = doc.getAnyString("status", default = if (isConsumed || remaining <= 0.000001) "ARCHIVED" else "ACTIVE")
                    val timestamp = doc.getAnyLong("addedAt", "timestamp", "createdAt", default = System.currentTimeMillis())
                    val initQty = doc.getAnyDouble("quantityAdded", "initialQuantity")
                    val pRate = doc.getAnyDouble("ratePerUnit", "purchaseRate", "rate")
                    InventoryBatchEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId"),
                        inventoryItemId = doc.getAnyString("inventoryItemId", "itemId"),
                        itemName = doc.getAnyString("itemName", "name"),
                        initialQuantity = initQty,
                        remainingQuantity = remaining,
                        purchaseRate = pRate,
                        unit = doc.getAnyString("unit", default = "KG"),
                        batchType = doc.getAnyString("batchType", default = "PURCHASE"),
                        notes = doc.getAnyString("notes"),
                        timestamp = timestamp,
                        isConsumed = isConsumed,
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis()),
                        createdAt = doc.getAnyLong("createdAt", default = timestamp),
                        createdByUserId = doc.getAnyString("createdByUserId"),
                        referenceTransactionId = doc.getAnyString("referenceTransactionId"),
                        status = status
                    )
                } catch (e: Exception) { null }
            }
            if (restoredBatches.isNotEmpty()) {
                db.inventoryBatchDao().insertAllBatches(restoredBatches)
            }

            // 8. Restore Expenses & Categories
            _syncState.value = SyncState.Syncing("Restoring expenses...")
            val expCatDocs = getDocsFromMultipleLocations(realUid, "expense_categories")
            val expCats = expCatDocs.mapNotNull { doc ->
                try {
                    ExpenseCategoryEntity(
                        id = doc.id,
                        name = doc.getAnyString("name", "categoryName"),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (expCats.isNotEmpty()) {
                db.expenseDao().insertAllCategories(expCats)
            }

            val expDocs = getDocsFromMultipleLocations(realUid, "expenses")
            val expenses = expDocs.mapNotNull { doc ->
                try {
                    ExpenseEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId"),
                        categoryId = doc.getAnyString("categoryId"),
                        categoryName = doc.getAnyString("categoryName", "category"),
                        amount = doc.getAnyDouble("amount"),
                        paymentMethod = doc.getAnyString("paymentMethod", default = "CASH"),
                        description = doc.getAnyString("description", "notes"),
                        timestamp = doc.getAnyLong("timestamp", default = System.currentTimeMillis()),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (expenses.isNotEmpty()) {
                db.expenseDao().insertAll(expenses)
            }

            // 9. Restore Cash Registers
            _syncState.value = SyncState.Syncing("Restoring cash register entries...")
            val regDocs = getDocsFromMultipleLocations(realUid, "cash_registers", "registers")
            val registers = regDocs.mapNotNull { doc ->
                try {
                    CashRegisterEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId"),
                        dateString = doc.getAnyString("dateString", "date"),
                        openingCash = doc.getAnyDouble("openingCash"),
                        cashSales = doc.getAnyDouble("cashSales"),
                        cashExpenses = doc.getAnyDouble("cashExpenses"),
                        cashAdded = doc.getAnyDouble("cashAdded"),
                        cashWithdrawn = doc.getAnyDouble("cashWithdrawn"),
                        expectedCash = doc.getAnyDouble("expectedCash"),
                        closingCashActual = doc.getAnyDouble("closingCashActual").takeIf { doc.get("closingCashActual") != null },
                        difference = doc.getAnyDouble("difference").takeIf { doc.get("difference") != null },
                        status = doc.getAnyString("status", default = "OPEN"),
                        openedAt = doc.getAnyLong("openedAt", default = System.currentTimeMillis()),
                        closedAt = doc.getAnyLong("closedAt").takeIf { doc.get("closedAt") != null },
                        notes = doc.getAnyString("notes"),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (registers.isNotEmpty()) {
                db.cashRegisterDao().insertAll(registers)
            }

            // 10. Restore Recipes
            _syncState.value = SyncState.Syncing("Restoring recipes & ingredients...")
            val recipeDocs = getDocsFromMultipleLocations(realUid, "recipes")
            val recipes = recipeDocs.mapNotNull { doc ->
                try {
                    val mItemId = doc.getAnyString("menuItemId", default = doc.id)
                    val ingredientsRaw = doc.getAnyString("ingredientsJson")
                    val parsedIngredients = if (ingredientsRaw.isNotBlank()) {
                        converters.toRecipeIngredientList(ingredientsRaw)
                    } else {
                        val rawList = doc.get("ingredients") as? List<Map<String, Any>>
                        rawList?.mapNotNull { m ->
                            try {
                                RecipeIngredient(
                                    inventoryItemId = m["inventoryItemId"] as? String ?: m["itemId"] as? String ?: "",
                                    inventoryItemName = m["inventoryItemName"] as? String ?: m["itemName"] as? String ?: "",
                                    quantity = (m["quantity"] as? Number)?.toDouble() ?: 0.0,
                                    usageUnit = m["usageUnit"] as? String ?: "pcs",
                                    isTakeawayExtra = m["isTakeawayExtra"] as? Boolean ?: false
                                )
                            } catch (e: Exception) { null }
                        } ?: emptyList()
                    }
                    RecipeEntity(
                        menuItemId = mItemId,
                        restaurantId = doc.getAnyString("restaurantId"),
                        menuItemName = doc.getAnyString("menuItemName"),
                        ingredients = parsedIngredients,
                        notes = doc.getAnyString("notes"),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (recipes.isNotEmpty()) {
                db.recipeDao().insertAll(recipes)
            }

            // 11. Restore Combos
            _syncState.value = SyncState.Syncing("Restoring combos...")
            val comboDocs = getDocsFromMultipleLocations(realUid, "combos")
            val combos = comboDocs.mapNotNull { doc ->
                try {
                    val slotsRaw = doc.getAnyString("slotsJson", default = "[]")
                    ComboEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId"),
                        name = doc.getAnyString("name"),
                        price = doc.getAnyDouble("price"),
                        badge = doc.getAnyString("badge"),
                        description = doc.getAnyString("description"),
                        isActive = doc.getAnyBoolean("isActive", default = true),
                        slots = converters.toComboSlotList(slotsRaw),
                        createdAt = doc.getAnyLong("createdAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (combos.isNotEmpty()) {
                db.comboDao().insertAll(combos)
            }

            // 12. Restore Stock Counts
            _syncState.value = SyncState.Syncing("Restoring stock counts...")
            val scDocs = getDocsFromMultipleLocations(realUid, "stock_counts")
            val restoredStockCounts = scDocs.mapNotNull { doc ->
                try {
                    val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    val parsedItems = rawItems.map { m ->
                        val rawAdjustments = m["batchAdjustments"] as? List<Map<String, Any>> ?: emptyList()
                        val parsedAdjustments = rawAdjustments.map { a ->
                            BatchAdjustmentRecord(
                                batchId = a["batchId"] as? String ?: "",
                                initialQuantity = (a["initialQuantity"] as? Number)?.toDouble() ?: 0.0,
                                previousRemainingQty = (a["previousRemainingQty"] as? Number)?.toDouble() ?: 0.0,
                                newRemainingQty = (a["newRemainingQty"] as? Number)?.toDouble() ?: 0.0,
                                adjustedQty = (a["adjustedQty"] as? Number)?.toDouble() ?: 0.0,
                                unitRate = (a["unitRate"] as? Number)?.toDouble() ?: 0.0,
                                batchNotes = a["batchNotes"] as? String ?: ""
                            )
                        }
                        StockCountItem(
                            inventoryItemId = m["inventoryItemId"] as? String ?: "",
                            itemName = m["itemName"] as? String ?: "",
                            unit = m["unit"] as? String ?: "KG",
                            systemStock = (m["systemStock"] as? Number)?.toDouble() ?: 0.0,
                            actualStock = (m["actualStock"] as? Number)?.toDouble() ?: 0.0,
                            varianceQty = (m["varianceQty"] as? Number)?.toDouble() ?: 0.0,
                            unitRate = (m["unitRate"] as? Number)?.toDouble() ?: 0.0,
                            varianceCost = (m["varianceCost"] as? Number)?.toDouble() ?: 0.0,
                            batchAdjustments = parsedAdjustments
                        )
                    }
                    StockCountEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId"),
                        countDate = doc.getAnyLong("countDate", default = System.currentTimeMillis()),
                        title = doc.getAnyString("title"),
                        totalItemsChecked = doc.getAnyInt("totalItemsChecked", default = parsedItems.size),
                        totalVarianceCost = doc.getAnyDouble("totalVarianceCost"),
                        isAdjusted = doc.getAnyBoolean("isAdjusted"),
                        notes = doc.getAnyString("notes"),
                        items = parsedItems,
                        createdAt = doc.getAnyLong("createdAt", default = System.currentTimeMillis()),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (restoredStockCounts.isNotEmpty()) {
                db.stockCountDao().insertAllStockCounts(restoredStockCounts)
            }

            // 12b. Restore Offers
            _syncState.value = SyncState.Syncing("Restoring offers...")
            val offerDocs = getDocsFromMultipleLocations(realUid, "offers")
            val restoredOffers = offerDocs.mapNotNull { doc ->
                try {
                    OfferEntity(
                        id = doc.id,
                        restaurantId = doc.getAnyString("restaurantId"),
                        name = doc.getAnyString("name"),
                        offerType = doc.getAnyString("offerType", default = "VISIT_BASED"),
                        rewardType = doc.getAnyString("rewardType", default = "FLAT_DISCOUNT"),
                        rewardValue = doc.getAnyDouble("rewardValue"),
                        minBillAmount = doc.getAnyDouble("minBillAmount"),
                        applicableOrderTypes = doc.getAnyString("applicableOrderTypes", default = "DINE_IN,TAKEAWAY"),
                        isActive = doc.getAnyBoolean("isActive", default = true),
                        totalVisitsInProgram = doc.getAnyInt("totalVisitsInProgram", default = 6),
                        visitNumber = doc.getAnyInt("visitNumber", default = 1),
                        validityDays = doc.getAnyInt("validityDays", default = 45),
                        freeItemQuantity = doc.getAnyInt("freeItemQuantity", default = 1),
                        eligibleMenuItemIds = doc.getAnyString("eligibleMenuItemIds", default = "[]"),
                        allowSameItemMultipleTimes = doc.getAnyBoolean("allowSameItemMultipleTimes", default = true),
                        cooldownDays = doc.getAnyInt("cooldownDays", default = 0),
                        createdAt = doc.getAnyLong("createdAt", default = System.currentTimeMillis()),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (restoredOffers.isNotEmpty()) {
                db.offerDao().insertAll(restoredOffers)
            }

            // 12c. Restore Points Batches
            _syncState.value = SyncState.Syncing("Restoring points batches...")
            val pbDocs = getDocsFromMultipleLocations(realUid, "points_batches")
            val restoredPointsBatches = pbDocs.mapNotNull { doc ->
                try {
                    PointsBatchEntity(
                        id = doc.id,
                        customerId = doc.getAnyString("customerId"),
                        customerPhone = doc.getAnyString("customerPhone"),
                        customerName = doc.getAnyString("customerName"),
                        batchType = doc.getAnyString("batchType", default = "REWARD"),
                        initialPoints = doc.getAnyInt("initialPoints"),
                        remainingPoints = doc.getAnyInt("remainingPoints"),
                        earnDate = doc.getAnyLong("earnDate", default = System.currentTimeMillis()),
                        expiryDate = doc.getAnyLong("expiryDate", default = System.currentTimeMillis()),
                        billId = doc.getAnyString("billId").takeIf { it.isNotBlank() },
                        billNumber = doc.getAnyString("billNumber").takeIf { it.isNotBlank() },
                        notes = doc.getAnyString("notes"),
                        isExpired = doc.getAnyBoolean("isExpired"),
                        reminderSent = doc.getAnyBoolean("reminderSent"),
                        reminderSentDate = doc.getAnyLong("reminderSentDate").takeIf { it > 0 },
                        createdAt = doc.getAnyLong("createdAt", default = System.currentTimeMillis()),
                        updatedAt = doc.getAnyLong("updatedAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (restoredPointsBatches.isNotEmpty()) {
                db.pointsBatchDao().insertAll(restoredPointsBatches)
            }

            // 12d. Restore Points Ledger
            _syncState.value = SyncState.Syncing("Restoring points ledger...")
            val plDocs = getDocsFromMultipleLocations(realUid, "points_ledger")
            val restoredLedger = plDocs.mapNotNull { doc ->
                try {
                    PointsLedgerEntity(
                        id = doc.id,
                        customerId = doc.getAnyString("customerId"),
                        customerPhone = doc.getAnyString("customerPhone"),
                        customerName = doc.getAnyString("customerName"),
                        transactionType = doc.getAnyString("transactionType", default = "EARNED"),
                        pointsAmount = doc.getAnyInt("pointsAmount"),
                        balanceType = doc.getAnyString("balanceType", default = "REWARD"),
                        billId = doc.getAnyString("billId").takeIf { it.isNotBlank() },
                        billNumber = doc.getAnyString("billNumber").takeIf { it.isNotBlank() },
                        expiryDate = doc.getAnyLong("expiryDate").takeIf { it > 0 },
                        rewardPointsBalanceAfter = doc.getAnyInt("rewardPointsBalanceAfter"),
                        giftPointsBalanceAfter = doc.getAnyInt("giftPointsBalanceAfter"),
                        notes = doc.getAnyString("notes"),
                        timestamp = doc.getAnyLong("timestamp", default = System.currentTimeMillis()),
                        createdAt = doc.getAnyLong("createdAt", default = System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
            if (restoredLedger.isNotEmpty()) {
                db.pointsLedgerDao().insertAll(restoredLedger)
            }

            // 12e. Restore Claimed Rewards and synchronize customer loyalty history
            val claimedRewardDocs = getDocsFromMultipleLocations(realUid, "claimed_rewards")
            for (cDoc in claimedRewardDocs) {
                try {
                    val phone = cDoc.getAnyString("customerPhone", "phone").trim()
                    val visitNum = cDoc.getAnyInt("visitNumber", default = 0)
                    val offerName = cDoc.getAnyString("offerName", "name")
                    val offerId = cDoc.getAnyString("offerId")
                    val billNum = cDoc.getAnyString("billNumber")
                    val claimedAt = cDoc.getAnyLong("claimedAt", "timestamp", default = System.currentTimeMillis())

                    if (phone.isNotBlank() && visitNum > 0) {
                        val cust = db.customerDao().getCustomerByContact(phone)
                        if (cust != null) {
                            val historyList = converters.toLoyaltyHistoryList(cust.loyaltyHistoryJson).toMutableList()
                            val alreadyHas = historyList.any { it.visitNumber == visitNum && !it.rewardGiven.isNullOrBlank() }
                            if (!alreadyHas) {
                                historyList.add(
                                    LoyaltyHistoryItem(
                                        eventType = "VISIT_COMPLETED",
                                        visitNumber = visitNum,
                                        billId = "",
                                        billNumber = billNum,
                                        billAmount = 0.0,
                                        offerName = offerName.takeIf { it.isNotBlank() },
                                        rewardGiven = offerName.ifBlank { "Claimed Reward" },
                                        timestamp = claimedAt
                                    )
                                )
                                val updatedCust = cust.copy(
                                    loyaltyHistoryJson = converters.fromLoyaltyHistoryList(historyList),
                                    updatedAt = System.currentTimeMillis()
                                )
                                db.customerDao().insertOrUpdate(updatedCust)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreSyncManager", "Error restoring claimed reward", e)
                }
            }

            val totalRestored = restaurants.size + categories.size + menuItems.size +
                    tables.size + customers.size + bills.size + invItems.size + restoredBatches.size +
                    expenses.size + registers.size + recipes.size + combos.size + restoredStockCounts.size +
                    restoredOffers.size + restoredPointsBatches.size + restoredLedger.size + claimedRewardDocs.size

            _lastSyncTimestamp.value = System.currentTimeMillis()
            val msg = "Restored $totalRestored records from cloud successfully"
            _syncState.value = SyncState.Success(msg)
            Result.success(msg)
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Restore error", e)
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Restore failed")
            Result.failure(e)
        }
    }

    // Direct background single-record syncs
    suspend fun uploadBill(userId: String, bill: BillEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val itemsListMap = bill.items.map { item ->
                hashMapOf(
                    "dishId" to item.dishId,
                    "dishName" to item.dishName,
                    "unitPrice" to item.unitPrice,
                    "quantity" to item.quantity,
                    "totalPrice" to item.totalPrice,
                    "notes" to item.notes,
                    "isFree" to item.isFree
                )
            }
            val data = hashMapOf(
                "id" to bill.id,
                "billNumber" to bill.billNumber,
                "restaurantId" to bill.restaurantId,
                "restaurantName" to bill.restaurantName,
                "orderType" to bill.orderType,
                "tableId" to bill.tableId,
                "tableName" to bill.tableName,
                "customerName" to bill.customerName,
                "customerPhone" to bill.customerPhone,
                "itemsJson" to converters.fromBillItemList(bill.items),
                "items" to itemsListMap,
                "subtotal" to bill.subtotal,
                "discountType" to bill.discountType,
                "discountValue" to bill.discountValue,
                "discountAmount" to bill.discountAmount,
                "totalAmount" to bill.totalAmount,
                "paymentMethod" to bill.paymentMethod,
                "cashAmount" to bill.cashAmount,
                "upiAmount" to bill.upiAmount,
                "isSettled" to bill.isSettled,
                "isStockDeducted" to bill.isStockDeducted,
                "totalFoodCost" to bill.totalFoodCost,
                "appliedRewardType" to bill.appliedRewardType,
                "appliedOfferId" to bill.appliedOfferId,
                "appliedOfferName" to bill.appliedOfferName,
                "rewardPointsEarned" to bill.rewardPointsEarned,
                "pointsRedeemed" to bill.pointsRedeemed,
                "rewardPointsRedeemed" to bill.rewardPointsRedeemed,
                "giftPointsRedeemed" to bill.giftPointsRedeemed,
                "isVoided" to bill.isVoided,
                "status" to bill.status,
                "isCancelled" to bill.isCancelled,
                "cancellationReason" to bill.cancellationReason,
                "cancelledAt" to bill.cancelledAt,
                "billTimestamp" to bill.billTimestamp,
                "createdAt" to bill.createdAt,
                "updatedAt" to bill.updatedAt
            )
            userRef(uid).collection("bills").document(bill.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Bill cloud sync deferred (offline)", e)
        }
    }

    suspend fun deleteBill(userId: String, billId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("bills").document(billId).delete().await()
            // Also delete any deductions linked to this bill
            val deductionsSnapshot = userRef(uid).collection("bill_deductions")
                .whereEqualTo("billId", billId)
                .get()
                .await()
            for (doc in deductionsSnapshot.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Bill cloud deletion deferred", e)
        }
    }

    suspend fun atomicUpdateCustomerLoyaltyBalances(
        userId: String,
        customerId: String,
        rewardPointsDelta: Int,
        giftPointsDelta: Int,
        customer: CustomerEntity
    ) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val docRef = userRef(uid).collection("customers").document(customerId)
            val updates = hashMapOf<String, Any>(
                "id" to customer.id,
                "name" to customer.name,
                "contactNumber" to customer.contactNumber,
                "isCreditCustomer" to customer.isCreditCustomer,
                "loyaltyVisitCount" to customer.loyaltyVisitCount,
                "loyaltyHistoryJson" to customer.loyaltyHistoryJson,
                "isEnrolledInLoyalty" to customer.isEnrolledInLoyalty,
                "updatedAt" to customer.updatedAt
            )
            customer.loyaltyStartDate?.let { updates["loyaltyStartDate"] = it }
            customer.loyaltyExpiryDate?.let { updates["loyaltyExpiryDate"] = it }
            customer.currentLoyaltyProgramId?.let { updates["currentLoyaltyProgramId"] = it }
            customer.birthday?.let { updates["birthday"] = it }
            customer.lastVisitTimestamp?.let { updates["lastVisitTimestamp"] = it }

            if (rewardPointsDelta != 0) {
                updates["rewardPointsBalance"] = FieldValue.increment(rewardPointsDelta.toLong())
            } else {
                updates["rewardPointsBalance"] = customer.rewardPointsBalance
            }
            if (giftPointsDelta != 0) {
                updates["giftPointsBalance"] = FieldValue.increment(giftPointsDelta.toLong())
            } else {
                updates["giftPointsBalance"] = customer.giftPointsBalance
            }
            docRef.set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Atomic customer loyalty update deferred", e)
        }
    }

    suspend fun incrementCustomerRewardPoints(
        userId: String,
        customerId: String,
        earnedPoints: Int,
        newBalance: Int
    ) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank() || earnedPoints <= 0) return@withContext
        try {
            val docRef = userRef(uid).collection("customers").document(customerId)
            val updates = hashMapOf<String, Any>(
                "rewardPointsBalance" to FieldValue.increment(earnedPoints.toLong()),
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Atomic customer reward points increment deferred", e)
        }
    }

    suspend fun uploadCustomer(userId: String, customer: CustomerEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to customer.id,
                "name" to customer.name,
                "contactNumber" to customer.contactNumber,
                "isCreditCustomer" to customer.isCreditCustomer,
                "loyaltyVisitCount" to customer.loyaltyVisitCount,
                "loyaltyStartDate" to customer.loyaltyStartDate,
                "loyaltyExpiryDate" to customer.loyaltyExpiryDate,
                "currentLoyaltyProgramId" to customer.currentLoyaltyProgramId,
                "loyaltyHistoryJson" to customer.loyaltyHistoryJson,
                "rewardPointsBalance" to customer.rewardPointsBalance,
                "giftPointsBalance" to customer.giftPointsBalance,
                "isEnrolledInLoyalty" to customer.isEnrolledInLoyalty,
                "lastVisitTimestamp" to customer.lastVisitTimestamp,
                "updatedAt" to customer.updatedAt
            )
            userRef(uid).collection("customers").document(customer.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Customer cloud sync deferred", e)
        }
    }

    suspend fun uploadOffer(userId: String, offer: OfferEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to offer.id,
                "restaurantId" to offer.restaurantId,
                "name" to offer.name,
                "offerType" to offer.offerType,
                "rewardType" to offer.rewardType,
                "rewardValue" to offer.rewardValue,
                "minBillAmount" to offer.minBillAmount,
                "applicableOrderTypes" to offer.applicableOrderTypes,
                "isActive" to offer.isActive,
                "totalVisitsInProgram" to offer.totalVisitsInProgram,
                "visitNumber" to offer.visitNumber,
                "validityDays" to offer.validityDays,
                "freeItemQuantity" to offer.freeItemQuantity,
                "eligibleMenuItemIds" to offer.eligibleMenuItemIds,
                "allowSameItemMultipleTimes" to offer.allowSameItemMultipleTimes,
                "cooldownDays" to offer.cooldownDays,
                "createdAt" to offer.createdAt,
                "updatedAt" to offer.updatedAt
            )
            userRef(uid).collection("offers").document(offer.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Offer sync deferred", e)
        }
    }

    suspend fun deleteOffer(userId: String, offerId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("offers").document(offerId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete offer sync deferred", e)
        }
    }

    suspend fun uploadPointsBatch(userId: String, batch: PointsBatchEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to batch.id,
                "customerId" to batch.customerId,
                "customerPhone" to batch.customerPhone,
                "customerName" to batch.customerName,
                "batchType" to batch.batchType,
                "initialPoints" to batch.initialPoints,
                "remainingPoints" to batch.remainingPoints,
                "earnDate" to batch.earnDate,
                "expiryDate" to batch.expiryDate,
                "billId" to batch.billId,
                "billNumber" to batch.billNumber,
                "notes" to batch.notes,
                "isExpired" to batch.isExpired,
                "reminderSent" to batch.reminderSent,
                "reminderSentDate" to batch.reminderSentDate,
                "createdAt" to batch.createdAt,
                "updatedAt" to batch.updatedAt
            )
            userRef(uid).collection("points_batches").document(batch.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Points batch sync deferred", e)
        }
    }

    suspend fun uploadPointsLedger(userId: String, entry: PointsLedgerEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to entry.id,
                "customerId" to entry.customerId,
                "customerPhone" to entry.customerPhone,
                "customerName" to entry.customerName,
                "transactionType" to entry.transactionType,
                "pointsAmount" to entry.pointsAmount,
                "balanceType" to entry.balanceType,
                "billId" to entry.billId,
                "billNumber" to entry.billNumber,
                "expiryDate" to entry.expiryDate,
                "rewardPointsBalanceAfter" to entry.rewardPointsBalanceAfter,
                "giftPointsBalanceAfter" to entry.giftPointsBalanceAfter,
                "notes" to entry.notes,
                "timestamp" to entry.timestamp,
                "createdAt" to entry.createdAt
            )
            userRef(uid).collection("points_ledger").document(entry.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Points ledger sync deferred", e)
        }
    }

    suspend fun uploadClaimedReward(
        userId: String,
        customerPhone: String,
        visitNumber: Int,
        offerId: String,
        offerName: String,
        billNumber: String
    ) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val docId = "${customerPhone.filter { it.isDigit() }}_visit_$visitNumber"
            val data = hashMapOf(
                "id" to docId,
                "customerPhone" to customerPhone.trim(),
                "visitNumber" to visitNumber,
                "offerId" to offerId,
                "offerName" to offerName,
                "billNumber" to billNumber,
                "claimedAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            userRef(uid).collection("claimed_rewards").document(docId).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Claimed reward sync deferred", e)
        }
    }

    suspend fun uploadCustomerPayment(userId: String, payment: CustomerPaymentEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to payment.id,
                "customerId" to payment.customerId,
                "customerName" to payment.customerName,
                "customerPhone" to payment.customerPhone,
                "amount" to payment.amount,
                "paymentMode" to payment.paymentMode,
                "notes" to payment.notes,
                "timestamp" to payment.timestamp,
                "createdAt" to payment.createdAt
            )
            userRef(uid).collection("customer_payments").document(payment.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Customer payment cloud sync deferred", e)
        }
    }

    suspend fun uploadInventoryItem(userId: String, item: InventoryItemEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to item.id,
                "restaurantId" to item.restaurantId,
                "name" to item.name,
                "unit" to item.unit,
                "openingStock" to item.openingStock,
                "currentStock" to item.currentStock,
                "lowStockThreshold" to item.lowStockThreshold,
                "purchasePrice" to item.purchasePrice,
                "baseRate" to item.purchasePrice,
                "updatedAt" to item.updatedAt
            )
            userRef(uid).collection("inventory").document(item.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Inventory sync deferred", e)
        }
    }

    suspend fun deleteInventoryItem(userId: String, itemId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("inventory").document(itemId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete inventory cloud sync deferred", e)
        }
    }

    suspend fun uploadBatch(userId: String, batch: InventoryBatchEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to batch.id,
                "restaurantId" to batch.restaurantId,
                "inventoryItemId" to batch.inventoryItemId,
                "itemName" to batch.itemName,
                "quantityAdded" to batch.initialQuantity,
                "initialQuantity" to batch.initialQuantity,
                "remainingQuantity" to batch.remainingQuantity,
                "ratePerUnit" to batch.purchaseRate,
                "purchaseRate" to batch.purchaseRate,
                "unit" to batch.unit,
                "batchType" to batch.batchType,
                "notes" to batch.notes,
                "addedAt" to batch.timestamp,
                "timestamp" to batch.timestamp,
                "isConsumed" to batch.isConsumed,
                "updatedAt" to batch.updatedAt,
                "createdAt" to batch.createdAt,
                "createdByUserId" to batch.createdByUserId,
                "referenceTransactionId" to batch.referenceTransactionId,
                "status" to batch.status
            )
            userRef(uid).collection("inventory_batches").document(batch.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Batch sync deferred", e)
        }
    }

    suspend fun deleteBatch(userId: String, batchId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("inventory_batches").document(batchId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete batch cloud sync deferred", e)
        }
    }

    suspend fun uploadStockCount(userId: String, stockCount: StockCountEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val itemsList = stockCount.items.map { item ->
                val adjsList = item.batchAdjustments.map { a ->
                    hashMapOf(
                        "batchId" to a.batchId,
                        "initialQuantity" to a.initialQuantity,
                        "previousRemainingQty" to a.previousRemainingQty,
                        "newRemainingQty" to a.newRemainingQty,
                        "adjustedQty" to a.adjustedQty,
                        "unitRate" to a.unitRate,
                        "batchNotes" to a.batchNotes
                    )
                }
                hashMapOf(
                    "inventoryItemId" to item.inventoryItemId,
                    "itemName" to item.itemName,
                    "unit" to item.unit,
                    "systemStock" to item.systemStock,
                    "actualStock" to item.actualStock,
                    "varianceQty" to item.varianceQty,
                    "unitRate" to item.unitRate,
                    "varianceCost" to item.varianceCost,
                    "batchAdjustments" to adjsList
                )
            }
            val data = hashMapOf(
                "id" to stockCount.id,
                "restaurantId" to stockCount.restaurantId,
                "countDate" to stockCount.countDate,
                "title" to stockCount.title,
                "totalItemsChecked" to stockCount.totalItemsChecked,
                "totalVarianceCost" to stockCount.totalVarianceCost,
                "isAdjusted" to stockCount.isAdjusted,
                "notes" to stockCount.notes,
                "items" to itemsList,
                "createdAt" to stockCount.createdAt,
                "updatedAt" to stockCount.updatedAt
            )
            userRef(uid).collection("stock_counts").document(stockCount.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Stock count cloud sync deferred", e)
        }
    }

    suspend fun deleteStockCount(userId: String, countId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("stock_counts").document(countId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete stock count cloud sync deferred", e)
        }
    }

    suspend fun uploadExpense(userId: String, expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to expense.id,
                "restaurantId" to expense.restaurantId,
                "categoryId" to expense.categoryId,
                "categoryName" to expense.categoryName,
                "amount" to expense.amount,
                "paymentMethod" to expense.paymentMethod,
                "description" to expense.description,
                "timestamp" to expense.timestamp,
                "updatedAt" to expense.updatedAt
            )
            userRef(uid).collection("expenses").document(expense.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Expense sync deferred", e)
        }
    }

    suspend fun deleteExpense(userId: String, expenseId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("expenses").document(expenseId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete expense cloud sync deferred", e)
        }
    }

    suspend fun uploadExpenseCategory(userId: String, category: ExpenseCategoryEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to category.id,
                "name" to category.name,
                "updatedAt" to category.updatedAt
            )
            userRef(uid).collection("expense_categories").document(category.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Expense category sync deferred", e)
        }
    }

    suspend fun deleteExpenseCategory(userId: String, categoryId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("expense_categories").document(categoryId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete expense category sync deferred", e)
        }
    }

    suspend fun uploadCashRegister(userId: String, register: CashRegisterEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to register.id,
                "restaurantId" to register.restaurantId,
                "dateString" to register.dateString,
                "openingCash" to register.openingCash,
                "cashSales" to register.cashSales,
                "cashExpenses" to register.cashExpenses,
                "cashAdded" to register.cashAdded,
                "cashWithdrawn" to register.cashWithdrawn,
                "expectedCash" to register.expectedCash,
                "closingCashActual" to register.closingCashActual,
                "difference" to register.difference,
                "status" to register.status,
                "openedAt" to register.openedAt,
                "closedAt" to register.closedAt,
                "notes" to register.notes,
                "updatedAt" to register.updatedAt
            )
            userRef(uid).collection("cash_registers").document(register.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Cash register sync deferred", e)
        }
    }

    suspend fun uploadRestaurant(userId: String, restaurant: RestaurantEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to restaurant.id,
                "name" to restaurant.name,
                "address" to restaurant.address,
                "phone" to restaurant.phone,
                "footerNote" to restaurant.footerNote,
                "logoPreset" to restaurant.logoPreset,
                "customLogoUri" to restaurant.customLogoUri,
                "customLogoBase64" to restaurant.customLogoBase64,
                "isActive" to restaurant.isActive,
                "updatedAt" to restaurant.updatedAt,
                "fssaiNumber" to restaurant.fssaiNumber,
                "isFssaiEnabled" to restaurant.isFssaiEnabled,
                "showFssaiOnBill" to restaurant.showFssaiOnBill,
                "gstNumber" to restaurant.gstNumber,
                "gstRate" to restaurant.gstRate,
                "isGstEnabled" to restaurant.isGstEnabled,
                "showGstOnBill" to restaurant.showGstOnBill,
                "billFormat" to restaurant.billFormat,
                "billPrefix" to restaurant.billPrefix,
                "billPrefixCountersJson" to restaurant.billPrefixCountersJson
            )
            userRef(uid).collection("restaurants").document(restaurant.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Restaurant sync deferred", e)
        }
    }

    suspend fun deleteRestaurant(userId: String, restaurantId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("restaurants").document(restaurantId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete restaurant cloud sync deferred", e)
        }
    }

    suspend fun uploadPointsEngineRules(userId: String, rules: PointsEngineRules) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "earnRatePercent" to rules.earnRatePercent,
                "pointValueRupees" to rules.pointValueRupees,
                "minRedemptionPoints" to rules.minRedemptionPoints,
                "maxDiscountCapPercent" to rules.maxDiscountCapPercent,
                "rewardPointsExpiryDays" to rules.rewardPointsExpiryDays,
                "giftPointsExpiryDays" to rules.giftPointsExpiryDays,
                "welcomeBonusEnabled" to rules.welcomeBonusEnabled,
                "welcomeBonusPoints" to rules.welcomeBonusPoints,
                "minBillAmountToEarn" to rules.minBillAmountToEarn,
                "autoEnrollInVisitPass" to rules.autoEnrollInVisitPass,
                "welcomeBonusExpiryDays" to rules.welcomeBonusExpiryDays,
                "updatedAt" to System.currentTimeMillis()
            )
            userRef(uid).collection("settings").document("points_engine_rules").set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Points rules sync deferred", e)
        }
    }

    suspend fun downloadPointsEngineRules(userId: String): PointsEngineRules? = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext null
        try {
            val doc = userRef(uid).collection("settings").document("points_engine_rules").get().await()
            if (!doc.exists()) return@withContext null
            val earnRatePercent = doc.getDouble("earnRatePercent") ?: 1.0
            val pointValueRupees = doc.getDouble("pointValueRupees") ?: 1.0
            val minRedemptionPoints = doc.getLong("minRedemptionPoints")?.toInt() ?: 50
            val maxDiscountCapPercent = doc.getDouble("maxDiscountCapPercent") ?: 50.0
            val rewardPointsExpiryDays = doc.getLong("rewardPointsExpiryDays")?.toInt() ?: 60
            val giftPointsExpiryDays = doc.getLong("giftPointsExpiryDays")?.toInt() ?: 30
            val welcomeBonusEnabled = doc.getBoolean("welcomeBonusEnabled") ?: false
            val welcomeBonusPoints = doc.getLong("welcomeBonusPoints")?.toInt() ?: 20
            val minBillAmountToEarn = doc.getDouble("minBillAmountToEarn") ?: 0.0
            val autoEnrollInVisitPass = doc.getBoolean("autoEnrollInVisitPass") ?: false
            val welcomeBonusExpiryDays = doc.getLong("welcomeBonusExpiryDays")?.toInt() ?: 30

            PointsEngineRules(
                earnRatePercent = earnRatePercent,
                pointValueRupees = pointValueRupees,
                minRedemptionPoints = minRedemptionPoints,
                maxDiscountCapPercent = maxDiscountCapPercent,
                rewardPointsExpiryDays = rewardPointsExpiryDays,
                giftPointsExpiryDays = giftPointsExpiryDays,
                welcomeBonusEnabled = welcomeBonusEnabled,
                welcomeBonusPoints = welcomeBonusPoints,
                minBillAmountToEarn = minBillAmountToEarn,
                autoEnrollInVisitPass = autoEnrollInVisitPass,
                welcomeBonusExpiryDays = welcomeBonusExpiryDays
            )
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Error downloading points rules", e)
            null
        }
    }

    suspend fun uploadCategory(userId: String, category: CategoryEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to category.id,
                "name" to category.name,
                "displayOrder" to category.displayOrder,
                "isDiscountEligible" to category.isDiscountEligible,
                "updatedAt" to category.updatedAt
            )
            userRef(uid).collection("categories").document(category.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Category sync deferred", e)
        }
    }

    suspend fun deleteCategory(userId: String, categoryId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("categories").document(categoryId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete category cloud sync deferred", e)
        }
    }

    suspend fun uploadMenuItem(userId: String, item: MenuItemEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to item.id,
                "categoryId" to item.categoryId,
                "categoryName" to item.categoryName,
                "name" to item.name,
                "price" to item.price,
                "isAvailable" to item.isAvailable,
                "isDiscountEligible" to item.isDiscountEligible,
                "imageUri" to item.imageUri,
                "variantsJson" to item.variantsJson,
                "addonsJson" to item.addonsJson,
                "updatedAt" to item.updatedAt
            )
            userRef(uid).collection("menu_items").document(item.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Menu item sync deferred", e)
        }
    }

    suspend fun deleteMenuItem(userId: String, menuItemId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("menu_items").document(menuItemId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete menu item cloud sync deferred", e)
        }
    }

    suspend fun uploadTable(userId: String, table: CafeTableEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to table.id,
                "restaurantId" to table.restaurantId,
                "name" to table.name,
                "capacity" to table.capacity,
                "isOccupied" to table.isOccupied,
                "activeBillId" to table.activeBillId,
                "updatedAt" to table.updatedAt
            )
            userRef(uid).collection("tables").document(table.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Table sync deferred", e)
        }
    }

    suspend fun deleteTable(userId: String, tableId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("tables").document(tableId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete table cloud sync deferred", e)
        }
    }

    suspend fun uploadRecipe(userId: String, recipe: RecipeEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val ingredientsList = recipe.ingredients.map { ing ->
                hashMapOf(
                    "inventoryItemId" to ing.inventoryItemId,
                    "inventoryItemName" to ing.inventoryItemName,
                    "quantity" to ing.quantity,
                    "usageUnit" to ing.usageUnit,
                    "isTakeawayExtra" to ing.isTakeawayExtra
                )
            }
            val data = hashMapOf(
                "menuItemId" to recipe.menuItemId,
                "restaurantId" to recipe.restaurantId,
                "menuItemName" to recipe.menuItemName,
                "ingredientsJson" to converters.fromRecipeIngredientList(recipe.ingredients),
                "ingredients" to ingredientsList,
                "notes" to recipe.notes,
                "updatedAt" to recipe.updatedAt
            )
            userRef(uid).collection("recipes").document(recipe.menuItemId).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Recipe sync deferred", e)
        }
    }

    suspend fun deleteRecipe(userId: String, menuItemId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("recipes").document(menuItemId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete recipe sync deferred", e)
        }
    }

    suspend fun uploadCombo(userId: String, combo: ComboEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "id" to combo.id,
                "restaurantId" to combo.restaurantId,
                "name" to combo.name,
                "price" to combo.price,
                "badge" to combo.badge,
                "description" to combo.description,
                "isActive" to combo.isActive,
                "slotsJson" to converters.fromComboSlotList(combo.slots),
                "createdAt" to combo.createdAt
            )
            userRef(uid).collection("combos").document(combo.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Combo sync deferred", e)
        }
    }

    suspend fun deleteCombo(userId: String, comboId: String) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            userRef(uid).collection("combos").document(comboId).delete().await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Delete combo sync deferred", e)
        }
    }

    suspend fun uploadStockTransaction(userId: String, st: StockTransactionEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "restaurantId" to st.restaurantId,
                "inventoryItemId" to st.inventoryItemId,
                "itemName" to st.itemName,
                "transactionType" to st.transactionType,
                "quantity" to st.quantity,
                "unitRate" to st.unitRate,
                "balanceAfter" to st.balanceAfter,
                "batchId" to st.batchId,
                "billId" to st.billId,
                "referenceId" to st.referenceId,
                "notes" to st.notes,
                "timestamp" to st.timestamp
            )
            userRef(uid).collection("stock_transactions").document(st.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Stock transaction sync deferred", e)
        }
    }

    suspend fun uploadBillBatchDeduction(userId: String, d: BillBatchDeductionEntity) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        try {
            val data = hashMapOf(
                "billId" to d.billId,
                "menuItemId" to d.menuItemId,
                "inventoryItemId" to d.inventoryItemId,
                "batchId" to d.batchId,
                "quantityDeducted" to d.quantityDeducted,
                "rate" to d.rate,
                "cost" to d.cost,
                "isNegativeStock" to d.isNegativeStock,
                "timestamp" to d.timestamp,
                "createdAt" to d.createdAt,
                "status" to d.status,
                "referenceId" to d.referenceId
            )
            userRef(uid).collection("bill_batch_deductions").document(d.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Deduction cloud sync deferred", e)
        }
    }

    suspend fun uploadBillBatchDeductions(userId: String, deductions: List<BillBatchDeductionEntity>) = withContext(Dispatchers.IO) {
        val uid = getRealFirebaseUid(userId)
        if (uid.isBlank()) return@withContext
        for (d in deductions) {
            uploadBillBatchDeduction(uid, d)
        }
    }

    private val activeListeners = mutableListOf<ListenerRegistration>()

    /**
     * Real-time sync listener on key collections:
     * Reflects changes made on other devices automatically without manual sync.
     */
    fun startRealtimeSync(userId: String, db: CafePosDatabase) {
        val realUid = getRealFirebaseUid(userId)
        if (realUid.isBlank()) return

        stopRealtimeSync()

        val root = userRef(realUid)

        // Realtime listener for Inventory
        val invReg = root.collection("inventory").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.inventoryDao().deleteItemById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.inventoryDao().getItemById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                        val item = InventoryItemEntity(
                            id = d.id,
                            restaurantId = d.getAnyString("restaurantId"),
                            name = d.getAnyString("name", "itemName"),
                            unit = d.getAnyString("unit", default = "pcs"),
                            purchasePrice = d.getAnyDouble("purchasePrice", "purchaseRate"),
                            openingStock = d.getAnyDouble("openingStock"),
                            currentStock = d.getAnyDouble("currentStock", "stock"),
                            lowStockThreshold = d.getAnyDouble("lowStockThreshold", default = 5.0),
                            updatedAt = cloudUpdatedAt
                        )
                        db.inventoryDao().insertOrUpdateItem(item)
                    }
                }
            }
        }
        activeListeners.add(invReg)

        // Realtime listener for Tables
        val tableReg = root.collection("tables").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.cafeTableDao().deleteById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.cafeTableDao().getTableById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                        val rawActiveBillId = d.getAnyString("activeBillId", "billId").takeIf { it.isNotBlank() }
                        val rawOccupied = d.getAnyBoolean("isOccupied", "occupied")
                        val bill = if (rawActiveBillId != null) db.billDao().getBillById(rawActiveBillId) else null
                        val isGenuinelyOccupied = if (bill != null) (!bill.isCancelled && !bill.isVoided && !bill.isSettled) else (rawOccupied && rawActiveBillId != null)
                        val cleanActiveBillId = if (isGenuinelyOccupied) rawActiveBillId else null

                        val table = CafeTableEntity(
                            id = d.id,
                            restaurantId = d.getAnyString("restaurantId"),
                            name = d.getAnyString("name", "tableName"),
                            capacity = d.getAnyInt("capacity", "seats", default = 4),
                            isOccupied = isGenuinelyOccupied,
                            activeBillId = cleanActiveBillId,
                            updatedAt = cloudUpdatedAt
                        )
                        db.cafeTableDao().insertOrUpdate(table)
                    }
                }
            }
        }
        activeListeners.add(tableReg)

        // Realtime listener for Expenses
        val expReg = root.collection("expenses").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.expenseDao().deleteById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.expenseDao().getExpenseById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                        val exp = ExpenseEntity(
                            id = d.id,
                            restaurantId = d.getAnyString("restaurantId"),
                            categoryId = d.getAnyString("categoryId"),
                            categoryName = d.getAnyString("categoryName", "category"),
                            amount = d.getAnyDouble("amount"),
                            paymentMethod = d.getAnyString("paymentMethod", default = "CASH"),
                            description = d.getAnyString("description", "notes"),
                            timestamp = d.getAnyLong("timestamp", default = System.currentTimeMillis()),
                            updatedAt = cloudUpdatedAt
                        )
                        db.expenseDao().insertOrUpdate(exp)
                    }
                }
            }
        }
        activeListeners.add(expReg)

        // Realtime listener for Expense Categories
        val expCatReg = root.collection("expense_categories").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.expenseDao().deleteCategoryById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val cat = ExpenseCategoryEntity(
                            id = d.id,
                            name = d.getAnyString("name", "categoryName"),
                            updatedAt = cloudUpdatedAt
                        )
                        db.expenseDao().insertCategory(cat)
                    }
                }
            }
        }
        activeListeners.add(expCatReg)

        // Realtime listener for Cash Register
        val crReg = root.collection("cash_registers").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                    val existing = db.cashRegisterDao().getRegisterById(d.id)
                    if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                    val reg = CashRegisterEntity(
                        id = d.id,
                        restaurantId = d.getAnyString("restaurantId"),
                        dateString = d.getAnyString("dateString", "date"),
                        openingCash = d.getAnyDouble("openingCash"),
                        cashSales = d.getAnyDouble("cashSales"),
                        cashExpenses = d.getAnyDouble("cashExpenses"),
                        cashAdded = d.getAnyDouble("cashAdded"),
                        cashWithdrawn = d.getAnyDouble("cashWithdrawn"),
                        expectedCash = d.getAnyDouble("expectedCash"),
                        closingCashActual = d.getAnyDouble("closingCashActual").takeIf { d.get("closingCashActual") != null },
                        difference = d.getAnyDouble("difference").takeIf { d.get("difference") != null },
                        status = d.getAnyString("status", default = "OPEN"),
                        openedAt = d.getAnyLong("openedAt", default = System.currentTimeMillis()),
                        closedAt = d.getAnyLong("closedAt").takeIf { d.get("closedAt") != null },
                        notes = d.getAnyString("notes"),
                        updatedAt = cloudUpdatedAt
                    )
                    db.cashRegisterDao().insertOrUpdate(reg)
                }
            }
        }
        activeListeners.add(crReg)

        // Realtime listener for Customers
        val custReg = root.collection("customers").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.customerDao().deleteById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val contact = d.getAnyString("contactNumber", "phone", "mobile")
                        val existing = db.customerDao().getCustomerById(d.id)
                            ?: if (contact.isNotBlank()) db.customerDao().getCustomerByContact(contact) else null
                        val cloudReward = d.getAnyInt("rewardPointsBalance", "rewardPoints", "points")
                        val cloudGift = d.getAnyInt("giftPointsBalance", "giftPoints")
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                        val cust = CustomerEntity(
                            id = existing?.id ?: d.id,
                            name = d.getAnyString("name", "customerName", "clientName"),
                            contactNumber = if (contact.isNotBlank()) contact else (existing?.contactNumber ?: ""),
                            isCreditCustomer = d.getAnyBoolean("isCreditCustomer", "isCredit"),
                            loyaltyVisitCount = d.getAnyInt("loyaltyVisitCount", "visits"),
                            loyaltyStartDate = d.getAnyLong("loyaltyStartDate").takeIf { it > 0 } ?: existing?.loyaltyStartDate,
                            loyaltyExpiryDate = d.getAnyLong("loyaltyExpiryDate").takeIf { it > 0 } ?: existing?.loyaltyExpiryDate,
                            currentLoyaltyProgramId = d.getAnyString("currentLoyaltyProgramId").takeIf { it.isNotBlank() } ?: existing?.currentLoyaltyProgramId,
                            loyaltyHistoryJson = d.getAnyString("loyaltyHistoryJson", default = "[]"),
                            rewardPointsBalance = cloudReward,
                            giftPointsBalance = cloudGift,
                            isEnrolledInLoyalty = d.getAnyBoolean("isEnrolledInLoyalty", default = true),
                            birthday = d.getAnyString("birthday", "birthDate", "dob").takeIf { it.isNotBlank() } ?: existing?.birthday,
                            lastVisitTimestamp = d.getAnyLong("lastVisitTimestamp").takeIf { it > 0 } ?: existing?.lastVisitTimestamp,
                            updatedAt = maxOf(cloudUpdatedAt, existing?.updatedAt ?: 0L)
                        )
                        db.customerDao().insertOrUpdate(cust)
                    }
                }
            }
        }
        activeListeners.add(custReg)

        // Realtime listener for Bills
        val billsReg = root.collection("bills").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.billDao().deleteById(d.id)
                    } else {
                        try {
                            val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                            val existingLocal = db.billDao().getBillById(d.id)
                            if (existingLocal != null && existingLocal.updatedAt > cloudUpdatedAt) continue

                            var itemsList = parseBillItemsFromDoc(d)
                            if (itemsList.isEmpty() && existingLocal != null && existingLocal.items.isNotEmpty()) {
                                itemsList = existingLocal.items
                            }

                            val bill = BillEntity(
                                id = d.id,
                                billNumber = d.getAnyString("billNumber", "billNo", "orderNumber", default = d.id),
                                restaurantId = d.getAnyString("restaurantId"),
                                restaurantName = d.getAnyString("restaurantName"),
                                orderType = d.getAnyString("orderType", default = "DINE_IN"),
                                tableId = d.getAnyString("tableId").takeIf { it.isNotBlank() },
                                tableName = d.getAnyString("tableName").takeIf { it.isNotBlank() },
                                customerName = d.getAnyString("customerName", "clientName"),
                                customerPhone = d.getAnyString("customerPhone", "phone"),
                                items = itemsList,
                                subtotal = d.getAnyDouble("subtotal", "subTotal"),
                                discountType = d.getAnyString("discountType", default = "NONE"),
                                discountValue = d.getAnyDouble("discountValue"),
                                discountAmount = d.getAnyDouble("discountAmount", "discount"),
                                totalAmount = d.getAnyDouble("totalAmount", "total", "amount"),
                                paymentMethod = d.getAnyString("paymentMethod", "paymentMode", default = "CASH"),
                                cashAmount = d.getAnyDouble("cashAmount"),
                                upiAmount = d.getAnyDouble("upiAmount"),
                                isSettled = d.getAnyBoolean("isSettled", "settled", "paid", default = true),
                                isStockDeducted = d.getAnyBoolean("isStockDeducted"),
                                totalFoodCost = d.getAnyDouble("totalFoodCost"),
                                appliedRewardType = d.getAnyString("appliedRewardType", default = "NONE"),
                                appliedOfferId = d.getAnyString("appliedOfferId").takeIf { it.isNotBlank() },
                                appliedOfferName = d.getAnyString("appliedOfferName"),
                                rewardPointsEarned = d.getAnyInt("rewardPointsEarned"),
                                pointsRedeemed = d.getAnyInt("pointsRedeemed"),
                                rewardPointsRedeemed = d.getAnyInt("rewardPointsRedeemed"),
                                giftPointsRedeemed = d.getAnyInt("giftPointsRedeemed"),
                                isVoided = d.getAnyBoolean("isVoided"),
                                status = d.getAnyString("status", default = if (d.getAnyBoolean("isCancelled", "cancelled")) "CANCELLED" else "SETTLED"),
                                isCancelled = d.getAnyBoolean("isCancelled", "cancelled"),
                                cancellationReason = d.getAnyString("cancellationReason", "cancelReason"),
                                cancelledAt = d.getLong("cancelledAt"),
                                billTimestamp = d.getAnyLong("billTimestamp", "timestamp", "createdAt", default = System.currentTimeMillis()),
                                createdAt = d.getAnyLong("createdAt", default = System.currentTimeMillis()),
                                updatedAt = cloudUpdatedAt
                            )
                            db.billDao().insertOrUpdate(bill)
                        } catch (e: Exception) {
                            Log.w("FirestoreSyncManager", "Error parsing realtime bill", e)
                        }
                    }
                }
            }
        }
        activeListeners.add(billsReg)

        // Realtime listener for Categories
        val catReg = root.collection("categories").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.categoryDao().deleteById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.categoryDao().getCategoryById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                        val cat = CategoryEntity(
                            id = d.id,
                            name = d.getAnyString("name", "categoryName"),
                            displayOrder = d.getAnyInt("displayOrder", "order"),
                            isDiscountEligible = d.getAnyBoolean("isDiscountEligible", default = true),
                            updatedAt = cloudUpdatedAt
                        )
                        db.categoryDao().insertOrUpdate(cat)
                    }
                }
            }
        }
        activeListeners.add(catReg)

        // Realtime listener for Menu Items
        val menuReg = root.collection("menu_items").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.menuItemDao().deleteById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.menuItemDao().getMenuItemById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                        val item = MenuItemEntity(
                            id = d.id,
                            categoryId = d.getAnyString("categoryId"),
                            categoryName = d.getAnyString("categoryName", "category"),
                            name = d.getAnyString("name", "dishName"),
                            price = d.getAnyDouble("price", "rate"),
                            isAvailable = d.getAnyBoolean("isAvailable", default = true),
                            isDiscountEligible = d.getAnyBoolean("isDiscountEligible", default = true),
                            imageUri = d.getAnyString("imageUri", "imageUrl").takeIf { it.isNotBlank() },
                            variantsJson = d.getAnyString("variantsJson", default = "[]"),
                            addonsJson = d.getAnyString("addonsJson", default = "[]"),
                            updatedAt = cloudUpdatedAt
                        )
                        db.menuItemDao().insertOrUpdate(item)
                    }
                }
            }
        }
        activeListeners.add(menuReg)

        // Realtime listener for Restaurants
        val restReg = root.collection("restaurants").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.restaurantDao().deleteById(d.id)
                    } else {
                        val name = d.getAnyString("name", "restaurantName")
                        if (d.id.contains("bakers_boy", ignoreCase = true) || name.contains("Baker's Boy", ignoreCase = true)) {
                            continue
                        }
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.restaurantDao().getRestaurantById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue

                        val base64 = d.getAnyString("customLogoBase64")
                        val restoredUri = if (base64.isNotBlank()) {
                            restoreBase64LogoToFile(context, d.id, base64)
                        } else null

                        val rest = RestaurantEntity(
                            id = d.id,
                            name = name,
                            address = d.getAnyString("address"),
                            phone = d.getAnyString("phone"),
                            footerNote = d.getAnyString("footerNote", default = "Thank you for visiting! Please visit again."),
                            logoPreset = d.getAnyString("logoPreset", default = "cafe_coffee"),
                            customLogoUri = restoredUri ?: d.getAnyString("customLogoUri").takeIf { it.isNotBlank() },
                            customLogoBase64 = base64,
                            isActive = d.getAnyBoolean("isActive", default = true),
                            updatedAt = cloudUpdatedAt,
                            fssaiNumber = d.getAnyString("fssaiNumber"),
                            isFssaiEnabled = d.getAnyBoolean("isFssaiEnabled"),
                            showFssaiOnBill = d.getAnyBoolean("showFssaiOnBill"),
                            gstNumber = d.getAnyString("gstNumber"),
                            gstRate = d.getAnyDouble("gstRate", default = 5.0),
                            isGstEnabled = d.getAnyBoolean("isGstEnabled"),
                            showGstOnBill = d.getAnyBoolean("showGstOnBill"),
                            billFormat = d.getAnyString("billFormat", default = "ELEGANT_DINE_IN"),
                            billPrefix = d.getAnyString("billPrefix"),
                            billPrefixCountersJson = d.getAnyString("billPrefixCountersJson", default = "{}")
                        )
                        db.restaurantDao().insertOrUpdate(rest)
                    }
                }
            }
        }
        activeListeners.add(restReg)

        // Realtime listener for Points Batches
        val pbReg = root.collection("points_batches").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.pointsBatchDao().deleteById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.pointsBatchDao().getBatchById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue
                        val batch = PointsBatchEntity(
                            id = d.id,
                            customerId = d.getAnyString("customerId"),
                            customerPhone = d.getAnyString("customerPhone"),
                            customerName = d.getAnyString("customerName"),
                            batchType = d.getAnyString("batchType", default = "REWARD"),
                            initialPoints = d.getAnyInt("initialPoints"),
                            remainingPoints = d.getAnyInt("remainingPoints"),
                            earnDate = d.getAnyLong("earnDate"),
                            expiryDate = d.getAnyLong("expiryDate"),
                            isExpired = d.getAnyBoolean("isExpired"),
                            reminderSent = d.getAnyBoolean("reminderSent"),
                            reminderSentDate = d.getAnyLong("reminderSentDate").takeIf { it > 0 },
                            billId = d.getAnyString("billId").takeIf { it.isNotBlank() },
                            billNumber = d.getAnyString("billNumber").takeIf { it.isNotBlank() },
                            notes = d.getAnyString("notes"),
                            createdAt = d.getAnyLong("createdAt"),
                            updatedAt = cloudUpdatedAt
                        )
                        db.pointsBatchDao().insertOrUpdate(batch)
                    }
                }
            }
        }
        activeListeners.add(pbReg)

        // Realtime listener for Points Ledger
        val plReg = root.collection("points_ledger").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.pointsLedgerDao().deleteById(d.id)
                    } else {
                        val ledger = PointsLedgerEntity(
                            id = d.id,
                            customerId = d.getAnyString("customerId"),
                            customerPhone = d.getAnyString("customerPhone"),
                            customerName = d.getAnyString("customerName"),
                            transactionType = d.getAnyString("transactionType", default = "EARNED"),
                            pointsAmount = d.getAnyInt("pointsAmount"),
                            balanceType = d.getAnyString("balanceType", default = "REWARD"),
                            billId = d.getAnyString("billId").takeIf { it.isNotBlank() },
                            billNumber = d.getAnyString("billNumber").takeIf { it.isNotBlank() },
                            expiryDate = d.getAnyLong("expiryDate").takeIf { it > 0 },
                            rewardPointsBalanceAfter = d.getAnyInt("rewardPointsBalanceAfter"),
                            giftPointsBalanceAfter = d.getAnyInt("giftPointsBalanceAfter"),
                            notes = d.getAnyString("notes"),
                            timestamp = d.getAnyLong("timestamp", default = System.currentTimeMillis()),
                            createdAt = d.getAnyLong("createdAt", default = System.currentTimeMillis())
                        )
                        db.pointsLedgerDao().insert(ledger)
                    }
                }
            }
        }
        activeListeners.add(plReg)

        // Realtime listener for Offers & Loyalty Programs
        val offReg = root.collection("offers").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type == DocumentChange.Type.REMOVED) {
                        db.offerDao().deleteById(d.id)
                    } else {
                        val cloudUpdatedAt = d.getAnyLong("updatedAt", "timestamp")
                        val existing = db.offerDao().getOfferById(d.id)
                        if (existing != null && existing.updatedAt > cloudUpdatedAt) continue
                        val offer = OfferEntity(
                            id = d.id,
                            restaurantId = d.getAnyString("restaurantId", default = "REST-001"),
                            name = d.getAnyString("name", "offerName"),
                            offerType = d.getAnyString("offerType", default = "VISIT_BASED"),
                            rewardType = d.getAnyString("rewardType", default = "FREE_ITEM"),
                            rewardValue = d.getAnyDouble("rewardValue"),
                            minBillAmount = d.getAnyDouble("minBillAmount", "minimumBillAmount"),
                            applicableOrderTypes = d.getAnyString("applicableOrderTypes", default = "DINE_IN,TAKEAWAY"),
                            isActive = d.getAnyBoolean("isActive", default = true),
                            totalVisitsInProgram = d.getAnyInt("totalVisitsInProgram", default = 6),
                            visitNumber = d.getAnyInt("visitNumber", "requiredVisitNumber", default = 1),
                            validityDays = d.getAnyInt("validityDays", default = 45),
                            freeItemQuantity = d.getAnyInt("freeItemQuantity", default = 1),
                            eligibleMenuItemIds = d.getAnyString("eligibleMenuItemIds", default = "[]"),
                            allowSameItemMultipleTimes = d.getAnyBoolean("allowSameItemMultipleTimes", default = true),
                            freeItemName = d.getAnyString("freeItemName"),
                            maxDiscountCap = d.getAnyDouble("maxDiscountCap").takeIf { it > 0 },
                            cooldownDays = d.getAnyInt("cooldownDays", default = 0),
                            createdAt = d.getAnyLong("createdAt", default = System.currentTimeMillis()),
                            updatedAt = cloudUpdatedAt
                        )
                        db.offerDao().insertOrUpdate(offer)
                    }
                }
            }
        }
        activeListeners.add(offReg)

        // Realtime listener for Claimed Rewards
        val claimedReg = root.collection("claimed_rewards").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            CoroutineScope(Dispatchers.IO).launch {
                for (doc in snapshot.documentChanges) {
                    val d = doc.document
                    if (d.metadata.hasPendingWrites()) continue
                    if (doc.type != DocumentChange.Type.REMOVED) {
                        try {
                            val phone = d.getAnyString("customerPhone", "phone").trim()
                            val visitNum = d.getAnyInt("visitNumber", default = 0)
                            val offerName = d.getAnyString("offerName", "name")
                            val billNum = d.getAnyString("billNumber")
                            val claimedAt = d.getAnyLong("claimedAt", "timestamp", default = System.currentTimeMillis())

                            if (phone.isNotBlank() && visitNum > 0) {
                                val cust = db.customerDao().getCustomerByContact(phone)
                                if (cust != null) {
                                    val historyList = converters.toLoyaltyHistoryList(cust.loyaltyHistoryJson).toMutableList()
                                    val alreadyHas = historyList.any { it.visitNumber == visitNum && !it.rewardGiven.isNullOrBlank() }
                                    if (!alreadyHas) {
                                        historyList.add(
                                            LoyaltyHistoryItem(
                                                eventType = "VISIT_COMPLETED",
                                                visitNumber = visitNum,
                                                billId = "",
                                                billNumber = billNum,
                                                billAmount = 0.0,
                                                offerName = offerName.takeIf { it.isNotBlank() },
                                                rewardGiven = offerName.ifBlank { "Claimed Reward" },
                                                timestamp = claimedAt
                                            )
                                        )
                                        val updatedCust = cust.copy(
                                            loyaltyHistoryJson = converters.fromLoyaltyHistoryList(historyList),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        db.customerDao().insertOrUpdate(updatedCust)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("FirestoreSyncManager", "Error syncing realtime claimed reward", e)
                        }
                    }
                }
            }
        }
        activeListeners.add(claimedReg)
    }

    fun stopRealtimeSync() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    suspend fun uploadAddon(userId: String, addon: AddonDefinitionEntity) = withContext(Dispatchers.IO) {
        val realUid = getRealFirebaseUid(userId)
        if (realUid.isBlank()) return@withContext
        val root = userRef(realUid)
        root.collection("addons").document(addon.id).set(
            hashMapOf(
                "id" to addon.id,
                "name" to addon.name,
                "price" to addon.price,
                "selectionType" to addon.selectionType,
                "groupName" to addon.groupName,
                "isRequired" to addon.isRequired,
                "minCount" to addon.minCount,
                "maxCount" to addon.maxCount,
                "isActive" to addon.isActive,
                "inventoryItemId" to (addon.inventoryItemId ?: ""),
                "inventoryItemName" to (addon.inventoryItemName ?: ""),
                "inventoryQty" to addon.inventoryQty,
                "usageUnit" to addon.usageUnit,
                "updatedAt" to addon.updatedAt
            ), SetOptions.merge()
        ).await()
    }

    suspend fun deleteAddon(userId: String, addonId: String) = withContext(Dispatchers.IO) {
        val realUid = getRealFirebaseUid(userId)
        if (realUid.isBlank()) return@withContext
        val root = userRef(realUid)
        root.collection("addons").document(addonId).delete().await()
    }

    private fun restoreBase64LogoToFile(context: Context, restaurantId: String, base64: String): String? {
        if (base64.isBlank()) return null
        return try {
            val decodedBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                val file = java.io.File(context.filesDir, "restored_custom_logo_${restaurantId}.png")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                android.net.Uri.fromFile(file).toString()
            } else null
        } catch (e: Exception) {
            android.util.Log.e("FirestoreSync", "Error restoring base64 logo", e)
            null
        }
    }
}
