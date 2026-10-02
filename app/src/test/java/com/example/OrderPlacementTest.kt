package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.auth.AuthManager
import com.example.data.cloud.FirestoreSyncManager
import com.example.data.local.database.CafePosDatabase
import com.example.data.local.entity.*
import com.example.data.repository.PosRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrderPlacementTest {

    private lateinit var db: CafePosDatabase
    private lateinit var context: Context
    private lateinit var repository: PosRepository
    private lateinit var authManager: AuthManager
    private lateinit var syncManager: FirestoreSyncManager

    private val restaurantId = "test_rest_1"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.cafepos.bkrboy")
                .setProjectId("dummy-test")
                .setApiKey("fakeApiKey12345")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
        db = Room.inMemoryDatabaseBuilder(context, CafePosDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        authManager = AuthManager(context)
        syncManager = FirestoreSyncManager(context)
        repository = PosRepository(context, db, authManager, syncManager)

        // Seed restaurant
        val rest = RestaurantEntity(
            id = restaurantId,
            name = "Test Cafe",
            address = "Test Street",
            phone = "1234567890",
            isActive = true
        )
        db.restaurantDao().insertOrUpdate(rest)

        // Seed table
        val table = CafeTableEntity(
            id = "table_1",
            restaurantId = restaurantId,
            name = "Table 1",
            capacity = 4,
            isOccupied = false
        )
        db.cafeTableDao().insertOrUpdate(table)

        // Seed Inventory
        val invCoffeeBeans = InventoryItemEntity(
            id = "inv_coffee",
            restaurantId = restaurantId,
            name = "Coffee Beans",
            unit = "gm",
            openingStock = 1000.0,
            currentStock = 1000.0,
            lowStockThreshold = 50.0
        )
        val invMilk = InventoryItemEntity(
            id = "inv_milk",
            restaurantId = restaurantId,
            name = "Milk",
            unit = "ml",
            openingStock = 5000.0,
            currentStock = 5000.0,
            lowStockThreshold = 200.0
        )
        val invFlour = InventoryItemEntity(
            id = "inv_flour",
            restaurantId = restaurantId,
            name = "Flour",
            unit = "gm",
            openingStock = 2000.0,
            currentStock = 2000.0,
            lowStockThreshold = 100.0
        )
        db.inventoryDao().insertOrUpdateItem(invCoffeeBeans)
        db.inventoryDao().insertOrUpdateItem(invMilk)
        db.inventoryDao().insertOrUpdateItem(invFlour)

        // Seed Menu items
        val coffee = MenuItemEntity(
            id = "dish_coffee",
            categoryId = "cat_bev",
            categoryName = "Beverages",
            name = "Cappuccino",
            price = 150.0,
            isAvailable = true
        )
        val croissant = MenuItemEntity(
            id = "dish_croissant",
            categoryId = "cat_bakery",
            categoryName = "Bakery",
            name = "Butter Croissant",
            price = 120.0,
            isAvailable = true
        )
        db.menuItemDao().insertOrUpdate(coffee)
        db.menuItemDao().insertOrUpdate(croissant)

        // Seed Recipes
        val coffeeRecipe = RecipeEntity(
            menuItemId = "dish_coffee",
            restaurantId = restaurantId,
            menuItemName = "Cappuccino",
            ingredients = listOf(
                RecipeIngredient("inv_coffee", "Coffee Beans", 18.0, "gm"),
                RecipeIngredient("inv_milk", "Milk", 150.0, "ml")
            )
        )
        val croissantRecipe = RecipeEntity(
            menuItemId = "dish_croissant",
            restaurantId = restaurantId,
            menuItemName = "Butter Croissant",
            ingredients = listOf(
                RecipeIngredient("inv_flour", "Flour", 100.0, "gm")
            )
        )
        db.recipeDao().insertOrUpdate(coffeeRecipe)
        db.recipeDao().insertOrUpdate(croissantRecipe)

        // Seed Combo
        val breakfastCombo = ComboEntity(
            id = "combo_breakfast",
            restaurantId = restaurantId,
            name = "Breakfast Combo",
            description = "Coffee + Croissant",
            price = 220.0,
            slots = listOf(
                ComboSlot(id = "slot_bev", label = "Pick 1 Drink", categoryIds = listOf("cat_bev"), quantity = 1),
                ComboSlot(id = "slot_bake", label = "Pick 1 Pastry", categoryIds = listOf("cat_bakery"), quantity = 1)
            ),
            isActive = true
        )
        db.comboDao().insertOrUpdate(breakfastCombo)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testRegularOrderSendToKitchenAndCashOut() = runBlocking {
        val items = listOf(
            BillItem(
                dishId = "dish_coffee",
                dishName = "Cappuccino",
                unitPrice = 150.0,
                quantity = 1,
                totalPrice = 150.0,
                notes = ""
            )
        )

        val bill = repository.sendOrderToKitchen(
            restaurantId = restaurantId,
            restaurantName = "Test Cafe",
            orderType = "DINE_IN",
            tableId = "table_1",
            tableName = "Table 1",
            customerName = "John",
            customerPhone = "9999999999",
            items = items
        )

        assertNotNull(bill)
        assertEquals(150.0, bill.totalAmount, 0.01)
        assertFalse(bill.isSettled)

        // Verify table is marked occupied
        val table = db.cafeTableDao().getTableById("table_1")
        assertNotNull(table)
        assertTrue(table!!.isOccupied)
        assertEquals(bill.id, table.activeBillId)

        // Verify stock deducted for coffee: 1000 - (18gm * 0.001) = 999.982
        val coffeeStock = db.inventoryDao().getItemById("inv_coffee")
        assertEquals(999.982, coffeeStock!!.currentStock, 0.001)

        // Now cash out active bill
        val settled = repository.cashOutActiveBill(
            billId = bill.id,
            paymentMethod = "CASH",
            discountType = "NONE",
            discountValue = 0.0
        )

        assertTrue(settled.isSettled)
        val tableAfterSettle = db.cafeTableDao().getTableById("table_1")
        assertFalse(tableAfterSettle!!.isOccupied)
        assertNull(tableAfterSettle.activeBillId)
    }

    @Test
    fun testComboOrderSendToKitchenAndCashOut() = runBlocking {
        val comboNotes = "Cappuccino, Butter Croissant [DISHES:dish_coffee=1;dish_croissant=1]"
        val items = listOf(
            BillItem(
                dishId = "combo_breakfast_${UUID.randomUUID().toString().take(6)}",
                dishName = "Breakfast Combo",
                unitPrice = 220.0,
                quantity = 1,
                totalPrice = 220.0,
                notes = comboNotes
            )
        )

        val bill = repository.sendOrderToKitchen(
            restaurantId = restaurantId,
            restaurantName = "Test Cafe",
            orderType = "DINE_IN",
            tableId = "table_1",
            tableName = "Table 1",
            customerName = "Jane",
            customerPhone = "8888888888",
            items = items
        )

        assertNotNull(bill)
        assertEquals(220.0, bill.totalAmount, 0.01)

        // Verify stock deducted for BOTH items in combo:
        // coffee beans: 1000 - 0.018 = 999.982
        val coffeeStock = db.inventoryDao().getItemById("inv_coffee")
        assertEquals(999.982, coffeeStock!!.currentStock, 0.001)
        // milk: 5000 - 0.15 = 4999.85
        val milkStock = db.inventoryDao().getItemById("inv_milk")
        assertEquals(4999.85, milkStock!!.currentStock, 0.001)
        // flour: 2000 - 0.1 = 1999.9
        val flourStock = db.inventoryDao().getItemById("inv_flour")
        assertEquals(1999.9, flourStock!!.currentStock, 0.001)

        // Settle bill
        val settled = repository.cashOutActiveBill(
            billId = bill.id,
            paymentMethod = "UPI",
            discountType = "NONE",
            discountValue = 0.0
        )
        assertTrue(settled.isSettled)
    }

    @Test
    fun testComboDirectSettleCashOut() = runBlocking {
        val comboNotes = "Cappuccino, Butter Croissant [DISHES:dish_coffee=1;dish_croissant=1]"
        val items = listOf(
            BillItem(
                dishId = "combo_breakfast_direct",
                dishName = "Breakfast Combo",
                unitPrice = 220.0,
                quantity = 1,
                totalPrice = 220.0,
                notes = comboNotes
            )
        )

        val settled = repository.createAndSettleBill(
            restaurantId = restaurantId,
            restaurantName = "Test Cafe",
            orderType = "TAKEAWAY",
            tableId = null,
            tableName = null,
            customerName = "Walk-in",
            customerPhone = "7777777777",
            items = items,
            discountType = "NONE",
            discountValue = 0.0,
            paymentMethod = "CASH"
        )

        assertNotNull(settled)
        assertTrue(settled.isSettled)
        assertEquals(220.0, settled.totalAmount, 0.01)
    }

    @Test
    fun testTakeawayOrder_SendToKitchen_And_CashOut_RemovesFromActiveUnsettled() = runBlocking {
        val items = listOf(
            BillItem(
                dishId = "dish_coffee",
                dishName = "Cappuccino",
                unitPrice = 120.0,
                quantity = 2,
                totalPrice = 240.0
            )
        )

        // 1. Send takeaway order to kitchen
        val order = repository.sendOrderToKitchen(
            restaurantId = restaurantId,
            restaurantName = "Test Cafe",
            orderType = "TAKEAWAY",
            tableId = null,
            tableName = null,
            customerName = "Rohan Sharma",
            customerPhone = "9876543210",
            items = items
        )

        assertNotNull(order)
        assertEquals("TAKEAWAY", order.orderType)
        assertFalse(order.isSettled)

        // 2. Verify it is in active unsettled bills
        val activeBillsBefore = db.billDao().getActiveUnsettledBills()
        val directUnsettledBefore = db.billDao().getAllBillsDirect().filter { !it.isSettled }
        assertTrue(directUnsettledBefore.any { it.id == order.id && it.orderType == "TAKEAWAY" })

        // 3. Cash Out the active takeaway bill (CASH)
        val settled = repository.cashOutActiveBill(
            billId = order.id,
            paymentMethod = "CASH",
            discountType = "NONE",
            discountValue = 0.0
        )

        assertTrue(settled.isSettled)
        assertEquals("CASH", settled.paymentMethod)
        assertEquals(240.0, settled.totalAmount, 0.01)

        // 4. Verify it is NO LONGER in active unsettled bills
        val directUnsettledAfter = db.billDao().getAllBillsDirect().filter { !it.isSettled }
        assertFalse("Cashed out takeaway order must NOT be in active unsettled list",
            directUnsettledAfter.any { it.id == order.id })

        // 5. Verify it appears correctly in all bills history
        val allBills = db.billDao().getAllBillsDirect()
        val historyBill = allBills.find { it.id == order.id }
        assertNotNull("Cashed out takeaway order must exist in bill history", historyBill)
        assertTrue(historyBill!!.isSettled)
        assertEquals("CASH", historyBill.paymentMethod)
        assertEquals("Rohan Sharma", historyBill.customerName)
        assertEquals(240.0, historyBill.totalAmount, 0.01)
    }

    @Test
    fun testTakeawayOrder_UPI_CashOut() = runBlocking {
        val items = listOf(
            BillItem(
                dishId = "dish_croissant",
                dishName = "Butter Croissant",
                unitPrice = 80.0,
                quantity = 1,
                totalPrice = 80.0
            )
        )

        // 1. Send takeaway order to kitchen
        val order = repository.sendOrderToKitchen(
            restaurantId = restaurantId,
            restaurantName = "Test Cafe",
            orderType = "TAKEAWAY",
            customerName = "Priya",
            customerPhone = "9123456780",
            items = items
        )
        assertFalse(order.isSettled)

        // 2. Cash Out via UPI
        val settled = repository.cashOutActiveBill(
            billId = order.id,
            paymentMethod = "UPI",
            discountType = "FLAT",
            discountValue = 10.0
        )

        assertTrue(settled.isSettled)
        assertEquals("UPI", settled.paymentMethod)
        assertEquals(70.0, settled.totalAmount, 0.01)

        // 3. Verify removed from unsettled
        val unsettled = db.billDao().getAllBillsDirect().filter { !it.isSettled }
        assertFalse(unsettled.any { it.id == order.id })
    }
}
