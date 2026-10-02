package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.PointsEngineRules
import com.example.data.local.entity.RestaurantEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

object BillShareUtil {

    private fun cleanPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length == 10) {
            "91$digits" // Default India country code if 10 digits
        } else {
            digits
        }
    }

    /**
     * Builds a clean text summary of the bill for instant messaging, SMS, Email, or Clipboard.
     * Incorporates Receipt Box Design for previous pending balance and UPI payment link.
     */
    fun buildBillSummaryText(
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0,
        context: Context? = null
    ): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateFormatted = sdf.format(Date(bill.billTimestamp))

        val sb = StringBuilder()
        sb.appendLine("🧾 *BILL RECEIPT - ${restaurant.name.uppercase()}*")
        if (restaurant.address.isNotBlank()) sb.appendLine(restaurant.address)
        if (restaurant.phone.isNotBlank()) sb.appendLine("Ph: ${restaurant.phone}")

        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstNumber.isNotBlank()) {
            sb.appendLine("GSTIN: ${restaurant.gstNumber}")
        }
        if (restaurant.isFssaiEnabled && restaurant.showFssaiOnBill && restaurant.fssaiNumber.isNotBlank()) {
            sb.appendLine("FSSAI Lic. No: ${restaurant.fssaiNumber}")
        }

        sb.appendLine("----------------------------------------")
        sb.appendLine("Invoice No: *#${bill.billNumber}*")
        sb.appendLine("Date: $dateFormatted")
        sb.appendLine("Type: ${if (bill.orderType == "DINE_IN") "Dine In (${bill.tableName ?: "Table"})" else "Takeaway"}")
        if (bill.customerName.isNotBlank()) {
            sb.appendLine("Customer: *${bill.customerName}* (${bill.customerPhone.ifBlank { "N/A" }})")
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("*ORDERED ITEMS:*")
        bill.items.forEach { item ->
            val freeTag = if (item.isFree) {
                if (item.unitPrice > 0) " (🎁 FREE - Worth ₹${String.format(Locale.US, "%.0f", item.unitPrice)})" else " (🎁 FREE)"
            } else ""
            val noteTag = if (item.cookingNotes.isNotBlank()) " [Note: ${item.cookingNotes}]" else if (item.notes.isNotBlank() && !item.notes.startsWith("[DISHES:")) " [Note: ${item.notes.substringBefore("[DISHES:").trim()}]" else ""
            val selectedAddons = item.getSelectedAddonsList()
            val baseItemPrice = (item.unitPrice - item.calculateAddonsTotal()).coerceAtLeast(0.0)
            val baseItemTotal = baseItemPrice * item.quantity

            val isItemFree = item.isFree || item.totalPrice == 0.0
            if (selectedAddons.isNotEmpty()) {
                val displayBase = if (isItemFree) 0.0 else baseItemTotal
                sb.appendLine("• *${item.getFormattedDisplayName()}*$freeTag  x${item.quantity}  = ₹${String.format(Locale.US, "%.2f", displayBase)}$noteTag")
                selectedAddons.forEach { addon ->
                    val totalAddonQty = addon.quantity * item.quantity
                    val addonTotal = addon.totalPrice * item.quantity
                    sb.appendLine("   ↳ _+ ${addon.name}_ (x$totalAddonQty)  = ₹${String.format(Locale.US, "%.2f", addonTotal)}")
                }
            } else {
                val displayTotal = if (isItemFree) 0.0 else item.totalPrice
                sb.appendLine("• *${item.getFormattedDisplayName()}*$freeTag  x${item.quantity}  = ₹${String.format(Locale.US, "%.2f", displayTotal)}$noteTag")
            }
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("Subtotal: ₹${String.format(Locale.US, "%.2f", bill.subtotal)}")
        if (bill.discountAmount > 0) {
            val discLabel = if (bill.discountType == "PERCENT") "Discount (${bill.discountValue.toInt()}%)" else "Discount"
            sb.appendLine("$discLabel: -₹${String.format(Locale.US, "%.2f", bill.discountAmount)}")
        }
        if (bill.appliedRewardType == "VISIT_REWARD" || bill.appliedOfferName.isNotBlank()) {
            sb.appendLine("🎉 Loyalty Benefit: *${bill.appliedOfferName.ifBlank { "Reward Pass Offer" }}* (Applied)")
        }
        if (bill.rewardPointsRedeemed > 0) {
            sb.appendLine("Reward Points Redeemed: ${bill.rewardPointsRedeemed} Pts (-₹${String.format(Locale.US, "%.2f", bill.rewardPointsRedeemed.toDouble())})")
        }
        if (bill.giftPointsRedeemed > 0) {
            sb.appendLine("Gift Points Redeemed: ${bill.giftPointsRedeemed} Pts (-₹${String.format(Locale.US, "%.2f", bill.giftPointsRedeemed.toDouble())})")
        } else if (bill.pointsRedeemed > 0 && bill.rewardPointsRedeemed == 0) {
            sb.appendLine("Points Discount (${bill.pointsRedeemed} Pts): -₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}")
        }

        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstNumber.isNotBlank()) {
            val halfGst = (bill.subtotal - bill.discountAmount).coerceAtLeast(0.0) * (restaurant.gstRate / 200.0)
            sb.appendLine("CGST (${restaurant.gstRate / 2.0}%): ₹${String.format(Locale.US, "%.2f", halfGst)}")
            sb.appendLine("SGST (${restaurant.gstRate / 2.0}%): ₹${String.format(Locale.US, "%.2f", halfGst)}")
        }

        sb.appendLine("*Final Total: ₹${String.format(Locale.US, "%.2f", bill.totalAmount)}*")

        val freeItemSavings = bill.items.filter { it.isFree }.sumOf { it.unitPrice * it.quantity }
        val pointsSavings = (if (bill.pointsRedeemed > 0) bill.pointsRedeemed.toDouble() else (bill.rewardPointsRedeemed + bill.giftPointsRedeemed).toDouble())
        val totalSavings = bill.discountAmount + freeItemSavings + pointsSavings

        if (totalSavings > 0) {
            sb.appendLine("🌟 *TOTAL SAVINGS TODAY: ₹${String.format(Locale.US, "%.2f", totalSavings)}*")
            val parts = mutableListOf<String>()
            if (bill.discountAmount > 0) parts.add("₹${String.format(Locale.US, "%.0f", bill.discountAmount)} Discount")
            if (freeItemSavings > 0) parts.add("₹${String.format(Locale.US, "%.0f", freeItemSavings)} Free Reward Value")
            if (pointsSavings > 0) parts.add("₹${String.format(Locale.US, "%.0f", pointsSavings)} Points")
            sb.appendLine("   ↳ _${parts.joinToString(" + ")}_")
        }

        if (bill.paymentMethod == "SPLIT" || (bill.cashAmount > 0.0 && bill.upiAmount > 0.0)) {
            sb.appendLine("Payment: SPLIT (Cash: ₹${String.format(Locale.US, "%.2f", bill.cashAmount)}, Online/UPI: ₹${String.format(Locale.US, "%.2f", bill.upiAmount)})")
        } else {
            sb.appendLine("Payment: ${bill.paymentMethod}")
        }

        // RECEIPT BOX DESIGN FOR PENDING DUES
        val netPayable = if (bill.paymentMethod == "CREDIT") {
            bill.totalAmount + previousDue
        } else {
            previousDue
        }

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            sb.appendLine("")
            sb.appendLine("💳 *ACCOUNT LEDGER:*")
            sb.appendLine("• Today's Bill Total:      ₹${String.format(Locale.US, "%.2f", bill.totalAmount)}")
            if (previousDue > 0.0) {
                sb.appendLine("• Previous Account Due:    ₹${String.format(Locale.US, "%.2f", previousDue)}")
                val totalRemaining = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
                sb.appendLine("• *Current Outstanding:   ₹${String.format(Locale.US, "%.2f", totalRemaining)}*")
            } else {
                sb.appendLine("• *Current Outstanding:   ₹${String.format(Locale.US, "%.2f", bill.totalAmount)}*")
            }
        }

        // LOYALTY POINTS SUMMARY IN SHAREABLE TEXT
        var totalBalance: Int? = null
        var rewardBalance: Int = 0
        var giftBalance: Int = 0
        if (context != null && bill.customerPhone.isNotBlank()) {
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

        if (bill.pointsRedeemed > 0 || bill.rewardPointsEarned > 0 || totalBalance != null) {
            sb.appendLine("")
            sb.appendLine("🎁 *REWARD POINTS SUMMARY:*")
            if (bill.pointsRedeemed > 0) {
                sb.appendLine("• Points Redeemed:         ${bill.pointsRedeemed} Pts (-₹${bill.pointsRedeemed}.00)")
                if (bill.rewardPointsRedeemed > 0 || bill.giftPointsRedeemed > 0) {
                    sb.appendLine("  (Reward: ${bill.rewardPointsRedeemed} | Gift: ${bill.giftPointsRedeemed})")
                }
            }
            if (bill.rewardPointsEarned > 0) {
                sb.appendLine("• Points Earned:           +${bill.rewardPointsEarned} Pts")
            }
            if (giftBalance > 0) {
                sb.appendLine("• 🎁 Welcome Gift Points:  +$giftBalance Pts")
            }
            if (totalBalance != null) {
                sb.appendLine("• *New Points Balance:     $totalBalance Pts*")
                sb.appendLine("  (Reward Balance: $rewardBalance | Gift Balance: $giftBalance)")
            }
        }

        sb.appendLine("Payment Status: *${if (bill.paymentMethod == "CREDIT") "PAYMENT DUE" else "PAID IN FULL (${bill.paymentMethod})"}*")
        sb.appendLine("----------------------------------------")
        
        val defaultNote = if (bill.orderType == "TAKEAWAY") {
            "Thank you for your order! Enjoy your delicious meal & see you soon. 🛍️"
        } else {
            "Thank you for dining with us! We look forward to welcoming you again. 🍽️"
        }
        val finalNote = if (restaurant.footerNote.isNotBlank() && restaurant.footerNote != "Thank you for visiting! Please visit again.") {
            restaurant.footerNote
        } else {
            defaultNote
        }
        sb.appendLine(finalNote)
        return sb.toString()
    }

    private fun getWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.whatsapp", 0)
            "com.whatsapp"
        } catch (_: Exception) {
            try {
                pm.getPackageInfo("com.whatsapp.w4b", 0)
                "com.whatsapp.w4b"
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * OPTION 1: Send bill via WhatsApp text summary (Supports WhatsApp & WhatsApp Business with url/chooser fallback).
     */
    fun sendViaWhatsApp(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        previousDue: Double = 0.0
    ) {
        val phone = cleanPhoneNumber(bill.customerPhone)
        val text = buildBillSummaryText(bill, restaurant, previousDue, context)

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bill Summary", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "📋 Bill summary copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}

        val waPkg = getWhatsAppPackage(context)

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (phone.isNotBlank()) {
                    putExtra("jid", "$phone@s.whatsapp.net")
                }
                if (waPkg != null) {
                    setPackage(waPkg)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (waPkg != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Share Bill via").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        } catch (e: Exception) {
            try {
                // Direct URL fallback
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = if (phone.isNotBlank()) {
                    "https://api.whatsapp.com/send?phone=$phone&text=$encodedText"
                } else {
                    "https://api.whatsapp.com/send?text=$encodedText"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (waPkg != null) setPackage(waPkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open WhatsApp. Please check if WhatsApp is installed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Overload to support sending with optional PDF file and previous due.
     */
    fun sendViaWhatsApp(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        pdfFile: File?,
        previousDue: Double = 0.0
    ) {
        if (pdfFile != null && pdfFile.exists()) {
            sendViaWhatsAppPdf(context, bill, restaurant, pdfFile, previousDue)
        } else {
            sendViaWhatsApp(context, bill, restaurant, previousDue)
        }
    }

    /**
     * OPTION 2: Send bill PDF invoice directly via WhatsApp document share.
     */
    fun sendViaWhatsAppPdf(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        pdfFile: File,
        previousDue: Double = 0.0
    ) {
        val waPkg = getWhatsAppPackage(context)
        try {
            val fullCaption = buildBillSummaryText(bill, restaurant, previousDue, context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val phone = cleanPhoneNumber(bill.customerPhone)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, fullCaption)
                if (phone.isNotBlank()) {
                    putExtra("jid", "$phone@s.whatsapp.net")
                }
                if (waPkg != null) {
                    setPackage(waPkg)
                }
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (waPkg != null) {
                try {
                    context.grantUriPermission(waPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Share PDF Invoice").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        } catch (e: Exception) {
            // Fallback to sending text
            sendViaWhatsApp(context, bill, restaurant, previousDue)
        }
    }

    /**
     * OPTION 2b: Send bill via WhatsApp with high-res Receipt Image attached.
     */
    fun sendViaWhatsAppImage(
        context: Context,
        bill: BillEntity,
        restaurant: RestaurantEntity,
        imageFile: File,
        previousDue: Double = 0.0
    ) {
        val phone = cleanPhoneNumber(bill.customerPhone)
        val imageCaption = """
🧾 *BILL RECEIPT - ${restaurant.name.uppercase()}*
Invoice: *#${bill.billNumber}* | Total: *₹${String.format(Locale.US, "%.2f", bill.totalAmount)}*
${if (!bill.tableName.isNullOrBlank()) "Table: ${bill.tableName} (Dine-In)" else if (bill.orderType.equals("TAKEAWAY", ignoreCase = true)) "Type: Takeaway / Parcel" else "Type: Dine-In"}
${restaurant.footerNote.ifBlank { "Thank you for dining with us! Visit again. 🍽️" }}
        """.trimIndent()
        val waPkg = getWhatsAppPackage(context)

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, imageCaption)
                if (phone.isNotBlank()) {
                    putExtra("jid", "$phone@s.whatsapp.net")
                }
                if (waPkg != null) {
                    setPackage(waPkg)
                }
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (waPkg != null) {
                try {
                    context.grantUriPermission(waPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Share Receipt Image").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        } catch (e: Exception) {
            // Fallback to sending text
            sendViaWhatsApp(context, bill, restaurant, previousDue)
        }
    }

    /**
     * OPTION 3: Send bill summary via direct SMS.
     */
    fun sendViaSms(context: Context, bill: BillEntity, restaurant: RestaurantEntity) {
        val phone = bill.customerPhone.trim()
        val text = buildBillSummaryText(bill, restaurant, context = context)

        try {
            val uri = if (phone.isNotBlank()) Uri.parse("smsto:$phone") else Uri.parse("smsto:")
            val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(smsIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch SMS app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * OPTION 4: Send bill via Email with A4 PDF attached.
     */
    fun sendViaEmail(context: Context, bill: BillEntity, restaurant: RestaurantEntity, pdfFile: File?) {
        try {
            val text = buildBillSummaryText(bill, restaurant, context = context)
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (pdfFile != null) "application/pdf" else "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Tax Invoice ${bill.billNumber} - ${restaurant.name}")
                putExtra(Intent.EXTRA_TEXT, "Dear Customer,\n\nPlease find attached the invoice for your visit to ${restaurant.name}.\n\n$text")
                if (pdfFile != null) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )
                    putExtra(Intent.EXTRA_STREAM, uri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                } else {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(Intent.createChooser(emailIntent, "Send Invoice via Email"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch Email app", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * OPTION 5: View & Print A4 PDF via Android Print Framework.
     */
    fun viewA4Pdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Open Invoice PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "No PDF viewer app found", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Prints the bill PDF directly using Android Print Framework / Thermal Driver.
     */
    fun printThermalReceiptDirect(context: Context, bill: BillEntity, restaurant: RestaurantEntity) {
        try {
            val pdfFile = PdfInvoiceGenerator.generateA4Pdf(context, bill, restaurant)
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val printAdapter = object : android.print.PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: android.os.Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val info = android.print.PrintDocumentInfo.Builder("Bill_${bill.billNumber}.pdf")
                            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build()
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out android.print.PageRange>?,
                        destination: android.os.ParcelFileDescriptor?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        try {
                            val input = FileInputStream(pdfFile)
                            val output = FileOutputStream(destination?.fileDescriptor)
                            val buf = ByteArray(1024)
                            var bytesRead: Int
                            while (input.read(buf).also { bytesRead = it } > 0) {
                                output.write(buf, 0, bytesRead)
                            }
                            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                            input.close()
                            output.close()
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                        }
                    }
                }
                printManager.print("Bill_${bill.billNumber}", printAdapter, PrintAttributes.Builder().build())
            } else {
                Toast.makeText(context, "Print service unavailable on this device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Print error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * OPTION 6: Share A4 PDF or summary to ANY installed app (Telegram, Bluetooth, Drive, Quick Share, etc.).
     */
    fun shareA4PdfFile(context: Context, pdfFile: File, bill: BillEntity) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Invoice ${bill.billNumber}")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Share Invoice via..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * OPTION 8: Send receipt image directly via WhatsApp image share.
     */
    fun sendViaWhatsAppImage(context: Context, bill: BillEntity, restaurant: RestaurantEntity, imageFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Here is your invoice ${bill.billNumber} from ${restaurant.name}. Thank you for visiting!")
                setPackage("com.whatsapp")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to sending standard WhatsApp text
            sendViaWhatsApp(context, bill, restaurant)
        }
    }

    /**
     * OPTION 9: View receipt image.
     */
    fun viewReceiptImage(context: Context, imageFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "View Bill Receipt"))
        } catch (e: Exception) {
            Toast.makeText(context, "No image viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * OPTION 10: Share receipt image to ANY app.
     */
    fun shareBillImageFile(context: Context, imageFile: File, bill: BillEntity) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Receipt ${bill.billNumber}")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Share Receipt Image via..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * OPTION 7: Copy complete bill text to clipboard.
     */
    fun copyBillToClipboard(context: Context, bill: BillEntity, restaurant: RestaurantEntity) {
        try {
            val text = buildBillSummaryText(bill, restaurant, context = context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Invoice ${bill.billNumber}", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Bill summary copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Builds payment receipt text for sharing with customer after collecting payment.
     */
    fun buildPaymentReceiptText(
        payment: com.example.data.local.entity.CustomerPaymentEntity,
        previousDue: Double,
        remainingDue: Double,
        restaurant: RestaurantEntity?
    ): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateFormatted = sdf.format(Date(payment.timestamp))
        val restName = restaurant?.name?.ifBlank { "BBC FOOD HUB" } ?: "BBC FOOD HUB"

        val sb = StringBuilder()
        sb.appendLine("🧾 *PAYMENT RECEIPT - ${restName.uppercase()}*")
        if (!restaurant?.address.isNullOrBlank()) sb.appendLine(restaurant?.address)
        if (!restaurant?.phone.isNullOrBlank()) sb.appendLine("Ph: ${restaurant?.phone}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("Receipt No: *#${payment.id}*")
        sb.appendLine("Date & Time: $dateFormatted")
        sb.appendLine("Customer: *${payment.customerName}*")
        if (payment.customerPhone.isNotBlank()) sb.appendLine("Phone: ${payment.customerPhone}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("✅ *PAYMENT RECEIVED: ₹${String.format(Locale.US, "%.2f", payment.amount)}*")
        sb.appendLine("Payment Mode: *${payment.paymentMode}*")
        if (payment.notes.isNotBlank()) sb.appendLine("Notes: ${payment.notes}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("╔═════════ 💳 ACCOUNT STATUS ═════════╗")
        sb.appendLine("║  Total Due Before:    ₹${String.format(Locale.US, "%10.2f", previousDue)}  ║")
        sb.appendLine("║  Amount Paid:         ₹${String.format(Locale.US, "%10.2f", payment.amount)}  ║")
        sb.appendLine("║  ────────────────────────────────── ║")
        sb.appendLine("║  🔴 *REMAINING DUE:   ₹${String.format(Locale.US, "%10.2f", remainingDue)}* ║")
        sb.appendLine("╚═════════════════════════════════════╝")
        sb.appendLine("----------------------------------------")
        sb.appendLine("Thank you for your payment! 🙏")
        sb.appendLine("Please visit again • ${restName}")
        return sb.toString()
    }

    /**
     * Send payment receipt via WhatsApp.
     */
    fun sendPaymentReceiptViaWhatsApp(
        context: Context,
        payment: com.example.data.local.entity.CustomerPaymentEntity,
        previousDue: Double,
        remainingDue: Double,
        restaurant: RestaurantEntity?
    ) {
        val text = buildPaymentReceiptText(payment, previousDue, remainingDue, restaurant)
        val phoneDigits = cleanPhoneNumber(payment.customerPhone)
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = if (phoneDigits.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedText"
            } else {
                "https://api.whatsapp.com/send?text=$encodedText"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(shareIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Builds pending credit bill statement text with customizable Date Range and Summary / Detailed mode.
     */
    fun buildPendingCreditBillStatementText(
        customerName: String,
        customerPhone: String,
        outstandingBalance: Double,
        unsettledBills: List<BillEntity>,
        recentPayments: List<com.example.data.local.entity.CustomerPaymentEntity>,
        restaurant: RestaurantEntity?,
        isSummaryOnly: Boolean = false,
        dateRangeLabel: String = "All Time"
    ): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateFormatted = sdf.format(Date())
        val restName = restaurant?.name?.ifBlank { "BBC FOOD HUB" } ?: "BBC FOOD HUB"

        val sb = StringBuilder()
        sb.appendLine("📋 *CUSTOMER ACCOUNT STATEMENT*")
        sb.appendLine("*${restName.uppercase()}*")
        if (!restaurant?.phone.isNullOrBlank()) sb.appendLine("Contact: ${restaurant?.phone}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("Customer: *${customerName}*")
        if (customerPhone.isNotBlank()) sb.appendLine("Phone: ${customerPhone}")
        sb.appendLine("Statement Period: *${dateRangeLabel}* (As of $dateFormatted)")
        sb.appendLine("----------------------------------------")

        val totalBilled = unsettledBills.sumOf { it.totalAmount }
        val totalPaid = recentPayments.sumOf { it.amount }

        sb.appendLine("💳 *ACCOUNT SUMMARY*")
        sb.appendLine("• Total Orders:            *₹${String.format(Locale.US, "%,.2f", totalBilled)}*")
        sb.appendLine("• Total Payments Received: *₹${String.format(Locale.US, "%,.2f", totalPaid)}*")
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("📌 *CURRENT OUTSTANDING BALANCE: ₹${String.format(Locale.US, "%,.2f", outstandingBalance)}*")
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("----------------------------------------")

        if (unsettledBills.isNotEmpty()) {
            if (isSummaryOnly) {
                sb.appendLine("*DUE ORDERS SUMMARY (${unsettledBills.size} bills):*")
                unsettledBills.take(5).forEach { b ->
                    val bDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(b.billTimestamp))
                    sb.appendLine("• Bill #${b.billNumber} ($bDate) - *₹${String.format(Locale.US, "%.2f", b.totalAmount)}*")
                }
                if (unsettledBills.size > 5) {
                    sb.appendLine("  ...and ${unsettledBills.size - 5} more pending bills")
                }
                sb.appendLine("----------------------------------------")
            } else {
                sb.appendLine("*DETAILED ORDER BREAKDOWN:*")
                unsettledBills.forEach { b ->
                    val bDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(b.billTimestamp))
                    sb.appendLine("• *Bill #${b.billNumber}* ($bDate) - ₹${String.format(Locale.US, "%.2f", b.totalAmount)}")
                    b.items.forEach { item ->
                        sb.appendLine("   - ${item.dishName} x${item.quantity} = ₹${String.format(Locale.US, "%.2f", item.totalPrice)}")
                    }
                    if (b.discountAmount > 0) {
                        sb.appendLine("   Discount: -₹${String.format(Locale.US, "%.2f", b.discountAmount)}")
                    }
                    sb.appendLine("----------------------------------------")
                }
            }
        }

        if (recentPayments.isNotEmpty()) {
            sb.appendLine("*RECENT PAYMENTS RECEIVED:*")
            recentPayments.take(5).forEach { p ->
                val pDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(p.timestamp))
                sb.appendLine("✅ ₹${String.format(Locale.US, "%.2f", p.amount)} on $pDate via ${p.paymentMode}")
            }
            sb.appendLine("----------------------------------------")
        }

        sb.appendLine("Kindly review the statement at your convenience. For any queries or adjustments, feel free to reach out to us.")
        sb.appendLine("")
        sb.appendLine("Thank you for dining with us! Looking forward to serving you again soon. 🙏")
        sb.appendLine("*Warm regards,*")
        sb.appendLine("*${restName}*")
        return sb.toString()
    }

    /**
     * Share pending credit bill statement via WhatsApp.
     */
    fun sharePendingBillViaWhatsApp(
        context: Context,
        customerName: String,
        customerPhone: String,
        outstandingBalance: Double,
        unsettledBills: List<BillEntity>,
        recentPayments: List<com.example.data.local.entity.CustomerPaymentEntity>,
        restaurant: RestaurantEntity?,
        isSummaryOnly: Boolean = false,
        dateRangeLabel: String = "All Time"
    ) {
        val text = buildPendingCreditBillStatementText(
            customerName = customerName,
            customerPhone = customerPhone,
            outstandingBalance = outstandingBalance,
            unsettledBills = unsettledBills,
            recentPayments = recentPayments,
            restaurant = restaurant,
            isSummaryOnly = isSummaryOnly,
            dateRangeLabel = dateRangeLabel
        )

        val phoneDigits = cleanPhoneNumber(customerPhone)
        if (phoneDigits.isNotBlank()) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedText"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to generic share intent
            }
        }

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
        } catch (ex: Exception) {
            val genericIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(genericIntent, "Share Statement via"))
        }
    }

    /**
     * Send Loyalty Reward Unlock notification via WhatsApp.
     */
    fun sendLoyaltyRewardWhatsApp(
        context: Context,
        phone: String,
        customerName: String,
        offerName: String,
        visitNumber: Int,
        restaurant: RestaurantEntity?
    ) {
        val restName = restaurant?.name ?: "BBC Food Hub"
        val cleanName = customerName.trim().ifBlank { "Valued Customer" }
        val text = buildString {
            appendLine("🎉 *CONGRATULATIONS $cleanName!*")
            appendLine("You have unlocked a milestone reward at *$restName*! 🍕🎁")
            appendLine("--------------------------------")
            appendLine("🏆 *Visit #$visitNumber Milestone*")
            appendLine("🎁 *Reward:* $offerName")
            appendLine("--------------------------------")
            appendLine("Visit us today to claim your free reward!")
            if (!restaurant?.phone.isNullOrBlank()) appendLine("📞 Contact: ${restaurant?.phone}")
            if (!restaurant?.address.isNullOrBlank()) appendLine("📍 Address: ${restaurant?.address}")
            appendLine("\nThank you for choosing $restName! ❤️")
        }

        val phoneDigits = cleanPhoneNumber(phone)
        if (phoneDigits.isNotBlank()) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedText"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback
            }
        }

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share Reward"
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(chooser)
        }
    }

    /**
     * Send Gifted Loyalty Points notification via WhatsApp.
     */
    fun sendGiftPointsWhatsApp(
        context: Context,
        phone: String,
        customerName: String,
        points: Int,
        validityDays: Int,
        notes: String,
        restaurant: RestaurantEntity?
    ) {
        val restName = restaurant?.name ?: "BBC Food Hub"
        val cleanName = customerName.trim().ifBlank { "Valued Customer" }
        val text = buildString {
            appendLine("🎁 *SPECIAL GIFT FROM $restName!*")
            appendLine("Hello $cleanName, we appreciate your loyalty! ❤️")
            appendLine("--------------------------------")
            appendLine("💰 *Points Awarded:* ₹$points Bonus Points")
            appendLine("⏳ *Validity:* $validityDays Days")
            if (notes.isNotBlank()) appendLine("📝 *Note:* $notes")
            appendLine("--------------------------------")
            appendLine("Use these points for instant discount on your next bill at *$restName*!")
            if (!restaurant?.phone.isNullOrBlank()) appendLine("📞 Contact: ${restaurant?.phone}")
            appendLine("\nVisit us soon & enjoy your favorite food! 🍕🍔☕")
        }

        val phoneDigits = cleanPhoneNumber(phone)
        if (phoneDigits.isNotBlank()) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedText"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback
            }
        }

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share Gift Points"
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(chooser)
        }
    }

    /**
     * Send Customer Loyalty & Points balance statement via WhatsApp.
     */
    /**
     * Send Customer Points Status (Reward Points & Gift Points + Points Expiry) via WhatsApp.
     */
    fun sendCustomerPointsStatusWhatsApp(
        context: Context,
        customer: CustomerEntity,
        restaurant: RestaurantEntity? = null,
        pointsExpiryMillis: Long? = null,
        rules: PointsEngineRules? = null
    ) {
        val restName = restaurant?.name?.trim()?.ifBlank { "BBC Food Hub" } ?: "BBC Food Hub"
        val cleanName = customer.name.trim().ifBlank { "Valued Customer" }
        val totalPoints = customer.rewardPointsBalance + customer.giftPointsBalance
        val pointVal = rules?.pointValueRupees ?: 1.0
        val totalValue = totalPoints * pointVal
        val text = buildString {
            appendLine("🏬 *${restName.uppercase()}*")
            appendLine("🎁 *REWARD & GIFT POINTS UPDATE*")
            appendLine()
            appendLine("Hello $cleanName! ❤️")
            appendLine()
            appendLine("Here is your updated Reward & Gift Points balance:")
            appendLine()
            appendLine("💰 *Total Points Balance:* $totalPoints Points (₹${String.format(Locale.US, "%.2f", totalValue)} Value)")
            appendLine("   • Reward Points: ${customer.rewardPointsBalance}")
            appendLine("   • Gift Points: ${customer.giftPointsBalance}")
            if (pointsExpiryMillis != null && pointsExpiryMillis > 0) {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                appendLine("⏳ *Points Expiry Date:* ${sdf.format(Date(pointsExpiryMillis))}")
            } else {
                val expiryDays = rules?.rewardPointsExpiryDays ?: 60
                val defaultExpiry = System.currentTimeMillis() + (expiryDays.toLong() * 24 * 60 * 60 * 1000)
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                appendLine("⏳ *Points Expiry Date:* ${sdf.format(Date(defaultExpiry))}")
            }
            appendLine("--------------------------------")
            appendLine("Redeem your points on your next bill for instant cash discount at *$restName*!")
            if (!restaurant?.phone.isNullOrBlank()) appendLine("📞 Contact: ${restaurant?.phone}")
            if (!restaurant?.address.isNullOrBlank()) appendLine("📍 Address: ${restaurant?.address}")
            appendLine("\nSee you soon! 🍕🍔☕")
        }

        dispatchWhatsAppMessage(context, customer.contactNumber, text)
    }

    /**
     * Send Customer Visit Rewards Pass Status (Visits completed & Visit Pass Expiry) via WhatsApp.
     */
    fun sendCustomerVisitPassWhatsApp(
        context: Context,
        customer: CustomerEntity,
        restaurant: RestaurantEntity? = null
    ) {
        val restName = restaurant?.name?.trim()?.ifBlank { "BBC Food Hub" } ?: "BBC Food Hub"
        val cleanName = customer.name.trim().ifBlank { "Valued Customer" }
        val text = buildString {
            appendLine("🏬 *${restName.uppercase()}*")
            appendLine("🏆 *VISIT REWARDS PASS UPDATE*")
            appendLine()
            appendLine("Hello $cleanName! ❤️")
            appendLine()
            appendLine("Here is your Visit Pass progress:")
            appendLine()
            appendLine("🎯 *Completed Visits:* ${customer.loyaltyVisitCount} Visits")
            if (customer.loyaltyExpiryDate != null && customer.loyaltyExpiryDate > 0) {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                appendLine("⏳ *Visit Pass Valid Till:* ${sdf.format(Date(customer.loyaltyExpiryDate))}")
            }
            appendLine("--------------------------------")
            appendLine("Visit us again soon to complete your milestone and claim your free gift at *$restName*!")
            if (!restaurant?.phone.isNullOrBlank()) appendLine("📞 Contact: ${restaurant?.phone}")
            if (!restaurant?.address.isNullOrBlank()) appendLine("📍 Address: ${restaurant?.address}")
            appendLine("\nSee you soon! 🍕🍔☕")
        }

        dispatchWhatsAppMessage(context, customer.contactNumber, text)
    }

    private fun dispatchWhatsAppMessage(context: Context, contactNumber: String, text: String) {
        val phoneDigits = cleanPhoneNumber(contactNumber)
        if (phoneDigits.isNotBlank()) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedText"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback
            }
        }

        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share Status"
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(chooser)
        }
    }

    /**
     * Send Customer Loyalty & Points balance statement via WhatsApp (Backward compatible).
     */
    fun sendCustomerLoyaltyStatusWhatsApp(
        context: Context,
        customer: CustomerEntity,
        restaurant: RestaurantEntity? = null
    ) {
        sendCustomerPointsStatusWhatsApp(context, customer, restaurant)
    }
}
