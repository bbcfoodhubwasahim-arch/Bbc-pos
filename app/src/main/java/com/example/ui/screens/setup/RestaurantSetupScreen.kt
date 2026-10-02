package com.example.ui.screens.setup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantSetupScreen(
    viewModel: MainViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    var restaurantName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var footerNote by remember { mutableStateOf("Thank you for visiting! Freshly served with love.") }
    var selectedLogoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPreset by remember { mutableStateOf("cafe_coffee") }
    var isSubmitting by remember { mutableStateOf(false) }

    // FSSAI & GST state
    var isFssaiEnabled by remember { mutableStateOf(false) }
    var fssaiNumber by remember { mutableStateOf("") }
    var showFssaiOnBill by remember { mutableStateOf(false) }
    var isGstEnabled by remember { mutableStateOf(false) }
    var gstNumber by remember { mutableStateOf("") }
    var gstRate by remember { mutableStateOf("5.0") }
    var showGstOnBill by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedLogoUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Restaurant Setup", fontWeight = FontWeight.Bold)
                        Text(
                            "Configure your cafe details to start billing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CreamSurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CaramelWarm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "First-Time Shop Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EspressoDark
                        )
                        Text(
                            text = "Enter your restaurant name, address, and logo. This will appear on all your bills and receipts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EspressoBrown
                        )
                    }
                }
            }

            // Logo Picker Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Restaurant Logo / Brand Icon",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Logo Preview
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, CaramelWarm, CircleShape)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedLogoUri != null) {
                            AsyncImage(
                                model = selectedLogoUri,
                                contentDescription = "Restaurant Logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Upload Logo",
                                    tint = CaramelWarm,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    "Add Logo",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CaramelWarm
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedLogoUri != null) "Change Photo" else "Upload Logo")
                        }

                        if (selectedLogoUri != null) {
                            TextButton(onClick = { selectedLogoUri = null }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Restaurant Details Form
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Basic Business Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Restaurant Name (Empty by default)
                    OutlinedTextField(
                        value = restaurantName,
                        onValueChange = { restaurantName = it },
                        label = { Text("Restaurant / Cafe Name *") },
                        placeholder = { Text("e.g. My Cafe & Bistro") },
                        leadingIcon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = isSubmitting && restaurantName.isBlank(),
                        supportingText = {
                            if (isSubmitting && restaurantName.isBlank()) {
                                Text("Restaurant name is required", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )

                    // Address
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address / Location") },
                        placeholder = { Text("e.g. Shop 12, Food Street, Main Market") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )

                    // Phone Number
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Contact Phone Number") },
                        placeholder = { Text("e.g. +91 98765 43210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Footer / Receipt Note
                    OutlinedTextField(
                        value = footerNote,
                        onValueChange = { footerNote = it },
                        label = { Text("Invoice Footer Note") },
                        placeholder = { Text("e.g. Thank you for visiting! Freshly baked with love.") },
                        leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }

            // FSSAI Configuration Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFssaiEnabled) CaramelWarm.copy(alpha = 0.08f) else CreamSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("FSSAI Food License", fontWeight = FontWeight.Bold)
                            Text("Optional food safety registration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isFssaiEnabled,
                            onCheckedChange = { isFssaiEnabled = it }
                        )
                    }

                    if (isFssaiEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = fssaiNumber,
                            onValueChange = { fssaiNumber = it },
                            label = { Text("FSSAI Number (14 Digits)") },
                            placeholder = { Text("e.g. 11521019000123") },
                            leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Print FSSAI on customer bills", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = showFssaiOnBill,
                                onCheckedChange = { showFssaiOnBill = it }
                            )
                        }
                    }
                }
            }

            // GST Configuration Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGstEnabled) SuccessGreen.copy(alpha = 0.08f) else CreamSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("GST / Tax Invoicing", fontWeight = FontWeight.Bold)
                            Text("Enable if your cafe is GST registered", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isGstEnabled,
                            onCheckedChange = { isGstEnabled = it }
                        )
                    }

                    if (isGstEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = gstNumber,
                            onValueChange = { gstNumber = it.uppercase() },
                            label = { Text("GSTIN Number (15 Digits)") },
                            placeholder = { Text("e.g. 27AAAAA0000A1Z5") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = gstRate,
                            onValueChange = { gstRate = it },
                            label = { Text("GST Tax Rate (%)") },
                            placeholder = { Text("e.g. 5.0") },
                            leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Print GST details on customer bills", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = showGstOnBill,
                                onCheckedChange = { showGstOnBill = it }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            Button(
                onClick = {
                    if (restaurantName.isBlank()) {
                        isSubmitting = true
                        Toast.makeText(context, "Please enter your restaurant name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSubmitting = true
                    viewModel.saveInitialRestaurantSetup(
                        name = restaurantName,
                        address = address,
                        phone = phoneNumber,
                        footerNote = footerNote,
                        logoUri = selectedLogoUri?.toString(),
                        fssaiNumber = fssaiNumber.trim(),
                        isFssaiEnabled = isFssaiEnabled,
                        showFssaiOnBill = showFssaiOnBill,
                        gstNumber = gstNumber.trim(),
                        gstRate = gstRate.toDoubleOrNull() ?: 5.0,
                        isGstEnabled = isGstEnabled,
                        showGstOnBill = showGstOnBill
                    ) {
                        Toast.makeText(context, "Welcome to $restaurantName!", Toast.LENGTH_SHORT).show()
                        onSetupComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete Setup & Start Billing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
