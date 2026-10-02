package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.CafePosDatabase
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.BillItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataIsolationTest {

    private lateinit var db: CafePosDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CafePosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testAccountDataIsolationOnSignOutAndNewAccountLogin() = runBlocking {
        // --- 1. Account A logs in and creates a bill ---
        val accountABill = BillEntity(
            id = "bill_account_a_001",
            billNumber = "BILL-001",
            restaurantId = "rest_1",
            restaurantName = "The Baker's Boy Cafe",
            orderType = "DINE_IN",
            tableId = "table_1",
            tableName = "Table 1",
            customerName = "Customer A",
            customerPhone = "9876543210",
            items = listOf(BillItem("item_1", "Cappuccino", 140.0, 1)),
            subtotal = 140.0,
            discountType = "NONE",
            discountValue = 0.0,
            discountAmount = 0.0,
            totalAmount = 140.0,
            paymentMethod = "CASH",
            isSettled = true,
            billTimestamp = System.currentTimeMillis()
        )
        db.billDao().insertOrUpdate(accountABill)

        // Verify Account A has 1 bill
        assertEquals(1, db.billDao().getCountDirect())
        assertEquals("bill_account_a_001", db.billDao().getAllBillsDirect().first().id)

        // --- 2. Account A Signs Out: Complete database wipe ---
        db.clearAllTables()

        // Verify all tables are wiped clean on Sign Out
        assertEquals(0, db.billDao().getCountDirect())
        assertTrue(db.billDao().getAllBillsDirect().isEmpty())
        assertEquals(0, db.restaurantDao().getCountDirect())
        assertEquals(0, db.menuItemDao().getCountDirect())
        assertEquals(0, db.cafeTableDao().getCountDirect())

        // --- 3. Account B logs in as a fresh account (0 cloud bills) ---
        // Verify Account B starts with ZERO bills from Account A
        assertEquals(0, db.billDao().getCountDirect())
        assertTrue(db.billDao().getAllBillsDirect().isEmpty())

        // Account B creates their own bill
        val accountBBill = BillEntity(
            id = "bill_account_b_001",
            billNumber = "BILL-B-001",
            restaurantId = "rest_1",
            restaurantName = "The Baker's Boy Cafe",
            orderType = "TAKEAWAY",
            tableId = null,
            tableName = null,
            customerName = "Customer B",
            customerPhone = "9123456780",
            items = listOf(BillItem("item_2", "Butter Croissant", 110.0, 2)),
            subtotal = 220.0,
            discountType = "NONE",
            discountValue = 0.0,
            discountAmount = 0.0,
            totalAmount = 220.0,
            paymentMethod = "UPI",
            isSettled = true,
            billTimestamp = System.currentTimeMillis()
        )
        db.billDao().insertOrUpdate(accountBBill)

        // Account B has only their 1 bill
        assertEquals(1, db.billDao().getCountDirect())
        val accountBBills = db.billDao().getAllBillsDirect()
        assertEquals(1, accountBBills.size)
        assertEquals("bill_account_b_001", accountBBills.first().id)
        assertEquals("Customer B", accountBBills.first().customerName)

        // --- 4. Account B Signs Out: Database wiped clean again ---
        db.clearAllTables()
        assertEquals(0, db.billDao().getCountDirect())

        // --- 5. Account A logs back in and restores Account A's bill ---
        db.billDao().insertOrUpdate(accountABill)
        val restoredBills = db.billDao().getAllBillsDirect()
        assertEquals(1, restoredBills.size)
        assertEquals("bill_account_a_001", restoredBills.first().id)
        assertEquals("Customer A", restoredBills.first().customerName)
    }
}
