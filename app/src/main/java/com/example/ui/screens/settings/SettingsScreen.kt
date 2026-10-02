package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import coil.compose.AsyncImage
import com.example.data.cloud.SyncState
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.BillItem
import com.example.data.local.entity.RestaurantEntity
import com.example.ui.MainViewModel
import com.example.ui.components.BillPreviewDialog
import com.example.ui.components.BillTemplateDesign
import com.example.ui.components.UniversalCafeProReceipt
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.theme.*
import com.example.util.ApkExportHelper
import java.util.*

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    SHOP_DETAILS(
        "Shop / Branch Details",
        "Manage shop name, address, contact, FSSAI & GST",
        Icons.Default.Storefront
    ),
    REWARD_POINTS(
        "Reward & Gift Points",
        "1% earning rule, 1 Pt = ₹1 redemption, gift points & liabilities",
        Icons.Default.MonetizationOn
    ),
    VISIT_REWARDS(
        "Visit Rewards & Pass Campaigns",
        "Foodie Club Pass, visit milestones, free items & flat ₹ discounts",
        Icons.Default.Loyalty
    ),
    MANUAL_OFFERS(
        "Promotional Offers",
        "Custom discount coupons, manual promo deals & seasonal offers",
        Icons.Default.LocalOffer
    ),
    CUSTOMER_LOYALTY(
        "Customer Directory & Pass Enrollment",
        "Manage enrolled customers, pass status & points history",
        Icons.Default.People
    ),
    BILLING_FORMAT(
        "Billing & Receipt Format",
        "Configure A4, 2-inch or 3-inch thermal bill format & preview",
        Icons.Default.ReceiptLong
    ),
    CLOUD_SYNC(
        "Cloud Sync & Data Restore",
        "Firestore cloud backup & live database statistics",
        Icons.Default.CloudSync
    ),
    AUTH_SECURITY(
        "Authentication & Security",
        "Google account, permanent UID & session security",
        Icons.Default.Security
    ),
    MASTER_REPORTS(
        "Master Business Reports & Analytics Hub",
        "Comprehensive Sales, Product & Party, Wastage, GST, P&L Reports",
        Icons.Default.Insights
    ),
    APP_VERSION_EXPORT(
        "App Version & APK Export",
        "v9.4 Release • Bulk Stock Fix & Clean Excel Reports",
        Icons.Default.Android
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activeRestaurant by viewModel.activeRestaurant.collectAsState()
    val allRestaurants by viewModel.allRestaurants.collectAsState()
    val userState by viewModel.userState.collectAsState()
    val syncState by viewModel.syncManager.syncState.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    // Live Database Record Counts
    val billsCount by viewModel.liveBillsCount.collectAsState()
    val customersCount by viewModel.liveCustomersCount.collectAsState()
    val inventoryCount by viewModel.liveInventoryCount.collectAsState()
    val expensesCount by viewModel.liveExpensesCount.collectAsState()
    val menuItemsCount by viewModel.liveMenuItemsCount.collectAsState()
    val categoriesCount by viewModel.liveCategoriesCount.collectAsState()
    val tablesCount by viewModel.liveTablesCount.collectAsState()
    val registersCount by viewModel.liveRegistersCount.collectAsState()

    var activeSubScreen by remember { mutableStateOf<SettingsCategory?>(null) }
    var showAddRestaurantDialog by remember { mutableStateOf(false) }
    var restaurantToEdit by remember { mutableStateOf<RestaurantEntity?>(null) }
    var restaurantToDelete by remember { mutableStateOf<RestaurantEntity?>(null) }

    val isDark = isDarkTheme == true
    val headerContainerColor = if (isDark) NearBlackHeader else DeepEmeraldHeader

    // Handle back button to return from sub-screen to main list menu
    BackHandler(enabled = activeSubScreen != null) {
        activeSubScreen = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeSubScreen?.title ?: "Settings",
                        fontWeight = FontWeight.Bold,
                        color = LightGreenWhiteText
                    )
                },
                navigationIcon = {
                    if (activeSubScreen != null) {
                        IconButton(onClick = { activeSubScreen = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Settings",
                                tint = GoldAccent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerContainerColor,
                    titleContentColor = LightGreenWhiteText,
                    navigationIconContentColor = GoldAccent
                )
            )
        }
    ) { paddingValues ->
        if (activeSubScreen == null) {
            // MAIN SETTINGS SCREEN: Simple list-menu navigation pattern
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header badge with active shop
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = GoldAccent.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeRestaurant?.name ?: "BBC POS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = activeRestaurant?.address ?: "No branch configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "v8.9",
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // List menu items
                items(SettingsCategory.values()) { category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeSubScreen = category },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = GoldAccent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = GoldAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = category.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = category.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Navigate to ${category.title}",
                                tint = GoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // DEDICATED SUB-SCREENS
            when (activeSubScreen) {
                SettingsCategory.SHOP_DETAILS -> {
                    // SUB-SCREEN 1: Shop / Branch Details (with prominent Edit option!)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
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
                                    Text(
                                        text = "Branches & Outlets",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${allRestaurants.size} branch(es) configured",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showAddRestaurantDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldAccent,
                                        contentColor = GoldTextDark
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ Add Shop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        items(allRestaurants) { rest ->
                            val isActive = rest.id == activeRestaurant?.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.switchActiveRestaurant(rest.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = if (isActive) {
                                    BorderStroke(2.dp, GoldAccent)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                },
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                if (isActive) Icons.Default.CheckCircle else Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = if (isActive) GoldAccent else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = rest.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isActive) {
                                                    Text(
                                                        text = "CURRENT ACTIVE BRANCH",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GoldAccent
                                                    )
                                                }
                                            }
                                        }

                                        if (isActive) {
                                            Surface(
                                                color = GoldAccent.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    "ACTIVE",
                                                    color = GoldAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Shop Details: Address, Phone, FSSAI, GST
                                    Text(
                                        text = "Address: ${rest.address.ifBlank { "No address set" }}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Phone: ${rest.phone.ifBlank { "No phone set" }}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (rest.isFssaiEnabled && rest.fssaiNumber.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "FSSAI: ${rest.fssaiNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (rest.isGstEnabled && rest.gstNumber.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "GSTIN: ${rest.gstNumber} (${rest.gstRate}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // ACTION BUTTONS: Edit Shop Details & Delete
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Prominent Edit option allowing update of shop name, address, and phone
                                        Button(
                                            onClick = { restaurantToEdit = rest },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GoldAccent,
                                                contentColor = GoldTextDark
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Edit Shop Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        if (allRestaurants.size > 1) {
                                            OutlinedButton(
                                                onClick = { restaurantToDelete = rest },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Delete", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsCategory.BILLING_FORMAT -> {
                    // Dedicated Sub-Screen: Billing & Receipt Settings
                    val currentFormat = BillTemplateDesign.fromId(activeRestaurant?.billFormat).id
                    var showSampleBillPreview by remember { mutableStateOf(false) }

                    val sampleBillForPreview = remember {
                        BillEntity(
                            id = "sample_preview_settings",
                            billNumber = "BBC-101",
                            restaurantId = "default",
                            restaurantName = "BBC FOOD HUB",
                            orderType = "DINE_IN",
                            items = listOf(
                                BillItem("item1", "Paneer Cheese Pizza", 199.0, 2, 398.0),
                                BillItem("item2", "Cold Coffee w/ Ice Cream", 90.0, 1, 90.0),
                                BillItem("item3", "Crispy French Fries", 80.0, 1, 80.0)
                            ),
                            subtotal = 568.0,
                            discountAmount = 28.0,
                            discountType = "5% Offer",
                            totalAmount = 540.0,
                            paymentMethod = "CREDIT",
                            customerName = "Rahul Sharma",
                            customerPhone = "9876543210",
                            tableName = "Table 4",
                            billTimestamp = System.currentTimeMillis()
                        )
                    }

                    if (showSampleBillPreview) {
                        val safeRest = activeRestaurant ?: RestaurantEntity(
                            id = "default",
                            name = "BBC FOOD HUB",
                            phone = "9130694963",
                            address = "Washim",
                            fssaiNumber = ""
                        )
                        BillPreviewDialog(
                            bill = sampleBillForPreview,
                            restaurant = safeRest,
                            previousDue = 320.0,
                            onDismiss = { showSampleBillPreview = false },
                            onSetDefaultTemplate = { chosenFormat ->
                                viewModel.updateBillFormat(chosenFormat)
                            }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Stars, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Standard Bill Format (Universal Pro)", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "The entire app (In-App Preview, Thermal Print, WhatsApp HD Image, and PDF Invoice) uses this unified, high-contrast, professional cafe design. Customize your brand logo, tagline, header, Dynamic UPI QR, and footer notes below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { showSampleBillPreview = true },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("👁️ Live Bill Preview", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { restaurantToEdit = activeRestaurant },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Customize Bill", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Active Bill Design & Feature Highlights:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = GoldAccent.copy(alpha = 0.12f)),
                                border = BorderStroke(1.5.dp, GoldAccent)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                            Text("Universal Cafe Pro (Active & Locked)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                        Surface(
                                            color = SuccessGreen.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("STANDARD", color = SuccessGreen, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(
                                        "• Custom Logo + Cafe Header & Tagline\n• Token Number & Order Mode (Dine-in / Parcel)\n• Description, Rate, Qty & Total Amount Table\n• Offer Discount & Loyalty Points Breakdown\n• 'You Saved ₹...' Emerald Green Banner\n• Dynamic UPI QR Code for instant scan & pay\n• Wi-Fi, Socials, FSSAI & Terms in Footer",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Standard Hardware Paper Sizes:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Option 1: A4 Standard Document
                        item {
                            val isSelected = currentFormat == "A4"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateBillFormat("A4")
                                        Toast.makeText(context, "Bill format set to A4 Standard", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) GoldAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) GoldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isSelected) GoldAccent else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = if (isSelected) GoldTextDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("A4 Document (Standard)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = GoldAccent.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "ACTIVE",
                                                        color = GoldAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "Full page (210 x 297 mm). Formal retail/tax invoice with header, item tables, tax breakdown, and footer notes.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.updateBillFormat("A4")
                                            Toast.makeText(context, "Bill format set to A4 Standard", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                                    )
                                }
                            }
                        }

                        // Option 2: 3-Inch / 80mm Thermal Receipt
                        item {
                            val isSelected = currentFormat == "THERMAL_3_INCH"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateBillFormat("THERMAL_3_INCH")
                                        Toast.makeText(context, "Bill format set to 3-Inch (80mm) Thermal", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) GoldAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) GoldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isSelected) GoldAccent else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.ReceiptLong,
                                                contentDescription = null,
                                                tint = if (isSelected) GoldTextDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("3-Inch Thermal Roll (80mm)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = GoldAccent.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "ACTIVE",
                                                        color = GoldAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "Standard desktop POS thermal printers (576 dots). Wide receipt format with monospaced itemized alignment.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.updateBillFormat("THERMAL_3_INCH")
                                            Toast.makeText(context, "Bill format set to 3-Inch (80mm) Thermal", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                                    )
                                }
                            }
                        }

                        // Option 3: 2-Inch / 58mm Thermal Receipt
                        item {
                            val isSelected = currentFormat == "THERMAL_2_INCH"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateBillFormat("THERMAL_2_INCH")
                                        Toast.makeText(context, "Bill format set to 2-Inch (58mm) Thermal", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) GoldAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) GoldAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isSelected) GoldAccent else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Receipt,
                                                contentDescription = null,
                                                tint = if (isSelected) GoldTextDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("2-Inch Thermal Roll (58mm)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = GoldAccent.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "ACTIVE",
                                                        color = GoldAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "Compact handheld & Bluetooth mobile POS thermal printers (384 dots). Compact, inkless receipts.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.updateBillFormat("THERMAL_2_INCH")
                                            Toast.makeText(context, "Bill format set to 2-Inch (58mm) Thermal", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = GoldAccent)
                                    )
                                }
                            }
                        }

                        // Sequential Bill Numbering & Prefix Section
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            var prefixInput by remember(activeRestaurant?.billPrefix) {
                                mutableStateOf(activeRestaurant?.billPrefix ?: "")
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Numbers,
                                            contentDescription = null,
                                            tint = CaramelWarm,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Bill Number Prefix & Sequencing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Configure a custom invoice prefix for all generated bills (e.g. 'INV', 'BILL', 'CAFE'). Sequence numbers auto-increment automatically (e.g. INV-001, INV-002).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = prefixInput,
                                        onValueChange = { prefixInput = it.uppercase() },
                                        label = { Text("Invoice Prefix") },
                                        placeholder = { Text("e.g. INV (Leave blank for default #001)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        supportingText = {
                                            val preview = if (prefixInput.isNotBlank()) "${prefixInput.trim().uppercase()}-001" else "#001"
                                            Text("Preview next format: $preview")
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            viewModel.updateBillPrefix(prefixInput)
                                            Toast.makeText(
                                                context,
                                                if (prefixInput.isNotBlank()) "Prefix set to ${prefixInput.trim().uppercase()}" else "Prefix cleared (using #001)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CaramelWarm,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Save Prefix", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Preview & Test Section
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Test Bill Image Generation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "Generates a preview receipt bitmap using the currently active format ($currentFormat) and opens it for review.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            val rest = activeRestaurant
                                            if (rest != null) {
                                                val sampleBill = com.example.data.local.entity.BillEntity(
                                                    id = "sample_test",
                                                    billNumber = "SAMPLE-001",
                                                    restaurantId = rest.id,
                                                    restaurantName = rest.name,
                                                    orderType = "DINE_IN",
                                                    tableName = "Table 1",
                                                    customerName = "Sample Customer",
                                                    customerPhone = "9876543210",
                                                    subtotal = 380.0,
                                                    discountAmount = 0.0,
                                                    totalAmount = 380.0,
                                                    paymentMethod = "UPI",
                                                    billTimestamp = System.currentTimeMillis(),
                                                    items = listOf(
                                                        com.example.data.local.entity.BillItem(
                                                            dishId = "sample_1",
                                                            dishName = "Cappuccino (Large)",
                                                            quantity = 2,
                                                            unitPrice = 140.0,
                                                            totalPrice = 280.0
                                                        ),
                                                        com.example.data.local.entity.BillItem(
                                                            dishId = "sample_2",
                                                            dishName = "Chocolate Croissant",
                                                            quantity = 1,
                                                            unitPrice = 100.0,
                                                            totalPrice = 100.0
                                                        )
                                                    )
                                                )
                                                val file = com.example.util.ReceiptBitmapGenerator.generateReceiptImageFile(context, sampleBill, rest)
                                                com.example.util.BillShareUtil.viewReceiptImage(context, file)
                                            } else {
                                                Toast.makeText(context, "No active branch found", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldAccent,
                                            contentColor = GoldTextDark
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Generate & Preview Receipt Image", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsCategory.CLOUD_SYNC -> {
                    // SUB-SCREEN 2: Cloud Sync & Data Restore
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Firestore Cloud Isolation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val currentFirebaseUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                        ?: userState.uid.takeIf { !it.startsWith("pos_uid_") }
                                        ?: ""
                                    Text(
                                        text = "Collection Path: pos_users/${currentFirebaseUid.ifBlank { "{userId}" }}/...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoldAccent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Security rules enforce strict per-user data isolation. Offline changes auto-sync when online.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Live Record Counts
                                    Text("Live Database Record Counts (Room DB):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                LiveCountBadge("Bills", billsCount)
                                                LiveCountBadge("Customers", customersCount)
                                                LiveCountBadge("Inventory", inventoryCount)
                                                LiveCountBadge("Expenses", expensesCount)
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                LiveCountBadge("Dishes", menuItemsCount)
                                                LiveCountBadge("Categories", categoriesCount)
                                                LiveCountBadge("Tables", tablesCount)
                                                LiveCountBadge("Registers", registersCount)
                                            }
                                        }
                                    }

                                    // Sync Status message
                                    Spacer(modifier = Modifier.height(12.dp))
                                    when (syncState) {
                                        is SyncState.Syncing -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GoldAccent)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text((syncState as SyncState.Syncing).message, fontSize = 12.sp, color = GoldAccent)
                                            }
                                        }
                                        is SyncState.Success -> {
                                            Text(
                                                text = "✓ ${(syncState as SyncState.Success).message}",
                                                fontSize = 12.sp,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        is SyncState.Error -> {
                                            val errorMsg = (syncState as SyncState.Error).error
                                            Column {
                                                Text(
                                                    text = "⚠ $errorMsg",
                                                    fontSize = 12.sp,
                                                    color = ErrorRed
                                                )
                                                if (errorMsg.contains("PERMISSION_DENIED", ignoreCase = true) || errorMsg.contains("permission", ignoreCase = true)) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Surface(
                                                        color = Color(0xFFFFF3CD),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Text(
                                                                text = "💡 Firebase Rules Setup Required:",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF332000)
                                                            )
                                                            Text(
                                                                text = "Firebase Console -> Firestore Database -> Rules:\nmatch /pos_users/{userId}/{document=**} {\n  allow read, write: if request.auth != null && request.auth.uid == userId;\n}",
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF4A3408),
                                                                lineHeight = 14.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = "Ready to sync • Offline changes preserved",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Action Buttons: Sync & Restore
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.triggerCloudSync()
                                                Toast.makeText(context, "Syncing data to Firestore...", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GoldAccent,
                                                contentColor = GoldTextDark
                                            )
                                        ) {
                                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sync Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.triggerCloudRestore()
                                                Toast.makeText(context, "Restoring data from Firestore...", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                                            border = BorderStroke(1.dp, GoldAccent)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Restore Data", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsCategory.AUTH_SECURITY -> {
                    // SUB-SCREEN 3: Authentication & Security
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = GoldAccent,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = userState.displayName.ifBlank { "BBC Cafe Admin" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = userState.email.ifBlank { "Google Signed In" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Permanent Firebase UID: ${userState.uid}",
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = GoldAccent
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "✓ Mapped permanently: Logging in with this Google account always links to your isolated Firestore cloud database.",
                                        fontSize = 12.sp,
                                        color = SuccessGreen
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "✓ Persistent session: Remains logged in after closing or restarting the app.",
                                        fontSize = 12.sp,
                                        color = SuccessGreen
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.signOut()
                                            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sign Out", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsCategory.APP_VERSION_EXPORT -> {
                    // SUB-SCREEN 5: App Version & APK Export (v4.1)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("BBC POS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            Text(
                                                "Release v${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})",
                                                fontWeight = FontWeight.Bold,
                                                color = GoldAccent,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Surface(
                                            color = SuccessGreen.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "v${com.example.BuildConfig.VERSION_NAME} READY",
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        "What's New in v${com.example.BuildConfig.VERSION_NAME}:\n" +
                                        "• Bulk Stock Purchase Fix: Decimal quantities (e.g. .5, .4) update total bill & items live.\n" +
                                        "• Clean Excel CSV Reports: Removed leading '=' symbols from CSV headers to fix #ERROR! in Excel/WPS.\n" +
                                        "• Cascade Batch Purge: Deleted items & purchase expenses clean up all orphaned stock batches.\n" +
                                        "• 0-Second Instant App Launch & Offline Resilience.\n" +
                                        "• Direct APK Export & WhatsApp Share directly from Settings.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 20.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Android,
                                                contentDescription = null,
                                                tint = GoldAccent,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("BBC POS v${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE}) • Master", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(
                                                    "Full Universal APK (~34 MB) with all DEX & architectures. Tap 'Download APK' to save.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Direct Download & Install buttons
                                    Button(
                                        onClick = {
                                            val (success, message) = ApkExportHelper.saveApkToDownloads(context)
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            if (success) {
                                                ApkExportHelper.installOrOpenApk(context)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldAccent,
                                            contentColor = GoldTextDark
                                        )
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download APK to Device Downloads", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            val shared = ApkExportHelper.shareApkFile(context)
                                            if (!shared) {
                                                Toast.makeText(context, "Preparing APK file...", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("📤 Share v${com.example.BuildConfig.VERSION_NAME} APK File (WhatsApp / Drive / Gmail)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    FilledTonalButton(
                                        onClick = {
                                            val launched = ApkExportHelper.installOrOpenApk(context)
                                            if (!launched) {
                                                Toast.makeText(context, "Opening APK installer...", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Install / Open BBC POS v${com.example.BuildConfig.VERSION_NAME} APK (~34 MB)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                "Installation Tip:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "If your phone asks for 'Install Unknown Apps' permission, tap Settings -> Allow, then return to complete the installation.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsCategory.REWARD_POINTS -> {
                    OffersLoyaltySettingsSubScreen(viewModel, paddingValues, initialTabIndex = 0, showTabs = false)
                }

                SettingsCategory.VISIT_REWARDS -> {
                    OffersLoyaltySettingsSubScreen(viewModel, paddingValues, initialTabIndex = 1, showTabs = false)
                }

                SettingsCategory.MANUAL_OFFERS -> {
                    OffersLoyaltySettingsSubScreen(viewModel, paddingValues, initialTabIndex = 2, showTabs = false)
                }

                SettingsCategory.CUSTOMER_LOYALTY -> {
                    OffersLoyaltySettingsSubScreen(viewModel, paddingValues, initialTabIndex = 3, showTabs = false)
                }

                SettingsCategory.MASTER_REPORTS -> {
                    com.example.ui.screens.reports.ReportsScreen(
                        viewModel = viewModel,
                        isEmbeddedInSettings = true,
                        onBackClick = { activeSubScreen = null }
                    )
                }

                null -> {}
            }
        }
    }

    // Add / Edit Restaurant Dialog
    if (showAddRestaurantDialog || restaurantToEdit != null) {
        RestaurantFormDialog(
            restaurant = restaurantToEdit,
            onDismiss = {
                showAddRestaurantDialog = false
                restaurantToEdit = null
            },
            onSave = { rest ->
                viewModel.saveRestaurant(rest)
                showAddRestaurantDialog = false
                restaurantToEdit = null
                Toast.makeText(context, "Shop saved successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog (FIX 4)
    restaurantToDelete?.let { rest ->
        DeleteConfirmationDialog(
            title = "Delete Branch / Shop?",
            itemName = rest.name,
            onDismiss = { restaurantToDelete = null },
            onConfirm = {
                viewModel.deleteRestaurant(rest.id)
                restaurantToDelete = null
                Toast.makeText(context, "Branch deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun LiveCountBadge(title: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GoldAccent)
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ColorSwatch(color: Color, label: String, isLight: Boolean = false) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(8.dp),
            color = color,
            border = BorderStroke(1.dp, if (isLight) Color.Gray.copy(alpha = 0.3f) else Color.Transparent)
        ) {}
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RestaurantFormDialog(
    restaurant: RestaurantEntity?,
    onDismiss: () -> Unit,
    onSave: (RestaurantEntity) -> Unit
) {
    val context = LocalContext.current

    // Basic Header Info
    var name by remember { mutableStateOf(restaurant?.name ?: "BBC Food Hub") }
    var tagline by remember { mutableStateOf(restaurant?.tagline ?: "Taste the Good Life • Fresh & Delicious") }
    var address by remember { mutableStateOf(restaurant?.address ?: "") }
    var phone by remember { mutableStateOf(restaurant?.phone ?: "") }
    var altPhone by remember { mutableStateOf(restaurant?.altPhone ?: "") }

    // Logo state
    var logoPreset by remember { mutableStateOf(restaurant?.logoPreset ?: "cafe_coffee") }
    var customLogoUri by remember { mutableStateOf(restaurant?.customLogoUri) }
    var customLogoBase64 by remember { mutableStateOf(restaurant?.customLogoBase64 ?: "") }

    // Logo image picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Take persistable URI permission if possible
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            customLogoUri = uri.toString()
            customLogoBase64 = try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
                    val byteArray = byteArrayOutputStream.toByteArray()
                    android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
                } else ""
            } catch (e: Exception) {
                ""
            }
            Toast.makeText(context, "Logo updated successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic UPI QR
    var upiId by remember { mutableStateOf(restaurant?.upiId ?: "bbcfoodhubwasahim@okaxis") }
    var upiQrEnabled by remember { mutableStateOf(restaurant?.upiQrEnabled ?: true) }

    // Wi-Fi & Social
    var wifiDetails by remember { mutableStateOf(restaurant?.wifiDetails ?: "") }
    var socialHandle by remember { mutableStateOf(restaurant?.socialHandle ?: "") }

    // Footer & Terms
    var footerNote by remember { mutableStateOf(restaurant?.footerNote ?: "Thank you for visiting! Please visit again.") }
    var customTermsNote by remember { mutableStateOf(restaurant?.customTermsNote ?: "• Prices are inclusive of all taxes\n• Items once prepared cannot be cancelled") }

    // Bill Badges & Features
    var showLogoOnBill by remember { mutableStateOf(restaurant?.showLogoOnBill ?: true) }
    var showTokenOnBill by remember { mutableStateOf(restaurant?.showTokenOnBill ?: true) }
    var showSavingsOnBill by remember { mutableStateOf(restaurant?.showSavingsOnBill ?: true) }
    var showPointsOnBill by remember { mutableStateOf(restaurant?.showPointsOnBill ?: true) }
    var showPreviousDueOnBill by remember { mutableStateOf(restaurant?.showPreviousDueOnBill ?: true) }

    // FSSAI State
    var isFssaiEnabled by remember { mutableStateOf(restaurant?.isFssaiEnabled ?: false) }
    var fssaiNumber by remember { mutableStateOf(restaurant?.fssaiNumber ?: "") }
    var showFssaiOnBill by remember { mutableStateOf(restaurant?.showFssaiOnBill ?: false) }

    // GST State
    var isGstEnabled by remember { mutableStateOf(restaurant?.isGstEnabled ?: false) }
    var gstNumber by remember { mutableStateOf(restaurant?.gstNumber ?: "") }
    var gstRate by remember { mutableStateOf((restaurant?.gstRate ?: 5.0).toString()) }
    var showGstOnBill by remember { mutableStateOf(restaurant?.showGstOnBill ?: false) }

    // Bill Format State
    var billFormat by remember { mutableStateOf(BillTemplateDesign.fromId(restaurant?.billFormat).id) }
    var billPrefix by remember { mutableStateOf(restaurant?.billPrefix ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
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
                // Header Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GoldAccent,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = GoldTextDark, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(
                                text = if (restaurant != null) "Shop Profile & Bill Customizer" else "Add New Shop Profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Customize Logo, Header, Footer, UPI QR & Bill Elements",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // ==========================================
                    // 1. BRAND LOGO SECTION
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                                Text("1. Brand Logo (Printed on Top of Bill)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show Shop Logo on Bill", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (showLogoOnBill) "Logo displayed at the top of the bill"
                                        else "Logo disabled. Bill starts directly with shop name in bold text.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = showLogoOnBill, onCheckedChange = { showLogoOnBill = it })
                            }

                            if (showLogoOnBill) {
                                // Custom Logo Upload
                                Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (customLogoUri != null) {
                                    AsyncImage(
                                        model = customLogoUri,
                                        contentDescription = "Custom Logo",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, GoldAccent, RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(64.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                when (logoPreset) {
                                                    "cafe_coffee" -> "☕"
                                                    "burger_snacks" -> "🍔"
                                                    "pizza_italian" -> "🍕"
                                                    "dessert_icecream" -> "🍦"
                                                    "bakery_sandwich" -> "🥪"
                                                    "indian_dhaba" -> "🍛"
                                                    "beverages_juice" -> "🍹"
                                                    else -> "🏪"
                                                },
                                                fontSize = 28.sp
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = GoldTextDark),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Upload Logo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (customLogoUri != null) {
                                            OutlinedButton(
                                                onClick = { customLogoUri = null; customLogoBase64 = "" },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (customLogoUri != null) "Custom logo loaded from gallery" else "Or choose a standard cafe preset below:",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Preset Logo Chips
                            Text("Logo Preset Icons:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    "cafe_coffee" to "☕ Cafe Coffee",
                                    "burger_snacks" to "🍔 Burger Hub",
                                    "pizza_italian" to "🍕 Pizza",
                                    "dessert_icecream" to "🍦 Ice Cream",
                                    "bakery_sandwich" to "🥪 Bakery",
                                    "indian_dhaba" to "🍛 Dining",
                                    "beverages_juice" to "🍹 Drinks"
                                )
                                presets.forEach { (presetKey, label) ->
                                    val isSelected = logoPreset == presetKey && customLogoUri == null
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            logoPreset = presetKey; customLogoBase64 = ""
                                            customLogoUri = null
                                        },
                                        label = { Text(label, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                            }
                        }
                    }

                    // ==========================================
                    // 2. HEADER DETAILS (100% EDITABLE)
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                                Text("2. Shop Header & Branding (Editable)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Shop / Brand Name *") },
                                placeholder = { Text("e.g. BBC FOOD HUB") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = tagline,
                                onValueChange = { tagline = it },
                                label = { Text("Tagline / Slogan (Printed below Name)") },
                                placeholder = { Text("e.g. Taste the Good Life") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Full Shop Address (Line 1 & 2)") },
                                placeholder = { Text("e.g. Near Bus Stand, Washim - 444505") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("Primary Phone") },
                                    placeholder = { Text("9876543210") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                )
                                OutlinedTextField(
                                    value = altPhone,
                                    onValueChange = { altPhone = it },
                                    label = { Text("Alt / Landline") },
                                    placeholder = { Text("Optional") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                )
                            }
                        }
                    }

                    // ==========================================
                    // 3. DYNAMIC UPI PAYMENT QR CODE
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (upiQrEnabled) SuccessGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = if (upiQrEnabled) SuccessGreen else Color.Gray)
                                    Column {
                                        Text("3. Dynamic UPI Payment QR Code", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Auto-encodes bill amount for 1-tap customer pay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(checked = upiQrEnabled, onCheckedChange = { upiQrEnabled = it })
                            }

                            if (upiQrEnabled) {
                                OutlinedTextField(
                                    value = upiId,
                                    onValueChange = { upiId = it.trim().lowercase() },
                                    label = { Text("Merchant UPI VPA ID *") },
                                    placeholder = { Text("e.g. bbcfoodhubwasahim@okaxis / 9876543210@paytm") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Text(
                                    text = "⚡ Each customer bill dynamically generates a scan-to-pay QR with the exact grand total prefilled!",
                                    fontSize = 11.sp,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }

                    // ==========================================
                    // 4. BILL ITEM TABLE & SMART HIGHLIGHTS
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.TableRows, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                                Text("4. Bill Item Table & Smart Highlights", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "• Full item details (Description, Rate, Quantity, Total Amount, Addons & Notes) are always shown in high contrast.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Show Order Type & Token Badge (Dine-in / Parcel)", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showTokenOnBill, onCheckedChange = { showTokenOnBill = it })
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Show 'You Saved ₹...' Banner", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showSavingsOnBill, onCheckedChange = { showSavingsOnBill = it })
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Show Loyalty Points Earned & Balance", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showPointsOnBill, onCheckedChange = { showPointsOnBill = it })
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Show Previous Due (Credit Balance) on Bills", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showPreviousDueOnBill, onCheckedChange = { showPreviousDueOnBill = it })
                            }
                        }
                    }

                    // ==========================================
                    // 5. FOOTER, WI-FI & SOCIAL DETAILS
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                                Text("5. Footer, Wi-Fi & Social Media (Editable)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            OutlinedTextField(
                                value = footerNote,
                                onValueChange = { footerNote = it },
                                label = { Text("Footer Thank You Message") },
                                placeholder = { Text("e.g. Thank you for visiting! Please visit again.") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = customTermsNote,
                                onValueChange = { customTermsNote = it },
                                label = { Text("Terms & Conditions Note") },
                                placeholder = { Text("e.g. Items once sold cannot be returned.") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = wifiDetails,
                                    onValueChange = { wifiDetails = it },
                                    label = { Text("Free Wi-Fi Details") },
                                    placeholder = { Text("e.g. BBC_GUEST / cafe@123") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = socialHandle,
                                    onValueChange = { socialHandle = it },
                                    label = { Text("Instagram / Review") },
                                    placeholder = { Text("e.g. @bbcfoodhub") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // ==========================================
                    // 6. FSSAI & GST TAX CONFIGURATION
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                                Text("6. FSSAI License & GST Tax", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            // FSSAI
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Enable FSSAI Food License", fontWeight = FontWeight.Medium)
                                Switch(checked = isFssaiEnabled, onCheckedChange = { isFssaiEnabled = it })
                            }
                            if (isFssaiEnabled) {
                                OutlinedTextField(
                                    value = fssaiNumber,
                                    onValueChange = { fssaiNumber = it.trim() },
                                    label = { Text("FSSAI License No. (14 Digits)") },
                                    placeholder = { Text("e.g. 11521019000123") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Print FSSAI on Customer Bill", style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = showFssaiOnBill, onCheckedChange = { showFssaiOnBill = it })
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            // GST
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Enable GST Tax Calculation", fontWeight = FontWeight.Medium)
                                Switch(checked = isGstEnabled, onCheckedChange = { isGstEnabled = it })
                            }
                            if (isGstEnabled) {
                                OutlinedTextField(
                                    value = gstNumber,
                                    onValueChange = { gstNumber = it.trim().uppercase() },
                                    label = { Text("GSTIN Registration No.") },
                                    placeholder = { Text("e.g. 27AAAAA0000A1Z5") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = gstRate,
                                    onValueChange = { gstRate = it },
                                    label = { Text("GST Rate (%)") },
                                    placeholder = { Text("5.0") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Print GST & Tax Split on Customer Bill", style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = showGstOnBill, onCheckedChange = { showGstOnBill = it })
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 7. BILL NUMBER PREFIX & DEFAULT FORMAT
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = GoldAccent.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                                Text("7. Standard Bill Format (Unified Design)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "Recommended: Universal Cafe Pro provides the ultimate unified experience with logo, table headers, QR code, and crystal clear typography across print & share.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = billPrefix,
                                onValueChange = { billPrefix = it.uppercase() },
                                label = { Text("Bill Number Prefix (Optional)") },
                                placeholder = { Text("e.g. BBC, INV, CAFE") },
                                supportingText = { Text("Sequential format: ${if (billPrefix.isNotBlank()) "${billPrefix.trim().uppercase()}-001" else "#001"}") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Bottom Action Buttons Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    Toast.makeText(context, "Please enter shop name", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val entity = RestaurantEntity(
                                    id = restaurant?.id ?: "rest_${UUID.randomUUID()}",
                                    name = name.trim(),
                                    tagline = tagline.trim(),
                                    address = address.trim(),
                                    phone = phone.trim(),
                                    altPhone = altPhone.trim(),
                                    footerNote = footerNote.trim(),
                                    customTermsNote = customTermsNote.trim(),
                                    logoPreset = logoPreset,
                                    customLogoUri = customLogoUri, customLogoBase64 = customLogoBase64,
                                    isActive = restaurant?.isActive ?: true,
                                    fssaiNumber = fssaiNumber.trim(),
                                    isFssaiEnabled = isFssaiEnabled,
                                    showFssaiOnBill = showFssaiOnBill,
                                    gstNumber = gstNumber.trim(),
                                    gstRate = gstRate.toDoubleOrNull() ?: 5.0,
                                    isGstEnabled = isGstEnabled,
                                    showGstOnBill = showGstOnBill,
                                    upiId = upiId.trim(),
                                    upiQrEnabled = upiQrEnabled,
                                    wifiDetails = wifiDetails.trim(),
                                    socialHandle = socialHandle.trim(),
                                    showLogoOnBill = showLogoOnBill,
                                    showTokenOnBill = showTokenOnBill,
                                    showSavingsOnBill = showSavingsOnBill,
                                    showPointsOnBill = showPointsOnBill,
                                    showPreviousDueOnBill = showPreviousDueOnBill,
                                    paperWidth = restaurant?.paperWidth ?: "80MM",
                                    billFormat = billFormat,
                                    billPrefix = billPrefix.trim().uppercase(),
                                    billPrefixCountersJson = restaurant?.billPrefixCountersJson ?: "{}"
                                )
                                onSave(entity)
                            },
                            modifier = Modifier.weight(2f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAccent,
                                contentColor = GoldTextDark
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (restaurant != null) "Save Shop Profile" else "Create Shop Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
