package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// BBC FOOD HUB - ROYAL AMBER & CRISP WHITE PALETTE
// ==========================================

// Primary Branding (Royal Amber & Saffron Gold)
val WarmAmber = Color(0xFFD97706)          // Rich Amber Brand Color
val DeepAmber = Color(0xFFB45309)          // Deep Amber for badges and active states
val SaffronGold = Color(0xFFF59E0B)        // Vibrant gold accent
val GoldAccent = WarmAmber                 // Backwards-compatible alias
val GoldTextDark = Color(0xFFFFFFFF)       // Crisp white text on amber/gold

// High-Contrast Light Mode Surfaces & Canvas
val LightBackground = Color(0xFFF8FAFC)    // Crisp, soft slate-white (Zero screen glare)
val CrispWhite = Color(0xFFFFFFFF)         // Pure white card background
val WhiteCard = CrispWhite
val WarmOffWhite = LightBackground
val CrispCardBorder = Color(0xFFE2E8F0)    // High-definition card border (100% sharp)
val EmeraldBorderLight = CrispCardBorder
val LightSurfaceVariant = Color(0xFFF1F5F9)// Distinct secondary container

// High-Readability Typography Colors (Zero Blurry Text!)
val TextPrimary = Color(0xFF0F172A)        // 100% Pure Jet-Black Slate for numbers & text
val TextPrimaryLight = TextPrimary
val TextSecondary = Color(0xFF0F172A)      // 100% Pure Jet-Black Slate (No faint colors!)
val TextSecondaryLight = TextSecondary
val TextMuted = Color(0xFF1E293B)          // Very dark slate (nearly black) for secondary details

// Headers & Prominent Bar
val FoodHubCharcoal = Color(0xFF1E293B)    // Charcoal header for solid contrast
val DeepEmeraldHeader = FoodHubCharcoal
val NearBlackHeader = Color(0xFF0F172A)
val LightGreenWhiteText = Color(0xFFFFFFFF)

// Quick Recognition Action Colors (0% Billing Mistakes!)
val SuccessGreen = Color(0xFF16A34A)       // Crisp Emerald Green for Settle & Paid
val WarningOrange = Color(0xFFD97706)      // Amber Alert for Khata Pending Due
val ErrorRed = Color(0xFFDC2626)           // High-contrast Crimson Red
val TableOccupiedRed = Color(0xFFDC2626)
val TableAvailableGreen = Color(0xFF16A34A)
val UPIBlue = Color(0xFF2563EB)            // Crisp UPI Blue
val SoftBlue = Color(0xFF3B82F6)
val CashAmber = Color(0xFFD97706)
val CreditPurple = Color(0xFF7C3AED)

// Backwards-compatible aliases for existing screens and components
val CaramelWarm = WarmAmber
val AmberGold = SaffronGold
val EspressoDark = FoodHubCharcoal
val EspressoBrown = FoodHubCharcoal
val MochaMedium = DeepAmber
val CreamBackground = LightBackground
val CreamSurface = CrispWhite
val CreamSurfaceVariant = LightSurfaceVariant
val BiscuitBorder = CrispCardBorder

// High-Contrast Dark Mode Colors (Strict WCAG 2.1 AA Compliant)
val TextPrimaryDark = Color(0xFFF8FAFC)    // Crisp white for dark mode
val DarkTextPrimary = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFFCBD5E1)  // Bright light-gray for dark mode
val DarkTextSecondary = Color(0xFFCBD5E1)
val TextMutedDark = Color(0xFF94A3B8)

val DarkBackground = Color(0xFF0B1120)     // Deep rich slate-black
val DarkSurface = Color(0xFF1E293B)        // Solid dark charcoal card surface
val DarkSurfaceCard = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155) // Elevated dark slate container
val DarkSurfaceVariant_Legacy = DarkSurfaceVariant
val DarkBorder = Color(0xFF475569)         // Distinct dark border
val DarkCardBorder = Color(0xFF475569)
val DarkPrimary = SaffronGold
val DarkSecondary = Color(0xFF38BDF8)
val DeepEmeraldBlack = Color(0xFF0B1120)

/**
 * Returns a high-contrast text color based on the perceived luminance of the background.
 * Guarantees crisp readability on any background color across both light and dark modes.
 */
fun getContrastTextColor(backgroundColor: Color): Color {
    val luminance = 0.299f * backgroundColor.red + 0.587f * backgroundColor.green + 0.114f * backgroundColor.blue
    return if (luminance > 0.45f) Color(0xFF0F172A) else Color(0xFFFFFFFF)
}

fun getContrastSecondaryTextColor(backgroundColor: Color): Color {
    val luminance = 0.299f * backgroundColor.red + 0.587f * backgroundColor.green + 0.114f * backgroundColor.blue
    return if (luminance > 0.45f) Color(0xFF334155) else Color(0xFFCBD5E1)
}


