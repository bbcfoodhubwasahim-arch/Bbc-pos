package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val altPhone: String = "",
    val tagline: String = "Taste the Best, Love the Rest!",
    val footerNote: String = "Thank you for visiting! Please visit again.",
    val customTermsNote: String = "",
    val logoPreset: String = "cafe_coffee", // default preset or custom
    val customLogoUri: String? = null,
    val isActive: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),

    // FSSAI Configuration
    val fssaiNumber: String = "",
    val isFssaiEnabled: Boolean = false,
    val showFssaiOnBill: Boolean = false,

    // GST Configuration
    val gstNumber: String = "",
    val gstRate: Double = 5.0,
    val isGstEnabled: Boolean = false,
    val showGstOnBill: Boolean = false,

    // UPI & Payment QR
    val upiId: String = "bbcfoodhubwasahim@okaxis",
    val upiQrEnabled: Boolean = true,

    // Wi-Fi & Social Details
    val wifiDetails: String = "",
    val socialHandle: String = "",

    // Bill Customization Switches
    val showLogoOnBill: Boolean = true,
    val showTokenOnBill: Boolean = true,
    val showSavingsOnBill: Boolean = true,
    val showPointsOnBill: Boolean = true,
    val showPreviousDueOnBill: Boolean = true,

    // Printer Paper Width (80MM or 58MM)
    val paperWidth: String = "80MM",

    // Bill & Receipt Format: default "UNIVERSAL_CAFE_PRO"
    val billFormat: String = "UNIVERSAL_CAFE_PRO",

    // Custom Logo Base64 Representation
    val customLogoBase64: String = "",

    // Sequential Bill Number Configuration
    val billPrefix: String = "",
    val billPrefixCountersJson: String = "{}"
)


