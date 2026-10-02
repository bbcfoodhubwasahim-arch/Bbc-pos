package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.CategoryEntity
import com.example.ui.screens.bills.BillDetailsDialog
import com.example.data.local.entity.MenuItemEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.OfferEntity
import com.example.data.local.entity.PointsBatchEntity
import com.example.data.local.entity.PointsEngineRules
import com.example.data.local.entity.PointsLedgerEntity
import com.example.data.local.entity.LoyaltyHistoryItem
import com.example.ui.MainViewModel
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.theme.*
import com.example.util.BillShareUtil
import com.example.util.DateUtils
import org.json.JSONArray
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersLoyaltySettingsSubScreen(
    viewModel: MainViewModel,
    paddingValues: PaddingValues,
    initialTabIndex: Int = 0,
    showTabs: Boolean = true
) {
    val context = LocalContext.current
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()
    val allOffers by viewModel.allOffers.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allActivePointsBatches by viewModel.allActivePointsBatches.collectAsState()
    val allPointsLedger by viewModel.allPointsLedger.collectAsState()
    val allBills by viewModel.allBills.collectAsState()
    val pointsEngineRules by viewModel.pointsEngineRules.collectAsState()

    var selectedTabIndex by remember(initialTabIndex) { mutableStateOf(initialTabIndex.coerceIn(0, 3)) }
    val tabs = listOf("🪙 Reward Points", "🎫 Visit Pass Campaigns", "🏷️ Promo Offers", "👥 Customers & Passes")

    // Modals
    var showOfferDialog by remember { mutableStateOf(false) }
    var offerToEdit by remember { mutableStateOf<OfferEntity?>(null) }
    var offerToDelete by remember { mutableStateOf<OfferEntity?>(null) }

    var showGrantGiftPointsDialog by remember { mutableStateOf(false) }
    var customerForGiftPoints by remember { mutableStateOf<CustomerEntity?>(null) }

    var showBulkGiftPointsDialog by remember { mutableStateOf(false) }
    var selectedBulkCustomerIds by remember { mutableStateOf(listOf<String>()) }

    var customerForHistoryDialog by remember { mutableStateOf<CustomerEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Tab Navigation
        if (showTabs) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = GoldAccent,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }
        }

        when (selectedTabIndex) {
            0 -> PointsLiabilitiesTab(
                viewModel = viewModel,
                batches = allActivePointsBatches,
                allCustomers = allCustomers,
                onGrantPointsClick = { cust ->
                    customerForGiftPoints = cust
                    showGrantGiftPointsDialog = true
                },
                onBulkGrantPointsClick = { customerIds ->
                    selectedBulkCustomerIds = customerIds
                    showBulkGiftPointsDialog = true
                },
                onViewCustomerHistory = { cust ->
                    customerForHistoryDialog = cust
                }
            )

            1 -> VisitProgramTab(
                restaurantId = activeRestaurant?.id ?: "REST-001",
                offers = allOffers.filter { it.offerType == "VISIT_BASED" },
                menuItemNames = menuItems.map { it.name },
                autoEnrollInVisitPass = pointsEngineRules.autoEnrollInVisitPass,
                onToggleAutoEnroll = { isEnrolled ->
                    viewModel.updatePointsEngineRules(pointsEngineRules.copy(autoEnrollInVisitPass = isEnrolled))
                    viewModel.setZeroVisitCustomersLoyaltyEnrollment(isEnrolled) {
                        Toast.makeText(
                            context,
                            if (isEnrolled) "Auto-Enroll ON: All new & 0-visit customers enrolled." else "Auto-Enroll OFF: All 0-visit customers set to Not Enrolled.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onTurnOffAllZeroVisitPasses = {
                    viewModel.setZeroVisitCustomersLoyaltyEnrollment(false) {
                        Toast.makeText(context, "Turned OFF pass for all 0-visit customers", Toast.LENGTH_SHORT).show()
                    }
                },
                onTurnOnAllPasses = {
                    viewModel.setAllCustomersLoyaltyEnrollment(true) {
                        Toast.makeText(context, "Turned ON pass for all customers", Toast.LENGTH_SHORT).show()
                    }
                },
                onAddMilestone = {
                    offerToEdit = null
                    showOfferDialog = true
                },
                onEditOffer = { offer ->
                    offerToEdit = offer
                    showOfferDialog = true
                },
                onToggleActive = { offer, active ->
                    viewModel.toggleOfferActive(offer.id, active)
                },
                onDeleteOffer = { offer ->
                    offerToDelete = offer
                },
                onSaveProgramSettings = { totalVisits, validityDays ->
                    val restId = activeRestaurant?.id ?: "REST-001"
                    val currentVisitOffers = allOffers.filter { it.offerType == "VISIT_BASED" }
                    val updated = currentVisitOffers.map {
                        it.copy(
                            totalVisitsInProgram = totalVisits,
                            validityDays = validityDays
                        )
                    }
                    viewModel.saveVisitProgram(totalVisits, updated) {
                        Toast.makeText(context, "Program settings updated", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            2 -> ManualOffersTab(
                offers = allOffers.filter { it.offerType == "MANUAL" },
                menuItemNames = menuItems.map { it.name },
                onCreateOffer = {
                    offerToEdit = null
                    showOfferDialog = true
                },
                onEditOffer = { offer ->
                    offerToEdit = offer
                    showOfferDialog = true
                },
                onToggleActive = { offer, active ->
                    viewModel.toggleOfferActive(offer.id, active)
                },
                onDeleteOffer = { offer ->
                    offerToDelete = offer
                }
            )

            3 -> CustomerLoyaltyTab(
                customers = allCustomers,
                onSaveCustomer = { name, phone, birthday, isEnrolled ->
                    viewModel.registerOrUpdateCustomer(
                        name = name,
                        phone = phone,
                        birthday = birthday,
                        isEnrolledInLoyalty = isEnrolled
                    ) {
                        Toast.makeText(context, "Saved customer $name", Toast.LENGTH_SHORT).show()
                    }
                },
                onResetCycle = { cust ->
                    viewModel.resetCustomerLoyalty(cust.id) {
                        Toast.makeText(context, "Reset loyalty cycle for ${cust.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onReEnableCycle = { cust ->
                    viewModel.reEnableCustomerLoyalty(cust.id)
                    Toast.makeText(context, "Re-enabled pass for ${cust.name}", Toast.LENGTH_SHORT).show()
                },
                onExtendValidity = { cust ->
                    viewModel.extendCustomerLoyalty(cust.id, 30) {
                        Toast.makeText(context, "Extended validity by 30 days", Toast.LENGTH_SHORT).show()
                    }
                },
                onGrantGiftPoints = { cust ->
                    customerForGiftPoints = cust
                    showGrantGiftPointsDialog = true
                },
                onBulkGrantGiftPoints = { selectedIds ->
                    selectedBulkCustomerIds = selectedIds
                    showBulkGiftPointsDialog = true
                },
                onToggleEnrollment = { id, isEnrolled ->
                    viewModel.toggleCustomerLoyaltyEnrollment(id, isEnrolled)
                },
                onBulkEnrollment = { ids, isEnrolled ->
                    viewModel.bulkUpdateLoyaltyEnrollment(ids, isEnrolled)
                    Toast.makeText(context, "Updated ${ids.size} customers' pass status", Toast.LENGTH_SHORT).show()
                },
                onViewHistory = { cust ->
                    customerForHistoryDialog = cust
                }
            )
        }
    }

    // Offer Create / Edit Dialog
    if (showOfferDialog) {
        OfferFormDialog(
            restaurantId = activeRestaurant?.id ?: "REST-001",
            offer = offerToEdit,
            defaultOfferType = if (selectedTabIndex == 0) "VISIT_BASED" else "MANUAL",
            menuItems = menuItems,
            categories = categories,
            onDismiss = { showOfferDialog = false },
            onSave = { offer ->
                viewModel.saveOffer(offer) {
                    Toast.makeText(context, "Offer saved successfully", Toast.LENGTH_SHORT).show()
                    showOfferDialog = false
                }
            }
        )
    }

    // Delete confirmation
    if (offerToDelete != null) {
        DeleteConfirmationDialog(
            title = "Delete Offer?",
            itemName = offerToDelete?.name ?: "Offer",
            message = "Are you sure you want to delete '${offerToDelete?.name}'? This cannot be undone.",
            onConfirm = {
                offerToDelete?.let {
                    viewModel.deleteOffer(it.id) {
                        Toast.makeText(context, "Offer deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                offerToDelete = null
            },
            onDismiss = { offerToDelete = null }
        )
    }

    // Single Customer Grant Gift Points Dialog
    if (showGrantGiftPointsDialog) {
        GrantGiftPointsDialog(
            allCustomers = allCustomers,
            initialCustomer = customerForGiftPoints,
            onDismiss = { showGrantGiftPointsDialog = false },
            onGrant = { custId, pts, note, expiryDays ->
                viewModel.addGiftPoints(custId, pts, note, expiryDays) {
                    Toast.makeText(context, "Awarded $pts gift points (Validity: $expiryDays days)", Toast.LENGTH_SHORT).show()
                    showGrantGiftPointsDialog = false
                }
            }
        )
    }

    // Bulk Customer Grant Gift Points Dialog
    if (showBulkGiftPointsDialog && selectedBulkCustomerIds.isNotEmpty()) {
        val selectedCustomerObjects = remember(selectedBulkCustomerIds, allCustomers) {
            allCustomers.filter { it.id in selectedBulkCustomerIds }
        }
        BulkGrantGiftPointsDialog(
            selectedCustomers = selectedCustomerObjects,
            onDismiss = { showBulkGiftPointsDialog = false },
            onGrantBulk = { ids, pts, notes, expiryDays, shareWhatsApp ->
                viewModel.addBulkGiftPoints(ids, pts, notes, expiryDays) {
                    Toast.makeText(context, "🎁 Awarded $pts points to ${ids.size} customers successfully! (Validity: $expiryDays days)", Toast.LENGTH_LONG).show()
                    showBulkGiftPointsDialog = false
                }
            }
        )
    }

    // Customer History Dialog
    if (customerForHistoryDialog != null) {
        val cust = customerForHistoryDialog!!
        val historyItems = remember(cust) { viewModel.repository.parseLoyaltyHistory(cust.loyaltyHistoryJson) }
        val customerLedger = remember(cust, allPointsLedger) {
            allPointsLedger.filter { it.customerId == cust.id }.sortedByDescending { it.timestamp }
        }

        CustomerLoyaltyHistoryDialog(
            customer = cust,
            historyItems = historyItems,
            ledgerItems = customerLedger,
            allBills = allBills,
            viewModel = viewModel,
            onDismiss = { customerForHistoryDialog = null }
        )
    }
}

@Composable
private fun VisitProgramTab(
    restaurantId: String,
    offers: List<OfferEntity>,
    menuItemNames: List<String>,
    autoEnrollInVisitPass: Boolean = false,
    onToggleAutoEnroll: (Boolean) -> Unit = {},
    onTurnOffAllZeroVisitPasses: () -> Unit = {},
    onTurnOnAllPasses: () -> Unit = {},
    onAddMilestone: () -> Unit,
    onEditOffer: (OfferEntity) -> Unit,
    onToggleActive: (OfferEntity, Boolean) -> Unit,
    onDeleteOffer: (OfferEntity) -> Unit,
    onSaveProgramSettings: (totalVisits: Int, validityDays: Int) -> Unit
) {
    val totalVisits = offers.firstOrNull()?.totalVisitsInProgram ?: 10
    val validityDays = offers.firstOrNull()?.validityDays ?: 45

    var totalVisitsInput by remember(totalVisits) { mutableStateOf(totalVisits.toString()) }
    var validityDaysInput by remember(validityDays) { mutableStateOf(validityDays.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, WarmAmber)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Visit Loyalty Cycle", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Surface(
                            color = SuccessGreen,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Auto Progress",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Customers earn progress towards milestone rewards each time they visit and settle a bill. The cycle automatically resets after reaching the total visits or after the validity period expires.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = totalVisitsInput,
                            onValueChange = { totalVisitsInput = it },
                            label = { Text("Visits in Cycle", color = TextPrimary, fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = validityDaysInput,
                            onValueChange = { validityDaysInput = it },
                            label = { Text("Validity (Days)", color = TextPrimary, fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val tv = totalVisitsInput.toIntOrNull() ?: 10
                            val vd = validityDaysInput.toIntOrNull() ?: 45
                            onSaveProgramSettings(tv, vd)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepAmber, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Cycle Rules", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Auto-Enroll New Customers Master Setting Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (autoEnrollInVisitPass) SuccessGreen.copy(alpha = 0.5f) else CrispCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = if (autoEnrollInVisitPass) SuccessGreen else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Auto-Enroll New Customers",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (autoEnrollInVisitPass) "New customers will automatically get a Visit Pass enabled at billing." else "Disabled (Recommended): Visit Pass is OFF by default for new customers. Cashier can enable it selectively on billing screen.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoEnrollInVisitPass,
                            onCheckedChange = onToggleAutoEnroll,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SuccessGreen,
                                checkedTrackColor = SuccessGreen.copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTurnOffAllZeroVisitPasses,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Turn OFF Pass (0-Visits)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }

                        OutlinedButton(
                            onClick = onTurnOnAllPasses,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Turn ON Pass (All)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }
                }
            }
        }

        // Milestone rewards header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Milestone Rewards", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${offers.size} milestone reward(s) active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onAddMilestone,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Milestone", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (offers.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Visit Milestones Configured", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Add milestone rewards like '3rd Visit: Free Brownie' or '6th Visit: 15% Off'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(offers.sortedBy { it.visitNumber }) { offer ->
                OfferCard(
                    offer = offer,
                    onEdit = { onEditOffer(offer) },
                    onToggleActive = { onToggleActive(offer, it) },
                    onDelete = { onDeleteOffer(offer) }
                )
            }
        }
    }
}

@Composable
private fun ManualOffersTab(
    offers: List<OfferEntity>,
    menuItemNames: List<String>,
    onCreateOffer: () -> Unit,
    onEditOffer: (OfferEntity) -> Unit,
    onToggleActive: (OfferEntity, Boolean) -> Unit,
    onDeleteOffer: (OfferEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Manual Promotional Offers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Ad-hoc specials selectable by cashier at settlement",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onCreateOffer,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ New Offer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (offers.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Manual Offers Configured", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Create promotional offers like 'Weekend Special ₹50 off on ₹300' or 'Rainy Day Free Beverage'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(offers) { offer ->
                OfferCard(
                    offer = offer,
                    onEdit = { onEditOffer(offer) },
                    onToggleActive = { onToggleActive(offer, it) },
                    onDelete = { onDeleteOffer(offer) }
                )
            }
        }
    }
}

@Composable
private fun OfferCard(
    offer: OfferEntity,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (offer.isActive) WarmAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = if (offer.offerType == "VISIT_BASED") WarmAmber.copy(alpha = 0.15f) else SoftBlue.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (offer.offerType == "VISIT_BASED") Icons.Default.EmojiEvents else Icons.Default.LocalOffer,
                                contentDescription = null,
                                tint = if (offer.offerType == "VISIT_BASED") WarmAmber else SoftBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = offer.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (offer.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                        if (offer.offerType == "VISIT_BASED") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Milestone: Visit #${offer.visitNumber} of ${offer.totalVisitsInProgram}",
                                    fontSize = 12.sp,
                                    color = WarmAmber,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val totalDots = offer.totalVisitsInProgram.coerceIn(1, 10)
                                    for (i in 1..totalDots) {
                                        val isReached = i <= offer.visitNumber
                                        Box(
                                            modifier = Modifier
                                                .size(if (isReached) 8.dp else 6.dp)
                                                .background(
                                                    color = if (isReached) WarmAmber else Color(0xFFCBD5E1),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Manual Promo Offer",
                                fontSize = 12.sp,
                                color = SoftBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Switch(
                    checked = offer.isActive,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WarmAmber,
                        checkedTrackColor = WarmAmber.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reward details badge
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CrispCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = when (offer.rewardType) {
                                "FREE_ITEM" -> "🎁 Free Reward Item (${offer.freeItemQuantity}x)"
                                "FLAT_DISCOUNT" -> "🏷️ Flat ₹${offer.rewardValue.toInt()} OFF"
                                "PERCENT_DISCOUNT" -> "🏷️ ${offer.rewardValue.toInt()}% OFF"
                                else -> "Custom Reward"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (offer.minBillAmount > 0) {
                                Surface(
                                    color = DeepAmber.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, DeepAmber.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Min Bill: ₹${offer.minBillAmount.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepAmber,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (offer.offerType == "MANUAL") {
                                val (cdText, cdColor) = when {
                                    offer.cooldownDays == 0 -> "⚡ Every Visit" to SoftBlue
                                    offer.cooldownDays == 1 -> "📅 Once / Day" to WarmAmber
                                    offer.cooldownDays == -1 -> "🔒 Lifetime 1-Time" to ErrorRed
                                    else -> "⏳ Every ${offer.cooldownDays} Days" to SuccessGreen
                                }
                                Surface(
                                    color = cdColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, cdColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = cdText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cdColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, CrispCardBorder)
                        ) {
                            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DeepAmber, modifier = Modifier.size(18.dp))
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, CrispCardBorder)
                        ) {
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsLiabilitiesTab(
    viewModel: MainViewModel,
    batches: List<PointsBatchEntity>,
    allCustomers: List<CustomerEntity>,
    onGrantPointsClick: (CustomerEntity?) -> Unit,
    onBulkGrantPointsClick: (List<String>) -> Unit,
    onViewCustomerHistory: (CustomerEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var totalLiability by remember { mutableStateOf(0.0) }
    val expiringBatches by viewModel.repository.getExpiringBatches(7).collectAsState(initial = emptyList())
    val pointsEngineRules by viewModel.pointsEngineRules.collectAsState()
    var showEditRulesDialog by remember { mutableStateOf(false) }

    // Search, Filter, Sort & Multi-Select state for Customer Points Ledger inside this screen
    var searchQuery by remember { mutableStateOf("") }
    var pointsFilterType by remember { mutableStateOf(0) } // 0 = All Customers, 1 = With Points Only
    var sortOption by remember { mutableStateOf(0) } // 0 = Name A-Z, 1 = Name Z-A, 2 = Points High->Low
    var selectedCustomerIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(batches) {
        totalLiability = viewModel.getOutstandingPointsLiability()
    }

    val filteredCustomers = remember(allCustomers, searchQuery, pointsFilterType, sortOption) {
        var list = allCustomers
        if (pointsFilterType == 1) {
            list = list.filter { (it.rewardPointsBalance + it.giftPointsBalance) > 0 }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.name.contains(q, ignoreCase = true) || it.contactNumber.contains(q)
            }
        }
        when (sortOption) {
            0 -> list.sortedBy { it.name.trim().lowercase() }
            1 -> list.sortedByDescending { it.name.trim().lowercase() }
            2 -> list.sortedByDescending { it.rewardPointsBalance + it.giftPointsBalance }
            else -> list.sortedBy { it.name.trim().lowercase() }
        }
    }

    val isAllSelected = filteredCustomers.isNotEmpty() && filteredCustomers.all { it.id in selectedCustomerIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // KPI Liability Card (Clean Horizontal Layout - Zero Vertical Squeezed Buttons)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, WarmAmber)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Total Outstanding Points Liability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", totalLiability)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = WarmAmber
                    )
                    Text(
                        text = "${totalLiability.toInt()} total active unredeemed points across all customers",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onGrantPointsClick(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepAmber, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Single Gift Pts", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (selectedCustomerIds.isNotEmpty()) {
                                    onBulkGrantPointsClick(selectedCustomerIds.toList())
                                } else {
                                    onBulkGrantPointsClick(filteredCustomers.map { it.id })
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FoodHubCharcoal, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedCustomerIds.isNotEmpty()) "Bulk Grant (${selectedCustomerIds.size})" else "Bulk Gift Points",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Interactive Points Engine Rules Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, WarmAmber)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Points Engine Rules", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Button(
                            onClick = { showEditRulesDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DeepAmber, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1-Tap Welcome Bonus Master Toggle Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = if (pointsEngineRules.welcomeBonusEnabled) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, if (pointsEngineRules.welcomeBonusEnabled) WarmAmber.copy(alpha = 0.6f) else CrispCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CardGiftcard,
                                        contentDescription = null,
                                        tint = if (pointsEngineRules.welcomeBonusEnabled) WarmAmber else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "🎁 Welcome Bonus for New Customers",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (pointsEngineRules.welcomeBonusEnabled) "ON: New customers get +${pointsEngineRules.welcomeBonusPoints} Welcome Gift Points on their first visit (Valid ${pointsEngineRules.welcomeBonusExpiryDays}d)." else "OFF: Welcome Bonus is disabled. New customers earn standard bill % only.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = pointsEngineRules.welcomeBonusEnabled,
                                onCheckedChange = { isEnabled ->
                                    viewModel.updatePointsEngineRules(pointsEngineRules.copy(welcomeBonusEnabled = isEnabled))
                                    Toast.makeText(context, if (isEnabled) "Welcome Bonus Enabled (+${pointsEngineRules.welcomeBonusPoints} Pts)" else "Welcome Bonus Disabled", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DeepAmber,
                                    checkedTrackColor = WarmAmber.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1-Tap Quick Campaign Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                viewModel.updatePointsEngineRules(PointsEngineRules.DEFAULT)
                                Toast.makeText(context, "Applied Standard Rules (1%)", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🟢 Standard (1%)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        SuggestionChip(
                            onClick = {
                                viewModel.updatePointsEngineRules(PointsEngineRules.FESTIVE_2X)
                                Toast.makeText(context, "Applied 2X Festive Bonus (2%)", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🟡 2X Festive (2%)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        SuggestionChip(
                            onClick = {
                                viewModel.updatePointsEngineRules(PointsEngineRules.VIP_MEGA)
                                Toast.makeText(context, "Applied VIP Mega Offer (5%)", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🔴 VIP Mega (5%)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic Rules Details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "• Earning: ${pointsEngineRules.earnRatePercent}% points earned on net payable amount (1 pt per ₹${String.format(Locale.US, "%.0f", 100.0 / pointsEngineRules.earnRatePercent.coerceAtLeast(0.01))} spent)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "• Valuation: 1 Point = ₹${String.format(Locale.US, "%.2f", pointsEngineRules.pointValueRupees)} discount upon redemption",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "• Minimum Redemption: ${pointsEngineRules.minRedemptionPoints} points required to redeem",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "• Redemption Cap: Reward Points max ${pointsEngineRules.maxDiscountCapPercent.toInt()}% of bill; Gift Points max 100% of bill",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "• Expiry: ${pointsEngineRules.rewardPointsExpiryDays} Days FIFO (Reward)  •  ${pointsEngineRules.giftPointsExpiryDays} Days (Gift Points)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        if (pointsEngineRules.welcomeBonusEnabled) {
                            Text(
                                "• Welcome Bonus: ${pointsEngineRules.welcomeBonusPoints} Gift Points awarded on new customer's first registration (${pointsEngineRules.welcomeBonusExpiryDays} Days validity)",
                                fontSize = 12.sp,
                                color = WarmAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "• Visit Pass Auto-Enroll: ${if (pointsEngineRules.autoEnrollInVisitPass) "Enabled (All New Customers)" else "Disabled (Selective / Cashier Choice)"}",
                            fontSize = 12.sp,
                            color = if (pointsEngineRules.autoEnrollInVisitPass) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        if (pointsEngineRules.minBillAmountToEarn > 0.0) {
                            Text(
                                "• Min Bill to Earn: ₹${pointsEngineRules.minBillAmountToEarn.toInt()} minimum bill amount required to earn points",
                                fontSize = 12.sp,
                                color = DeepAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Rule Simulator Box
                    val sampleBill = 500.0
                    val sampleEarned = ((sampleBill * (pointsEngineRules.earnRatePercent / 100.0))).toInt()
                    val sampleValue = sampleEarned * pointsEngineRules.pointValueRupees
                    val sampleMaxRedeemable = (sampleBill * (pointsEngineRules.maxDiscountCapPercent / 100.0)).toInt()

                    Surface(
                        color = WarmAmber.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Live Calculator Preview (Sample ₹500 Bill)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WarmAmber)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Customer earns $sampleEarned Points (Value: ₹${String.format(Locale.US, "%.2f", sampleValue)}). Max points discount on bill: ₹$sampleMaxRedeemable.00 (Cap: ${pointsEngineRules.maxDiscountCapPercent.toInt()}%).",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Expiring Soon Batches Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Expiring in Next 7 Days (${expiringBatches.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (expiringBatches.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        "No customer points expiring within the next 7 days.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(expiringBatches) { batch ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = batch.customerName.ifBlank { "Customer" } + " (${batch.customerPhone})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${batch.remainingPoints} points expiring on ${DateUtils.formatDate(batch.expiryDate, "dd MMM yyyy")}",
                                fontSize = 12.sp,
                                color = AmberGold,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (batch.reminderSent) {
                                Text("Reminder marked sent", fontSize = 11.sp, color = SuccessGreen)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (batch.customerPhone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val phoneDigits = batch.customerPhone.filter { it.isDigit() }
                                        val message = "Dear ${batch.customerName.ifBlank { "Customer" }}, you have ${batch.remainingPoints} reward points expiring soon on ${DateUtils.formatDate(batch.expiryDate, "dd MMM yyyy")}! Visit us soon to redeem ₹${batch.remainingPoints} on your bill."
                                        val url = "https://wa.me/91$phoneDigits?text=${Uri.encode(message)}"
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                            coroutineScope.launch {
                                                viewModel.repository.markPointsReminderSent(batch.id)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Send WhatsApp", tint = SuccessGreen)
                                }
                            }

                            if (!batch.reminderSent) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.repository.markPointsReminderSent(batch.id)
                                            Toast.makeText(context, "Marked reminder sent", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Mark Sent", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Customer Points Directory & Ledger
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("👥 Customer Points Directory & Ledger", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("View earned, redeemed & gift points for all customers", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Search bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by customer name or phone number...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Filter & Sort Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = pointsFilterType == 0,
                        onClick = { pointsFilterType = 0 },
                        label = { Text("All Customers (${allCustomers.size})") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = pointsFilterType == 1,
                        onClick = { pointsFilterType = 1 },
                        label = { Text("With Points (${allCustomers.count { (it.rewardPointsBalance + it.giftPointsBalance) > 0 }})") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sort:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = sortOption == 0,
                            onClick = { sortOption = 0 },
                            label = { Text("🔤 A-Z", fontSize = 11.sp, fontWeight = if (sortOption == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        FilterChip(
                            selected = sortOption == 1,
                            onClick = { sortOption = 1 },
                            label = { Text("🔤 Z-A", fontSize = 11.sp, fontWeight = if (sortOption == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                        FilterChip(
                            selected = sortOption == 2,
                            onClick = { sortOption = 2 },
                            label = { Text("🪙 Points", fontSize = 11.sp, fontWeight = if (sortOption == 2) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }

        // Multi-Select Action Bar inside Points Directory
        item {
            Surface(
                color = FoodHubCharcoal.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAllSelected,
                            onCheckedChange = { checked ->
                                selectedCustomerIds = if (checked) {
                                    selectedCustomerIds + filteredCustomers.map { it.id }
                                } else {
                                    selectedCustomerIds - filteredCustomers.map { it.id }.toSet()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedCustomerIds.isEmpty()) "Select Customers for Bulk Action" else "${selectedCustomerIds.size} Selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCustomerIds.isNotEmpty()) WarmAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (selectedCustomerIds.isNotEmpty()) {
                        Button(
                            onClick = { onBulkGrantPointsClick(selectedCustomerIds.toList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = WarmAmber, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Grant Gift Points to ${selectedCustomerIds.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Customer Cards List
        if (filteredCustomers.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No matching customers found", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        } else {
            items(filteredCustomers) { customer ->
                val isSelected = customer.id in selectedCustomerIds
                val totalPoints = customer.rewardPointsBalance + customer.giftPointsBalance

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) WarmAmber.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) WarmAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedCustomerIds = if (checked) {
                                            selectedCustomerIds + customer.id
                                        } else {
                                            selectedCustomerIds - customer.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = customer.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "📞 ${customer.contactNumber}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Total Points Badge
                            Surface(
                                color = if (totalPoints > 0) SuccessGreen.copy(alpha = 0.15f) else FoodHubCharcoal.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "$totalPoints Pts",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = if (totalPoints > 0) SuccessGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Value: ₹$totalPoints.00",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Detailed Earned / Redeemed / Gift breakdown row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Reward Points", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${customer.rewardPointsBalance} pts", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmAmber)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Gift Points", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${customer.giftPointsBalance} pts", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepAmber)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Visits", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${customer.loyaltyVisitCount} visits", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Individual Customer Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onViewCustomerHistory(customer) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Points Ledger History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { onGrantPointsClick(customer) },
                                colors = ButtonDefaults.buttonColors(containerColor = WarmAmber, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Gift Points", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditRulesDialog) {
        EditPointsEngineRulesDialog(
            currentRules = pointsEngineRules,
            onDismiss = { showEditRulesDialog = false },
            onSave = { updatedRules ->
                viewModel.updatePointsEngineRules(updatedRules)
                Toast.makeText(context, "Points Engine Rules updated successfully!", Toast.LENGTH_SHORT).show()
                showEditRulesDialog = false
            }
        )
    }
}

@Composable
private fun CustomerLoyaltyTab(
    customers: List<CustomerEntity>,
    onSaveCustomer: (name: String, phone: String, birthday: String?, isEnrolled: Boolean) -> Unit,
    onResetCycle: (CustomerEntity) -> Unit,
    onReEnableCycle: (CustomerEntity) -> Unit,
    onExtendValidity: (CustomerEntity) -> Unit,
    onGrantGiftPoints: (CustomerEntity) -> Unit,
    onBulkGrantGiftPoints: (List<String>) -> Unit,
    onToggleEnrollment: (String, Boolean) -> Unit,
    onBulkEnrollment: (List<String>, Boolean) -> Unit,
    onViewHistory: (CustomerEntity) -> Unit
) {
    val context = LocalContext.current
    var subTabIndex by remember { mutableStateOf(0) } // 0 = Visit Program Members, 1 = Reward Points Ledger
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(0) } // 0 = All, 1 = Active Enrolled, 2 = Completed / Off
    var sortOption by remember { mutableStateOf(0) } // 0 = Name A-Z (Alphabetical), 1 = Name Z-A, 2 = Most Visits, 3 = Most Points
    var selectedCustomerIds by remember { mutableStateOf(setOf<String>()) }
    var customerToReset by remember { mutableStateOf<CustomerEntity?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    val filtered = remember(customers, searchQuery, filterType, subTabIndex, sortOption) {
        var list = customers
        if (subTabIndex == 0) {
            // Visit Program Members Tab
            if (filterType == 1) {
                list = list.filter { it.isEnrolledInLoyalty }
            } else if (filterType == 2) {
                list = list.filter { !it.isEnrolledInLoyalty || it.loyaltyVisitCount > 0 }
            }
        } else {
            // Reward Points Ledger Tab
            if (filterType == 1) {
                list = list.filter { (it.rewardPointsBalance + it.giftPointsBalance) > 0 }
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.name.contains(q, ignoreCase = true) || it.contactNumber.contains(q)
            }
        }

        // Alphabetical sorting by default and sorting selections
        list = when (sortOption) {
            0 -> list.sortedBy { it.name.trim().lowercase() }
            1 -> list.sortedByDescending { it.name.trim().lowercase() }
            2 -> list.sortedByDescending { it.loyaltyVisitCount }
            3 -> list.sortedByDescending { it.rewardPointsBalance + it.giftPointsBalance }
            else -> list.sortedBy { it.name.trim().lowercase() }
        }

        list
    }

    val isAllSelected = filtered.isNotEmpty() && filtered.all { it.id in selectedCustomerIds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sub-Tabs Header: Visit Program Members vs Reward Points Ledger
        TabRow(
            selectedTabIndex = subTabIndex,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = GoldAccent,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = subTabIndex == 0,
                onClick = {
                    subTabIndex = 0
                    filterType = 0
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Visit Program Members", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
            Tab(
                selected = subTabIndex == 1,
                onClick = {
                    subTabIndex = 1
                    filterType = 0
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reward Points Ledger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search and Filters
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by customer name or 10-digit phone...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Filter Chips based on Active Sub-Tab
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterType == 0,
                onClick = { filterType = 0 },
                label = { Text("All (${customers.size})") },
                modifier = Modifier.weight(1f)
            )
            if (subTabIndex == 0) {
                FilterChip(
                    selected = filterType == 1,
                    onClick = { filterType = 1 },
                    label = { Text("Active Enrolled (${customers.count { it.isEnrolledInLoyalty }})") },
                    modifier = Modifier.weight(1.3f)
                )
                FilterChip(
                    selected = filterType == 2,
                    onClick = { filterType = 2 },
                    label = { Text("Completed / Off (${customers.count { !it.isEnrolledInLoyalty }})") },
                    modifier = Modifier.weight(1.3f)
                )
            } else {
                FilterChip(
                    selected = filterType == 1,
                    onClick = { filterType = 1 },
                    label = { Text("With Points (${customers.count { (it.rewardPointsBalance + it.giftPointsBalance) > 0 }})") },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sorting Row (A-Z Alphabetical default)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sort Order:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = sortOption == 0,
                    onClick = { sortOption = 0 },
                    label = { Text("🔤 A-Z", fontSize = 11.sp, fontWeight = if (sortOption == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                FilterChip(
                    selected = sortOption == 1,
                    onClick = { sortOption = 1 },
                    label = { Text("🔤 Z-A", fontSize = 11.sp, fontWeight = if (sortOption == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                FilterChip(
                    selected = sortOption == 2,
                    onClick = { sortOption = 2 },
                    label = { Text("🔢 Visits", fontSize = 11.sp, fontWeight = if (sortOption == 2) FontWeight.Bold else FontWeight.Normal) }
                )
                FilterChip(
                    selected = sortOption == 3,
                    onClick = { sortOption = 3 },
                    label = { Text("🪙 Points", fontSize = 11.sp, fontWeight = if (sortOption == 3) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Multi-Select Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = { checked ->
                            selectedCustomerIds = if (checked) {
                                selectedCustomerIds + filtered.map { it.id }
                            } else {
                                selectedCustomerIds - filtered.map { it.id }.toSet()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (selectedCustomerIds.isEmpty()) "Select Customers for Bulk Action" else "${selectedCustomerIds.size} Selected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedCustomerIds.isNotEmpty()) GoldAccent else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { showAddCustomerDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Register Customer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (selectedCustomerIds.isNotEmpty()) {
                        Button(
                            onClick = { onBulkGrantGiftPoints(selectedCustomerIds.toList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Gift", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onBulkEnrollment(selectedCustomerIds.toList(), true) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Enroll", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onBulkEnrollment(selectedCustomerIds.toList(), false) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Unenroll", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching customers found", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { customer ->
                    val isSelected = customer.id in selectedCustomerIds
                    CustomerLoyaltyCard(
                        customer = customer,
                        isSelected = isSelected,
                        onToggleSelect = {
                            selectedCustomerIds = if (isSelected) {
                                selectedCustomerIds - customer.id
                            } else {
                                selectedCustomerIds + customer.id
                            }
                        },
                        onToggleEnrollment = { isEnrolled ->
                            onToggleEnrollment(customer.id, isEnrolled)
                        },
                        onResetCycle = { customerToReset = customer },
                        onReEnableCycle = { onReEnableCycle(customer) },
                        onExtendValidity = { onExtendValidity(customer) },
                        onGrantGiftPoints = { onGrantGiftPoints(customer) },
                        onViewHistory = { onViewHistory(customer) },
                        onShareWhatsApp = {
                            BillShareUtil.sendCustomerLoyaltyStatusWhatsApp(context, customer)
                        }
                    )
                }
            }
        }

        // Reset Cycle Confirmation Dialog
        if (customerToReset != null) {
            val cust = customerToReset!!
            AlertDialog(
                onDismissRequest = { customerToReset = null },
                title = { Text("Reset Loyalty Cycle?") },
                text = { Text("Are you sure you want to reset the visit count and cycle for ${cust.name.ifBlank { "this customer" }}? Current visit progress will be reset to 0.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onResetCycle(cust)
                            customerToReset = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Confirm Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { customerToReset = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add / Register Customer Dialog (Requirement 11: Optional Birthday support)
        if (showAddCustomerDialog) {
            AddCustomerLoyaltyDialog(
                onDismiss = { showAddCustomerDialog = false },
                onSave = { name, phone, birthday, isEnrolled ->
                    onSaveCustomer(name, phone, birthday, isEnrolled)
                    showAddCustomerDialog = false
                }
            )
        }
    }
}

@Composable
private fun AddCustomerLoyaltyDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, birthday: String?, isEnrolled: Boolean) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var birthdayInput by remember { mutableStateOf("") }
    var isEnrolledInput by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Customer", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Customer Name *") },
                    placeholder = { Text("e.g. Ramesh Kumar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Mobile Phone Number *") },
                    placeholder = { Text("10-digit number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = birthdayInput,
                    onValueChange = { birthdayInput = it },
                    label = { Text("Birthday (Optional)") },
                    placeholder = { Text("e.g. 15 Aug / 15-08") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enroll in Visit Loyalty Program", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Switch(checked = isEnrolledInput, onCheckedChange = { isEnrolledInput = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameInput.isNotBlank() && phoneInput.isNotBlank()) {
                        onSave(nameInput.trim(), phoneInput.trim(), birthdayInput.trim().ifBlank { null }, isEnrolledInput)
                    }
                },
                enabled = nameInput.isNotBlank() && phoneInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Register & Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CustomerLoyaltyCard(
    customer: CustomerEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onToggleEnrollment: (Boolean) -> Unit,
    onResetCycle: () -> Unit,
    onReEnableCycle: () -> Unit,
    onExtendValidity: () -> Unit,
    onGrantGiftPoints: () -> Unit,
    onViewHistory: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val totalPoints = customer.rewardPointsBalance + customer.giftPointsBalance
    val isExpired = customer.loyaltyExpiryDate != null && System.currentTimeMillis() > customer.loyaltyExpiryDate!!

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GoldAccent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) GoldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = customer.name.ifBlank { "Customer" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = customer.contactNumber.ifBlank { "No phone recorded" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            if (!customer.birthday.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🎂 ${customer.birthday}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = customer.isEnrolledInLoyalty,
                        onClick = { onToggleEnrollment(!customer.isEnrolledInLoyalty) },
                        label = {
                            Text(
                                if (customer.isEnrolledInLoyalty) "Enrolled" else "Off",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SuccessGreen.copy(alpha = 0.2f),
                            selectedLabelColor = SuccessGreen
                        )
                    )

                    Surface(
                        color = GoldAccent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$totalPoints Pts",
                            color = GoldAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visit Progression bar
            val currentVisits = customer.loyaltyVisitCount
            val targetVisits = 10
            val progress = (currentVisits.toFloat() / targetVisits.toFloat()).coerceIn(0f, 1f)

            Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Visit Progress: Visit #$currentVisits",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isExpired) "Cycle Expired" else if (customer.loyaltyExpiryDate != null) "Expires ${DateUtils.formatDate(customer.loyaltyExpiryDate!!, "dd MMM yyyy")}" else "Active",
                        fontSize = 11.sp,
                        color = if (isExpired) MaterialTheme.colorScheme.error else SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = GoldAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Points breakdown: Reward vs Gift
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Reward: ${customer.rewardPointsBalance} pts (50% max)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Gift: ${customer.giftPointsBalance} pts (100% max)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!customer.isEnrolledInLoyalty) {
                    Button(
                        onClick = onReEnableCycle,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Re-Enable Cycle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onGrantGiftPoints,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Gift Pts", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onExtendValidity,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+45 Days", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onResetCycle,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                FilledTonalButton(
                    onClick = onViewHistory,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text("History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onShareWhatsApp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share on WhatsApp",
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HighContrastPill(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String? = null,
    subtitle: String? = null,
    isFullWidth: Boolean = false
) {
    val activeBg = Color(0xFFE65100) // Vibrant Deep Orange
    val activeContent = Color.White
    val inactiveBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val inactiveContent = MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeBg else inactiveBg,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) activeBg else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .then(if (isFullWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = activeContent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = inactiveContent.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (emoji != null) {
                Text(emoji, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(horizontalAlignment = if (isFullWidth) Alignment.Start else Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (selected) activeContent else inactiveContent,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = if (selected) activeContent.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferFormDialog(
    restaurantId: String,
    offer: OfferEntity?,
    defaultOfferType: String,
    menuItems: List<MenuItemEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (OfferEntity) -> Unit
) {
    var name by remember { mutableStateOf(offer?.name ?: "") }
    var offerType by remember { mutableStateOf(offer?.offerType ?: defaultOfferType) }
    var visitNumberInput by remember { mutableStateOf(offer?.visitNumber?.toString() ?: "3") }
    var rewardType by remember { mutableStateOf(offer?.rewardType ?: "FREE_ITEM") }
    var rewardValueInput by remember { mutableStateOf(offer?.rewardValue?.toString() ?: "50") }
    var maxDiscountCapInput by remember { mutableStateOf(offer?.maxDiscountCap?.toString() ?: "") }
    var freeQuantityInput by remember { mutableStateOf(offer?.freeItemQuantity?.toString() ?: "1") }
    var minBillInput by remember { mutableStateOf(offer?.minBillAmount?.toString() ?: "249") }
    var applicableOrderTypes by remember { mutableStateOf(offer?.applicableOrderTypes ?: "DINE_IN,TAKEAWAY") }
    var validityDaysInput by remember { mutableStateOf(offer?.validityDays?.toString() ?: "45") }
    var isActive by remember { mutableStateOf(offer?.isActive ?: true) }

    var cooldownDaysSelection by remember {
        mutableStateOf(
            when (offer?.cooldownDays) {
                null, 0 -> 0 // Unlimited / Every visit
                1 -> 1 // Once per day
                7 -> 7 // Weekly
                30 -> 30 // Every 30 days
                60 -> 60 // Every 60 days
                -1 -> -1 // Lifetime once
                else -> 999 // Custom days
            }
        )
    }
    var customCooldownInput by remember {
        mutableStateOf(
            if (offer != null && offer.cooldownDays !in listOf(0, 1, 7, 30, 60, -1)) offer.cooldownDays.toString() else "15"
        )
    }

    // Eligible menu items selection
    val initialSelectedIds = remember(offer) {
        val json = offer?.eligibleMenuItemIds
        if (json.isNullOrBlank()) emptySet<String>()
        else {
            try {
                val array = JSONArray(json)
                (0 until array.length()).map { array.getString(it) }.toSet()
            } catch (e: Exception) {
                json.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        }
    }
    var selectedItemIds by remember { mutableStateOf(initialSelectedIds) }
    var itemSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (offer == null) "Create New Offer" else "Edit Offer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (offerType == "VISIT_BASED") "Milestone pass program for customer loyalty" else "Direct promo offer with frequency limits",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LIVE PREVIEW CARD
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE65100).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Live Offer Preview",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE65100)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val minBillText = if ((minBillInput.toDoubleOrNull() ?: 0.0) > 0) "on min bill of ₹${minBillInput}" else "on any bill amount"
                            val rewardSummary = when (rewardType) {
                                "FREE_ITEM" -> {
                                    val count = freeQuantityInput.toIntOrNull() ?: 1
                                    if (selectedItemIds.isEmpty()) "$count Free Item (Any Dish Allowed)"
                                    else "$count Free Item (${selectedItemIds.size} Specific Dishes)"
                                }
                                "FLAT_DISCOUNT" -> "Flat ₹${rewardValueInput.ifBlank { "0" }} Off"
                                "PERCENT_DISCOUNT" -> {
                                    val cap = maxDiscountCapInput.toDoubleOrNull()
                                    if (cap != null && cap > 0) "${rewardValueInput.ifBlank { "0" }}% Off (Up to ₹${cap.toInt()})"
                                    else "${rewardValueInput.ifBlank { "0" }}% Off"
                                }
                                else -> "Reward"
                            }
                            val orderTypeSummary = when (applicableOrderTypes) {
                                "DINE_IN" -> "Dine-In only"
                                "TAKEAWAY" -> "Takeaway only"
                                else -> "All orders (Dine-in & Takeaway)"
                            }
                            val cooldownSummary = if (offerType == "VISIT_BASED") {
                                "Applicable on Visit #${visitNumberInput}"
                            } else {
                                when (cooldownDaysSelection) {
                                    0 -> "Re-claim every visit"
                                    1 -> "Re-claim once daily"
                                    7 -> "Re-claim once every 7 days"
                                    30 -> "Re-claim once every 30 days"
                                    60 -> "Re-claim once every 60 days"
                                    -1 -> "One-time lifetime per customer"
                                    else -> "Re-claim once every ${customCooldownInput.ifBlank { "0" }} days"
                                }
                            }
                            Text(
                                text = "👉 $rewardSummary $minBillText • $orderTypeSummary • $cooldownSummary.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 1. BASIC DETAILS CARD
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "🏷️ Basic Offer Information",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Offer Name *") },
                                placeholder = { Text("e.g. 1st Visit Free Pizza on ₹249+ Bill") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Offer Program Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HighContrastPill(
                                    selected = offerType == "VISIT_BASED",
                                    onClick = { offerType = "VISIT_BASED" },
                                    title = "Visit Milestone",
                                    subtitle = "e.g. 3rd or 5th visit reward",
                                    emoji = "🎫",
                                    modifier = Modifier.weight(1f)
                                )
                                HighContrastPill(
                                    selected = offerType == "MANUAL",
                                    onClick = { offerType = "MANUAL" },
                                    title = "Direct Promo Offer",
                                    subtitle = "Cooldown-limited offer",
                                    emoji = "⚡",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (offerType == "VISIT_BASED") {
                                OutlinedTextField(
                                    value = visitNumberInput,
                                    onValueChange = { visitNumberInput = it },
                                    label = { Text("Target Visit Number (e.g. 1, 3, 5, 10) *") },
                                    placeholder = { Text("1 for 1st visit, 3 for 3rd visit...") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("1", "3", "5", "10").forEach { v ->
                                        HighContrastPill(
                                            selected = visitNumberInput == v,
                                            onClick = { visitNumberInput = v },
                                            title = "Visit #$v",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = minBillInput,
                                onValueChange = { minBillInput = it },
                                label = { Text("Minimum Qualifying Bill Amount (₹) *") },
                                placeholder = { Text("e.g. 249 (0 for no minimum)") },
                                leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Applicable Order Types", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HighContrastPill(
                                    selected = applicableOrderTypes == "DINE_IN,TAKEAWAY",
                                    onClick = { applicableOrderTypes = "DINE_IN,TAKEAWAY" },
                                    title = "All Orders",
                                    subtitle = "Dine-In & Parcel",
                                    modifier = Modifier.weight(1f)
                                )
                                HighContrastPill(
                                    selected = applicableOrderTypes == "DINE_IN",
                                    onClick = { applicableOrderTypes = "DINE_IN" },
                                    title = "Dine-In Only",
                                    subtitle = "Table dining",
                                    modifier = Modifier.weight(1f)
                                )
                                HighContrastPill(
                                    selected = applicableOrderTypes == "TAKEAWAY",
                                    onClick = { applicableOrderTypes = "TAKEAWAY" },
                                    title = "Takeaway Only",
                                    subtitle = "Parcel / Delivery",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 2. REWARD & DISCOUNTS CARD
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "🎁 Reward & Discount Setup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text("Reward Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HighContrastPill(
                                    selected = rewardType == "FREE_ITEM",
                                    onClick = { rewardType = "FREE_ITEM" },
                                    title = "Free Item",
                                    emoji = "🎁",
                                    modifier = Modifier.weight(1f)
                                )
                                HighContrastPill(
                                    selected = rewardType == "FLAT_DISCOUNT",
                                    onClick = { rewardType = "FLAT_DISCOUNT" },
                                    title = "Flat ₹ Off",
                                    emoji = "🏷️",
                                    modifier = Modifier.weight(1f)
                                )
                                HighContrastPill(
                                    selected = rewardType == "PERCENT_DISCOUNT",
                                    onClick = { rewardType = "PERCENT_DISCOUNT" },
                                    title = "% Off",
                                    emoji = "📊",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            when (rewardType) {
                                "FREE_ITEM" -> {
                                    OutlinedTextField(
                                        value = freeQuantityInput,
                                        onValueChange = { freeQuantityInput = it },
                                        label = { Text("Free Item Quantity (e.g. 1)") },
                                        placeholder = { Text("1") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Menu item selector header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (selectedItemIds.isEmpty()) "Eligible Dishes: All Menu Items Allowed" else "Eligible Dishes: ${selectedItemIds.size} Selected",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFFE65100)
                                        )
                                    }

                                    // Category Filter Pills
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HighContrastPill(
                                            selected = selectedCategoryId == null,
                                            onClick = { selectedCategoryId = null },
                                            title = "All Dishes"
                                        )
                                        categories.forEach { cat ->
                                            HighContrastPill(
                                                selected = selectedCategoryId == cat.id,
                                                onClick = {
                                                    selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id
                                                },
                                                title = cat.name
                                            )
                                        }
                                    }

                                    // Search Dish Bar
                                    OutlinedTextField(
                                        value = itemSearchQuery,
                                        onValueChange = { itemSearchQuery = it },
                                        placeholder = { Text("Search pizza, burger, beverage...") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        trailingIcon = {
                                            if (itemSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { itemSearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    val filteredDishes = remember(menuItems, itemSearchQuery, selectedCategoryId) {
                                        menuItems.filter { item ->
                                            val matchesCat = selectedCategoryId == null || item.categoryId == selectedCategoryId
                                            val matchesQuery = itemSearchQuery.isBlank() || item.name.contains(itemSearchQuery, ignoreCase = true)
                                            matchesCat && matchesQuery
                                        }
                                    }

                                    // Dish Checkbox List
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 120.dp, max = 220.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${filteredDishes.size} dishes found",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                TextButton(
                                                    onClick = {
                                                        val filteredIds = filteredDishes.map { it.id }.toSet()
                                                        selectedItemIds = if (selectedItemIds.containsAll(filteredIds)) {
                                                            selectedItemIds - filteredIds
                                                        } else {
                                                            selectedItemIds + filteredIds
                                                        }
                                                    }
                                                ) {
                                                    val filteredIds = filteredDishes.map { it.id }.toSet()
                                                    Text(
                                                        if (selectedItemIds.containsAll(filteredIds) && filteredIds.isNotEmpty()) "Deselect Filtered" else "Select All Filtered",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFE65100)
                                                    )
                                                }
                                            }

                                            if (filteredDishes.isEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("No dishes match search/category", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                                }
                                            }

                                            filteredDishes.forEach { dish ->
                                                val isDishSelected = dish.id in selectedItemIds
                                                Surface(
                                                    onClick = {
                                                        selectedItemIds = if (isDishSelected) selectedItemIds - dish.id
                                                        else selectedItemIds + dish.id
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isDishSelected) Color(0xFFE65100).copy(alpha = 0.12f) else Color.Transparent,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isDishSelected,
                                                            onCheckedChange = {
                                                                selectedItemIds = if (isDishSelected) selectedItemIds - dish.id
                                                                else selectedItemIds + dish.id
                                                            },
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = Color(0xFFE65100)
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                dish.name,
                                                                fontWeight = if (isDishSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                "₹${dish.price.toInt()}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = if (isDishSelected) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                "FLAT_DISCOUNT" -> {
                                    OutlinedTextField(
                                        value = rewardValueInput,
                                        onValueChange = { rewardValueInput = it },
                                        label = { Text("Flat Discount Amount (₹) *") },
                                        placeholder = { Text("e.g. 50") },
                                        leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                "PERCENT_DISCOUNT" -> {
                                    OutlinedTextField(
                                        value = rewardValueInput,
                                        onValueChange = { rewardValueInput = it },
                                        label = { Text("Discount Percentage (%) *") },
                                        placeholder = { Text("e.g. 15") },
                                        leadingIcon = { Text("%", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = maxDiscountCapInput,
                                        onValueChange = { maxDiscountCapInput = it },
                                        label = { Text("Max Discount Limit (₹) [Optional]") },
                                        placeholder = { Text("e.g. 100 (leave empty for unlimited)") },
                                        leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // 3. CUSTOMER COOLDOWN & FREQUENCY LIMITS CARD
                    if (offerType == "MANUAL") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "⏳ Customer Re-claim Frequency / Cooldown",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Control how often the same customer phone number can re-claim this reward.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Row 1 Cooldown Options
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HighContrastPill(
                                        selected = cooldownDaysSelection == 0,
                                        onClick = { cooldownDaysSelection = 0 },
                                        title = "Every Visit",
                                        subtitle = "Unlimited",
                                        emoji = "⚡",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HighContrastPill(
                                        selected = cooldownDaysSelection == 1,
                                        onClick = { cooldownDaysSelection = 1 },
                                        title = "Once / Day",
                                        subtitle = "Daily (1d)",
                                        emoji = "📅",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HighContrastPill(
                                        selected = cooldownDaysSelection == 7,
                                        onClick = { cooldownDaysSelection = 7 },
                                        title = "Weekly",
                                        subtitle = "Every 7 days",
                                        emoji = "🗓️",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Row 2 Cooldown Options
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HighContrastPill(
                                        selected = cooldownDaysSelection == 30,
                                        onClick = { cooldownDaysSelection = 30 },
                                        title = "30 Days",
                                        subtitle = "Monthly (30d)",
                                        emoji = "🗓️",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HighContrastPill(
                                        selected = cooldownDaysSelection == 60,
                                        onClick = { cooldownDaysSelection = 60 },
                                        title = "60 Days",
                                        subtitle = "2 Months (60d)",
                                        emoji = "🗓️",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HighContrastPill(
                                        selected = cooldownDaysSelection == -1,
                                        onClick = { cooldownDaysSelection = -1 },
                                        title = "Lifetime",
                                        subtitle = "1-Time Only",
                                        emoji = "🔒",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Custom Days Pill
                                HighContrastPill(
                                    selected = cooldownDaysSelection == 999,
                                    onClick = { cooldownDaysSelection = 999 },
                                    title = "✏️ Custom Days (Enter Manually)",
                                    subtitle = if (cooldownDaysSelection == 999) "Custom period: ${customCooldownInput.ifBlank { "0" }} days" else "Set your own custom cooldown (e.g. 15, 45, 90 days)",
                                    isFullWidth = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (cooldownDaysSelection == 999) {
                                    OutlinedTextField(
                                        value = customCooldownInput,
                                        onValueChange = { customCooldownInput = it },
                                        label = { Text("Enter Custom Cooldown Period (Days) *") },
                                        placeholder = { Text("e.g. 15, 45, 90, 180") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Explanatory Hint Banner
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = when (cooldownDaysSelection) {
                                            0 -> "🟢 Customer can claim this offer on every single visit."
                                            1 -> "📅 Customer can claim this offer maximum once per day."
                                            7 -> "🗓️ Customer can claim this offer maximum once every 7 days."
                                            30 -> "🗓️ Customer can re-claim this offer once every 30 days."
                                            60 -> "🗓️ Customer can re-claim this offer once every 60 days."
                                            -1 -> "🔒 Strict limit: Customer can only claim this offer ONCE per mobile number in their lifetime."
                                            else -> "✏️ Customer can only re-claim this offer after ${customCooldownInput.ifBlank { "0" }} days."
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4. VALIDITY & STATUS CARD
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "⚙️ Campaign Validity & Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = validityDaysInput,
                                onValueChange = { validityDaysInput = it },
                                label = { Text("Campaign Validity Cycle (Days)") },
                                placeholder = { Text("e.g. 45") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Active Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        if (isActive) "🟢 Active & Available at Billing" else "⚪ Inactive / Paused",
                                        fontSize = 12.sp,
                                        color = if (isActive) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isActive,
                                    onCheckedChange = { isActive = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFE65100)
                                    )
                                )
                            }
                        }
                    }
                }

                // Sticky Bottom Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                if (name.isBlank()) return@Button
                                val rewardVal = rewardValueInput.toDoubleOrNull() ?: 0.0
                                val maxCapVal = maxDiscountCapInput.toDoubleOrNull()
                                val minBill = minBillInput.toDoubleOrNull() ?: 0.0
                                val visitNum = visitNumberInput.toIntOrNull() ?: 1
                                val freeQty = freeQuantityInput.toIntOrNull() ?: 1

                                val eligibleIdsJson = if (rewardType == "FREE_ITEM" && selectedItemIds.isNotEmpty()) {
                                    JSONArray(selectedItemIds).toString()
                                } else ""

                                val resolvedCooldown = when (cooldownDaysSelection) {
                                    0 -> 0
                                    1 -> 1
                                    7 -> 7
                                    30 -> 30
                                    60 -> 60
                                    -1 -> -1
                                    else -> customCooldownInput.toIntOrNull() ?: 15
                                }

                                val newOffer = OfferEntity(
                                    id = offer?.id ?: UUID.randomUUID().toString(),
                                    restaurantId = restaurantId,
                                    name = name.trim(),
                                    offerType = offerType,
                                    visitNumber = if (offerType == "VISIT_BASED") visitNum else 1,
                                    rewardType = rewardType,
                                    rewardValue = rewardVal,
                                    maxDiscountCap = if (rewardType == "PERCENT_DISCOUNT") maxCapVal else null,
                                    freeItemQuantity = freeQty,
                                    minBillAmount = minBill,
                                    applicableOrderTypes = applicableOrderTypes,
                                    validityDays = validityDaysInput.toIntOrNull() ?: 45,
                                    eligibleMenuItemIds = eligibleIdsJson,
                                    isActive = isActive,
                                    cooldownDays = if (offerType == "MANUAL") resolvedCooldown else 0,
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(newOffer)
                            },
                            enabled = name.isNotBlank(),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE65100),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (offer == null) "Save & Create Offer" else "Save Changes",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantGiftPointsDialog(
    allCustomers: List<CustomerEntity>,
    initialCustomer: CustomerEntity?,
    onDismiss: () -> Unit,
    onGrant: (customerId: String, points: Int, notes: String, expiryDays: Int) -> Unit
) {
    var selectedCustomer by remember { mutableStateOf(initialCustomer) }
    var pointsInput by remember { mutableStateOf("100") }
    var expiryDaysInput by remember { mutableStateOf("60") }
    var notesInput by remember { mutableStateOf("Goodwill / Loyalty Gift") }
    var customerSearch by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grant Gift Points", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Gift points can be redeemed up to 100% of a bill. You can customize the validity period in days below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedCustomer == null) {
                    OutlinedTextField(
                        value = customerSearch,
                        onValueChange = { customerSearch = it },
                        label = { Text("Search Customer") },
                        placeholder = { Text("Type name or phone...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val matches = remember(customerSearch, allCustomers) {
                        val filtered = if (customerSearch.length >= 2) {
                            allCustomers.filter {
                                it.name.contains(customerSearch, ignoreCase = true) || it.contactNumber.contains(customerSearch)
                            }
                        } else allCustomers.take(10)

                        filtered.sortedBy { it.name.trim().lowercase() }.take(10)
                    }

                    matches.forEach { cust ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCustomer = cust }
                        ) {
                            Text(
                                "${cust.name} (${cust.contactNumber})",
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GoldAccent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, GoldAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(selectedCustomer!!.name, fontWeight = FontWeight.Bold)
                                Text(selectedCustomer!!.contactNumber, fontSize = 12.sp)
                            }
                            TextButton(onClick = { selectedCustomer = null }) {
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = pointsInput,
                    onValueChange = { pointsInput = it },
                    label = { Text("Points to Award (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = expiryDaysInput,
                    onValueChange = { expiryDaysInput = it },
                    label = { Text("Validity Period (Days) *") },
                    placeholder = { Text("e.g. 30, 60, 90") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Note / Reason") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cust = selectedCustomer ?: return@Button
                    val pts = pointsInput.toIntOrNull() ?: 0
                    val days = expiryDaysInput.toIntOrNull() ?: 60
                    if (pts > 0) {
                        onGrant(cust.id, pts, notesInput.trim(), days)
                    }
                },
                enabled = selectedCustomer != null && (pointsInput.toIntOrNull() ?: 0) > 0 && (expiryDaysInput.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark)
            ) {
                Text("Grant Points", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BulkGrantGiftPointsDialog(
    selectedCustomers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onGrantBulk: (customerIds: List<String>, points: Int, notes: String, expiryDays: Int, shareWhatsApp: Boolean) -> Unit
) {
    var pointsInput by remember { mutableStateOf("100") }
    var expiryDaysInput by remember { mutableStateOf("60") }
    var notesInput by remember { mutableStateOf("Festival Special Gift Points") }
    var shareOnWhatsApp by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = GoldAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🎁 Bulk Gift Points (${selectedCustomers.size} Customers)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You are granting gift points to ${selectedCustomers.size} selected customer(s) at once. Gift points can be used to pay up to 100% of future bills with custom validity in days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Quick preset points buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("50", "100", "200", "500").forEach { preset ->
                        FilterChip(
                            selected = pointsInput == preset,
                            onClick = { pointsInput = preset },
                            label = { Text("₹$preset", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = pointsInput,
                    onValueChange = { pointsInput = it },
                    label = { Text("Points per Customer (₹) *") },
                    placeholder = { Text("e.g. 100") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = expiryDaysInput,
                    onValueChange = { expiryDaysInput = it },
                    label = { Text("Validity Period (Days) *") },
                    placeholder = { Text("e.g. 30, 60, 90") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Campaign / Occasion Note") },
                    placeholder = { Text("e.g. Diwali Dhamaka, VIP Bonus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // List preview of first 5 selected customers
                Text(
                    "Recipient List Preview (${selectedCustomers.size} total):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        selectedCustomers.sortedBy { it.name.trim().lowercase() }.take(5).forEach { cust ->
                            Text("• ${cust.name} (${cust.contactNumber})", fontSize = 12.sp)
                        }
                        if (selectedCustomers.size > 5) {
                            Text("... and ${selectedCustomers.size - 5} more customers", fontSize = 11.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pts = pointsInput.toIntOrNull() ?: 0
                    val days = expiryDaysInput.toIntOrNull() ?: 60
                    if (pts > 0 && selectedCustomers.isNotEmpty()) {
                        onGrantBulk(selectedCustomers.map { it.id }, pts, notesInput.trim(), days, shareOnWhatsApp)
                    }
                },
                enabled = (pointsInput.toIntOrNull() ?: 0) > 0 && (expiryDaysInput.toIntOrNull() ?: 0) > 0 && selectedCustomers.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark)
            ) {
                Text("Award Points to ${selectedCustomers.size} Customers", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CustomerLoyaltyHistoryDialog(
    customer: CustomerEntity,
    historyItems: List<LoyaltyHistoryItem>,
    ledgerItems: List<PointsLedgerEntity>,
    allBills: List<BillEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var historyTab by remember { mutableStateOf(0) } // 0 = Visits, 1 = Points Ledger
    var selectedBillForDetails by remember { mutableStateOf<BillEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${customer.name} - Loyalty History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(customer.contactNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = historyTab == 0,
                        onClick = { historyTab = 0 },
                        label = { Text("Visits (${historyItems.size})") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = historyTab == 1,
                        onClick = { historyTab = 1 },
                        label = { Text("Points Ledger (${ledgerItems.size})") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (historyTab == 0) {
                    if (historyItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No visit progression events recorded", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(historyItems.reversed()) { item ->
                                val matchedBill = remember(item, allBills) {
                                    allBills.find {
                                        (item.billId != null && it.id == item.billId) ||
                                        (item.billNumber != null && it.billNumber == item.billNumber)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    onClick = {
                                        if (matchedBill != null) selectedBillForDetails = matchedBill
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = when (item.eventType) {
                                                    "VISIT_COMPLETED" -> "Visit #${item.visitNumber}"
                                                    "CYCLE_RESET" -> "Cycle Reset"
                                                    "CYCLE_EXTENDED" -> "Validity Extended"
                                                    "VISIT_REVERSED" -> "Visit Reversed"
                                                    else -> item.eventType
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                DateUtils.formatDate(item.timestamp, "dd MMM yyyy, hh:mm a"),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        if (item.rewardGiven != null) {
                                            Text("🎁 Reward: ${item.rewardGiven}", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.billNumber != null) {
                                                Text("Bill: #${item.billNumber} • ₹${item.billAmount.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                            if (matchedBill != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        "📄 View Bill ➔",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (ledgerItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No points transactions recorded", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ledgerItems) { item ->
                                val matchedBill = remember(item, allBills) {
                                    allBills.find {
                                        (item.billId != null && it.id == item.billId) ||
                                        (item.billNumber != null && it.billNumber == item.billNumber) ||
                                        (item.notes.isNotBlank() && item.notes.contains(it.billNumber))
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    onClick = {
                                        if (matchedBill != null) selectedBillForDetails = matchedBill
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.transactionType + " (${item.balanceType})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = item.notes.ifBlank { DateUtils.formatDate(item.timestamp, "dd MMM yyyy") },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            if (matchedBill != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = "📄 View Bill #${matchedBill.billNumber} ➔",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = if (item.pointsAmount > 0) "+${item.pointsAmount}" else "${item.pointsAmount}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = if (item.pointsAmount > 0) SuccessGreen else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (selectedBillForDetails != null) {
        BillDetailsDialog(
            bill = selectedBillForDetails!!,
            viewModel = viewModel,
            onDismiss = { selectedBillForDetails = null },
            onEdit = {}
        )
    }
}

@Composable
private fun EditPointsEngineRulesDialog(
    currentRules: PointsEngineRules,
    onDismiss: () -> Unit,
    onSave: (PointsEngineRules) -> Unit
) {
    var earnRateInput by remember { mutableStateOf(currentRules.earnRatePercent.toString()) }
    var pointValueInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentRules.pointValueRupees)) }
    var minRedemptionInput by remember { mutableStateOf(currentRules.minRedemptionPoints.toString()) }
    var maxCapPercentInput by remember { mutableStateOf(currentRules.maxDiscountCapPercent.toInt().toString()) }
    var rewardExpiryDaysInput by remember { mutableStateOf(currentRules.rewardPointsExpiryDays.toString()) }
    var giftExpiryDaysInput by remember { mutableStateOf(currentRules.giftPointsExpiryDays.toString()) }
    var welcomeBonusEnabled by remember { mutableStateOf(currentRules.welcomeBonusEnabled) }
    var welcomeBonusPointsInput by remember { mutableStateOf(currentRules.welcomeBonusPoints.toString()) }
    var welcomeBonusExpiryDaysInput by remember { mutableStateOf(currentRules.welcomeBonusExpiryDays.toString()) }
    var minBillAmountInput by remember { mutableStateOf(currentRules.minBillAmountToEarn.toInt().toString()) }
    var autoEnrollInVisitPass by remember { mutableStateOf(currentRules.autoEnrollInVisitPass) }

    val currentEarnRate = earnRateInput.toDoubleOrNull() ?: currentRules.earnRatePercent
    val currentPointVal = pointValueInput.toDoubleOrNull() ?: currentRules.pointValueRupees
    val currentCap = maxCapPercentInput.toDoubleOrNull() ?: currentRules.maxDiscountCapPercent

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Points Engine Rules", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Configure how customer loyalty points are earned on bills, converted to rupees, capped, and expired.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )

                // Quick Campaign Presets
                Text("Quick Preset Templates:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepAmber)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            val def = PointsEngineRules.DEFAULT
                            earnRateInput = def.earnRatePercent.toString()
                            pointValueInput = String.format(Locale.US, "%.2f", def.pointValueRupees)
                            minRedemptionInput = def.minRedemptionPoints.toString()
                            maxCapPercentInput = def.maxDiscountCapPercent.toInt().toString()
                            rewardExpiryDaysInput = def.rewardPointsExpiryDays.toString()
                            giftExpiryDaysInput = def.giftPointsExpiryDays.toString()
                            welcomeBonusEnabled = def.welcomeBonusEnabled
                            welcomeBonusPointsInput = def.welcomeBonusPoints.toString()
                            minBillAmountInput = def.minBillAmountToEarn.toInt().toString()
                        },
                        label = { Text("🟢 1% Standard", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                    SuggestionChip(
                        onClick = {
                            val f2 = PointsEngineRules.FESTIVE_2X
                            earnRateInput = f2.earnRatePercent.toString()
                            pointValueInput = String.format(Locale.US, "%.2f", f2.pointValueRupees)
                            minRedemptionInput = f2.minRedemptionPoints.toString()
                            maxCapPercentInput = f2.maxDiscountCapPercent.toInt().toString()
                            rewardExpiryDaysInput = f2.rewardPointsExpiryDays.toString()
                            giftExpiryDaysInput = f2.giftPointsExpiryDays.toString()
                            welcomeBonusEnabled = f2.welcomeBonusEnabled
                            welcomeBonusPointsInput = f2.welcomeBonusPoints.toString()
                            minBillAmountInput = f2.minBillAmountToEarn.toInt().toString()
                        },
                        label = { Text("🟡 2X Festive", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                    SuggestionChip(
                        onClick = {
                            val vip = PointsEngineRules.VIP_MEGA
                            earnRateInput = vip.earnRatePercent.toString()
                            pointValueInput = String.format(Locale.US, "%.2f", vip.pointValueRupees)
                            minRedemptionInput = vip.minRedemptionPoints.toString()
                            maxCapPercentInput = vip.maxDiscountCapPercent.toInt().toString()
                            rewardExpiryDaysInput = vip.rewardPointsExpiryDays.toString()
                            giftExpiryDaysInput = vip.giftPointsExpiryDays.toString()
                            welcomeBonusEnabled = vip.welcomeBonusEnabled
                            welcomeBonusPointsInput = vip.welcomeBonusPoints.toString()
                            minBillAmountInput = vip.minBillAmountToEarn.toInt().toString()
                        },
                        label = { Text("🔴 VIP 5%", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CrispCardBorder)
                )

                // 1. Earning Rules
                Text("1. Point Earning Rules", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarmAmber)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = earnRateInput,
                        onValueChange = { earnRateInput = it },
                        label = { Text("Earn Rate (%)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minBillAmountInput,
                        onValueChange = { minBillAmountInput = it },
                        label = { Text("Min Bill (₹)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Customer earns 1 point for every ₹${if (currentEarnRate > 0) String.format(Locale.US, "%.0f", 100.0 / currentEarnRate) else "0"} spent on eligible bills.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )

                // 2. Valuation & Redemption Caps
                Text("2. Valuation & Redemption Cap", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarmAmber)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pointValueInput,
                        onValueChange = { pointValueInput = it },
                        label = { Text("1 Point = ₹", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxCapPercentInput,
                        onValueChange = { maxCapPercentInput = it },
                        label = { Text("Max Bill Cap (%)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = minRedemptionInput,
                    onValueChange = { minRedemptionInput = it },
                    label = { Text("Min Points Required to Redeem", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. FIFO Expiry (Days)
                Text("3. Points Expiry (FIFO Days)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarmAmber)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rewardExpiryDaysInput,
                        onValueChange = { rewardExpiryDaysInput = it },
                        label = { Text("Reward Expiry (Days)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = giftExpiryDaysInput,
                        onValueChange = { giftExpiryDaysInput = it },
                        label = { Text("Gift Expiry (Days)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // 4. Welcome Bonus
                Text("4. New Customer Registration Bonus", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarmAmber)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Welcome Bonus", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Gift points credited on first registration", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Switch(
                        checked = welcomeBonusEnabled,
                        onCheckedChange = { welcomeBonusEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarmAmber,
                            checkedTrackColor = WarmAmber.copy(alpha = 0.3f)
                        )
                    )
                }

                if (welcomeBonusEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = welcomeBonusPointsInput,
                            onValueChange = { welcomeBonusPointsInput = it },
                            label = { Text("Welcome Points", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = welcomeBonusExpiryDaysInput,
                            onValueChange = { welcomeBonusExpiryDaysInput = it },
                            label = { Text("Validity (Days)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 5. Visit Pass Auto-Enroll
                Text("5. Visit Pass Enrollment Default", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarmAmber)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Enroll in Visit Pass", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (autoEnrollInVisitPass) "ON: All new customers enrolled" else "OFF: Pass is off by default for new customers", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Switch(
                        checked = autoEnrollInVisitPass,
                        onCheckedChange = { autoEnrollInVisitPass = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarmAmber,
                            checkedTrackColor = WarmAmber.copy(alpha = 0.3f)
                        )
                    )
                }

                // Live Preview Card
                val simBill = 500.0
                val simEarned = (simBill * (currentEarnRate / 100.0)).toInt()
                val simVal = simEarned * currentPointVal
                val simCapVal = (simBill * (currentCap / 100.0)).toInt()

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("💡 Live Preview on ₹500 Bill:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DeepAmber)
                        Text(
                            "• Earned: $simEarned Points (Value: ₹${String.format(Locale.US, "%.2f", simVal)})\n• Max points discount allowable: ₹$simCapVal.00 (${currentCap.toInt()}% Cap)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = earnRateInput.toDoubleOrNull() ?: currentRules.earnRatePercent
                    val ptVal = pointValueInput.toDoubleOrNull() ?: currentRules.pointValueRupees
                    val minRedeem = minRedemptionInput.toIntOrNull() ?: currentRules.minRedemptionPoints
                    val cap = maxCapPercentInput.toDoubleOrNull() ?: currentRules.maxDiscountCapPercent
                    val rewExp = rewardExpiryDaysInput.toIntOrNull() ?: currentRules.rewardPointsExpiryDays
                    val giftExp = giftExpiryDaysInput.toIntOrNull() ?: currentRules.giftPointsExpiryDays
                    val welPts = welcomeBonusPointsInput.toIntOrNull() ?: currentRules.welcomeBonusPoints
                    val welExp = welcomeBonusExpiryDaysInput.toIntOrNull() ?: currentRules.welcomeBonusExpiryDays
                    val minBill = minBillAmountInput.toDoubleOrNull() ?: currentRules.minBillAmountToEarn

                    val updated = PointsEngineRules(
                        earnRatePercent = rate.coerceAtLeast(0.0),
                        pointValueRupees = ptVal.coerceAtLeast(0.01),
                        minRedemptionPoints = minRedeem.coerceAtLeast(1),
                        maxDiscountCapPercent = cap.coerceIn(1.0, 100.0),
                        rewardPointsExpiryDays = rewExp.coerceAtLeast(1),
                        giftPointsExpiryDays = giftExp.coerceAtLeast(1),
                        welcomeBonusEnabled = welcomeBonusEnabled,
                        welcomeBonusPoints = welPts.coerceAtLeast(0),
                        minBillAmountToEarn = minBill.coerceAtLeast(0.0),
                        autoEnrollInVisitPass = autoEnrollInVisitPass,
                        welcomeBonusExpiryDays = welExp.coerceAtLeast(1)
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmber, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save & Apply Rules", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Cancel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

