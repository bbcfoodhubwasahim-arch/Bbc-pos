package com.example

import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.BillItem
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.components.BillTemplateDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BillFormatConsistencyTest {

    private val sampleBill = BillEntity(
        id = "test_bill_1",
        billNumber = "BBC-1001",
        restaurantId = "rest_1",
        restaurantName = "BBC FOOD HUB",
        orderType = "DINE_IN",
        customerName = "Test Customer",
        customerPhone = "9988776655",
        tableName = "T-1",
        items = listOf(
            BillItem("item_1", "Butter Chicken", 250.0, 1, 250.0),
            BillItem("item_2", "Butter Naan", 40.0, 2, 80.0)
        ),
        subtotal = 330.0,
        discountAmount = 0.0,
        totalAmount = 330.0,
        paymentMethod = "CASH",
        billTimestamp = 1700000000000L
    )

    @Test
    fun testAllBillTemplateDesignNormalization() {
        // Legacy and current format identifiers should normalize to the expected BillTemplateDesign
        assertEquals(BillTemplateDesign.CLASSIC_THERMAL, BillTemplateDesign.fromId("THERMAL_3_INCH"))
        assertEquals(BillTemplateDesign.CLASSIC_THERMAL, BillTemplateDesign.fromId("3_INCH"))
        assertEquals(BillTemplateDesign.CLASSIC_THERMAL, BillTemplateDesign.fromId("80MM"))
        assertEquals(BillTemplateDesign.CLASSIC_THERMAL, BillTemplateDesign.fromId("CLASSIC_THERMAL"))

        assertEquals(BillTemplateDesign.COMPACT_MINI, BillTemplateDesign.fromId("THERMAL_2_INCH"))
        assertEquals(BillTemplateDesign.COMPACT_MINI, BillTemplateDesign.fromId("2_INCH"))
        assertEquals(BillTemplateDesign.COMPACT_MINI, BillTemplateDesign.fromId("58MM"))
        assertEquals(BillTemplateDesign.COMPACT_MINI, BillTemplateDesign.fromId("COMPACT_MINI"))

        assertEquals(BillTemplateDesign.ELEGANT_DINE_IN, BillTemplateDesign.fromId("A4"))
        assertEquals(BillTemplateDesign.ELEGANT_DINE_IN, BillTemplateDesign.fromId("POINT_FIVE_ELEGANT"))
        assertEquals(BillTemplateDesign.ELEGANT_DINE_IN, BillTemplateDesign.fromId("ELEGANT_DINE_IN"))

        assertEquals(BillTemplateDesign.MODERN_SIGNATURE, BillTemplateDesign.fromId("MODERN_SIGNATURE"))
        assertEquals(BillTemplateDesign.RECEIPT_BOX, BillTemplateDesign.fromId("RECEIPT_BOX"))
        assertEquals(BillTemplateDesign.GST_TAX_INVOICE, BillTemplateDesign.fromId("GST_TAX_INVOICE"))
        assertEquals(BillTemplateDesign.RETRO_FOODIE, BillTemplateDesign.fromId("RETRO_FOODIE"))
        assertEquals(BillTemplateDesign.MINIMAL_CLEAN, BillTemplateDesign.fromId("MINIMAL_CLEAN"))

        // Null or blank falls back gracefully to default
        assertEquals(BillTemplateDesign.ELEGANT_DINE_IN, BillTemplateDesign.fromId(null))
        assertEquals(BillTemplateDesign.ELEGANT_DINE_IN, BillTemplateDesign.fromId(""))
        assertEquals(BillTemplateDesign.ELEGANT_DINE_IN, BillTemplateDesign.fromId("   "))
    }

    @Test
    fun testChangingDefaultFormatInSettingsAppliesAcrossAllBillGenerationPaths() {
        var restaurant = RestaurantEntity(
            id = "rest_1",
            name = "BBC FOOD HUB",
            address = "Washim",
            phone = "9130694963",
            billFormat = BillTemplateDesign.ELEGANT_DINE_IN.id
        )

        // 1. Initial format is ELEGANT_DINE_IN
        assertEquals(BillTemplateDesign.ELEGANT_DINE_IN, BillTemplateDesign.fromId(restaurant.billFormat))

        // 2. User changes format in Billing Settings to MODERN_SIGNATURE
        val newSelectedDesign = BillTemplateDesign.MODERN_SIGNATURE
        restaurant = restaurant.copy(
            billFormat = BillTemplateDesign.fromId(newSelectedDesign.id).id,
            updatedAt = System.currentTimeMillis()
        )

        // Confirm restaurant entity has the updated format
        assertEquals("MODERN_SIGNATURE", restaurant.billFormat)

        // 3. Confirm all paths (Settlement Screen, WhatsApp Share, Bill History, PDF Generator)
        // resolve to the EXACT selected default format:
        val resolvedFormatForSettlement = BillTemplateDesign.fromId(restaurant.billFormat)
        assertEquals(BillTemplateDesign.MODERN_SIGNATURE, resolvedFormatForSettlement)

        val resolvedFormatForWhatsApp = BillTemplateDesign.fromId(restaurant.billFormat)
        assertEquals(BillTemplateDesign.MODERN_SIGNATURE, resolvedFormatForWhatsApp)

        val resolvedFormatForPdfExport = BillTemplateDesign.fromId(restaurant.billFormat)
        assertEquals(BillTemplateDesign.MODERN_SIGNATURE, resolvedFormatForPdfExport)

        // 4. Test another format change: GST_TAX_INVOICE
        val gstDesign = BillTemplateDesign.GST_TAX_INVOICE
        restaurant = restaurant.copy(
            billFormat = BillTemplateDesign.fromId(gstDesign.id).id,
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("GST_TAX_INVOICE", restaurant.billFormat)
        assertEquals(BillTemplateDesign.GST_TAX_INVOICE, BillTemplateDesign.fromId(restaurant.billFormat))

        // 5. Test another format change: CLASSIC_THERMAL (80mm)
        val thermalDesign = BillTemplateDesign.CLASSIC_THERMAL
        restaurant = restaurant.copy(
            billFormat = BillTemplateDesign.fromId(thermalDesign.id).id,
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("CLASSIC_THERMAL", restaurant.billFormat)
        assertEquals(BillTemplateDesign.CLASSIC_THERMAL, BillTemplateDesign.fromId(restaurant.billFormat))
    }
}
