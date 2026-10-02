package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Customer entity containing name, contact number, and credit customer status.
 */
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val contactNumber: String,
    val isCreditCustomer: Boolean = false,
    val loyaltyVisitCount: Int = 0,
    val loyaltyStartDate: Long? = null,
    val loyaltyExpiryDate: Long? = null,
    val currentLoyaltyProgramId: String? = null,
    val loyaltyHistoryJson: String = "[]",
    val rewardPointsBalance: Int = 0,
    val giftPointsBalance: Int = 0,
    val isEnrolledInLoyalty: Boolean = false,
    val birthday: String? = null,
    val lastVisitTimestamp: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
