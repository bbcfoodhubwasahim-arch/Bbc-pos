package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.auth.AuthManager
import com.example.data.cloud.FirestoreSyncManager
import com.example.data.local.database.CafePosDatabase
import com.example.data.local.entity.*
import androidx.room.withTransaction
import com.example.util.DateUtils
import com.example.util.FoodCostCalculator
import com.example.util.PdfInvoiceGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class PosRepository(
    private val context: Context,
    private val db: CafePosDatabase,
    private val authManager: AuthManager,
    private val syncManager: FirestoreSyncManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Current active user's UID (always real Firebase UID directly from FirebaseAuth.currentUser)
    val currentUserId: StateFlow<String> = authManager.userState
        .map { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: it.uid.takeIf { id -> !id.startsWith("pos_uid_") } ?: "" }
        .stateIn(scope, SharingStarted.Eagerly, com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")

    // DAOs
    val restaurantDao = db.restaurantDao()
    val categoryDao = db.categoryDao()
    val menuItemDao = db.menuItemDao()
    val cafeTableDao = db.cafeTableDao()
    val customerDao = db.customerDao()
    val billDao = db.billDao()
    val inventoryDao = db.inventoryDao()
    val expenseDao = db.expenseDao()
    val cashRegisterDao = db.cashRegisterDao()
    val comboDao = db.comboDao()
    val recipeDao = db.recipeDao()
    val inventoryBatchDao = db.inventoryBatchDao()
    val stockTransactionDao = db.stockTransactionDao()
    val stockCountDao = db.stockCountDao()
    val customerPaymentDao = db.customerPaymentDao()
    val offerDao = db.offerDao()
    val pointsBatchDao = db.pointsBatchDao()
    val pointsLedgerDao = db.pointsLedgerDao()
    val addonDao = db.addonDao()

    val allBatches: Flow<List<InventoryBatchEntity>> = inventoryBatchDao.getAllBatches()
    val allStockCounts: Flow<List<StockCountEntity>> = stockCountDao.getAllStockCounts()
    val allCustomerPayments: Flow<List<CustomerPaymentEntity>> = customerPaymentDao.getAllPayments()
    val allOffers: Flow<List<OfferEntity>> = offerDao.getAllOffers()
    val allActivePointsBatches: Flow<List<PointsBatchEntity>> = pointsBatchDao.getAllActiveBatches()
    val allPointsLedger: Flow<List<PointsLedgerEntity>> = pointsLedgerDao.getAllEntries()
    val allStockTransactions: Flow<List<StockTransactionEntity>> = stockTransactionDao.getAllTransactions()
    val allAddonDefinitions: Flow<List<AddonDefinitionEntity>> = addonDao.getAllAddons()
    val allActiveAddonDefinitions: Flow<List<AddonDefinitionEntity>> = addonDao.getActiveAddons()

    // Live Database Record Counts (MANDATORY: Always calculated live from current Room DB)
    val liveBillsCount: Flow<Int> = billDao.getCount()
    val liveCustomersCount: Flow<Int> = customerDao.getCount()
    val liveInventoryCount: Flow<Int> = inventoryDao.getCount()
    val liveExpensesCount: Flow<Int> = expenseDao.getCount()
    val liveMenuItemsCount: Flow<Int> = menuItemDao.getCount()
    val liveCategoriesCount: Flow<Int> = categoryDao.getCount()
    val liveTablesCount: Flow<Int> = cafeTableDao.getCount()
    val liveRegistersCount: Flow<Int> = cashRegisterDao.getCount()
    val liveCombosCount: Flow<Int> = comboDao.getCount()
    val liveLowStockCount: Flow<Int> = inventoryDao.getLowStockCount()

    // Default persistent fallback restaurant for BBC FOOD HUB
    val defaultBbcRestaurant = RestaurantEntity(
        id = "bbc_food_hub_main",
        name = "BBC FOOD HUB",
        tagline = "Taste the Good Life • Fresh & Delicious",
        address = "Point of Sale • Retail Billing",
        phone = "9130694963",
        altPhone = "",
        footerNote = "Thank you for visiting! Please visit again.",
        customTermsNote = "• Prices are inclusive of all taxes\n• Items once prepared cannot be cancelled",
        logoPreset = "cafe_coffee",
        customLogoUri = null,
        isActive = true,
        fssaiNumber = "",
        isFssaiEnabled = false,
        showFssaiOnBill = false,
        gstNumber = "",
        gstRate = 5.0,
        isGstEnabled = false,
        showGstOnBill = false,
        upiId = "bbcfoodhubwasahim@okaxis",
        upiQrEnabled = true,
        wifiDetails = "",
        socialHandle = "@bbcfoodhub",
        showLogoOnBill = true,
        showTokenOnBill = true,
        showSavingsOnBill = true,
        showPointsOnBill = true,
        showPreviousDueOnBill = true,
        paperWidth = "80MM",
        billFormat = "UNIVERSAL_CAFE_PRO",
        billPrefix = "BBC",
        billPrefixCountersJson = "{}",
        updatedAt = System.currentTimeMillis()
    )

    // Fast SharedPreferences cache for zero-lag instant app launch
    private val restaurantCachePrefs = context.getSharedPreferences("restaurant_cache_prefs", Context.MODE_PRIVATE)

    fun getCachedRestaurant(): RestaurantEntity? {
        val id = restaurantCachePrefs.getString("cached_id", null) ?: return null
        val name = restaurantCachePrefs.getString("cached_name", "BBC FOOD HUB") ?: "BBC FOOD HUB"
        val tagline = restaurantCachePrefs.getString("cached_tagline", "Taste the Good Life • Fresh & Delicious") ?: "Taste the Good Life • Fresh & Delicious"
        val address = restaurantCachePrefs.getString("cached_address", "") ?: ""
        val phone = restaurantCachePrefs.getString("cached_phone", "") ?: ""
        val altPhone = restaurantCachePrefs.getString("cached_altPhone", "") ?: ""
        val footerNote = restaurantCachePrefs.getString("cached_footerNote", "Thank you for visiting! Please visit again.") ?: "Thank you for visiting! Please visit again."
        val customTermsNote = restaurantCachePrefs.getString("cached_customTermsNote", "") ?: ""
        val logoPreset = restaurantCachePrefs.getString("cached_logoPreset", "cafe_coffee") ?: "cafe_coffee"
        val customLogoUri = restaurantCachePrefs.getString("cached_customLogoUri", null)
        val fssaiNumber = restaurantCachePrefs.getString("cached_fssaiNumber", "") ?: ""
        val isFssaiEnabled = restaurantCachePrefs.getBoolean("cached_isFssaiEnabled", false)
        val showFssaiOnBill = restaurantCachePrefs.getBoolean("cached_showFssaiOnBill", false)
        val gstNumber = restaurantCachePrefs.getString("cached_gstNumber", "") ?: ""
        val gstRate = restaurantCachePrefs.getFloat("cached_gstRate", 5.0f).toDouble()
        val isGstEnabled = restaurantCachePrefs.getBoolean("cached_isGstEnabled", false)
        val showGstOnBill = restaurantCachePrefs.getBoolean("cached_showGstOnBill", false)
        val upiId = restaurantCachePrefs.getString("cached_upiId", "bbcfoodhubwasahim@okaxis") ?: "bbcfoodhubwasahim@okaxis"
        val upiQrEnabled = restaurantCachePrefs.getBoolean("cached_upiQrEnabled", true)
        val wifiDetails = restaurantCachePrefs.getString("cached_wifiDetails", "") ?: ""
        val socialHandle = restaurantCachePrefs.getString("cached_socialHandle", "") ?: ""
        val showLogoOnBill = restaurantCachePrefs.getBoolean("cached_showLogoOnBill", true)
        val showTokenOnBill = restaurantCachePrefs.getBoolean("cached_showTokenOnBill", true)
        val showSavingsOnBill = restaurantCachePrefs.getBoolean("cached_showSavingsOnBill", true)
        val showPointsOnBill = restaurantCachePrefs.getBoolean("cached_showPointsOnBill", true)
        val showPreviousDueOnBill = restaurantCachePrefs.getBoolean("cached_showPreviousDueOnBill", true)
        val paperWidth = restaurantCachePrefs.getString("cached_paperWidth", "80MM") ?: "80MM"
        val billFormat = restaurantCachePrefs.getString("cached_billFormat", "UNIVERSAL_CAFE_PRO") ?: "UNIVERSAL_CAFE_PRO"
        val billPrefix = restaurantCachePrefs.getString("cached_billPrefix", "BBC") ?: "BBC"
        val billPrefixCountersJson = restaurantCachePrefs.getString("cached_billPrefixCountersJson", "{}") ?: "{}"

        return RestaurantEntity(
            id = id,
            name = name,
            tagline = tagline,
            address = address,
            phone = phone,
            altPhone = altPhone,
            footerNote = footerNote,
            customTermsNote = customTermsNote,
            logoPreset = logoPreset,
            customLogoUri = customLogoUri,
            isActive = true,
            fssaiNumber = fssaiNumber,
            isFssaiEnabled = isFssaiEnabled,
            showFssaiOnBill = showFssaiOnBill,
            gstNumber = gstNumber,
            gstRate = gstRate,
            isGstEnabled = isGstEnabled,
            showGstOnBill = showGstOnBill,
            upiId = upiId,
            upiQrEnabled = upiQrEnabled,
            wifiDetails = wifiDetails,
            socialHandle = socialHandle,
            showLogoOnBill = showLogoOnBill,
            showTokenOnBill = showTokenOnBill,
            showSavingsOnBill = showSavingsOnBill,
            showPointsOnBill = showPointsOnBill,
            showPreviousDueOnBill = showPreviousDueOnBill,
            paperWidth = paperWidth,
            billFormat = billFormat,
            billPrefix = billPrefix,
            billPrefixCountersJson = billPrefixCountersJson
        )
    }

    fun cacheRestaurant(restaurant: RestaurantEntity) {
        restaurantCachePrefs.edit()
            .putString("cached_id", restaurant.id)
            .putString("cached_name", restaurant.name)
            .putString("cached_tagline", restaurant.tagline)
            .putString("cached_address", restaurant.address)
            .putString("cached_phone", restaurant.phone)
            .putString("cached_altPhone", restaurant.altPhone)
            .putString("cached_footerNote", restaurant.footerNote)
            .putString("cached_customTermsNote", restaurant.customTermsNote)
            .putString("cached_logoPreset", restaurant.logoPreset)
            .putString("cached_customLogoUri", restaurant.customLogoUri)
            .putString("cached_fssaiNumber", restaurant.fssaiNumber)
            .putBoolean("cached_isFssaiEnabled", restaurant.isFssaiEnabled)
            .putBoolean("cached_showFssaiOnBill", restaurant.showFssaiOnBill)
            .putString("cached_gstNumber", restaurant.gstNumber)
            .putFloat("cached_gstRate", restaurant.gstRate.toFloat())
            .putBoolean("cached_isGstEnabled", restaurant.isGstEnabled)
            .putBoolean("cached_showGstOnBill", restaurant.showGstOnBill)
            .putString("cached_upiId", restaurant.upiId)
            .putBoolean("cached_upiQrEnabled", restaurant.upiQrEnabled)
            .putString("cached_wifiDetails", restaurant.wifiDetails)
            .putString("cached_socialHandle", restaurant.socialHandle)
            .putBoolean("cached_showLogoOnBill", restaurant.showLogoOnBill)
            .putBoolean("cached_showTokenOnBill", restaurant.showTokenOnBill)
            .putBoolean("cached_showSavingsOnBill", restaurant.showSavingsOnBill)
            .putBoolean("cached_showPointsOnBill", restaurant.showPointsOnBill)
            .putBoolean("cached_showPreviousDueOnBill", restaurant.showPreviousDueOnBill)
            .putString("cached_paperWidth", restaurant.paperWidth)
            .putString("cached_billFormat", restaurant.billFormat)
            .putString("cached_billPrefix", restaurant.billPrefix)
            .putString("cached_billPrefixCountersJson", restaurant.billPrefixCountersJson)
            .putBoolean("has_configured_restaurant", true)
            .apply()
    }

    fun isRestaurantConfigured(): Boolean {
        return restaurantCachePrefs.getBoolean("has_configured_restaurant", true)
    }

    // Active Restaurant
    val activeRestaurant: Flow<RestaurantEntity?> = restaurantDao.getActiveRestaurant()
    val allRestaurants: Flow<List<RestaurantEntity>> = restaurantDao.getAllRestaurants()

    // Configurable Points Engine Rules (Persistent via SharedPreferences)
    private val pointsRulesPrefs = context.getSharedPreferences("points_engine_rules_prefs", Context.MODE_PRIVATE)

    private val _pointsEngineRules = MutableStateFlow(loadPointsEngineRules())
    val pointsEngineRules: StateFlow<PointsEngineRules> = _pointsEngineRules.asStateFlow()

    private fun loadPointsEngineRules(): PointsEngineRules {
        return PointsEngineRules(
            earnRatePercent = pointsRulesPrefs.getFloat("earnRatePercent", 1.0f).toDouble(),
            pointValueRupees = pointsRulesPrefs.getFloat("pointValueRupees", 1.0f).toDouble(),
            minRedemptionPoints = pointsRulesPrefs.getInt("minRedemptionPoints", 50),
            maxDiscountCapPercent = pointsRulesPrefs.getFloat("maxDiscountCapPercent", 50.0f).toDouble(),
            rewardPointsExpiryDays = pointsRulesPrefs.getInt("rewardPointsExpiryDays", 60),
            giftPointsExpiryDays = pointsRulesPrefs.getInt("giftPointsExpiryDays", 30),
            welcomeBonusEnabled = pointsRulesPrefs.getBoolean("welcomeBonusEnabled", false),
            welcomeBonusPoints = pointsRulesPrefs.getInt("welcomeBonusPoints", 20),
            minBillAmountToEarn = pointsRulesPrefs.getFloat("minBillAmountToEarn", 0.0f).toDouble(),
            autoEnrollInVisitPass = pointsRulesPrefs.getBoolean("autoEnrollInVisitPass", false),
            welcomeBonusExpiryDays = pointsRulesPrefs.getInt("welcomeBonusExpiryDays", 30)
        )
    }

    suspend fun updatePointsEngineRules(rules: PointsEngineRules) = withContext(Dispatchers.IO) {
        pointsRulesPrefs.edit()
            .putFloat("earnRatePercent", rules.earnRatePercent.toFloat())
            .putFloat("pointValueRupees", rules.pointValueRupees.toFloat())
            .putInt("minRedemptionPoints", rules.minRedemptionPoints)
            .putFloat("maxDiscountCapPercent", rules.maxDiscountCapPercent.toFloat())
            .putInt("rewardPointsExpiryDays", rules.rewardPointsExpiryDays)
            .putInt("giftPointsExpiryDays", rules.giftPointsExpiryDays)
            .putBoolean("welcomeBonusEnabled", rules.welcomeBonusEnabled)
            .putInt("welcomeBonusPoints", rules.welcomeBonusPoints)
            .putFloat("minBillAmountToEarn", rules.minBillAmountToEarn.toFloat())
            .putBoolean("autoEnrollInVisitPass", rules.autoEnrollInVisitPass)
            .putInt("welcomeBonusExpiryDays", rules.welcomeBonusExpiryDays)
            .apply()
        _pointsEngineRules.value = rules
        scope.launch {
            try {
                syncManager.uploadPointsEngineRules(currentUserId.value, rules)
            } catch (e: Exception) {
                Log.w("PosRepository", "Points rules sync deferred", e)
            }
        }
    }

    suspend fun getActiveRestaurantOrFirst(): RestaurantEntity = withContext(Dispatchers.IO) {
        val fromDb = restaurantDao.getActiveRestaurantDirect() ?: restaurantDao.getAllRestaurantsDirect().firstOrNull()
        if (fromDb != null) {
            cacheRestaurant(fromDb)
            return@withContext fromDb
        }
        val cached = getCachedRestaurant()
        if (cached != null) {
            restaurantDao.insertOrUpdate(cached)
            return@withContext cached
        }
        // Fallback default BBC Food Hub so the app never shows empty setup form
        val defaultBbc = defaultBbcRestaurant
        restaurantDao.insertOrUpdate(defaultBbc)
        cacheRestaurant(defaultBbc)
        defaultBbc
    }

    init {
        scope.launch {
            // Keep SharedPreferences cache fresh whenever DB emits active restaurant
            restaurantDao.getActiveRestaurant().collect { rest ->
                if (rest != null) {
                    cacheRestaurant(rest)
                }
            }
        }
        scope.launch {
            if (authManager.userState.value.isLoggedIn) {
                cleanupLegacyBakersBoyShops()
            }
            // Auto-migrate any existing restaurants to Universal Cafe Pro standard
            try {
                val allRests = restaurantDao.getAllRestaurantsDirect()
                for (rest in allRests) {
                    if (rest.billFormat != "UNIVERSAL_CAFE_PRO") {
                        val updated = rest.copy(billFormat = "UNIVERSAL_CAFE_PRO", updatedAt = System.currentTimeMillis())
                        restaurantDao.insertOrUpdate(updated)
                        if (rest.isActive) {
                            cacheRestaurant(updated)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PosRepository", "Bill format migration deferred", e)
            }
            migrateLegacyStockIfNeeded()
            reconcileTableOccupancy()
        }
    }

    /**
     * Reconciles table occupancy with active bills so any orphaned occupied flags
     * from previously cancelled or deleted bills are cleanly reset to vacant.
     */
    suspend fun reconcileTableOccupancy() = withContext(Dispatchers.IO) {
        try {
            val allTables = cafeTableDao.getAllTablesDirect()
            for (table in allTables) {
                val activeBill = billDao.getActiveBillForTable(table.id)
                if (activeBill == null && (table.isOccupied || table.activeBillId != null)) {
                    cafeTableDao.updateTableOccupancy(table.id, isOccupied = false, activeBillId = null)
                    val updated = cafeTableDao.getTableById(table.id)
                    if (updated != null) {
                        try {
                            syncManager.uploadTable(currentUserId.value, updated)
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Table sync deferred on reconcile", e)
                        }
                    }
                } else if (activeBill != null && (!table.isOccupied || table.activeBillId != activeBill.id)) {
                    cafeTableDao.updateTableOccupancy(table.id, isOccupied = true, activeBillId = activeBill.id)
                    val updated = cafeTableDao.getTableById(table.id)
                    if (updated != null) {
                        try {
                            syncManager.uploadTable(currentUserId.value, updated)
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Table sync deferred on reconcile", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PosRepository", "reconcileTableOccupancy deferred", e)
        }
    }

    /**
     * Purges legacy dummy Baker's Boy cafes so only user-added cafes exist.
     */
    suspend fun cleanupLegacyBakersBoyShops() = withContext(Dispatchers.IO) {
        try {
            val all = restaurantDao.getAllRestaurantsDirect()
            val legacy = all.filter {
                it.id.contains("bakers_boy", ignoreCase = true) || it.name.contains("Baker's Boy", ignoreCase = true)
            }
            if (legacy.isNotEmpty()) {
                for (r in legacy) {
                    restaurantDao.deleteById(r.id)
                    syncManager.deleteRestaurant(currentUserId.value, r.id)
                }
                val remaining = restaurantDao.getAllRestaurantsDirect()
                if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                    restaurantDao.setActiveRestaurant(remaining.first().id)
                }
            }
        } catch (e: Exception) {
            Log.w("PosRepository", "Legacy cleanup deferred", e)
        }
    }

    /**
     * Delete a restaurant / branch from local DB and cloud sync.
     */
    suspend fun deleteRestaurant(restaurantId: String) = withContext(Dispatchers.IO) {
        restaurantDao.deleteById(restaurantId)
        syncManager.deleteRestaurant(currentUserId.value, restaurantId)
        val remaining = restaurantDao.getAllRestaurantsDirect()
        if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
            restaurantDao.setActiveRestaurant(remaining.first().id)
        }
    }

    /**
     * Completely wipes/clears the entire local Room database across all tables.
     * Guaranteed data isolation when switching accounts or signing out.
     */
    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        try {
            context.cacheDir.listFiles { _, name -> name.endsWith(".pdf") }?.forEach { it.delete() }
        } catch (e: Exception) {
            // ignore cache clear errors
        }
    }

    /**
     * Legacy seed catalog methods - all sections start empty by default.
     * Only user-created items, categories, inventory, recipes, tables, and expenses exist.
     */
    suspend fun seedCatalogForRestaurant(restId: String) = withContext(Dispatchers.IO) {
        // No-op: Do NOT seed any hardcoded or demo data.
    }

    suspend fun seedInitialCatalogIfEmpty() = withContext(Dispatchers.IO) {
        // No-op: Do NOT seed any hardcoded or demo data.
    }

    /**
     * Generates a sequential bill number per restaurant and prefix.
     * If restaurant has a prefix configured (e.g. "INV", "CAFE", "BILL"),
     * it generates sequential numbers like "INV-001", "INV-002", etc.
     * If no prefix is configured, it defaults to "#1", "#2", etc.
     * Counters are maintained per prefix and also cross-checked with existing bills in DB
     * so it never generates duplicate numbers.
     */
    suspend fun generateNextBillNumber(restaurantId: String): String {
        val restaurant = restaurantDao.getRestaurantById(restaurantId)
            ?: restaurantDao.getActiveRestaurantDirect()
        val rawPrefix = restaurant?.billPrefix?.trim() ?: ""
        val prefixKey = if (rawPrefix.isNotBlank()) rawPrefix.uppercase() else "DEFAULT"

        // 1. Read existing counter map from restaurant entity JSON
        val counterMap = try {
            val json = restaurant?.billPrefixCountersJson ?: "{}"
            val jsonObj = org.json.JSONObject(json)
            val map = mutableMapOf<String, Int>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = jsonObj.optInt(k, 0)
            }
            map
        } catch (e: Exception) {
            mutableMapOf<String, Int>()
        }

        var currentCounter = counterMap[prefixKey] ?: 0

        // 2. Query all bills for this restaurant to ensure counter is at least highest existing sequence
        val allBills = billDao.getAllBillsDirect().filter { it.restaurantId == restaurantId }
        val existingMax = allBills.mapNotNull { b ->
            val num = b.billNumber.trim()
            if (rawPrefix.isNotBlank()) {
                // Look for patterns like "INV-001", "INV-1", "INV001", etc.
                val upperNum = num.uppercase()
                val upperPrefix = rawPrefix.uppercase()
                when {
                    upperNum.startsWith("$upperPrefix-") -> upperNum.removePrefix("$upperPrefix-").toIntOrNull()
                    upperNum.startsWith("$upperPrefix/") -> upperNum.removePrefix("$upperPrefix/").toIntOrNull()
                    upperNum.startsWith(upperPrefix) -> upperNum.removePrefix(upperPrefix).toIntOrNull()
                    else -> null
                }
            } else {
                // Default pattern like "#1", "#001", "1", "001"
                val cleaned = if (num.startsWith("#")) num.substring(1) else num
                cleaned.toIntOrNull()
            }
        }.maxOrNull() ?: 0

        if (existingMax > currentCounter) {
            currentCounter = existingMax
        }

        val nextCounter = currentCounter + 1
        counterMap[prefixKey] = nextCounter

        // 3. Save updated counter back to restaurant entity and sync
        if (restaurant != null) {
            val updatedJson = org.json.JSONObject(counterMap as Map<*, *>).toString()
            val updatedRest = restaurant.copy(
                billPrefixCountersJson = updatedJson,
                updatedAt = System.currentTimeMillis()
            )
            restaurantDao.insertOrUpdate(updatedRest)
            scope.launch {
                try {
                    syncManager.uploadRestaurant(currentUserId.value, updatedRest)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Prefix counter sync deferred", e)
                }
            }
        }

        // 4. Format the formatted bill number: e.g. "INV-001" or "#1"
        return if (rawPrefix.isNotBlank()) {
            val formattedSeq = String.format(Locale.US, "%03d", nextCounter)
            val cleanPrefix = rawPrefix.trim().trimEnd('-', '/', '_')
            "${cleanPrefix.uppercase()}-$formattedSeq"
        } else {
            val formattedSeq = String.format(Locale.US, "%03d", nextCounter)
            "#$formattedSeq"
        }
    }

    /**
     * Resets or sets the sequential bill number counter for a prefix in a restaurant.
     */
    suspend fun setBillPrefixCounter(restaurantId: String, prefix: String, nextNumber: Int) = withContext(Dispatchers.IO) {
        val restaurant = restaurantDao.getRestaurantById(restaurantId) ?: return@withContext
        val prefixKey = if (prefix.trim().isNotBlank()) prefix.trim().uppercase() else "DEFAULT"

        val counterMap = try {
            val json = restaurant.billPrefixCountersJson
            val jsonObj = org.json.JSONObject(json)
            val map = mutableMapOf<String, Int>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = jsonObj.optInt(k, 0)
            }
            map
        } catch (e: Exception) {
            mutableMapOf<String, Int>()
        }

        counterMap[prefixKey] = (nextNumber - 1).coerceAtLeast(0)
        val updatedJson = org.json.JSONObject(counterMap as Map<*, *>).toString()
        val updatedRest = restaurant.copy(
            billPrefixCountersJson = updatedJson,
            updatedAt = System.currentTimeMillis()
        )
        restaurantDao.insertOrUpdate(updatedRest)
        scope.launch {
            try {
                syncManager.uploadRestaurant(currentUserId.value, updatedRest)
            } catch (e: Exception) {
                Log.w("PosRepository", "Prefix counter sync deferred", e)
            }
        }
    }

    /**
     * First-time Restaurant Setup: User fills name, address, phone, footer, logo.
     */
    suspend fun saveInitialRestaurantSetup(
        name: String,
        address: String,
        phone: String,
        footerNote: String,
        logoUri: String? = null,
        fssaiNumber: String = "",
        isFssaiEnabled: Boolean = false,
        showFssaiOnBill: Boolean = false,
        gstNumber: String = "",
        gstRate: Double = 5.0,
        isGstEnabled: Boolean = false,
        showGstOnBill: Boolean = false
    ): RestaurantEntity = withContext(Dispatchers.IO) {
        val restId = "rest_${UUID.randomUUID().toString().take(8)}"
        val restaurant = RestaurantEntity(
            id = restId,
            name = name.trim(),
            address = address.trim(),
            phone = phone.trim(),
            footerNote = footerNote.trim(),
            customLogoUri = logoUri,
            isActive = true,
            fssaiNumber = fssaiNumber.trim(),
            isFssaiEnabled = isFssaiEnabled,
            showFssaiOnBill = showFssaiOnBill,
            gstNumber = gstNumber.trim(),
            gstRate = gstRate,
            isGstEnabled = isGstEnabled,
            showGstOnBill = showGstOnBill
        )
        restaurantDao.insertOrUpdate(restaurant)
        syncManager.uploadRestaurant(currentUserId.value, restaurant)
        restaurant
    }

    /**
     * Send order to kitchen (KOT):
     * Does NOT settle the bill. Saves as unsettled, marks table as occupied.
     */
    suspend fun sendOrderToKitchen(
        restaurantId: String,
        restaurantName: String,
        orderType: String,
        tableId: String? = null,
        tableName: String? = null,
        customerName: String = "",
        customerPhone: String = "",
        items: List<BillItem>,
        existingBillId: String? = null
    ): BillEntity = withContext(Dispatchers.IO) {
        val subtotal = items.sumOf { it.totalPrice }

        val bill = db.withTransaction {
            val existing = if (existingBillId != null) billDao.getBillById(existingBillId) else null
            val billId = existing?.id ?: existingBillId ?: "BILL-${System.currentTimeMillis().toString().takeLast(6)}"
            val billNumber = existing?.billNumber ?: generateNextBillNumber(restaurantId)
            val createdAt = existing?.createdAt ?: System.currentTimeMillis()
            val billTimestamp = existing?.billTimestamp ?: System.currentTimeMillis()

            val fifoCost = processFifoDeductionForBill(billId, items, orderType)
            val foodCost = if (fifoCost > 0.0) fifoCost else calculateItemsFoodCost(items, orderType)
            val existingDiscountType = existing?.discountType ?: "NONE"
            val existingDiscountVal = existing?.discountValue ?: 0.0
            val eligibleSubtotal = calculateEligibleSubtotal(items)
            val discountAmount = when (existingDiscountType) {
                "PERCENT" -> (eligibleSubtotal * (existingDiscountVal.coerceIn(0.0, 100.0) / 100.0))
                "FLAT" -> existingDiscountVal.coerceAtMost(eligibleSubtotal)
                else -> 0.0
            }
            val totalAmount = (subtotal - discountAmount).coerceAtLeast(0.0)

            val b = BillEntity(
                id = billId,
                billNumber = billNumber,
                restaurantId = restaurantId,
                restaurantName = restaurantName,
                orderType = orderType,
                tableId = tableId,
                tableName = tableName,
                customerName = customerName.trim(),
                customerPhone = customerPhone.trim(),
                items = items,
                subtotal = subtotal,
                discountType = existingDiscountType,
                discountValue = existingDiscountVal,
                discountAmount = discountAmount,
                totalAmount = totalAmount,
                paymentMethod = existing?.paymentMethod ?: "CASH",
                isSettled = false,
                isStockDeducted = true,
                totalFoodCost = foodCost,
                billTimestamp = billTimestamp,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis()
            )
            billDao.insertOrUpdate(b)
            b
        }

        // Save customer details if entered
        if (customerPhone.isNotBlank() || customerName.isNotBlank()) {
            val existing = if (customerPhone.isNotBlank()) customerDao.getCustomerByContact(customerPhone.trim()) else null
            val customer = existing?.copy(
                name = customerName.ifBlank { existing.name }.trim(),
                contactNumber = customerPhone.ifBlank { existing.contactNumber }.trim(),
                updatedAt = System.currentTimeMillis()
            ) ?: CustomerEntity(
                id = if (customerPhone.isNotBlank()) "cust_${customerPhone.filter { it.isDigit() }}" else "cust_${UUID.randomUUID()}",
                name = customerName.ifBlank { "Customer" }.trim(),
                contactNumber = customerPhone.trim(),
                updatedAt = System.currentTimeMillis()
            )
            customerDao.insertOrUpdate(customer)
            scope.launch {
                syncManager.uploadCustomer(currentUserId.value, customer)
            }
        }

        // Table marked occupied immediately
        if (tableId != null) {
            cafeTableDao.updateTableOccupancy(tableId, isOccupied = true, activeBillId = bill.id)
            val tbl = cafeTableDao.getTableById(tableId)
            if (tbl != null) {
                scope.launch {
                    try {
                        syncManager.uploadTable(currentUserId.value, tbl)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Table sync deferred", e)
                    }
                }
            }
        }

        scope.launch {
            syncManager.uploadBill(currentUserId.value, bill)
        }

        bill
    }

    /**
     * Calculates the subtotal of items that are eligible for discounts.
     * An item is eligible only if BOTH its own isDiscountEligible AND its category's isDiscountEligible are true.
     */
    suspend fun calculateEligibleSubtotal(items: List<BillItem>): Double {
        val menuItems = menuItemDao.getAllMenuItemsDirect().associateBy { it.id }
        val categories = categoryDao.getAllCategoriesDirect().associateBy { it.id }
        return items.sumOf { item ->
            val mi = menuItems[item.dishId]
            val itemEligible = mi?.isDiscountEligible ?: true
            val cat = mi?.categoryId?.let { categories[it] }
            val catEligible = cat?.isDiscountEligible ?: true
            val isEligible = itemEligible && catEligible
            if (isEligible) item.totalPrice else 0.0
        }
    }

    /**
     * Cash Out an active running bill:
     * Settles the bill, releases the table, updates cash register if CASH or SPLIT.
     */
    suspend fun cashOutActiveBill(
        billId: String,
        paymentMethod: String,
        discountType: String,
        discountValue: Double,
        items: List<BillItem>? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        cashAmount: Double = 0.0,
        upiAmount: Double = 0.0,
        appliedRewardType: String = "NONE",
        appliedOfferId: String? = null,
        appliedOfferName: String = "",
        pointsToRedeem: Int = 0,
        enrollInPass: Boolean? = null
    ): BillEntity = withContext(Dispatchers.IO) {
        val settled = db.withTransaction {
            val existing = billDao.getBillById(billId) ?: throw IllegalArgumentException("Bill not found: $billId")
            val finalItems = items ?: existing.items
            val subtotal = finalItems.sumOf { it.totalPrice }
            val eligibleSubtotal = calculateEligibleSubtotal(finalItems)
            val discountAmount = when (discountType) {
                "PERCENT" -> (eligibleSubtotal * (discountValue.coerceIn(0.0, 100.0) / 100.0))
                "FLAT" -> discountValue.coerceAtMost(eligibleSubtotal)
                else -> 0.0
            }
            val totalAmount = (subtotal - discountAmount - pointsToRedeem).coerceAtLeast(0.0)

            val finalCashAmount = when (paymentMethod) {
                "CASH" -> (totalAmount - pointsToRedeem).coerceAtLeast(0.0)
                "SPLIT" -> cashAmount
                else -> 0.0
            }
            val finalUpiAmount = when (paymentMethod) {
                "UPI" -> (totalAmount - pointsToRedeem).coerceAtLeast(0.0)
                "SPLIT" -> upiAmount
                else -> 0.0
            }

            val fifoCost = if (existing.isStockDeducted && existing.totalFoodCost > 0.0 && items == null) {
                existing.totalFoodCost
            } else {
                processFifoDeductionForBill(existing.id, finalItems, existing.orderType)
            }
            val foodCost = if (fifoCost > 0.0) fifoCost else calculateItemsFoodCost(finalItems, existing.orderType)

            val finalCustomerName = customerName?.trim() ?: existing.customerName
            val finalCustomerPhone = customerPhone?.trim() ?: existing.customerPhone

            val s = existing.copy(
                customerName = finalCustomerName,
                customerPhone = finalCustomerPhone,
                items = finalItems,
                subtotal = subtotal,
                discountType = discountType,
                discountValue = discountValue,
                discountAmount = discountAmount,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                cashAmount = finalCashAmount,
                upiAmount = finalUpiAmount,
                isSettled = true,
                isStockDeducted = true,
                totalFoodCost = foodCost,
                updatedAt = System.currentTimeMillis()
            )
            billDao.insertOrUpdate(s)
            s
        }

        // Free table
        if (settled.tableId != null) {
            cafeTableDao.updateTableOccupancy(settled.tableId, isOccupied = false, activeBillId = null)
            val tbl = cafeTableDao.getTableById(settled.tableId)
            if (tbl != null) {
                scope.launch {
                    try {
                        syncManager.uploadTable(currentUserId.value, tbl)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Table sync deferred", e)
                    }
                }
            }
        }

        // Process loyalty progression, reward points, and FIFO points redemption
        val finalSettled = processSettlementLoyalty(
            bill = settled,
            pointsToRedeem = pointsToRedeem,
            appliedRewardType = appliedRewardType,
            appliedOfferId = appliedOfferId,
            appliedOfferName = appliedOfferName,
            paymentMethod = paymentMethod,
            enrollInPass = enrollInPass
        )

        // Cash register
        val cashToRecord = when (paymentMethod) {
            "CASH" -> (finalSettled.totalAmount - pointsToRedeem).coerceAtLeast(0.0)
            "SPLIT" -> finalSettled.cashAmount
            else -> 0.0
        }
        if (cashToRecord > 0.0) {
            val dateStr = DateUtils.formatDate(finalSettled.billTimestamp, "yyyy-MM-dd")
            val reg = cashRegisterDao.getRegisterForDate(finalSettled.restaurantId, dateStr)
            if (reg != null) {
                val updatedReg = reg.copy(
                    cashSales = reg.cashSales + cashToRecord,
                    updatedAt = System.currentTimeMillis()
                )
                cashRegisterDao.insertOrUpdate(updatedReg)
                scope.launch {
                    syncManager.uploadCashRegister(currentUserId.value, updatedReg)
                }
            }
        }

        finalSettled
    }

    /**
     * Transfer/Shift an active order from one table to another.
     */
    suspend fun transferTable(
        sourceTableId: String,
        destTableId: String,
        billId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val destTable = cafeTableDao.getTableById(destTableId) ?: return@withContext false
        val bill = billDao.getBillById(billId) ?: return@withContext false

        // Release old table
        cafeTableDao.updateTableOccupancy(sourceTableId, isOccupied = false, activeBillId = null)
        val oldTbl = cafeTableDao.getTableById(sourceTableId)
        if (oldTbl != null) syncManager.uploadTable(currentUserId.value, oldTbl)

        // Occupy new table
        cafeTableDao.updateTableOccupancy(destTableId, isOccupied = true, activeBillId = billId)
        val newTbl = cafeTableDao.getTableById(destTableId)
        if (newTbl != null) syncManager.uploadTable(currentUserId.value, newTbl)

        // Update bill
        val updatedBill = bill.copy(
            tableId = destTableId,
            tableName = destTable.name,
            updatedAt = System.currentTimeMillis()
        )
        billDao.insertOrUpdate(updatedBill)
        true
    }

    /**
     * Add new custom table
     */
    suspend fun addCustomTable(name: String, capacity: Int, restaurantId: String): CafeTableEntity = withContext(Dispatchers.IO) {
        val table = CafeTableEntity(
            id = "tbl_${UUID.randomUUID().toString().take(8)}",
            restaurantId = restaurantId,
            name = name.trim(),
            capacity = capacity,
            isOccupied = false,
            activeBillId = null
        )
        cafeTableDao.insertOrUpdate(table)
        syncManager.uploadTable(currentUserId.value, table)
        table
    }

    suspend fun uploadTable(table: CafeTableEntity) {
        try {
            syncManager.uploadTable(currentUserId.value, table)
        } catch (e: Exception) {
            Log.w("PosRepository", "Table sync deferred", e)
        }
    }

    /**
     * Creates and settles a bill (Dine In or Takeaway).
     * Strictly Cash or UPI. Strictly No GST.
     * Supports custom backdated timestamp.
     */
    suspend fun createAndSettleBill(
        restaurantId: String,
        restaurantName: String,
        orderType: String, // "DINE_IN" or "TAKEAWAY"
        tableId: String? = null,
        tableName: String? = null,
        customerName: String,
        customerPhone: String,
        items: List<BillItem>,
        discountType: String, // "NONE", "FLAT", "PERCENT"
        discountValue: Double,
        paymentMethod: String, // "CASH", "UPI", "CREDIT", "SPLIT"
        cashAmount: Double = 0.0,
        upiAmount: Double = 0.0,
        customTimestamp: Long? = null, // For backdated bill generation
        appliedRewardType: String = "NONE",
        appliedOfferId: String? = null,
        appliedOfferName: String = "",
        pointsToRedeem: Int = 0,
        enrollInPass: Boolean? = null
    ): BillEntity = withContext(Dispatchers.IO) {
        val timestamp = customTimestamp ?: System.currentTimeMillis()
        val billId = "BILL-${System.currentTimeMillis().toString().takeLast(6)}"
        val billNumber = generateNextBillNumber(restaurantId)

        val subtotal = items.sumOf { it.totalPrice }
        val eligibleSubtotal = calculateEligibleSubtotal(items)
        val discountAmount = when (discountType) {
            "PERCENT" -> (eligibleSubtotal * (discountValue.coerceIn(0.0, 100.0) / 100.0))
            "FLAT" -> discountValue.coerceAtMost(eligibleSubtotal)
            else -> 0.0
        }
        val totalAmount = (subtotal - discountAmount - pointsToRedeem).coerceAtLeast(0.0)

        val finalCashAmount = when (paymentMethod) {
            "CASH" -> (totalAmount - pointsToRedeem).coerceAtLeast(0.0)
            "SPLIT" -> cashAmount
            else -> 0.0
        }
        val finalUpiAmount = when (paymentMethod) {
            "UPI" -> (totalAmount - pointsToRedeem).coerceAtLeast(0.0)
            "SPLIT" -> upiAmount
            else -> 0.0
        }

        val bill = db.withTransaction {
            val fifoCost = processFifoDeductionForBill(billId, items, orderType)
            val foodCost = if (fifoCost > 0.0) fifoCost else calculateItemsFoodCost(items, orderType)
            val b = BillEntity(
                id = billId,
                billNumber = billNumber,
                restaurantId = restaurantId,
                restaurantName = restaurantName,
                orderType = orderType,
                tableId = tableId,
                tableName = tableName,
                customerName = customerName.trim(),
                customerPhone = customerPhone.trim(),
                items = items,
                subtotal = subtotal,
                discountType = discountType,
                discountValue = discountValue,
                discountAmount = discountAmount,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                cashAmount = finalCashAmount,
                upiAmount = finalUpiAmount,
                isSettled = true,
                isStockDeducted = true,
                totalFoodCost = foodCost,
                billTimestamp = timestamp,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            billDao.insertOrUpdate(b)
            b
        }

        // 2. Process loyalty progression, reward points, and FIFO points redemption
        val finalBill = processSettlementLoyalty(
            bill = bill,
            pointsToRedeem = pointsToRedeem,
            appliedRewardType = appliedRewardType,
            appliedOfferId = appliedOfferId,
            appliedOfferName = appliedOfferName,
            paymentMethod = paymentMethod,
            enrollInPass = enrollInPass
        )

        // 3. If Dine In table was occupied, release table status
        if (tableId != null) {
            cafeTableDao.updateTableOccupancy(tableId, isOccupied = false, activeBillId = null)
        }

        // 4. Update daily cash register if CASH or SPLIT with cash portion
        val cashToRecord = when (paymentMethod) {
            "CASH" -> (finalBill.totalAmount - pointsToRedeem).coerceAtLeast(0.0)
            "SPLIT" -> finalBill.cashAmount
            else -> 0.0
        }
        if (cashToRecord > 0.0) {
            val dateStr = DateUtils.formatDate(timestamp, "yyyy-MM-dd")
            val existingReg = cashRegisterDao.getRegisterForDate(restaurantId, dateStr)
            if (existingReg != null) {
                val updatedReg = existingReg.copy(
                    cashSales = existingReg.cashSales + cashToRecord,
                    updatedAt = System.currentTimeMillis()
                )
                cashRegisterDao.insertOrUpdate(updatedReg)
                scope.launch {
                    syncManager.uploadCashRegister(currentUserId.value, updatedReg)
                }
            }
        }

        finalBill
    }

    /**
     * Updates an existing bill (allows editing backdated or previously settled bills).
     */
    suspend fun updateSettledBill(updatedBill: BillEntity) {
        billDao.insertOrUpdate(updatedBill)
        scope.launch {
            syncManager.uploadBill(currentUserId.value, updatedBill)
        }
    }

    /**
     * Combo operations
     */
    fun getCombosByRestaurant(restaurantId: String): Flow<List<com.example.data.local.entity.ComboEntity>> {
        return comboDao.getCombosByRestaurant(restaurantId)
    }

    fun getActiveCombosByRestaurant(restaurantId: String): Flow<List<com.example.data.local.entity.ComboEntity>> {
        return comboDao.getActiveCombosByRestaurant(restaurantId)
    }

    suspend fun saveCombo(combo: com.example.data.local.entity.ComboEntity) = withContext(Dispatchers.IO) {
        comboDao.insertOrUpdate(combo)
        scope.launch {
            try {
                syncManager.uploadCombo(currentUserId.value, combo)
            } catch (e: Exception) {
                Log.w("PosRepository", "Combo sync deferred", e)
            }
        }
    }

    suspend fun deleteCombo(comboId: String) = withContext(Dispatchers.IO) {
        comboDao.deleteById(comboId)
        scope.launch {
            try {
                syncManager.deleteCombo(currentUserId.value, comboId)
            } catch (e: Exception) {
                Log.w("PosRepository", "Combo delete sync deferred", e)
            }
        }
    }

    /**
     * Deducts recipe ingredients from inventory for the given bill items.
     * Backwards-compatible facade that forwards to FIFO processor.
     */
    suspend fun deductStockForOrderItems(items: List<BillItem>, orderType: String) = withContext(Dispatchers.IO) {
        processFifoDeductionForBill("TEMP-${UUID.randomUUID()}", items, orderType)
    }

    /**
     * Executes FIFO batch consumption for a bill inside a transaction.
     * Guaranteed atomic, idempotent, non-blocking for negative stock.
     * Returns the exact food cost for the bill.
     */
    suspend fun processFifoDeductionForBill(
        billId: String,
        items: List<BillItem>,
        orderType: String
    ): Double = withContext(Dispatchers.IO) {
        db.withTransaction {
            // Idempotency check: if deductions already recorded for this bill, return existing cost
            val existingDeductions = inventoryBatchDao.getDeductionsForBill(billId)
            if (existingDeductions.isNotEmpty()) {
                Log.d("PosRepository", "Bill $billId already has deductions recorded. Skipping re-deduction.")
                return@withTransaction existingDeductions.filter { it.status != "REFUNDED" }.sumOf { it.cost }
            }

            val isTakeaway = orderType == "TAKEAWAY"
            val allMenu = menuItemDao.getAllMenuItemsDirect()
            val allRecipes = recipeDao.getAllRecipesDirect().associateBy { it.menuItemId }
            val inventoryMap = inventoryDao.getAllInventoryDirect().associateBy { it.id }

            // Aggregate required quantities per (menuItem, inventoryItem)
            data class RequirementKey(val menuItemId: String, val inventoryItemId: String)
            val requirements = mutableMapOf<RequirementKey, Double>()

            for (item in items) {
                val qty = item.quantity
                if (item.dishId.startsWith("combo_")) {
                    val notes = item.notes
                    val hasEncodedDishes = notes.contains("[DISHES:")
                    if (hasEncodedDishes) {
                        val encodedPart = notes.substringAfter("[DISHES:").substringBefore("]")
                        val pairs = encodedPart.split(";").filter { it.isNotBlank() }
                        for (pair in pairs) {
                            val parts = pair.split("=")
                            val dishId = parts[0]
                            val subQty = parts.getOrNull(1)?.toIntOrNull() ?: 1
                            val recipe = allRecipes[dishId]
                            if (recipe != null) {
                                for (ing in recipe.ingredients) {
                                    if (isTakeaway || !ing.isTakeawayExtra) {
                                        val invItem = inventoryMap[ing.inventoryItemId]
                                        val baseUnit = invItem?.unit ?: "Nos"
                                        val factor = FoodCostCalculator.getConversionFactorToBase(ing.usageUnit, baseUnit)
                                        val baseAmount = ing.quantity * factor * subQty * qty
                                        val key = RequirementKey(dishId, ing.inventoryItemId)
                                        requirements[key] = (requirements[key] ?: 0.0) + baseAmount
                                    }
                                }
                            }
                        }
                    } else {
                        val cleanedNotes = notes.substringBefore("[DISHES:").trim()
                        val subNames = cleanedNotes.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        for (rawSubName in subNames) {
                            val subName = rawSubName.replace(Regex("^\\d+\\s*x\\s*", RegexOption.IGNORE_CASE), "").trim()
                            val dish = allMenu.find { it.name.equals(subName, ignoreCase = true) }
                            if (dish != null) {
                                val recipe = allRecipes[dish.id]
                                if (recipe != null) {
                                    for (ing in recipe.ingredients) {
                                        if (isTakeaway || !ing.isTakeawayExtra) {
                                            val invItem = inventoryMap[ing.inventoryItemId]
                                            val baseUnit = invItem?.unit ?: "Nos"
                                            val factor = FoodCostCalculator.getConversionFactorToBase(ing.usageUnit, baseUnit)
                                            val baseAmount = ing.quantity * factor * qty
                                            val key = RequirementKey(dish.id, ing.inventoryItemId)
                                            requirements[key] = (requirements[key] ?: 0.0) + baseAmount
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val recipe = allRecipes[item.dishId]
                    if (recipe != null) {
                        for (ing in recipe.ingredients) {
                            val matchesMode = when (ing.applyMode) {
                                "DINE_IN_ONLY" -> !isTakeaway
                                "TAKEAWAY_ONLY" -> isTakeaway
                                else -> true
                            } && (isTakeaway || !ing.isTakeawayExtra)

                            if (matchesMode) {
                                val invItem = inventoryMap[ing.inventoryItemId]
                                val baseUnit = invItem?.unit ?: "Nos"
                                val factor = FoodCostCalculator.getConversionFactorToBase(ing.usageUnit, baseUnit)
                                val baseAmount = ing.quantity * factor * qty
                                val key = RequirementKey(item.dishId, ing.inventoryItemId)
                                requirements[key] = (requirements[key] ?: 0.0) + baseAmount
                            }
                        }
                    }

                    // Also deduct inventory for selected Add-ons!
                    val selectedAddons = item.getSelectedAddonsList()
                    for (addon in selectedAddons) {
                        val invId = addon.inventoryItemId
                        if (!invId.isNullOrBlank()) {
                            val invItem = inventoryMap[invId]
                            val baseUnit = invItem?.unit ?: "Nos"
                            val factor = FoodCostCalculator.getConversionFactorToBase(addon.usageUnit, baseUnit)
                            val baseAmount = addon.inventoryQty * addon.quantity * qty * factor
                            val key = RequirementKey(item.dishId, invId)
                            requirements[key] = (requirements[key] ?: 0.0) + baseAmount
                        }
                    }
                }
            }

            // Group requirements by inventory item ID
            val itemToRequirements = requirements.entries.groupBy { it.key.inventoryItemId }
            val deductionsList = mutableListOf<BillBatchDeductionEntity>()
            val stockTxns = mutableListOf<StockTransactionEntity>()

            for ((invId, entries) in itemToRequirements) {
                val invItem = inventoryDao.getItemById(invId) ?: continue
                val totalAmountNeeded = entries.sumOf { it.value }
                if (totalAmountNeeded <= 0.000001) continue

                val activeBatches = inventoryBatchDao.getActiveBatchesForItemFifo(invId)
                val latestBatch = inventoryBatchDao.getLatestBatchForItem(invId)
                val fallbackRate = latestBatch?.purchaseRate ?: if (invItem.purchasePrice > 0.0) invItem.purchasePrice else 0.0

                var remainingNeeded = totalAmountNeeded
                var batchIndex = 0

                // 1. Consume from active batches (FIFO)
                while (remainingNeeded > 0.000001 && batchIndex < activeBatches.size) {
                    val batch = activeBatches[batchIndex]
                    val avail = batch.remainingQuantity
                    if (avail <= 0.000001) {
                        batchIndex++
                        continue
                    }
                    val toTake = kotlin.math.min(remainingNeeded, avail)
                    val newRemaining = avail - toTake
                    val isConsumed = newRemaining <= 0.000001
                    val cost = kotlin.math.round(toTake * batch.purchaseRate * 100.0) / 100.0

                    val updatedBatch = batch.copy(
                        remainingQuantity = if (isConsumed) 0.0 else newRemaining,
                        isConsumed = isConsumed,
                        status = if (isConsumed) "ARCHIVED" else "ACTIVE",
                        updatedAt = System.currentTimeMillis()
                    )
                    inventoryBatchDao.updateBatch(updatedBatch)

                    val deductionId = "ded_${billId}_${invId}_${batch.id}_${deductionsList.size}"
                    val deduction = BillBatchDeductionEntity(
                        id = deductionId,
                        billId = billId,
                        inventoryItemId = invId,
                        batchId = batch.id,
                        quantityDeducted = toTake,
                        rate = batch.purchaseRate,
                        cost = cost,
                        isNegativeStock = false,
                        timestamp = System.currentTimeMillis(),
                        menuItemId = entries.firstOrNull()?.key?.menuItemId ?: "",
                        createdAt = System.currentTimeMillis(),
                        status = "ACTIVE",
                        referenceId = "REF-$billId-$invId-${deductionsList.size}"
                    )
                    deductionsList.add(deduction)

                    remainingNeeded -= toTake
                    batchIndex++
                }

                // 2. Negative stock portion (non-blocking)
                if (remainingNeeded > 0.000001) {
                    val negativeCost = kotlin.math.round(remainingNeeded * fallbackRate * 100.0) / 100.0
                    val deductionId = "ded_${billId}_${invId}_neg_${deductionsList.size}"
                    val deduction = BillBatchDeductionEntity(
                        id = deductionId,
                        billId = billId,
                        inventoryItemId = invId,
                        batchId = null,
                        quantityDeducted = remainingNeeded,
                        rate = fallbackRate,
                        cost = negativeCost,
                        isNegativeStock = true,
                        timestamp = System.currentTimeMillis(),
                        menuItemId = entries.firstOrNull()?.key?.menuItemId ?: "",
                        createdAt = System.currentTimeMillis(),
                        status = "ACTIVE",
                        referenceId = "REF-$billId-$invId-neg-${deductionsList.size}"
                    )
                    deductionsList.add(deduction)
                }

                // Update inventory item current stock and current FIFO rate
                val remainingActiveBatches = inventoryBatchDao.getActiveBatchesForItemFifo(invId)
                val totalActiveRemaining = remainingActiveBatches.sumOf { it.remainingQuantity }
                val nextOldestBatch = remainingActiveBatches.firstOrNull()
                val updatedFifoRate = nextOldestBatch?.purchaseRate ?: fallbackRate

                val updatedStock = if (remainingNeeded > 0.000001) {
                    invItem.currentStock - totalAmountNeeded
                } else {
                    totalActiveRemaining
                }

                inventoryDao.insertOrUpdateItem(
                    invItem.copy(
                        currentStock = updatedStock,
                        purchasePrice = updatedFifoRate,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // Audit ledger
                stockTxns.add(
                    StockTransactionEntity(
                        id = "st_${UUID.randomUUID()}",
                        restaurantId = invItem.restaurantId,
                        inventoryItemId = invId,
                        itemName = invItem.name,
                        transactionType = "SALE",
                        quantity = totalAmountNeeded,
                        unitRate = fallbackRate,
                        balanceAfter = updatedStock,
                        billId = billId,
                        referenceId = "SALE-$billId",
                        notes = "Deducted for bill $billId",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            if (deductionsList.isNotEmpty()) {
                inventoryBatchDao.insertDeductions(deductionsList)
            }
            if (stockTxns.isNotEmpty()) {
                stockTransactionDao.insertAll(stockTxns)
            }

            scope.launch {
                try {
                    syncManager.uploadBillBatchDeductions(currentUserId.value, deductionsList)
                    for (st in stockTxns) {
                        syncManager.uploadStockTransaction(currentUserId.value, st)
                    }
                } catch (e: Exception) {
                    Log.w("PosRepository", "Deduction cloud sync deferred", e)
                }
            }

            val totalCost = deductionsList.sumOf { it.cost }
            kotlin.math.round(totalCost * 100.0) / 100.0
        }
    }

    /**
     * Cancels a bill, marks it as CANCELLED, safely restores inventory (if restoreInventory is true),
     * reverses loyalty points/visits and customer dues, and syncs everything to Cloud Firestore.
     */
    suspend fun cancelBill(
        billId: String,
        reason: String = "Cancelled by user",
        restoreInventory: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        db.withTransaction {
            val bill = billDao.getBillById(billId) ?: return@withTransaction false
            val now = System.currentTimeMillis()

            // 1. Inventory Restoration or Kitchen Wastage Logging
            if (bill.isStockDeducted) {
                val deductions = inventoryBatchDao.getDeductionsForBill(billId)
                if (restoreInventory) {
                    val activeDeductions = deductions.filter { it.status != "REFUNDED" }
                    val updatedDeductions = mutableListOf<BillBatchDeductionEntity>()
                    val stockTxns = mutableListOf<StockTransactionEntity>()

                    for (deduction in activeDeductions) {
                        val invItem = inventoryDao.getItemById(deduction.inventoryItemId)
                        val qty = deduction.quantityDeducted

                        if (deduction.batchId != null) {
                            val batch = inventoryBatchDao.getBatchById(deduction.batchId)
                            if (batch != null) {
                                val newRemaining = batch.remainingQuantity + qty
                                val restoredBatch = batch.copy(
                                    remainingQuantity = newRemaining,
                                    isConsumed = false,
                                    status = "ACTIVE",
                                    updatedAt = now
                                )
                                inventoryBatchDao.updateBatch(restoredBatch)
                            } else {
                                val restoredBatch = InventoryBatchEntity(
                                    id = deduction.batchId,
                                    restaurantId = invItem?.restaurantId ?: "",
                                    inventoryItemId = deduction.inventoryItemId,
                                    itemName = invItem?.name ?: "",
                                    initialQuantity = qty,
                                    remainingQuantity = qty,
                                    purchaseRate = deduction.rate,
                                    unit = invItem?.unit ?: "Nos",
                                    batchType = "RESTORATION",
                                    notes = "Restored from cancelled bill ${bill.billNumber}",
                                    timestamp = now,
                                    isConsumed = false,
                                    updatedAt = now,
                                    createdAt = now,
                                    status = "ACTIVE"
                                )
                                inventoryBatchDao.insertBatch(restoredBatch)
                            }
                        }

                        if (invItem != null) {
                            val activeBatchesAfterRefund = inventoryBatchDao.getActiveBatchesForItemFifo(deduction.inventoryItemId)
                            val oldestActive = activeBatchesAfterRefund.firstOrNull()
                            val restoredRate = oldestActive?.purchaseRate ?: deduction.rate
                            val totalBatchStock = activeBatchesAfterRefund.sumOf { it.remainingQuantity }
                            val restoredStock = if (totalBatchStock > 0.0) totalBatchStock else invItem.currentStock + qty
                            inventoryDao.insertOrUpdateItem(
                                invItem.copy(
                                    currentStock = restoredStock,
                                    purchasePrice = restoredRate,
                                    updatedAt = now
                                )
                            )
                            stockTxns.add(
                                StockTransactionEntity(
                                    id = "st_${UUID.randomUUID()}",
                                    restaurantId = invItem.restaurantId,
                                    inventoryItemId = invItem.id,
                                    itemName = invItem.name,
                                    transactionType = "REFUND",
                                    quantity = qty,
                                    unitRate = deduction.rate,
                                    balanceAfter = restoredStock,
                                    batchId = deduction.batchId,
                                    billId = billId,
                                    referenceId = "CANCEL-${bill.billNumber}",
                                    notes = "Restored from cancelled bill ${bill.billNumber} ($reason)",
                                    timestamp = now
                                )
                            )
                        }
                        updatedDeductions.add(deduction.copy(status = "REFUNDED"))
                    }

                    if (updatedDeductions.isNotEmpty()) {
                        inventoryBatchDao.updateDeductions(updatedDeductions)
                    }
                    if (stockTxns.isNotEmpty()) {
                        stockTransactionDao.insertAll(stockTxns)
                    }

                    scope.launch {
                        try {
                            syncManager.uploadBillBatchDeductions(currentUserId.value, updatedDeductions)
                            for (st in stockTxns) {
                                syncManager.uploadStockTransaction(currentUserId.value, st)
                            }
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Cancel inventory sync deferred", e)
                        }
                    }
                } else {
                    // Mark as kitchen wastage
                    val stockTxns = deductions.mapNotNull { deduction ->
                        val invItem = inventoryDao.getItemById(deduction.inventoryItemId) ?: return@mapNotNull null
                        StockTransactionEntity(
                            id = "st_${UUID.randomUUID()}",
                            restaurantId = invItem.restaurantId,
                            inventoryItemId = invItem.id,
                            itemName = invItem.name,
                            transactionType = "WASTAGE",
                            quantity = deduction.quantityDeducted,
                            unitRate = deduction.rate,
                            balanceAfter = invItem.currentStock,
                            batchId = deduction.batchId,
                            billId = billId,
                            referenceId = "WASTE-${bill.billNumber}",
                            notes = "Kitchen Wastage from cancelled bill ${bill.billNumber} ($reason)",
                            timestamp = now
                        )
                    }
                    if (stockTxns.isNotEmpty()) {
                        stockTransactionDao.insertAll(stockTxns)
                        scope.launch {
                            for (st in stockTxns) {
                                syncManager.uploadStockTransaction(currentUserId.value, st)
                            }
                        }
                    }
                }
            }

            // 2. Customer Loyalty / Points Reversal
            if (bill.customerPhone.isNotBlank()) {
                val cust = customerDao.getCustomerByContact(bill.customerPhone.trim())
                if (cust != null) {
                    val rewardEarned = bill.rewardPointsEarned
                    val rewardRedeemed = bill.rewardPointsRedeemed
                    val giftRedeemed = bill.giftPointsRedeemed

                    val newRewardBal = maxOf(0, cust.rewardPointsBalance - rewardEarned + rewardRedeemed)
                    val newGiftBal = cust.giftPointsBalance + giftRedeemed
                    val newVisitCount = maxOf(0, cust.loyaltyVisitCount - 1)

                    val history = parseLoyaltyHistory(cust.loyaltyHistoryJson).toMutableList()
                    history.add(
                        LoyaltyHistoryItem(
                            eventType = "VISIT_REVERSED",
                            visitNumber = newVisitCount,
                            billId = bill.id,
                            billNumber = bill.billNumber,
                            billAmount = bill.totalAmount,
                            notes = "Cancelled bill ${bill.billNumber} ($reason)",
                            timestamp = now
                        )
                    )

                    val updatedCust = cust.copy(
                        rewardPointsBalance = newRewardBal,
                        giftPointsBalance = newGiftBal,
                        loyaltyVisitCount = newVisitCount,
                        loyaltyHistoryJson = formatLoyaltyHistory(history),
                        updatedAt = now
                    )
                    customerDao.insertOrUpdate(updatedCust)

                    // Log ledger reversal
                    if (rewardEarned > 0) {
                        pointsLedgerDao.insert(
                            PointsLedgerEntity(
                                id = "led_${UUID.randomUUID()}",
                                customerId = cust.id,
                                customerPhone = cust.contactNumber,
                                customerName = cust.name,
                                transactionType = "REVERSAL",
                                pointsAmount = -rewardEarned,
                                balanceType = "REWARD",
                                billId = bill.id,
                                billNumber = bill.billNumber,
                                rewardPointsBalanceAfter = newRewardBal,
                                giftPointsBalanceAfter = newGiftBal,
                                notes = "Points reversed for cancelled bill ${bill.billNumber}",
                                timestamp = now
                            )
                        )
                    }

                    scope.launch {
                        try {
                            syncManager.uploadCustomer(currentUserId.value, updatedCust)
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Customer cancel reversal sync deferred", e)
                        }
                    }
                }
            }

            // 3. Mark Bill as CANCELLED
            val updatedBill = bill.copy(
                isCancelled = true,
                status = "CANCELLED",
                isSettled = false,
                isVoided = true,
                cancellationReason = reason,
                cancelledAt = now,
                isStockDeducted = false,
                updatedAt = now
            )
            billDao.insertOrUpdate(updatedBill)

            // Release table occupancy if associated with a table
            if (bill.tableId != null) {
                cafeTableDao.updateTableOccupancy(bill.tableId, isOccupied = false, activeBillId = null)
                val tbl = cafeTableDao.getTableById(bill.tableId)
                if (tbl != null) {
                    scope.launch {
                        try {
                            syncManager.uploadTable(currentUserId.value, tbl)
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Table sync deferred on cancel", e)
                        }
                    }
                }
            }

            scope.launch {
                try {
                    syncManager.uploadBill(currentUserId.value, updatedBill)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Cancelled bill cloud sync deferred", e)
                }
            }

            true
        }
    }

    /**
     * Permanently deletes a bill from the local Room database and Cloud Firestore.
     * Safely restores inventory and reverses customer points if needed.
     */
    suspend fun deleteBillPermanently(
        billId: String,
        restoreInventory: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        db.withTransaction {
            val bill = billDao.getBillById(billId) ?: return@withTransaction false
            val now = System.currentTimeMillis()

            // 1. If not already cancelled, restore inventory and reverse points
            if (!bill.isCancelled) {
                if (restoreInventory && bill.isStockDeducted) {
                    val deductions = inventoryBatchDao.getDeductionsForBill(billId)
                    for (deduction in deductions.filter { it.status != "REFUNDED" }) {
                        val invItem = inventoryDao.getItemById(deduction.inventoryItemId)
                        val qty = deduction.quantityDeducted
                        if (deduction.batchId != null) {
                            val batch = inventoryBatchDao.getBatchById(deduction.batchId)
                            if (batch != null) {
                                inventoryBatchDao.updateBatch(
                                    batch.copy(
                                        remainingQuantity = batch.remainingQuantity + qty,
                                        isConsumed = false,
                                        status = "ACTIVE",
                                        updatedAt = now
                                    )
                                )
                            }
                        }
                        if (invItem != null) {
                            inventoryDao.insertOrUpdateItem(
                                invItem.copy(
                                    currentStock = invItem.currentStock + qty,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                }

                if (bill.customerPhone.isNotBlank()) {
                    val cust = customerDao.getCustomerByContact(bill.customerPhone.trim())
                    if (cust != null) {
                        val updatedCust = cust.copy(
                            rewardPointsBalance = maxOf(0, cust.rewardPointsBalance - bill.rewardPointsEarned + bill.rewardPointsRedeemed),
                            giftPointsBalance = cust.giftPointsBalance + bill.giftPointsRedeemed,
                            loyaltyVisitCount = maxOf(0, cust.loyaltyVisitCount - 1),
                            updatedAt = now
                        )
                        customerDao.insertOrUpdate(updatedCust)
                        scope.launch {
                            try { syncManager.uploadCustomer(currentUserId.value, updatedCust) } catch (e: Exception) {}
                        }
                    }
                }
            }

            // 2. Delete deductions from database
            inventoryBatchDao.deleteDeductionsForBill(billId)

            // Release table occupancy if associated with a table
            if (bill.tableId != null) {
                cafeTableDao.updateTableOccupancy(bill.tableId, isOccupied = false, activeBillId = null)
                val tbl = cafeTableDao.getTableById(bill.tableId)
                if (tbl != null) {
                    scope.launch {
                        try {
                            syncManager.uploadTable(currentUserId.value, tbl)
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Table sync deferred on delete", e)
                        }
                    }
                }
            }

            // 3. Delete Bill from local Room DB
            billDao.deleteById(billId)

            // 4. Delete Bill from Cloud Firestore
            scope.launch {
                try {
                    syncManager.deleteBill(currentUserId.value, billId)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Bill cloud permanent delete deferred", e)
                }
            }

            true
        }
    }

    /**
     * Legacy wrapper for refundBillInventory - now safely redirects to cancelBill.
     */
    suspend fun refundBillInventory(billId: String): Boolean = withContext(Dispatchers.IO) {
        cancelBill(billId, "Refunded via legacy action", restoreInventory = true)
    }

    /**
     * Adds inventory stock as a discrete batch, settling any negative deficit.
     */
    suspend fun addInventoryStockBatch(
        itemId: String,
        quantity: Double,
        purchaseRate: Double? = null,
        notes: String = "",
        batchType: String = "PURCHASE",
        referenceId: String? = null,
        customTimestamp: Long? = null
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val invItem = inventoryDao.getItemById(itemId) ?: return@withTransaction
            val rate = purchaseRate ?: if (invItem.purchasePrice > 0.0) invItem.purchasePrice else 0.0

            // Negative deficit settlement per Requirement 7
            val currentStock = invItem.currentStock
            val deficit = if (currentStock < 0.0) kotlin.math.abs(currentStock) else 0.0
            val settleAmount = kotlin.math.min(deficit, quantity)
            val remainingQtyForBatch = (quantity - settleAmount).coerceAtLeast(0.0)
            val isFullyConsumed = remainingQtyForBatch <= 0.000001
            val batchStatus = if (isFullyConsumed) "ARCHIVED" else "ACTIVE"

            val batchNotes = if (settleAmount > 0.0) {
                if (notes.isNotBlank()) "$notes (${String.format(Locale.US, "%.2f", settleAmount)} ${invItem.unit} settled negative deficit)"
                else "${String.format(Locale.US, "%.2f", settleAmount)} ${invItem.unit} settled negative deficit"
            } else notes

            val targetTimestamp = customTimestamp ?: System.currentTimeMillis()
            val batchId = "batch_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val batch = InventoryBatchEntity(
                id = batchId,
                restaurantId = invItem.restaurantId,
                inventoryItemId = invItem.id,
                itemName = invItem.name,
                initialQuantity = quantity,
                remainingQuantity = remainingQtyForBatch,
                purchaseRate = rate,
                unit = invItem.unit,
                batchType = batchType,
                notes = batchNotes,
                timestamp = targetTimestamp,
                isConsumed = isFullyConsumed,
                updatedAt = System.currentTimeMillis(),
                createdAt = targetTimestamp,
                createdByUserId = currentUserId.value,
                referenceTransactionId = referenceId ?: "TXN-${System.currentTimeMillis()}",
                status = batchStatus
            )
            inventoryBatchDao.insertBatch(batch)

            // Re-fetch all active batches in FIFO order
            val activeBatches = inventoryBatchDao.getActiveBatchesForItemFifo(itemId)
            val totalBatchRemaining = activeBatches.sumOf { it.remainingQuantity }
            val oldestActiveBatch = activeBatches.firstOrNull()
            val currentFifoRate = oldestActiveBatch?.purchaseRate ?: rate

            val newCurrentStock = if (currentStock < 0.0 && totalBatchRemaining == 0.0) {
                currentStock + quantity
            } else {
                totalBatchRemaining
            }

            val updatedItem = invItem.copy(
                currentStock = newCurrentStock,
                purchasePrice = currentFifoRate,
                updatedAt = System.currentTimeMillis()
            )
            inventoryDao.insertOrUpdateItem(updatedItem)

            val additionLog = StockAdditionEntity(
                id = "add_${UUID.randomUUID()}",
                inventoryItemId = invItem.id,
                itemName = invItem.name,
                quantityAdded = quantity,
                notes = batchNotes,
                timestamp = targetTimestamp,
                updatedAt = System.currentTimeMillis()
            )
            inventoryDao.insertStockAddition(additionLog)

            val stockTxn = StockTransactionEntity(
                id = "st_${UUID.randomUUID()}",
                restaurantId = invItem.restaurantId,
                inventoryItemId = invItem.id,
                itemName = invItem.name,
                transactionType = if (batchType == "OPENING_STOCK") "OPENING_STOCK" else "ADD",
                quantity = quantity,
                unitRate = rate,
                balanceAfter = newCurrentStock,
                batchId = batchId,
                referenceId = batch.referenceTransactionId,
                notes = batchNotes,
                timestamp = targetTimestamp
            )
            stockTransactionDao.insert(stockTxn)

            scope.launch {
                try {
                    syncManager.uploadBatch(currentUserId.value, batch)
                    syncManager.uploadInventoryItem(currentUserId.value, updatedItem)
                    syncManager.uploadStockTransaction(currentUserId.value, stockTxn)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Stock addition sync deferred", e)
                }
            }
        }
    }

    /**
     * Records an expense and creates a corresponding FIFO Inventory Purchase batch.
     * Guaranteed atomic: adds batch, updates stock & FIFO rates, logs expense and updates cash register.
     */
    suspend fun recordExpenseWithInventoryPurchase(
        restaurantId: String,
        categoryId: String,
        categoryName: String,
        amount: Double,
        paymentMethod: String,
        description: String,
        inventoryItemId: String,
        quantity: Double,
        ratePerUnit: Double? = null,
        timestamp: Long = System.currentTimeMillis()
    ): ExpenseEntity = withContext(Dispatchers.IO) {
        val rate = ratePerUnit ?: if (quantity > 0.0) amount / quantity else 0.0

        // 1. Create stock batch for the inventory item (FIFO)
        addInventoryStockBatch(
            itemId = inventoryItemId,
            quantity = quantity,
            purchaseRate = rate,
            notes = if (description.isNotBlank()) "Expense: $description" else "Inventory Purchase Expense",
            batchType = "PURCHASE"
        )

        // 2. Create Expense entity
        val exp = ExpenseEntity(
            id = "exp_${UUID.randomUUID()}",
            restaurantId = restaurantId,
            categoryId = categoryId,
            categoryName = categoryName,
            amount = amount,
            paymentMethod = paymentMethod,
            description = description,
            timestamp = timestamp,
            updatedAt = timestamp
        )
        expenseDao.insertOrUpdate(exp)

        // 3. Update cash register if CASH
        if (paymentMethod == "CASH") {
            val dateStr = com.example.util.DateUtils.formatDate(timestamp, "yyyy-MM-dd")
            val reg = cashRegisterDao.getRegisterForDate(restaurantId, dateStr)
            if (reg != null) {
                val updated = reg.copy(
                    cashExpenses = reg.cashExpenses + amount,
                    updatedAt = System.currentTimeMillis()
                )
                cashRegisterDao.insertOrUpdate(updated)
                syncManager.uploadCashRegister(currentUserId.value, updated)
            }
        }

        syncManager.uploadExpense(currentUserId.value, exp)
        exp
    }

    /**
     * Migrates legacy opening stock into batches for items with positive stock and zero existing batches.
     * Fully idempotent and non-destructive.
     */
    suspend fun migrateLegacyStockIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val allItems = inventoryDao.getAllInventoryDirect()
            for (item in allItems) {
                if (item.currentStock > 0.000001) {
                    val batches = inventoryBatchDao.getBatchesForItemDirect(item.id)
                    if (batches.isEmpty()) {
                        val batchId = "batch_migrated_${item.id}"
                        val rate = if (item.purchasePrice > 0.0) item.purchasePrice else 0.0
                        val batch = InventoryBatchEntity(
                            id = batchId,
                            restaurantId = item.restaurantId,
                            inventoryItemId = item.id,
                            itemName = item.name,
                            initialQuantity = item.currentStock,
                            remainingQuantity = item.currentStock,
                            purchaseRate = rate,
                            unit = item.unit,
                            batchType = "OPENING_STOCK",
                            notes = "Migrated from existing opening stock",
                            timestamp = item.updatedAt,
                            isConsumed = false,
                            updatedAt = item.updatedAt,
                            createdAt = item.updatedAt,
                            createdByUserId = currentUserId.value,
                            referenceTransactionId = "MIGRATE-${item.id}",
                            status = "ACTIVE"
                        )
                        inventoryBatchDao.insertBatch(batch)
                        stockTransactionDao.insert(
                            StockTransactionEntity(
                                id = "txn_migrated_${item.id}",
                                restaurantId = item.restaurantId,
                                inventoryItemId = item.id,
                                itemName = item.name,
                                transactionType = "OPENING_STOCK",
                                quantity = item.currentStock,
                                unitRate = rate,
                                balanceAfter = item.currentStock,
                                batchId = batchId,
                                referenceId = "MIGRATE-${item.id}",
                                notes = "Migrated legacy stock",
                                timestamp = item.updatedAt
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PosRepository", "Error migrating legacy stock", e)
        }
    }

    /**
     * Calculates total dish-level food cost for bill items based on linked recipes.
     */
    suspend fun calculateItemsFoodCost(items: List<BillItem>, orderType: String): Double = withContext(Dispatchers.IO) {
        try {
            val isTakeaway = orderType == "TAKEAWAY"
            val allMenu = menuItemDao.getAllMenuItemsDirect()
            val allRecipes = recipeDao.getAllRecipesDirect().associateBy { it.menuItemId }
            val inventoryMap = inventoryDao.getAllInventoryDirect().associateBy { it.id }

            var totalFoodCost = 0.0

            for (item in items) {
                val qty = item.quantity
                if (item.dishId.startsWith("combo_")) {
                    val notes = item.notes
                    if (notes.contains("[DISHES:")) {
                        val encodedPart = notes.substringAfter("[DISHES:").substringBefore("]")
                        val pairs = encodedPart.split(";").filter { it.isNotBlank() }
                        var comboUnitCost = 0.0
                        for (pair in pairs) {
                            val parts = pair.split("=")
                            val dishId = parts[0]
                            val subQty = parts.getOrNull(1)?.toIntOrNull() ?: 1
                            val recipe = allRecipes[dishId]
                            if (recipe != null) {
                                val summary = FoodCostCalculator.calculateRecipeCost(recipe, inventoryMap)
                                val dishUnitCost = if (isTakeaway) summary.takeawayTotalCost else summary.dineInCost
                                comboUnitCost += dishUnitCost * subQty
                            }
                        }
                        totalFoodCost += comboUnitCost * qty
                    } else {
                        val cleanedNotes = notes.substringBefore("[DISHES:").trim()
                        val subNames = cleanedNotes.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val summary = FoodCostCalculator.calculateComboCost(
                            subItemNames = subNames,
                            menuItems = allMenu,
                            recipesMap = allRecipes,
                            inventoryMap = inventoryMap
                        )
                        val unitCost = if (isTakeaway) summary.takeawayTotalCost else summary.dineInCost
                        totalFoodCost += unitCost * qty
                    }
                } else {
                    val recipe = allRecipes[item.dishId]
                        ?: allRecipes.values.find { it.menuItemName.equals(item.dishName, ignoreCase = true) }
                    if (recipe != null) {
                        val summary = FoodCostCalculator.calculateRecipeCost(recipe, inventoryMap)
                        val unitCost = if (isTakeaway) summary.takeawayTotalCost else summary.dineInCost
                        totalFoodCost += unitCost * qty
                    }
                }
            }
            totalFoodCost
        } catch (e: Exception) {
            Log.e("PosRepository", "Error calculating food cost", e)
            0.0
        }
    }

    // Recipe Operations
    fun getAllRecipes(): Flow<List<RecipeEntity>> = recipeDao.getAllRecipes()

    suspend fun getRecipeForMenuItem(menuItemId: String): RecipeEntity? = withContext(Dispatchers.IO) {
        recipeDao.getRecipeForMenuItemDirect(menuItemId)
    }

    suspend fun saveRecipe(recipe: RecipeEntity) = withContext(Dispatchers.IO) {
        recipeDao.insertOrUpdate(recipe)
        scope.launch {
            try {
                syncManager.uploadRecipe(currentUserId.value, recipe)
            } catch (e: Exception) {
                Log.w("PosRepository", "Recipe sync deferred", e)
            }
        }
    }

    suspend fun deleteRecipe(menuItemId: String) = withContext(Dispatchers.IO) {
        recipeDao.deleteRecipeByMenuItemId(menuItemId)
        scope.launch {
            try {
                syncManager.deleteRecipe(currentUserId.value, menuItemId)
            } catch (e: Exception) {
                Log.w("PosRepository", "Recipe delete sync deferred", e)
            }
        }
    }

    // Inventory Item Operations (Add / Edit / Delete)
    suspend fun saveInventoryItem(item: InventoryItemEntity) = withContext(Dispatchers.IO) {
        inventoryDao.insertOrUpdateItem(item)
        scope.launch {
            try {
                syncManager.uploadInventoryItem(currentUserId.value, item)
            } catch (e: Exception) {
                Log.w("PosRepository", "Inventory item sync deferred", e)
            }
        }
    }

    suspend fun deleteInventoryItem(id: String) = withContext(Dispatchers.IO) {
        inventoryDao.deleteItemById(id)
        scope.launch {
            try {
                syncManager.deleteInventoryItem(currentUserId.value, id)
            } catch (e: Exception) {
                Log.w("PosRepository", "Inventory item delete sync deferred", e)
            }
        }
    }

    // Add-on Definition Operations
    suspend fun saveAddonDefinition(addon: AddonDefinitionEntity) = withContext(Dispatchers.IO) {
        addonDao.insertOrUpdate(addon)
        scope.launch {
            try {
                syncManager.uploadAddon(currentUserId.value, addon)
            } catch (e: Exception) {
                Log.w("PosRepository", "Addon upload deferred", e)
            }
        }
    }

    suspend fun deactivateAddonDefinition(addonId: String) = withContext(Dispatchers.IO) {
        val existing = addonDao.getAddonById(addonId) ?: return@withContext
        val updated = existing.copy(isActive = false, updatedAt = System.currentTimeMillis())
        addonDao.insertOrUpdate(updated)
        scope.launch {
            try {
                syncManager.uploadAddon(currentUserId.value, updated)
            } catch (e: Exception) {
                Log.w("PosRepository", "Addon deactivate deferred", e)
            }
        }
    }

    suspend fun deleteAddonDefinition(addonId: String) = withContext(Dispatchers.IO) {
        addonDao.deleteById(addonId)
        scope.launch {
            try {
                syncManager.deleteAddon(currentUserId.value, addonId)
            } catch (e: Exception) {
                Log.w("PosRepository", "Addon delete deferred", e)
            }
        }
    }

    suspend fun updateCategoryDefaultAddons(categoryId: String, defaultAddonIds: List<String>) = withContext(Dispatchers.IO) {
        val cat = categoryDao.getCategoryById(categoryId) ?: return@withContext
        val updated = cat.copy(
            defaultAddonIdsJson = formatAddonIds(defaultAddonIds),
            updatedAt = System.currentTimeMillis()
        )
        categoryDao.insertOrUpdate(updated)
        scope.launch {
            try {
                syncManager.uploadCategory(currentUserId.value, updated)
            } catch (e: Exception) {
                Log.w("PosRepository", "Category default addons sync deferred", e)
            }
        }
    }

    suspend fun updateMenuItemAddonOverrides(menuItemId: String, excludedAddonIds: List<String>, itemAddonIds: List<String>) = withContext(Dispatchers.IO) {
        val item = menuItemDao.getMenuItemById(menuItemId) ?: return@withContext
        val updated = item.copy(
            excludedAddonIdsJson = formatAddonIds(excludedAddonIds),
            itemAddonIdsJson = formatAddonIds(itemAddonIds),
            updatedAt = System.currentTimeMillis()
        )
        menuItemDao.insertOrUpdate(updated)
        scope.launch {
            try {
                syncManager.uploadMenuItem(currentUserId.value, updated)
            } catch (e: Exception) {
                Log.w("PosRepository", "MenuItem addon overrides sync deferred", e)
            }
        }
    }

    suspend fun getEffectiveAddonDefinitionsForMenuItem(menuItemId: String): List<AddonDefinitionEntity> = withContext(Dispatchers.IO) {
        val item = menuItemDao.getMenuItemById(menuItemId) ?: return@withContext emptyList()
        val category = categoryDao.getCategoryById(item.categoryId)
        val categoryDefaultAddonIds = parseAddonIds(category?.defaultAddonIdsJson)
        val excludedAddonIds = parseAddonIds(item.excludedAddonIdsJson).toSet()
        val itemAddonIds = parseAddonIds(item.itemAddonIdsJson)

        val effectiveIds = (categoryDefaultAddonIds.filter { it !in excludedAddonIds } + itemAddonIds).distinct()
        if (effectiveIds.isEmpty()) return@withContext emptyList()

        val allActiveAddons = addonDao.getActiveAddonsDirect().associateBy { it.id }
        effectiveIds.mapNotNull { allActiveAddons[it] }
    }

    /**
     * Generates A4 PDF invoice and returns the File.
     */
    fun generateInvoicePdf(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double = 0.0): File {
        return PdfInvoiceGenerator.generateA4Pdf(context, bill, restaurant, previousDue)
    }

    /**
     * Generates receipt image (PNG) based on restaurant's billFormat (Point 5 Elegant, A4, 2-inch, or 3-inch).
     */
    fun generateBillImage(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double = 0.0): File {
        return com.example.util.ReceiptBitmapGenerator.generateReceiptImageFile(context, bill, restaurant, previousDue)
    }

    /**
     * Daily Stock Count & Variance Report
     * Applies physical count to in-scope items.
     * Shortage reduces oldest batches first (FIFO), without changing rate.
     * Excess refills starting with most-recently-active batch up to original purchased quantity,
     * then spills backward into older batches without exceeding original amount or changing rate.
     * Main inventory current stock immediately reflects counted quantity.
     * Past sales COGS remain untouched.
     */
    suspend fun performStockCountAdjustment(
        countedQuantities: Map<String, Double>, // itemId -> actual count
        notes: String = ""
    ): Result<StockCountEntity> = withContext(Dispatchers.IO) {
        if (countedQuantities.isEmpty()) {
            return@withContext Result.failure(Exception("No items were counted"))
        }
        db.withTransaction {
            val countDate = System.currentTimeMillis()
            val countId = "sc_${UUID.randomUUID().toString().take(12)}"
            val currentRestId = activeRestaurant.firstOrNull()?.id ?: "default_rest"
            val countTitle = "Stock Count • ${java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(countDate))}"

            val countItems = mutableListOf<StockCountItem>()
            val batchesToUpdate = mutableListOf<InventoryBatchEntity>()
            val stockTransactions = mutableListOf<StockTransactionEntity>()
            var totalVarianceCost = 0.0

            for ((itemId, actualStock) in countedQuantities) {
                val invItem = inventoryDao.getItemById(itemId) ?: continue
                val allBatches = inventoryBatchDao.getBatchesForItemDirect(itemId)
                val expectedStock = if (allBatches.isNotEmpty()) {
                    allBatches.filter { it.status != "ARCHIVED" && it.remainingQuantity > 0.000001 }.sumOf { it.remainingQuantity }
                } else {
                    invItem.currentStock
                }

                val variance = actualStock - expectedStock
                val activeBatchesFifo = inventoryBatchDao.getActiveBatchesForItemFifo(itemId)
                val currentFifoRate = activeBatchesFifo.firstOrNull()?.purchaseRate
                    ?: if (invItem.purchasePrice > 0.0) invItem.purchasePrice else 0.0
                val varianceCost = kotlin.math.round(variance * currentFifoRate * 100.0) / 100.0
                totalVarianceCost += varianceCost

                val batchAdjustments = mutableListOf<BatchAdjustmentRecord>()

                if (variance < -0.000001) {
                    // Shortage: reduce quantity from the OLDEST existing batch(es) first (FIFO order), without changing any batch's rate
                    var remainingShortage = -variance
                    for (batch in activeBatchesFifo) {
                        if (remainingShortage <= 0.000001) break
                        val currentRem = batch.remainingQuantity
                        if (currentRem <= 0.000001) continue

                        val toTake = kotlin.math.min(remainingShortage, currentRem)
                        val newRem = currentRem - toTake
                        val isConsumed = newRem <= 0.000001
                        val updatedBatch = batch.copy(
                            remainingQuantity = if (isConsumed) 0.0 else newRem,
                            isConsumed = isConsumed,
                            status = if (isConsumed) "ARCHIVED" else "ACTIVE",
                            updatedAt = System.currentTimeMillis()
                        )
                        batchesToUpdate.add(updatedBatch)
                        batchAdjustments.add(
                            BatchAdjustmentRecord(
                                batchId = batch.id,
                                initialQuantity = batch.initialQuantity,
                                previousRemainingQty = currentRem,
                                newRemainingQty = updatedBatch.remainingQuantity,
                                adjustedQty = -toTake,
                                unitRate = batch.purchaseRate,
                                batchNotes = "Shortage deduction (${batch.batchType})"
                            )
                        )
                        remainingShortage -= toTake
                    }
                } else if (variance > 0.000001) {
                    // Excess: distribute the excess quantity starting with the most-recently-active (currently being consumed) batch
                    // refilling it up to — but never exceeding — its own ORIGINAL purchased quantity, at that batch's existing rate.
                    // If excess remains, move to the next-older batch and repeat backward through progressively older batches.
                    // Do NOT create any new batch and do NOT change any batch's rate.
                    var remainingExcess = variance

                    val chronologicalBatches = allBatches.sortedWith(
                        compareBy({ it.timestamp }, { it.createdAt }, { it.id })
                    )

                    if (chronologicalBatches.isNotEmpty()) {
                        // Find most-recently-active (currently being consumed) batch in FIFO
                        var targetIdx = chronologicalBatches.indexOfFirst { it.remainingQuantity > 0.000001 }
                        if (targetIdx == -1) {
                            targetIdx = chronologicalBatches.lastIndex
                        }

                        val candidateIndices = mutableListOf<Int>()
                        for (i in targetIdx downTo 0) {
                            candidateIndices.add(i)
                        }
                        for (i in (targetIdx + 1)..chronologicalBatches.lastIndex) {
                            candidateIndices.add(i)
                        }

                        for (idx in candidateIndices) {
                            if (remainingExcess <= 0.000001) break
                            val b = chronologicalBatches[idx]
                            val maxRefill = kotlin.math.max(0.0, b.initialQuantity - b.remainingQuantity)
                            if (maxRefill > 0.000001) {
                                val refillAmount = kotlin.math.min(remainingExcess, maxRefill)
                                val newRem = b.remainingQuantity + refillAmount
                                val updatedBatch = b.copy(
                                    remainingQuantity = newRem,
                                    isConsumed = false,
                                    status = "ACTIVE",
                                    updatedAt = System.currentTimeMillis()
                                )
                                batchesToUpdate.add(updatedBatch)
                                batchAdjustments.add(
                                    BatchAdjustmentRecord(
                                        batchId = b.id,
                                        initialQuantity = b.initialQuantity,
                                        previousRemainingQty = b.remainingQuantity,
                                        newRemainingQty = newRem,
                                        adjustedQty = refillAmount,
                                        unitRate = b.purchaseRate,
                                        batchNotes = "Excess refill (capped at original ${b.initialQuantity} ${b.unit})"
                                    )
                                )
                                remainingExcess -= refillAmount
                            }
                        }
                    }
                }

                // Immediately reflect counted quantity in inventory item
                val allBatchesForItem = inventoryBatchDao.getBatchesForItemDirect(itemId).toMutableList()
                for (ub in batchesToUpdate.filter { it.inventoryItemId == itemId }) {
                    val existingIdx = allBatchesForItem.indexOfFirst { it.id == ub.id }
                    if (existingIdx != -1) {
                        allBatchesForItem[existingIdx] = ub
                    } else {
                        allBatchesForItem.add(ub)
                    }
                }
                val activeAfter = allBatchesForItem.filter { it.status != "ARCHIVED" && it.remainingQuantity > 0.000001 }
                    .sortedWith(compareBy({ it.timestamp }, { it.createdAt }, { it.id }))
                val newRate = activeAfter.firstOrNull()?.purchaseRate ?: invItem.purchasePrice

                inventoryDao.insertOrUpdateItem(
                    invItem.copy(
                        currentStock = actualStock,
                        purchasePrice = newRate,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // Audit ledger transaction
                stockTransactions.add(
                    StockTransactionEntity(
                        id = "st_${UUID.randomUUID()}",
                        restaurantId = invItem.restaurantId,
                        inventoryItemId = itemId,
                        itemName = invItem.name,
                        transactionType = when {
                            variance < -0.000001 -> "STOCK_COUNT_SHORTAGE"
                            variance > 0.000001 -> "STOCK_COUNT_EXCESS"
                            else -> "STOCK_COUNT_VERIFIED"
                        },
                        quantity = kotlin.math.abs(variance),
                        unitRate = currentFifoRate,
                        balanceAfter = actualStock,
                        referenceId = countId,
                        notes = "Stock Count: Expected $expectedStock ${invItem.unit}, Counted $actualStock ${invItem.unit}, Variance: ${if (variance >= 0) "+" else ""}$variance ${invItem.unit}",
                        timestamp = countDate
                    )
                )

                countItems.add(
                    StockCountItem(
                        inventoryItemId = itemId,
                        itemName = invItem.name,
                        unit = invItem.unit,
                        systemStock = expectedStock,
                        actualStock = actualStock,
                        varianceQty = variance,
                        unitRate = currentFifoRate,
                        varianceCost = varianceCost,
                        batchAdjustments = batchAdjustments
                    )
                )
            }

            if (batchesToUpdate.isNotEmpty()) {
                inventoryBatchDao.insertAllBatches(batchesToUpdate)
            }
            if (stockTransactions.isNotEmpty()) {
                stockTransactionDao.insertAll(stockTransactions)
            }

            val session = StockCountEntity(
                id = countId,
                restaurantId = currentRestId,
                countDate = countDate,
                title = countTitle,
                totalItemsChecked = countItems.size,
                totalVarianceCost = kotlin.math.round(totalVarianceCost * 100.0) / 100.0,
                isAdjusted = true,
                notes = notes,
                items = countItems,
                createdAt = countDate,
                updatedAt = countDate
            )
            stockCountDao.insertOrUpdate(session)

            scope.launch {
                try {
                    for (b in batchesToUpdate) {
                        syncManager.uploadBatch(currentUserId.value, b)
                    }
                    for (item in countItems) {
                        inventoryDao.getItemById(item.inventoryItemId)?.let {
                            syncManager.uploadInventoryItem(currentUserId.value, it)
                        }
                    }
                    for (st in stockTransactions) {
                        syncManager.uploadStockTransaction(currentUserId.value, st)
                    }
                    syncManager.uploadStockCount(currentUserId.value, session)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Stock count sync deferred: ${e.message}")
                }
            }

            Result.success(session)
        }
    }

    /**
     * Customer & Credit Management
     */
    fun getAllCustomers(): Flow<List<CustomerEntity>> = customerDao.getAllCustomers()

    suspend fun saveCustomer(customer: CustomerEntity) = withContext(Dispatchers.IO) {
        val isNewCustomer = customerDao.getCustomerById(customer.id) == null &&
                (customer.contactNumber.isBlank() || customerDao.getCustomerByContact(customer.contactNumber) == null)
        customerDao.insertOrUpdate(customer)
        if (isNewCustomer && _pointsEngineRules.value.welcomeBonusEnabled && _pointsEngineRules.value.welcomeBonusPoints > 0) {
            addGiftPoints(
                customerId = customer.id,
                points = _pointsEngineRules.value.welcomeBonusPoints,
                notes = "Welcome Gift Points for joining BBC Food Hub!",
                expiryDays = _pointsEngineRules.value.giftPointsExpiryDays
            )
        }
        scope.launch {
            try {
                syncManager.uploadCustomer(currentUserId.value, customer)
            } catch (e: Exception) {
                Log.w("PosRepository", "Customer sync deferred", e)
            }
        }
    }

    suspend fun setCustomerCreditStatus(customerId: String, isCreditCustomer: Boolean) = withContext(Dispatchers.IO) {
        val existing = customerDao.getCustomerById(customerId)
        if (existing != null) {
            val updated = existing.copy(isCreditCustomer = isCreditCustomer, updatedAt = System.currentTimeMillis())
            customerDao.insertOrUpdate(updated)
            scope.launch {
                try {
                    syncManager.uploadCustomer(currentUserId.value, updated)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Customer sync deferred", e)
                }
            }
        }
    }

    suspend fun recordCustomerPayment(
        customerId: String,
        customerName: String,
        customerPhone: String,
        amount: Double,
        paymentMode: String, // "CASH" or "UPI"
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): CustomerPaymentEntity = withContext(Dispatchers.IO) {
        val paymentId = "PAY-${System.currentTimeMillis().toString().takeLast(6)}"
        val payment = CustomerPaymentEntity(
            id = paymentId,
            customerId = customerId,
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim(),
            amount = amount,
            paymentMode = paymentMode,
            notes = notes.trim(),
            timestamp = timestamp,
            createdAt = System.currentTimeMillis()
        )
        customerPaymentDao.insert(payment)

        // Update daily cash register if paymentMode == "CASH" and payment is for today
        if (paymentMode == "CASH") {
            val rest = restaurantDao.getActiveRestaurantDirect() ?: restaurantDao.getAllRestaurantsDirect().firstOrNull()
            if (rest != null) {
                val dateStr = DateUtils.formatDate(timestamp, "yyyy-MM-dd")
                val existingReg = cashRegisterDao.getRegisterForDate(rest.id, dateStr)
                if (existingReg != null) {
                    val updatedReg = existingReg.copy(
                        cashSales = existingReg.cashSales + amount,
                        updatedAt = System.currentTimeMillis()
                    )
                    cashRegisterDao.insertOrUpdate(updatedReg)
                    scope.launch {
                        syncManager.uploadCashRegister(currentUserId.value, updatedReg)
                    }
                }
            }
        }

        scope.launch {
            try {
                syncManager.uploadCustomerPayment(currentUserId.value, payment)
            } catch (e: Exception) {
                Log.w("PosRepository", "Payment sync deferred", e)
            }
        }
        payment
    }

    /**
     * Manual Trigger for Cloud Sync
     */
    suspend fun syncAllNow(): Result<String> {
        return syncManager.syncAllToCloud(currentUserId.value, db)
    }

    /**
     * Manual Trigger for Cloud Restore
     * Restores all data from pos_users/{userId}/... into Room DB, refreshing live counts.
     */
    suspend fun restoreAllNow(): Result<String> {
        val res = syncManager.restoreFromCloud(currentUserId.value, db)
        if (res.isSuccess) {
            try {
                val cloudRules = syncManager.downloadPointsEngineRules(currentUserId.value)
                if (cloudRules != null) {
                    updatePointsEngineRules(cloudRules)
                }
            } catch (e: Exception) {
                Log.w("PosRepository", "Error restoring points rules from cloud", e)
            }
        }
        return res
    }

    fun startRealtimeSync() {
        syncManager.startRealtimeSync(currentUserId.value, db)
    }

    fun stopRealtimeSync() {
        syncManager.stopRealtimeSync()
    }

    // ==========================================
    // OFFERS & LOYALTY METHODS
    // ==========================================

    data class CustomerPointsSummary(
        val usableRewardPoints: Int,
        val usableGiftPoints: Int,
        val totalUsablePoints: Int
    )

    fun parseLoyaltyHistory(json: String?): List<LoyaltyHistoryItem> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<LoyaltyHistoryItem>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LoyaltyHistoryItem(
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
            Log.w("PosRepository", "Error parsing loyalty history", e)
        }
        return list
    }

    fun formatLoyaltyHistory(list: List<LoyaltyHistoryItem>): String {
        val array = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject()
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

    fun getOffersByRestaurant(restaurantId: String): Flow<List<OfferEntity>> =
        offerDao.getOffersByRestaurant(restaurantId)

    fun getActiveOffersByRestaurant(restaurantId: String): Flow<List<OfferEntity>> =
        offerDao.getActiveOffersByRestaurant(restaurantId)

    suspend fun saveOffer(offer: OfferEntity) = withContext(Dispatchers.IO) {
        offerDao.insertOrUpdate(offer)
        scope.launch {
            try {
                syncManager.uploadOffer(currentUserId.value, offer)
            } catch (e: Exception) {
                Log.w("PosRepository", "Offer sync deferred", e)
            }
        }
    }

    suspend fun deleteOffer(offerId: String) = withContext(Dispatchers.IO) {
        offerDao.deleteById(offerId)
        scope.launch {
            try {
                syncManager.deleteOffer(currentUserId.value, offerId)
            } catch (e: Exception) {
                Log.w("PosRepository", "Delete offer sync deferred", e)
            }
        }
    }

    suspend fun toggleOfferActive(offerId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        val existing = offerDao.getOfferById(offerId) ?: return@withContext
        val updated = existing.copy(isActive = isActive, updatedAt = System.currentTimeMillis())
        saveOffer(updated)
    }

    suspend fun saveVisitProgram(restaurantId: String, totalVisits: Int, offers: List<OfferEntity>) = withContext(Dispatchers.IO) {
        for (offer in offers) {
            val updated = offer.copy(
                restaurantId = restaurantId,
                totalVisitsInProgram = totalVisits,
                updatedAt = System.currentTimeMillis()
            )
            offerDao.insertOrUpdate(updated)
            scope.launch {
                try {
                    syncManager.uploadOffer(currentUserId.value, updated)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Offer sync deferred", e)
                }
            }
        }
    }

    suspend fun getCustomerPointsSummary(customerId: String): CustomerPointsSummary = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val customer = customerDao.getCustomerById(customerId)
            ?: if (customerId.startsWith("cust_")) customerDao.getCustomerByContact(customerId.removePrefix("cust_")) else null
            ?: customerDao.getCustomerByContact(customerId)
            ?: customerDao.getAllCustomersDirect().firstOrNull { it.id == customerId || it.contactNumber == customerId }
        
        val usableReward = maxOf(0, customer?.rewardPointsBalance ?: 0)
        val usableGift = maxOf(0, customer?.giftPointsBalance ?: 0)
        
        CustomerPointsSummary(
            usableRewardPoints = usableReward,
            usableGiftPoints = usableGift,
            totalUsablePoints = usableReward + usableGift
        )
    }

    suspend fun resetCustomerLoyalty(customerId: String) = withContext(Dispatchers.IO) {
        val cust = customerDao.getCustomerById(customerId) ?: return@withContext
        val history = parseLoyaltyHistory(cust.loyaltyHistoryJson).toMutableList()
        history.add(
            LoyaltyHistoryItem(
                eventType = "CYCLE_RESET",
                visitNumber = cust.loyaltyVisitCount,
                notes = "Loyalty cycle manually reset by staff",
                timestamp = System.currentTimeMillis()
            )
        )
        val updated = cust.copy(
            loyaltyVisitCount = 0,
            loyaltyStartDate = null,
            loyaltyExpiryDate = null,
            loyaltyHistoryJson = formatLoyaltyHistory(history),
            updatedAt = System.currentTimeMillis()
        )
        customerDao.insertOrUpdate(updated)
        scope.launch {
            try {
                syncManager.uploadCustomer(currentUserId.value, updated)
            } catch (e: Exception) {
                Log.w("PosRepository", "Customer reset sync deferred", e)
            }
        }
    }

    suspend fun extendCustomerLoyalty(customerId: String, daysToAdd: Int = 45) = withContext(Dispatchers.IO) {
        val cust = customerDao.getCustomerById(customerId) ?: return@withContext
        val now = System.currentTimeMillis()
        val baseTime = maxOf(now, cust.loyaltyExpiryDate ?: now)
        val newExpiry = baseTime + daysToAdd * 24L * 60 * 60 * 1000L
        val history = parseLoyaltyHistory(cust.loyaltyHistoryJson).toMutableList()
        history.add(
            LoyaltyHistoryItem(
                eventType = "CYCLE_EXTENDED",
                visitNumber = cust.loyaltyVisitCount,
                notes = "Loyalty cycle validity extended by $daysToAdd days",
                timestamp = now
            )
        )
        val updated = cust.copy(
            loyaltyExpiryDate = newExpiry,
            loyaltyHistoryJson = formatLoyaltyHistory(history),
            updatedAt = now
        )
        customerDao.insertOrUpdate(updated)
        scope.launch {
            try {
                syncManager.uploadCustomer(currentUserId.value, updated)
            } catch (e: Exception) {
                Log.w("PosRepository", "Customer extend sync deferred", e)
            }
        }
    }

    suspend fun addGiftPoints(customerId: String, points: Int, notes: String = "", expiryDays: Int = 60) = withContext(Dispatchers.IO) {
        if (points <= 0) return@withContext
        val cust = customerDao.getCustomerById(customerId)
            ?: (if (customerId.startsWith("cust_")) customerDao.getCustomerByContact(customerId.removePrefix("cust_")) else null)
            ?: customerDao.getCustomerByContact(customerId)
            ?: customerDao.getAllCustomersDirect().firstOrNull { it.id == customerId || it.contactNumber == customerId }
            ?: return@withContext
        val now = System.currentTimeMillis()
        val isLifetime = expiryDays <= 0
        val expiry = if (isLifetime) now + 36500L * 24 * 60 * 60 * 1000L else now + expiryDays.toLong() * 24 * 60 * 60 * 1000L
        val batch = PointsBatchEntity(
            id = UUID.randomUUID().toString(),
            customerId = cust.id,
            customerPhone = cust.contactNumber,
            customerName = cust.name,
            batchType = "GIFT",
            initialPoints = points,
            remainingPoints = points,
            earnDate = now,
            expiryDate = expiry,
            notes = notes.ifBlank { if (isLifetime) "Gift points (No Expiry)" else "Gift points ($expiryDays days validity)" },
            createdAt = now,
            updatedAt = now
        )
        pointsBatchDao.insertOrUpdate(batch)

        val newGiftBal = cust.giftPointsBalance + points
        val ledger = PointsLedgerEntity(
            id = UUID.randomUUID().toString(),
            customerId = cust.id,
            customerPhone = cust.contactNumber,
            customerName = cust.name,
            transactionType = "GIFTED",
            pointsAmount = points,
            balanceType = "GIFT",
            expiryDate = expiry,
            rewardPointsBalanceAfter = cust.rewardPointsBalance,
            giftPointsBalanceAfter = newGiftBal,
            notes = notes.ifBlank { if (isLifetime) "Gift points awarded (No Expiry)" else "Gift points awarded ($expiryDays days validity)" },
            timestamp = now,
            createdAt = now
        )
        pointsLedgerDao.insert(ledger)

        val updatedCust = cust.copy(
            giftPointsBalance = newGiftBal,
            updatedAt = now
        )
        customerDao.insertOrUpdate(updatedCust)

        scope.launch {
            try {
                syncManager.uploadPointsBatch(currentUserId.value, batch)
                syncManager.uploadPointsLedger(currentUserId.value, ledger)
                syncManager.uploadCustomer(currentUserId.value, updatedCust)
            } catch (e: Exception) {
                Log.w("PosRepository", "Gift points sync deferred", e)
            }
        }
    }

    suspend fun toggleCustomerLoyaltyEnrollment(customerId: String, isEnrolled: Boolean) = withContext(Dispatchers.IO) {
        val cust = customerDao.getCustomerById(customerId) ?: return@withContext
        val updated = cust.copy(
            isEnrolledInLoyalty = isEnrolled,
            updatedAt = System.currentTimeMillis()
        )
        customerDao.insertOrUpdate(updated)
        scope.launch {
            try {
                syncManager.uploadCustomer(currentUserId.value, updated)
            } catch (e: Exception) {
                Log.w("PosRepository", "Customer loyalty toggle sync deferred", e)
            }
        }
    }

    suspend fun reEnableCustomerLoyalty(customerId: String) = withContext(Dispatchers.IO) {
        val cust = customerDao.getCustomerById(customerId) ?: return@withContext
        val now = System.currentTimeMillis()
        val updated = cust.copy(
            isEnrolledInLoyalty = true,
            loyaltyVisitCount = 0,
            loyaltyStartDate = now,
            loyaltyExpiryDate = now + 45L * 24 * 60 * 60 * 1000L,
            updatedAt = now
        )
        customerDao.insertOrUpdate(updated)
        scope.launch {
            try {
                syncManager.uploadCustomer(currentUserId.value, updated)
            } catch (e: Exception) {
                Log.w("PosRepository", "Re-enable customer loyalty sync deferred", e)
            }
        }
    }

    suspend fun bulkUpdateLoyaltyEnrollment(customerIds: List<String>, isEnrolled: Boolean) = withContext(Dispatchers.IO) {
        if (customerIds.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        for (id in customerIds) {
            val cust = customerDao.getCustomerById(id) ?: continue
            val updated = cust.copy(isEnrolledInLoyalty = isEnrolled, updatedAt = now)
            customerDao.insertOrUpdate(updated)
            scope.launch {
                try {
                    syncManager.uploadCustomer(currentUserId.value, updated)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Bulk loyalty enrollment sync deferred", e)
                }
            }
        }
    }

    suspend fun setZeroVisitCustomersLoyaltyEnrollment(isEnrolled: Boolean) = withContext(Dispatchers.IO) {
        val allCusts = customerDao.getAllCustomersDirect()
        val targetCusts = allCusts.filter { it.loyaltyVisitCount == 0 }
        val now = System.currentTimeMillis()
        for (cust in targetCusts) {
            if (cust.isEnrolledInLoyalty != isEnrolled) {
                val updated = cust.copy(isEnrolledInLoyalty = isEnrolled, updatedAt = now)
                customerDao.insertOrUpdate(updated)
                scope.launch {
                    try {
                        syncManager.uploadCustomer(currentUserId.value, updated)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Zero-visit loyalty sync deferred", e)
                    }
                }
            }
        }
    }

    suspend fun setAllCustomersLoyaltyEnrollment(isEnrolled: Boolean) = withContext(Dispatchers.IO) {
        val allCusts = customerDao.getAllCustomersDirect()
        val now = System.currentTimeMillis()
        for (cust in allCusts) {
            if (cust.isEnrolledInLoyalty != isEnrolled) {
                val updated = cust.copy(isEnrolledInLoyalty = isEnrolled, updatedAt = now)
                customerDao.insertOrUpdate(updated)
                scope.launch {
                    try {
                        syncManager.uploadCustomer(currentUserId.value, updated)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "All customers loyalty sync deferred", e)
                    }
                }
            }
        }
    }

    suspend fun addBulkGiftPoints(customerIds: List<String>, points: Int, notes: String = "") = withContext(Dispatchers.IO) {
        for (cid in customerIds) {
            addGiftPoints(cid, points, notes)
        }
    }

    fun getExpiringBatches(daysAhead: Int = 7): Flow<List<PointsBatchEntity>> {
        val now = System.currentTimeMillis()
        val threshold = now + daysAhead * 24L * 60 * 60 * 1000L
        return pointsBatchDao.getExpiringBatches(now, threshold)
    }

    suspend fun markPointsReminderSent(batchId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        pointsBatchDao.markReminderSent(batchId, now)
    }

    suspend fun getOutstandingPointsLiability(): Double = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val active = pointsBatchDao.getAllActiveBatchesList(now)
        active.sumOf { it.remainingPoints }.toDouble()
    }

    suspend fun reverseSettledBillLoyaltyAndPoints(billId: String, reason: String = "Bill voided") = withContext(Dispatchers.IO) {
        db.withTransaction {
            val bill = billDao.getBillById(billId) ?: return@withTransaction
            if (bill.isVoided) return@withTransaction

            val now = System.currentTimeMillis()
            var cust = if (bill.customerPhone.isNotBlank()) {
                customerDao.getCustomerByContact(bill.customerPhone.trim())
            } else null

            // 1. Reverse earned Reward Points
            if (bill.rewardPointsEarned > 0 && cust != null) {
                val batches = pointsBatchDao.getBatchesByBillId(bill.id)
                for (b in batches) {
                    pointsBatchDao.updateRemainingPoints(b.id, 0, isExpired = true, now = now)
                    val updatedBatch = b.copy(remainingPoints = 0, isExpired = true, updatedAt = now)
                    scope.launch {
                        try {
                            syncManager.uploadPointsBatch(currentUserId.value, updatedBatch)
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Points batch reverse sync deferred", e)
                        }
                    }
                }
                val summary = getCustomerPointsSummary(cust.id)
                val ledger = PointsLedgerEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = cust.id,
                    customerPhone = cust.contactNumber,
                    customerName = cust.name,
                    transactionType = "REVERSED",
                    pointsAmount = -bill.rewardPointsEarned,
                    balanceType = "REWARD",
                    billId = bill.id,
                    billNumber = bill.billNumber,
                    rewardPointsBalanceAfter = summary.usableRewardPoints,
                    giftPointsBalanceAfter = summary.usableGiftPoints,
                    notes = "Reversed earned points for voided bill ${bill.billNumber}: $reason",
                    timestamp = now,
                    createdAt = now
                )
                pointsLedgerDao.insert(ledger)
                cust = cust.copy(rewardPointsBalance = summary.usableRewardPoints, updatedAt = now)
            }

            // 2. Restore redeemed points
            if (bill.pointsRedeemed > 0 && cust != null) {
                val restoreBatch = PointsBatchEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = cust.id,
                    customerPhone = cust.contactNumber,
                    customerName = cust.name,
                    batchType = if (bill.giftPointsRedeemed > 0 && bill.rewardPointsRedeemed == 0) "GIFT" else "REWARD",
                    initialPoints = bill.pointsRedeemed,
                    remainingPoints = bill.pointsRedeemed,
                    earnDate = now,
                    expiryDate = now + 60L * 24 * 60 * 60 * 1000L,
                    notes = "Restored points from voided bill ${bill.billNumber}",
                    createdAt = now,
                    updatedAt = now
                )
                pointsBatchDao.insertOrUpdate(restoreBatch)
                val summary = getCustomerPointsSummary(cust.id)
                val ledger = PointsLedgerEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = cust.id,
                    customerPhone = cust.contactNumber,
                    customerName = cust.name,
                    transactionType = "REVERSED",
                    pointsAmount = bill.pointsRedeemed,
                    balanceType = "COMBINED",
                    billId = bill.id,
                    billNumber = bill.billNumber,
                    rewardPointsBalanceAfter = summary.usableRewardPoints,
                    giftPointsBalanceAfter = summary.usableGiftPoints,
                    notes = "Restored redeemed points for voided bill ${bill.billNumber}",
                    timestamp = now,
                    createdAt = now
                )
                pointsLedgerDao.insert(ledger)
                cust = cust.copy(
                    rewardPointsBalance = summary.usableRewardPoints,
                    giftPointsBalance = summary.usableGiftPoints,
                    updatedAt = now
                )
            }

            // 3. Reverse Visit Count if Visit Reward was applied or visit was credited
            if (bill.appliedRewardType == "VISIT_REWARD" && cust != null && cust.loyaltyVisitCount > 0) {
                val history = parseLoyaltyHistory(cust.loyaltyHistoryJson).toMutableList()
                history.add(
                    LoyaltyHistoryItem(
                        eventType = "VISIT_REVERSED",
                        visitNumber = cust.loyaltyVisitCount - 1,
                        billId = bill.id,
                        billNumber = bill.billNumber,
                        notes = "Reversed visit count for voided bill ${bill.billNumber}",
                        timestamp = now
                    )
                )
                cust = cust.copy(
                    loyaltyVisitCount = maxOf(0, cust.loyaltyVisitCount - 1),
                    loyaltyHistoryJson = formatLoyaltyHistory(history),
                    updatedAt = now
                )
            }

            if (cust != null) {
                customerDao.insertOrUpdate(cust)
                scope.launch {
                    try {
                        syncManager.uploadCustomer(currentUserId.value, cust)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Customer reversal sync deferred", e)
                    }
                }
            }

            val voidedBill = bill.copy(isVoided = true, updatedAt = now)
            billDao.insertOrUpdate(voidedBill)
            scope.launch {
                try {
                    syncManager.uploadBill(currentUserId.value, voidedBill)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Voided bill sync deferred", e)
                }
            }
        }
    }

    private suspend fun processSettlementLoyalty(
        bill: BillEntity,
        pointsToRedeem: Int,
        appliedRewardType: String,
        appliedOfferId: String?,
        appliedOfferName: String,
        paymentMethod: String,
        enrollInPass: Boolean? = null
    ): BillEntity {
        // Requirement 2: Points are ONLY credited upon full settlement/completion of the order
        if (!bill.isSettled) {
            return bill
        }

        // Requirement 7: Idempotency check - if this bill was already processed for loyalty points, do not credit again!
        val existingLedgerEntries = pointsLedgerDao.getEntriesByBillId(bill.id)
        val alreadyCredited = existingLedgerEntries.any { it.transactionType == "EARNED" }
        if (alreadyCredited) {
            return bill
        }

        var rewardPtsRedeemed = 0
        var giftPtsRedeemed = 0
        val now = System.currentTimeMillis()

        // Robust customer phone and ID resolution
        val cleanPhone = bill.customerPhone.trim()
        val phoneDigits = cleanPhone.filter { it.isDigit() }
        val custId = if (phoneDigits.isNotBlank()) "cust_$phoneDigits" else "cust_${UUID.randomUUID()}"
        var existingCust = if (cleanPhone.isNotBlank()) {
            customerDao.getCustomerByContact(cleanPhone)
                ?: if (phoneDigits.length >= 10) customerDao.getCustomerByContact(phoneDigits.takeLast(10)) else null
                ?: customerDao.getCustomerById(custId)
                ?: customerDao.getAllCustomersDirect().firstOrNull {
                    val d = it.contactNumber.filter { c -> c.isDigit() }
                    d.isNotBlank() && (d == phoneDigits || (phoneDigits.length >= 10 && d.endsWith(phoneDigits.takeLast(10))))
                }
        } else if (bill.customerName.isNotBlank()) {
            customerDao.getAllCustomersDirect().firstOrNull {
                it.name.trim().equals(bill.customerName.trim(), ignoreCase = true)
            }
        } else null

        val targetCustomerId = existingCust?.id ?: custId

        // 1. Process FIFO points redemption if requested (Guaranteed Direct Balance Deduction)
        val currentRewardBal = existingCust?.rewardPointsBalance ?: 0
        val currentGiftBal = existingCust?.giftPointsBalance ?: 0

        if (pointsToRedeem > 0) {
            var remainingToDeduct = pointsToRedeem
            val deductReward = minOf(currentRewardBal, remainingToDeduct)
            remainingToDeduct -= deductReward
            val deductGift = minOf(currentGiftBal, remainingToDeduct)
            remainingToDeduct -= deductGift

            rewardPtsRedeemed = deductReward
            giftPtsRedeemed = deductGift

            // Also update / consume active FIFO batches
            val validBatches = pointsBatchDao.getValidBatchesForCustomer(targetCustomerId, now).toMutableList()
            if (validBatches.isEmpty() && cleanPhone.isNotBlank()) {
                val allBatches = pointsBatchDao.getAllActiveBatchesList(now)
                val byPhone = allBatches.filter {
                    val d = it.customerPhone.filter { c -> c.isDigit() }
                    d.isNotBlank() && (d == phoneDigits || (phoneDigits.length >= 10 && d.endsWith(phoneDigits.takeLast(10))))
                }
                validBatches.addAll(byPhone)
            }

            var batchNeeded = pointsToRedeem
            for (batch in validBatches) {
                if (batchNeeded <= 0) break
                val deduct = minOf(batch.remainingPoints, batchNeeded)
                val rem = batch.remainingPoints - deduct
                pointsBatchDao.updateRemainingPoints(batch.id, rem, isExpired = (rem == 0), now = now)
                val updatedBatch = batch.copy(remainingPoints = rem, isExpired = (rem == 0), updatedAt = now)
                scope.launch {
                    try {
                        syncManager.uploadPointsBatch(currentUserId.value, updatedBatch)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Points batch update sync deferred", e)
                    }
                }
                batchNeeded -= deduct
            }
        }

        // 2. Calculate earned points using dynamic rules (earnRatePercent, minBillAmountToEarn)
        val currentRules = _pointsEngineRules.value
        val netPayable = if (bill.totalAmount > 0.0) bill.totalAmount else (bill.subtotal - bill.discountAmount - pointsToRedeem).coerceAtLeast(0.0)
        val rewardPtsEarned = if (netPayable >= currentRules.minBillAmountToEarn && netPayable > 0.0 && cleanPhone.isNotBlank()) {
            (netPayable * (currentRules.earnRatePercent / 100.0)).toInt()
        } else 0

        // 3. Customer loyalty progression, history & persistent balance calculation
        if (bill.customerName.isNotBlank() || cleanPhone.isNotBlank()) {
            val isBrandNewCustomer = existingCust == null
            val cycleExpired = existingCust?.loyaltyExpiryDate != null && now > existingCust.loyaltyExpiryDate!!
            val currentVisits = if (cycleExpired) 0 else (existingCust?.loyaltyVisitCount ?: 0)

            val isEnrolled = enrollInPass ?: existingCust?.isEnrolledInLoyalty ?: currentRules.autoEnrollInVisitPass
            val isSameDayVisit = existingCust?.lastVisitTimestamp != null && DateUtils.isSameDay(now, existingCust.lastVisitTimestamp!!)

            val activeVisitOffers = offerDao.getAllOffersDirect().filter { it.isActive && it.offerType == "VISIT_BASED" }
            val maxVisitNumber = activeVisitOffers.maxOfOrNull { it.visitNumber } ?: 0

            val isVisitRewardClaimed = appliedRewardType == "VISIT_REWARD"
            val shouldAdvanceVisit = isEnrolled && (isVisitRewardClaimed || (!isSameDayVisit && netPayable > 0.0 && cleanPhone.isNotBlank()))
            val (newVisitCount, newStartDate, newExpiryDate) = if (shouldAdvanceVisit) {
                val nextCount = currentVisits + 1
                val startDate = if (currentVisits == 0 || existingCust?.loyaltyStartDate == null || cycleExpired) now else existingCust.loyaltyStartDate!!
                val expiry = if (currentVisits == 0 || existingCust?.loyaltyExpiryDate == null || cycleExpired) now + 45L * 24 * 60 * 60 * 1000L else existingCust.loyaltyExpiryDate!!
                Triple(nextCount, startDate, expiry)
            } else {
                Triple(currentVisits, existingCust?.loyaltyStartDate, existingCust?.loyaltyExpiryDate)
            }

            // Auto-disable loyalty if customer completes max visit offer or achieves max visits
            val finalIsEnrolled = if (maxVisitNumber > 0 && newVisitCount >= maxVisitNumber && (isVisitRewardClaimed || newVisitCount >= maxVisitNumber)) {
                false
            } else {
                isEnrolled
            }

            val history = parseLoyaltyHistory(existingCust?.loyaltyHistoryJson).toMutableList()
            if (shouldAdvanceVisit) {
                history.add(
                    LoyaltyHistoryItem(
                        eventType = "VISIT_COMPLETED",
                        visitNumber = newVisitCount,
                        billId = bill.id,
                        billNumber = bill.billNumber,
                        billAmount = netPayable,
                        offerName = appliedOfferName.takeIf { it.isNotBlank() },
                        rewardGiven = if (appliedRewardType == "VISIT_REWARD") appliedOfferName else null,
                        timestamp = now
                    )
                )
                if (isVisitRewardClaimed) {
                    scope.launch {
                        try {
                            syncManager.uploadClaimedReward(
                                userId = currentUserId.value,
                                customerPhone = cleanPhone,
                                visitNumber = newVisitCount,
                                offerId = appliedOfferId ?: "",
                                offerName = appliedOfferName,
                                billNumber = bill.billNumber
                            )
                        } catch (e: Exception) {
                            Log.w("PosRepository", "Claimed reward cloud sync deferred", e)
                        }
                    }
                }
            }

            // Requirement 3: Add exact earned amount and welcome bonus to customer's persistent balances
            val hasPriorWelcomeBonus = if (existingCust != null) {
                val ledgers = pointsLedgerDao.getEntriesByCustomerDirect(existingCust.id)
                ledgers.any { it.transactionType == "GIFT" && it.notes.contains("Welcome", ignoreCase = true) } ||
                (existingCust.loyaltyVisitCount > 0 && existingCust.giftPointsBalance > 0)
            } else false

            val shouldAwardWelcomeBonus = !hasPriorWelcomeBonus && currentRules.welcomeBonusEnabled && currentRules.welcomeBonusPoints > 0
            val welcomeBonusAwarded = if (shouldAwardWelcomeBonus) currentRules.welcomeBonusPoints else 0

            val finalRewardBal = maxOf(0, currentRewardBal - rewardPtsRedeemed + rewardPtsEarned)
            val finalGiftBal = maxOf(0, currentGiftBal - giftPtsRedeemed) + welcomeBonusAwarded

            // Requirement 4: Record Batch for FIFO expiry tracking if points earned
            if (rewardPtsEarned > 0) {
                val rewardExpiryMs = now + (currentRules.rewardPointsExpiryDays.toLong() * 24 * 60 * 60 * 1000L)
                val batch = PointsBatchEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = targetCustomerId,
                    customerPhone = cleanPhone,
                    customerName = bill.customerName.trim(),
                    batchType = "REWARD",
                    initialPoints = rewardPtsEarned,
                    remainingPoints = rewardPtsEarned,
                    earnDate = now,
                    expiryDate = rewardExpiryMs,
                    billId = bill.id,
                    billNumber = bill.billNumber,
                    notes = "Earned on bill ${bill.billNumber}",
                    createdAt = now,
                    updatedAt = now
                )
                pointsBatchDao.insertOrUpdate(batch)
                scope.launch {
                    try {
                        syncManager.uploadPointsBatch(currentUserId.value, batch)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Points batch sync deferred", e)
                    }
                }
            }

            // Record Welcome Bonus batch & ledger if awarded to new customer
            if (welcomeBonusAwarded > 0) {
                val welcomeExpiryMs = now + (currentRules.welcomeBonusExpiryDays.toLong() * 24 * 60 * 60 * 1000L)
                val welcomeBatch = PointsBatchEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = targetCustomerId,
                    customerPhone = cleanPhone,
                    customerName = bill.customerName.trim().ifBlank { "Customer" },
                    batchType = "GIFT",
                    initialPoints = welcomeBonusAwarded,
                    remainingPoints = welcomeBonusAwarded,
                    earnDate = now,
                    expiryDate = welcomeExpiryMs,
                    billId = bill.id,
                    billNumber = bill.billNumber,
                    notes = "Welcome Bonus (${currentRules.welcomeBonusExpiryDays}d validity)",
                    createdAt = now,
                    updatedAt = now
                )
                pointsBatchDao.insertOrUpdate(welcomeBatch)
                scope.launch {
                    try {
                        syncManager.uploadPointsBatch(currentUserId.value, welcomeBatch)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Welcome points batch sync deferred", e)
                    }
                }

                val welcomeLedger = PointsLedgerEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = targetCustomerId,
                    customerPhone = cleanPhone,
                    customerName = bill.customerName.trim().ifBlank { "Customer" },
                    transactionType = "GIFT",
                    pointsAmount = welcomeBonusAwarded,
                    balanceType = "GIFT",
                    billId = bill.id,
                    billNumber = bill.billNumber,
                    expiryDate = welcomeExpiryMs,
                    rewardPointsBalanceAfter = finalRewardBal,
                    giftPointsBalanceAfter = finalGiftBal,
                    notes = "Welcome Gift Points (${currentRules.welcomeBonusExpiryDays}d validity)",
                    timestamp = now,
                    createdAt = now
                )
                pointsLedgerDao.insert(welcomeLedger)
                scope.launch {
                    try {
                        syncManager.uploadPointsLedger(currentUserId.value, welcomeLedger)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Welcome points ledger sync deferred", e)
                    }
                }
            }

            // Record redemption ledger if points redeemed
            if (pointsToRedeem > 0) {
                val redeemLedger = PointsLedgerEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = targetCustomerId,
                    customerPhone = cleanPhone,
                    customerName = bill.customerName.trim(),
                    transactionType = "REDEEMED",
                    pointsAmount = -pointsToRedeem,
                    balanceType = if (rewardPtsRedeemed > 0 && giftPtsRedeemed == 0) "REWARD" else if (giftPtsRedeemed > 0 && rewardPtsRedeemed == 0) "GIFT" else "COMBINED",
                    billId = bill.id,
                    billNumber = bill.billNumber,
                    rewardPointsBalanceAfter = finalRewardBal - rewardPtsEarned,
                    giftPointsBalanceAfter = finalGiftBal,
                    notes = "Redeemed for bill ${bill.billNumber}",
                    timestamp = now,
                    createdAt = now
                )
                pointsLedgerDao.insert(redeemLedger)
                scope.launch {
                    try {
                        syncManager.uploadPointsLedger(currentUserId.value, redeemLedger)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Points ledger sync deferred", e)
                    }
                }
            }

            // Record earned ledger if points earned
            if (rewardPtsEarned > 0) {
                val rewardExpiryMs = now + (currentRules.rewardPointsExpiryDays.toLong() * 24 * 60 * 60 * 1000L)
                val earnedLedger = PointsLedgerEntity(
                    id = UUID.randomUUID().toString(),
                    customerId = targetCustomerId,
                    customerPhone = cleanPhone,
                    customerName = bill.customerName.trim(),
                    transactionType = "EARNED",
                    pointsAmount = rewardPtsEarned,
                    balanceType = "REWARD",
                    billId = bill.id,
                    billNumber = bill.billNumber,
                    expiryDate = rewardExpiryMs,
                    rewardPointsBalanceAfter = finalRewardBal,
                    giftPointsBalanceAfter = finalGiftBal,
                    notes = "Earned ${currentRules.earnRatePercent}% on bill ${bill.billNumber}",
                    timestamp = now,
                    createdAt = now
                )
                pointsLedgerDao.insert(earnedLedger)
                scope.launch {
                    try {
                        syncManager.uploadPointsLedger(currentUserId.value, earnedLedger)
                    } catch (e: Exception) {
                        Log.w("PosRepository", "Points ledger sync deferred", e)
                    }
                }
            }

            // Requirement 4: Persist updated balance in Room immediately
            val finalCustomer = (existingCust ?: CustomerEntity(
                id = targetCustomerId,
                name = bill.customerName.ifBlank { "Customer" }.trim(),
                contactNumber = cleanPhone,
                isEnrolledInLoyalty = isEnrolled
            )).copy(
                name = bill.customerName.ifBlank { existingCust?.name ?: "Customer" }.trim(),
                contactNumber = if (cleanPhone.isNotBlank()) cleanPhone else (existingCust?.contactNumber ?: ""),
                isCreditCustomer = existingCust?.isCreditCustomer ?: (paymentMethod == "CREDIT"),
                loyaltyVisitCount = newVisitCount,
                loyaltyStartDate = newStartDate,
                loyaltyExpiryDate = newExpiryDate,
                currentLoyaltyProgramId = existingCust?.currentLoyaltyProgramId,
                loyaltyHistoryJson = formatLoyaltyHistory(history),
                rewardPointsBalance = finalRewardBal,
                giftPointsBalance = finalGiftBal,
                isEnrolledInLoyalty = finalIsEnrolled,
                lastVisitTimestamp = if (shouldAdvanceVisit) now else existingCust?.lastVisitTimestamp,
                updatedAt = now
            )
            customerDao.insertOrUpdate(finalCustomer)

            // Requirement 5: Sync updated balance to Firestore using atomic updates
            scope.launch {
                try {
                    if (rewardPtsEarned > 0) {
                        syncManager.incrementCustomerRewardPoints(
                            userId = currentUserId.value,
                            customerId = finalCustomer.id,
                            earnedPoints = rewardPtsEarned,
                            newBalance = finalRewardBal
                        )
                    }
                    syncManager.uploadCustomer(currentUserId.value, finalCustomer)
                } catch (e: Exception) {
                    Log.w("PosRepository", "Customer cloud sync deferred", e)
                }
            }
        }

        // 4. Update the bill with loyalty attributes
        val updatedBill = bill.copy(
            appliedRewardType = appliedRewardType,
            appliedOfferId = appliedOfferId,
            appliedOfferName = appliedOfferName,
            rewardPointsEarned = rewardPtsEarned,
            pointsRedeemed = pointsToRedeem,
            rewardPointsRedeemed = rewardPtsRedeemed,
            giftPointsRedeemed = giftPtsRedeemed,
            updatedAt = now
        )
        billDao.insertOrUpdate(updatedBill)
        scope.launch {
            try {
                syncManager.uploadBill(currentUserId.value, updatedBill)
            } catch (e: Exception) {
                Log.w("PosRepository", "Bill upload sync deferred", e)
            }
        }
        return updatedBill
    }
}
