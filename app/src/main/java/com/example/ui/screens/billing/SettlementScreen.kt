package com.example.ui.screens.billing

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.OfferEntity
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.MainViewModel
import com.example.ui.components.BillPreviewDialog
import com.example.util.ReceiptBitmapGenerator
import com.example.ui.components.FreeItemSelectionDialog
import com.example.ui.components.LoyaltyRewardBanner
import com.example.ui.theme.*
import com.example.util.BillShareUtil
import com.example.util.PdfInvoiceGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.util.DateUtils
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSettled: (BillEntity) -> Unit
) {
    val context = LocalContext.current

    BackHandler(enabled = true) {
        onBack()
    }

    val cartState by viewModel.cartState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val allOffers by viewModel.allOffers.collectAsState()
    val allBills by viewModel.allBills.collectAsState()
    val allCustomerPayments by viewModel.allCustomerPayments.collectAsState()
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()
    val safeRestaurant = remember(activeRestaurant) {
        activeRestaurant ?: RestaurantEntity(id = "default", name = "Restaurant", address = "", phone = "")
    }

    var showLiveDraftPreview by remember { mutableStateOf(false) }
    var settledBillForDialog by remember { mutableStateOf<BillEntity?>(null) }

    // Customer info states
    var customerName by remember { mutableStateOf(cartState.customerName) }
    var customerPhone by remember { mutableStateOf(cartState.customerPhone) }

    // Payment method: "CASH", "UPI", "CREDIT", "SPLIT"
    var paymentMethod by remember { mutableStateOf(cartState.paymentMethod.ifBlank { "CASH" }) }

    // Split amounts
    var cashAmountInput by remember { mutableStateOf("") }
    var upiAmountInput by remember { mutableStateOf("") }

    // Discount state
    var discountType by remember { mutableStateOf(cartState.discountType) } // "NONE", "PERCENT", "FLAT"
    var discountValueInput by remember {
        mutableStateOf(if (cartState.discountValue > 0.0) cartState.discountValue.toString() else "")
    }

    // Modal to add items directly on this screen
    var showAddItemsSheet by remember { mutableStateOf(false) }

    // Free reward item dialog state
    var showFreeItemDialog by remember { mutableStateOf(false) }
    var activeRewardOffer by remember { mutableStateOf<OfferEntity?>(null) }

    // Points to redeem state & confirmation dialog
    var pointsToRedeemInput by remember { mutableStateOf("") }
    var pendingSettleAutoSendWhatsApp by remember { mutableStateOf<Boolean?>(null) }
    var showRedeemConfirmDialog by remember { mutableStateOf(false) }

    // Auto-fill customer name if phone matches known customer
    LaunchedEffect(customerPhone) {
        val cleanPhone = customerPhone.trim()
        if (cleanPhone.length >= 10) {
            val matched = allCustomers.find { it.contactNumber.trim() == cleanPhone }
            if (matched != null && customerName.isBlank()) {
                customerName = matched.name
            }
        }
    }

    // Continuously sync customer info back to cartState
    LaunchedEffect(customerName, customerPhone) {
        if (customerName != cartState.customerName || customerPhone != cartState.customerPhone) {
            viewModel.setCustomerInfo(customerName, customerPhone)
        }
    }

    // Continuously sync discount back to cartState
    LaunchedEffect(discountType, discountValueInput) {
        val dVal = discountValueInput.toDoubleOrNull() ?: 0.0
        if (discountType != cartState.discountType || dVal != cartState.discountValue) {
            viewModel.setDiscount(discountType, dVal)
        }
    }

    // Continuously sync payment method back to cartState
    LaunchedEffect(paymentMethod) {
        if (paymentMethod != cartState.paymentMethod && (paymentMethod == "CASH" || paymentMethod == "UPI")) {
            viewModel.setPaymentMethod(paymentMethod)
        }
    }

    // Matched Customer & Loyalty status
    val matchedCustomer = remember(customerPhone, customerName, allCustomers) {
        val cleanPhone = customerPhone.trim()
        val cleanName = customerName.trim()
        if (cleanPhone.length >= 10) {
            allCustomers.find { it.contactNumber.trim() == cleanPhone }
        } else if (cleanName.isNotBlank()) {
            allCustomers.find { it.name.trim().equals(cleanName, ignoreCase = true) }
        } else null
    }

    val nextVisitNumber = (matchedCustomer?.loyaltyVisitCount ?: 0) + 1

    // Fetch customer points summary
    val customerPointsSummary by produceState<com.example.data.repository.PosRepository.CustomerPointsSummary?>(initialValue = null, matchedCustomer, customerPhone, customerName) {
        value = matchedCustomer?.let { viewModel.getCustomerPointsSummary(it.id) }
    }
    val totalUsablePoints = customerPointsSummary?.totalUsablePoints ?: 0

    // Applicable Visit Offer matching next visit number (only for enrolled customers and only if unclaimed for this visit)
    var appliedVisitRewardOfferId by remember { mutableStateOf<String?>(null) }
    var appliedManualOffer by remember { mutableStateOf<OfferEntity?>(null) }
    var showPromoOffersSheet by remember { mutableStateOf(false) }
    var promoDialogTab by remember { mutableStateOf(0) }

    val activeManualOffers = remember(allOffers) {
        allOffers.filter { it.isActive && it.offerType == "MANUAL" }
    }

    val applicableVisitOffer = remember(allOffers, nextVisitNumber, matchedCustomer, allBills) {
        if (matchedCustomer != null && matchedCustomer.contactNumber.isNotBlank() && matchedCustomer.isEnrolledInLoyalty) {
            val phone = matchedCustomer.contactNumber.trim()
            val history = viewModel.repository.parseLoyaltyHistory(matchedCustomer.loyaltyHistoryJson)
            val activeVisitOffers = allOffers.filter { it.isActive && it.offerType == "VISIT_BASED" }
            val maxVisitNumber = activeVisitOffers.maxOfOrNull { it.visitNumber } ?: 0
            if (maxVisitNumber > 0 && nextVisitNumber > maxVisitNumber) {
                null
            } else {
                val targetOffer = allOffers.find { it.isActive && it.offerType == "VISIT_BASED" && it.visitNumber == nextVisitNumber }
                if (targetOffer == null) {
                    null
                } else {
                    // Check if this specific visit reward was already claimed in history or recorded bills
                    val alreadyClaimedInHistory = history.any { it.visitNumber == nextVisitNumber && !it.rewardGiven.isNullOrBlank() }
                    val alreadyClaimedInBills = allBills.any { b ->
                        b.customerPhone.trim() == phone &&
                        !b.isVoided &&
                        b.appliedRewardType == "VISIT_REWARD" &&
                        (b.appliedOfferId == targetOffer.id || b.appliedOfferName.equals(targetOffer.name, ignoreCase = true))
                    }
                    if (alreadyClaimedInHistory || alreadyClaimedInBills) {
                        null
                    } else {
                        targetOffer
                    }
                }
            }
        } else {
            null
        }
    }

    // Check if free item is currently in cart or if a discount reward is applied
    val claimedFreeItem = cartState.items.find { it.isFree }
    val isRewardClaimed = claimedFreeItem != null || (applicableVisitOffer != null && appliedVisitRewardOfferId == applicableVisitOffer.id)

    // Previous Khata / Credit balance lookup
    val previousKhataBalance = remember(customerName, customerPhone, allCustomers, allBills, allCustomerPayments) {
        viewModel.getCustomerOutstandingCredit(customerName, customerPhone)
    }

    // Live Discount Calculation based on eligible items (Strict Single Offer Policy)
    val effectiveDiscountType = if (claimedFreeItem != null) "NONE" else discountType
    val effectiveDiscountVal = if (claimedFreeItem != null) 0.0 else (discountValueInput.toDoubleOrNull() ?: 0.0)
    val discountBreakdown = remember(cartState.items, effectiveDiscountType, effectiveDiscountVal, categories, menuItems) {
        viewModel.calculateDiscountBreakdown(cartState.items, effectiveDiscountType, effectiveDiscountVal)
    }

    val subtotal = discountBreakdown.subtotal
    val discountAmount = discountBreakdown.discountAmount
    val eligibleSubtotal = discountBreakdown.eligibleSubtotal

    // Cooldown and past claim analysis for promotional offers
    val promoOfferStatuses = remember(activeManualOffers, allBills, customerPhone, subtotal) {
        val phone = customerPhone.trim()
        activeManualOffers.map { offer ->
            val lastClaimedBill = if (phone.isNotBlank()) {
                allBills.filter { b ->
                    b.customerPhone.trim() == phone &&
                    !b.isVoided &&
                    !b.isCancelled &&
                    (b.appliedOfferId == offer.id || b.appliedOfferName.equals(offer.name, ignoreCase = true))
                }.maxByOrNull { it.createdAt }
            } else null

            val isPhoneRequired = offer.cooldownDays != 0 && phone.isBlank()
            var isCooldownLocked = false
            var daysRemaining = 0
            var elapsedDays = 0
            var unlockDate = 0L

            if (lastClaimedBill != null) {
                if (offer.cooldownDays == -1) {
                    isCooldownLocked = true
                } else if (offer.cooldownDays > 0) {
                    val elapsedMillis = System.currentTimeMillis() - lastClaimedBill.createdAt
                    elapsedDays = (elapsedMillis / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                    if (elapsedDays < offer.cooldownDays) {
                        isCooldownLocked = true
                        daysRemaining = offer.cooldownDays - elapsedDays
                        unlockDate = lastClaimedBill.createdAt + (offer.cooldownDays.toLong() * 24 * 60 * 60 * 1000L)
                    }
                }
            }

            val isMinBillShort = subtotal < offer.minBillAmount
            val shortfall = (offer.minBillAmount - subtotal).coerceAtLeast(0.0)

            PromoOfferCooldownStatus(
                offer = offer,
                lastClaimedBill = lastClaimedBill,
                isCooldownLocked = isCooldownLocked,
                isPhoneRequired = isPhoneRequired,
                isMinBillShort = isMinBillShort,
                shortfall = shortfall,
                elapsedDays = elapsedDays,
                daysRemaining = daysRemaining,
                unlockDate = unlockDate
            )
        }
    }

    val availablePromoOffers = remember(promoOfferStatuses) {
        promoOfferStatuses.filter { !it.isCooldownLocked && !it.isPhoneRequired }
    }
    val claimedCooldownPromoOffers = remember(promoOfferStatuses) {
        promoOfferStatuses.filter { it.isCooldownLocked || it.isPhoneRequired }
    }

    // Points calculation
    val pointsRules by viewModel.pointsEngineRules.collectAsState()
    var newCustomerEnrollInPass by remember(customerPhone, pointsRules.autoEnrollInVisitPass) {
        mutableStateOf(pointsRules.autoEnrollInVisitPass)
    }

    val requestedPoints = pointsToRedeemInput.toIntOrNull() ?: 0
    val maxDiscountAllowedByBill = (discountBreakdown.totalAmount * (pointsRules.maxDiscountCapPercent / 100.0) / pointsRules.pointValueRupees).toInt()
    val maxRedeemablePoints = if (totalUsablePoints >= pointsRules.minRedemptionPoints) {
        minOf(totalUsablePoints, maxDiscountAllowedByBill, (discountBreakdown.totalAmount / pointsRules.pointValueRupees).toInt()).coerceAtLeast(0)
    } else 0
    val pointsRedeemed = requestedPoints.coerceIn(0, maxRedeemablePoints)
    val pointsDiscountAmount = pointsRedeemed * pointsRules.pointValueRupees

    // Final total payable after discount and points redemption
    val finalTotal = (discountBreakdown.totalAmount - pointsDiscountAmount).coerceAtLeast(0.0)

    // Free Item Value Savings
    val freeItemSavings = remember(cartState.items, menuItems) {
        val itemMap = menuItems.associateBy { it.id }
        cartState.items.filter { it.isFree }.sumOf { freeItem ->
            val originalPrice = itemMap[freeItem.dishId]?.price ?: 0.0
            originalPrice * freeItem.quantity
        }
    }
    val totalOverallSavings = discountAmount + freeItemSavings + pointsRedeemed

    // Split payment amounts parsed
    val cashPart = cashAmountInput.toDoubleOrNull() ?: 0.0
    val upiPart = upiAmountInput.toDoubleOrNull() ?: 0.0
    val splitSum = cashPart + upiPart
    val splitDiff = finalTotal - splitSum
    val isSplitExactMatch = abs(splitDiff) < 0.01

    // Validation
    val isCredit = paymentMethod == "CREDIT"
    val isSplit = paymentMethod == "SPLIT"

    val isCreditValid = !isCredit || (customerName.trim().isNotBlank() && customerPhone.trim().isNotBlank())
    val isSplitValid = !isSplit || isSplitExactMatch
    val isCartValid = cartState.items.isNotEmpty()

    val canSettle = isCartValid && isCreditValid && isSplitValid

    val executeSettleBill: (Boolean) -> Unit = { autoSendWhatsApp ->
        val isDiscountVisitReward = appliedVisitRewardOfferId != null && applicableVisitOffer != null && (appliedVisitRewardOfferId == applicableVisitOffer.id)
        val isManualPromoReward = appliedManualOffer != null
        val rewardType = if (isManualPromoReward) {
            "MANUAL_OFFER"
        } else if (claimedFreeItem != null) {
            if (applicableVisitOffer != null) "VISIT_REWARD" else "MANUAL_OFFER"
        } else if (isDiscountVisitReward) {
            "VISIT_REWARD"
        } else if (pointsRedeemed > 0) {
            "POINTS_REDEMPTION"
        } else if (effectiveDiscountVal > 0) {
            "DISCOUNT"
        } else {
            "NONE"
        }

        val finalOfferId = when {
            appliedManualOffer != null -> appliedManualOffer!!.id
            claimedFreeItem != null || isDiscountVisitReward -> applicableVisitOffer?.id ?: cartState.appliedOfferId
            else -> cartState.appliedOfferId
        }
        val finalOfferName = when {
            appliedManualOffer != null -> appliedManualOffer!!.name
            claimedFreeItem != null || isDiscountVisitReward -> applicableVisitOffer?.name ?: cartState.appliedOfferName
            else -> cartState.appliedOfferName
        }

        val finalEnrollInPass = if (matchedCustomer != null) matchedCustomer.isEnrolledInLoyalty else newCustomerEnrollInPass

        viewModel.settleBill(
            paymentMethod = paymentMethod,
            discountType = effectiveDiscountType,
            discountValue = effectiveDiscountVal,
            customerName = customerName,
            customerPhone = customerPhone,
            cashAmount = if (isSplit) cashPart else if (paymentMethod == "CASH") finalTotal else 0.0,
            upiAmount = if (isSplit) upiPart else if (paymentMethod == "UPI") finalTotal else 0.0,
            appliedRewardType = rewardType,
            appliedOfferId = finalOfferId,
            appliedOfferName = finalOfferName,
            pointsToRedeem = pointsRedeemed,
            enrollInPass = finalEnrollInPass,
            onSuccess = { settled ->
                val prevDue = previousKhataBalance
                val currentRest = activeRestaurant ?: safeRestaurant
                if (autoSendWhatsApp) {
                    try {
                        val img = ReceiptBitmapGenerator.generateReceiptImageFile(context, settled, currentRest, prevDue)
                        BillShareUtil.sendViaWhatsAppImage(context, settled, currentRest, img, prevDue)
                    } catch (e: Exception) {
                        try {
                            val pdf = PdfInvoiceGenerator.generateA4Pdf(context, settled, currentRest, prevDue)
                            BillShareUtil.sendViaWhatsAppPdf(context, settled, currentRest, pdf, prevDue)
                        } catch (ex: Exception) {
                            BillShareUtil.sendViaWhatsApp(context, settled, currentRest, prevDue)
                        }
                    }
                }
                settledBillForDialog = settled
            }
        )
    }

    val settleOrderAction: (Boolean) -> Unit = { autoSendWhatsApp ->
        val cleanPhone = customerPhone.trim().filter { it.isDigit() }
        if (autoSendWhatsApp && cleanPhone.length < 10) {
            Toast.makeText(context, "⚠️ Please enter a valid 10-digit mobile number for WhatsApp bill!", Toast.LENGTH_SHORT).show()
        } else if (pointsRedeemed >= 50) {
            // Requirement 9: Show confirmation for large point redemption
            pendingSettleAutoSendWhatsApp = autoSendWhatsApp
            showRedeemConfirmDialog = true
        } else {
            executeSettleBill(autoSendWhatsApp)
        }
    }

    // Requirement 9: Large Point Redemption Confirmation Dialog
    if (showRedeemConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRedeemConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = GoldAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Point Redemption")
                }
            },
            text = {
                Text(
                    text = "Redeem $pointsRedeemed Reward Points (-₹$pointsRedeemed.00) for ${customerName.ifBlank { "this bill" }}?\n\nFinal Payable: ₹${String.format(Locale.US, "%.2f", finalTotal)}",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRedeemConfirmDialog = false
                        val autoSend = pendingSettleAutoSendWhatsApp ?: false
                        executeSettleBill(autoSend)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Confirm & Settle", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRedeemConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Order Settlement",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = when (cartState.orderType) {
                                "DINE_IN" -> "Dine In • ${cartState.selectedTable?.name ?: "Table"}"
                                else -> "Takeaway Order"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { showLiveDraftPreview = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bill Design", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { showAddItemsSheet = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Items", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Split validation alert in bottom bar if split selected and mismatch
                    if (isSplit && !isSplitExactMatch) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (splitDiff > 0) {
                                        "Split remaining: ₹${String.format(Locale.US, "%.2f", splitDiff)}"
                                    } else {
                                        "Split exceeds total by ₹${String.format(Locale.US, "%.2f", abs(splitDiff))}"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (isCredit && !isCreditValid) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Name and Phone required for Credit settlement",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.85f)) {
                            Text(
                                "Total Payable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "₹${String.format(Locale.US, "%.2f", finalTotal)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = CaramelWarm
                            )
                        }

                        Row(
                            modifier = Modifier.weight(1.85f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ⚡ Settle & WhatsApp (PDF + Text Caption)
                            Button(
                                onClick = {
                                    if (!canSettle) return@Button
                                    settleOrderAction(true)
                                },
                                enabled = canSettle,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1.2f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("⚡ Settle & WhatsApp", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                            }

                            // Regular Settle
                            Button(
                                onClick = {
                                    if (!canSettle) return@Button
                                    settleOrderAction(false)
                                },
                                enabled = canSettle,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (paymentMethod == "CREDIT") AmberGold else SuccessGreen
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(0.85f).height(48.dp)
                            ) {
                                Text(
                                    text = if (paymentMethod == "CREDIT") "Credit" else "Settle",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 1: TOP - Loyalty Visit Reward Banner (Only if enrolled and active offer present)
            if (matchedCustomer != null && matchedCustomer.isEnrolledInLoyalty && applicableVisitOffer != null) {
                item {
                    LoyaltyRewardBanner(
                        offer = applicableVisitOffer,
                        visitNumber = nextVisitNumber,
                        subtotal = subtotal,
                        isRewardClaimed = isRewardClaimed,
                        claimedRewardItemName = claimedFreeItem?.dishName,
                        onClaimClick = {
                            // Single-offer rule: clear promotional offer if applied
                            if (appliedManualOffer != null) {
                                appliedManualOffer = null
                            }
                            when (applicableVisitOffer.rewardType) {
                                "FLAT_DISCOUNT" -> {
                                    discountType = "FLAT"
                                    discountValueInput = applicableVisitOffer.rewardValue.toInt().toString()
                                    appliedVisitRewardOfferId = applicableVisitOffer.id
                                }
                                "PERCENT_DISCOUNT" -> {
                                    discountType = "PERCENT"
                                    discountValueInput = applicableVisitOffer.rewardValue.toInt().toString()
                                    appliedVisitRewardOfferId = applicableVisitOffer.id
                                }
                                else -> {
                                    activeRewardOffer = applicableVisitOffer
                                    showFreeItemDialog = true
                                }
                            }
                        },
                        onRemoveRewardClick = {
                            if (claimedFreeItem != null) {
                                viewModel.removeFreeItemReward()
                            }
                            if (appliedVisitRewardOfferId != null) {
                                discountType = "NONE"
                                discountValueInput = ""
                                appliedVisitRewardOfferId = null
                            }
                        }
                    )
                }
            }

            // SECTION 2: Customer Details & Lookup
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Customer & Reward Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            if (isCredit) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "Required for Credit",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text("(Optional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("Phone Number" + if (isCredit) " *" else "") },
                                placeholder = { Text("10-digit number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Customer Name" + if (isCredit) " *" else "") },
                                placeholder = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Requirement 7: Partial digits autocomplete for phone number
                        val matchingSuggestions = remember(customerPhone, customerName, allCustomers) {
                            val cleanP = customerPhone.trim().filter { it.isDigit() }
                            val cleanN = customerName.trim()
                            if (cleanP.isNotEmpty() || (cleanN.length >= 2 && matchedCustomer == null)) {
                                allCustomers.filter {
                                    val custPhoneDigits = it.contactNumber.filter { c -> c.isDigit() }
                                    (cleanP.isNotEmpty() && (custPhoneDigits.startsWith(cleanP) || custPhoneDigits.contains(cleanP))) ||
                                    (cleanN.length >= 2 && it.name.contains(cleanN, ignoreCase = true))
                                }.take(4)
                            } else emptyList()
                        }

                        if (matchingSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Matching Saved Customers:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(matchingSuggestions) { cust ->
                                    SuggestionChip(
                                        onClick = {
                                            customerPhone = cust.contactNumber
                                            customerName = cust.name
                                        },
                                        label = {
                                            Text(
                                                "${cust.name} (${cust.contactNumber})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        icon = {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }
                        }

                        // Matched Customer Badge, Pass Enrollment & Reward points summary
                        if (matchedCustomer != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldAccent.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Verified, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (matchedCustomer.isEnrolledInLoyalty) "Loyalty: Visit #$nextVisitNumber" else "Registered Customer",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Stars, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                                            Text(
                                                "$totalUsablePoints Reward Pts",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = GoldTextDark
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (matchedCustomer.isEnrolledInLoyalty) "🎫 Reward Pass: Enrolled" else "🎫 Reward Pass: Not Enrolled",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (matchedCustomer.isEnrolledInLoyalty) SuccessGreen else EspressoDark
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (matchedCustomer.isEnrolledInLoyalty) "Pass Active" else "Enroll in Pass",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (matchedCustomer.isEnrolledInLoyalty) SuccessGreen else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Switch(
                                                checked = matchedCustomer.isEnrolledInLoyalty,
                                                onCheckedChange = { isEnrolled ->
                                                    viewModel.toggleCustomerLoyaltyEnrollment(matchedCustomer.id, isEnrolled)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = GoldAccent,
                                                    checkedTrackColor = GoldAccent.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (customerPhone.trim().isNotBlank() || customerName.trim().isNotBlank()) {
                            // New Customer Banner & Visit Pass Toggle (Default: pointsRules.autoEnrollInVisitPass)
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "✨ New Customer Registration",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (pointsRules.welcomeBonusEnabled && pointsRules.welcomeBonusPoints > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFFFEF3C7)
                                            ) {
                                                Text(
                                                    "+${pointsRules.welcomeBonusPoints} Gift Pts",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF92400E),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (pointsRules.welcomeBonusEnabled && pointsRules.welcomeBonusPoints > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "🎁 Welcome Bonus: +${pointsRules.welcomeBonusPoints} Gift Points (₹${String.format(Locale.US, "%.2f", pointsRules.welcomeBonusPoints * pointsRules.pointValueRupees)} value, ${pointsRules.welcomeBonusExpiryDays}d validity) will be credited for next visit!",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFB45309)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (newCustomerEnrollInPass) "🎫 Reward Pass: Enrolled" else "🎫 Reward Pass: Not Enrolled",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (newCustomerEnrollInPass) SuccessGreen else EspressoDark
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (newCustomerEnrollInPass) "Pass Active" else "Enroll in Pass",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (newCustomerEnrollInPass) SuccessGreen else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Switch(
                                                checked = newCustomerEnrollInPass,
                                                onCheckedChange = { isEnrolled ->
                                                    newCustomerEnrollInPass = isEnrolled
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = GoldAccent,
                                                    checkedTrackColor = GoldAccent.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // AUTO SMART ALERT: Previous Pending Due & Combined Outstanding
                        if (previousKhataBalance > 0.0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.5.dp, Color(0xFFD97706)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "⚠️ PENDING DUE ALERT",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF92400E)
                                            )
                                        }
                                        Text(
                                            "Old Due: ₹${String.format(Locale.US, "%.2f", previousKhataBalance)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = Color(0xFFD97706).copy(alpha = 0.4f))
                                    val netAfterOrder = previousKhataBalance + (if (paymentMethod == "CREDIT") finalTotal else 0.0)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (paymentMethod == "CREDIT") "Net Balance with this Credit Order:" else "Remaining Pending Due:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF78350F)
                                        )
                                        Text(
                                            text = "₹${String.format(Locale.US, "%.2f", netAfterOrder)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Order Items (Editable directly on this screen)
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Order Items (${cartState.items.sumOf { it.quantity }})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            TextButton(
                                onClick = { showAddItemsSheet = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Items", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (cartState.items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No items in order", color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(onClick = { showAddItemsSheet = true }) {
                                        Text("Add Items From Menu")
                                    }
                                }
                            }
                        } else {
                            val catMap = remember(categories) { categories.associateBy { it.id } }
                            val itemMap = remember(menuItems) { menuItems.associateBy { it.id } }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                cartState.items.forEach { item ->
                                    val mi = itemMap[item.dishId]
                                    val itemEligible = mi?.isDiscountEligible ?: true
                                    val cat = mi?.categoryId?.let { catMap[it] }
                                    val catEligible = cat?.isDiscountEligible ?: true
                                    val isEligible = itemEligible && catEligible

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.getFormattedDisplayName(),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                                if (!isEligible) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(3.dp),
                                                        color = Color(0xFFEEEEEE)
                                                    ) {
                                                        Text(
                                                            text = "No Disc",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF757575),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            val selectedAddons = item.getSelectedAddonsList()
                                            val addonsTotal = selectedAddons.sumOf { it.totalPrice }
                                            val baseUnitPrice = (item.unitPrice - addonsTotal).coerceAtLeast(0.0)

                                            Text(
                                                text = "₹${String.format(Locale.US, "%.2f", baseUnitPrice)} each",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Show selected add-ons breakdown as sub-rows with bold "Add-on:" header
                                            if (selectedAddons.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Add-on:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CaramelWarm,
                                                    modifier = Modifier.padding(start = 6.dp)
                                                )
                                                selectedAddons.forEach { addon ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 12.dp, top = 1.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "• ${addon.name} (${addon.quantity}x ₹${String.format(Locale.US, "%.0f", addon.unitPrice)})",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "+₹${String.format(Locale.US, "%.2f", addon.totalPrice)}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = CaramelWarm
                                                        )
                                                    }
                                                }
                                            }

                                            if (item.cookingNotes.isNotBlank()) {
                                                Text(
                                                    text = "Note: ${item.cookingNotes}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                                                )
                                            }
                                        }

                                        // Quantity controls: - / + / remove
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            FilledTonalIconButton(
                                                onClick = { viewModel.decreaseCartItem(item.dishId) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (item.quantity == 1) Icons.Default.DeleteOutline else Icons.Default.Remove,
                                                    contentDescription = "Decrease",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = if (item.quantity == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Text(
                                                text = "${item.quantity}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )

                                            FilledTonalIconButton(
                                                onClick = { viewModel.increaseCartItem(item.dishId) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Increase",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Text(
                                            text = "₹${String.format(Locale.US, "%.2f", item.totalPrice)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = CaramelWarm
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2B: Promotional Offers Card
            item {
                val isDark = isSystemInDarkTheme()
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (appliedManualOffer != null) {
                            if (isDark) Color(0xFF0F2E1B) else Color(0xFFE8F5E9)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    ),
                    border = if (appliedManualOffer != null) {
                        BorderStroke(1.dp, if (isDark) Color(0xFF10B981) else Color(0xFF4CAF50))
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (appliedManualOffer != null) Icons.Default.Celebration else Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = if (appliedManualOffer != null) {
                                        if (isDark) Color(0xFF6EE7B7) else Color(0xFF2E7D32)
                                    } else CaramelWarm,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (appliedManualOffer != null) "Promotional Offer Applied" else "Promotional Offers",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (appliedManualOffer != null) {
                                            if (isDark) Color(0xFF6EE7B7) else Color(0xFF1B5E20)
                                        } else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (appliedManualOffer == null) {
                                        Text(
                                            text = if (availablePromoOffers.isNotEmpty()) {
                                                "${availablePromoOffers.size} promo offer(s) available"
                                            } else if (activeManualOffers.isNotEmpty()) {
                                                "Offers currently in cooldown / phone required"
                                            } else {
                                                "No active promo offers"
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (appliedManualOffer != null) {
                                TextButton(
                                    onClick = {
                                        appliedManualOffer = null
                                        discountType = "NONE"
                                        discountValueInput = ""
                                        if (claimedFreeItem != null && cartState.appliedRewardType == "MANUAL_OFFER") {
                                            viewModel.removeFreeItemReward()
                                        }
                                    }
                                ) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = { showPromoOffersSheet = true },
                                    enabled = activeManualOffers.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Apply Offer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (appliedManualOffer != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF143823) else Color.White.copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF81C784).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appliedManualOffer!!.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF1B5E20)
                                        )
                                        Text(
                                            text = when (appliedManualOffer!!.rewardType) {
                                                "PERCENT_DISCOUNT" -> "${appliedManualOffer!!.rewardValue.toInt()}% discount applied"
                                                "FLAT_DISCOUNT" -> "Flat ₹${appliedManualOffer!!.rewardValue.toInt()} discount applied"
                                                else -> "Free item unlocked"
                                            },
                                            fontSize = 11.sp,
                                            color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF388E3C)
                                        )
                                    }
                                    Text(
                                        text = "-₹${String.format(Locale.US, "%.2f", discountAmount)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF1B5E20)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Discount Field & Rules
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Discount, contentDescription = null, tint = CaramelWarm, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discount", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = discountType == "NONE",
                                    onClick = {
                                        discountType = "NONE"
                                        discountValueInput = ""
                                        appliedManualOffer = null
                                        appliedVisitRewardOfferId = null
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                                ) {
                                    Text("None", fontSize = 11.sp)
                                }
                                SegmentedButton(
                                    selected = discountType == "PERCENT" && claimedFreeItem == null,
                                    onClick = {
                                        if (claimedFreeItem != null) {
                                            viewModel.removeFreeItemReward()
                                            appliedVisitRewardOfferId = null
                                            Toast.makeText(context, "Free reward removed to apply discount (1 offer rule)", Toast.LENGTH_SHORT).show()
                                        }
                                        discountType = "PERCENT"
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                                ) {
                                    Text("%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                SegmentedButton(
                                    selected = discountType == "FLAT" && claimedFreeItem == null,
                                    onClick = {
                                        if (claimedFreeItem != null) {
                                            viewModel.removeFreeItemReward()
                                            appliedVisitRewardOfferId = null
                                            Toast.makeText(context, "Free reward removed to apply discount (1 offer rule)", Toast.LENGTH_SHORT).show()
                                        }
                                        discountType = "FLAT"
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                                ) {
                                    Text("₹ Flat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (claimedFreeItem != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🔒 Free reward active (${claimedFreeItem.dishName}). Selecting a discount will replace the free reward (Strict 1 Offer Rule).",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        if (discountType != "NONE") {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = discountValueInput,
                                onValueChange = {
                                    if (claimedFreeItem != null && it.isNotBlank()) {
                                        viewModel.removeFreeItemReward()
                                        appliedVisitRewardOfferId = null
                                    }
                                    discountValueInput = it
                                },
                                label = {
                                    Text(if (discountType == "PERCENT") "Discount Percentage (%)" else "Discount Amount (₹)")
                                },
                                placeholder = { Text(if (discountType == "PERCENT") "e.g. 10" else "e.g. 50") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Show info note if there are ineligible items
                            if (discountBreakdown.hasIneligibleItems) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Calculated on eligible items subtotal (₹${String.format(Locale.US, "%.2f", eligibleSubtotal)})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: Reward Points Redemption Card (Appears directly after Discount, near final total)
            if (totalUsablePoints > 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = GoldAccent.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Stars, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Redeem Reward Points", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoldAccent.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "1 Pt = ₹${String.format(Locale.US, "%.2f", pointsRules.pointValueRupees)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldTextDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Available Balance: $totalUsablePoints points (Reward: ${customerPointsSummary?.usableRewardPoints ?: 0} + Gift: ${customerPointsSummary?.usableGiftPoints ?: 0})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (totalUsablePoints < pointsRules.minRedemptionPoints) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "⚠️ Min ${pointsRules.minRedemptionPoints} points required to redeem (Available: $totalUsablePoints pts)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepAmber
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pointsToRedeemInput,
                                    onValueChange = { pointsToRedeemInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("Points to Redeem") },
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = {
                                        pointsToRedeemInput = maxRedeemablePoints.toString()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Max Pts", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                if (pointsRedeemed > 0) {
                                    OutlinedButton(
                                        onClick = { pointsToRedeemInput = "" },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Clear", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Quick Preset Chips for Easy Tapping
                            if (totalUsablePoints >= pointsRules.minRedemptionPoints) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(25, 50, 100, 200).filter { it <= maxRedeemablePoints && it >= pointsRules.minRedemptionPoints }.forEach { preset ->
                                        SuggestionChip(
                                            onClick = {
                                                pointsToRedeemInput = preset.toString()
                                            },
                                            label = { Text("$preset Pts", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                }
                            }

                            if (pointsRedeemed > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Applying ₹${String.format(Locale.US, "%.2f", pointsDiscountAmount)} discount from $pointsRedeemed points",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Bill Summary Breakdown Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Bill Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Bill summary breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format(Locale.US, "%.2f", subtotal)}", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (discountAmount > 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (effectiveDiscountType == "PERCENT") "Discount (${effectiveDiscountVal.toInt()}%)" else "Discount (Flat)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    "-₹${String.format(Locale.US, "%.2f", discountAmount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (pointsRedeemed > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Points Redeemed ($pointsRedeemed pts)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GoldTextDark
                                )
                                Text(
                                    "-₹${String.format(Locale.US, "%.2f", pointsRedeemed.toDouble())}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GoldTextDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (freeItemSavings > 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🎁 Free Reward Item",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GoldTextDark
                                )
                                Text(
                                    "Saved ₹${String.format(Locale.US, "%.2f", freeItemSavings)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GoldTextDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Net Total Payable", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "₹${String.format(Locale.US, "%.2f", finalTotal)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = CaramelWarm
                            )
                        }

                        if (totalOverallSavings > 0.0) {
                            val isDark = isSystemInDarkTheme()
                            val freeClaimed = cartState.items.find { it.isFree }
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF0F2E1B) else SuccessGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else SuccessGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Savings, contentDescription = null, tint = if (isDark) Color(0xFF6EE7B7) else SuccessGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "🎉 Total Customer Savings Today: ₹${String.format(Locale.US, "%.2f", totalOverallSavings)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)
                                        )
                                    }
                                    if (freeItemSavings > 0.0 || discountAmount > 0.0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val parts = mutableListOf<String>()
                                        if (discountAmount > 0.0) parts.add("₹${String.format(Locale.US, "%.0f", discountAmount)} Discount")
                                        if (freeItemSavings > 0.0) parts.add("₹${String.format(Locale.US, "%.0f", freeItemSavings)} Free Item (${freeClaimed?.dishName?.substringBefore(" (") ?: "Free Item"})")
                                        if (pointsDiscountAmount > 0.0) parts.add("₹${String.format(Locale.US, "%.0f", pointsDiscountAmount)} Points")
                                        Text(
                                            text = parts.joinToString(" + "),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF166534)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: Payment Method Selection & Split Payment
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Payment Method", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 4 payment options: Cash, UPI, Split Payment, Credit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaymentOptionButton(
                                label = "Cash",
                                icon = Icons.Default.Payments,
                                isSelected = paymentMethod == "CASH",
                                color = CashAmber,
                                modifier = Modifier.weight(1f),
                                onClick = { paymentMethod = "CASH" }
                            )

                            PaymentOptionButton(
                                label = "UPI",
                                icon = Icons.Default.AccountBalanceWallet,
                                isSelected = paymentMethod == "UPI",
                                color = UPIBlue,
                                modifier = Modifier.weight(1f),
                                onClick = { paymentMethod = "UPI" }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaymentOptionButton(
                                label = "Split Payment",
                                icon = Icons.Default.CallSplit,
                                isSelected = paymentMethod == "SPLIT",
                                color = Color(0xFF673AB7),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    paymentMethod = "SPLIT"
                                    // Initialize split amounts if blank
                                    if (cashAmountInput.isBlank() && upiAmountInput.isBlank()) {
                                        val half = String.format(Locale.US, "%.2f", finalTotal / 2.0)
                                        cashAmountInput = half
                                        val rem = (finalTotal - (half.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)
                                        upiAmountInput = String.format(Locale.US, "%.2f", rem)
                                    }
                                }
                            )

                            PaymentOptionButton(
                                label = "Credit (Khata)",
                                icon = Icons.Default.AccountBalanceWallet,
                                isSelected = paymentMethod == "CREDIT",
                                color = AmberGold,
                                modifier = Modifier.weight(1f),
                                onClick = { paymentMethod = "CREDIT" }
                            )
                        }

                        // SPLIT PAYMENT INPUTS
                        if (paymentMethod == "SPLIT") {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF3E5F5),
                                border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CallSplit, contentDescription = null, tint = Color(0xFF673AB7), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Split Between Cash & UPI", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF4A148C))
                                    }
                                    Text(
                                        "Cash + UPI must exactly equal total (₹${String.format(Locale.US, "%.2f", finalTotal)})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6A1B9A)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = cashAmountInput,
                                            onValueChange = { newVal ->
                                                cashAmountInput = newVal
                                                val c = newVal.toDoubleOrNull() ?: 0.0
                                                val remaining = (finalTotal - c).coerceAtLeast(0.0)
                                                upiAmountInput = String.format(Locale.US, "%.2f", remaining)
                                            },
                                            label = { Text("Cash Amount (₹)") },
                                            placeholder = { Text("0.00") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )

                                        OutlinedTextField(
                                            value = upiAmountInput,
                                            onValueChange = { newVal ->
                                                upiAmountInput = newVal
                                                val u = newVal.toDoubleOrNull() ?: 0.0
                                                val remaining = (finalTotal - u).coerceAtLeast(0.0)
                                                cashAmountInput = String.format(Locale.US, "%.2f", remaining)
                                            },
                                            label = { Text("UPI Amount (₹)") },
                                            placeholder = { Text("0.00") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Visual validation indicator
                                    if (isSplitExactMatch) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFE8F5E9),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Match: Cash ₹${String.format(Locale.US, "%.2f", cashPart)} + UPI ₹${String.format(Locale.US, "%.2f", upiPart)} = ₹${String.format(Locale.US, "%.2f", finalTotal)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFFEBEE),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Mismatch: Sum = ₹${String.format(Locale.US, "%.2f", splitSum)} (Diff: ₹${String.format(Locale.US, "%.2f", abs(splitDiff))})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFC62828)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // CREDIT SUMMARY
                        if (paymentMethod == "CREDIT") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AmberGold.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = EspressoDark, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Khata Credit Settlement", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EspressoDark)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "This bill (₹${String.format(Locale.US, "%.2f", finalTotal)}) will be recorded into the customer's Khata ledger.",
                                        fontSize = 12.sp,
                                        color = EspressoDark
                                    )
                                    if (previousKhataBalance > 0.0) {
                                        val newTotalBalance = previousKhataBalance + finalTotal
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "New Total Khata Balance will be: ₹${String.format(Locale.US, "%.2f", newTotalBalance)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = EspressoDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Promotional Offers Selection Dialog with Available vs Claimed & Cooldown sections
    if (showPromoOffersSheet) {
        val isDark = isSystemInDarkTheme()
        AlertDialog(
            onDismissRequest = { showPromoOffersSheet = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = CaramelWarm, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Promotional Offers", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showPromoOffersSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                if (activeManualOffers.isEmpty()) {
                    Text(
                        "No active promotional offers right now. You can create discounts and promo deals in Settings > Promotional Offers.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = promoDialogTab == 0,
                                onClick = { promoDialogTab = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Available (${availablePromoOffers.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            SegmentedButton(
                                selected = promoDialogTab == 1,
                                onClick = { promoDialogTab = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Claimed / Cooldown (${claimedCooldownPromoOffers.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (promoDialogTab == 0) {
                                if (availablePromoOffers.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = CaramelWarm, modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "No offers available right now",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "Offers may be locked in cooldown or require a customer mobile number to verify eligibility.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    availablePromoOffers.forEach { status ->
                                        val offer = status.offer
                                        val isEligible = !status.isMinBillShort

                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isEligible) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isEligible) CaramelWarm.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = offer.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = when (offer.rewardType) {
                                                                    "PERCENT_DISCOUNT" -> "${offer.rewardValue.toInt()}% OFF"
                                                                    "FLAT_DISCOUNT" -> "Flat ₹${offer.rewardValue.toInt()} OFF"
                                                                    else -> "Free Item Deal"
                                                                },
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 13.sp,
                                                                color = CaramelWarm
                                                            )
                                                            // Cooldown frequency badge
                                                            val (freqText, freqColor) = when {
                                                                offer.cooldownDays == 0 -> "⚡ Every Visit" to SoftBlue
                                                                offer.cooldownDays == 1 -> "📅 Once / Day" to WarmAmber
                                                                offer.cooldownDays == -1 -> "🔒 Lifetime 1x" to ErrorRed
                                                                else -> "⏳ Every ${offer.cooldownDays}d" to SuccessGreen
                                                            }
                                                            Surface(
                                                                color = freqColor.copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(4.dp),
                                                                border = BorderStroke(0.5.dp, freqColor.copy(alpha = 0.5f))
                                                            ) {
                                                                Text(
                                                                    text = freqText,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = freqColor,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                        if (offer.minBillAmount > 0) {
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "Min Bill: ₹${offer.minBillAmount.toInt()}",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    if (isEligible) {
                                                        Button(
                                                            onClick = {
                                                                // Single-offer rule: clear visit reward if active
                                                                if (claimedFreeItem != null) {
                                                                    viewModel.removeFreeItemReward()
                                                                }
                                                                appliedVisitRewardOfferId = null

                                                                appliedManualOffer = offer
                                                                when (offer.rewardType) {
                                                                    "FLAT_DISCOUNT" -> {
                                                                        discountType = "FLAT"
                                                                        discountValueInput = offer.rewardValue.toInt().toString()
                                                                    }
                                                                    "PERCENT_DISCOUNT" -> {
                                                                        discountType = "PERCENT"
                                                                        discountValueInput = offer.rewardValue.toInt().toString()
                                                                    }
                                                                    else -> {
                                                                        activeRewardOffer = offer
                                                                        showFreeItemDialog = true
                                                                    }
                                                                }
                                                                showPromoOffersSheet = false
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = CaramelWarm),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                        ) {
                                                            Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    } else {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = DeepAmber.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "Add ₹${String.format(Locale.US, "%.0f", status.shortfall)} more",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = DeepAmber,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Claimed / Cooldown Offers Tab
                                if (claimedCooldownPromoOffers.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "No offers currently in cooldown",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                "All promotional offers are ready and available for this customer.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    claimedCooldownPromoOffers.forEach { status ->
                                        val offer = status.offer
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = offer.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (status.isPhoneRequired) Color(0xFFFEF3C7) else Color(0xFFFEE2E2),
                                                        border = BorderStroke(0.5.dp, if (status.isPhoneRequired) Color(0xFFF59E0B) else Color(0xFFEF4444))
                                                    ) {
                                                        Text(
                                                            text = if (status.isPhoneRequired) "⚠️ Phone Required" else if (offer.cooldownDays == -1) "🔒 Lifetime Used" else "🔒 In Cooldown",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (status.isPhoneRequired) Color(0xFFB45309) else Color(0xFFDC2626),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                if (status.isPhoneRequired) {
                                                    Text(
                                                        text = "Enter customer's mobile number on the billing screen to verify their ${offer.cooldownDays}-day cooldown eligibility.",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        lineHeight = 15.sp
                                                    )
                                                } else if (status.lastClaimedBill != null) {
                                                    Text(
                                                        text = "🧾 Claimed on Bill #${status.lastClaimedBill.billNumber} (${DateUtils.formatDate(status.lastClaimedBill.createdAt, "dd MMM yyyy")})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (offer.cooldownDays > 0) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "⏳ Cooldown: ${offer.cooldownDays} Days • ${status.daysRemaining} days left",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (status.unlockDate > 0) {
                                                            Text(
                                                                text = "📅 Next eligible on: ${DateUtils.formatDate(status.unlockDate, "dd MMM yyyy")}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = CaramelWarm
                                                            )
                                                        }
                                                    } else if (offer.cooldownDays == -1) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "This is a strictly one-time welcome offer per customer.",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Modal Sheet to Add Dishes directly from the Settlement Screen
    if (showAddItemsSheet) {
        AddItemsDialog(
            categories = categories,
            menuItems = menuItems,
            cartItems = cartState.items,
            onAddItem = { dish -> viewModel.addItemToCart(dish) },
            onDecreaseItem = { dishId -> viewModel.decreaseCartItem(dishId) },
            onDismiss = { showAddItemsSheet = false }
        )
    }

    // Free Item Reward Selection Dialog
    if (showFreeItemDialog && activeRewardOffer != null) {
        FreeItemSelectionDialog(
            offer = activeRewardOffer!!,
            menuItems = menuItems,
            categories = categories,
            onSelectDish = { chosenDish, qty ->
                viewModel.applyFreeItemReward(
                    offer = activeRewardOffer!!,
                    dish = chosenDish,
                    quantity = qty
                )
                showFreeItemDialog = false
            },
            onDismiss = {
                showFreeItemDialog = false
            }
        )
    }

    // In-App Live Draft Bill Preview Modal (8 Designs)
    if (showLiveDraftPreview) {
        val draftBill = remember(cartState, customerName, customerPhone, finalTotal, paymentMethod, safeRestaurant) {
            val nextNum = (allBills.mapNotNull { it.billNumber.toIntOrNull() }.maxOrNull() ?: allBills.size) + 1
            BillEntity(
                id = "DRAFT-${System.currentTimeMillis()}",
                billNumber = nextNum.toString(),
                restaurantId = safeRestaurant.id,
                restaurantName = safeRestaurant.name,
                orderType = cartState.orderType,
                items = cartState.items,
                subtotal = subtotal,
                discountAmount = discountAmount,
                totalAmount = finalTotal,
                paymentMethod = paymentMethod,
                customerName = customerName,
                customerPhone = customerPhone,
                tableName = cartState.selectedTable?.name,
                billTimestamp = System.currentTimeMillis()
            )
        }

        BillPreviewDialog(
            bill = draftBill,
            restaurant = safeRestaurant,
            previousDue = previousKhataBalance,
            onDismiss = { showLiveDraftPreview = false },
            onSetDefaultTemplate = { chosenFormat ->
                viewModel.updateBillFormat(chosenFormat)
            }
        )
    }

    var show8FormatPreviewForSettled by remember { mutableStateOf<BillEntity?>(null) }

    if (show8FormatPreviewForSettled != null) {
        val settled = show8FormatPreviewForSettled!!
        val currentRest = activeRestaurant ?: safeRestaurant
        BillPreviewDialog(
            bill = settled,
            restaurant = currentRest,
            previousDue = previousKhataBalance,
            onDismiss = {
                show8FormatPreviewForSettled = null
                onSettled(settled)
            },
            onSetDefaultTemplate = { chosenFormat ->
                viewModel.updateBillFormat(chosenFormat)
            }
        )
    }

    // Settled Bill Success & WhatsApp / Preview Dialog
    if (settledBillForDialog != null) {
        val settled = settledBillForDialog!!
        AlertDialog(
            onDismissRequest = {
                val b = settled
                settledBillForDialog = null
                onSettled(b)
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bill #${settled.billNumber} Settled!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total: ₹${String.format(Locale.US, "%.2f", settled.totalAmount)} (${settled.paymentMethod})", fontWeight = FontWeight.SemiBold)
                    if (previousKhataBalance > 0.0) {
                        Text("Previous Due: ₹${String.format(Locale.US, "%.2f", previousKhataBalance)}", fontSize = 12.sp, color = Color(0xFFD97706))
                    }
                    Text("Share elegant bill or view in 8 designer formats:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val b = settled
                            settledBillForDialog = null
                            show8FormatPreviewForSettled = b
                        }
                    ) {
                        Text("🎨 Live Bill Formats")
                    }
                    Button(
                        onClick = {
                            val currentRest = activeRestaurant ?: safeRestaurant
                            try {
                                val img = ReceiptBitmapGenerator.generateReceiptImageFile(context, settled, currentRest, previousKhataBalance)
                                BillShareUtil.sendViaWhatsAppImage(context, settled, currentRest, img, previousKhataBalance)
                            } catch (e: Exception) {
                                BillShareUtil.sendViaWhatsApp(context, settled, currentRest, previousKhataBalance)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val b = settled
                        settledBillForDialog = null
                        onSettled(b)
                    }
                ) {
                    Text("Next Order")
                }
            }
        )
    }
}

@Composable
private fun PaymentOptionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent
        ),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) color else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemsDialog(
    categories: List<com.example.data.local.entity.CategoryEntity>,
    menuItems: List<MenuItemEntity>,
    cartItems: List<com.example.data.local.entity.BillItem>,
    onAddItem: (MenuItemEntity) -> Unit,
    onDecreaseItem: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf<String?>(null) }

    val filteredItems = remember(menuItems, searchQuery, selectedCatId) {
        menuItems.filter { item ->
            val matchSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.categoryName.contains(searchQuery, ignoreCase = true)
            val matchCat = selectedCatId == null || item.categoryId == selectedCatId
            matchSearch && matchCat
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Menu Items", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search dishes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCatId == null,
                            onClick = { selectedCatId = null },
                            label = { Text("All (${menuItems.size})", fontSize = 12.sp) }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCatId == cat.id,
                            onClick = { selectedCatId = cat.id },
                            label = { Text(cat.name, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dishes List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems) { dish ->
                        val inCart = cartItems.find { it.dishId == dish.id }
                        val qty = inCart?.quantity ?: 0

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dish.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "₹${String.format(Locale.US, "%.2f", dish.price)} • ${dish.categoryName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (qty > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { onDecreaseItem(dish.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(16.dp))
                                        }

                                        Text(
                                            "$qty",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )

                                        FilledTonalIconButton(
                                            onClick = { onAddItem(dish) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onAddItem(dish) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done Editing Items", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class PromoOfferCooldownStatus(
    val offer: OfferEntity,
    val lastClaimedBill: BillEntity?,
    val isCooldownLocked: Boolean,
    val isPhoneRequired: Boolean,
    val isMinBillShort: Boolean,
    val shortfall: Double,
    val elapsedDays: Int,
    val daysRemaining: Int,
    val unlockDate: Long
)
