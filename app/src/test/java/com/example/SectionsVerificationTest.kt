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
class SectionsVerificationTest {

    private lateinit var db: CafePosDatabase
    private lateinit var context: Context
    private lateinit var repository: PosRepository
    private lateinit var authManager: AuthManager
    private lateinit var syncManager: FirestoreSyncManager

    private val restaurantId = "rest_test_sections"

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

        val rest = RestaurantEntity(
            id = restaurantId,
            name = "Royal Cafe",
            address = "MG Road",
            phone = "9876543210",
            billPrefix = "INV-",
            billPrefixCountersJson = "{}",
            isActive = true
        )
        db.restaurantDao().insertOrUpdate(rest)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // SECTION 1 & 2: Credit / Udhaar Settlement & Customer Ledger Validation
    @Test
    fun testSection1And2_CreditBillingAndOutstandingCalculation() = runBlocking {
        val customerName = "John Doe"
        val customerPhone = "9998887776"

        // Create a bill with CREDIT payment method
        val bill = BillEntity(
            id = "bill_credit_1",
            restaurantId = restaurantId,
            restaurantName = "Royal Cafe",
            billNumber = "INV-001",
            orderType = "DINE_IN",
            tableName = "Table 1",
            items = listOf(BillItem(dishId = "m1", dishName = "Latte", unitPrice = 150.0, quantity = 2, totalPrice = 300.0)),
            subtotal = 300.0,
            discountAmount = 0.0,
            totalAmount = 300.0,
            paymentMethod = "CREDIT",
            customerName = customerName,
            customerPhone = customerPhone,
            billTimestamp = System.currentTimeMillis()
        )
        db.billDao().insertOrUpdate(bill)

        // Verify bill is stored as CREDIT
        val fetchedBills = db.billDao().getAllBillsDirect()
        assertEquals(1, fetchedBills.size)
        assertEquals("CREDIT", fetchedBills[0].paymentMethod)
        assertEquals(300.0, fetchedBills[0].totalAmount, 0.01)

        // Customer payment recorded
        val payment1 = CustomerPaymentEntity(
            id = "pay_1",
            customerId = "cust_1",
            customerName = customerName,
            customerPhone = customerPhone,
            amount = 100.0,
            paymentMode = "CASH",
            timestamp = System.currentTimeMillis()
        )
        db.customerPaymentDao().insert(payment1)

        // Outstanding calculation: total credit bills (300) - total customer payments (100) = 200
        val creditBillsTotal = fetchedBills.filter {
            (it.customerPhone.trim() == customerPhone || it.customerName.trim().equals(customerName, ignoreCase = true)) &&
            it.paymentMethod == "CREDIT"
        }.sumOf { it.totalAmount }

        val paymentsTotal = db.customerPaymentDao().getPaymentsForCustomerDirect("cust_1", customerPhone).sumOf { it.amount }

        val outstanding = creditBillsTotal - paymentsTotal
        assertEquals(200.0, outstanding, 0.01)
    }

    // SECTION 3 & 3B: Bills History Filtering (Date filter, Party-wise filter)
    @Test
    fun testSection3And3B_BillsHistoryFiltering() = runBlocking {
        val now = System.currentTimeMillis()
        val customer1 = "Alice Brown"
        val phone1 = "9876543211"
        val customer2 = "Bob White"
        val phone2 = "9876543212"

        // Bill 1: Alice (Cash)
        val bill1 = BillEntity(
            id = "b1",
            restaurantId = restaurantId,
            restaurantName = "Royal Cafe",
            billNumber = "INV-001",
            orderType = "DINE_IN",
            items = emptyList(),
            subtotal = 100.0,
            totalAmount = 100.0,
            paymentMethod = "CASH",
            customerName = customer1,
            customerPhone = phone1,
            billTimestamp = now
        )

        // Bill 2: Bob (Credit)
        val bill2 = BillEntity(
            id = "b2",
            restaurantId = restaurantId,
            restaurantName = "Royal Cafe",
            billNumber = "INV-002",
            orderType = "TAKEAWAY",
            items = emptyList(),
            subtotal = 250.0,
            totalAmount = 250.0,
            paymentMethod = "CREDIT",
            customerName = customer2,
            customerPhone = phone2,
            billTimestamp = now
        )

        // Bill 3: Unnamed Cash bill
        val bill3 = BillEntity(
            id = "b3",
            restaurantId = restaurantId,
            restaurantName = "Royal Cafe",
            billNumber = "INV-003",
            orderType = "DINE_IN",
            items = emptyList(),
            subtotal = 50.0,
            totalAmount = 50.0,
            paymentMethod = "CASH",
            customerName = "",
            customerPhone = "",
            billTimestamp = now
        )

        db.billDao().insertOrUpdate(bill1)
        db.billDao().insertOrUpdate(bill2)
        db.billDao().insertOrUpdate(bill3)

        val allBills = db.billDao().getAllBillsDirect()
        assertEquals(3, allBills.size)

        // Party filter for Bob White
        val bobBills = allBills.filter { b ->
            b.customerPhone == phone2 || b.customerName.equals(customer2, ignoreCase = true)
        }
        assertEquals(1, bobBills.size)
        assertEquals("INV-002", bobBills[0].billNumber)
        assertEquals("CREDIT", bobBills[0].paymentMethod)

        // All Parties filter (bills with party name or phone)
        val partyBills = allBills.filter { it.customerName.isNotBlank() || it.customerPhone.isNotBlank() }
        assertEquals(2, partyBills.size)
    }

    // SECTION 4: Bill Number Prefix Generation
    @Test
    fun testSection4_SequentialBillPrefix() = runBlocking {
        // Generate sequential bills under active restaurant prefix "INV-"
        val generated1 = repository.generateNextBillNumber(restaurantId)
        assertEquals("INV-001", generated1)

        val generated2 = repository.generateNextBillNumber(restaurantId)
        assertEquals("INV-002", generated2)

        val generated3 = repository.generateNextBillNumber(restaurantId)
        assertEquals("INV-003", generated3)
    }
}
