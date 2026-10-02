package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthManager
import com.example.auth.PosUserState
import com.example.data.cloud.FirestoreSyncManager
import com.example.data.cloud.SyncState
import com.example.data.local.database.CafePosDatabase
import com.example.data.local.database.Converters
import com.example.data.local.entity.*
import com.example.data.repository.PosRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

data class CartState(
    val orderType: String = "DINE_IN", // "DINE_IN" or "TAKEAWAY"
    val selectedTable: CafeTableEntity? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val deliveryAddress: String = "",
    val items: List<BillItem> = emptyList(),
    val discountType: String = "NONE", // "NONE", "FLAT", "PERCENT"
    val discountValue: Double = 0.0,
    val taxPercent: Double = 0.0,
    val paymentMethod: String = "CASH", // "CASH" or "UPI"
    val customTimestamp: Long? = null, // For backdated bills
    val activeBillId: String? = null, // For editing existing running order
    val appliedRewardType: String = "NONE", // "NONE", "VISIT_REWARD", "MANUAL_OFFER", "POINTS_REDEMPTION"
    val appliedOfferId: String? = null,
    val appliedOfferName: String = "",
    val pointsToRedeem: Int = 0
) {
    val subtotal: Double
        get() = items.sumOf { it.totalPrice }

    val discountAmount: Double
        get() = when (discountType) {
            "PERCENT" -> (subtotal * (discountValue.coerceIn(0.0, 100.0) / 100.0))
            "FLAT" -> discountValue.coerceAtMost(subtotal)
            else -> 0.0
        }

    val taxAmount: Double
        get() = ((subtotal - discountAmount).coerceAtLeast(0.0) * (taxPercent.coerceIn(0.0, 100.0) / 100.0))

    val totalAmount: Double
        get() = ((subtotal - discountAmount).coerceAtLeast(0.0) + taxAmount).coerceAtLeast(0.0)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CafePosDatabase.getDatabase(application)
    val authManager = AuthManager(application)
    val syncManager = FirestoreSyncManager(application)
    val repository = PosRepository(application, db, authManager, syncManager)

    // User Auth State
    val userState: StateFlow<PosUserState> = authManager.userState

    // Theme Mode: null = system, true = dark, false = light
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    fun setThemeMode(isDark: Boolean?) {
        _isDarkTheme.value = isDark
    }

    // App Launch & Loading States - Instant launch
    private val _isRestaurantLoaded = MutableStateFlow(true)
    val isRestaurantLoaded: StateFlow<Boolean> = _isRestaurantLoaded.asStateFlow()

    private val _isTablesLoading = MutableStateFlow(false)
    val isTablesLoading: StateFlow<Boolean> = _isTablesLoading.asStateFlow()

    // Active Restaurant & List - Pre-warmed with cached or default BBC FOOD HUB for zero flicker
    val activeRestaurant: StateFlow<RestaurantEntity?> = repository.activeRestaurant
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repository.getCachedRestaurant() ?: repository.defaultBbcRestaurant
        )

    val allRestaurants: StateFlow<List<RestaurantEntity>> = repository.allRestaurants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Database Record Counts (Calculated live from Room DB)
    val liveBillsCount: StateFlow<Int> = repository.liveBillsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveCustomersCount: StateFlow<Int> = repository.liveCustomersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveInventoryCount: StateFlow<Int> = repository.liveInventoryCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveExpensesCount: StateFlow<Int> = repository.liveExpensesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveMenuItemsCount: StateFlow<Int> = repository.liveMenuItemsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveCategoriesCount: StateFlow<Int> = repository.liveCategoriesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveTablesCount: StateFlow<Int> = repository.liveTablesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveRegistersCount: StateFlow<Int> = repository.liveRegistersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveLowStockCount: StateFlow<Int> = repository.liveLowStockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Data streams
    val categories: StateFlow<List<CategoryEntity>> = repository.categoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val menuItems: StateFlow<List<MenuItemEntity>> = repository.menuItemDao.getAllMenuItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val combos: StateFlow<List<ComboEntity>> = repository.activeRestaurant
        .flatMapLatest { rest ->
            if (rest != null) repository.getCombosByRestaurant(rest.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeCombos: StateFlow<List<ComboEntity>> = repository.activeRestaurant
        .flatMapLatest { rest ->
            if (rest != null) repository.getActiveCombosByRestaurant(rest.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tables: StateFlow<List<CafeTableEntity>> = repository.cafeTableDao.getAllTables()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAddonDefinitions: StateFlow<List<AddonDefinitionEntity>> = repository.allAddonDefinitions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allActiveAddonDefinitions: StateFlow<List<AddonDefinitionEntity>> = repository.allActiveAddonDefinitions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBills: StateFlow<List<BillEntity>> = repository.billDao.getAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInventory: StateFlow<List<InventoryItemEntity>> = repository.inventoryDao.getAllInventory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecipes: StateFlow<List<RecipeEntity>> = repository.getAllRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipesMap: StateFlow<Map<String, RecipeEntity>> = repository.getAllRecipes()
        .map { list -> list.associateBy { it.menuItemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val inventoryMap: StateFlow<Map<String, InventoryItemEntity>> = repository.inventoryDao.getAllInventory()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val lowStockInventory: StateFlow<List<InventoryItemEntity>> = repository.inventoryDao.getLowStockItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<ExpenseEntity>> = repository.expenseDao.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBatches: StateFlow<List<com.example.data.local.entity.InventoryBatchEntity>> = repository.inventoryBatchDao.getAllBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<ExpenseCategoryEntity>> = repository.expenseDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRegisters: StateFlow<List<CashRegisterEntity>> = repository.cashRegisterDao.getAllRegisters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockCounts: StateFlow<List<StockCountEntity>> = repository.allStockCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomers: StateFlow<List<CustomerEntity>> = repository.getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomerPayments: StateFlow<List<com.example.data.local.entity.CustomerPaymentEntity>> = repository.allCustomerPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOffers: StateFlow<List<OfferEntity>> = repository.allOffers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allActivePointsBatches: StateFlow<List<PointsBatchEntity>> = repository.allActivePointsBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPointsLedger: StateFlow<List<PointsLedgerEntity>> = repository.allPointsLedger
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockTransactions: StateFlow<List<com.example.data.local.entity.StockTransactionEntity>> = repository.allStockTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pointsEngineRules: StateFlow<PointsEngineRules> = repository.pointsEngineRules

    fun updatePointsEngineRules(rules: PointsEngineRules) {
        viewModelScope.launch {
            repository.updatePointsEngineRules(rules)
        }
    }

    private val converters = Converters()
    private val billingPrefs = application.getSharedPreferences("pos_billing_temp_state", Context.MODE_PRIVATE)

    // Billing Navigation & UI Presentation State Preserved Across Tabs and Navigation
    private val _orderMode = MutableStateFlow("DINE_IN")
    val orderMode: StateFlow<String> = _orderMode.asStateFlow()

    private val _isOrderingMode = MutableStateFlow(false)
    val isOrderingMode: StateFlow<Boolean> = _isOrderingMode.asStateFlow()

    private val _isSettlementScreen = MutableStateFlow(false)
    val isSettlementScreen: StateFlow<Boolean> = _isSettlementScreen.asStateFlow()

    private val _selectedVacantTable = MutableStateFlow<CafeTableEntity?>(null)
    val selectedVacantTable: StateFlow<CafeTableEntity?> = _selectedVacantTable.asStateFlow()

    private val _showTakeawayCustomerDialog = MutableStateFlow(false)
    val showTakeawayCustomerDialog: StateFlow<Boolean> = _showTakeawayCustomerDialog.asStateFlow()

    fun setOrderMode(mode: String) {
        _orderMode.value = mode
        setOrderType(mode)
    }

    fun setOrderingMode(enabled: Boolean) {
        _isOrderingMode.value = enabled
    }

    fun setSettlementScreen(enabled: Boolean) {
        _isSettlementScreen.value = enabled
    }

    fun setSelectedVacantTable(table: CafeTableEntity?) {
        _selectedVacantTable.value = table
    }

    fun setShowTakeawayCustomerDialog(show: Boolean) {
        _showTakeawayCustomerDialog.value = show
    }

    private data class BillingPersistedSnapshot(
        val cart: CartState,
        val isOrdering: Boolean,
        val isSettlement: Boolean,
        val orderMode: String
    )

    private fun persistBillingState(snapshot: BillingPersistedSnapshot) {
        val cart = snapshot.cart
        billingPrefs.edit()
            .putBoolean("isOrderingMode", snapshot.isOrdering)
            .putBoolean("isSettlementScreen", snapshot.isSettlement)
            .putString("orderMode", snapshot.orderMode)
            .putString("orderType", cart.orderType)
            .putString("customerName", cart.customerName)
            .putString("customerPhone", cart.customerPhone)
            .putString("deliveryAddress", cart.deliveryAddress)
            .putString("tableId", cart.selectedTable?.id)
            .putString("tableRestaurantId", cart.selectedTable?.restaurantId ?: "")
            .putString("tableName", cart.selectedTable?.name)
            .putInt("tableCapacity", cart.selectedTable?.capacity ?: 4)
            .putString("itemsJson", converters.fromBillItemList(cart.items))
            .putString("discountType", cart.discountType)
            .putString("discountValue", cart.discountValue.toString())
            .putString("taxPercent", cart.taxPercent.toString())
            .putString("paymentMethod", cart.paymentMethod)
            .putString("activeBillId", cart.activeBillId)
            .apply()
    }

    private fun restoreBillingState() {
        try {
            val hasSaved = billingPrefs.contains("orderType") || billingPrefs.contains("itemsJson") || billingPrefs.contains("customerPhone")
            if (!hasSaved) return
            val itemsJson = billingPrefs.getString("itemsJson", null)
            val items = converters.toBillItemList(itemsJson)
            val customerName = billingPrefs.getString("customerName", "") ?: ""
            val customerPhone = billingPrefs.getString("customerPhone", "") ?: ""
            val deliveryAddress = billingPrefs.getString("deliveryAddress", "") ?: ""
            val orderType = billingPrefs.getString("orderType", "DINE_IN") ?: "DINE_IN"
            val orderModePref = billingPrefs.getString("orderMode", orderType) ?: orderType
            val tableId = billingPrefs.getString("tableId", null)
            val tableRestaurantId = billingPrefs.getString("tableRestaurantId", "") ?: ""
            val tableName = billingPrefs.getString("tableName", null)
            val tableCapacity = billingPrefs.getInt("tableCapacity", 4)
            val selectedTable = if (tableId != null && tableName != null) {
                CafeTableEntity(id = tableId, restaurantId = tableRestaurantId, name = tableName, capacity = tableCapacity)
            } else null
            val discountType = billingPrefs.getString("discountType", "NONE") ?: "NONE"
            val discountValue = billingPrefs.getString("discountValue", "0.0")?.toDoubleOrNull() ?: 0.0
            val taxPercent = billingPrefs.getString("taxPercent", "0.0")?.toDoubleOrNull() ?: 0.0
            val paymentMethod = billingPrefs.getString("paymentMethod", "CASH") ?: "CASH"
            val activeBillId = billingPrefs.getString("activeBillId", null)
            val isOrdering = billingPrefs.getBoolean("isOrderingMode", false) || items.isNotEmpty() || customerName.isNotBlank()
            val isSettlement = billingPrefs.getBoolean("isSettlementScreen", false)

            if (items.isNotEmpty() || customerName.isNotBlank() || isOrdering || isSettlement) {
                _cartState.value = CartState(
                    orderType = orderType,
                    selectedTable = selectedTable,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    deliveryAddress = deliveryAddress,
                    items = items,
                    discountType = discountType,
                    discountValue = discountValue,
                    taxPercent = taxPercent,
                    paymentMethod = paymentMethod,
                    activeBillId = activeBillId
                )
                _orderMode.value = orderModePref
                _isOrderingMode.value = isOrdering
                _isSettlementScreen.value = isSettlement
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error restoring billing state from prefs", e)
        }
    }

    private fun clearBillingStateFromPrefs() {
        billingPrefs.edit().clear().apply()
    }

    // Cart / Billing State
    private val _cartState = MutableStateFlow(CartState())
    val cartState: StateFlow<CartState> = _cartState.asStateFlow()

    // Last Settled Bill (for A4 PDF viewing and WhatsApp/SMS share)
    private val _settledBill = MutableStateFlow<BillEntity?>(null)
    val settledBill: StateFlow<BillEntity?> = _settledBill.asStateFlow()

    // Active KOT Bill (for KOT printing/preview dialog - items only, NO prices)
    private val _activeKotBill = MutableStateFlow<BillEntity?>(null)
    val activeKotBill: StateFlow<BillEntity?> = _activeKotBill.asStateFlow()

    init {
        // App launch loading tracking & table occupancy reconciliation
        viewModelScope.launch {
            try {
                val directRest = repository.getActiveRestaurantOrFirst()
                if (!directRest.isActive) {
                    repository.restaurantDao.setActiveRestaurant(directRest.id)
                }
                repository.reconcileTableOccupancy()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error checking initial restaurant", e)
            } finally {
                _isRestaurantLoaded.value = true
                _isTablesLoading.value = false
            }
        }

        viewModelScope.launch {
            repository.cafeTableDao.getAllTables().collect {
                _isTablesLoading.value = false
            }
        }

        // Automatically restore / sync from cloud on startup when authenticated
        viewModelScope.launch {
            repository.currentUserId.collect { uid ->
                if (uid.isNotBlank() && !uid.startsWith("pos_uid_")) {
                    try {
                        repository.restoreAllNow()
                        repository.startRealtimeSync()
                    } catch (e: Exception) {
                        // Safe startup background restore
                    }
                } else {
                    repository.stopRealtimeSync()
                }
            }
        }

        // Restore any temporary unsaved billing state across process restarts
        restoreBillingState()

        // Automatically persist billing state changes in real time
        viewModelScope.launch {
            combine(_cartState, _isOrderingMode, _isSettlementScreen, _orderMode) { cart, ordering, settlement, mode ->
                BillingPersistedSnapshot(cart, ordering, settlement, mode)
            }.collect { snapshot ->
                val cart = snapshot.cart
                if (cart.items.isEmpty() && cart.customerName.isBlank() && cart.customerPhone.isBlank() && !snapshot.isOrdering && !snapshot.isSettlement) {
                    clearBillingStateFromPrefs()
                } else {
                    persistBillingState(snapshot)
                }
            }
        }
    }

    val activeUnsettledBills: StateFlow<List<BillEntity>> = repository.billDao.getActiveUnsettledBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearSettledBill() {
        _settledBill.value = null
    }

    fun dismissKot() {
        _activeKotBill.value = null
    }

    fun showKotForBill(bill: BillEntity) {
        _activeKotBill.value = bill
    }

    // Cart operations
    fun setOrderType(type: String) {
        // "DINE_IN" or "TAKEAWAY"
        _cartState.value = _cartState.value.copy(
            orderType = type,
            selectedTable = if (type == "TAKEAWAY") null else _cartState.value.selectedTable
        )
    }

    fun selectTable(table: CafeTableEntity?) {
        _cartState.value = _cartState.value.copy(selectedTable = table)
    }

    fun setCustomerInfo(name: String, phone: String, address: String = _cartState.value.deliveryAddress) {
        _cartState.value = _cartState.value.copy(
            customerName = name,
            customerPhone = phone,
            deliveryAddress = address
        )
    }

    fun setTaxPercent(tax: Double) {
        _cartState.value = _cartState.value.copy(taxPercent = tax)
    }

    fun setDiscount(type: String, value: Double) {
        _cartState.value = _cartState.value.copy(
            discountType = type,
            discountValue = value
        )
    }

    fun setPaymentMethod(method: String) {
        // Strictly "CASH" or "UPI"
        _cartState.value = _cartState.value.copy(paymentMethod = method)
    }

    fun setCustomTimestamp(timestamp: Long?) {
        _cartState.value = _cartState.value.copy(customTimestamp = timestamp)
    }

    fun addItemToCart(dish: MenuItemEntity) {
        val variants = parseVariants(dish.variantsJson)
        val addons = parseAddons(dish.addonsJson)
        if (variants.isNotEmpty() || addons.isNotEmpty()) {
            // Default to first variant if present
            addItemToCartWithCustomization(
                dish = dish,
                selectedVariant = variants.firstOrNull(),
                selectedAddons = emptyList(),
                cookingNotes = ""
            )
        } else {
            addItemToCartWithCustomization(
                dish = dish,
                selectedVariant = null,
                selectedAddons = emptyList(),
                cookingNotes = ""
            )
        }
    }

    fun addItemToCartWithCustomization(
        dish: MenuItemEntity,
        selectedVariant: MenuItemVariant? = null,
        selectedAddons: List<SelectedAddon> = emptyList(),
        cookingNotes: String = "",
        quantity: Int = 1,
        isComplimentary: Boolean = false,
        complimentaryReason: String = ""
    ) {
        val basePrice = selectedVariant?.price ?: dish.price
        val addonsPrice = selectedAddons.sumOf { it.totalPrice }
        val finalUnitPrice = basePrice + addonsPrice
        val variantName = selectedVariant?.name ?: ""
        val addonsJson = formatSelectedAddons(selectedAddons)
        val noteStr = if (isComplimentary) "Complimentary: ${complimentaryReason.ifBlank { "On the House" }}" else ""

        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst {
            it.dishId == dish.id &&
                    it.selectedVariant == variantName &&
                    it.selectedAddonsJson == addonsJson &&
                    it.cookingNotes == cookingNotes &&
                    it.isFree == isComplimentary
        }

        if (index >= 0) {
            val existing = currentItems[index]
            val newQty = existing.quantity + quantity
            currentItems[index] = existing.copy(
                quantity = newQty,
                unitPrice = finalUnitPrice,
                totalPrice = if (isComplimentary) 0.0 else (newQty * finalUnitPrice),
                isFree = isComplimentary,
                notes = noteStr
            )
        } else {
            currentItems.add(
                BillItem(
                    dishId = dish.id,
                    dishName = dish.name,
                    unitPrice = finalUnitPrice,
                    quantity = quantity,
                    totalPrice = if (isComplimentary) 0.0 else (quantity * finalUnitPrice),
                    selectedVariant = variantName,
                    selectedAddonsJson = addonsJson,
                    cookingNotes = cookingNotes,
                    isFree = isComplimentary,
                    notes = noteStr
                )
            )
        }
        _cartState.value = _cartState.value.copy(items = currentItems)
    }

    fun updateCartItem(
        oldItem: BillItem,
        dish: MenuItemEntity,
        selectedVariant: MenuItemVariant? = null,
        selectedAddons: List<SelectedAddon> = emptyList(),
        cookingNotes: String = "",
        quantity: Int = 1,
        isComplimentary: Boolean = false,
        complimentaryReason: String = ""
    ) {
        val basePrice = selectedVariant?.price ?: dish.price
        val addonsPrice = selectedAddons.sumOf { it.totalPrice }
        val finalUnitPrice = basePrice + addonsPrice
        val variantName = selectedVariant?.name ?: ""
        val addonsJson = formatSelectedAddons(selectedAddons)
        val noteStr = if (isComplimentary) "Complimentary: ${complimentaryReason.ifBlank { "On the House" }}" else oldItem.notes

        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst {
            it.dishId == oldItem.dishId &&
                    it.selectedVariant == oldItem.selectedVariant &&
                    it.selectedAddonsJson == oldItem.selectedAddonsJson &&
                    it.cookingNotes == oldItem.cookingNotes &&
                    it.notes == oldItem.notes
        }

        val newItem = BillItem(
            dishId = dish.id,
            dishName = dish.name,
            unitPrice = finalUnitPrice,
            quantity = quantity,
            totalPrice = if (isComplimentary) 0.0 else (quantity * finalUnitPrice),
            selectedVariant = variantName,
            selectedAddonsJson = addonsJson,
            cookingNotes = cookingNotes,
            notes = noteStr,
            isFree = isComplimentary
        )

        if (index >= 0) {
            currentItems[index] = newItem
        } else {
            currentItems.add(newItem)
        }
        _cartState.value = _cartState.value.copy(items = currentItems)
    }

    fun toggleCartItemComplimentary(item: BillItem, isComplimentary: Boolean, reason: String = "Owner Treat") {
        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst {
            it.dishId == item.dishId &&
                    it.selectedVariant == item.selectedVariant &&
                    it.selectedAddonsJson == item.selectedAddonsJson &&
                    it.cookingNotes == item.cookingNotes &&
                    it.notes == item.notes
        }
        if (index >= 0) {
            val existing = currentItems[index]
            val updated = existing.copy(
                isFree = isComplimentary,
                totalPrice = if (isComplimentary) 0.0 else (existing.quantity * existing.unitPrice),
                notes = if (isComplimentary) "Complimentary: $reason" else existing.notes.replace(Regex("Complimentary:.*"), "").trim()
            )
            currentItems[index] = updated
            _cartState.value = _cartState.value.copy(items = currentItems)
        }
    }

    fun decreaseCartItem(dishId: String) {
        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst { it.dishId == dishId }
        if (index >= 0) {
            val existing = currentItems[index]
            if (existing.quantity > 1) {
                currentItems[index] = existing.copy(
                    quantity = existing.quantity - 1,
                    totalPrice = (existing.quantity - 1) * existing.unitPrice
                )
            } else {
                currentItems.removeAt(index)
            }
            _cartState.value = _cartState.value.copy(items = currentItems)
        }
    }

    fun decreaseCartItem(item: BillItem) {
        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst {
            it.dishId == item.dishId &&
                    it.selectedVariant == item.selectedVariant &&
                    it.selectedAddonsJson == item.selectedAddonsJson &&
                    it.cookingNotes == item.cookingNotes &&
                    it.notes == item.notes
        }
        if (index >= 0) {
            val existing = currentItems[index]
            if (existing.quantity > 1) {
                currentItems[index] = existing.copy(
                    quantity = existing.quantity - 1,
                    totalPrice = (existing.quantity - 1) * existing.unitPrice
                )
            } else {
                currentItems.removeAt(index)
            }
            _cartState.value = _cartState.value.copy(items = currentItems)
        }
    }

    fun increaseCartItem(dishId: String) {
        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst { it.dishId == dishId }
        if (index >= 0) {
            val existing = currentItems[index]
            currentItems[index] = existing.copy(
                quantity = existing.quantity + 1,
                totalPrice = (existing.quantity + 1) * existing.unitPrice
            )
            _cartState.value = _cartState.value.copy(items = currentItems)
        }
    }

    fun increaseCartItem(item: BillItem) {
        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst {
            it.dishId == item.dishId &&
                    it.selectedVariant == item.selectedVariant &&
                    it.selectedAddonsJson == item.selectedAddonsJson &&
                    it.cookingNotes == item.cookingNotes &&
                    it.notes == item.notes
        }
        if (index >= 0) {
            val existing = currentItems[index]
            currentItems[index] = existing.copy(
                quantity = existing.quantity + 1,
                totalPrice = (existing.quantity + 1) * existing.unitPrice
            )
            _cartState.value = _cartState.value.copy(items = currentItems)
        }
    }

    fun removeCartItem(item: BillItem) {
        val currentItems = _cartState.value.items.toMutableList()
        val index = currentItems.indexOfFirst {
            it.dishId == item.dishId &&
                    it.selectedVariant == item.selectedVariant &&
                    it.selectedAddonsJson == item.selectedAddonsJson &&
                    it.cookingNotes == item.cookingNotes &&
                    it.notes == item.notes
        }
        if (index >= 0) {
            currentItems.removeAt(index)
            _cartState.value = _cartState.value.copy(items = currentItems)
        }
    }

    fun addComboToCart(
        combo: ComboEntity,
        selectedItemNames: List<String>,
        selectedDishQuantities: Map<String, Int> = emptyMap()
    ) {
        val currentItems = _cartState.value.items.toMutableList()
        val userVisibleNotes = selectedItemNames.joinToString(", ")
        // Format encoded dish mapping: e.g. [DISHES:dish1=1;dish2=1]
        val encodedDishes = if (selectedDishQuantities.isNotEmpty()) {
            " [DISHES:" + selectedDishQuantities.entries.joinToString(";") { "${it.key}=${it.value}" } + "]"
        } else ""
        val fullNotes = userVisibleNotes + encodedDishes
        val comboLineId = "combo_${combo.id}_${UUID.randomUUID().toString().take(6)}"
        currentItems.add(
            BillItem(
                dishId = comboLineId,
                dishName = combo.name,
                unitPrice = combo.price,
                quantity = 1,
                totalPrice = combo.price,
                notes = fullNotes
            )
        )
        _cartState.value = _cartState.value.copy(items = currentItems)
    }

    fun saveCombo(combo: ComboEntity) {
        viewModelScope.launch {
            repository.saveCombo(combo)
        }
    }

    fun deleteCombo(comboId: String) {
        viewModelScope.launch {
            repository.deleteCombo(comboId)
        }
    }

    fun toggleComboActive(combo: ComboEntity) {
        viewModelScope.launch {
            repository.saveCombo(combo.copy(isActive = !combo.isActive))
        }
    }

    fun removeCartItem(dishId: String) {
        val currentItems = _cartState.value.items.filter { it.dishId != dishId }
        val remainingFree = currentItems.any { it.isFree }
        _cartState.value = _cartState.value.copy(
            items = currentItems,
            appliedRewardType = if (remainingFree) _cartState.value.appliedRewardType else "NONE",
            appliedOfferId = if (remainingFree) _cartState.value.appliedOfferId else null,
            appliedOfferName = if (remainingFree) _cartState.value.appliedOfferName else ""
        )
    }

    fun applyFreeItemReward(offer: OfferEntity, dish: MenuItemEntity, quantity: Int = 1) {
        val currentItems = _cartState.value.items.toMutableList()
        // Remove prior free item if any
        currentItems.removeAll { it.isFree }
        currentItems.add(
            BillItem(
                dishId = dish.id,
                dishName = "${dish.name} (🎁 Free Reward)",
                unitPrice = dish.price,
                quantity = quantity,
                totalPrice = 0.0,
                isFree = true,
                notes = "Offer: ${offer.name}"
            )
        )
        _cartState.value = _cartState.value.copy(
            items = currentItems,
            appliedRewardType = if (offer.offerType == "VISIT_BASED") "VISIT_REWARD" else "MANUAL_OFFER",
            appliedOfferId = offer.id,
            appliedOfferName = offer.name
        )
    }

    fun removeFreeItemReward() {
        val currentItems = _cartState.value.items.filterNot { it.isFree }
        _cartState.value = _cartState.value.copy(
            items = currentItems,
            appliedRewardType = "NONE",
            appliedOfferId = null,
            appliedOfferName = ""
        )
    }

    fun setPointsToRedeem(points: Int) {
        _cartState.value = _cartState.value.copy(pointsToRedeem = points.coerceAtLeast(0))
    }

    fun clearCart() {
        _cartState.value = CartState(
            orderType = _cartState.value.orderType,
            paymentMethod = _cartState.value.paymentMethod
        )
        _isOrderingMode.value = false
        _isSettlementScreen.value = false
        _selectedVacantTable.value = null
        _showTakeawayCustomerDialog.value = false
        clearBillingStateFromPrefs()
    }

    /**
     * Completes and settles current bill.
     */
    fun settleCurrentBill(
        paymentMethod: String? = null,
        discountType: String? = null,
        discountValue: Double? = null,
        onSuccess: (BillEntity) -> Unit
    ) {
        val cart = _cartState.value
        if (cart.items.isEmpty()) return

        viewModelScope.launch {
            try {
                val restaurant = activeRestaurant.value ?: repository.getActiveRestaurantOrFirst()
                if (restaurant == null) {
                    Log.e("MainViewModel", "Cannot settle bill: no restaurant available")
                    return@launch
                }
                val bill = repository.createAndSettleBill(
                    restaurantId = restaurant.id,
                    restaurantName = restaurant.name,
                    orderType = cart.orderType,
                    tableId = cart.selectedTable?.id,
                    tableName = cart.selectedTable?.name,
                    customerName = cart.customerName,
                    customerPhone = cart.customerPhone,
                    items = cart.items,
                    discountType = discountType ?: cart.discountType,
                    discountValue = discountValue ?: cart.discountValue,
                    paymentMethod = paymentMethod ?: cart.paymentMethod,
                    customTimestamp = cart.customTimestamp,
                    appliedRewardType = cart.appliedRewardType,
                    appliedOfferId = cart.appliedOfferId,
                    appliedOfferName = cart.appliedOfferName,
                    pointsToRedeem = cart.pointsToRedeem
                )
                _settledBill.value = bill
                clearCart()
                onSuccess(bill)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error settling bill", e)
            }
        }
    }

    /**
     * Send current order to kitchen (KOT):
     * Does not settle the bill. Saves as active order, marks table occupied, and generates KOT.
     */
    fun sendCurrentOrderToKitchen(onSuccess: (BillEntity) -> Unit) {
        val cart = _cartState.value
        if (cart.items.isEmpty()) return

        viewModelScope.launch {
            try {
                val restaurant = activeRestaurant.value ?: repository.getActiveRestaurantOrFirst()
                if (restaurant == null) {
                    Log.e("MainViewModel", "Cannot send order to kitchen: no restaurant available")
                    return@launch
                }
                val bill = repository.sendOrderToKitchen(
                    restaurantId = restaurant.id,
                    restaurantName = restaurant.name,
                    orderType = cart.orderType,
                    tableId = cart.selectedTable?.id,
                    tableName = cart.selectedTable?.name,
                    customerName = cart.customerName,
                    customerPhone = cart.customerPhone,
                    items = cart.items,
                    existingBillId = cart.activeBillId
                )
                _activeKotBill.value = bill
                clearCart()
                onSuccess(bill)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error sending order to kitchen", e)
            }
        }
    }

    /**
     * Start a new Dine-In order for a table after capturing customer details.
     */
    fun startNewOrderForTable(table: CafeTableEntity, customerName: String, customerPhone: String, deliveryAddress: String = "") {
        _cartState.value = CartState(
            orderType = "DINE_IN",
            selectedTable = table,
            customerName = customerName,
            customerPhone = customerPhone,
            deliveryAddress = deliveryAddress,
            items = emptyList(),
            activeBillId = null
        )
        _orderMode.value = "DINE_IN"
        _isOrderingMode.value = true
        _isSettlementScreen.value = false
        _selectedVacantTable.value = null
    }

    /**
     * Start a new Takeaway order after capturing customer details.
     */
    fun startNewTakeawayOrder(customerName: String, customerPhone: String, deliveryAddress: String = "") {
        _cartState.value = CartState(
            orderType = "TAKEAWAY",
            selectedTable = null,
            customerName = customerName,
            customerPhone = customerPhone,
            deliveryAddress = deliveryAddress,
            items = emptyList(),
            activeBillId = null
        )
        _orderMode.value = "TAKEAWAY"
        _isOrderingMode.value = true
        _isSettlementScreen.value = false
        _showTakeawayCustomerDialog.value = false
    }

    /**
     * Load an active takeaway bill back into the cart (to edit or cash out).
     */
    fun loadActiveTakeawayOrder(bill: BillEntity, onLoaded: () -> Unit = {}) {
        _cartState.value = CartState(
            orderType = "TAKEAWAY",
            selectedTable = null,
            customerName = bill.customerName,
            customerPhone = bill.customerPhone,
            deliveryAddress = "",
            items = bill.items,
            discountType = bill.discountType,
            discountValue = bill.discountValue,
            paymentMethod = bill.paymentMethod,
            customTimestamp = null,
            activeBillId = bill.id
        )
        _orderMode.value = "TAKEAWAY"
        _isOrderingMode.value = true
        _isSettlementScreen.value = false
        onLoaded()
    }

    /**
     * Load an active unsettled bill back into the cart (e.g. when staff taps an occupied table to add more items).
     */
    fun loadActiveOrderForTable(table: CafeTableEntity, onLoaded: (BillEntity?) -> Unit) {
        viewModelScope.launch {
            // Must strictly only fetch a valid, non-cancelled, non-voided, non-settled bill
            val candidateBill = if (!table.activeBillId.isNullOrBlank()) {
                val b = repository.billDao.getBillById(table.activeBillId!!)
                if (b != null && !b.isCancelled && !b.isVoided && !b.isSettled) b else null
            } else null

            val validBill = candidateBill ?: repository.billDao.getActiveBillForTable(table.id)

            if (validBill != null) {
                _cartState.value = CartState(
                    orderType = "DINE_IN",
                    selectedTable = table,
                    customerName = validBill.customerName,
                    customerPhone = validBill.customerPhone,
                    deliveryAddress = "",
                    items = validBill.items,
                    discountType = validBill.discountType,
                    discountValue = validBill.discountValue,
                    paymentMethod = validBill.paymentMethod,
                    customTimestamp = null,
                    activeBillId = validBill.id
                )
            } else {
                // Table had an orphaned cancelled/settled bill ID. Reset table to vacant immediately!
                repository.cafeTableDao.updateTableOccupancy(table.id, isOccupied = false, activeBillId = null)
                val cleanTable = repository.cafeTableDao.getTableById(table.id)
                if (cleanTable != null) {
                    try {
                        repository.uploadTable(cleanTable)
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Table reset sync deferred", e)
                    }
                }
                _cartState.value = CartState(
                    orderType = "DINE_IN",
                    selectedTable = cleanTable ?: table.copy(isOccupied = false, activeBillId = null),
                    customerName = "",
                    customerPhone = "",
                    deliveryAddress = "",
                    items = emptyList(),
                    activeBillId = null
                )
            }
            _orderMode.value = "DINE_IN"
            _isOrderingMode.value = true
            _isSettlementScreen.value = false
            onLoaded(validBill)
        }
    }

    data class DiscountBreakdown(
        val subtotal: Double,
        val eligibleSubtotal: Double,
        val discountAmount: Double,
        val totalAmount: Double,
        val hasIneligibleItems: Boolean
    )

    fun calculateDiscountBreakdown(
        items: List<BillItem>,
        discountType: String,
        discountValue: Double
    ): DiscountBreakdown {
        val catMap = categories.value.associateBy { it.id }
        val itemMap = menuItems.value.associateBy { it.id }

        val subtotal = items.sumOf { it.totalPrice }
        var eligibleSubtotal = 0.0
        var hasIneligible = false

        for (item in items) {
            val mi = itemMap[item.dishId]
            val itemEligible = mi?.isDiscountEligible ?: true
            val cat = mi?.categoryId?.let { catMap[it] }
            val catEligible = cat?.isDiscountEligible ?: true
            val isEligible = itemEligible && catEligible
            if (isEligible) {
                eligibleSubtotal += item.totalPrice
            } else {
                hasIneligible = true
            }
        }

        val discountAmount = when (discountType) {
            "PERCENT" -> (eligibleSubtotal * (discountValue.coerceIn(0.0, 100.0) / 100.0))
            "FLAT" -> discountValue.coerceAtMost(eligibleSubtotal)
            else -> 0.0
        }
        val totalAmount = (subtotal - discountAmount).coerceAtLeast(0.0)

        return DiscountBreakdown(
            subtotal = subtotal,
            eligibleSubtotal = eligibleSubtotal,
            discountAmount = discountAmount,
            totalAmount = totalAmount,
            hasIneligibleItems = hasIneligible
        )
    }

    /**
     * Gets total outstanding credit (Khata) balance for a customer.
     */
    fun getCustomerOutstandingCredit(customerName: String, customerPhone: String): Double {
        val cleanPhone = customerPhone.trim()
        val cleanName = customerName.trim()
        if (cleanPhone.isBlank() && cleanName.isBlank()) return 0.0

        val creditBills = allBills.value.filter { b ->
            val matchPhone = cleanPhone.isNotBlank() && b.customerPhone.trim() == cleanPhone
            val matchName = cleanName.isNotBlank() && b.customerName.trim().equals(cleanName, ignoreCase = true)
            (matchPhone || matchName) && b.paymentMethod == "CREDIT" && !b.isVoided
        }
        val totalCredit = creditBills.sumOf { it.totalAmount }

        val payments = allCustomerPayments.value.filter { p ->
            val matchPhone = cleanPhone.isNotBlank() && p.customerPhone.trim() == cleanPhone
            val matchName = cleanName.isNotBlank() && p.customerName.trim().equals(cleanName, ignoreCase = true)
            matchPhone || matchName
        }
        val totalPaid = payments.sumOf { it.amount }

        return (totalCredit - totalPaid).coerceAtLeast(0.0)
    }

    /**
     * Unified settlement method for SettlementScreen.
     * Handles live-edited items, updated customer details, Split (Cash + UPI), Credit, and discounts.
     */
    fun settleBill(
        paymentMethod: String,
        discountType: String,
        discountValue: Double,
        customerName: String,
        customerPhone: String,
        cashAmount: Double = 0.0,
        upiAmount: Double = 0.0,
        appliedRewardType: String = "NONE",
        appliedOfferId: String? = null,
        appliedOfferName: String = "",
        pointsToRedeem: Int = 0,
        enrollInPass: Boolean? = null,
        onSuccess: (BillEntity) -> Unit
    ) {
        val cart = _cartState.value
        if (cart.items.isEmpty()) return

        viewModelScope.launch {
            try {
                val restaurant = activeRestaurant.value ?: repository.getActiveRestaurantOrFirst()
                if (restaurant == null) {
                    Log.e("MainViewModel", "Cannot settle bill: no restaurant available")
                    return@launch
                }

                val settled = if (cart.activeBillId != null) {
                    repository.cashOutActiveBill(
                        billId = cart.activeBillId,
                        paymentMethod = paymentMethod,
                        discountType = discountType,
                        discountValue = discountValue,
                        items = cart.items,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        cashAmount = cashAmount,
                        upiAmount = upiAmount,
                        appliedRewardType = appliedRewardType,
                        appliedOfferId = appliedOfferId,
                        appliedOfferName = appliedOfferName,
                        pointsToRedeem = pointsToRedeem,
                        enrollInPass = enrollInPass
                    )
                } else {
                    repository.createAndSettleBill(
                        restaurantId = restaurant.id,
                        restaurantName = restaurant.name,
                        orderType = cart.orderType,
                        tableId = cart.selectedTable?.id,
                        tableName = cart.selectedTable?.name,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        items = cart.items,
                        discountType = discountType,
                        discountValue = discountValue,
                        paymentMethod = paymentMethod,
                        cashAmount = cashAmount,
                        upiAmount = upiAmount,
                        customTimestamp = cart.customTimestamp,
                        appliedRewardType = appliedRewardType,
                        appliedOfferId = appliedOfferId,
                        appliedOfferName = appliedOfferName,
                        pointsToRedeem = pointsToRedeem,
                        enrollInPass = enrollInPass
                    )
                }
                _settledBill.value = settled
                clearCart()
                onSuccess(settled)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error settling bill", e)
            }
        }
    }

    /**
     * Cash Out an active running bill for a table.
     */
    fun cashOutActiveBill(
        billId: String,
        paymentMethod: String,
        discountType: String,
        discountValue: Double,
        onSuccess: (BillEntity) -> Unit
    ) {
        viewModelScope.launch {
            val settled = repository.cashOutActiveBill(billId, paymentMethod, discountType, discountValue)
            _settledBill.value = settled
            clearCart()
            onSuccess(settled)
        }
    }

    // ==========================================
    // OFFERS & LOYALTY VIEWMODEL METHODS
    // ==========================================

    fun saveOffer(offer: OfferEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveOffer(offer)
            onDone()
        }
    }

    fun deleteOffer(offerId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteOffer(offerId)
            onDone()
        }
    }

    fun toggleOfferActive(offerId: String, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleOfferActive(offerId, isActive)
        }
    }

    fun saveVisitProgram(totalVisits: Int, offers: List<OfferEntity>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val restId = activeRestaurant.value?.id ?: "REST-001"
            repository.saveVisitProgram(restId, totalVisits, offers)
            onDone()
        }
    }

    fun resetCustomerLoyalty(customerId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetCustomerLoyalty(customerId)
            onDone()
        }
    }

    fun extendCustomerLoyalty(customerId: String, days: Int = 45, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.extendCustomerLoyalty(customerId, days)
            onDone()
        }
    }

    fun addGiftPoints(customerId: String, points: Int, notes: String = "", expiryDays: Int = 60, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addGiftPoints(customerId, points, notes, expiryDays)
            onDone()
        }
    }

    fun addBulkGiftPoints(customerIds: List<String>, points: Int, notes: String = "", expiryDays: Int = 60, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            for (id in customerIds) {
                repository.addGiftPoints(id, points, notes, expiryDays)
            }
            onDone()
        }
    }

    suspend fun getCustomerPointsSummary(customerId: String): com.example.data.repository.PosRepository.CustomerPointsSummary {
        return repository.getCustomerPointsSummary(customerId)
    }

    suspend fun getOutstandingPointsLiability(): Double {
        return repository.getOutstandingPointsLiability()
    }

    fun voidBill(billId: String, reason: String = "Voided by staff", onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.reverseSettledBillLoyaltyAndPoints(billId, reason)
            onDone()
        }
    }

    /**
     * Transfer/Shift an active order from one table to another.
     */
    fun transferTable(
        sourceTableId: String,
        destTableId: String,
        billId: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.transferTable(sourceTableId, destTableId, billId)
            onResult(success)
        }
    }

    /**
     * Add new custom table (e.g. "Table 1", "Window Seat", "VIP-1").
     */
    fun addCustomTable(name: String, capacity: Int) {
        val rest = activeRestaurant.value ?: return
        viewModelScope.launch {
            repository.addCustomTable(name, capacity, rest.id)
        }
    }

    /**
     * Delete custom table.
     */
    fun deleteCustomTable(tableId: String) {
        viewModelScope.launch {
            repository.cafeTableDao.deleteById(tableId)
            syncManager.deleteTable(repository.currentUserId.value, tableId)
        }
    }

    /**
     * Lookup customer by phone to auto-fill name.
     */
    fun findCustomerByPhone(phone: String, onResult: (CustomerEntity?) -> Unit) {
        viewModelScope.launch {
            val cust = repository.customerDao.getCustomerByContact(phone.trim())
            onResult(cust)
        }
    }

    /**
     * Fetch past orders history for a customer phone.
     */
    fun getCustomerBillHistory(phone: String): Flow<List<BillEntity>> {
        return repository.billDao.getBillsByCustomerPhone(phone.trim())
    }

    /**
     * Set or toggle Credit Customer status for a customer.
     */
    fun setCustomerCreditStatus(customerId: String, isCreditCustomer: Boolean, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.setCustomerCreditStatus(customerId, isCreditCustomer)
            onComplete?.invoke()
        }
    }

    fun toggleCustomerLoyaltyEnrollment(customerId: String, isEnrolled: Boolean) {
        viewModelScope.launch {
            repository.toggleCustomerLoyaltyEnrollment(customerId, isEnrolled)
        }
    }

    fun reEnableCustomerLoyalty(customerId: String) {
        viewModelScope.launch {
            repository.reEnableCustomerLoyalty(customerId)
        }
    }

    fun bulkUpdateLoyaltyEnrollment(customerIds: List<String>, isEnrolled: Boolean) {
        viewModelScope.launch {
            repository.bulkUpdateLoyaltyEnrollment(customerIds, isEnrolled)
        }
    }

    fun setZeroVisitCustomersLoyaltyEnrollment(isEnrolled: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setZeroVisitCustomersLoyaltyEnrollment(isEnrolled)
            onDone()
        }
    }

    fun setAllCustomersLoyaltyEnrollment(isEnrolled: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setAllCustomersLoyaltyEnrollment(isEnrolled)
            onDone()
        }
    }

    /**
     * Save or update customer details.
     */
    fun saveCustomer(customer: CustomerEntity, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.saveCustomer(customer)
            onComplete?.invoke()
        }
    }

    /**
     * Record a payment made by a customer against their credit balance.
     */
    fun recordCustomerPayment(
        customerId: String,
        customerName: String,
        customerPhone: String,
        amount: Double,
        paymentMode: String,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onComplete: ((com.example.data.local.entity.CustomerPaymentEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val payment = repository.recordCustomerPayment(
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                amount = amount,
                paymentMode = paymentMode,
                notes = notes,
                timestamp = timestamp
            )
            onComplete?.invoke(payment)
        }
    }

    /**
     * Save first-time restaurant setup.
     */
    fun saveInitialRestaurantSetup(
        name: String,
        address: String,
        phone: String,
        footerNote: String,
        logoUri: String?,
        fssaiNumber: String = "",
        isFssaiEnabled: Boolean = false,
        showFssaiOnBill: Boolean = false,
        gstNumber: String = "",
        gstRate: Double = 5.0,
        isGstEnabled: Boolean = false,
        showGstOnBill: Boolean = false,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.saveInitialRestaurantSetup(
                name, address, phone, footerNote, logoUri,
                fssaiNumber, isFssaiEnabled, showFssaiOnBill,
                gstNumber, gstRate, isGstEnabled, showGstOnBill
            )
            onComplete()
        }
    }

    /**
     * Delete a restaurant / branch
     */
    fun deleteRestaurant(restaurantId: String) {
        viewModelScope.launch {
            repository.deleteRestaurant(restaurantId)
        }
    }

    /**
     * Edit previously settled or backdated bill.
     */
    fun updateBill(bill: BillEntity) {
        viewModelScope.launch {
            repository.updateSettledBill(bill)
        }
    }

    /**
     * Multi-Shop Management: Switch active restaurant
     */
    fun switchActiveRestaurant(restaurantId: String) {
        viewModelScope.launch {
            repository.restaurantDao.setActiveRestaurant(restaurantId)
        }
    }

    /**
     * Multi-Shop Management: Add or edit restaurant
     */
    fun saveRestaurant(restaurant: RestaurantEntity) {
        viewModelScope.launch {
            repository.restaurantDao.insertOrUpdate(restaurant)
            syncManager.uploadRestaurant(repository.currentUserId.value, restaurant)
        }
    }

    /**
     * Menu & Categories Management
     */
    fun saveCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.categoryDao.insertOrUpdate(category)
            syncManager.uploadCategory(repository.currentUserId.value, category)
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.categoryDao.deleteById(id)
            syncManager.deleteCategory(repository.currentUserId.value, id)
        }
    }

    fun saveMenuItem(item: MenuItemEntity) {
        viewModelScope.launch {
            repository.menuItemDao.insertOrUpdate(item)
            syncManager.uploadMenuItem(repository.currentUserId.value, item)
        }
    }

    fun deleteMenuItem(id: String) {
        viewModelScope.launch {
            repository.menuItemDao.deleteById(id)
            syncManager.deleteMenuItem(repository.currentUserId.value, id)
        }
    }

    /**
     * Tables Management
     */
    fun saveTable(table: CafeTableEntity) {
        viewModelScope.launch {
            repository.cafeTableDao.insertOrUpdate(table)
            syncManager.uploadTable(repository.currentUserId.value, table)
        }
    }

    fun deleteTable(id: String) {
        viewModelScope.launch {
            repository.cafeTableDao.deleteById(id)
            syncManager.deleteTable(repository.currentUserId.value, id)
        }
    }

    fun setTableOccupancy(tableId: String, isOccupied: Boolean) {
        viewModelScope.launch {
            repository.cafeTableDao.updateTableOccupancy(tableId, isOccupied, null)
            val updated = repository.cafeTableDao.getTableById(tableId)
            if (updated != null) {
                syncManager.uploadTable(repository.currentUserId.value, updated)
            }
        }
    }

    /**
     * Inventory Management: Add stock, create item, edit
     */
    fun saveInventoryItem(item: InventoryItemEntity) {
        viewModelScope.launch {
            val existing = repository.inventoryDao.getItemById(item.id)
            repository.inventoryDao.insertOrUpdateItem(item)
            syncManager.uploadInventoryItem(repository.currentUserId.value, item)
            // If new item with opening stock, create initial opening batch
            if (existing == null && item.openingStock > 0.000001) {
                repository.addInventoryStockBatch(
                    itemId = item.id,
                    quantity = item.openingStock,
                    purchaseRate = item.purchasePrice,
                    notes = "Opening Stock",
                    batchType = "OPENING_STOCK"
                )
            }
        }
    }

    fun addStockToItem(itemId: String, itemName: String, quantity: Double, notes: String, purchaseRate: Double? = null) {
        viewModelScope.launch {
            repository.addInventoryStockBatch(itemId, quantity, purchaseRate, notes, "PURCHASE")
        }
    }

    fun refundBill(billId: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = repository.cancelBill(billId, "Refunded via bill history", restoreInventory = true)
            onComplete?.invoke(result)
        }
    }

    fun cancelBill(
        billId: String,
        reason: String,
        restoreInventory: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.cancelBill(billId, reason, restoreInventory)
            onComplete?.invoke(result)
        }
    }

    fun deleteBillPermanently(
        billId: String,
        restoreInventory: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.deleteBillPermanently(billId, restoreInventory)
            onComplete?.invoke(result)
        }
    }

    fun deleteInventoryItem(id: String) {
        viewModelScope.launch {
            try {
                val linkedBatches = repository.inventoryBatchDao.getAllBatchesDirect().filter { it.inventoryItemId == id }
                linkedBatches.forEach { batch ->
                    repository.inventoryBatchDao.deleteBatchById(batch.id)
                    syncManager.deleteBatch(repository.currentUserId.value, batch.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.inventoryDao.deleteItemById(id)
            syncManager.deleteInventoryItem(repository.currentUserId.value, id)
        }
    }

    fun deleteInventoryBatch(batchId: String) {
        viewModelScope.launch {
            val batch = repository.inventoryBatchDao.getBatchById(batchId) ?: return@launch
            val itemId = batch.inventoryItemId
            
            repository.inventoryBatchDao.deleteBatchById(batchId)
            syncManager.deleteBatch(repository.currentUserId.value, batchId)
            
            val item = repository.inventoryDao.getItemById(itemId)
            if (item != null) {
                val newStock = (item.currentStock - batch.remainingQuantity).coerceAtLeast(0.0)
                
                val activeBatches = repository.inventoryBatchDao.getActiveBatchesForItemFifo(itemId)
                val nextRate = activeBatches.firstOrNull()?.purchaseRate ?: item.purchasePrice
                
                val updatedItem = item.copy(
                    currentStock = newStock,
                    purchasePrice = nextRate,
                    updatedAt = System.currentTimeMillis()
                )
                repository.inventoryDao.insertOrUpdateItem(updatedItem)
                syncManager.uploadInventoryItem(repository.currentUserId.value, updatedItem)
            }
        }
    }

    fun editInventoryBatch(batchId: String, newQty: Double, newRate: Double) {
        viewModelScope.launch {
            val batch = repository.inventoryBatchDao.getBatchById(batchId) ?: return@launch
            val itemId = batch.inventoryItemId
            
            val qtyDifference = newQty - batch.initialQuantity
            val newRemainingQty = (batch.remainingQuantity + qtyDifference).coerceAtLeast(0.0)
            val isConsumed = newRemainingQty <= 0.000001
            val newStatus = if (isConsumed) "CONSUMED" else "ACTIVE"
            
            val updatedBatch = batch.copy(
                initialQuantity = newQty,
                remainingQuantity = newRemainingQty,
                purchaseRate = newRate,
                isConsumed = isConsumed,
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
            repository.inventoryBatchDao.insertBatch(updatedBatch)
            syncManager.uploadBatch(repository.currentUserId.value, updatedBatch)
            
            val item = repository.inventoryDao.getItemById(itemId)
            if (item != null) {
                val newStock = (item.currentStock + qtyDifference).coerceAtLeast(0.0)
                
                val activeBatches = repository.inventoryBatchDao.getActiveBatchesForItemFifo(itemId)
                val nextRate = activeBatches.firstOrNull()?.purchaseRate ?: newRate
                
                val updatedItem = item.copy(
                    currentStock = newStock,
                    purchasePrice = nextRate,
                    updatedAt = System.currentTimeMillis()
                )
                repository.inventoryDao.insertOrUpdateItem(updatedItem)
                syncManager.uploadInventoryItem(repository.currentUserId.value, updatedItem)
            }
        }
    }

    fun recordWastage(
        inventoryItemId: String,
        quantity: Double,
        reason: String,
        notes: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val item = repository.inventoryDao.getItemById(inventoryItemId) ?: return@launch
            val newStock = (item.currentStock - quantity).coerceAtLeast(0.0)
            val rate = item.purchasePrice
            
            // 1. Record stock transaction
            val txn = com.example.data.local.entity.StockTransactionEntity(
                id = UUID.randomUUID().toString(),
                restaurantId = item.restaurantId,
                inventoryItemId = item.id,
                itemName = item.name,
                transactionType = "WASTAGE",
                quantity = quantity,
                unitRate = rate,
                balanceAfter = newStock,
                notes = if (notes.isNotBlank()) "$reason: $notes" else reason,
                timestamp = timestamp
            )
            repository.stockTransactionDao.insert(txn)
            
            // 2. Update item stock
            val updatedItem = item.copy(
                currentStock = newStock,
                updatedAt = System.currentTimeMillis()
            )
            repository.inventoryDao.insertOrUpdateItem(updatedItem)
            syncManager.uploadInventoryItem(repository.currentUserId.value, updatedItem)
        }
    }

    fun recordMultiItemPurchase(
        restaurantId: String,
        purchasedItems: List<MultiItemPurchaseInput>,
        paymentMethod: String,
        paymentStatus: String,
        paidAmount: Double,
        dueAmount: Double,
        vendorName: String,
        invoiceNo: String,
        notes: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val totalBillAmount = purchasedItems.sumOf { it.quantity * it.rate }
            val itemsDescription = purchasedItems.joinToString(", ") { "${it.itemName} (${String.format(Locale.US, "%.2f", it.quantity).replace(".00", "")} ${it.unit} @ ₹${it.rate})" }
            
            // Build safe embedded GRN notes compatible with cloud sync and local db
            val grnNotes = listOf(
                if (invoiceNo.isNotBlank()) "GRN_INVOICE:$invoiceNo" else "",
                if (vendorName.isNotBlank()) "GRN_VENDOR:$vendorName" else "",
                "GRN_PAY_STATUS:$paymentStatus",
                "GRN_METHOD:$paymentMethod",
                if (notes.isNotBlank()) "GRN_USER_NOTES:$notes" else "",
                "Items: $itemsDescription"
            ).filter { it.isNotBlank() }.joinToString(" | ")

            val expenseAmount = if (paymentStatus == "UNPAID_CREDIT") 0.0 else paidAmount
            val expenseId = "grn_${UUID.randomUUID()}"
            
            if (expenseAmount > 0.0 && paymentMethod == "CASH") {
                val dateStr = com.example.util.DateUtils.formatDate(timestamp, "yyyy-MM-dd")
                val reg = repository.cashRegisterDao.getRegisterForDate(restaurantId, dateStr)
                if (reg != null) {
                    val updated = reg.copy(
                        cashExpenses = reg.cashExpenses + expenseAmount,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.cashRegisterDao.insertOrUpdate(updated)
                    syncManager.uploadCashRegister(repository.currentUserId.value, updated)
                }
            }

            purchasedItems.forEach { pItem ->
                repository.addInventoryStockBatch(
                    itemId = pItem.itemId,
                    quantity = pItem.quantity,
                    purchaseRate = pItem.rate,
                    notes = grnNotes,
                    batchType = "PURCHASE",
                    referenceId = expenseId,
                    customTimestamp = timestamp
                )
            }
        }
    }

    fun payGRN(batch: com.example.data.local.entity.InventoryBatchEntity, paymentMethod: String, amount: Double) {
        viewModelScope.launch {
            val updatedNotes = batch.notes
                .replace("GRN_PAY_STATUS:PENDING", "GRN_PAY_STATUS:FULLY_PAID")
                .replace("GRN_PAY_STATUS:UNPAID_CREDIT", "GRN_PAY_STATUS:FULLY_PAID")
                .replace("GRN_PAY_STATUS:PARTIALLY_PAID", "GRN_PAY_STATUS:FULLY_PAID")
                .replace("GRN_METHOD:CASH", "GRN_METHOD:$paymentMethod")
                .replace("GRN_METHOD:UPI", "GRN_METHOD:$paymentMethod")
            
            val updatedBatch = batch.copy(
                notes = updatedNotes,
                updatedAt = System.currentTimeMillis()
            )
            repository.inventoryBatchDao.updateBatch(updatedBatch)
            syncManager.uploadBatch(repository.currentUserId.value, updatedBatch)

            if (paymentMethod == "CASH" && amount > 0.0) {
                val dateStr = com.example.util.DateUtils.formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
                val reg = repository.cashRegisterDao.getRegisterForDate(batch.restaurantId, dateStr)
                if (reg != null) {
                    val updatedReg = reg.copy(
                        cashExpenses = reg.cashExpenses + amount,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.cashRegisterDao.insertOrUpdate(updatedReg)
                    syncManager.uploadCashRegister(repository.currentUserId.value, updatedReg)
                }
            }
        }
    }

    fun purgeOldTrialBatches(restaurantId: String) {
        viewModelScope.launch {
            val thresholdTime = 1727766000000L // Oct 1st, 2026, 00:00:00
            val allB = repository.inventoryBatchDao.getAllBatchesDirect()
            allB.forEach { b ->
                if (b.timestamp < thresholdTime) {
                    repository.inventoryBatchDao.deleteBatchById(b.id)
                    syncManager.deleteBatch(repository.currentUserId.value, b.id)
                }
            }
            
            // Re-fetch items and recalculate current stock based on active batches only
            val items = repository.inventoryDao.getAllInventoryDirect()
            for (item in items) {
                val activeBatches = repository.inventoryBatchDao.getActiveBatchesForItemFifo(item.id)
                val newCurrentStock = activeBatches.sumOf { it.remainingQuantity }
                val updated = item.copy(
                    currentStock = newCurrentStock,
                    updatedAt = System.currentTimeMillis()
                )
                repository.inventoryDao.insertOrUpdateItem(updated)
                syncManager.uploadInventoryItem(repository.currentUserId.value, updated)
            }
        }
    }

    /**
     * Expenses Management & Custom Categories
     */
    fun saveExpenseCategory(name: String) {
        viewModelScope.launch {
            val cat = ExpenseCategoryEntity(
                id = "expcat_${name.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}",
                name = name
            )
            repository.expenseDao.insertCategory(cat)
            syncManager.uploadExpenseCategory(repository.currentUserId.value, cat)
        }
    }

    fun deleteExpenseCategory(id: String) {
        viewModelScope.launch {
            repository.expenseDao.deleteCategoryById(id)
            syncManager.deleteExpenseCategory(repository.currentUserId.value, id)
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            // Find any stock batches linked to this expense and delete them to revert stock!
            try {
                val linkedBatches = repository.inventoryBatchDao.getAllBatchesDirect().filter { it.referenceTransactionId == id }
                linkedBatches.forEach { batch ->
                    val itemId = batch.inventoryItemId
                    repository.inventoryBatchDao.deleteBatchById(batch.id)
                    syncManager.deleteBatch(repository.currentUserId.value, batch.id)
                    
                    val item = repository.inventoryDao.getItemById(itemId)
                    if (item != null) {
                        val newStock = (item.currentStock - batch.remainingQuantity).coerceAtLeast(0.0)
                        val activeBatches = repository.inventoryBatchDao.getActiveBatchesForItemFifo(itemId)
                        val nextRate = activeBatches.firstOrNull()?.purchaseRate ?: item.purchasePrice
                        val updatedItem = item.copy(
                            currentStock = newStock,
                            purchasePrice = nextRate,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.inventoryDao.insertOrUpdateItem(updatedItem)
                        syncManager.uploadInventoryItem(repository.currentUserId.value, updatedItem)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Revert Cash Register expense impact if paid via Cash
            try {
                val exp = repository.expenseDao.getExpenseById(id)
                if (exp != null && exp.paymentMethod == "CASH") {
                    val dateStr = com.example.util.DateUtils.formatDate(exp.timestamp, "yyyy-MM-dd")
                    val reg = repository.cashRegisterDao.getRegisterForDate(exp.restaurantId, dateStr)
                    if (reg != null) {
                        val updated = reg.copy(
                            cashExpenses = (reg.cashExpenses - exp.amount).coerceAtLeast(0.0),
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.cashRegisterDao.insertOrUpdate(updated)
                        syncManager.uploadCashRegister(repository.currentUserId.value, updated)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            repository.expenseDao.deleteById(id)
            syncManager.deleteExpense(repository.currentUserId.value, id)
        }
    }

    fun editExpense(
        id: String,
        categoryId: String,
        categoryName: String,
        amount: Double,
        paymentMethod: String,
        description: String,
        timestamp: Long
    ) {
        viewModelScope.launch {
            val existing = repository.expenseDao.getExpenseById(id) ?: return@launch
            val oldAmount = existing.amount
            val oldMethod = existing.paymentMethod
            
            val updated = existing.copy(
                categoryId = categoryId,
                categoryName = categoryName,
                amount = amount,
                paymentMethod = paymentMethod,
                description = description,
                timestamp = timestamp,
                updatedAt = System.currentTimeMillis()
            )
            repository.expenseDao.insertOrUpdate(updated)
            syncManager.uploadExpense(repository.currentUserId.value, updated)
            
            // Adjust Cash Register if paymentMethod or amount changed
            try {
                val dateStr = com.example.util.DateUtils.formatDate(timestamp, "yyyy-MM-dd")
                val reg = repository.cashRegisterDao.getRegisterForDate(existing.restaurantId, dateStr)
                if (reg != null) {
                    var cashExp = reg.cashExpenses
                    if (oldMethod == "CASH") {
                        cashExp -= oldAmount
                    }
                    if (paymentMethod == "CASH") {
                        cashExp += amount
                    }
                    val updatedReg = reg.copy(
                        cashExpenses = cashExp.coerceAtLeast(0.0),
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.cashRegisterDao.insertOrUpdate(updatedReg)
                    syncManager.uploadCashRegister(repository.currentUserId.value, updatedReg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveExpense(
        restaurantId: String,
        categoryId: String,
        categoryName: String,
        amount: Double,
        paymentMethod: String,
        description: String,
        inventoryItemId: String? = null,
        quantity: Double? = null,
        ratePerUnit: Double? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            if (!inventoryItemId.isNullOrBlank() && quantity != null && quantity > 0.0) {
                repository.recordExpenseWithInventoryPurchase(
                    restaurantId = restaurantId,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    description = description,
                    inventoryItemId = inventoryItemId,
                    quantity = quantity,
                    ratePerUnit = ratePerUnit,
                    timestamp = timestamp
                )
            } else {
                val exp = ExpenseEntity(
                    id = "exp_${UUID.randomUUID()}",
                    restaurantId = restaurantId,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    description = description,
                    timestamp = timestamp
                )
                repository.expenseDao.insertOrUpdate(exp)

                // If paid in cash and for today, update daily cash register
                if (paymentMethod == "CASH") {
                    val dateStr = com.example.util.DateUtils.formatDate(timestamp, "yyyy-MM-dd")
                    val reg = repository.cashRegisterDao.getRegisterForDate(restaurantId, dateStr)
                    if (reg != null) {
                        val updated = reg.copy(
                            cashExpenses = reg.cashExpenses + amount,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.cashRegisterDao.insertOrUpdate(updated)
                        syncManager.uploadCashRegister(repository.currentUserId.value, updated)
                    }
                }

                syncManager.uploadExpense(repository.currentUserId.value, exp)
            }
        }
    }

    /**
     * Cash Register: Open & Close
     */
    fun openCashRegister(openingFloat: Double, notes: String) {
        val rest = activeRestaurant.value ?: return
        val todayStr = com.example.util.DateUtils.getTodayDateString()
        viewModelScope.launch {
            val reg = CashRegisterEntity(
                id = "reg_${rest.id}_$todayStr",
                restaurantId = rest.id,
                dateString = todayStr,
                openingCash = openingFloat,
                cashSales = 0.0,
                cashExpenses = 0.0,
                cashAdded = 0.0,
                cashWithdrawn = 0.0,
                status = "OPEN",
                notes = notes
            )
            repository.cashRegisterDao.insertOrUpdate(reg)
            syncManager.uploadCashRegister(repository.currentUserId.value, reg)
        }
    }

    fun closeCashRegister(registerId: String, actualCashInDrawer: Double, notes: String) {
        viewModelScope.launch {
            val existing = repository.cashRegisterDao.getRegisterById(registerId) ?: return@launch
            val expected = existing.openingCash + existing.cashSales - existing.cashExpenses + existing.cashAdded - existing.cashWithdrawn
            val diff = actualCashInDrawer - expected
            val closed = existing.copy(
                closingCashActual = actualCashInDrawer,
                difference = diff,
                status = "CLOSED",
                closedAt = System.currentTimeMillis(),
                notes = if (notes.isNotBlank()) "${existing.notes}\nClosing: $notes" else existing.notes,
                updatedAt = System.currentTimeMillis()
            )
            repository.cashRegisterDao.insertOrUpdate(closed)
            syncManager.uploadCashRegister(repository.currentUserId.value, closed)
        }
    }

    /**
     * Cloud Sync & Restore
     */
    fun triggerCloudSync() {
        viewModelScope.launch {
            repository.syncAllNow()
        }
    }

    fun triggerCloudRestore() {
        viewModelScope.launch {
            repository.restoreAllNow()
        }
    }

    /**
     * Daily Stock Count & Variance Report
     */
    fun performStockCount(
        countedQuantities: Map<String, Double>,
        notes: String = "",
        onComplete: (Result<StockCountEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.performStockCountAdjustment(countedQuantities, notes)
            onComplete(result)
        }
    }

    /**
     * Recipe & Inventory item operations
     */
    fun saveRecipe(recipe: RecipeEntity) {
        viewModelScope.launch {
            repository.saveRecipe(recipe)
        }
    }

    fun deleteRecipe(menuItemId: String) {
        viewModelScope.launch {
            repository.deleteRecipe(menuItemId)
        }
    }

    /**
     * Generate A4 PDF invoice File
     */
    fun generateInvoicePdfFile(bill: BillEntity, previousDue: Double = 0.0): File? {
        val rest = activeRestaurant.value 
            ?: kotlinx.coroutines.runBlocking { repository.getActiveRestaurantOrFirst() } 
            ?: return null
        return repository.generateInvoicePdf(bill, rest, previousDue)
    }

    /**
     * Generate Receipt Image (PNG) based on restaurant's billFormat (Point 5 Elegant, A4, 2-inch, or 3-inch)
     */
    fun generateBillImageFile(bill: BillEntity, previousDue: Double = 0.0): File? {
        val rest = activeRestaurant.value 
            ?: kotlinx.coroutines.runBlocking { repository.getActiveRestaurantOrFirst() } 
            ?: return null
        return repository.generateBillImage(bill, rest, previousDue)
    }

    /**
     * Update active restaurant bill format ("A4", "THERMAL_3_INCH", "THERMAL_2_INCH", etc.)
     */
    fun updateBillFormat(billFormat: String) {
        val normalizedFormat = com.example.ui.components.BillTemplateDesign.fromId(billFormat).id
        viewModelScope.launch {
            val current = activeRestaurant.value 
                ?: repository.getActiveRestaurantOrFirst()
            if (current != null) {
                val updated = current.copy(billFormat = normalizedFormat, updatedAt = System.currentTimeMillis())
                repository.restaurantDao.insertOrUpdate(updated)
                syncManager.uploadRestaurant(repository.currentUserId.value, updated)
            }
        }
    }

    /**
     * Update active restaurant custom bill prefix (e.g., "INV", "BILL", "CAFE").
     */
    fun updateBillPrefix(prefix: String) {
        val cleanPrefix = prefix.trim().uppercase()
        viewModelScope.launch {
            val current = activeRestaurant.value ?: repository.getActiveRestaurantOrFirst()
            if (current != null) {
                val updated = current.copy(billPrefix = cleanPrefix, updatedAt = System.currentTimeMillis())
                repository.restaurantDao.insertOrUpdate(updated)
                syncManager.uploadRestaurant(repository.currentUserId.value, updated)
            }
        }
    }

    /**
     * Set/Reset the next bill sequence number for a prefix.
     */
    fun setBillPrefixCounter(prefix: String, nextNumber: Int) {
        val current = activeRestaurant.value ?: return
        viewModelScope.launch {
            repository.setBillPrefixCounter(current.id, prefix, nextNumber)
        }
    }

    /**
     * Native Google Sign-In with Android Credential Manager & Firebase Authentication.
     * Shows the system Google account picker dialog, authenticates to real Firebase UID,
     * and automatically restores all cloud-synced data for this account (Canva-like sync).
     */
    fun signInWithGoogleNative(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authManager.signInWithGoogleNative(context)
            if (result.isSuccess) {
                // 1. Completely WIPE the entire local Room database before restoring this account
                repository.clearAllLocalData()
                _cartState.value = CartState()
                _settledBill.value = null

                // 2. Fetch and restore ONLY that account's own data from pos_users/{thatAccountUid}/... in Firestore
                repository.restoreAllNow()
                repository.startRealtimeSync()
                onSuccess()
            } else {
                val exception = result.exceptionOrNull()
                if (exception is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    // User closed the account picker popup; dismiss silently
                    onError("CANCELLED")
                } else {
                    onError(exception?.localizedMessage ?: "Google Sign-In failed")
                }
            }
        }
    }

    /**
     * Guest / Local Mode Sign-In with Firebase Anonymous Authentication.
     * Instantly authenticates a local session without requiring Google OAuth setup.
     */
    fun signInAsGuest(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authManager.signInAsGuest()
            if (result.isSuccess) {
                repository.clearAllLocalData()
                _cartState.value = CartState()
                _settledBill.value = null
                repository.restoreAllNow()
                repository.startRealtimeSync()
                onSuccess()
            } else {
                val exception = result.exceptionOrNull()
                onError(exception?.localizedMessage ?: "Guest Sign-In failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Stop realtime listeners
            repository.stopRealtimeSync()
            // 1. Wipe all local Room tables completely (bills, customers, inventory, menu, tables, expenses, registers, restaurants)
            repository.clearAllLocalData()
            // 2. Clear in-memory cart and settled bill state
            _cartState.value = CartState()
            _settledBill.value = null
            // 3. Clear auth credentials & SharedPreferences
            authManager.signOut()
        }
    }

    fun collectCustomerDue(
        customerName: String,
        customerPhone: String,
        amount: Double,
        paymentMode: String,
        notes: String = "",
        onComplete: (Boolean, CustomerPaymentEntity?, Double, Double, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val previousDue = getCustomerOutstandingCredit(customerName, customerPhone)
                val customer = allCustomers.value.find {
                    (customerPhone.isNotBlank() && it.contactNumber.trim() == customerPhone.trim()) ||
                    (customerName.isNotBlank() && it.name.trim().equals(customerName.trim(), ignoreCase = true))
                }
                val customerId = customer?.id ?: "CUST-${System.currentTimeMillis().toString().takeLast(6)}"

                // 1. Record CustomerPaymentEntity in Room + Cash Register (if CASH) + Firestore
                val payment = repository.recordCustomerPayment(
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    amount = amount,
                    paymentMode = paymentMode,
                    notes = notes
                )

                val remainingDue = (previousDue - amount).coerceAtLeast(0.0)
                onComplete(true, payment, previousDue, remainingDue, "Payment of ₹$amount collected successfully via $paymentMode")
            } catch (e: Exception) {
                onComplete(false, null, 0.0, 0.0, "Failed to collect payment: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Register or edit a customer with optional Birthday field
     */
    fun registerOrUpdateCustomer(
        id: String? = null,
        name: String,
        phone: String,
        birthday: String? = null,
        isEnrolledInLoyalty: Boolean? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = if (!id.isNullOrBlank()) {
                repository.customerDao.getCustomerById(id)
            } else {
                repository.customerDao.getCustomerByContact(phone.trim())
            }

            val finalEnroll = isEnrolledInLoyalty ?: existing?.isEnrolledInLoyalty ?: pointsEngineRules.value.autoEnrollInVisitPass

            val updated = CustomerEntity(
                id = existing?.id ?: id ?: "cust_${System.currentTimeMillis()}",
                name = name.trim(),
                contactNumber = phone.trim(),
                loyaltyVisitCount = existing?.loyaltyVisitCount ?: 0,
                lastVisitTimestamp = existing?.lastVisitTimestamp,
                loyaltyHistoryJson = existing?.loyaltyHistoryJson ?: "[]",
                rewardPointsBalance = existing?.rewardPointsBalance ?: 0,
                giftPointsBalance = existing?.giftPointsBalance ?: 0,
                isEnrolledInLoyalty = finalEnroll,
                birthday = birthday?.trim()?.ifBlank { null } ?: existing?.birthday,
                updatedAt = System.currentTimeMillis()
            )

            repository.customerDao.insertOrUpdate(updated)
            syncManager.uploadCustomer(repository.currentUserId.value, updated)
            onDone()
        }
    }

    // Add-on Management Operations
    fun saveAddonDefinition(addon: AddonDefinitionEntity) {
        viewModelScope.launch {
            repository.saveAddonDefinition(addon)
        }
    }

    fun deactivateAddonDefinition(addonId: String) {
        viewModelScope.launch {
            repository.deactivateAddonDefinition(addonId)
        }
    }

    fun deleteAddonDefinition(addonId: String) {
        viewModelScope.launch {
            repository.deleteAddonDefinition(addonId)
        }
    }

    fun updateCategoryDefaultAddons(categoryId: String, defaultAddonIds: List<String>) {
        viewModelScope.launch {
            repository.updateCategoryDefaultAddons(categoryId, defaultAddonIds)
        }
    }

    fun updateMenuItemAddonOverrides(menuItemId: String, excludedAddonIds: List<String>, itemAddonIds: List<String>) {
        viewModelScope.launch {
            repository.updateMenuItemAddonOverrides(menuItemId, excludedAddonIds, itemAddonIds)
        }
    }
}

data class MultiItemPurchaseInput(
    val itemId: String,
    val itemName: String,
    val quantity: Double,
    val rate: Double,
    val unit: String
)
