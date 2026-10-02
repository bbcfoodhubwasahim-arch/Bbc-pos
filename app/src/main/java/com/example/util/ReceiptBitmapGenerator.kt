package com.example.util

import android.content.Context
import android.graphics.*
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.RestaurantEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReceiptBitmapGenerator {

    // High contrast solid dark black text on all white/light backgrounds
    private val SOLID_BLACK = Color.rgb(26, 26, 26) // #1A1A1A

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * Generates a high-quality, high-resolution (High DPI) Bitmap representing the receipt image.
     * Supports all 8 handcrafted bill template designs:
     * - MODERN_SIGNATURE
     * - RECEIPT_BOX
     * - GST_TAX_INVOICE
     * - ELEGANT_DINE_IN
     * - MINIMAL_CLEAN
     * - RETRO_FOODIE
     * - COMPACT_MINI (58mm)
     * - CLASSIC_THERMAL (80mm)
     */
    fun generateReceiptBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val template = com.example.ui.components.BillTemplateDesign.fromId(restaurant.billFormat)
        return when (template) {
            com.example.ui.components.BillTemplateDesign.UNIVERSAL_CAFE_PRO,
            com.example.ui.components.BillTemplateDesign.ELEGANT_DINE_IN -> generateUniversalCafeProBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.MODERN_SIGNATURE -> generateModernSignatureBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.RECEIPT_BOX -> generateReceiptBoxBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.GST_TAX_INVOICE -> generateGstTaxInvoiceBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.MINIMAL_CLEAN -> generateMinimalCleanBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.RETRO_FOODIE -> generateRetroFoodieBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.COMPACT_MINI -> generateThermalBitmap(context, bill, restaurant, previousDue, "THERMAL_2_INCH")
            com.example.ui.components.BillTemplateDesign.CLASSIC_THERMAL -> generateThermalBitmap(context, bill, restaurant, previousDue, "THERMAL_3_INCH")
            com.example.ui.components.BillTemplateDesign.FAST_FOOD_EXPRESS -> generateFastFoodExpressBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.ROYAL_PREMIUM -> generateRoyalPremiumBitmap(context, bill, restaurant, previousDue)
            com.example.ui.components.BillTemplateDesign.KITCHEN_DETAILED -> generateKitchenDetailedBitmap(context, bill, restaurant, previousDue)
        }
    }

    // ==========================================
    // 0. UNIVERSAL CAFE PRO (ULTRA-SHARP B&W FLAGSHIP)
    // ==========================================
    private fun generateUniversalCafeProBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        // Optimized 1080px Full HD width for 100% crisp WhatsApp delivery with zero blurring
        val width = 1080
        val padding = 44f
        val bodySize = 28f
        val smallSize = 24f
        val lineHeight = 46f

        var estimatedHeight = padding * 2 + 500f
        if (restaurant.showLogoOnBill) estimatedHeight += 180f
        if (restaurant.address.isNotBlank()) estimatedHeight += lineHeight
        if (restaurant.phone.isNotBlank() || restaurant.altPhone.isNotBlank()) estimatedHeight += lineHeight
        if (restaurant.tagline.isNotBlank()) estimatedHeight += lineHeight
        estimatedHeight += (bill.items.size * (lineHeight + 50f)) + lineHeight * 14
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4f
        estimatedHeight += 350f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val solidLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val thinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 200, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        var y = padding + 24f

        // 1. Logo (Only if showLogoOnBill is enabled)
        if (restaurant.showLogoOnBill) {
            var logoDrawn = false
            if (!restaurant.customLogoUri.isNullOrBlank()) {
                try {
                    val uri = android.net.Uri.parse(restaurant.customLogoUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val logoBmp = android.graphics.BitmapFactory.decodeStream(inputStream)
                    if (logoBmp != null) {
                        val scaledLogo = Bitmap.createScaledBitmap(logoBmp, 160, 160, true)
                        canvas.drawBitmap(scaledLogo, (width - 160) / 2f, y, paint)
                        y += 160f + 40f // Clean 40px gap between logo and cafe name
                        logoDrawn = true
                    }
                } catch (_: Exception) {}
            }
            if (!logoDrawn) {
                // Crisp Black BBC Square Badge
                paint.color = Color.BLACK
                paint.style = Paint.Style.FILL
                val badgeRect = RectF((width - 120) / 2f, y, (width + 120) / 2f, y + 100f)
                canvas.drawRect(badgeRect, paint)
                textPaint.color = Color.WHITE
                textPaint.textSize = 40f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("BBC", width / 2f, y + 64f, textPaint)
                y += 100f + 40f // Clean 40px gap between badge and cafe name
            }
        }

        // 2. Cafe Name & Tagline
        y += 36f
        textPaint.color = Color.BLACK
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 50f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), width / 2f, y, textPaint)

        if (restaurant.tagline.isNotBlank()) {
            y += 42f
            textPaint.textSize = 24f
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            textPaint.color = Color.rgb(50, 50, 50)
            canvas.drawText(restaurant.tagline, width / 2f, y, textPaint)
        }

        if (restaurant.address.isNotBlank()) {
            y += 38f
            textPaint.textSize = 23f
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textPaint.color = Color.BLACK
            canvas.drawText(restaurant.address, width / 2f, y, textPaint)
        }

        val phones = listOfNotNull(
            restaurant.phone.takeIf { it.isNotBlank() },
            restaurant.altPhone.takeIf { it.isNotBlank() }
        ).joinToString(" / ")
        if (phones.isNotBlank()) {
            y += 34f
            textPaint.textSize = 23f
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.color = Color.BLACK
            canvas.drawText("Contact: $phones", width / 2f, y, textPaint)
        }

        // FSSAI / GSTIN
        val regList = mutableListOf<String>()
        if (restaurant.isFssaiEnabled && restaurant.showFssaiOnBill && restaurant.fssaiNumber.isNotBlank()) regList.add("FSSAI: ${restaurant.fssaiNumber}")
        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstNumber.isNotBlank()) regList.add("GSTIN: ${restaurant.gstNumber}")
        if (regList.isNotEmpty()) {
            y += 34f
            textPaint.textSize = 22f
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.color = Color.BLACK
            canvas.drawText(regList.joinToString(" • "), width / 2f, y, textPaint)
        }

        y += 24f
        canvas.drawLine(padding, y, width - padding, y, solidLinePaint)
        y += 20f

        // 3. Token & Order Type Banner Box (Square frame)
        val bannerHeight = 120f
        val bannerRect = RectF(padding, y, width - padding, y + bannerHeight)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(bannerRect, paint)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(bannerRect, paint)

        val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(bill.billTimestamp))
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.BLACK
        textPaint.textSize = 28f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val tokenText = if (restaurant.showTokenOnBill) "TOKEN #${bill.billNumber}" else "Bill #${bill.billNumber}"
        canvas.drawText(tokenText, padding + 20f, y + 46f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = Color.BLACK
        textPaint.textSize = 26f
        val orderLabel = when {
            !bill.tableName.isNullOrBlank() -> "Table: ${bill.tableName} (Dine-In)"
            bill.orderType.equals("TAKEAWAY", ignoreCase = true) -> "Takeaway / Parcel"
            bill.orderType.equals("DELIVERY", ignoreCase = true) -> "Delivery"
            else -> "Dine-In"
        }
        canvas.drawText(orderLabel, width - padding - 20f, y + 46f, textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.BLACK
        textPaint.textSize = 22f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("Date: $dateStr", padding + 20f, y + 92f, textPaint)

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.BLACK
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            val custText = "Cust: ${bill.customerName.ifBlank { "Guest" }} ${if (bill.customerPhone.isNotBlank()) "(${bill.customerPhone})" else ""}"
            canvas.drawText(custText, width - padding - 20f, y + 92f, textPaint)
        }

        y += bannerHeight + 28f

        // 4. Table Header (Solid Black lines & ITEM label)
        canvas.drawLine(padding, y, width - padding, y, solidLinePaint)
        y += 32f

        textPaint.color = Color.BLACK
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 26f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM", padding + 12f, y, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", width - padding - 310f, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", width - padding - 160f, y, textPaint)
        canvas.drawText("AMOUNT", width - padding - 12f, y, textPaint)

        y += 14f
        canvas.drawLine(padding, y, width - padding, y, solidLinePaint)
        y += 24f

        // 5. Table Items
        bill.items.forEach { item ->
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.textSize = 26f
            textPaint.color = Color.BLACK

            val isItemFree = item.isFree || item.totalPrice == 0.0

            // Clean up any repeated or nested tags from dish name
            val rawName = item.dishName
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\(.*?Free.*?\\)", RegexOption.IGNORE_CASE), "")
                .replace("(🎁 Free Reward)", "")
                .replace("[🎁FREE]", "")
                .replace("[FREE]", "")
                .trim()
            val cleanName = if (rawName.isBlank()) item.dishName else rawName
            val displayName = if (isItemFree) {
                if (item.notes.contains("Complimentary", ignoreCase = true)) {
                    "$cleanName [🎁 COMPLIMENTARY]"
                } else {
                    "$cleanName [🎁 FREE REWARD]"
                }
            } else {
                item.dishName
            }

            val nameLines = wrapText(displayName, textPaint, 520f)

            // Draw Prices & Quantity alongside the first line
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", width - padding - 310f, y + 16f, textPaint)

            // RATE
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", width - padding - 160f, y + 16f, textPaint)

            // AMOUNT (Strictly ₹0.00 if free reward/complimentary)
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            val amountText = if (isItemFree) "₹0.00" else "₹${String.format(Locale.US, "%.2f", item.totalPrice)}"
            canvas.drawText(amountText, width - padding - 12f, y + 16f, textPaint)

            // Draw wrapped dish name lines
            textPaint.textAlign = Paint.Align.LEFT
            nameLines.forEachIndexed { lineIdx, line ->
                if (lineIdx > 0) y += 32f
                canvas.drawText(line, padding + 12f, y + 16f, textPaint)
            }

            y += lineHeight

            // Add-ons formatting (Clean subtext without duplicating right-side amount)
            val addonsList = item.getSelectedAddonsList()
            if (addonsList.isNotEmpty()) {
                addonsList.forEach { addon ->
                    textPaint.textSize = 21f
                    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textPaint.color = Color.rgb(60, 60, 60)
                    textPaint.textAlign = Paint.Align.LEFT
                    val addonTotal = addon.totalPrice * item.quantity
                    val addonText = "  ↳ + ${addon.name} (${addon.quantity * item.quantity}x) (incl. ₹${String.format(Locale.US, "%.2f", addonTotal)})"
                    canvas.drawText(addonText, padding + 24f, y + 4f, textPaint)
                    y += 28f
                }
            }

            // Cooking notes / Offer notes formatting
            val itemNote = item.cookingNotes.ifBlank { item.notes }
            if (itemNote.isNotBlank()) {
                textPaint.textSize = 21f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                textPaint.color = Color.rgb(70, 70, 70)
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("  ↳ Note: $itemNote", padding + 24f, y + 4f, textPaint)
                y += 28f
            }

            canvas.drawLine(padding, y + 6f, width - padding, y + 6f, thinLinePaint)
            y += 18f
        }

        y += 10f

        // 6. Subtotal & Totals
        val subtotal = bill.subtotal
        textPaint.textSize = bodySize
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Subtotal (${bill.items.sumOf { it.quantity }} items):", padding + 12f, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("₹${String.format(Locale.US, "%.2f", subtotal)}", width - padding - 12f, y, textPaint)

        if (bill.discountAmount > 0) {
            y += lineHeight
            val discLabel = if (bill.appliedOfferName.isNotBlank()) "Offer (${bill.appliedOfferName}):" else "Discount:"
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText(discLabel, padding + 12f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding - 12f, y, textPaint)
        }

        if (bill.pointsRedeemed > 0) {
            y += lineHeight
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Points Redeemed (${bill.pointsRedeemed} Pts):", padding + 12f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}", width - padding - 12f, y, textPaint)
        }

        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstRate > 0) {
            val gstAmount = bill.totalAmount * (restaurant.gstRate / 100.0)
            val halfGst = gstAmount / 2.0
            val rate = restaurant.gstRate / 2.0
            y += lineHeight
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("CGST ($rate%):", padding + 12f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", width - padding - 12f, y, textPaint)

            y += lineHeight
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("SGST ($rate%):", padding + 12f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", width - padding - 12f, y, textPaint)
        }

        y += 20f
        canvas.drawLine(padding, y, width - padding, y, solidLinePaint)
        y += 44f

        // 7. Grand Total
        textPaint.color = Color.BLACK
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 42f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("GRAND TOTAL:", padding + 12f, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 12f, y, textPaint)

        y += 42f
        textPaint.textSize = 25f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Payment Status:", padding + 12f, y, textPaint)

        val statusText = when (bill.paymentMethod.uppercase()) {
            "UPI" -> "PAID VIA UPI"
            "CASH" -> "PAID VIA CASH"
            "SPLIT" -> "PAID VIA SPLIT"
            "CREDIT" -> "CREDIT / KHATA DUE"
            else -> "${bill.paymentMethod} (PAID)"
        }
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(statusText, width - padding - 12f, y, textPaint)

        // 8. Consolidated Clean Summary Card (Savings, Points, UPI)
        val totalSavings = bill.discountAmount + bill.pointsRedeemed.toDouble()
        val hasSavings = totalSavings > 0 && restaurant.showSavingsOnBill
        val hasPoints = restaurant.showPointsOnBill && (bill.rewardPointsEarned > 0 || bill.pointsRedeemed > 0)
        val hasUpi = restaurant.upiQrEnabled && restaurant.upiId.isNotBlank()

        if (hasSavings || hasPoints || hasUpi) {
            y += 26f
            var cardHeight = 20f
            if (hasSavings) cardHeight += 50f
            if (hasPoints) cardHeight += 44f
            if (hasUpi) cardHeight += 80f

            val cardRect = RectF(padding, y, width - padding, y + cardHeight)
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(cardRect, paint)
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(cardRect, paint)

            var cardY = y + 36f

            if (hasSavings) {
                val offerName = bill.appliedOfferName.ifBlank { "OFFER DISCOUNT" }
                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = 23f
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("🎉 SAVINGS ON THIS BILL: ₹${String.format(Locale.US, "%.2f", totalSavings)} ($offerName)", width / 2f, cardY, textPaint)
                cardY += 46f
                if (hasPoints || hasUpi) {
                    canvas.drawLine(padding + 24f, cardY - 14f, width - padding - 24f, cardY - 14f, thinLinePaint)
                }
            }

            if (hasPoints) {
                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = 22f
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("⭐ Reward Points: +${bill.rewardPointsEarned} Pts Earned on this Bill", width / 2f, cardY, textPaint)
                cardY += 42f
                if (hasUpi) {
                    canvas.drawLine(padding + 24f, cardY - 14f, width - padding - 24f, cardY - 14f, thinLinePaint)
                }
            }

            if (hasUpi) {
                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = 22f
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("Scan & Pay via UPI: ${restaurant.upiId}", width / 2f, cardY, textPaint)
                textPaint.textSize = 19f
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                canvas.drawText("Google Pay • PhonePe • Paytm • BHIM", width / 2f, cardY + 28f, textPaint)
            }

            y += cardHeight + 20f
        }

        // 9. Previous Due Box (Square border)
        if ((previousDue > 0.0 || bill.paymentMethod == "CREDIT") && restaurant.showPreviousDueOnBill) {
            y += 20f
            val dueRect = RectF(padding, y, width - padding, y + 90f)
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(dueRect, paint)
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(dueRect, paint)

            textPaint.textSize = 22f
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Previous Account Due:", padding + 20f, y + 36f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", previousDue)}", width - padding - 20f, y + 36f, textPaint)

            val netDue = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Total Outstanding Balance:", padding + 20f, y + 72f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", netDue)}", width - padding - 20f, y + 72f, textPaint)
            y += 100f
        }

        if (restaurant.wifiDetails.isNotBlank()) {
            y += 26f
            textPaint.textSize = 21f
            textPaint.color = Color.BLACK
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("📶 Free Wi-Fi: ${restaurant.wifiDetails}", width / 2f, y, textPaint)
        }

        if (restaurant.socialHandle.isNotBlank()) {
            y += 26f
            textPaint.textSize = 21f
            textPaint.color = Color.BLACK
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("📸 Instagram: ${restaurant.socialHandle}", width / 2f, y, textPaint)
        }

        y += 40f
        canvas.drawLine(padding, y, width - padding, y, solidLinePaint)
        y += 34f

        textPaint.color = Color.BLACK
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 24f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.footerNote.ifBlank { "Thank you for visiting BBC Food Hub! Visit Again 😊" }, width / 2f, y, textPaint)

        if (restaurant.customTermsNote.isNotBlank()) {
            y += 28f
            textPaint.textSize = 19f
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textPaint.color = Color.rgb(50, 50, 50)
            canvas.drawText(restaurant.customTermsNote, width / 2f, y, textPaint)
        }

        val actualHeight = (y + padding + 30f).toInt()
        val finalHeight = actualHeight.coerceAtMost(totalHeight)

        // Draw outer square framing border around the entire receipt
        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(12f, 12f, width - 12f, finalHeight - 12f, outerBorderPaint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, finalHeight)
    }

    // ==========================================
    // 1. MODERN SIGNATURE (Dark Header & Gold Accent)
    // ==========================================
    private fun generateModernSignatureBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        // High resolution: 1080px wide
        val width = 1080
        val padding = 44f
        val bodySize = 26f
        val smallSize = 22f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + 250f
        if (restaurant.address.isNotBlank()) estimatedHeight += lineHeight
        if (restaurant.phone.isNotBlank()) estimatedHeight += lineHeight
        estimatedHeight += (bill.items.size * (lineHeight + 10f)) + lineHeight * 9
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4f
        estimatedHeight += 160f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dark Top Header Banner (Colored container)
        val headerHeight = 200f
        paint.color = Color.rgb(24, 24, 27) // Dark Charcoal
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, paint)

        // Gold Accent Divider Line
        paint.color = Color.rgb(234, 179, 8)
        canvas.drawRect(0f, headerHeight - 6f, width.toFloat(), headerHeight, paint)

        // Header Text inside dark container (White/Gold for contrast)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 38f
        textPaint.textAlign = Paint.Align.CENTER
        var y = 68f
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), width / 2f, y, textPaint)

        y += 40f
        textPaint.textSize = 21f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.rgb(229, 231, 235)
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, width / 2f, y, textPaint)
            y += 32f
        }
        val contactStr = buildString {
            if (restaurant.phone.isNotBlank()) append("Ph: ${restaurant.phone}  ")
            if (restaurant.isGstEnabled && restaurant.gstNumber.isNotBlank()) append("GSTIN: ${restaurant.gstNumber}")
        }
        if (contactStr.isNotBlank()) {
            canvas.drawText(contactStr, width / 2f, y, textPaint)
        }

        y = headerHeight + padding + 16f

        // On white background: ALL text MUST be SOLID_BLACK
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(bill.billTimestamp))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = smallSize + 2f
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("INVOICE #${bill.billNumber}", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        val typeText = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Takeaway"
        canvas.drawText(typeText, width - padding, y, textPaint)
        y += lineHeight * 0.95f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = SOLID_BLACK
        canvas.drawText("Date: $dateStr", padding, y, textPaint)

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            y += lineHeight * 0.95f
            canvas.drawText("Customer: ${bill.customerName} (${bill.customerPhone})", padding, y, textPaint)
        }

        y += 20f
        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        // Table Header: Bold Solid Black
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 2f
        textPaint.color = SOLID_BLACK

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM DESCRIPTION", padding, y, textPaint)

        val qtyX = width - padding - 220f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", qtyX, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", width - padding, y, textPaint)

        y += 16f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight + 4f

        // Items: Bold dish names, bold quantities, bold amounts, all SOLID_BLACK
        bill.items.forEach { item ->
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.color = SOLID_BLACK
            textPaint.textAlign = Paint.Align.LEFT
            val name = if (item.dishName.length > 32) item.dishName.substring(0, 31) + "…" else item.dishName
            canvas.drawText(name, padding, y, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", qtyX, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", width - padding, y, textPaint)
            y += lineHeight + 4f
        }

        y += 10f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        // Subtotal: Bold Solid Black
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize
        textPaint.color = SOLID_BLACK
        canvas.drawText("Subtotal", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", width - padding, y, textPaint)
        y += lineHeight

        if (bill.discountAmount > 0.0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            val discLabel = if (bill.discountType == "PERCENT") "Discount (${bill.discountValue.toInt()}%)" else "Discount"
            canvas.drawText(discLabel, padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding, y, textPaint)
            y += lineHeight
        }

        if (bill.appliedRewardType == "VISIT_REWARD" || bill.appliedOfferName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(22, 163, 74)
            canvas.drawText("🎁 Visit Reward (${bill.appliedOfferName.ifBlank { "Free Item" }})", padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("APPLIED", width - padding, y, textPaint)
            y += lineHeight
        }

        if (bill.pointsRedeemed > 0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            canvas.drawText("Points Redeemed (${bill.pointsRedeemed} Pts)", padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}", width - padding, y, textPaint)
            y += lineHeight
        }

        // Net Total Payable Box (Dark container with gold text for contrast)
        y += 10f
        val totalBoxRect = RectF(padding, y, width - padding, y + lineHeight + 22f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(24, 24, 27)
        canvas.drawRoundRect(totalBoxRect, 12f, 12f, paint)

        textPaint.color = Color.rgb(234, 179, 8)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 6f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Net Payable", padding + 22f, y + lineHeight * 0.8f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 22f, y + lineHeight * 0.8f, textPaint)
        y += lineHeight + 36f

        if (bill.paymentMethod == "SPLIT" || (bill.cashAmount > 0.0 && bill.upiAmount > 0.0)) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = SOLID_BLACK
            textPaint.textSize = smallSize + 1f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Payment: Cash ₹${String.format(Locale.US, "%.2f", bill.cashAmount)}  |  UPI ₹${String.format(Locale.US, "%.2f", bill.upiAmount)}", padding, y, textPaint)
            y += lineHeight + 8f
        }

        // Previous Due & Khata Box
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        // Loyalty Points Summary Box
        y = drawLoyaltyPointsSummaryBox(context, canvas, width, padding, y, lineHeight, smallSize, bill)

        // Status Badge
        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 2. RECEIPT BOX (Khata Focus & Blue/Indigo Accent)
    // ==========================================
    private fun generateReceiptBoxBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1080
        val padding = 44f
        val bodySize = 26f
        val smallSize = 22f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + 280f
        estimatedHeight += (bill.items.size * (lineHeight + 10f)) + lineHeight * 11
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 5f
        estimatedHeight += 160f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(248, 250, 252))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = padding + 14f

        // Top Border Box Header
        val headerBoxRect = RectF(padding, y, width - padding, y + 150f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(239, 246, 255)
        canvas.drawRoundRect(headerBoxRect, 14f, 14f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(30, 58, 138)
        paint.strokeWidth = 3f
        canvas.drawRoundRect(headerBoxRect, 14f, 14f, paint)

        // Shop name inside header box: prominent bold
        textPaint.color = Color.rgb(30, 58, 138)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 36f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), width / 2f, y + 54f, textPaint)

        textPaint.textSize = 21f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = SOLID_BLACK
        val subInfo = buildString {
            if (restaurant.phone.isNotBlank()) append("Ph: ${restaurant.phone}  •  ")
            if (restaurant.address.isNotBlank()) append(restaurant.address)
        }
        canvas.drawText(subInfo.ifBlank { "Customer Receipt" }, width / 2f, y + 104f, textPaint)

        y += 180f

        // Prominent Khata Box near top if due exists
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        // Bill Details: Solid Black
        val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(bill.billTimestamp))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = smallSize + 1f
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BILL NO: #${bill.billNumber}", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("DATE: $dateStr", width - padding, y, textPaint)
        y += lineHeight * 0.95f

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = SOLID_BLACK
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("CUSTOMER: ${bill.customerName} ${bill.customerPhone}", padding, y, textPaint)
            y += lineHeight * 0.95f
        }

        y += 16f

        // Items Box Table
        val itemsBoxRect = RectF(padding, y, width - padding, y + (bill.items.size + 1) * (lineHeight + 10f) + 18f)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(itemsBoxRect, 12f, 12f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 2f
        canvas.drawRoundRect(itemsBoxRect, 12f, 12f, paint)

        var itemY = y + lineHeight

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 2f
        textPaint.color = SOLID_BLACK

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM", padding + 18f, itemY, textPaint)

        val qtyX = width - padding - 200f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", qtyX, itemY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL", width - padding - 18f, itemY, textPaint)

        itemY += 12f
        canvas.drawLine(padding + 12f, itemY, width - padding - 12f, itemY, paint)
        itemY += lineHeight

        // Items: bold dish names, bold quantities, bold amounts, all SOLID_BLACK
        bill.items.forEach { item ->
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.color = SOLID_BLACK

            textPaint.textAlign = Paint.Align.LEFT
            val name = if (item.dishName.length > 30) item.dishName.substring(0, 29) + "…" else item.dishName
            canvas.drawText(name, padding + 18f, itemY, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", qtyX, itemY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", width - padding - 18f, itemY, textPaint)
            itemY += lineHeight + 8f
        }

        y = itemsBoxRect.bottom + 20f

        // Subtotal & Total
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize
        textPaint.color = SOLID_BLACK
        canvas.drawText("Subtotal:", padding + 10f, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", width - padding - 10f, y, textPaint)
        y += lineHeight

        if (bill.discountAmount > 0.0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            val discLabel = if (bill.discountType == "PERCENT") "Discount (${bill.discountValue.toInt()}%)" else "Discount"
            canvas.drawText("$discLabel:", padding + 10f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding - 10f, y, textPaint)
            y += lineHeight
        }

        if (bill.appliedRewardType == "VISIT_REWARD" || bill.appliedOfferName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(22, 163, 74)
            canvas.drawText("🎁 Visit Reward (${bill.appliedOfferName.ifBlank { "Free Item" }}):", padding + 10f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("APPLIED", width - padding - 10f, y, textPaint)
            y += lineHeight
        }

        if (bill.pointsRedeemed > 0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            canvas.drawText("Points Redeemed (${bill.pointsRedeemed} Pts):", padding + 10f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}", width - padding - 10f, y, textPaint)
            y += lineHeight
        }

        y += 10f
        val totalBoxRect = RectF(padding, y, width - padding, y + lineHeight + 22f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawRoundRect(totalBoxRect, 12f, 12f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 6f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Grand Total", padding + 22f, y + lineHeight * 0.8f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 22f, y + lineHeight * 0.8f, textPaint)
        y += lineHeight + 36f

        if (bill.paymentMethod == "SPLIT" || (bill.cashAmount > 0.0 && bill.upiAmount > 0.0)) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = SOLID_BLACK
            textPaint.textSize = smallSize + 1f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Payment: Cash ₹${String.format(Locale.US, "%.2f", bill.cashAmount)}  |  UPI ₹${String.format(Locale.US, "%.2f", bill.upiAmount)}", padding + 10f, y, textPaint)
            y += lineHeight + 8f
        }

        // Loyalty Points Summary Box
        y = drawLoyaltyPointsSummaryBox(context, canvas, width, padding, y, lineHeight, smallSize, bill)

        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 3. GST TAX INVOICE (Official Tax Grid)
    // ==========================================
    private fun generateGstTaxInvoiceBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1080
        val padding = 44f
        val bodySize = 25f
        val smallSize = 22f
        val lineHeight = bodySize * 1.6f

        val totalAddonsCount = bill.items.sumOf { it.getSelectedAddonsList().size }
        var estimatedHeight = padding * 2 + 280f
        estimatedHeight += (bill.items.size * (lineHeight + 8f)) + (totalAddonsCount * (lineHeight + 4f)) + lineHeight * 12
        if (restaurant.isGstEnabled) estimatedHeight += lineHeight * 3f
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4f
        estimatedHeight += 160f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = padding + 16f

        // Navy Header Title Bar
        val headerRect = RectF(padding, y, width - padding, y + 60f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawRect(headerRect, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("TAX INVOICE / CASH BILL", width / 2f, y + 42f, textPaint)
        y += 88f

        // Restaurant Name: Bold Solid Black
        textPaint.color = SOLID_BLACK
        textPaint.textSize = 34f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), padding, y, textPaint)
        y += lineHeight * 0.95f

        textPaint.textSize = smallSize
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, padding, y, textPaint)
            y += lineHeight * 0.85f
        }

        val gstInfo = buildString {
            if (restaurant.isGstEnabled && restaurant.gstNumber.isNotBlank()) append("GSTIN: ${restaurant.gstNumber}  ")
            if (restaurant.phone.isNotBlank()) append("Ph: ${restaurant.phone}")
        }
        if (gstInfo.isNotBlank()) {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(gstInfo, padding, y, textPaint)
            y += lineHeight * 0.95f
        }

        y += 12f
        paint.color = Color.rgb(180, 180, 180)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize + 1f
        canvas.drawText("Invoice No: #${bill.billNumber}", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", width - padding, y, textPaint)
        y += lineHeight * 0.9f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Customer: ${bill.customerName.ifBlank { "Walk-in Guest" }} (${bill.customerPhone.ifBlank { "N/A" }})", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Time: ${timeSdf.format(Date(bill.billTimestamp))}", width - padding, y, textPaint)
        y += lineHeight + 10f

        // Table Columns
        val col1X = padding
        val col2X = padding + 460f
        val col3X = padding + 590f
        val col4X = padding + 760f
        val col5X = width - padding

        paint.color = SOLID_BLACK
        paint.strokeWidth = 2f
        canvas.drawLine(padding, y, width - padding, y, paint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize + 2f
        textPaint.color = SOLID_BLACK

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Item Description", col1X + 8f, y + lineHeight * 0.75f, textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Qty", (col2X + col3X) / 2f, y + lineHeight * 0.75f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Rate", col4X - 8f, y + lineHeight * 0.75f, textPaint)
        canvas.drawText("Amount", col5X - 8f, y + lineHeight * 0.75f, textPaint)

        y += lineHeight + 6f
        canvas.drawLine(padding, y, width - padding, y, paint)

        // Item Rows: bold text, SOLID_BLACK
        bill.items.forEach { item ->
            val selectedAddons = item.getSelectedAddonsList()
            val basePrice = (item.unitPrice - item.calculateAddonsTotal()).coerceAtLeast(0.0)
            val baseTotal = basePrice * item.quantity
            val displayName = item.getFormattedDisplayName()

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.color = SOLID_BLACK

            textPaint.textAlign = Paint.Align.LEFT
            val name = if (displayName.length > 32) displayName.substring(0, 31) + "…" else displayName
            canvas.drawText(name, col1X + 8f, y + lineHeight * 0.75f, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", (col2X + col3X) / 2f, y + lineHeight * 0.75f, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            if (selectedAddons.isNotEmpty()) {
                canvas.drawText("₹${String.format(Locale.US, "%.2f", basePrice)}", col4X - 8f, y + lineHeight * 0.75f, textPaint)
                canvas.drawText("₹${String.format(Locale.US, "%.2f", baseTotal)}", col5X - 8f, y + lineHeight * 0.75f, textPaint)
            } else {
                canvas.drawText("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", col4X - 8f, y + lineHeight * 0.75f, textPaint)
                canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", col5X - 8f, y + lineHeight * 0.75f, textPaint)
            }

            y += lineHeight + 4f

            // Add-on sub-rows
            selectedAddons.forEach { addon ->
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.textSize = smallSize + 1f
                textPaint.color = Color.rgb(180, 83, 9) // Rich amber for addons

                textPaint.textAlign = Paint.Align.LEFT
                val addonName = "   ↳ + ${addon.name}"
                val safeAddonName = if (addonName.length > 30) addonName.substring(0, 29) + "…" else addonName
                canvas.drawText(safeAddonName, col1X + 8f, y + lineHeight * 0.65f, textPaint)

                textPaint.textAlign = Paint.Align.CENTER
                val totalAddonQty = addon.quantity * item.quantity
                canvas.drawText("$totalAddonQty", (col2X + col3X) / 2f, y + lineHeight * 0.65f, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("₹${String.format(Locale.US, "%.2f", addon.unitPrice)}", col4X - 8f, y + lineHeight * 0.65f, textPaint)
                canvas.drawText("₹${String.format(Locale.US, "%.2f", addon.totalPrice * item.quantity)}", col5X - 8f, y + lineHeight * 0.65f, textPaint)

                y += lineHeight * 0.85f + 2f
            }
        }

        canvas.drawLine(padding, y, width - padding, y, paint)
        y += 14f

        // Totals Grid: bold SOLID_BLACK
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize
        canvas.drawText("Taxable Subtotal", col1X + 8f, y + lineHeight * 0.7f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", col5X - 8f, y + lineHeight * 0.7f, textPaint)
        y += lineHeight

        if (restaurant.isGstEnabled) {
            val halfGst = (bill.subtotal - bill.discountAmount).coerceAtLeast(0.0) * (restaurant.gstRate / 200.0)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("CGST (${restaurant.gstRate / 2.0}%)", col1X + 8f, y + lineHeight * 0.7f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", col5X - 8f, y + lineHeight * 0.7f, textPaint)
            y += lineHeight

            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("SGST (${restaurant.gstRate / 2.0}%)", col1X + 8f, y + lineHeight * 0.7f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", halfGst)}", col5X - 8f, y + lineHeight * 0.7f, textPaint)
            y += lineHeight
        }

        // Net Total Box
        val totalRect = RectF(padding, y, width - padding, y + lineHeight + 22f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(15, 23, 42)
        canvas.drawRoundRect(totalRect, 10f, 10f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 6f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Total Invoice Amount", padding + 20f, y + lineHeight * 0.8f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 20f, y + lineHeight * 0.8f, textPaint)
        y += lineHeight + 42f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 4. ELEGANT DINE IN (Cafe & Bistro Look)
    // ==========================================
    private fun generateElegantDineInBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1080
        val padding = 44f
        val bodySize = 26f
        val smallSize = 22f
        val titleSize = 38f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + titleSize + 220f
        if (restaurant.address.isNotBlank()) estimatedHeight += lineHeight
        if (restaurant.phone.isNotBlank()) estimatedHeight += lineHeight
        estimatedHeight += (bill.items.size * (lineHeight + 10f))
        estimatedHeight += lineHeight * 11
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4.5f
        estimatedHeight += 160f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(255, 251, 240)) // Warm Ivory

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer Accent Border
        paint.color = Color.rgb(180, 83, 9)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val borderMargin = 16f
        canvas.drawRoundRect(
            RectF(borderMargin, borderMargin, width - borderMargin, totalHeight - borderMargin),
            16f, 16f, paint
        )

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 119, 6)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        fun drawAmberDivider(yPos: Float) {
            canvas.drawLine(padding, yPos, width - padding, yPos, dividerPaint)
        }

        var y = padding + titleSize + 10f

        // Shop Name: Bold Prominent
        textPaint.color = Color.rgb(120, 53, 15)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = titleSize
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("☕ ${restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()} ☕", width / 2f, y, textPaint)
        y += lineHeight * 0.95f

        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        textPaint.textSize = smallSize + 1f
        canvas.drawText("Bistro & Cafe Experience", width / 2f, y, textPaint)
        y += lineHeight

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, width / 2f, y, textPaint)
            y += lineHeight * 0.9f
        }
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText("Contact: ${restaurant.phone}", width / 2f, y, textPaint)
            y += lineHeight * 0.9f
        }

        y += 10f
        drawAmberDivider(y)
        y += lineHeight

        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(bill.billTimestamp))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = smallSize + 1f
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val tableLabel = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Type: TAKEAWAY"
        canvas.drawText(tableLabel, padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Invoice: #${bill.billNumber}", width - padding, y, textPaint)
        y += lineHeight * 0.95f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date: $dateStr", padding, y, textPaint)

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            y += lineHeight * 0.95f
            val custStr = buildString {
                if (bill.customerName.isNotBlank()) append("Customer: ${bill.customerName}")
                if (bill.customerPhone.isNotBlank()) {
                    if (isNotEmpty()) append(" (${bill.customerPhone})") else append("Phone: ${bill.customerPhone}")
                }
            }
            canvas.drawText(custStr, padding, y, textPaint)
        }

        y += 12f
        drawAmberDivider(y)
        y += lineHeight

        // Table Header: Bold Solid Black
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 2f
        textPaint.color = SOLID_BLACK

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEMS ORDERED", padding, y, textPaint)

        val qtyX = width - padding - 220f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", qtyX, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", width - padding, y, textPaint)

        y += 12f
        drawAmberDivider(y)
        y += lineHeight + 4f

        // Items: bold dish names, bold quantities, bold amounts, all SOLID_BLACK
        bill.items.forEach { item ->
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.color = SOLID_BLACK

            textPaint.textAlign = Paint.Align.LEFT
            val dishName = if (item.dishName.length > 30) item.dishName.substring(0, 29) + "…" else item.dishName
            canvas.drawText(dishName, padding, y, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", qtyX, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", width - padding, y, textPaint)
            y += lineHeight + 6f
        }

        y += 8f
        drawAmberDivider(y)
        y += lineHeight

        // Subtotal: bold SOLID_BLACK
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize
        textPaint.color = SOLID_BLACK
        canvas.drawText("Order Subtotal", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", width - padding, y, textPaint)
        y += lineHeight

        if (bill.discountAmount > 0.0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            canvas.drawText("Discount", padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding, y, textPaint)
            y += lineHeight
        }

        // Net Payable Box
        val totalBoxRect = RectF(padding - 6f, y - 8f, width - padding + 6f, y + lineHeight + 18f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(254, 243, 199)
        canvas.drawRoundRect(totalBoxRect, 10f, 10f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(217, 119, 6)
        paint.strokeWidth = 2f
        canvas.drawRoundRect(totalBoxRect, 10f, 10f, paint)

        textPaint.color = Color.rgb(120, 53, 15)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 4f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Net Total Payable", padding + 16f, y + lineHeight * 0.75f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 16f, y + lineHeight * 0.75f, textPaint)
        y += lineHeight + 38f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + borderMargin + 20f).toInt().coerceAtLeast(100)
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 5. MINIMAL CLEAN (Sleek & Contemporary)
    // ==========================================
    private fun generateMinimalCleanBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1080
        val padding = 44f
        val bodySize = 26f
        val smallSize = 22f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + 250f
        estimatedHeight += (bill.items.size * (lineHeight + 10f)) + lineHeight * 9
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4.5f
        estimatedHeight += 160f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = padding + 30f

        // Shop Name: Bold Large Solid Black
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 38f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }, padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = smallSize + 2f
        canvas.drawText("#${bill.billNumber}", width - padding, y, textPaint)

        y += 38f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, padding, y, textPaint)
            y += 28f
        }

        y += 16f
        paint.color = Color.rgb(200, 200, 200)
        paint.strokeWidth = 2f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", padding, y, textPaint)
        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Customer: ${bill.customerName}", width - padding, y, textPaint)
        }
        y += lineHeight

        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight + 4f

        // Items: bold dish names, bold quantities, bold amounts, all SOLID_BLACK
        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.color = SOLID_BLACK
            val name = if (item.dishName.length > 30) item.dishName.substring(0, 29) + "…" else item.dishName
            canvas.drawText(name, padding, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("x${item.quantity}   ₹${String.format(Locale.US, "%.2f", item.totalPrice)}", width - padding, y, textPaint)
            y += lineHeight + 6f
        }

        y += 12f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 4f
        canvas.drawText("Total Amount", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding, y, textPaint)
        y += lineHeight + 34f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 6. RETRO FOODIE (Trendy Diner Style)
    // ==========================================
    private fun generateRetroFoodieBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1080
        val padding = 44f
        val bodySize = 26f
        val smallSize = 22f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + 280f
        estimatedHeight += (bill.items.size * (lineHeight + 10f)) + lineHeight * 11
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4.5f
        estimatedHeight += 160f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(254, 252, 232))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = padding + 16f

        // Retro Crimson Top Banner (Colored container)
        val bannerRect = RectF(padding, y, width - padding, y + 140f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(185, 28, 28)
        canvas.drawRoundRect(bannerRect, 16f, 16f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 36f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("⭐ ${restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()} ⭐", width / 2f, y + 60f, textPaint)

        textPaint.textSize = 21f
        textPaint.color = Color.rgb(254, 226, 226)
        canvas.drawText("Delicious Food & Good Times", width / 2f, y + 104f, textPaint)

        y += 170f

        val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.getDefault())
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize + 1f
        canvas.drawText("BILL #${bill.billNumber}", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(sdf.format(Date(bill.billTimestamp)), width - padding, y, textPaint)
        y += lineHeight

        paint.color = Color.rgb(185, 28, 28)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        canvas.drawLine(padding, y, width - padding, y, paint)
        paint.pathEffect = null
        y += lineHeight

        // Items: bold dish names, bold quantities, bold amounts, all SOLID_BLACK
        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = SOLID_BLACK
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            val name = if (item.dishName.length > 28) item.dishName.substring(0, 27) + "…" else item.dishName
            canvas.drawText(name, padding, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("x${item.quantity}  ₹${String.format(Locale.US, "%.2f", item.totalPrice)}", width - padding, y, textPaint)
            y += lineHeight + 6f
        }

        y += 8f
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        canvas.drawLine(padding, y, width - padding, y, paint)
        paint.pathEffect = null
        y += lineHeight

        val netBoxRect = RectF(padding, y, width - padding, y + lineHeight + 22f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(185, 28, 28)
        canvas.drawRoundRect(netBoxRect, 12f, 12f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 6f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL AMOUNT", padding + 20f, y + lineHeight * 0.8f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 20f, y + lineHeight * 0.8f, textPaint)

        y += lineHeight + 42f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 7 & 8. THERMAL BITMAP GENERATOR (58mm / 80mm)
    // ==========================================
    private fun generateThermalBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double,
        format: String
    ): Bitmap {
        // High DPI for crisp thermal receipts:
        val is58mm = format == "THERMAL_2_INCH"
        val width = if (is58mm) 640 else 860
        val padding = if (is58mm) 24f else 36f
        val titleSize = if (is58mm) 34f else 38f
        val bodySize = if (is58mm) 24f else 26f
        val smallSize = if (is58mm) 20f else 22f
        val lineHeight = bodySize * 1.55f

        var estimatedHeight = padding * 2 + titleSize + 20f
        if (restaurant.address.isNotBlank()) estimatedHeight += lineHeight
        if (restaurant.phone.isNotBlank()) estimatedHeight += lineHeight
        estimatedHeight += lineHeight * 4
        estimatedHeight += (bill.items.size * (lineHeight + 6f))
        estimatedHeight += lineHeight * 5
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4.5f
        estimatedHeight += padding * 2 + 50f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOLID_BLACK
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOLID_BLACK
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
            style = Paint.Style.STROKE
        }

        fun drawDivider(yPos: Float) {
            canvas.drawLine(padding, yPos, width - padding, yPos, dashPaint)
        }

        val contentWidth = width - (padding * 2)
        var y = padding + titleSize

        // Shop Name: Bold Large SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = titleSize
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), width / 2f, y, textPaint)
        y += lineHeight

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize
        if (restaurant.address.isNotBlank()) {
            canvas.drawText(restaurant.address, width / 2f, y, textPaint)
            y += lineHeight * 0.9f
        }
        if (restaurant.phone.isNotBlank()) {
            canvas.drawText("Ph: ${restaurant.phone}", width / 2f, y, textPaint)
            y += lineHeight * 0.9f
        }

        y += 8f
        drawDivider(y)
        y += lineHeight

        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(bill.billTimestamp))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Invoice: #${bill.billNumber}", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        val orderTypeStr = if (bill.orderType == "DINE_IN") "DINE-IN (${bill.tableName ?: "T"})" else "TAKEAWAY"
        canvas.drawText(orderTypeStr, width - padding, y, textPaint)
        y += lineHeight * 0.95f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date: $dateStr", padding, y, textPaint)
        y += lineHeight * 0.95f

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            canvas.drawText("Customer: ${bill.customerName} ${bill.customerPhone}", padding, y, textPaint)
            y += lineHeight * 0.95f
        }

        y += 8f
        drawDivider(y)
        y += lineHeight

        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = bodySize + 1f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        val qtyX = width - padding - (contentWidth * 0.28f)
        canvas.drawText("QTY", qtyX, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", width - padding, y, textPaint)

        y += 10f
        drawDivider(y)
        y += lineHeight + 4f

        // Items: bold dish names, bold quantities, bold amounts, all SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = bodySize
        bill.items.forEach { item ->
            textPaint.textAlign = Paint.Align.LEFT
            val maxChars = if (is58mm) 18 else 26
            val dishDisplay = if (item.dishName.length > maxChars) item.dishName.substring(0, maxChars - 1) + "…" else item.dishName
            canvas.drawText(dishDisplay, padding, y, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", qtyX, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format(Locale.US, "%.2f", item.totalPrice), width - padding, y, textPaint)
            y += lineHeight + 4f

            val addonsSummary = item.getAddonsSummary()
            if (addonsSummary.isNotBlank()) {
                textPaint.textSize = smallSize - 2f
                textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(" └ $addonsSummary", padding + 8f, y, textPaint)
                textPaint.textSize = bodySize
                textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                y += lineHeight
            }
        }

        y += 6f
        drawDivider(y)
        y += lineHeight

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Order Subtotal:", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", width - padding, y, textPaint)
        y += lineHeight

        if (bill.discountAmount > 0) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Discount:", padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding, y, textPaint)
            y += lineHeight
        }

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 4f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TOTAL PAYABLE:", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding, y, textPaint)
        y += lineHeight + 8f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding).toInt().coerceAtLeast(100)
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // COMMON UTILITY FUNCTIONS (Dues Box & Badge)
    // ==========================================
    private fun drawAccountDuesBox(
        canvas: Canvas,
        width: Int,
        padding: Float,
        startY: Float,
        lineHeight: Float,
        smallSize: Float,
        previousDue: Double,
        bill: BillEntity
    ): Float {
        var y = startY
        val totalDue = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
        val dueBoxRect = RectF(padding, y, width - padding, y + (lineHeight * 2.4f))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(255, 237, 213) // Amber Light
        }
        canvas.drawRoundRect(dueBoxRect, 12f, 12f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(234, 88, 12)
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(dueBoxRect, 12f, 12f, paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = smallSize + 1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(154, 52, 18) // High contrast dark rust
        }

        if (previousDue > 0.0) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Previous Account Due:", padding + 18f, y + lineHeight * 0.9f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", previousDue)}", width - padding - 18f, y + lineHeight * 0.9f, textPaint)
        }

        textPaint.textSize = smallSize + 3f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Outstanding Balance:", padding + 18f, y + lineHeight * 1.9f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", totalDue)}", width - padding - 18f, y + lineHeight * 1.9f, textPaint)

        return y + (lineHeight * 2.4f) + 24f
    }

    private fun drawLoyaltyPointsSummaryBox(
        context: Context,
        canvas: Canvas,
        width: Int,
        padding: Float,
        startY: Float,
        lineHeight: Float,
        smallSize: Float,
        bill: BillEntity
    ): Float {
        var totalBalance: Int? = null
        var rewardBalance = 0
        var giftBalance = 0
        if (bill.customerPhone.isNotBlank()) {
            try {
                kotlinx.coroutines.runBlocking {
                    val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                    val cust = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
                    if (cust != null) {
                        rewardBalance = cust.rewardPointsBalance
                        giftBalance = cust.giftPointsBalance
                        totalBalance = cust.rewardPointsBalance + cust.giftPointsBalance
                    }
                }
            } catch (_: Exception) {}
        }

        if (bill.pointsRedeemed <= 0 && bill.rewardPointsEarned <= 0 && totalBalance == null) {
            return startY
        }

        var y = startY
        var numLines = 1.0f
        if (bill.pointsRedeemed > 0) numLines += 1f
        if (bill.rewardPointsEarned > 0) numLines += 1f
        if (totalBalance != null) numLines += 1.8f

        val boxHeight = (lineHeight * numLines) + 16f
        val boxRect = RectF(padding, y, width - padding, y + boxHeight)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(240, 253, 244) // Soft green
        }
        canvas.drawRoundRect(boxRect, 10f, 10f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(22, 163, 74)
        paint.strokeWidth = 2f
        canvas.drawRoundRect(boxRect, 10f, 10f, paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = smallSize + 1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(21, 128, 61)
        }

        y += lineHeight * 0.85f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("🎁 REWARD POINTS SUMMARY", padding + 16f, y, textPaint)

        if (bill.pointsRedeemed > 0) {
            y += lineHeight
            textPaint.color = SOLID_BLACK
            canvas.drawText("Points Redeemed:", padding + 16f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.rgb(220, 38, 38)
            canvas.drawText("${bill.pointsRedeemed} Pts (-₹${bill.pointsRedeemed}.00)", width - padding - 16f, y, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        if (bill.rewardPointsEarned > 0) {
            y += lineHeight
            textPaint.color = SOLID_BLACK
            canvas.drawText("Points Earned:", padding + 16f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.rgb(22, 163, 74)
            canvas.drawText("+${bill.rewardPointsEarned} Pts", width - padding - 16f, y, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        if (totalBalance != null) {
            y += lineHeight
            textPaint.color = SOLID_BLACK
            canvas.drawText("New Points Balance:", padding + 16f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.rgb(21, 128, 61)
            canvas.drawText("$totalBalance Pts", width - padding - 16f, y, textPaint)
            textPaint.textAlign = Paint.Align.LEFT

            y += lineHeight * 0.8f
            textPaint.textSize = smallSize - 2f
            textPaint.color = Color.rgb(0, 77, 64)
            canvas.drawText("(Reward: $rewardBalance | Gift: $giftBalance)", padding + 16f, y, textPaint)
        }

        return startY + boxHeight + 20f
    }

    private fun drawStatusBadge(
        canvas: Canvas,
        width: Int,
        startY: Float,
        smallSize: Float,
        bill: BillEntity
    ): Float {
        var y = startY
        val isCredit = bill.paymentMethod == "CREDIT"
        val statusText = if (isCredit) "PAYMENT DUE (CREDIT)" else "PAID IN FULL (${bill.paymentMethod})"
        val badgeColor = if (isCredit) Color.rgb(220, 38, 38) else Color.rgb(22, 163, 74)

        val badgeWidth = 380f
        val badgeHeight = 48f
        val badgeRect = RectF((width - badgeWidth) / 2f, y, (width + badgeWidth) / 2f, y + badgeHeight)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isCredit) Color.rgb(254, 226, 226) else Color.rgb(220, 252, 231)
        }
        canvas.drawRoundRect(badgeRect, 10f, 10f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = badgeColor
        paint.strokeWidth = 2f
        canvas.drawRoundRect(badgeRect, 10f, 10f, paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = smallSize + 3f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(statusText, width / 2f, y + 32f, textPaint)

        return y + badgeHeight + 28f
    }

    // ==========================================
    // 9. FAST FOOD EXPRESS (Bold Token & Fast Turnaround)
    // ==========================================
    private fun generateFastFoodExpressBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1200
        val padding = 48f
        val bodySize = 28f
        val smallSize = 24f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + 340f
        estimatedHeight += (bill.items.size * (lineHeight + 16f))
        bill.items.forEach { if (it.notes.isNotBlank()) estimatedHeight += lineHeight * 0.9f }
        estimatedHeight += lineHeight * 10
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4.5f
        estimatedHeight += 180f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(255, 255, 255))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            hinting = Paint.HINTING_ON
        }

        var y = padding + 10f

        // Top Bright Orange Banner
        val bannerH = 170f
        val bannerRect = RectF(padding, y, width - padding, y + bannerH)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(234, 88, 12) // #EA580C
        canvas.drawRoundRect(bannerRect, 18f, 18f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 42f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), width / 2f, y + 68f, textPaint)

        textPaint.textSize = 23f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.rgb(255, 237, 213)
        val subTxt = buildString {
            if (restaurant.phone.isNotBlank()) append("Ph: ${restaurant.phone}  •  ")
            if (restaurant.address.isNotBlank()) append(restaurant.address)
        }
        canvas.drawText(subTxt.ifBlank { "Fast Food & Quick Service" }, width / 2f, y + 120f, textPaint)

        y += bannerH + 28f

        // Token & Order Type Highlights
        val tokenBoxW = (width - (padding * 2) - 24f) / 2f

        // Box 1: Token #
        val tokenRect = RectF(padding, y, padding + tokenBoxW, y + 110f)
        paint.color = Color.rgb(255, 247, 237)
        canvas.drawRoundRect(tokenRect, 14f, 14f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(234, 88, 12)
        paint.strokeWidth = 3f
        canvas.drawRoundRect(tokenRect, 14f, 14f, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.rgb(194, 65, 12)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 21f
        canvas.drawText("TOKEN / ORDER #", tokenRect.centerX(), tokenRect.top + 38f, textPaint)

        textPaint.color = Color.rgb(234, 88, 12)
        textPaint.textSize = 38f
        canvas.drawText("#${bill.billNumber}", tokenRect.centerX(), tokenRect.top + 88f, textPaint)

        // Box 2: Order Type
        val typeRect = RectF(padding + tokenBoxW + 24f, y, width - padding, y + 110f)
        val isDineIn = bill.orderType == "DINE_IN"
        paint.style = Paint.Style.FILL
        paint.color = if (isDineIn) Color.rgb(239, 246, 255) else Color.rgb(254, 242, 242)
        canvas.drawRoundRect(typeRect, 14f, 14f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = if (isDineIn) Color.rgb(59, 130, 246) else Color.rgb(239, 68, 68)
        canvas.drawRoundRect(typeRect, 14f, 14f, paint)

        textPaint.color = if (isDineIn) Color.rgb(29, 78, 216) else Color.rgb(185, 28, 28)
        textPaint.textSize = 21f
        canvas.drawText("ORDER TYPE", typeRect.centerX(), typeRect.top + 38f, textPaint)

        textPaint.textSize = 30f
        val typeLabel = if (isDineIn) "DINE-IN (${bill.tableName ?: "Counter"})" else "TAKEAWAY"
        canvas.drawText(typeLabel, typeRect.centerX(), typeRect.top + 86f, textPaint)

        y += 140f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", padding, y, textPaint)

        if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Guest: ${bill.customerName} (${bill.customerPhone})", width - padding, y, textPaint)
        }
        y += lineHeight

        paint.color = Color.rgb(203, 213, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        // Table Header
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 2f
        textPaint.color = SOLID_BLACK

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEMS ORDERED", padding, y, textPaint)

        val qtyX = width - padding - 260f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", qtyX, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", width - padding, y, textPaint)

        y += 16f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight + 4f

        // Items list with note support
        bill.items.forEach { item ->
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.color = SOLID_BLACK
            textPaint.textAlign = Paint.Align.LEFT

            val dishName = (if (item.isFree) "🎁 " else "") + item.dishName
            val display = if (dishName.length > 30) dishName.substring(0, 29) + "…" else dishName
            canvas.drawText(display, padding, y, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}x", qtyX, y, textPaint)

            val isItemFree = item.isFree || item.totalPrice == 0.0
            val itemAmountStr = if (isItemFree) "₹0.00" else "₹${String.format(Locale.US, "%.2f", item.totalPrice)}"
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(itemAmountStr, width - padding, y, textPaint)
            y += lineHeight

            if (item.notes.isNotBlank()) {
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = smallSize - 2f
                textPaint.color = Color.rgb(220, 38, 38)
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("  ↳ Note: ${item.notes}", padding + 12f, y, textPaint)
                y += lineHeight * 0.85f
            }
        }

        y += 8f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize
        textPaint.color = SOLID_BLACK
        canvas.drawText("Subtotal", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", width - padding, y, textPaint)
        y += lineHeight

        if (bill.discountAmount > 0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            val discLabel = if (bill.discountType == "PERCENT") "Discount (${bill.discountValue.toInt()}%)" else "Discount"
            canvas.drawText(discLabel, padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding, y, textPaint)
            y += lineHeight
        }

        // Net Total Box
        val netBoxRect = RectF(padding, y, width - padding, y + lineHeight + 26f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(234, 88, 12)
        canvas.drawRoundRect(netBoxRect, 14f, 14f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 8f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Net Total Payable", padding + 24f, y + lineHeight * 0.85f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 24f, y + lineHeight * 0.85f, textPaint)
        y += lineHeight + 42f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawLoyaltyPointsSummaryBox(context, canvas, width, padding, y, lineHeight, smallSize, bill)
        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 10. ROYAL PREMIUM (Deep Navy & Gold Ornate Frame)
    // ==========================================
    private fun generateRoyalPremiumBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1200
        val padding = 48f
        val bodySize = 28f
        val smallSize = 24f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + 360f
        estimatedHeight += (bill.items.size * (lineHeight + 16f))
        bill.items.forEach { if (it.notes.isNotBlank()) estimatedHeight += lineHeight * 0.9f }
        estimatedHeight += lineHeight * 10
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4.5f
        estimatedHeight += 180f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(255, 255, 255))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            hinting = Paint.HINTING_ON
        }

        // Royal Outer Double Border
        paint.color = Color.rgb(212, 175, 55) // Royal Gold #D4AF37
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRoundRect(RectF(18f, 18f, width - 18f, totalHeight - 18f), 18f, 18f, paint)

        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(RectF(26f, 26f, width - 26f, totalHeight - 26f), 14f, 14f, paint)

        var y = padding + 16f

        // Royal Navy Header Container
        val headerH = 180f
        val headerRect = RectF(padding, y, width - padding, y + headerH)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(15, 23, 42) // Deep Slate Navy #0F172A
        canvas.drawRoundRect(headerRect, 14f, 14f, paint)

        textPaint.color = Color.rgb(253, 224, 71) // Bright Gold Accent
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 42f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("👑 ${restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()} 👑", width / 2f, y + 68f, textPaint)

        textPaint.textSize = 21f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.rgb(226, 232, 240)
        canvas.drawText("FINE DINING & PREMIUM EXPERIENCE", width / 2f, y + 114f, textPaint)

        if (restaurant.address.isNotBlank() || restaurant.phone.isNotBlank()) {
            val contactStr = buildString {
                if (restaurant.address.isNotBlank()) append("${restaurant.address}  •  ")
                if (restaurant.phone.isNotBlank()) append("Ph: ${restaurant.phone}")
            }
            textPaint.textSize = 20f
            textPaint.color = Color.rgb(203, 213, 225)
            canvas.drawText(contactStr, width / 2f, y + 152f, textPaint)
        }

        y += headerH + 30f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize + 2f
        canvas.drawText("Tax Invoice #${bill.billNumber}", padding, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Date: ${sdf.format(Date(bill.billTimestamp))}", width - padding, y, textPaint)
        y += lineHeight

        textPaint.textAlign = Paint.Align.LEFT
        val orderTypeStr = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Takeaway Order"
        canvas.drawText(orderTypeStr, padding, y, textPaint)

        if (bill.customerName.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Guest: ${bill.customerName} (${bill.customerPhone})", width - padding, y, textPaint)
        }
        y += lineHeight

        paint.color = Color.rgb(212, 175, 55)
        paint.strokeWidth = 2.5f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        // Table Header
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 2f
        textPaint.color = Color.rgb(15, 23, 42)

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM DESCRIPTION", padding, y, textPaint)

        val qtyX = width - padding - 260f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", qtyX, y, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", width - padding, y, textPaint)

        y += 16f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight + 4f

        bill.items.forEach { item ->
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.color = SOLID_BLACK

            textPaint.textAlign = Paint.Align.LEFT
            val name = (if (item.isFree) "🎁 " else "") + item.dishName
            val display = if (name.length > 32) name.substring(0, 31) + "…" else name
            canvas.drawText(display, padding, y, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", qtyX, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", width - padding, y, textPaint)
            y += lineHeight

            if (item.notes.isNotBlank()) {
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = smallSize - 2f
                textPaint.color = Color.rgb(180, 83, 9)
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("  ↳ Instructions: ${item.notes}", padding + 12f, y, textPaint)
                y += lineHeight * 0.85f
            }
        }

        y += 8f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize
        textPaint.color = SOLID_BLACK
        canvas.drawText("Subtotal", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", width - padding, y, textPaint)
        y += lineHeight

        if (bill.discountAmount > 0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            val discLabel = if (bill.discountType == "PERCENT") "Discount (${bill.discountValue.toInt()}%)" else "Discount"
            canvas.drawText(discLabel, padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding, y, textPaint)
            y += lineHeight
        }

        // Net Total Box with Gold Border
        val totalBoxRect = RectF(padding, y, width - padding, y + lineHeight + 26f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(254, 243, 199) // Soft Gold
        canvas.drawRoundRect(totalBoxRect, 14f, 14f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(212, 175, 55)
        paint.strokeWidth = 3f
        canvas.drawRoundRect(totalBoxRect, 14f, 14f, paint)

        textPaint.color = Color.rgb(120, 53, 15)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 8f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Grand Total Amount", padding + 24f, y + lineHeight * 0.85f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 24f, y + lineHeight * 0.85f, textPaint)
        y += lineHeight + 44f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawLoyaltyPointsSummaryBox(context, canvas, width, padding, y, lineHeight, smallSize, bill)
        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    // ==========================================
    // 11. KITCHEN DETAILED (Itemized & Cooking Notes)
    // ==========================================
    private fun generateKitchenDetailedBitmap(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): Bitmap {
        val width = 1200
        val padding = 48f
        val bodySize = 28f
        val smallSize = 24f
        val lineHeight = bodySize * 1.6f

        var estimatedHeight = padding * 2 + 340f
        estimatedHeight += (bill.items.size * (lineHeight + 24f))
        bill.items.forEach { if (it.notes.isNotBlank()) estimatedHeight += lineHeight }
        estimatedHeight += lineHeight * 10
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") estimatedHeight += lineHeight * 4.5f
        estimatedHeight += 180f

        val totalHeight = estimatedHeight.toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            hinting = Paint.HINTING_ON
        }

        var y = padding + 12f

        // Top Slate Header Box
        val headerH = 150f
        val headerRect = RectF(padding, y, width - padding, y + headerH)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(241, 245, 249) // Slate 100
        canvas.drawRoundRect(headerRect, 14f, 14f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(71, 85, 105) // Slate 600
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(headerRect, 14f, 14f, paint)

        textPaint.color = Color.rgb(15, 23, 42)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 38f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(), padding + 24f, y + 54f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 24f
        canvas.drawText("ITEMIZED RECEIPT", width - padding - 24f, y + 54f, textPaint)

        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = smallSize
        textPaint.color = SOLID_BLACK
        val headerSub = "Bill #${bill.billNumber}  •  ${sdf.format(Date(bill.billTimestamp))}"
        canvas.drawText(headerSub, padding + 24f, y + 100f, textPaint)

        val orderTypeStr = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Type: TAKEAWAY"
        val customerStr = if (bill.customerName.isNotBlank()) "  •  Cust: ${bill.customerName}" else ""
        canvas.drawText(orderTypeStr + customerStr, padding + 24f, y + 134f, textPaint)

        y += headerH + 28f

        // Table Header
        paint.color = Color.rgb(51, 65, 85)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(padding, y, width - padding, y + 50f), 8f, 8f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = smallSize + 2f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Item Description & Instructions", padding + 18f, y + 34f, textPaint)

        val qtyX = width - padding - 260f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Qty", qtyX, y + 34f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total", width - padding - 18f, y + 34f, textPaint)

        y += 68f

        bill.items.forEachIndexed { index, item ->
            textPaint.color = SOLID_BLACK
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = bodySize
            textPaint.textAlign = Paint.Align.LEFT
            val itemName = "${index + 1}. " + (if (item.isFree) "🎁 " else "") + item.dishName
            val display = if (itemName.length > 32) itemName.substring(0, 31) + "…" else itemName
            canvas.drawText(display, padding + 8f, y, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${item.quantity}", qtyX, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", width - padding - 8f, y, textPaint)
            y += lineHeight

            if (item.notes.isNotBlank()) {
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = smallSize - 2f
                textPaint.color = Color.rgb(220, 38, 38)
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("   📝 Instruction: ${item.notes}", padding + 16f, y, textPaint)
                y += lineHeight * 0.9f
            }
        }

        y += 10f
        paint.color = Color.rgb(203, 213, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawLine(padding, y, width - padding, y, paint)
        y += lineHeight

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = SOLID_BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize
        canvas.drawText("Subtotal", padding, y, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.subtotal)}", width - padding, y, textPaint)
        y += lineHeight

        if (bill.discountAmount > 0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.rgb(220, 38, 38)
            val discLabel = if (bill.discountType == "PERCENT") "Discount (${bill.discountValue.toInt()}%)" else "Discount"
            canvas.drawText(discLabel, padding, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", width - padding, y, textPaint)
            y += lineHeight
        }

        val totalBoxRect = RectF(padding, y, width - padding, y + lineHeight + 24f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(15, 23, 42)
        canvas.drawRoundRect(totalBoxRect, 12f, 12f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = bodySize + 6f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Total Invoice Amount", padding + 22f, y + lineHeight * 0.85f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", width - padding - 22f, y + lineHeight * 0.85f, textPaint)
        y += lineHeight + 42f

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            y = drawAccountDuesBox(canvas, width, padding, y, lineHeight, smallSize, previousDue, bill)
        }

        y = drawLoyaltyPointsSummaryBox(context, canvas, width, padding, y, lineHeight, smallSize, bill)
        y = drawStatusBadge(canvas, width, y, smallSize, bill)

        val actualHeight = (y + padding + 30f).toInt()
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(totalHeight))
    }

    /**
     * Saves receipt bitmap to a PNG image file in cache directory with 100% quality.
     */
    fun generateReceiptImageFile(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ): File {
        val bitmap = generateReceiptBitmap(context, bill, restaurant, previousDue)
        val file = File(context.cacheDir, "receipt_${bill.billNumber}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
