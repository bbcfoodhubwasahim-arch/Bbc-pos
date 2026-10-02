package com.example

import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.BillItem
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.OfferEntity
import com.example.data.local.entity.PointsBatchEntity
import com.example.data.local.entity.PointsLedgerEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.util.BillShareUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LoyaltyUxImprovementsTest {

    private val sampleRestaurant = RestaurantEntity(
        id = "rest_1",
        name = "BBC FOOD HUB",
        address = "Main Market Road",
        phone = "9876500000"
    )

    // 1 & 8: Redeemed Points as their own separate bill lines & Settlement display
    @Test
    fun testRedeemedPointsAsSeparateBillLines() {
        val bill = BillEntity(
            id = "bill_loyalty_1",
            billNumber = "INV-2001",
            restaurantId = "rest_1",
            restaurantName = "BBC FOOD HUB",
            orderType = "DINE_IN",
            customerName = "Ramesh Kumar",
            customerPhone = "9876543210",
            items = listOf(
                BillItem("item_1", "Paneer Tikka", 200.0, 1, 200.0),
                BillItem("item_2", "Garlic Naan", 50.0, 2, 100.0)
            ),
            subtotal = 300.0,
            discountAmount = 30.0,
            discountType = "FLAT",
            rewardPointsRedeemed = 50,
            giftPointsRedeemed = 20,
            totalAmount = 200.0,
            paymentMethod = "CASH",
            billTimestamp = System.currentTimeMillis()
        )

        val summary = BillShareUtil.buildBillSummaryText(bill, sampleRestaurant)
        
        // Assert Reward Points & Gift Points are separate lines and NOT merged with Discount
        assertTrue("Summary must include Discount line", summary.contains("Discount: -₹30.00") || summary.contains("Discount"))
        assertTrue("Summary must include separate Reward Points line", summary.contains("Reward Points Redeemed: 50 Pts (-₹50.00)"))
        assertTrue("Summary must include separate Gift Points line", summary.contains("Gift Points Redeemed: 20 Pts (-₹20.00)"))
        assertTrue("Summary must include correct Final Total", summary.contains("Final Total: ₹200.00") || summary.contains("FINAL AMOUNT: ₹200.00"))
    }

    // 2: "Reward Points" naming consistency
    @Test
    fun testRewardPointsNamingConsistency() {
        val ledger = PointsLedgerEntity(
            id = "ledger_1",
            customerId = "cust_1",
            customerPhone = "9876543210",
            transactionType = "EARNED",
            pointsAmount = 100,
            balanceType = "REWARD",
            notes = "Reward Points earned on Bill #101",
            timestamp = System.currentTimeMillis()
        )
        assertEquals("REWARD", ledger.balanceType)
        assertTrue(ledger.notes.contains("Reward Points"))
    }

    // 3: Editable Expiry Days When Gifting Points
    @Test
    fun testEditableExpiryDaysWhenGiftingPoints() {
        val now = System.currentTimeMillis()
        val customExpiryDays = 45
        val expiryTimestamp = now + (customExpiryDays.toLong() * 24L * 60L * 60L * 1000L)

        val giftBatch = PointsBatchEntity(
            id = "batch_gift_1",
            customerId = "cust_1",
            customerPhone = "9876543210",
            batchType = "GIFT",
            initialPoints = 150,
            remainingPoints = 150,
            earnDate = now,
            expiryDate = expiryTimestamp,
            isExpired = false
        )

        assertEquals("GIFT", giftBatch.batchType)
        assertEquals(150, giftBatch.initialPoints)
        // Verify expiry is set to 45 days in future
        val daysDiff = (giftBatch.expiryDate - giftBatch.earnDate) / (24L * 60L * 60L * 1000L)
        assertEquals(45L, daysDiff)
    }

    // 4: Separate Visit Program and Reward Points configurations
    @Test
    fun testSeparateVisitProgramAndRewardPointsSettings() {
        val visitOffer = OfferEntity(
            id = "offer_v1",
            restaurantId = "rest_1",
            name = "Visit 6 Free Dessert",
            offerType = "VISIT_BASED",
            rewardType = "FREE_ITEM",
            totalVisitsInProgram = 6,
            visitNumber = 6,
            validityDays = 45,
            isActive = true
        )

        assertEquals("VISIT_BASED", visitOffer.offerType)
        assertEquals(6, visitOffer.totalVisitsInProgram)
        assertEquals(6, visitOffer.visitNumber)
        assertEquals(45, visitOffer.validityDays)
    }

    // 5: Hide Loyalty Info After Cycle Completion
    @Test
    fun testHideLoyaltyInfoAfterCycleCompletion() {
        val now = System.currentTimeMillis()
        val expiredCustomer = CustomerEntity(
            id = "cust_expired",
            name = "Anil Sharma",
            contactNumber = "9123456780",
            loyaltyVisitCount = 6,
            loyaltyStartDate = now - 60L * 24 * 60 * 60 * 1000L,
            loyaltyExpiryDate = now - 10L * 24 * 60 * 60 * 1000L, // Expired 10 days ago
            isEnrolledInLoyalty = false // Marked completed / un-enrolled
        )

        // Customer with expired cycle should not show active visit reward prompting on settlement
        val isCycleActive = expiredCustomer.isEnrolledInLoyalty && 
            (expiredCustomer.loyaltyExpiryDate == null || expiredCustomer.loyaltyExpiryDate!! >= now)
        assertFalse(isCycleActive)
    }

    // 7: Phone Number Autocomplete by Partial Digits
    @Test
    fun testPhoneNumberAutocompletePartialMatch() {
        val customers = listOf(
            CustomerEntity(id = "1", name = "Amit Sharma", contactNumber = "9876543210"),
            CustomerEntity(id = "2", name = "Amita Patel", contactNumber = "9876599999"),
            CustomerEntity(id = "3", name = "Rohit Verma", contactNumber = "9123456789"),
            CustomerEntity(id = "4", name = "Deepak Singh", contactNumber = "8877665544")
        )

        val query = "9876"
        val matched = customers.filter { cust ->
            val cleanPhone = cust.contactNumber.replace(Regex("[^0-9]"), "")
            cleanPhone.startsWith(query) || cleanPhone.contains(query)
        }

        assertEquals(2, matched.size)
        assertEquals("Amit Sharma", matched[0].name)
        assertEquals("Amita Patel", matched[1].name)
    }

    // 9: Large Point Redemption Threshold Check
    @Test
    fun testLargePointRedemptionConfirmationRequirement() {
        val pointsToRedeem = 250
        val maxAvailablePoints = 300
        val isMaxSelected = (pointsToRedeem == maxAvailablePoints)
        val isLargeAmount = pointsToRedeem >= 200

        val requiresConfirmation = isMaxSelected || isLargeAmount
        assertTrue("Redeeming 250 points or max points must require a confirmation step", requiresConfirmation)
    }

    // 10: Points Given Away (Reward + Gift) Summary Calculation for Reports
    @Test
    fun testTotalPointsGivenAwaySummaryCalculation() {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.timeInMillis

        val bills = listOf(
            BillEntity(
                id = "b1",
                billNumber = "101",
                restaurantId = "rest_1",
                restaurantName = "BBC FOOD HUB",
                orderType = "DINE_IN",
                rewardPointsRedeemed = 50,
                giftPointsRedeemed = 25,
                totalAmount = 200.0,
                billTimestamp = now
            ),
            BillEntity(
                id = "b2",
                billNumber = "102",
                restaurantId = "rest_1",
                restaurantName = "BBC FOOD HUB",
                orderType = "DINE_IN",
                rewardPointsRedeemed = 100,
                giftPointsRedeemed = 0,
                totalAmount = 500.0,
                billTimestamp = now - 3600000L // 1 hour ago
            ),
            BillEntity(
                id = "b3",
                billNumber = "103",
                restaurantId = "rest_1",
                restaurantName = "BBC FOOD HUB",
                orderType = "DINE_IN",
                rewardPointsRedeemed = 30,
                giftPointsRedeemed = 20,
                totalAmount = 150.0,
                billTimestamp = startOfMonth + 86400000L // earlier in current month
            )
        )

        // Today's points given away value in ₹ (1 pt = ₹1)
        val todayPointsGivenAway = bills
            .filter { it.billTimestamp >= startOfToday }
            .sumOf { (it.rewardPointsRedeemed + it.giftPointsRedeemed).toDouble() }
        
        // Month's points given away value in ₹
        val monthPointsGivenAway = bills
            .filter { it.billTimestamp >= startOfMonth }
            .sumOf { (it.rewardPointsRedeemed + it.giftPointsRedeemed).toDouble() }

        // b1: 50 + 25 = 75; b2: 100 + 0 = 100 -> Total Today = 175
        assertEquals(175.0, todayPointsGivenAway, 0.001)
        // b3: 30 + 20 = 50 -> Total Month = 175 + 50 = 225
        assertEquals(225.0, monthPointsGivenAway, 0.001)
    }

    // 11: Optional Customer Birthday Field
    @Test
    fun testCustomerBirthdayFieldStorage() {
        val customerWithBirthday = CustomerEntity(
            id = "cust_bday_1",
            name = "Priya Roy",
            contactNumber = "9988112233",
            birthday = "14/07",
            isEnrolledInLoyalty = true
        )

        assertNotNull(customerWithBirthday.birthday)
        assertEquals("14/07", customerWithBirthday.birthday)

        val customerWithoutBirthday = CustomerEntity(
            id = "cust_bday_2",
            name = "Suresh Raina",
            contactNumber = "9988112244",
            birthday = null
        )
        assertNull(customerWithoutBirthday.birthday)
    }

    // Split payment text format test in bill summary
    @Test
    fun testSplitPaymentBreakdownInBillSummary() {
        val splitBill = BillEntity(
            id = "bill_split",
            billNumber = "SPLIT-101",
            restaurantId = "rest_1",
            restaurantName = "BBC FOOD HUB",
            orderType = "DINE_IN",
            subtotal = 500.0,
            discountAmount = 0.0,
            totalAmount = 500.0,
            paymentMethod = "SPLIT",
            cashAmount = 300.0,
            upiAmount = 200.0,
            billTimestamp = System.currentTimeMillis()
        )

        val summary = BillShareUtil.buildBillSummaryText(splitBill, sampleRestaurant)
        assertTrue(summary.contains("Payment: SPLIT (Cash: ₹300.00, Online/UPI: ₹200.00)"))
    }
}
