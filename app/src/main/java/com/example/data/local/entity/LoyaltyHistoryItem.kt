package com.example.data.local.entity

/**
 * Historical record of a customer's loyalty lifecycle event.
 */
data class LoyaltyHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val eventType: String, // "VISIT_COMPLETED", "CYCLE_STARTED", "CYCLE_EXPIRED", "CYCLE_RESET", "CYCLE_EXTENDED", "VISIT_REVERSED"
    val visitNumber: Int = 0,
    val billId: String? = null,
    val billNumber: String? = null,
    val billAmount: Double = 0.0,
    val offerName: String? = null,
    val rewardGiven: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)
