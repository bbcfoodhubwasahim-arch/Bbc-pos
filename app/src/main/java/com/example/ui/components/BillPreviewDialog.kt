package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.theme.*
import com.example.util.BillShareUtil
import com.example.util.PdfInvoiceGenerator
import com.example.util.ReceiptBitmapGenerator
import java.text.SimpleDateFormat
import java.util.*

enum class BillTemplateDesign(val id: String, val title: String, val subtitle: String) {
    UNIVERSAL_CAFE_PRO("UNIVERSAL_CAFE_PRO", "Universal Cafe Pro (Recommended)", "Unified Standard Cafe Bill"),
    MODERN_SIGNATURE("MODERN_SIGNATURE", "Modern Signature", "Clean & Bold"),
    RECEIPT_BOX("RECEIPT_BOX", "Receipt Box (Khata)", "Highlights Pending Dues"),
    CLASSIC_THERMAL("CLASSIC_THERMAL", "Classic 80mm", "Thermal Paper Style"),
    COMPACT_MINI("COMPACT_MINI", "Compact 58mm", "Quick Narrow Slip"),
    ELEGANT_DINE_IN("ELEGANT_DINE_IN", "Elegant Dine-In", "Cafe & Bistro Look"),
    GST_TAX_INVOICE("GST_TAX_INVOICE", "GST Tax Invoice", "Official Tax Grid"),
    RETRO_FOODIE("RETRO_FOODIE", "Retro Diner", "Trendy Street Food"),
    MINIMAL_CLEAN("MINIMAL_CLEAN", "Minimal Clean", "Sleek & Contemporary"),
    FAST_FOOD_EXPRESS("FAST_FOOD_EXPRESS", "Fast Food Express", "Bold Token & Fast Service"),
    ROYAL_PREMIUM("ROYAL_PREMIUM", "Royal Premium", "Deep Navy & Gold Border"),
    KITCHEN_DETAILED("KITCHEN_DETAILED", "Itemized & Notes", "Detailed with Kitchen Notes");

    companion object {
        fun fromId(rawId: String?): BillTemplateDesign {
            if (rawId.isNullOrBlank()) return UNIVERSAL_CAFE_PRO
            val cleaned = rawId.uppercase().trim()
            return when (cleaned) {
                "UNIVERSAL_CAFE_PRO", "UNIVERSAL", "PRO", "DEFAULT", "ELEGANT_DINE_IN", "A4", "POINT_FIVE_ELEGANT" -> UNIVERSAL_CAFE_PRO
                "GST_TAX_INVOICE" -> GST_TAX_INVOICE
                "RETRO_FOODIE" -> RETRO_FOODIE
                "MINIMAL_CLEAN" -> MINIMAL_CLEAN
                "FAST_FOOD_EXPRESS" -> FAST_FOOD_EXPRESS
                "ROYAL_PREMIUM" -> ROYAL_PREMIUM
                "KITCHEN_DETAILED" -> KITCHEN_DETAILED
                else -> entries.find { it.id == cleaned } ?: UNIVERSAL_CAFE_PRO
            }
        }
    }
}

