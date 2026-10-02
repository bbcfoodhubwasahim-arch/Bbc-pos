package com.example.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.RestaurantEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfInvoiceGenerator {

    // Standard A4 dimensions in points (72 points per inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    // High contrast solid dark black text on all white/light backgrounds
    private val SOLID_BLACK = Color.rgb(26, 26, 26) // #1A1A1A

    /**
     * Generates a high-resolution, perfectly formatted A4 PDF invoice.
     * Guaranteed to use pure Sans-Serif Typeface so custom phone fonts do NOT distort the PDF.
     * Dynamically switches design based on restaurant.billFormat (all 8 handcrafted templates).
     */
    fun generateA4Pdf(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val fontBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val fontNormal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val fontItalic = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)

        val template = com.example.ui.components.BillTemplateDesign.fromId(restaurant.billFormat)

        when (template) {
            com.example.ui.components.BillTemplateDesign.UNIVERSAL_CAFE_PRO,
            com.example.ui.components.BillTemplateDesign.ELEGANT_DINE_IN -> drawUniversalCafeProPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.MODERN_SIGNATURE -> drawModernSignaturePdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.GST_TAX_INVOICE -> drawGstTaxInvoicePdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.MINIMAL_CLEAN -> drawMinimalCleanPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.CLASSIC_THERMAL -> drawClassicThermalPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.COMPACT_MINI -> drawCompactMiniPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.RECEIPT_BOX -> drawReceiptBoxPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.RETRO_FOODIE -> drawRetroFoodiePdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.FAST_FOOD_EXPRESS -> drawFastFoodExpressPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.ROYAL_PREMIUM -> drawRoyalPremiumPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
            com.example.ui.components.BillTemplateDesign.KITCHEN_DETAILED -> drawKitchenDetailedPdf(canvas, bill, restaurant, previousDue, paint, textPaint, fontBold, fontNormal, fontItalic)
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "invoice_${bill.billNumber}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    // =========================================================================
    // 0. UNIVERSAL CAFE PRO PDF (Flagship Unified Design)
    // =========================================================================
    private fun drawUniversalCafeProPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 36f
        val right = PAGE_WIDTH - 36f
        var currentY = 32f

        // Outer Page Border
        paint.color = Color.rgb(226, 232, 240)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(RectF(18f, 18f, PAGE_WIDTH - 18f, PAGE_HEIGHT - 18f), 8f, 8f, paint)

        // Brand Badge / Initials
        paint.color = Color.rgb(230, 81, 0)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(PAGE_WIDTH / 2f, currentY + 18f, 18f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 12f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("BBC", PAGE_WIDTH / 2f, currentY + 23f, textPaint)
        currentY += 46f

        // Cafe Name & Tagline
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 22f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        val cafeName = restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()
        canvas.drawText(cafeName, PAGE_WIDTH / 2f, currentY, textPaint)
        currentY += 16f

        if (restaurant.tagline.isNotBlank()) {
            textPaint.textSize = 11f
            textPaint.typeface = fontItalic
            textPaint.color = Color.rgb(100, 116, 139)
            canvas.drawText(restaurant.tagline, PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }

        if (restaurant.address.isNotBlank()) {
            textPaint.textSize = 10f
            textPaint.typeface = fontNormal
            textPaint.color = Color.rgb(51, 65, 85)
            canvas.drawText(restaurant.address, PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 13f
        }

        val phones = listOfNotNull(
            restaurant.phone.takeIf { it.isNotBlank() },
            restaurant.altPhone.takeIf { it.isNotBlank() }
        ).joinToString(" / ")
        if (phones.isNotBlank()) {
            textPaint.textSize = 10f
            textPaint.color = Color.rgb(51, 65, 85)
            canvas.drawText("Contact: $phones", PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 13f
        }

        val regList = mutableListOf<String>()
        if (restaurant.isFssaiEnabled && restaurant.showFssaiOnBill && restaurant.fssaiNumber.isNotBlank()) regList.add("FSSAI: ${restaurant.fssaiNumber}")
        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstNumber.isNotBlank()) regList.add("GSTIN: ${restaurant.gstNumber}")
        if (regList.isNotEmpty()) {
            textPaint.textSize = 9.5f
            textPaint.typeface = fontBold
            textPaint.color = Color.rgb(71, 85, 105)
            canvas.drawText(regList.joinToString(" • "), PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }

        currentY += 4f
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 10f

        // Token & Order Details Banner Box
        val boxHeight = 52f
        val boxRect = RectF(left, currentY, right, currentY + boxHeight)
        paint.color = Color.rgb(248, 250, 252)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(boxRect, 6f, 6f, paint)
        paint.color = Color.rgb(226, 232, 240)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(boxRect, 6f, 6f, paint)

        val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.US)
        val dateFormatted = sdf.format(Date(bill.billTimestamp))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.rgb(230, 81, 0)
        textPaint.textSize = 12f
        textPaint.typeface = fontBold
        val tokenText = if (restaurant.showTokenOnBill) "TOKEN #${bill.billNumber}" else "Bill #${bill.billNumber}"
        canvas.drawText(tokenText, left + 10f, currentY + 20f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 11f
        val orderLabel = when {
            !bill.tableName.isNullOrBlank() -> "Table: ${bill.tableName} (Dine-In)"
            bill.orderType.equals("TAKEAWAY", ignoreCase = true) -> "Takeaway / Parcel"
            bill.orderType.equals("DELIVERY", ignoreCase = true) -> "Delivery"
            else -> "Dine-In"
        }
        canvas.drawText(orderLabel, right - 10f, currentY + 20f, textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.rgb(100, 116, 139)
        textPaint.textSize = 9.5f
        textPaint.typeface = fontNormal
        canvas.drawText("Date: $dateFormatted", left + 10f, currentY + 40f, textPaint)

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = SOLID_BLACK
            textPaint.typeface = fontBold
            val custText = "Customer: ${bill.customerName.ifBlank { "Guest" }} ${if (bill.customerPhone.isNotBlank()) "(${bill.customerPhone})" else ""}"
            canvas.drawText(custText, right - 10f, currentY + 40f, textPaint)
        }

        currentY += boxHeight + 14f

        // Table Header
        paint.color = Color.rgb(241, 245, 249)
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, currentY - 4f, right, currentY + 16f, paint)

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 10f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM", left + 6f, currentY + 10f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", right - 150f, currentY + 10f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", right - 75f, currentY + 10f, textPaint)
        canvas.drawText("AMOUNT", right - 6f, currentY + 10f, textPaint)

        currentY += 20f
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 12f

        // Items
        bill.items.forEach { item ->
            textPaint.color = SOLID_BLACK
            textPaint.textSize = 10.5f
            textPaint.typeface = fontBold
            textPaint.textAlign = Paint.Align.LEFT

            val displayName = if (item.isFree) "${item.dishName} [FREE]" else item.dishName
            canvas.drawText(displayName, left + 6f, currentY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", right - 150f, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.rgb(71, 85, 105)
            textPaint.typeface = fontNormal
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", right - 75f, currentY, textPaint)

            val isItemFree = item.isFree || item.totalPrice == 0.0
            val itemTotalStr = if (isItemFree) "₹0.00" else "₹${String.format(Locale.US, "%.2f", item.totalPrice)}"
            textPaint.color = SOLID_BLACK
            textPaint.typeface = fontBold
            canvas.drawText(itemTotalStr, right - 6f, currentY, textPaint)

            currentY += 14f
            val itemNote = item.cookingNotes.ifBlank { item.notes }
            if (itemNote.isNotBlank()) {
                textPaint.textSize = 8.5f
                textPaint.typeface = fontItalic
                textPaint.color = Color.rgb(100, 116, 139)
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("↳ Note: $itemNote", left + 12f, currentY, textPaint)
                currentY += 12f
            }
        }

        currentY += 6f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        // Subtotal & Discounts
        val subtotal = bill.items.sumOf { it.totalPrice }
        textPaint.textSize = 10.5f
        textPaint.typeface = fontNormal
        textPaint.color = Color.rgb(71, 85, 105)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Subtotal (${bill.items.sumOf { it.quantity }} items):", left + 6f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = fontBold
        canvas.drawText("₹${String.format(Locale.US, "%.2f", subtotal)}", right - 6f, currentY, textPaint)
        currentY += 14f

        if (bill.discountAmount > 0) {
            textPaint.color = Color.rgb(230, 81, 0)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Offer Discount:", left + 6f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", right - 6f, currentY, textPaint)
            currentY += 14f
        }

        if (bill.pointsRedeemed > 0) {
            textPaint.color = Color.rgb(22, 163, 74)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Points Redeemed (${bill.pointsRedeemed} Pts):", left + 6f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}", right - 6f, currentY, textPaint)
            currentY += 14f
        }

        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstRate > 0) {
            val gstAmount = bill.totalAmount * (restaurant.gstRate / 100.0)
            val halfGst = gstAmount / 2.0
            val rate = restaurant.gstRate / 2.0
            textPaint.color = Color.rgb(100, 116, 139)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("CGST ($rate%):", left + 6f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", right - 6f, currentY, textPaint)
            currentY += 14f

            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("SGST ($rate%):", left + 6f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", right - 6f, currentY, textPaint)
            currentY += 14f
        }

        currentY += 4f
        paint.color = SOLID_BLACK
        paint.strokeWidth = 1.5f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 18f

        // Grand Total
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 15f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("GRAND TOTAL:", left + 6f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", right - 6f, currentY, textPaint)
        currentY += 16f

        textPaint.textSize = 9.5f
        textPaint.color = Color.rgb(100, 116, 139)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Payment Mode:", left + 6f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = if (bill.paymentMethod == "CREDIT") Color.rgb(220, 38, 38) else Color.rgb(22, 163, 74)
        textPaint.typeface = fontBold
        val mode = if (bill.paymentMethod == "CREDIT") "CREDIT / KHATA DUE" else "${bill.paymentMethod.uppercase()} (PAID)"
        canvas.drawText(mode, right - 6f, currentY, textPaint)
        currentY += 18f

        // Savings Banner
        val totalSavings = bill.discountAmount + bill.pointsRedeemed.toDouble()
        if (totalSavings > 0 && restaurant.showSavingsOnBill) {
            val saveRect = RectF(left, currentY, right, currentY + 24f)
            paint.color = Color.rgb(220, 252, 231)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(saveRect, 4f, 4f, paint)
            textPaint.color = Color.rgb(21, 128, 61)
            textPaint.typeface = fontBold
            textPaint.textSize = 10f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎉 YOU SAVED: ₹${String.format(Locale.US, "%.2f", totalSavings)} ON THIS BILL! 🎉", PAGE_WIDTH / 2f, currentY + 16f, textPaint)
            currentY += 32f
        }

        // Previous Due Box
        if (previousDue > 0 && restaurant.showPreviousDueOnBill) {
            val dueRect = RectF(left, currentY, right, currentY + 28f)
            paint.color = Color.rgb(255, 237, 213)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(dueRect, 4f, 4f, paint)
            textPaint.color = Color.rgb(154, 52, 18)
            textPaint.textSize = 9.5f
            textPaint.typeface = fontBold
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Previous Account Due: ₹${String.format(Locale.US, "%.2f", previousDue)}", left + 10f, currentY + 18f, textPaint)
            val netDue = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Total Outstanding: ₹${String.format(Locale.US, "%.2f", netDue)}", right - 10f, currentY + 18f, textPaint)
            currentY += 36f
        }

        // UPI & Footer
        if (restaurant.upiQrEnabled && restaurant.upiId.isNotBlank()) {
            textPaint.color = SOLID_BLACK
            textPaint.textSize = 10f
            textPaint.typeface = fontBold
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Scan & Pay via UPI: ${restaurant.upiId}", PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }

        if (restaurant.wifiDetails.isNotBlank()) {
            textPaint.color = Color.rgb(71, 85, 105)
            textPaint.textSize = 9f
            canvas.drawText("📶 Free Wi-Fi: ${restaurant.wifiDetails}", PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 12f
        }

        currentY += 8f
        textPaint.color = SOLID_BLACK
        textPaint.typeface = fontBold
        textPaint.textSize = 11f
        canvas.drawText(restaurant.footerNote.ifBlank { "Thank you for visiting BBC Food Hub! Visit Again 😊" }, PAGE_WIDTH / 2f, currentY, textPaint)

        if (restaurant.customTermsNote.isNotBlank()) {
            currentY += 12f
            textPaint.color = Color.rgb(148, 163, 184)
            textPaint.textSize = 8.5f
            canvas.drawText(restaurant.customTermsNote, PAGE_WIDTH / 2f, currentY, textPaint)
        }
    }

    // =========================================================================
    // 1. MODERN SIGNATURE PDF (Clean, Bold Charcoal & Espresso Look)
    // =========================================================================
    private fun drawModernSignaturePdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 40f

        paint.color = Color.rgb(200, 200, 200)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(RectF(20f, 20f, PAGE_WIDTH - 20f, PAGE_HEIGHT - 20f), 8f, 8f, paint)

        // Top Pill Badge
        val badgeW = 120f
        val badgeH = 22f
        val badgeRect = RectF((PAGE_WIDTH - badgeW) / 2f, currentY, (PAGE_WIDTH + badgeW) / 2f, currentY + badgeH)
        paint.color = Color.rgb(74, 46, 27)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(badgeRect, 4f, 4f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("BILL RECEIPT", PAGE_WIDTH / 2f, currentY + 15f, textPaint)
        currentY += badgeH + 26f

        // Restaurant Name: Prominent Bold Solid Black
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 24f
        textPaint.typeface = fontBold
        val cafeName = restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()
        canvas.drawText(cafeName, PAGE_WIDTH / 2f, currentY, textPaint)
        currentY += 18f

        // Address & Phone: Solid Black
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 10.5f
        textPaint.typeface = fontNormal
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText("Ph: ${restaurant.phone}", PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }

        val showFssai = restaurant.isFssaiEnabled && restaurant.showFssaiOnBill && restaurant.fssaiNumber.isNotBlank()
        val showGst = restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstNumber.isNotBlank()
        if (showGst || showFssai) {
            val regInfo = buildString {
                if (showGst) append("GSTIN: ${restaurant.gstNumber}")
                if (showGst && showFssai) append("   |   ")
                if (showFssai) append("FSSAI: ${restaurant.fssaiNumber}")
            }
            textPaint.typeface = fontBold
            textPaint.color = SOLID_BLACK
            canvas.drawText(regInfo, PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }

        currentY += 6f
        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 16f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        val dateFormatted = sdf.format(Date(bill.billTimestamp))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 11f
        textPaint.typeface = fontBold
        canvas.drawText("Bill: #${bill.billNumber}", left, currentY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateFormatted, right, currentY, textPaint)
        currentY += 16f

        val orderTypeLabel = if (bill.orderType == "DINE_IN") "Order Type: DINE-IN (${bill.tableName ?: "Counter"})" else "Order Type: TAKEAWAY / PARCEL"
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = fontBold
        canvas.drawText(orderTypeLabel, left, currentY, textPaint)

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            val custText = buildString {
                if (bill.customerName.isNotBlank()) append("Customer: ${bill.customerName}")
                if (bill.customerPhone.isNotBlank()) {
                    if (isNotEmpty()) append(" (${bill.customerPhone})") else append("Phone: ${bill.customerPhone}")
                }
            }
            canvas.drawText(custText, right, currentY, textPaint)
        }
        currentY += 16f

        paint.color = Color.rgb(200, 200, 200)
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 16f

        // Items Table Header
        val colSr = left + 8f
        val colItem = left + 32f
        val colRate = right - 160f
        val colQty = right - 90f
        val colTotal = right - 8f

        paint.color = Color.rgb(243, 244, 246)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(left, currentY, right, currentY + 24f), 4f, 4f, paint)

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 11f
        textPaint.typeface = fontBold

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("#", colSr, currentY + 16f, textPaint)
        canvas.drawText("ITEM DESCRIPTION", colItem, currentY + 16f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", colRate, currentY + 16f, textPaint)
        canvas.drawText("QTY", colQty, currentY + 16f, textPaint)
        canvas.drawText("TOTAL", colTotal, currentY + 16f, textPaint)

        currentY += 30f

        val rowHeight = 22f
        textPaint.textSize = 11f

        // Items: bold dish names, bold quantities, bold amounts, all SOLID_BLACK
        bill.items.forEachIndexed { index, item ->
            textPaint.color = SOLID_BLACK
            textPaint.typeface = fontBold
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${index + 1}", colSr, currentY + 14f, textPaint)

            val dishDisplay = if (item.dishName.length > 36) item.dishName.substring(0, 35) + "…" else item.dishName
            canvas.drawText(dishDisplay, colItem, currentY + 14f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", colRate, currentY + 14f, textPaint)
            canvas.drawText("x${item.quantity}", colQty, currentY + 14f, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY + 14f, textPaint)

            currentY += rowHeight
        }

        currentY += 6f
        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 16f

        // Subtotal & Totals
        textPaint.textSize = 11.5f
        textPaint.typeface = fontBold
        textPaint.color = SOLID_BLACK

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Subtotal", right - 200f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 16f

        if (bill.discountAmount > 0.0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            canvas.drawText("Discount", right - 200f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", colTotal, currentY, textPaint)
            yAdvance(16f)
        }

        // Total Amount: Bold Larger Font
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 14f
        textPaint.typeface = fontBold
        canvas.drawText("Total Amount", right - 200f, currentY + 4f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", colTotal, currentY + 4f, textPaint)
        currentY += 32f

        // DUES & KHATA STATUS BOX
        val hasDues = previousDue > 0.0 || bill.paymentMethod == "CREDIT"
        if (hasDues) {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 84f
        } else {
            val badgeR = RectF(left, currentY, left + 180f, currentY + 26f)
            paint.color = Color.rgb(220, 252, 231)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(badgeR, 4f, 4f, paint)
            paint.color = Color.rgb(22, 163, 74)
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(badgeR, 4f, 4f, paint)

            textPaint.color = Color.rgb(22, 163, 74)
            textPaint.textSize = 10f
            textPaint.typeface = fontBold
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("PAID IN FULL (${bill.paymentMethod})", badgeR.centerX(), badgeR.centerY() + 3.5f, textPaint)
            currentY += 40f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 2. GST TAX INVOICE PDF
    // =========================================================================
    private fun drawGstTaxInvoicePdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 40f

        paint.color = Color.rgb(30, 58, 138)
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, currentY, right, currentY + 30f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 13f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("TAX INVOICE / CASH BILL", PAGE_WIDTH / 2f, currentY + 20f, textPaint)
        currentY += 44f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 20f
        textPaint.typeface = fontBold
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), left, currentY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 11.5f
        canvas.drawText("Invoice #: ${bill.billNumber}", right, currentY, textPaint)
        currentY += 18f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, left, currentY, textPaint)
        }
        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", right, currentY, textPaint)
        currentY += 15f

        textPaint.textAlign = Paint.Align.LEFT
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText("Phone: ${restaurant.phone}", left, currentY, textPaint)
        }
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Order Type: ${bill.orderType}", right, currentY, textPaint)
        currentY += 15f

        val gstStr = if (restaurant.gstNumber.isNotBlank()) "GSTIN: ${restaurant.gstNumber}" else "GSTIN: UNREGISTERED"
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(gstStr, left, currentY, textPaint)

        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Billed To: ${bill.customerName} (${bill.customerPhone})", right, currentY, textPaint)
        }
        currentY += 20f

        // Table Header
        paint.color = Color.rgb(30, 58, 138)
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, currentY, right, currentY + 24f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT

        val colSr = left + 8f
        val colItem = left + 32f
        val colRate = right - 160f
        val colQty = right - 90f
        val colTotal = right - 8f

        canvas.drawText("S.No", colSr, currentY + 16f, textPaint)
        canvas.drawText("Item Description", colItem, currentY + 16f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Rate (₹)", colRate, currentY + 16f, textPaint)
        canvas.drawText("Qty", colQty, currentY + 16f, textPaint)
        canvas.drawText("Amount (₹)", colTotal, currentY + 16f, textPaint)
        currentY += 28f

        val rowHeight = 22f
        textPaint.textSize = 11f
        textPaint.color = SOLID_BLACK

        bill.items.forEachIndexed { index, item ->
            val selectedAddons = item.getSelectedAddonsList()
            val basePrice = (item.unitPrice - item.calculateAddonsTotal()).coerceAtLeast(0.0)
            val baseTotal = basePrice * item.quantity
            val displayName = item.getFormattedDisplayName()

            textPaint.typeface = fontBold
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${index + 1}", colSr, currentY + 14f, textPaint)

            val name = if (displayName.length > 34) displayName.substring(0, 33) + "…" else displayName
            canvas.drawText(name, colItem, currentY + 14f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            if (selectedAddons.isNotEmpty()) {
                canvas.drawText("₹${String.format(Locale.US, "%.2f", basePrice)}", colRate, currentY + 14f, textPaint)
                canvas.drawText("${item.quantity}", colQty, currentY + 14f, textPaint)
                canvas.drawText("₹${String.format(Locale.US, "%.2f", baseTotal)}", colTotal, currentY + 14f, textPaint)
            } else {
                canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", colRate, currentY + 14f, textPaint)
                canvas.drawText("${item.quantity}", colQty, currentY + 14f, textPaint)
                canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY + 14f, textPaint)
            }

            currentY += rowHeight

            // Add-ons sub-rows in PDF
            selectedAddons.forEach { addon ->
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.textSize = 9.5f
                textPaint.color = Color.rgb(180, 83, 9)

                textPaint.textAlign = Paint.Align.LEFT
                val addonText = "  ↳ + ${addon.name}"
                val safeAddonText = if (addonText.length > 34) addonText.substring(0, 33) + "…" else addonText
                canvas.drawText(safeAddonText, colItem, currentY + 12f, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                val totalAddonQty = addon.quantity * item.quantity
                canvas.drawText("₹${String.format(Locale.US, "%.2f", addon.unitPrice)}", colRate, currentY + 12f, textPaint)
                canvas.drawText("$totalAddonQty", colQty, currentY + 12f, textPaint)
                canvas.drawText("₹${String.format(Locale.US, "%.2f", addon.totalPrice * item.quantity)}", colTotal, currentY + 12f, textPaint)

                currentY += 18f
            }
            textPaint.textSize = 11f
            textPaint.color = SOLID_BLACK
        }

        currentY += 6f
        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 16f

        textPaint.textSize = 11.5f
        textPaint.typeface = fontBold
        textPaint.color = SOLID_BLACK

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Taxable Subtotal", right - 220f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 16f

        if (restaurant.isGstEnabled) {
            val halfRate = restaurant.gstRate / 2.0
            val taxable = (bill.subtotal - bill.discountAmount).coerceAtLeast(0.0)
            val halfGst = taxable * (halfRate / 100.0)

            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("CGST ($halfRate%)", right - 220f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", colTotal, currentY, textPaint)
            currentY += 16f

            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("SGST ($halfRate%)", right - 220f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", colTotal, currentY, textPaint)
            currentY += 16f
        }

        val totalBoxRect = RectF(right - 230f, currentY, right, currentY + 28f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawRoundRect(totalBoxRect, 6f, 6f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 13f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL AMOUNT:", totalBoxRect.left + 10f, totalBoxRect.centerY() + 4.5f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", totalBoxRect.right - 10f, totalBoxRect.centerY() + 4.5f, textPaint)
        currentY += 40f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 84f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 3. MINIMAL CLEAN PDF
    // =========================================================================
    private fun drawMinimalCleanPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 46f

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 24f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }, left, currentY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 12f
        canvas.drawText("#${bill.billNumber}", right, currentY, textPaint)
        currentY += 20f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, left, currentY, textPaint)
            currentY += 15f
        }
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText(restaurant.phone, left, currentY, textPaint)
            currentY += 15f
        }

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", left, currentY, textPaint)
        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Customer: ${bill.customerName}", right, currentY, textPaint)
        }
        currentY += 20f

        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 1.5f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 20f

        val colItem = left
        val colRate = right - 160f
        val colQty = right - 90f
        val colTotal = right

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = fontBold
        textPaint.textSize = 11.5f
        canvas.drawText("ITEM", colItem, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", colRate, currentY, textPaint)
        canvas.drawText("QTY", colQty, currentY, textPaint)
        canvas.drawText("TOTAL", colTotal, currentY, textPaint)
        currentY += 16f

        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 18f

        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = fontBold
            textPaint.textSize = 11f
            val name = if (item.dishName.length > 34) item.dishName.substring(0, 33) + "…" else item.dishName
            canvas.drawText(name, colItem, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", colRate, currentY, textPaint)
            canvas.drawText("${item.quantity}", colQty, currentY, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY, textPaint)
            currentY += 22f
        }

        currentY += 8f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 20f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = fontBold
        textPaint.textSize = 12f
        canvas.drawText("Subtotal", right - 200f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 18f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 14f
        canvas.drawText("Total Amount", right - 200f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", colTotal, currentY, textPaint)
        currentY += 34f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 84f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 4. CLASSIC THERMAL PDF (80mm width proportion)
    // =========================================================================
    private fun drawClassicThermalPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        // Centered 80mm column
        val slipWidth = 380f
        val left = (PAGE_WIDTH - slipWidth) / 2f
        val right = left + slipWidth
        var currentY = 46f

        // Outer Dashed Border
        drawDashedLine(canvas, left - 12f, right + 12f, 20f, paint)
        drawDashedLine(canvas, left - 12f, right + 12f, PAGE_HEIGHT - 20f, paint)

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 22f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), PAGE_WIDTH / 2f, currentY, textPaint)
        currentY += 18f

        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText("Ph: ${restaurant.phone}", PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }

        currentY += 6f
        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 16f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("INV: #${bill.billNumber}", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(sdf.format(Date(bill.billTimestamp)), right, currentY, textPaint)
        currentY += 14f

        val orderTypeLabel = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Takeaway"
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(orderTypeLabel, left, currentY, textPaint)
        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(bill.customerName, right, currentY, textPaint)
        }
        currentY += 14f

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 16f

        val colItem = left
        val colQty = right - 90f
        val colTotal = right

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM", colItem, currentY, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", colQty, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", colTotal, currentY, textPaint)
        currentY += 14f

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 16f

        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            val name = if (item.dishName.length > 22) item.dishName.substring(0, 21) + "…" else item.dishName
            canvas.drawText(name, colItem, currentY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", colQty, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY, textPaint)
            currentY += 18f
        }

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 16f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Subtotal:", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", right, currentY, textPaint)
        currentY += 16f

        textPaint.textSize = 13f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL PAYABLE:", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", right, currentY, textPaint)
        currentY += 28f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 84f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 5. COMPACT MINI PDF (58mm narrow thermal slip)
    // =========================================================================
    private fun drawCompactMiniPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        // Centered 58mm narrow column
        val slipWidth = 280f
        val left = (PAGE_WIDTH - slipWidth) / 2f
        val right = left + slipWidth
        var currentY = 50f

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 19f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), PAGE_WIDTH / 2f, currentY, textPaint)
        currentY += 16f

        textPaint.textSize = 9.5f
        textPaint.typeface = fontBold
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 13f
        }
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText("Ph: ${restaurant.phone}", PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 13f
        }

        currentY += 4f
        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 14f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("INV: #${bill.billNumber}", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        val sdf = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.US)
        canvas.drawText(sdf.format(Date(bill.billTimestamp)), right, currentY, textPaint)
        currentY += 13f

        val orderTypeLabel = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "C"}" else "Takeaway"
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(orderTypeLabel, left, currentY, textPaint)
        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(bill.customerName, right, currentY, textPaint)
        }
        currentY += 13f

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 14f

        val colItem = left
        val colQty = right - 70f
        val colTotal = right

        canvas.drawText("ITEM", colItem, currentY, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", colQty, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMT", colTotal, currentY, textPaint)
        currentY += 13f

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 14f

        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            val name = if (item.dishName.length > 17) item.dishName.substring(0, 16) + "…" else item.dishName
            canvas.drawText(name, colItem, currentY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", colQty, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format(Locale.US, "%.2f", item.totalPrice), colTotal, currentY, textPaint)
            currentY += 16f
        }

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 14f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL:", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", right, currentY, textPaint)
        currentY += 26f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 80f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 6. RECEIPT BOX PDF (Khata & Dues Focus)
    // =========================================================================
    private fun drawReceiptBoxPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 40f

        // Top Border Box Header
        val headerRect = RectF(left, currentY, right, currentY + 68f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(239, 246, 255)
        canvas.drawRoundRect(headerRect, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(30, 58, 138)
        paint.strokeWidth = 2f
        canvas.drawRoundRect(headerRect, 8f, 8f, paint)

        textPaint.color = Color.rgb(30, 58, 138)
        textPaint.textSize = 22f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), PAGE_WIDTH / 2f, currentY + 28f, textPaint)

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 10f
        val subInfo = buildString {
            if (restaurant.phone.isNotBlank()) append("Ph: ${restaurant.phone}  •  ")
            if (restaurant.address.isNotBlank()) append(restaurant.address)
        }
        canvas.drawText(subInfo.ifBlank { "Customer Receipt" }, PAGE_WIDTH / 2f, currentY + 48f, textPaint)
        currentY += 82f

        // Top Highlighted Khata Box if due exists
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 82f
        }

        val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.US)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        canvas.drawText("BILL NO: #${bill.billNumber}", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("DATE: ${sdf.format(Date(bill.billTimestamp))}", right, currentY, textPaint)
        currentY += 16f

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("CUSTOMER: ${bill.customerName} ${bill.customerPhone}", left, currentY, textPaint)
            currentY += 16f
        }

        // Table Header
        val colItem = left
        val colQty = right - 120f
        val colTotal = right

        paint.color = Color.rgb(30, 58, 138)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(left, currentY, right, currentY + 22f), 4f, 4f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM", colItem + 8f, currentY + 15f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", colQty, currentY + 15f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL", colTotal - 8f, currentY + 15f, textPaint)
        currentY += 28f

        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = fontBold
            textPaint.color = SOLID_BLACK
            textPaint.textSize = 11f
            canvas.drawText(item.dishName, colItem + 8f, currentY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", colQty, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal - 8f, currentY, textPaint)
            currentY += 20f
        }

        currentY += 8f
        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 18f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = fontBold
        textPaint.textSize = 11.5f
        canvas.drawText("Subtotal:", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 16f

        val totalBoxRect = RectF(left, currentY, right, currentY + 26f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawRoundRect(totalBoxRect, 6f, 6f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 13f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Grand Total Payable:", totalBoxRect.left + 10f, totalBoxRect.centerY() + 4f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", totalBoxRect.right - 10f, totalBoxRect.centerY() + 4f, textPaint)
        currentY += 40f

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 7. RETRO FOODIE PDF
    // =========================================================================
    private fun drawRetroFoodiePdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.rgb(254, 252, 232)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 40f

        drawDashedLine(canvas, 20f, PAGE_WIDTH - 20f, 20f, paint)
        drawDashedLine(canvas, 20f, PAGE_WIDTH - 20f, PAGE_HEIGHT - 20f, paint)

        // Crimson Banner
        val bannerRect = RectF(left, currentY, right, currentY + 54f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(185, 28, 28)
        canvas.drawRoundRect(bannerRect, 8f, 8f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 20f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        val cafeName = "★ ${restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()} ★"
        canvas.drawText(cafeName, PAGE_WIDTH / 2f, currentY + 26f, textPaint)

        textPaint.textSize = 10f
        textPaint.color = Color.rgb(254, 226, 226)
        canvas.drawText("Delicious Food & Good Times", PAGE_WIDTH / 2f, currentY + 44f, textPaint)
        currentY += 68f

        val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.US)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = fontBold
        textPaint.textSize = 11f
        canvas.drawText("BILL #${bill.billNumber}", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(sdf.format(Date(bill.billTimestamp)), right, currentY, textPaint)
        currentY += 16f

        val colItem = left
        val colQty = right - 120f
        val colTotal = right

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 16f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM DESCRIPTION", colItem, currentY, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", colQty, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", colTotal, currentY, textPaint)
        currentY += 14f

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 16f

        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = fontBold
            textPaint.color = SOLID_BLACK
            textPaint.textSize = 11f
            val name = if (item.dishName.length > 32) item.dishName.substring(0, 31) + "…" else item.dishName
            canvas.drawText(name, colItem, currentY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", colQty, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY, textPaint)
            currentY += 20f
        }

        drawDashedLine(canvas, left, right, currentY, paint)
        currentY += 16f

        val totalBoxRect = RectF(left, currentY, right, currentY + 28f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(185, 28, 28)
        canvas.drawRoundRect(totalBoxRect, 6f, 6f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = fontBold
        textPaint.textSize = 13f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL AMOUNT:", totalBoxRect.left + 10f, totalBoxRect.centerY() + 4f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", totalBoxRect.right - 10f, totalBoxRect.centerY() + 4f, textPaint)
        currentY += 40f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 84f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 8. ELEGANT DINE IN PDF
    // =========================================================================
    private fun drawElegantDineInPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.rgb(255, 251, 240)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val outerMargin = 20f
        paint.color = Color.rgb(217, 119, 6)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(outerMargin, outerMargin, PAGE_WIDTH - outerMargin, PAGE_HEIGHT - outerMargin), 10f, 10f, paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 52f

        textPaint.color = Color.rgb(120, 53, 15)
        textPaint.textSize = 24f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        val cafeName = "☕ ${restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()} ☕"
        canvas.drawText(cafeName, PAGE_WIDTH / 2f, currentY, textPaint)
        currentY += 18f

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 11.5f
        textPaint.typeface = fontItalic
        canvas.drawText("Bistro & Cafe Experience", PAGE_WIDTH / 2f, currentY, textPaint)
        currentY += 16f

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText("Contact: ${restaurant.phone}", PAGE_WIDTH / 2f, currentY, textPaint)
            currentY += 14f
        }

        currentY += 6f
        paint.color = Color.rgb(217, 119, 6)
        paint.strokeWidth = 1.2f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 18f

        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US)
        val dateFormatted = sdf.format(Date(bill.billTimestamp))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 11f
        textPaint.typeface = fontBold
        val orderTypeLabel = if (bill.orderType == "DINE_IN") "Type: DINE-IN (${bill.tableName ?: "Counter"})" else "Type: TAKEAWAY / PARCEL"
        canvas.drawText(orderTypeLabel, left, currentY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("INVOICE #${bill.billNumber}", right, currentY, textPaint)
        currentY += 15f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date & Time: $dateFormatted", left, currentY, textPaint)

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            val custText = buildString {
                if (bill.customerName.isNotBlank()) append("Customer: ${bill.customerName}")
                if (bill.customerPhone.isNotBlank()) {
                    if (isNotEmpty()) append(" (${bill.customerPhone})") else append("Phone: ${bill.customerPhone}")
                }
            }
            canvas.drawText(custText, right, currentY, textPaint)
        }
        currentY += 18f

        // Items Header
        val tableHeaderY = currentY
        val tableHeaderRect = RectF(left, tableHeaderY, right, tableHeaderY + 24f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(254, 243, 199)
        canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

        textPaint.color = Color.rgb(120, 53, 15)
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold

        val colSr = left + 10f
        val colItem = left + 36f
        val colRate = right - 160f
        val colQty = right - 90f
        val colTotal = right - 10f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("#", colSr, tableHeaderY + 16f, textPaint)
        canvas.drawText("ITEM DESCRIPTION", colItem, tableHeaderY + 16f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", colRate, tableHeaderY + 16f, textPaint)
        canvas.drawText("QTY", colQty, tableHeaderY + 16f, textPaint)
        canvas.drawText("AMOUNT", colTotal, tableHeaderY + 16f, textPaint)
        currentY += 32f

        bill.items.forEachIndexed { index, item ->
            textPaint.color = SOLID_BLACK
            textPaint.typeface = fontBold
            textPaint.textSize = 11f
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${index + 1}", colSr, currentY + 13f, textPaint)

            val name = if (item.dishName.length > 34) item.dishName.substring(0, 33) + "…" else item.dishName
            canvas.drawText(name, colItem, currentY + 13f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", colRate, currentY + 13f, textPaint)
            canvas.drawText("x${item.quantity}", colQty, currentY + 13f, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY + 13f, textPaint)
            currentY += 22f
        }

        currentY += 6f
        paint.color = Color.rgb(217, 119, 6)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 16f

        textPaint.textSize = 11.5f
        textPaint.typeface = fontBold
        textPaint.color = SOLID_BLACK
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Order Subtotal:", right - 220f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 16f

        val netBoxRect = RectF(right - 230f, currentY, right, currentY + 28f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(254, 243, 199)
        canvas.drawRoundRect(netBoxRect, 6f, 6f, paint)

        textPaint.color = Color.rgb(120, 53, 15)
        textPaint.textSize = 12f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("NET PAYABLE:", netBoxRect.left + 10f, netBoxRect.centerY() + 4f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", netBoxRect.right - 10f, netBoxRect.centerY() + 4f, textPaint)
        currentY += 40f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 80f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private fun drawDuesBox(
        canvas: Canvas,
        bill: BillEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        left: Float,
        right: Float,
        currentY: Float
    ) {
        val totalRemaining = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
        val dueBoxHeight = 56f
        val dueBoxRect = RectF(left, currentY, right, currentY + dueBoxHeight)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 237, 213)
        canvas.drawRoundRect(dueBoxRect, 6f, 6f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(234, 88, 12)
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(dueBoxRect, 6f, 6f, paint)

        textPaint.color = Color.rgb(154, 52, 18)
        textPaint.textSize = 10.5f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("CUSTOMER ACCOUNT & OUTSTANDING BALANCE", left + 14f, currentY + 18f, textPaint)

        textPaint.typeface = fontBold
        textPaint.textSize = 10f
        val duesInfo = "Today's Bill: ₹${String.format(Locale.US, "%.2f", bill.totalAmount)}  |  Previous Due: ₹${String.format(Locale.US, "%.2f", previousDue)}  |  Net Due: ₹${String.format(Locale.US, "%.2f", totalRemaining)}"
        canvas.drawText(duesInfo, left + 14f, currentY + 38f, textPaint)
    }

    // =========================================================================
    // 9. FAST FOOD EXPRESS PDF
    // =========================================================================
    private fun drawFastFoodExpressPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 40f

        // Orange Header Banner
        val bannerH = 54f
        val bannerRect = RectF(left, currentY, right, currentY + bannerH)
        paint.color = Color.rgb(234, 88, 12)
        canvas.drawRoundRect(bannerRect, 6f, 6f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 20f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), PAGE_WIDTH / 2f, currentY + 26f, textPaint)

        textPaint.textSize = 10f
        textPaint.color = Color.rgb(255, 237, 213)
        val subTxt = buildString {
            if (restaurant.phone.isNotBlank()) append("Ph: ${restaurant.phone}  •  ")
            if (restaurant.address.isNotBlank()) append(restaurant.address)
        }
        canvas.drawText(subTxt.ifBlank { "Fast Food & Quick Service" }, PAGE_WIDTH / 2f, currentY + 44f, textPaint)
        currentY += bannerH + 16f

        // Token & Type Box
        val boxW = (right - left - 12f) / 2f
        val tokenR = RectF(left, currentY, left + boxW, currentY + 36f)
        paint.color = Color.rgb(255, 247, 237)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(tokenR, 4f, 4f, paint)
        paint.color = Color.rgb(234, 88, 12)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(tokenR, 4f, 4f, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.rgb(194, 65, 12)
        textPaint.textSize = 9f
        textPaint.typeface = fontBold
        canvas.drawText("TOKEN #", tokenR.left + 8f, tokenR.top + 14f, textPaint)
        textPaint.textSize = 14f
        textPaint.color = Color.rgb(234, 88, 12)
        canvas.drawText("#${bill.billNumber}", tokenR.left + 8f, tokenR.top + 30f, textPaint)

        val typeR = RectF(left + boxW + 12f, currentY, right, currentY + 36f)
        val isDineIn = bill.orderType == "DINE_IN"
        paint.color = if (isDineIn) Color.rgb(239, 246, 255) else Color.rgb(254, 242, 242)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(typeR, 4f, 4f, paint)
        paint.color = if (isDineIn) Color.rgb(59, 130, 246) else Color.rgb(239, 68, 68)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(typeR, 4f, 4f, paint)

        textPaint.color = if (isDineIn) Color.rgb(29, 78, 216) else Color.rgb(185, 28, 28)
        textPaint.textSize = 9f
        canvas.drawText("ORDER TYPE", typeR.left + 8f, typeR.top + 14f, textPaint)
        textPaint.textSize = 12f
        val typeLabel = if (isDineIn) "DINE-IN (${bill.tableName ?: "Counter"})" else "TAKEAWAY"
        canvas.drawText(typeLabel, typeR.left + 8f, typeR.top + 30f, textPaint)
        currentY += 48f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 10f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", left, currentY, textPaint)
        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Guest: ${bill.customerName} (${bill.customerPhone})", right, currentY, textPaint)
        }
        currentY += 16f

        // Table Header
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        val colItem = left
        val colQty = right - 120f
        val colTotal = right

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = fontBold
        textPaint.textSize = 10.5f
        canvas.drawText("ITEM DESCRIPTION", colItem, currentY, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", colQty, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", colTotal, currentY, textPaint)
        currentY += 12f

        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = fontBold
            textPaint.textSize = 10.5f
            textPaint.color = SOLID_BLACK
            val name = (if (item.isFree) "🎁 " else "") + item.dishName
            val display = if (name.length > 34) name.substring(0, 33) + "…" else name
            canvas.drawText(display, colItem, currentY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}x", colQty, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY, textPaint)
            currentY += 16f

            if (item.notes.isNotBlank()) {
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = 9f
                textPaint.color = Color.rgb(220, 38, 38)
                textPaint.typeface = fontItalic
                canvas.drawText("  ↳ Note: ${item.notes}", colItem + 8f, currentY, textPaint)
                currentY += 12f
            }
        }

        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = fontBold
        textPaint.textSize = 11f
        canvas.drawText("Subtotal", right - 180f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 14f

        val netBox = RectF(right - 200f, currentY, right, currentY + 24f)
        paint.color = Color.rgb(234, 88, 12)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(netBox, 4f, 4f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 12f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL AMOUNT", netBox.left + 8f, netBox.centerY() + 4f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", netBox.right - 8f, netBox.centerY() + 4f, textPaint)
        currentY += 36f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 80f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 10. ROYAL PREMIUM PDF
    // =========================================================================
    private fun drawRoyalPremiumPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 40f

        // Gold Outer Frame
        paint.color = Color.rgb(212, 175, 55)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(18f, 18f, PAGE_WIDTH - 18f, PAGE_HEIGHT - 18f), 6f, 6f, paint)

        // Navy Header
        val headerH = 60f
        val headerR = RectF(left, currentY, right, currentY + headerH)
        paint.color = Color.rgb(15, 23, 42)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(headerR, 6f, 6f, paint)

        textPaint.color = Color.rgb(253, 224, 71)
        textPaint.textSize = 20f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("👑 ${restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()} 👑", PAGE_WIDTH / 2f, currentY + 26f, textPaint)

        textPaint.color = Color.rgb(226, 232, 240)
        textPaint.textSize = 9.5f
        textPaint.typeface = fontNormal
        canvas.drawText("FINE DINING & PREMIUM EXPERIENCE", PAGE_WIDTH / 2f, currentY + 44f, textPaint)
        currentY += headerH + 20f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        textPaint.color = Color.rgb(15, 23, 42)
        textPaint.typeface = fontBold
        textPaint.textSize = 10.5f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Tax Invoice #${bill.billNumber}", left, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", right, currentY, textPaint)
        currentY += 16f

        val orderTypeStr = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Takeaway Order"
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(orderTypeStr, left, currentY, textPaint)
        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Guest: ${bill.customerName}", right, currentY, textPaint)
        }
        currentY += 18f

        paint.color = Color.rgb(212, 175, 55)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        val colItem = left
        val colRate = right - 160f
        val colQty = right - 90f
        val colTotal = right

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = fontBold
        textPaint.textSize = 10.5f
        textPaint.color = Color.rgb(15, 23, 42)
        canvas.drawText("ITEM DESCRIPTION", colItem, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", colRate, currentY, textPaint)
        canvas.drawText("QTY", colQty, currentY, textPaint)
        canvas.drawText("AMOUNT", colTotal, currentY, textPaint)
        currentY += 12f

        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = SOLID_BLACK
            textPaint.typeface = fontBold
            textPaint.textSize = 10.5f
            val name = (if (item.isFree) "🎁 " else "") + item.dishName
            val display = if (name.length > 32) name.substring(0, 31) + "…" else name
            canvas.drawText(display, colItem, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", colRate, currentY, textPaint)
            canvas.drawText("${item.quantity}", colQty, currentY, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal, currentY, textPaint)
            currentY += 16f
        }

        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = fontBold
        textPaint.textSize = 11f
        canvas.drawText("Subtotal", right - 180f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 14f

        val totalBox = RectF(right - 200f, currentY, right, currentY + 24f)
        paint.color = Color.rgb(254, 243, 199)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(totalBox, 4f, 4f, paint)
        paint.color = Color.rgb(212, 175, 55)
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(totalBox, 4f, 4f, paint)

        textPaint.color = Color.rgb(120, 53, 15)
        textPaint.textSize = 12f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Grand Total", totalBox.left + 8f, totalBox.centerY() + 4f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", totalBox.right - 8f, totalBox.centerY() + 4f, textPaint)
        currentY += 36f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 80f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    // =========================================================================
    // 11. KITCHEN DETAILED PDF
    // =========================================================================
    private fun drawKitchenDetailedPdf(
        canvas: Canvas,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        fontItalic: Typeface
    ) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        val left = 40f
        val right = PAGE_WIDTH - 40f
        var currentY = 40f

        val headerR = RectF(left, currentY, right, currentY + 54f)
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRoundRect(headerR, 6f, 6f, paint)
        paint.color = Color.rgb(71, 85, 105)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(headerR, 6f, 6f, paint)

        textPaint.color = Color.rgb(15, 23, 42)
        textPaint.textSize = 18f
        textPaint.typeface = fontBold
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), left + 12f, currentY + 24f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 11f
        canvas.drawText("ITEMIZED RECEIPT", right - 12f, currentY + 24f, textPaint)

        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 10f
        textPaint.typeface = fontNormal
        val subH = "Bill #${bill.billNumber}  |  ${sdf.format(Date(bill.billTimestamp))}  |  ${if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Takeaway"}"
        canvas.drawText(subH, left + 12f, currentY + 42f, textPaint)
        currentY += 68f

        val colItem = left
        val colQty = right - 120f
        val colTotal = right

        paint.color = Color.rgb(51, 65, 85)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(left, currentY, right, currentY + 20f), 4f, 4f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = fontBold
        textPaint.textSize = 10f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Item Description & Instructions", colItem + 8f, currentY + 14f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Qty", colQty, currentY + 14f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total", colTotal - 8f, currentY + 14f, textPaint)
        currentY += 26f

        bill.items.forEachIndexed { index, item ->
            textPaint.color = SOLID_BLACK
            textPaint.typeface = fontBold
            textPaint.textSize = 10.5f
            textPaint.textAlign = Paint.Align.LEFT
            val name = "${index + 1}. " + (if (item.isFree) "🎁 " else "") + item.dishName
            val display = if (name.length > 34) name.substring(0, 33) + "…" else name
            canvas.drawText(display, colItem + 4f, currentY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", colQty, currentY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", colTotal - 4f, currentY, textPaint)
            currentY += 15f

            if (item.notes.isNotBlank()) {
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = 9f
                textPaint.color = Color.rgb(220, 38, 38)
                textPaint.typeface = fontItalic
                canvas.drawText("   📝 Instruction: ${item.notes}", colItem + 10f, currentY, textPaint)
                currentY += 12f
            }
        }

        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawLine(left, currentY, right, currentY, paint)
        currentY += 14f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = fontBold
        textPaint.textSize = 11f
        canvas.drawText("Subtotal", right - 180f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", colTotal, currentY, textPaint)
        currentY += 14f

        val totalBox = RectF(right - 200f, currentY, right, currentY + 24f)
        paint.color = Color.rgb(15, 23, 42)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(totalBox, 4f, 4f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 12f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Total Amount", totalBox.left + 8f, totalBox.centerY() + 4f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", totalBox.right - 8f, totalBox.centerY() + 4f, textPaint)
        currentY += 36f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            drawDuesBox(canvas, bill, previousDue, paint, textPaint, fontBold, fontNormal, left, right, currentY)
            currentY += 80f
        }

        drawCleanFooter(canvas, restaurant, bill, fontNormal, fontItalic, textPaint, paint, left, right)
    }

    private fun drawCleanFooter(
        canvas: Canvas,
        restaurant: RestaurantEntity,
        bill: BillEntity,
        fontNormal: Typeface,
        fontItalic: Typeface,
        textPaint: Paint,
        paint: Paint,
        left: Float,
        right: Float
    ) {
        val footerY = PAGE_HEIGHT - 44f
        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(left, footerY - 14f, right, footerY - 14f, paint)

        val defaultFooter = if (bill.orderType == "TAKEAWAY") {
            "Thank you for your order! Enjoy your delicious meal & see you soon."
        } else {
            "Thank you for dining with us! We look forward to welcoming you again."
        }
        val displayFooter = if (restaurant.footerNote.isNotBlank() && restaurant.footerNote != "Thank you for visiting! Please visit again.") {
            restaurant.footerNote
        } else {
            defaultFooter
        }

        textPaint.color = SOLID_BLACK
        textPaint.textSize = 10f
        textPaint.typeface = fontItalic
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(displayFooter, PAGE_WIDTH / 2f, footerY, textPaint)

        textPaint.textSize = 8.5f
        textPaint.typeface = fontNormal
        canvas.drawText("Generated by BBC Food Hub POS System", PAGE_WIDTH / 2f, footerY + 14f, textPaint)
    }

    private fun drawDashedLine(canvas: Canvas, startX: Float, endX: Float, y: Float, paint: Paint) {
        paint.color = SOLID_BLACK
        paint.strokeWidth = 1.2f
        paint.style = Paint.Style.STROKE
        paint.pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        val path = Path()
        path.moveTo(startX, y)
        path.lineTo(endX, y)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
    }

    private fun yAdvance(amount: Float) {
        // Helper
    }
}
