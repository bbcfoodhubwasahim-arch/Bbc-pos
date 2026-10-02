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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RewardPointsInvestigationTest {

    private lateinit var db: CafePosDatabase
    private lateinit var context: Context
    private lateinit var repository: PosRepository
    private lateinit var authManager: AuthManager
    private lateinit var syncManager: FirestoreSyncManager

    private val restaurantId = "rest_test_1"

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

        db.restaurantDao().insertOrUpdate(
            RestaurantEntity(
                id = restaurantId,
                name = "Test Cafe",
                address = "123 Street",
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
    fun testRewardPointsCreditingOnSettlement() = runBlocking {
        val phone = "9876543210"
        val customerName = "Rahul Sharma"

        // Step 1: Pre-create customer with 0 points
        val initialCustomer = CustomerEntity(
            id = "cust_rahul",
            name = customerName,
            contactNumber = phone,
            rewardPointsBalance = 0,
            giftPointsBalance = 0
        )
        db.customerDao().insertOrUpdate(initialCustomer)

        // Step 2: Create and settle bill for ₹500 (should earn 5 reward points at 1%)
        val bill = repository.createAndSettleBill(
            restaurantId = restaurantId,
            restaurantName = "Test Cafe",
            orderType = "DINE_IN",
            customerName = customerName,
            customerPhone = phone,
            items = listOf(
                BillItem("item_1", "Cold Coffee", 100.0, 5, 500.0)
            ),
            discountType = "NONE",
            discountValue = 0.0,
            paymentMethod = "CASH"
        )

        // Step 3: Check bill's points earned
        println("DEBUG: bill.rewardPointsEarned = ${bill.rewardPointsEarned}")
        println("DEBUG: bill.isSettled = ${bill.isSettled}")
        assertEquals(5, bill.rewardPointsEarned)

        // Step 4: Check customer's persistent balance in Room
        val customerAfter = db.customerDao().getCustomerByContact(phone)
        assertNotNull(customerAfter)
        println("DEBUG: customerAfter.id = ${customerAfter?.id}")
        println("DEBUG: customerAfter.rewardPointsBalance = ${customerAfter?.rewardPointsBalance}")
        
        // Step 5: Check summary
        val summary = repository.getCustomerPointsSummary(customerAfter!!.id)
        println("DEBUG: summary.usableRewardPoints = ${summary.usableRewardPoints}")

        // Step 6: Check batches
        val batches = db.pointsBatchDao().getValidBatchesForCustomer(customerAfter.id)
        println("DEBUG: batches size = ${batches.size}")
        batches.forEach { b ->
            println("DEBUG: batch id=${b.id}, type=${b.batchType}, custId=${b.customerId}, remaining=${b.remainingPoints}, expired=${b.isExpired}")
        }
    }
}