@Composable
fun BillPreviewDialog(
    bill: BillEntity,
    restaurant: RestaurantEntity,
    previousDue: Double = 0.0,
    onDismiss: () -> Unit,
    onSetDefaultTemplate: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Standard Bill Preview (Universal Pro)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Unified design for Print, WhatsApp HD Image & PDF Invoice",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // In-App Paper Receipt Display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE9EEF4))
                        .padding(12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .shadow(8.dp, shape = RoundedCornerShape(6.dp))
                            .background(Color.White, shape = RoundedCornerShape(6.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        UniversalCafeProReceipt(bill, restaurant, previousDue)
                    }
                }

                // Bottom Action Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Print Button
                            Button(
                                onClick = {
                                    val targetRest = restaurant.copy(billFormat = "UNIVERSAL_CAFE_PRO")
                                    BillShareUtil.printThermalReceiptDirect(context, bill, targetRest)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Print Thermal", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            // Copy/SMS Text Summary
                            OutlinedButton(
                                onClick = {
                                    val text = BillShareUtil.buildBillSummaryText(bill, restaurant, previousDue, context)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Bill Summary", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "📋 Bill summary copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Text", fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // WhatsApp HD Image Button
                            Button(
                                onClick = {
                                    try {
                                        val waRestaurant = restaurant.copy(billFormat = "UNIVERSAL_CAFE_PRO")
                                        val imgFile = ReceiptBitmapGenerator.generateReceiptImageFile(context, bill, waRestaurant, previousDue)
                                        BillShareUtil.sendViaWhatsAppImage(context, bill, waRestaurant, imgFile, previousDue)
                                    } catch (e: Exception) {
                                        val waRestaurant = restaurant.copy(billFormat = "UNIVERSAL_CAFE_PRO")
                                        BillShareUtil.sendViaWhatsApp(context, bill, waRestaurant, previousDue)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075E54)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp Image", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }

                            // Send WhatsApp with PDF + Caption
                            Button(
                                onClick = {
                                    try {
                                        val waRestaurant = restaurant.copy(billFormat = "UNIVERSAL_CAFE_PRO")
                                        val pdfFile = PdfInvoiceGenerator.generateA4Pdf(context, bill, waRestaurant, previousDue)
                                        BillShareUtil.sendViaWhatsAppPdf(context, bill, waRestaurant, pdfFile, previousDue)
                                    } catch (e: Exception) {
                                        val waRestaurant = restaurant.copy(billFormat = "UNIVERSAL_CAFE_PRO")
                                        BillShareUtil.sendViaWhatsApp(context, bill, waRestaurant, previousDue)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp PDF", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SolidBlackReceiptText = Color(0xFF1A1A1A)

// ==========================================
// 0. UNIVERSAL CAFE PRO (UNIFIED FLAGSHIP FORMAT)
// ==========================================
@Composable
fun UniversalCafeProReceipt(
    bill: BillEntity,
    restaurant: RestaurantEntity,
    previousDue: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val sdf = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.getDefault())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.5.dp, Color.Black, RectangleShape)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Logo (Only if showLogoOnBill is enabled)
        if (restaurant.showLogoOnBill) {
            if (!restaurant.customLogoUri.isNullOrBlank()) {
                AsyncImage(
                    model = restaurant.customLogoUri,
                    contentDescription = "Cafe Logo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RectangleShape)
                )
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Surface(
                    color = Color.Black,
                    shape = RectangleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "BBC",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // 2. Cafe Name & Tagline
        Text(
            text = restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(),
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            letterSpacing = 0.5.sp,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        if (restaurant.tagline.isNotBlank()) {
            Text(
                text = restaurant.tagline,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }

        if (restaurant.address.isNotBlank()) {
            Text(
                text = restaurant.address,
                fontSize = 11.sp,
                color = Color(0xFF334155),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        val phones = listOfNotNull(
            restaurant.phone.takeIf { it.isNotBlank() },
            restaurant.altPhone.takeIf { it.isNotBlank() }
        ).joinToString(" / ")
        if (phones.isNotBlank()) {
            Text(
                text = "Contact: $phones",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155)
            )
        }

        // FSSAI & GSTIN
        val regDetails = mutableListOf<String>()
        if (restaurant.isFssaiEnabled && restaurant.showFssaiOnBill && restaurant.fssaiNumber.isNotBlank()) {
            regDetails.add("FSSAI: ${restaurant.fssaiNumber}")
        }
        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstNumber.isNotBlank()) {
            regDetails.add("GSTIN: ${restaurant.gstNumber}")
        }
        if (regDetails.isNotEmpty()) {
            Text(
                text = regDetails.joinToString(" • "),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF475569)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.5.dp, color = Color.Black)
        Spacer(modifier = Modifier.height(6.dp))

        // 3. Token & Order Details Banner (Square frame)
        Surface(
            color = Color.White,
            shape = RectangleShape,
            border = BorderStroke(1.dp, Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tokenText = if (restaurant.showTokenOnBill) "TOKEN #${bill.billNumber}" else "Bill #${bill.billNumber}"
                    Text(
                        text = tokenText,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color.Black
                    )

                    val orderTypeLabel = when {
                        !bill.tableName.isNullOrBlank() -> "Table: ${bill.tableName} (Dine-In)"
                        bill.orderType.equals("TAKEAWAY", ignoreCase = true) -> "Takeaway / Parcel"
                        bill.orderType.equals("DELIVERY", ignoreCase = true) -> "Delivery"
                        else -> "Dine-In"
                    }
                    Text(
                        text = orderTypeLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Date: ${sdf.format(Date(bill.billTimestamp))}",
                        fontSize = 11.sp,
                        color = Color.Black
                    )
                    if (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()) {
                        val custText = "Cust: ${bill.customerName.ifBlank { "Guest" }} ${if (bill.customerPhone.isNotBlank()) "(${bill.customerPhone})" else ""}"
                        Text(
                            text = custText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Table Header (Black & White with solid lines)
        HorizontalDivider(thickness = 1.5.dp, color = Color.Black)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ITEM", modifier = Modifier.weight(2.2f), fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Black)
            Text("QTY", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color.Black)
            Text("RATE", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.End, color = Color.Black)
            Text("AMOUNT", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.End, color = Color.Black)
        }
        HorizontalDivider(thickness = 1.dp, color = Color.Black)

        bill.items.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(2.2f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.dishName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        if (item.isFree) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = Color(0xFF16A34A).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "🎁 FREE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${item.quantity}",
                        modifier = Modifier.weight(0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF0F172A)
                    )

                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", item.unitPrice)}",
                        modifier = Modifier.weight(0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        color = Color.Black
                    )

                    val isItemFree = item.isFree || item.totalPrice == 0.0
                    Text(
                        text = if (isItemFree) "₹0.00" else "₹${String.format(Locale.US, "%.2f", item.totalPrice)}",
                        modifier = Modifier.weight(1.1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        color = Color.Black
                    )
                }

                val addonsList = item.getSelectedAddonsList()
                if (addonsList.isNotEmpty()) {
                    addonsList.forEach { addon ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "↳ + ${addon.name} (${addon.quantity * item.quantity}x)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", addon.totalPrice * item.quantity)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                val itemNote = item.cookingNotes.ifBlank { item.notes }
                if (itemNote.isNotBlank()) {
                    Text(
                        text = "↳ Note: $itemNote",
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF444444),
                        modifier = Modifier.padding(start = 10.dp, top = 1.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFDDDDDD))
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5. Totals & Breakdown
        val subtotal = bill.subtotal
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal (${bill.items.sumOf { it.quantity }} items):", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Text("₹${String.format(Locale.US, "%.2f", subtotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        if (bill.discountAmount > 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val discLabel = if (bill.appliedOfferName.isNotBlank()) "Offer (${bill.appliedOfferName}):" else "Discount:"
                Text(discLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text("-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
        }

        if (bill.pointsRedeemed > 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Points Redeemed (${bill.pointsRedeemed} Pts):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text("-₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
        }

        if (restaurant.isGstEnabled && restaurant.showGstOnBill && restaurant.gstRate > 0) {
            val gstAmount = bill.totalAmount * (restaurant.gstRate / 100.0)
            val halfGst = gstAmount / 2.0
            val rate = restaurant.gstRate / 2.0
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CGST ($rate%):", fontSize = 11.sp, color = Color(0xFF333333))
                Text("₹${String.format(Locale.US, "%.2f", halfGst)}", fontSize = 11.sp, color = Color.Black)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SGST ($rate%):", fontSize = 11.sp, color = Color(0xFF333333))
                Text("₹${String.format(Locale.US, "%.2f", halfGst)}", fontSize = 11.sp, color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(thickness = 1.5.dp, color = Color.Black)
        Spacer(modifier = Modifier.height(4.dp))

        // 6. Grand Total & Payment Mode
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GRAND TOTAL:", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.Black)
            Text("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.Black)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Payment Status:", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            val mode = when (bill.paymentMethod.uppercase()) {
                "UPI" -> "PAID VIA UPI"
                "CASH" -> "PAID VIA CASH"
                "SPLIT" -> "PAID VIA SPLIT"
                "CREDIT" -> "CREDIT / KHATA DUE"
                else -> "${bill.paymentMethod} (PAID)"
            }
            Text(mode, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
        }

        // 7. Single Offer Savings Box (Square border)
        val totalSavings = bill.discountAmount + bill.pointsRedeemed.toDouble()
        if (totalSavings > 0 && restaurant.showSavingsOnBill) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color.White,
                shape = RectangleShape,
                border = BorderStroke(1.dp, Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                val offerName = bill.appliedOfferName.ifBlank { "OFFER DISCOUNT" }
                Text(
                    text = "🎉 SAVINGS ON THIS BILL: ₹${String.format(Locale.US, "%.2f", totalSavings)} ($offerName)",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp)
                )
            }
        }

        // 8. Previous Dues / Khata Box (Square border)
        if (previousDue > 0 && restaurant.showPreviousDueOnBill) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color.White,
                shape = RectangleShape,
                border = BorderStroke(1.dp, Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Previous Account Due:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("₹${String.format(Locale.US, "%.2f", previousDue)}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                    val netDue = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Outstanding Balance:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        Text("₹${String.format(Locale.US, "%.2f", netDue)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }

        // 9. Loyalty Points Summary (Clean 1-line Square Box)
        if (restaurant.showPointsOnBill && (bill.rewardPointsEarned > 0 || bill.pointsRedeemed > 0)) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color.White,
                shape = RectangleShape,
                border = BorderStroke(1.dp, Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⭐ Reward Points: +${bill.rewardPointsEarned} Pts Earned on this Bill",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                )
            }
        }

        // 10. UPI QR / Payment / Wi-Fi / Social
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFCBD5E1))
        Spacer(modifier = Modifier.height(6.dp))

        if (restaurant.upiQrEnabled && restaurant.upiId.isNotBlank()) {
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Scan & Pay via UPI: ${restaurant.upiId}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("GPay / PhonePe / Paytm / BHIM", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (restaurant.wifiDetails.isNotBlank()) {
            Text("📶 Free Wi-Fi: ${restaurant.wifiDetails}", fontSize = 10.sp, color = Color(0xFF475569))
        }
        if (restaurant.socialHandle.isNotBlank()) {
            Text("📸 Tag us on Instagram: ${restaurant.socialHandle}", fontSize = 10.sp, color = Color(0xFF475569))
        }

        // 11. Footer Notes & Terms
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = restaurant.footerNote.ifBlank { "Thank you for visiting BBC Food Hub! Visit Again 😊" },
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )
        if (restaurant.customTermsNote.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = restaurant.customTermsNote,
                fontSize = 9.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// 1. MODERN BBC SIGNATURE
// ==========================================
@Composable
private fun ModernSignatureReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(4.dp)) {
            Text(
                text = "BILL RECEIPT",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(restaurant.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 20.sp, color = SolidBlackReceiptText)
        if (restaurant.address.isNotBlank()) Text(restaurant.address, fontSize = 11.sp, color = SolidBlackReceiptText, textAlign = TextAlign.Center)
        if (restaurant.phone.isNotBlank()) Text("Ph: ${restaurant.phone}", fontSize = 11.sp, color = SolidBlackReceiptText)
        
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFFCBD5E1))
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bill: #${bill.billNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SolidBlackReceiptText)
            Text(sdf.format(Date(bill.billTimestamp)), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SolidBlackReceiptText)
        }
        if (bill.customerName.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Customer: ${bill.customerName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                if (bill.customerPhone.isNotBlank()) Text(bill.customerPhone, fontSize = 12.sp, color = SolidBlackReceiptText)
            }
        }
        HorizontalDivider(color = Color(0xFFCBD5E1))
        Spacer(modifier = Modifier.height(8.dp))

        // Items Header
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text("ITEM", modifier = Modifier.weight(2f), fontWeight = FontWeight.Black, fontSize = 12.sp, color = SolidBlackReceiptText)
            Text("QTY", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center, color = SolidBlackReceiptText)
            Text("RATE", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.End, color = SolidBlackReceiptText)
            Text("TOTAL", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.End, color = SolidBlackReceiptText)
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF94A3B8))

        bill.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.dishName, modifier = Modifier.weight(2f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                Text("x${item.quantity}", modifier = Modifier.weight(0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = SolidBlackReceiptText)
                Text("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", modifier = Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End, color = SolidBlackReceiptText)
                Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", modifier = Modifier.weight(1.2f), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = SolidBlackReceiptText)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFFCBD5E1))

        // Subtotal & Grand Total
        BillFinancialBreakdownSection(bill)

        // DUES BOX
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFFD97706)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("💳 DUES & ACCOUNT STATUS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB45309))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Today's Bill:", fontSize = 12.sp, color = Color(0xFF92400E))
                        Text("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                    }
                    if (previousDue > 0.0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Previous Pending Due:", fontSize = 12.sp, color = Color(0xFF92400E))
                            Text("₹${String.format(Locale.US, "%.2f", previousDue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFD97706).copy(alpha = 0.5f))
                    val totalDue = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("NET OUTSTANDING PAYABLE:", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309))
                        Text("₹${String.format(Locale.US, "%.2f", totalDue)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309))
                    }
                }
            }
        }

        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        Spacer(modifier = Modifier.height(12.dp))
        if (restaurant.footerNote.isNotBlank()) {
            Text(restaurant.footerNote, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = SolidBlackReceiptText, textAlign = TextAlign.Center)
        }
    }
}

// ==========================================
// 2. RECEIPT BOX (KHATA FOCUS)
// ==========================================
@Composable
private fun ReceiptBoxDesign(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.5.dp, Color(0xFF0284C7)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(restaurant.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 20.sp, color = SolidBlackReceiptText)
                Text("ACCOUNT & BILLING VOUCHER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                Text("Bill #${bill.billNumber} • ${bill.customerName.ifBlank { "Customer" }}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Highlighted Dues Box at the top
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
            border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("⚡ KHATA / PENDING BALANCE LEDGER", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFB45309))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Today's Food Order:", fontSize = 12.sp, color = SolidBlackReceiptText)
                    Text("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Previous Pending Balance:", fontSize = 12.sp, color = SolidBlackReceiptText)
                    Text("₹${String.format(Locale.US, "%.2f", previousDue)}", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFF59E0B))
                val netBal = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL BALANCE OUTSTANDING:", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFFB45309))
                    Text("₹${String.format(Locale.US, "%.2f", netBal)}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFDC2626))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("ORDER BREAKDOWN:", fontWeight = FontWeight.Black, fontSize = 12.sp, color = SolidBlackReceiptText)
        bill.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.dishName} x${item.quantity}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFCBD5E1))
        BillFinancialBreakdownSection(bill)
        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFCBD5E1))
        Text("Status: ${bill.paymentMethod}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
    }
}

// ==========================================
// 3. CLASSIC THERMAL (80mm)
// ==========================================
@Composable
private fun ClassicThermalReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("*** ${restaurant.name.uppercase()} ***", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 16.sp, color = SolidBlackReceiptText)
        if (restaurant.phone.isNotBlank()) Text("TEL: ${restaurant.phone}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SolidBlackReceiptText)
        Text("--------------------------------", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SolidBlackReceiptText)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RCPT: #${bill.billNumber}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SolidBlackReceiptText)
            Text(sdf.format(Date(bill.billTimestamp)), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SolidBlackReceiptText)
        }
        if (bill.customerName.isNotBlank()) {
            Text("CUST: ${bill.customerName}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start), color = SolidBlackReceiptText)
        }
        Text("================================", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SolidBlackReceiptText)
        bill.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${item.dishName} x${item.quantity}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f), color = SolidBlackReceiptText)
                Text(String.format(Locale.US, "%.2f", item.totalPrice), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SolidBlackReceiptText)
            }
        }
        Text("--------------------------------", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SolidBlackReceiptText)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SUBTOTAL:", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SolidBlackReceiptText)
            Text(String.format(Locale.US, "%.2f", bill.subtotal), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SolidBlackReceiptText)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TOTAL:", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp, color = SolidBlackReceiptText)
            Text(String.format(Locale.US, "%.2f", bill.totalAmount), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp, color = SolidBlackReceiptText)
        }
        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            Text("--------------------------------", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SolidBlackReceiptText)
            Text("* PENDING BALANCE LEDGER *", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB45309))
            Text("PREV DUE: ₹${String.format(Locale.US, "%.2f", previousDue)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SolidBlackReceiptText)
            val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            Text("NET DUE : ₹${String.format(Locale.US, "%.2f", net)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFFDC2626))
        }
        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        Text("================================", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SolidBlackReceiptText)
        Text("THANK YOU! VISIT AGAIN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, color = SolidBlackReceiptText)
    }
}

// ==========================================
// 4. COMPACT MINI (58mm)
// ==========================================
@Composable
private fun CompactMiniReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(restaurant.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 16.sp, color = SolidBlackReceiptText)
        Text("#${bill.billNumber} • ${bill.customerName.ifBlank { "Walk-in" }}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color(0xFFCBD5E1))
        bill.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.dishName} x${item.quantity}", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = SolidBlackReceiptText)
                Text("₹${item.totalPrice.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color(0xFFCBD5E1))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BILL TOTAL:", fontWeight = FontWeight.Black, fontSize = 13.sp, color = SolidBlackReceiptText)
            Text("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = SolidBlackReceiptText)
        }
        if (previousDue > 0.0) {
            val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            Text("Prev Due: ₹${previousDue.toInt()} | Net: ₹${net.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
        }
        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        Text(bill.paymentMethod, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText, modifier = Modifier.padding(top = 4.dp))
    }
}

// ==========================================
// 5. ELEGANT DINE-IN
// ==========================================
@Composable
private fun ElegantDineInReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFB45309), RoundedCornerShape(8.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("☕ ${restaurant.name.uppercase()} ☕", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF78350F))
        Text("Bistro & Cafe Experience", fontStyle = FontStyle.Italic, fontSize = 11.sp, color = SolidBlackReceiptText)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Table: ${bill.tableName ?: "Counter"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            Text("Invoice: #${bill.billNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFD97706).copy(alpha = 0.5f))

        bill.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.dishName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                Text("x${item.quantity}  ₹${String.format(Locale.US, "%.2f", item.totalPrice)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFD97706).copy(alpha = 0.5f))

        BillFinancialBreakdownSection(bill)

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                    if (previousDue > 0.0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Previous Account Due:", fontSize = 11.sp, color = Color(0xFF92400E))
                            Text("₹${String.format(Locale.US, "%.2f", previousDue)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        }
                    }
                    val totalDue = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Outstanding Balance:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        Text("₹${String.format(Locale.US, "%.2f", totalDue)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF92400E))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        val statusText = if (bill.paymentMethod == "CREDIT") "PAYMENT DUE" else "PAID IN FULL"
        val statusColor = if (bill.paymentMethod == "CREDIT") Color(0xFFDC2626) else Color(0xFF16A34A)
        Surface(color = statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
            Text(
                statusText,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        Spacer(modifier = Modifier.height(8.dp))
        val footerNote = if (bill.orderType == "TAKEAWAY") {
            "Thank you for your order! Enjoy your delicious meal & see you soon. 🛍️"
        } else {
            "Thank you for dining with us! We look forward to welcoming you again. 🍽️"
        }
        Text(footerNote, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = SolidBlackReceiptText, textAlign = TextAlign.Center)
    }
}

// ==========================================
// 6. GST TAX INVOICE
// ==========================================
@Composable
private fun GstTaxInvoiceReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(restaurant.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = SolidBlackReceiptText)
                if (restaurant.gstNumber.isNotBlank()) Text("GSTIN: ${restaurant.gstNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                if (restaurant.fssaiNumber.isNotBlank()) Text("FSSAI: ${restaurant.fssaiNumber}", fontSize = 10.sp, color = SolidBlackReceiptText)
            }
            Surface(color = Color(0xFF047857), shape = RoundedCornerShape(4.dp)) {
                Text("TAX INVOICE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF94A3B8))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Inv No: #${bill.billNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            Text("Date: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(bill.billTimestamp))}", fontSize = 12.sp, color = SolidBlackReceiptText)
        }
        Text("Billed To: ${bill.customerName.ifBlank { "Cash Customer" }}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SolidBlackReceiptText)
        Spacer(modifier = Modifier.height(6.dp))

        // Grid Header
        Surface(color = Color(0xFFF1F5F9), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text("Description", modifier = Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                Text("Qty", modifier = Modifier.weight(0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = SolidBlackReceiptText)
                Text("Rate", modifier = Modifier.weight(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = SolidBlackReceiptText)
                Text("Taxable", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = SolidBlackReceiptText)
            }
        }
        bill.items.forEach { item ->
            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
                Text(item.dishName, modifier = Modifier.weight(2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                Text("${item.quantity}", modifier = Modifier.weight(0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = SolidBlackReceiptText)
                Text(String.format(Locale.US, "%.2f", item.unitPrice), modifier = Modifier.weight(0.8f), fontSize = 11.sp, textAlign = TextAlign.End, color = SolidBlackReceiptText)
                Text(String.format(Locale.US, "%.2f", item.totalPrice), modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = SolidBlackReceiptText)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFCBD5E1))

        // Tax details
        val halfGst = (bill.subtotal * (restaurant.gstRate / 200.0))
        SummaryRow("Taxable Value", "₹${String.format(Locale.US, "%.2f", bill.subtotal)}", isBold = true, color = SolidBlackReceiptText)
        SummaryRow("CGST (${restaurant.gstRate / 2.0}%)", "₹${String.format(Locale.US, "%.2f", halfGst)}", color = SolidBlackReceiptText)
        SummaryRow("SGST (${restaurant.gstRate / 2.0}%)", "₹${String.format(Locale.US, "%.2f", halfGst)}", color = SolidBlackReceiptText)
        SummaryRow("Grand Total", "₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", isBold = true, fontSize = 15, color = SolidBlackReceiptText)

        if (previousDue > 0.0) {
            SummaryRow("Previous Credit Due", "₹${String.format(Locale.US, "%.2f", previousDue)}", color = Color(0xFFDC2626))
            val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            SummaryRow("Net Receivable", "₹${String.format(Locale.US, "%.2f", net)}", isBold = true, color = Color(0xFFDC2626), fontSize = 14)
        }

        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)
    }
}

// ==========================================
// 7. RETRO FOODIE DINER
// ==========================================
@Composable
private fun RetroFoodieReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("★ ${restaurant.name.uppercase()} ★", fontWeight = FontWeight.Black, fontSize = 18.sp, color = SolidBlackReceiptText)
            Surface(color = Color.Black, shape = RoundedCornerShape(2.dp)) {
                Text("#${bill.billNumber}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Text("FRESH FOOD • FAST SERVICE", fontWeight = FontWeight.Black, fontSize = 11.sp, color = SolidBlackReceiptText)
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 2.dp, color = Color.Black)

        bill.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("• ${item.dishName} (${item.quantity})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SolidBlackReceiptText)
                Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SolidBlackReceiptText)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 1.5.dp, color = Color.Black)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CHECK TOTAL:", fontWeight = FontWeight.Black, fontSize = 15.sp, color = SolidBlackReceiptText)
            Text("₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", fontWeight = FontWeight.Black, fontSize = 17.sp, color = SolidBlackReceiptText)
        }
        if (previousDue > 0.0) {
            val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            Text("OLD TAB: ₹${previousDue.toInt()} | TOTAL TAB: ₹${net.toInt()}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 4.dp))
        }

        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)
    }
}

// ==========================================
// 8. MINIMAL CLEAN SLIP
// ==========================================
@Composable
private fun MinimalCleanReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(restaurant.name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = SolidBlackReceiptText)
        Text("Receipt #${bill.billNumber} • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(bill.billTimestamp))}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SolidBlackReceiptText)
        Spacer(modifier = Modifier.height(14.dp))

        bill.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.quantity}x  ${item.dishName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFCBD5E1))
        Spacer(modifier = Modifier.height(6.dp))

        BillFinancialBreakdownSection(bill)
        if (previousDue > 0.0) {
            val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            SummaryRow("Previous Balance", "₹${String.format(Locale.US, "%.2f", previousDue)}", color = Color(0xFFDC2626))
            SummaryRow("Outstanding Balance", "₹${String.format(Locale.US, "%.2f", net)}", isBold = true, color = Color(0xFFDC2626), fontSize = 14)
        }
        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        Spacer(modifier = Modifier.height(8.dp))
        Text("Thank you! Visit again.", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SolidBlackReceiptText)
    }
}

@Composable
private fun BillFinancialBreakdownSection(bill: BillEntity) {
    SummaryRow("Subtotal", "₹${String.format(Locale.US, "%.2f", bill.subtotal)}", isBold = true, fontSize = 13, color = SolidBlackReceiptText)
    if (bill.discountAmount > 0.0) {
        val discLabel = if (bill.discountType == "PERCENT") "Discount (${bill.discountValue.toInt()}%)" else "Discount"
        SummaryRow(discLabel, "-₹${String.format(Locale.US, "%.2f", bill.discountAmount)}", color = Color(0xFFDC2626))
    }
    if (bill.appliedRewardType == "VISIT_REWARD" || bill.appliedOfferName.isNotBlank()) {
        SummaryRow("🎉 Loyalty Pass (${bill.appliedOfferName.ifBlank { "Milestone Reward" }})", "APPLIED", color = Color(0xFF16A34A), isBold = true)
    }
    if (bill.pointsRedeemed > 0) {
        val ptsLabel = if (bill.giftPointsRedeemed > 0 && bill.rewardPointsRedeemed > 0) {
            "Reward (${bill.rewardPointsRedeemed}) & Gift (${bill.giftPointsRedeemed}) Pts Redeemed"
        } else if (bill.giftPointsRedeemed > 0) {
            "Gift Points Redeemed (${bill.giftPointsRedeemed} Pts)"
        } else {
            "Reward Points Redeemed (${bill.pointsRedeemed} Pts)"
        }
        SummaryRow(ptsLabel, "-₹${String.format(Locale.US, "%.2f", bill.pointsRedeemed.toDouble())}", color = Color(0xFFDC2626))
    }
    SummaryRow("Total Amount", "₹${String.format(Locale.US, "%.2f", bill.totalAmount)}", isBold = true, fontSize = 16, color = SolidBlackReceiptText)
    if (bill.paymentMethod == "SPLIT" || (bill.cashAmount > 0.0 && bill.upiAmount > 0.0)) {
        SummaryRow("Payment Breakdown", "Cash: ₹${String.format(Locale.US, "%.2f", bill.cashAmount)} | UPI: ₹${String.format(Locale.US, "%.2f", bill.upiAmount)}", fontSize = 11, color = SolidBlackReceiptText)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isBold: Boolean = false, fontSize: Int = 12, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = fontSize.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, color = color)
        Text(value, fontSize = fontSize.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
private fun LoyaltyPointsReceiptSection(bill: BillEntity, customer: com.example.data.local.entity.CustomerEntity?) {
    if (bill.pointsRedeemed > 0 || bill.rewardPointsEarned > 0 || customer != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = Color(0xFFF0FDF4), // soft green accent
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, Color(0xFF16A34A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "🎁 REWARD POINTS SUMMARY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF15803D)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (bill.pointsRedeemed > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Points Redeemed:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                        Text("${bill.pointsRedeemed} Pts (-₹${bill.pointsRedeemed}.00)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                    if (bill.rewardPointsRedeemed > 0 || bill.giftPointsRedeemed > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Text(
                                text = "  (Reward: ${bill.rewardPointsRedeemed} | Gift: ${bill.giftPointsRedeemed})",
                                fontSize = 10.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (bill.rewardPointsEarned > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Points Earned:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                        Text("+${bill.rewardPointsEarned} Pts", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }
                if (customer != null) {
                    val rewardBal = customer.rewardPointsBalance
                    val giftBal = customer.giftPointsBalance
                    val totalBal = rewardBal + giftBal
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("New Points Balance:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                        Text("$totalBal Pts", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text(
                            text = "  (Reward Balance: $rewardBal | Gift Balance: $giftBal)",
                            fontSize = 10.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. FAST FOOD EXPRESS (Bold Token & Quick Service)
// ==========================================
@Composable
private fun FastFoodExpressReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    Column(modifier = Modifier.fillMaxWidth()) {
        // Top Express Banner
        Surface(
            color = Color(0xFFEA580C), // Bright Orange
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                if (restaurant.phone.isNotBlank()) {
                    Text("Ph: ${restaurant.phone}", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Order Token & Type Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, Color(0xFFEA580C)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("TOKEN / ORDER #", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
                    Text("#${bill.billNumber}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFEA580C))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (bill.orderType == "DINE_IN") Color(0xFFEFF6FF) else Color(0xFFFEF2F2),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, if (bill.orderType == "DINE_IN") Color(0xFF3B82F6) else Color(0xFFEF4444)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("ORDER TYPE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (bill.orderType == "DINE_IN") Color(0xFF1D4ED8) else Color(0xFFB91C1C))
                    Text(if (bill.orderType == "DINE_IN") "DINE-IN (${bill.tableName ?: "Counter"})" else "TAKEAWAY / PARCEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (bill.orderType == "DINE_IN") Color(0xFF1D4ED8) else Color(0xFFB91C1C), maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Date: ${sdf.format(Date(bill.billTimestamp))}", fontSize = 11.sp, color = SolidBlackReceiptText)
            if (bill.customerName.isNotBlank()) {
                Text("Guest: ${bill.customerName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color(0xFFCBD5E1))

        Text("ITEMS ORDERED:", fontWeight = FontWeight.Black, fontSize = 12.sp, color = SolidBlackReceiptText)
        Spacer(modifier = Modifier.height(4.dp))

        bill.items.forEach { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            color = Color(0xFFFFEDD5),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "${item.quantity}x",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = Color(0xFFC2410C),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = item.dishName + if (item.isFree) " (FREE)" else "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SolidBlackReceiptText
                        )
                    }
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", item.totalPrice)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SolidBlackReceiptText
                    )
                }
                if (item.notes.isNotBlank()) {
                    Text(
                        text = "  ↳ Note: ${item.notes}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 1.dp, color = Color(0xFFCBD5E1))

        BillFinancialBreakdownSection(bill)

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFFD97706)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("💳 PENDING TAB / BALANCE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB45309))
                    val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL OUTSTANDING:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF92400E))
                        Text("₹${String.format(Locale.US, "%.2f", net)}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
                    }
                }
            }
        }

        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        Spacer(modifier = Modifier.height(8.dp))
        Text("⚡ Ready Fast • Freshly Made • Visit Again! 🍕", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEA580C), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ==========================================
// 10. ROYAL PREMIUM (Deep Navy & Gold Border)
// ==========================================
@Composable
private fun RoyalPremiumReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(10.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "👑 ${restaurant.name.ifBlank { "BBC FOOD HUB" }.uppercase()} 👑",
                    color = Color(0xFFFDE047),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "FINE DINING & PREMIUM EXPERIENCE",
                    color = Color(0xFFE2E8F0),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (restaurant.address.isNotBlank()) {
                    Text(restaurant.address, color = Color(0xFFCBD5E1), fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tax Invoice #${bill.billNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(sdf.format(Date(bill.billTimestamp)), fontSize = 11.sp, color = SolidBlackReceiptText)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val typeStr = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Takeaway Order"
            Text(typeStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF854D0E))
            if (bill.customerName.isNotBlank()) {
                Text("Guest: ${bill.customerName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 1.dp, color = Color(0xFFD4AF37))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("ITEM", modifier = Modifier.weight(2f), fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF0F172A))
            Text("QTY", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color(0xFF0F172A))
            Text("RATE", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.End, color = Color(0xFF0F172A))
            Text("AMOUNT", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.End, color = Color(0xFF0F172A))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color(0xFFD4AF37))

        bill.items.forEach { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.dishName + if (item.isFree) " (FREE)" else "", modifier = Modifier.weight(2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
                    Text("${item.quantity}", modifier = Modifier.weight(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = SolidBlackReceiptText)
                    Text("₹${String.format(Locale.US, "%.2f", item.unitPrice)}", modifier = Modifier.weight(0.9f), fontSize = 11.sp, textAlign = TextAlign.End, color = SolidBlackReceiptText)
                    Text("₹${String.format(Locale.US, "%.2f", item.totalPrice)}", modifier = Modifier.weight(1.1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = SolidBlackReceiptText)
                }
                if (item.notes.isNotBlank()) {
                    Text("↳ Notes: ${item.notes}", fontSize = 10.sp, fontStyle = FontStyle.Italic, color = Color(0xFFB45309))
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 1.dp, color = Color(0xFFD4AF37))

        BillFinancialBreakdownSection(bill)

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFD4AF37)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL OUTSTANDING:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        Text("₹${String.format(Locale.US, "%.2f", net)}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
                    }
                }
            }
        }

        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)

        Spacer(modifier = Modifier.height(8.dp))
        Text("✨ It was our pleasure serving you. Visit Again! ✨", fontSize = 10.5.sp, fontStyle = FontStyle.Italic, color = Color(0xFF854D0E), textAlign = TextAlign.Center)
    }
}

// ==========================================
// 11. KITCHEN DETAILED (Itemized & Cooking Notes)
// ==========================================
@Composable
private fun KitchenDetailedReceipt(bill: BillEntity, restaurant: RestaurantEntity, previousDue: Double) {
    val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault())
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(restaurant.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 16.sp, color = SolidBlackReceiptText)
                    Surface(color = Color(0xFF334155), shape = RoundedCornerShape(4.dp)) {
                        Text("ITEMIZED BILL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text("Bill #${bill.billNumber}  |  ${sdf.format(Date(bill.billTimestamp))}", fontSize = 11.sp, color = SolidBlackReceiptText)
                val tbl = if (bill.orderType == "DINE_IN") "Table: ${bill.tableName ?: "Counter"}" else "Type: TAKEAWAY / PARCEL"
                Text(tbl + if (bill.customerName.isNotBlank()) "  |  Customer: ${bill.customerName}" else "", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolidBlackReceiptText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("DETAILED ORDER BREAKDOWN:", fontWeight = FontWeight.Black, fontSize = 11.sp, color = SolidBlackReceiptText)
        Spacer(modifier = Modifier.height(4.dp))

        bill.items.forEachIndexed { index, item ->
            Surface(
                color = if (index % 2 == 0) Color(0xFFFAFAFA) else Color.White,
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${item.dishName}" + if (item.isFree) " (FREE)" else "", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SolidBlackReceiptText, modifier = Modifier.weight(1f))
                        Text("x${item.quantity}  =  ₹${String.format(Locale.US, "%.2f", item.totalPrice)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SolidBlackReceiptText)
                    }
                    if (item.notes.isNotBlank()) {
                        Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(3.dp), modifier = Modifier.padding(top = 3.dp)) {
                            Text("📝 Instruction: ${item.notes}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        BillFinancialBreakdownSection(bill)

        if (previousDue > 0.0 || bill.paymentMethod == "CREDIT") {
            Spacer(modifier = Modifier.height(6.dp))
            val net = if (bill.paymentMethod == "CREDIT") bill.totalAmount + previousDue else previousDue
            Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFF59E0B)), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Outstanding Balance:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB45309))
                    Text("₹${String.format(Locale.US, "%.2f", net)}", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFFDC2626))
                }
            }
        }

        val context = LocalContext.current
        val customerState by produceState<com.example.data.local.entity.CustomerEntity?>(initialValue = null, bill.customerPhone) {
            if (bill.customerPhone.isNotBlank()) {
                val db = com.example.data.local.database.CafePosDatabase.getDatabase(context)
                value = db.customerDao().getCustomerByContact(bill.customerPhone.trim())
            }
        }
        LoyaltyPointsReceiptSection(bill = bill, customer = customerState)
    }
}


