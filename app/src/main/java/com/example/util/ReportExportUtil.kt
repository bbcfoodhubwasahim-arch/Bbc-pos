package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.data.local.entity.InventoryItemEntity
import com.example.data.local.entity.InventoryBatchEntity
import com.example.ui.screens.reports.DaySalesMetric
import com.example.ui.screens.reports.ItemSalesMetric
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ReportExportUtil {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private val COLOR_DARK_HEADER = Color.rgb(24, 38, 28) // Deep Emerald / Charcoal
    private val COLOR_ACCENT_GOLD = Color.rgb(212, 160, 23) // Warm Gold
    private val COLOR_TEXT_DARK = Color.rgb(30, 30, 30)
    private val COLOR_TEXT_MUTED = Color.rgb(100, 100, 100)
    private val COLOR_BG_LIGHT = Color.rgb(248, 246, 242)
    private val COLOR_CARD_BG = Color.rgb(242, 240, 234)
    private val COLOR_SUCCESS_GREEN = Color.rgb(34, 139, 34)
    private val COLOR_ERROR_RED = Color.rgb(180, 40, 40)
    private val COLOR_BORDER = Color.rgb(220, 215, 205)

    // =========================================================================
    // 1. CSV REPORT EXPORT
    // =========================================================================
    fun generateSalesReportCsv(
        context: Context,
        reportTitle: String,
        bills: List<BillEntity>,
        expenses: List<ExpenseEntity>,
        restaurant: RestaurantEntity
    ): File {
        val activeBills = bills.filter { it.isStockDeducted }
        val totalSales = activeBills.sumOf { it.totalAmount }
        val totalFoodCost = activeBills.sumOf { it.totalFoodCost }
        val grossProfit = totalSales - totalFoodCost
        val totalBills = activeBills.size
        val avgOrderValue = if (totalBills > 0) totalSales / totalBills else 0.0

        val dineInSales = activeBills.filter { it.orderType == "DINE_IN" }.sumOf { it.totalAmount }
        val takeawaySales = activeBills.filter { it.orderType == "TAKEAWAY" }.sumOf { it.totalAmount }
        val cashSales = activeBills.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount }
        val upiSales = activeBills.filter { it.paymentMethod == "UPI" }.sumOf { it.totalAmount }
        val totalExpenses = expenses.sumOf { it.amount }
        val netProfit = grossProfit - totalExpenses

        val fileName = "SalesReport_${reportTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        val timestampStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        file.bufferedWriter().use { writer ->
            // Restaurant & Report Header
            writer.write("\"SALES & ANALYTICS REPORT\"\n")
            writer.write("\"Restaurant Name\",\"${escapeCsv(restaurant.name)}\"\n")
            writer.write("\"Address\",\"${escapeCsv(restaurant.address)}\"\n")
            writer.write("\"Contact Phone\",\"${escapeCsv(restaurant.phone)}\"\n")
            writer.write("\"Report Period\",\"${escapeCsv(reportTitle)}\"\n")
            writer.write("\"Export Timestamp\",\"${escapeCsv(timestampStr)}\"\n\n")

            // Key Metrics Summary Table
            writer.write("\"=== KEY METRICS SUMMARY ===\"\n")
            writer.write("\"Metric\",\"Value\"\n")
            writer.write("\"Total Gross Sales (INR)\",\"${String.format(Locale.US, "%.2f", totalSales)}\"\n")
            writer.write("\"Total Settled Bills\",\"$totalBills\"\n")
            writer.write("\"Average Order Value (INR)\",\"${String.format(Locale.US, "%.2f", avgOrderValue)}\"\n")
            writer.write("\"Food Cost (FIFO COGS INR)\",\"${String.format(Locale.US, "%.2f", totalFoodCost)}\"\n")
            writer.write("\"Gross Profit (INR)\",\"${String.format(Locale.US, "%.2f", grossProfit)}\"\n")
            writer.write("\"Total Operating Expenses (INR)\",\"${String.format(Locale.US, "%.2f", totalExpenses)}\"\n")
            writer.write("\"Net Operating Profit/Loss (INR)\",\"${String.format(Locale.US, "%.2f", netProfit)}\"\n")
            writer.write("\"Dine In Sales (INR)\",\"${String.format(Locale.US, "%.2f", dineInSales)}\"\n")
            writer.write("\"Takeaway Sales (INR)\",\"${String.format(Locale.US, "%.2f", takeawaySales)}\"\n")
            writer.write("\"Cash Paid Sales (INR)\",\"${String.format(Locale.US, "%.2f", cashSales)}\"\n")
            writer.write("\"UPI Paid Sales (INR)\",\"${String.format(Locale.US, "%.2f", upiSales)}\"\n\n")

            // Transaction History
            writer.write("\"=== TRANSACTION DETAILS (BILLS) ===\"\n")
            writer.write("\"Bill Number\",\"Date & Time\",\"Order Type\",\"Table\",\"Customer Name\",\"Customer Phone\",\"Payment Method\",\"Subtotal (INR)\",\"Discount (INR)\",\"Points Redeemed\",\"Final Amount (INR)\",\"Food Cost (INR)\",\"Net Margin (INR)\",\"Items Ordered\"\n")

            activeBills.sortedByDescending { it.billTimestamp }.forEach { bill ->
                val dateStr = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(bill.billTimestamp))
                val itemsSummary = bill.items.joinToString(" ; ") { "${it.dishName} x${it.quantity} (₹${it.totalPrice.toInt()})" }
                val billMargin = bill.totalAmount - bill.totalFoodCost

                writer.write("\"${escapeCsv(bill.billNumber)}\",")
                writer.write("\"$dateStr\",")
                writer.write("\"${bill.orderType}\",")
                writer.write("\"${escapeCsv(bill.tableName ?: "")}\",")
                writer.write("\"${escapeCsv(bill.customerName)}\",")
                writer.write("\"${escapeCsv(bill.customerPhone)}\",")
                writer.write("\"${bill.paymentMethod}\",")
                writer.write("\"${String.format(Locale.US, "%.2f", bill.subtotal)}\",")
                writer.write("\"${String.format(Locale.US, "%.2f", bill.discountAmount)}\",")
                writer.write("\"${bill.pointsRedeemed}\",")
                writer.write("\"${String.format(Locale.US, "%.2f", bill.totalAmount)}\",")
                writer.write("\"${String.format(Locale.US, "%.2f", bill.totalFoodCost)}\",")
                writer.write("\"${String.format(Locale.US, "%.2f", billMargin)}\",")
                writer.write("\"${escapeCsv(itemsSummary)}\"\n")
            }
            writer.write("\n")

            // Item-wise Breakdown
            writer.write("\"=== ITEM-WISE SALES BREAKDOWN ===\"\n")
            writer.write("\"Rank\",\"Dish / Item Name\",\"Quantity Sold\",\"Total Sales (INR)\",\"Average Unit Price (INR)\",\"Revenue Share (%)\"\n")

            val itemMap = mutableMapOf<String, Pair<Int, Double>>()
            activeBills.forEach { b ->
                b.items.forEach { item ->
                    val curr = itemMap[item.dishName] ?: Pair(0, 0.0)
                    itemMap[item.dishName] = Pair(curr.first + item.quantity, curr.second + item.totalPrice)
                }
            }

            val itemMetrics = itemMap.entries.map { entry ->
                val count = entry.value.first
                val rev = entry.value.second
                val avg = if (count > 0) rev / count else 0.0
                val pct = if (totalSales > 0) (rev / totalSales) * 100.0 else 0.0
                ItemSalesMetric(entry.key, count, rev, avg, pct.toFloat())
            }.sortedByDescending { it.quantity }

            itemMetrics.forEachIndexed { idx, item ->
                writer.write("\"#${idx + 1}\",")
                writer.write("\"${escapeCsv(item.dishName)}\",")
                writer.write("\"${item.quantity}\",")
                writer.write("\"${String.format(Locale.US, "%.2f", item.totalRevenue)}\",")
                writer.write("\"${String.format(Locale.US, "%.2f", item.averagePrice)}\",")
                writer.write("\"${String.format(Locale.US, "%.1f", item.revenuePercent)}%\"\n")
            }
            writer.write("\n")

            // Expenses Breakdown if any
            if (expenses.isNotEmpty()) {
                writer.write("\"=== OPERATING EXPENSES ===\"\n")
                writer.write("\"Description\",\"Category\",\"Amount (INR)\",\"Payment Mode\",\"Date\"\n")
                expenses.sortedByDescending { it.timestamp }.forEach { exp ->
                    val expDate = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(exp.timestamp))
                    writer.write("\"${escapeCsv(exp.description)}\",")
                    writer.write("\"${escapeCsv(exp.categoryName)}\",")
                    writer.write("\"${String.format(Locale.US, "%.2f", exp.amount)}\",")
                    writer.write("\"${escapeCsv(exp.paymentMethod)}\",")
                    writer.write("\"$expDate\"\n")
                }
            }
        }

        return file
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ")
    }

    // =========================================================================
    // 2. PDF REPORT EXPORT
    // =========================================================================
    fun generateSalesReportPdf(
        context: Context,
        reportTitle: String,
        bills: List<BillEntity>,
        expenses: List<ExpenseEntity>,
        restaurant: RestaurantEntity
    ): File {
        val activeBills = bills.filter { it.isStockDeducted }
        val totalSales = activeBills.sumOf { it.totalAmount }
        val totalFoodCost = activeBills.sumOf { it.totalFoodCost }
        val grossProfit = totalSales - totalFoodCost
        val totalBills = activeBills.size
        val avgOrderValue = if (totalBills > 0) totalSales / totalBills else 0.0

        val dineInBills = activeBills.filter { it.orderType == "DINE_IN" }
        val dineInSales = dineInBills.sumOf { it.totalAmount }
        val takeawayBills = activeBills.filter { it.orderType == "TAKEAWAY" }
        val takeawaySales = takeawayBills.sumOf { it.totalAmount }

        val cashBills = activeBills.filter { it.paymentMethod == "CASH" }
        val cashSales = cashBills.sumOf { it.totalAmount }
        val upiBills = activeBills.filter { it.paymentMethod == "UPI" }
        val upiSales = upiBills.sumOf { it.totalAmount }

        val totalExpenses = expenses.sumOf { it.amount }
        val netProfit = grossProfit - totalExpenses

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val fontBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val fontNormal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        // Draw White Background
        fun clearCanvas(c: Canvas) {
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)
        }

        clearCanvas(canvas)

        var y = 30f

        // Draw Top Header Banner
        paint.color = COLOR_DARK_HEADER
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(20f, y, PAGE_WIDTH - 20f, y + 75f), 10f, 10f, paint)

        // Header Text
        textPaint.typeface = fontBold
        textPaint.textSize = 16f
        textPaint.color = Color.WHITE
        canvas.drawText(restaurant.name.ifBlank { "BBC POS RESTAURANT" }.uppercase(), 35f, y + 26f, textPaint)

        textPaint.typeface = fontNormal
        textPaint.textSize = 9f
        textPaint.color = Color.rgb(220, 220, 220)
        val addressLine = listOf(restaurant.address, restaurant.phone).filter { it.isNotBlank() }.joinToString(" • ")
        canvas.drawText(addressLine.ifBlank { "Sales & Financial Analytics Statement" }, 35f, y + 42f, textPaint)

        textPaint.typeface = fontBold
        textPaint.textSize = 12f
        textPaint.color = COLOR_ACCENT_GOLD
        canvas.drawText("REPORT: $reportTitle", 35f, y + 62f, textPaint)

        // Export Date Badge
        val timestampStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        textPaint.typeface = fontNormal
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText("Generated: $timestampStr", PAGE_WIDTH - 180f, y + 24f, textPaint)

        y += 90f

        // Check page overflow function
        fun checkNewPage() {
            if (y > PAGE_HEIGHT - 60f) {
                // Footer
                textPaint.typeface = fontNormal
                textPaint.textSize = 8f
                textPaint.color = COLOR_TEXT_MUTED
                canvas.drawText("Page $pageNumber • BBC POS Sales Analytics Report", 30f, PAGE_HEIGHT - 20f, textPaint)

                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                clearCanvas(canvas)
                y = 40f
            }
        }

        // Key Financial Metrics Grid (4 Cards)
        val cardW = (PAGE_WIDTH - 50f) / 2f
        val cardH = 46f

        // Row 1: Total Sales & Avg Order Value
        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 20f, y, cardW, cardH, "TOTAL REVENUE", "₹${String.format(Locale.US, "%.2f", totalSales)}", "$totalBills settled bills", COLOR_SUCCESS_GREEN)
        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 30f + cardW, y, cardW, cardH, "AVG ORDER VALUE", "₹${String.format(Locale.US, "%.2f", avgOrderValue)}", "per invoice avg", COLOR_TEXT_DARK)
        y += cardH + 10f

        // Row 2: Food Cost (COGS) & Gross Profit
        val foodCostPct = if (totalSales > 0) (totalFoodCost / totalSales) * 100.0 else 0.0
        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 20f, y, cardW, cardH, "FOOD COST (FIFO COGS)", "₹${String.format(Locale.US, "%.2f", totalFoodCost)}", "${String.format(Locale.US, "%.1f", foodCostPct)}% of sales", COLOR_TEXT_DARK)
        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 30f + cardW, y, cardW, cardH, "GROSS OPERATING PROFIT", "₹${String.format(Locale.US, "%.2f", grossProfit)}", "${String.format(Locale.US, "%.1f", 100.0 - foodCostPct)}% food margin", COLOR_SUCCESS_GREEN)
        y += cardH + 12f

        // Row 3: Operating Expenses & Net Operating Profit
        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 20f, y, cardW, cardH, "OPERATING EXPENSES", "₹${String.format(Locale.US, "%.2f", totalExpenses)}", "${expenses.size} expense entries", COLOR_ERROR_RED)
        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 30f + cardW, y, cardW, cardH, "NET OPERATING PROFIT", "${if (netProfit >= 0) "₹" else "-₹"}${String.format(Locale.US, "%.2f", Math.abs(netProfit))}", "after all expenses", if (netProfit >= 0) COLOR_SUCCESS_GREEN else COLOR_ERROR_RED)
        y += cardH + 16f

        // Order Flow & Payment Mode Breakdown Box
        paint.color = COLOR_CARD_BG
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(20f, y, PAGE_WIDTH - 20f, y + 42f), 6f, 6f, paint)

        textPaint.typeface = fontBold
        textPaint.textSize = 9f
        textPaint.color = COLOR_TEXT_DARK
        canvas.drawText("ORDER TYPE & PAYMENT BREAKDOWN", 30f, y + 16f, textPaint)

        textPaint.typeface = fontNormal
        textPaint.textSize = 8.5f
        textPaint.color = COLOR_TEXT_MUTED
        val breakdownLine1 = "Dine In: ${dineInBills.size} bills (₹${String.format(Locale.US, "%.2f", dineInSales)})    •    Takeaway: ${takeawayBills.size} bills (₹${String.format(Locale.US, "%.2f", takeawaySales)})"
        val breakdownLine2 = "Cash Paid: ${cashBills.size} bills (₹${String.format(Locale.US, "%.2f", cashSales)})    •    UPI Paid: ${upiBills.size} bills (₹${String.format(Locale.US, "%.2f", upiSales)})"
        canvas.drawText(breakdownLine1, 30f, y + 28f, textPaint)
        canvas.drawText(breakdownLine2, 300f, y + 28f, textPaint)

        y += 54f

        // Item-Wise Sales Breakdown Table
        textPaint.typeface = fontBold
        textPaint.textSize = 10.5f
        textPaint.color = COLOR_TEXT_DARK
        canvas.drawText("ITEM-WISE SALES BREAKDOWN", 20f, y, textPaint)
        y += 10f

        // Draw Table Header
        paint.color = COLOR_DARK_HEADER
        paint.style = Paint.Style.FILL
        canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 18f, paint)

        textPaint.typeface = fontBold
        textPaint.textSize = 8f
        textPaint.color = Color.WHITE
        canvas.drawText("#", 26f, y + 12f, textPaint)
        canvas.drawText("Dish / Item Name", 50f, y + 12f, textPaint)
        canvas.drawText("Qty Sold", 300f, y + 12f, textPaint)
        canvas.drawText("Avg Price", 380f, y + 12f, textPaint)
        canvas.drawText("Total Revenue (₹)", 460f, y + 12f, textPaint)
        canvas.drawText("% Share", 540f, y + 12f, textPaint)

        y += 18f

        val itemMap = mutableMapOf<String, Pair<Int, Double>>()
        activeBills.forEach { b ->
            b.items.forEach { item ->
                val curr = itemMap[item.dishName] ?: Pair(0, 0.0)
                itemMap[item.dishName] = Pair(curr.first + item.quantity, curr.second + item.totalPrice)
            }
        }

        val itemMetrics = itemMap.entries.map { entry ->
            val count = entry.value.first
            val rev = entry.value.second
            val avg = if (count > 0) rev / count else 0.0
            val pct = if (totalSales > 0) (rev / totalSales) * 100.0 else 0.0
            ItemSalesMetric(entry.key, count, rev, avg, pct.toFloat())
        }.sortedByDescending { it.quantity }

        itemMetrics.take(25).forEachIndexed { idx, item ->
            checkNewPage()

            paint.color = if (idx % 2 == 0) COLOR_BG_LIGHT else Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 16f, paint)

            textPaint.typeface = fontNormal
            textPaint.textSize = 8f
            textPaint.color = COLOR_TEXT_DARK

            canvas.drawText("${idx + 1}", 26f, y + 11f, textPaint)
            val dishTruncated = if (item.dishName.length > 36) item.dishName.substring(0, 34) + ".." else item.dishName
            canvas.drawText(dishTruncated, 50f, y + 11f, textPaint)
            canvas.drawText("${item.quantity}", 300f, y + 11f, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.1f", item.averagePrice)}", 380f, y + 11f, textPaint)

            textPaint.typeface = fontBold
            canvas.drawText("₹${String.format(Locale.US, "%.2f", item.totalRevenue)}", 460f, y + 11f, textPaint)

            textPaint.typeface = fontNormal
            canvas.drawText("${String.format(Locale.US, "%.1f", item.revenuePercent)}%", 540f, y + 11f, textPaint)

            y += 16f
        }

        y += 14f
        checkNewPage()

        // Transaction Log Header
        textPaint.typeface = fontBold
        textPaint.textSize = 10.5f
        textPaint.color = COLOR_TEXT_DARK
        canvas.drawText("BILL TRANSACTIONS SUMMARY (Top 30 Recent)", 20f, y, textPaint)
        y += 10f

        paint.color = COLOR_DARK_HEADER
        paint.style = Paint.Style.FILL
        canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 18f, paint)

        textPaint.typeface = fontBold
        textPaint.textSize = 8f
        textPaint.color = Color.WHITE
        canvas.drawText("Bill #", 26f, y + 12f, textPaint)
        canvas.drawText("Date & Time", 90f, y + 12f, textPaint)
        canvas.drawText("Type", 210f, y + 12f, textPaint)
        canvas.drawText("Payment", 270f, y + 12f, textPaint)
        canvas.drawText("Customer Name", 330f, y + 12f, textPaint)
        canvas.drawText("Food Cost", 460f, y + 12f, textPaint)
        canvas.drawText("Total (₹)", 520f, y + 12f, textPaint)

        y += 18f

        activeBills.sortedByDescending { it.billTimestamp }.take(30).forEachIndexed { idx, bill ->
            checkNewPage()

            paint.color = if (idx % 2 == 0) COLOR_BG_LIGHT else Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 16f, paint)

            textPaint.typeface = fontNormal
            textPaint.textSize = 8f
            textPaint.color = COLOR_TEXT_DARK

            canvas.drawText(bill.billNumber, 26f, y + 11f, textPaint)
            val dt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(bill.billTimestamp))
            canvas.drawText(dt, 90f, y + 11f, textPaint)
            canvas.drawText(bill.orderType, 210f, y + 11f, textPaint)
            canvas.drawText(bill.paymentMethod, 270f, y + 11f, textPaint)

            val custStr = if (bill.customerName.isNotBlank()) bill.customerName else "Walk-In"
            val custTruncated = if (custStr.length > 20) custStr.substring(0, 18) + ".." else custStr
            canvas.drawText(custTruncated, 330f, y + 11f, textPaint)

            canvas.drawText("₹${String.format(Locale.US, "%.1f", bill.totalFoodCost)}", 460f, y + 11f, textPaint)

            textPaint.typeface = fontBold
            textPaint.color = COLOR_SUCCESS_GREEN
            canvas.drawText("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", 520f, y + 11f, textPaint)

            y += 16f
        }

        // Draw Final Footer on Last Page
        textPaint.typeface = fontNormal
        textPaint.textSize = 8f
        textPaint.color = COLOR_TEXT_MUTED
        canvas.drawText("Page $pageNumber • BBC POS Sales Analytics Report", 30f, PAGE_HEIGHT - 20f, textPaint)

        pdfDocument.finishPage(page)

        val fileName = "SalesReport_${reportTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    private fun drawMetricBox(
        canvas: Canvas,
        paint: Paint,
        textPaint: Paint,
        fontBold: Typeface,
        fontNormal: Typeface,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        value: String,
        subtitle: String,
        valueColor: Int
    ) {
        paint.color = COLOR_CARD_BG
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, paint)

        textPaint.typeface = fontBold
        textPaint.textSize = 7.5f
        textPaint.color = COLOR_TEXT_MUTED
        canvas.drawText(title, x + 10f, y + 12f, textPaint)

        textPaint.typeface = fontBold
        textPaint.textSize = 13f
        textPaint.color = valueColor
        canvas.drawText(value, x + 10f, y + 28f, textPaint)

        textPaint.typeface = fontNormal
        textPaint.textSize = 7.5f
        textPaint.color = COLOR_TEXT_MUTED
        canvas.drawText(subtitle, x + 10f, y + 39f, textPaint)
    }

    // =========================================================================
    // 3. FILE SAVING, SHARING & OPENING UTILITIES
    // =========================================================================
    fun saveToDownloads(context: Context, sourceFile: File, mimeType: String): Pair<Boolean, String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    Pair(true, "Saved report '${sourceFile.name}' to Downloads folder!")
                } else {
                    Pair(false, "Failed to create MediaStore entry")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, sourceFile.name)
                sourceFile.copyTo(targetFile, overwrite = true)
                Pair(true, "Saved to ${targetFile.absolutePath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Failed to save: ${e.localizedMessage}")
        }
    }

    fun shareReportFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Here is the exported Sales & Analytics Report ($title).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share $title"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openReportFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app available to view this file type", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // 4. INVENTORY REPORT EXPORT
    // =========================================================================
    fun generateInventoryReportCsv(
        context: Context,
        inventoryList: List<InventoryItemEntity>,
        batchesList: List<InventoryBatchEntity>,
        restaurant: RestaurantEntity
    ): File {
        val fileName = "InventoryReport_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        val timestampStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        val activeBatches = batchesList.filter { batch ->
            inventoryList.any { item -> item.id == batch.inventoryItemId } &&
            batch.remainingQuantity > 0.000001 &&
            batch.status != "ARCHIVED"
        }
        val totalValuation = activeBatches.sumOf { it.remainingQuantity * it.purchaseRate }

        file.bufferedWriter().use { writer ->
            writer.write("\"INVENTORY STOCK & VALUATION REPORT\"\n")
            writer.write("\"Restaurant Name\",\"${escapeCsv(restaurant.name)}\"\n")
            writer.write("\"Address\",\"${escapeCsv(restaurant.address)}\"\n")
            writer.write("\"Contact Phone\",\"${escapeCsv(restaurant.phone)}\"\n")
            writer.write("\"Export Timestamp\",\"${escapeCsv(timestampStr)}\"\n")
            writer.write("\"Total Inventory Value (INR)\",\"${String.format(Locale.US, "%.2f", totalValuation)}\"\n\n")

            writer.write("\"[ INVENTORY STOCK DETAILS ]\"\n")
            writer.write("\"Item Name\",\"Current Stock\",\"Unit\",\"Threshold\",\"Status\",\"Last Purchase Rate (INR)\",\"Estimated Value (INR)\"\n")

            inventoryList.forEach { item ->
                val isLowStock = item.currentStock <= item.lowStockThreshold
                val statusStr = if (isLowStock) "LOW STOCK" else "IN STOCK"
                val itemBatches = activeBatches.filter { it.inventoryItemId == item.id }
                val itemValuation = if (item.currentStock <= 0.0) 0.0 else itemBatches.sumOf { it.remainingQuantity * it.purchaseRate }.let { if (it > 0.0) it else item.currentStock * item.purchasePrice }
                
                writer.write("\"${escapeCsv(item.name)}\",\"${String.format(Locale.US, "%.2f", item.currentStock)}\",\"${escapeCsv(item.unit)}\",\"${String.format(Locale.US, "%.2f", item.lowStockThreshold)}\",\"$statusStr\",\"${String.format(Locale.US, "%.2f", item.purchasePrice)}\",\"${String.format(Locale.US, "%.2f", itemValuation)}\"\n")
            }

            writer.write("\n\"[ ACTIVE BATCH DETAILS ]\"\n")
            writer.write("\"Item Name\",\"Batch ID\",\"Purchase Date\",\"Original Qty\",\"Remaining Qty\",\"Unit\",\"Purchase Rate (INR)\",\"Remaining Value (INR)\",\"Notes\"\n")

            activeBatches.sortedBy { it.timestamp }.forEach { batch ->
                val dateStr = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(batch.timestamp))
                val batchVal = batch.remainingQuantity * batch.purchaseRate
                writer.write("\"${escapeCsv(batch.itemName)}\",\"${escapeCsv(batch.id)}\",\"$dateStr\",\"${String.format(Locale.US, "%.2f", batch.initialQuantity)}\",\"${String.format(Locale.US, "%.2f", batch.remainingQuantity)}\",\"${escapeCsv(batch.unit)}\",\"${String.format(Locale.US, "%.2f", batch.purchaseRate)}\",\"${String.format(Locale.US, "%.2f", batchVal)}\",\"${escapeCsv(batch.notes)}\"\n")
            }
        }
        return file
    }

    fun generateInventoryReportPdf(
        context: Context,
        inventoryList: List<InventoryItemEntity>,
        batchesList: List<InventoryBatchEntity>,
        restaurant: RestaurantEntity
    ): File {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val fontBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val fontNormal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        fun clearCanvas(c: Canvas) {
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)
        }

        clearCanvas(canvas)

        var y = 30f

        // Top Header Banner
        paint.color = COLOR_DARK_HEADER
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(20f, y, PAGE_WIDTH - 20f, y + 75f), 10f, 10f, paint)

        // Header Text
        textPaint.typeface = fontBold
        textPaint.textSize = 15f
        textPaint.color = Color.WHITE
        canvas.drawText(restaurant.name.ifBlank { "BBC POS RESTAURANT" }.uppercase(), 35f, y + 26f, textPaint)

        textPaint.typeface = fontNormal
        textPaint.textSize = 9f
        textPaint.color = Color.rgb(220, 220, 220)
        val addressLine = listOf(restaurant.address, restaurant.phone).filter { it.isNotBlank() }.joinToString(" • ")
        canvas.drawText(addressLine.ifBlank { "Inventory Stock & Valuation Report" }, 35f, y + 42f, textPaint)

        textPaint.typeface = fontBold
        textPaint.textSize = 12f
        textPaint.color = COLOR_ACCENT_GOLD
        canvas.drawText("INVENTORY STOCK STATUS REPORT", 35f, y + 62f, textPaint)

        // Export Date Badge
        val timestampStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        textPaint.typeface = fontNormal
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText("Generated: $timestampStr", PAGE_WIDTH - 180f, y + 24f, textPaint)

        y += 90f

        fun checkNewPage() {
            if (y > PAGE_HEIGHT - 60f) {
                // Footer
                textPaint.typeface = fontNormal
                textPaint.textSize = 8f
                textPaint.color = COLOR_TEXT_MUTED
                canvas.drawText("Page $pageNumber • BBC POS Inventory Report", 30f, PAGE_HEIGHT - 20f, textPaint)

                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                clearCanvas(canvas)
                y = 40f
            }
        }

        val activeBatches = batchesList.filter { it.remainingQuantity > 0.000001 && it.status != "ARCHIVED" }
        val totalValuation = activeBatches.sumOf { it.remainingQuantity * it.purchaseRate }
        val lowStockItems = inventoryList.filter { it.currentStock <= it.lowStockThreshold }

        // Metrics Grid (2 Cards)
        val cardW = (PAGE_WIDTH - 50f) / 2f
        val cardH = 46f

        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 20f, y, cardW, cardH, "TOTAL STOCK VALUE", "₹${String.format(Locale.US, "%.2f", totalValuation)}", "based on active batches", COLOR_SUCCESS_GREEN)
        drawMetricBox(canvas, paint, textPaint, fontBold, fontNormal, 30f + cardW, y, cardW, cardH, "LOW STOCK ALERTS", "${lowStockItems.size} items", "require refilling soon", if (lowStockItems.isNotEmpty()) COLOR_ERROR_RED else COLOR_SUCCESS_GREEN)
        y += cardH + 20f

        // Section Title: Inventory Status List
        textPaint.typeface = fontBold
        textPaint.textSize = 10.5f
        textPaint.color = COLOR_TEXT_DARK
        canvas.drawText("CURRENT INVENTORY STOCK DETAILS", 20f, y, textPaint)
        y += 10f

        // Draw Table Header
        paint.color = COLOR_DARK_HEADER
        paint.style = Paint.Style.FILL
        canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 18f, paint)

        textPaint.typeface = fontBold
        textPaint.textSize = 8f
        textPaint.color = Color.WHITE
        canvas.drawText("#", 26f, y + 12f, textPaint)
        canvas.drawText("Item Name", 50f, y + 12f, textPaint)
        canvas.drawText("Current Stock", 240f, y + 12f, textPaint)
        canvas.drawText("Threshold", 320f, y + 12f, textPaint)
        canvas.drawText("Status", 400f, y + 12f, textPaint)
        canvas.drawText("Est Value", 480f, y + 12f, textPaint)

        y += 18f

        inventoryList.forEachIndexed { idx, item ->
            checkNewPage()

            paint.color = if (idx % 2 == 0) COLOR_BG_LIGHT else Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 16f, paint)

            textPaint.typeface = fontNormal
            textPaint.textSize = 8f
            textPaint.color = COLOR_TEXT_DARK

            canvas.drawText("${idx + 1}", 26f, y + 11f, textPaint)
            val nameTruncated = if (item.name.length > 30) item.name.substring(0, 28) + ".." else item.name
            canvas.drawText(nameTruncated, 50f, y + 11f, textPaint)
            canvas.drawText("${String.format(Locale.US, "%.2f", item.currentStock).replace(".00", "")} ${item.unit}", 240f, y + 11f, textPaint)
            canvas.drawText("${String.format(Locale.US, "%.2f", item.lowStockThreshold).replace(".00", "")} ${item.unit}", 320f, y + 11f, textPaint)

            val isLowStock = item.currentStock <= item.lowStockThreshold
            textPaint.typeface = fontBold
            if (isLowStock) {
                textPaint.color = COLOR_ERROR_RED
                canvas.drawText("LOW STOCK", 400f, y + 11f, textPaint)
            } else {
                textPaint.color = COLOR_SUCCESS_GREEN
                canvas.drawText("IN STOCK", 400f, y + 11f, textPaint)
            }

            textPaint.color = COLOR_TEXT_DARK
            val itemVal = activeBatches.filter { it.inventoryItemId == item.id }.sumOf { it.remainingQuantity * it.purchaseRate }
            canvas.drawText("₹${String.format(Locale.US, "%.1f", itemVal)}", 480f, y + 11f, textPaint)

            y += 16f
        }

        y += 20f
        checkNewPage()

        // Section Title: Active Batches Details
        textPaint.typeface = fontBold
        textPaint.textSize = 10.5f
        textPaint.color = COLOR_TEXT_DARK
        canvas.drawText("ACTIVE STOCK BATCHES TRACEABILITY", 20f, y, textPaint)
        y += 10f

        paint.color = COLOR_DARK_HEADER
        paint.style = Paint.Style.FILL
        canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 18f, paint)

        textPaint.typeface = fontBold
        textPaint.textSize = 8f
        textPaint.color = Color.WHITE
        canvas.drawText("Item Name", 26f, y + 12f, textPaint)
        canvas.drawText("Purchase Date", 200f, y + 12f, textPaint)
        canvas.drawText("Orig Qty", 300f, y + 12f, textPaint)
        canvas.drawText("Rem Qty", 360f, y + 12f, textPaint)
        canvas.drawText("Rate/Unit", 420f, y + 12f, textPaint)
        canvas.drawText("Rem Value (₹)", 480f, y + 12f, textPaint)

        y += 18f

        activeBatches.sortedByDescending { it.timestamp }.take(40).forEachIndexed { bIdx, batch ->
            checkNewPage()

            paint.color = if (bIdx % 2 == 0) COLOR_BG_LIGHT else Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 16f, paint)

            textPaint.typeface = fontNormal
            textPaint.textSize = 8f
            textPaint.color = COLOR_TEXT_DARK

            val itemTruncated = if (batch.itemName.length > 24) batch.itemName.substring(0, 22) + ".." else batch.itemName
            canvas.drawText(itemTruncated, 26f, y + 11f, textPaint)

            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(batch.timestamp))
            canvas.drawText(dateStr, 200f, y + 11f, textPaint)
            canvas.drawText("${String.format(Locale.US, "%.1f", batch.initialQuantity).replace(".00", "")} ${batch.unit}", 300f, y + 11f, textPaint)
            canvas.drawText("${String.format(Locale.US, "%.1f", batch.remainingQuantity).replace(".00", "")} ${batch.unit}", 360f, y + 11f, textPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.1f", batch.purchaseRate)}", 420f, y + 11f, textPaint)

            textPaint.typeface = fontBold
            val remVal = batch.remainingQuantity * batch.purchaseRate
            canvas.drawText("₹${String.format(Locale.US, "%.1f", remVal)}", 480f, y + 11f, textPaint)

            y += 16f
        }

        // Draw Final Footer on Last Page
        textPaint.typeface = fontNormal
        textPaint.textSize = 8f
        textPaint.color = COLOR_TEXT_MUTED
        canvas.drawText("Page $pageNumber • BBC POS Inventory Report", 30f, PAGE_HEIGHT - 20f, textPaint)

        pdfDocument.finishPage(page)

        val fileName = "InventoryReport_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }
}
