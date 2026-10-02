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
class FifoInventoryTest {

    private lateinit var db: CafePosDatabase
    private lateinit var context: Context
    private lateinit var repository: PosRepository
    private lateinit var authManager: AuthManager
    private lateinit var syncManager: FirestoreSyncManager

    private val restaurantId = "rest_fifo_test"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.cafepos.bkrboy")
                .setProjectId("dummy-fifo-test")
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
        db.restaurantDao().insertOrUpdate(
            RestaurantEntity(
                id = restaurantId,
                name = "FIFO Cafe",
                address = "123 Market St",
                phone = "9876543210",
                isActive = true
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFifoConsumptionAcrossMultipleBatches() = runBlocking {
        // 1. Create inventory item (e.g. Cheese, unit: kg)
        val cheese = InventoryItemEntity(
            id = "inv_cheese",
            restaurantId = restaurantId,
            name = "Cheese",
            unit = "kg",
            openingStock = 0.0,
            currentStock = 0.0,
            purchasePrice = 100.0,
            lowStockThreshold = 2.0
        )
        db.inventoryDao().insertOrUpdateItem(cheese)

        // 2. Add Batch 1 (older, 10 kg @ 100/kg)
        repository.addInventoryStockBatch(
            itemId = "inv_cheese",
            quantity = 10.0,
            purchaseRate = 100.0,
            notes = "Batch 1 - Older"
        )
        // Ensure distinct timestamp
        kotlinx.coroutines.delay(20)

        // 3. Add Batch 2 (newer, 10 kg @ 120/kg)
        repository.addInventoryStockBatch(
            itemId = "inv_cheese",
            quantity = 10.0,
            purchaseRate = 120.0,
            notes = "Batch 2 - Newer"
        )

        // Verify initial batches
        val batches = repository.inventoryBatchDao.getActiveBatchesForItemFifo("inv_cheese")
        assertEquals(2, batches.size)
        assertEquals(10.0, batches[0].remainingQuantity, 0.001)
        assertEquals(100.0, batches[0].purchaseRate, 0.001)
        assertEquals(10.0, batches[1].remainingQuantity, 0.001)
        assertEquals(120.0, batches[1].purchaseRate, 0.001)

        // 4. Create Dish & Recipe requiring 15 kg cheese
        val dish = MenuItemEntity(
            id = "dish_pizza",
            categoryId = "cat_1",
            categoryName = "Pizzas",
            name = "Mega Pizza",
            price = 500.0,
            isAvailable = true
        )
        db.menuItemDao().insertOrUpdate(dish)

        val recipe = RecipeEntity(
            restaurantId = restaurantId,
            menuItemId = "dish_pizza",
            menuItemName = "Mega Pizza",
            ingredients = listOf(
                RecipeIngredient(
                    inventoryItemId = "inv_cheese",
                    inventoryItemName = "Cheese",
                    quantity = 15.0,
                    usageUnit = "kg"
                )
            )
        )
        db.recipeDao().insertOrUpdate(recipe)

        // 5. Place bill for 1 Mega Pizza
        val billItem = BillItem(
            dishId = "dish_pizza",
            dishName = "Mega Pizza",
            unitPrice = 500.0,
            quantity = 1,
            totalPrice = 500.0,
            notes = ""
        )
        val bill = repository.createAndSettleBill(
            restaurantId = restaurantId,
            restaurantName = "FIFO Cafe",
            orderType = "DINE_IN",
            customerName = "John",
            customerPhone = "1234567890",
            items = listOf(billItem),
            discountType = "NONE",
            discountValue = 0.0,
            paymentMethod = "CASH"
        )

        // Expected Cost: (10 * 100) + (5 * 120) = 1000 + 600 = 1600.0
        assertEquals(1600.0, bill.totalFoodCost, 0.01)

        // Verify remaining batches
        val updatedBatches = repository.inventoryBatchDao.getBatchesForItemDirect("inv_cheese")
        val batch1 = updatedBatches.find { it.id == batches[0].id }!!
        val batch2 = updatedBatches.find { it.id == batches[1].id }!!

        // Batch 1 should be completely consumed and ARCHIVED
        assertEquals(0.0, batch1.remainingQuantity, 0.001)
        assertTrue(batch1.isConsumed)
        assertEquals("ARCHIVED", batch1.status)

        // Batch 2 should have 5 kg remaining and ACTIVE
        assertEquals(5.0, batch2.remainingQuantity, 0.001)
        assertFalse(batch2.isConsumed)
        assertEquals("ACTIVE", batch2.status)

        // Current stock in item should be 5 kg
        val updatedCheese = repository.inventoryDao.getItemById("inv_cheese")!!
        assertEquals(5.0, updatedCheese.currentStock, 0.001)

        // Verify deductions recorded
        val deductions = repository.inventoryBatchDao.getDeductionsForBill(bill.id)
        assertEquals(2, deductions.size)
        assertEquals(10.0, deductions[0].quantityDeducted, 0.001)
        assertEquals(100.0, deductions[0].rate, 0.001)
        assertEquals(5.0, deductions[1].quantityDeducted, 0.001)
        assertEquals(120.0, deductions[1].rate, 0.001)
    }

    @Test
    fun testNonBlockingNegativeStock() = runBlocking {
        // 1. Inventory item with 0 stock
        val tomato = InventoryItemEntity(
            id = "inv_tomato",
            restaurantId = restaurantId,
            name = "Tomato",
            unit = "kg",
            openingStock = 0.0,
            currentStock = 0.0,
            purchasePrice = 40.0,
            lowStockThreshold = 1.0
        )
        db.inventoryDao().insertOrUpdateItem(tomato)

        // 2. Dish requiring 3 kg tomato
        val dish = MenuItemEntity(
            id = "dish_soup",
            categoryId = "cat_1",
            categoryName = "Soups",
            name = "Tomato Soup",
            price = 150.0,
            isAvailable = true
        )
        db.menuItemDao().insertOrUpdate(dish)
        db.recipeDao().insertOrUpdate(
            RecipeEntity(
                restaurantId = restaurantId,
                menuItemId = "dish_soup",
                menuItemName = "Tomato Soup",
                ingredients = listOf(
                    RecipeIngredient(
                        inventoryItemId = "inv_tomato",
                        inventoryItemName = "Tomato",
                        quantity = 3.0,
                        usageUnit = "kg"
                    )
                )
            )
        )

        // 3. Bill should succeed without blocking
        val bill = repository.createAndSettleBill(
            restaurantId = restaurantId,
            restaurantName = "FIFO Cafe",
            orderType = "DINE_IN",
            customerName = "Jane",
            customerPhone = "9999999999",
            items = listOf(BillItem(dishId = "dish_soup", dishName = "Tomato Soup", unitPrice = 150.0, quantity = 1, totalPrice = 150.0, notes = "")),
            discountType = "NONE",
            discountValue = 0.0,
            paymentMethod = "CASH"
        )

        // Cost: 3 * 40.0 = 120.0
        assertEquals(120.0, bill.totalFoodCost, 0.01)

        // Stock goes negative
        val itemAfter = repository.inventoryDao.getItemById("inv_tomato")!!
        assertEquals(-3.0, itemAfter.currentStock, 0.001)

        // Deduction marked as negative stock
        val deductions = repository.inventoryBatchDao.getDeductionsForBill(bill.id)
        assertEquals(1, deductions.size)
        assertTrue(deductions[0].isNegativeStock)
        assertNull(deductions[0].batchId)
        assertEquals(3.0, deductions[0].quantityDeducted, 0.001)

        // 4. Now add new stock of 10 kg (Requirement 7: Deficit settlement)
        repository.addInventoryStockBatch(
            itemId = "inv_tomato",
            quantity = 10.0,
            purchaseRate = 50.0,
            notes = "Restock"
        )

        val itemRestocked = repository.inventoryDao.getItemById("inv_tomato")!!
        assertEquals(7.0, itemRestocked.currentStock, 0.001)

        val latestBatches = repository.inventoryBatchDao.getActiveBatchesForItemFifo("inv_tomato")
        assertEquals(1, latestBatches.size)
        // 3 kg settled deficit, 7 kg remaining available
        assertEquals(7.0, latestBatches[0].remainingQuantity, 0.001)
        assertEquals(10.0, latestBatches[0].initialQuantity, 0.001)
    }

    @Test
    fun testRefundRestoresExactBatches() = runBlocking {
        // 1. Inventory item
        val butter = InventoryItemEntity(
            id = "inv_butter",
            restaurantId = restaurantId,
            name = "Butter",
            unit = "kg",
            openingStock = 0.0,
            currentStock = 0.0,
            purchasePrice = 200.0,
            lowStockThreshold = 1.0
        )
        db.inventoryDao().insertOrUpdateItem(butter)

        repository.addInventoryStockBatch(
            itemId = "inv_butter",
            quantity = 5.0,
            purchaseRate = 200.0,
            notes = "Initial butter"
        )

        val dish = MenuItemEntity(
            id = "dish_cake",
            categoryId = "c1",
            categoryName = "Bakery",
            name = "Cake",
            price = 300.0,
            isAvailable = true
        )
        db.menuItemDao().insertOrUpdate(dish)
        db.recipeDao().insertOrUpdate(
            RecipeEntity(
                restaurantId = restaurantId,
                menuItemId = "dish_cake",
                menuItemName = "Cake",
                ingredients = listOf(RecipeIngredient(inventoryItemId = "inv_butter", inventoryItemName = "Butter", quantity = 2.0, usageUnit = "kg"))
            )
        )

        // Place bill
        val bill = repository.createAndSettleBill(
            restaurantId = restaurantId,
            restaurantName = "FIFO Cafe",
            orderType = "DINE_IN",
            customerName = "Sam",
            customerPhone = "8888888888",
            items = listOf(BillItem(dishId = "dish_cake", dishName = "Cake", unitPrice = 300.0, quantity = 1, totalPrice = 300.0, notes = "")),
            discountType = "NONE",
            discountValue = 0.0,
            paymentMethod = "CASH"
        )

        val itemAfterDeduction = repository.inventoryDao.getItemById("inv_butter")!!
        assertEquals(3.0, itemAfterDeduction.currentStock, 0.001)

        // Refund bill
        val refunded = repository.refundBillInventory(bill.id)
        assertTrue(refunded)

        // Check restored stock
        val itemRestored = repository.inventoryDao.getItemById("inv_butter")!!
        assertEquals(5.0, itemRestored.currentStock, 0.001)

        val batches = repository.inventoryBatchDao.getActiveBatchesForItemFifo("inv_butter")
        assertEquals(5.0, batches[0].remainingQuantity, 0.001)

        // Check idempotency of refund: second refund should return false without double-restoring
        val secondRefund = repository.refundBillInventory(bill.id)
        assertFalse(secondRefund)
        assertEquals(5.0, repository.inventoryDao.getItemById("inv_butter")!!.currentStock, 0.001)
    }
}
