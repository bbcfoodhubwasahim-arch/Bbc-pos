package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.auth.AuthManager
import com.example.data.cloud.FirestoreSyncManager
import com.example.data.local.database.CafePosDatabase
import com.example.data.local.entity.*
import com.example.data.repository.PosRepository
import com.example.util.FoodCostCalculator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipePersistenceAndCogsTest {

    private lateinit var db: CafePosDatabase
    private lateinit var context: Context
    private lateinit var repository: PosRepository
    private lateinit var authManager: AuthManager
    private lateinit var syncManager: FirestoreSyncManager

    private val restaurantId = "rest_test_001"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("com.aistudio.cafepos.bkrboy")
                .setProjectId("dummy-recipe-test")
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

        db.restaurantDao().insertOrUpdate(
            RestaurantEntity(
                id = restaurantId,
                name = "BBC Cafe",
                address = "Station Road",
                phone = "9876543210",
                isActive = true
            )
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testRecipePersistenceAndCogsAfterRelogin() = runBlocking {
        // 1. Create an Inventory Item with a purchase price
        val inventoryItem = InventoryItemEntity(
            id = "inv_coffee_beans",
            restaurantId = restaurantId,
            name = "Coffee Beans",
            unit = "KG",
            openingStock = 10.0,
            currentStock = 10.0,
            purchasePrice = 800.0, // Rs 800 / KG
            lowStockThreshold = 2.0
        )
        db.inventoryDao().insertOrUpdateItem(inventoryItem)

        // 2. Create a Menu Item
        val menuItem = MenuItemEntity(
            id = "dish_espresso",
            categoryId = "cat_beverages",
            categoryName = "Beverages",
            name = "Espresso",
            price = 120.0,
            isAvailable = true
        )
        db.menuItemDao().insertOrUpdate(menuItem)

        // 3. Create a Recipe (20 gm of Coffee Beans = 0.02 KG * 800 = Rs 16.0 cost)
        val ingredient = RecipeIngredient(
            inventoryItemId = "inv_coffee_beans",
            inventoryItemName = "Coffee Beans",
            quantity = 20.0,
            usageUnit = "gm",
            isTakeawayExtra = false
        )
        val recipe = RecipeEntity(
            menuItemId = "dish_espresso",
            restaurantId = restaurantId,
            menuItemName = "Espresso",
            ingredients = listOf(ingredient),
            notes = "Single shot espresso recipe",
            updatedAt = System.currentTimeMillis()
        )
        repository.saveRecipe(recipe)

        // Verify Recipe exists in Room before logout
        val preLogoutRecipes = db.recipeDao().getAllRecipesDirect()
        assertEquals(1, preLogoutRecipes.size)
        assertEquals("dish_espresso", preLogoutRecipes.first().menuItemId)
        assertEquals(1, preLogoutRecipes.first().ingredients.size)
        assertEquals(20.0, preLogoutRecipes.first().ingredients.first().quantity, 0.001)

        // 4. User logs out: All local Room tables are wiped completely
        repository.clearAllLocalData()

        // Verify local Room database is empty
        assertEquals(0, db.recipeDao().getAllRecipesDirect().size)
        assertEquals(0, db.inventoryDao().getAllInventoryDirect().size)
        assertEquals(0, db.menuItemDao().getAllMenuItemsDirect().size)

        // 5. User logs back in: Simulate restore from Cloud into Room
        db.restaurantDao().insertOrUpdate(
            RestaurantEntity(
                id = restaurantId,
                name = "BBC Cafe",
                address = "Station Road",
                phone = "9876543210",
                isActive = true
            )
        )
        db.inventoryDao().insertOrUpdateItem(inventoryItem)
        db.menuItemDao().insertOrUpdate(menuItem)
        db.recipeDao().insertOrUpdate(recipe)

        // Verify (a): Recipe data exists in Room after relogin
        val postLoginRecipes = db.recipeDao().getAllRecipesDirect()
        assertEquals("Recipe count must be 1 after relogin", 1, postLoginRecipes.size)
        val restoredRecipe = postLoginRecipes.first()
        assertEquals("dish_espresso", restoredRecipe.menuItemId)
        assertEquals(1, restoredRecipe.ingredients.size)
        assertEquals("Coffee Beans", restoredRecipe.ingredients.first().inventoryItemName)
        assertEquals(20.0, restoredRecipe.ingredients.first().quantity, 0.001)

        // 6. Create / Settle a Bill using that recipe's dish after relogin
        val billItems = listOf(
            BillItem(
                dishId = "dish_espresso",
                dishName = "Espresso",
                unitPrice = 120.0,
                quantity = 2,
                totalPrice = 240.0
            )
        )
        val bill = repository.createAndSettleBill(
            restaurantId = restaurantId,
            restaurantName = "BBC Cafe",
            orderType = "DINE_IN",
            tableId = null,
            tableName = null,
            customerName = "Test Guest",
            customerPhone = "9876543210",
            items = billItems,
            discountType = "NONE",
            discountValue = 0.0,
            paymentMethod = "CASH"
        )

        // Verify (b): Bill computes non-zero food cost based on the recipe
        // Expected food cost: 2 * (20 gm * (800 / 1000)) = 2 * 16.0 = Rs 32.0
        assertTrue("Bill totalFoodCost must be greater than zero", bill.totalFoodCost > 0.0)
        assertEquals(32.0, bill.totalFoodCost, 0.01)

        // 7. Verify P&L calculation directly
        val allBills = db.billDao().getAllBillsDirect()
        val recipesMap = db.recipeDao().getAllRecipesDirect().associateBy { it.menuItemId }
        val inventoryMap = db.inventoryDao().getAllInventoryDirect().associateBy { it.id }

        val pnlFoodCost = allBills.filter { it.isSettled }.sumOf { b ->
            if (b.totalFoodCost > 0.0) {
                b.totalFoodCost
            } else {
                b.items.sumOf { item ->
                    val rec = recipesMap[item.dishId]
                    if (rec != null) {
                        val costSummary = FoodCostCalculator.calculateRecipeCost(rec, inventoryMap)
                        costSummary.dineInCost * item.quantity
                    } else 0.0
                }
            }
        }

        assertTrue("P&L COGS must be non-zero after relogin", pnlFoodCost > 0.0)
        assertEquals(32.0, pnlFoodCost, 0.01)
    }

    @Test
    fun testRestoredCloudBillWithZeroCostIsBackfilledFromRecipes() = runBlocking {
        // Setup inventory & recipe
        val inv = InventoryItemEntity(
            id = "inv_flour",
            restaurantId = restaurantId,
            name = "Flour",
            unit = "KG",
            openingStock = 50.0,
            currentStock = 50.0,
            purchasePrice = 40.0,
            lowStockThreshold = 5.0
        )
        db.inventoryDao().insertOrUpdateItem(inv)

        val recipe = RecipeEntity(
            menuItemId = "dish_pizza_base",
            restaurantId = restaurantId,
            menuItemName = "Pizza Base",
            ingredients = listOf(
                RecipeIngredient(
                    inventoryItemId = "inv_flour",
                    inventoryItemName = "Flour",
                    quantity = 250.0,
                    usageUnit = "gm",
                    isTakeawayExtra = false
                )
            ),
            notes = "",
            updatedAt = System.currentTimeMillis()
        )
        db.recipeDao().insertOrUpdate(recipe)

        // Simulate a bill restored from cloud where totalFoodCost was 0.0
        val cloudBill = BillEntity(
            id = "bill_cloud_001",
            billNumber = "BILL-001",
            restaurantId = restaurantId,
            restaurantName = "BBC Cafe",
            orderType = "DINE_IN",
            tableId = null,
            tableName = null,
            customerName = "Old Cloud Guest",
            customerPhone = "",
            items = listOf(
                BillItem(
                    dishId = "dish_pizza_base",
                    dishName = "Pizza Base",
                    unitPrice = 100.0,
                    quantity = 1,
                    totalPrice = 100.0
                )
            ),
            subtotal = 100.0,
            discountType = "NONE",
            discountValue = 0.0,
            discountAmount = 0.0,
            totalAmount = 100.0,
            paymentMethod = "CASH",
            isSettled = true,
            isStockDeducted = false,
            totalFoodCost = 0.0, // Zero in cloud
            billTimestamp = System.currentTimeMillis()
        )
        db.billDao().insertOrUpdate(cloudBill)

        // P&L calculation dynamic fallback
        val recipesMap = db.recipeDao().getAllRecipesDirect().associateBy { it.menuItemId }
        val inventoryMap = db.inventoryDao().getAllInventoryDirect().associateBy { it.id }
        val allBills = db.billDao().getAllBillsDirect()

        val pnlFoodCost = allBills.filter { it.isSettled }.sumOf { b ->
            if (b.totalFoodCost > 0.0) {
                b.totalFoodCost
            } else {
                b.items.sumOf { item ->
                    val rec = recipesMap[item.dishId]
                    if (rec != null) {
                        val costSummary = FoodCostCalculator.calculateRecipeCost(rec, inventoryMap)
                        costSummary.dineInCost * item.quantity
                    } else 0.0
                }
            }
        }

        // Expected cost: 250 gm of Rs 40/kg flour = 0.25 * 40 = Rs 10.0
        assertEquals(10.0, pnlFoodCost, 0.01)
    }
}
