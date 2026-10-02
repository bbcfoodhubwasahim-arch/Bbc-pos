package com.example.data.local.entity

/**
 * Configurable Points Engine Rules for BBC Food Hub loyalty system.
 * Allows the cafe owner to customize earning rates, point valuation,
 * minimum redemptions, redemption caps, and validity periods.
 */
data class PointsEngineRules(
    val earnRatePercent: Double = 1.0,           // 1.0 = 1% points earned on net bill
    val pointValueRupees: Double = 1.0,          // 1 Point = ₹1.00 discount upon redemption
    val minRedemptionPoints: Int = 50,           // Minimum points required to redeem
    val maxDiscountCapPercent: Double = 50.0,    // Reward points max % cap of bill (e.g. 50%)
    val rewardPointsExpiryDays: Int = 60,        // Expiry for earned reward points
    val giftPointsExpiryDays: Int = 30,          // Expiry for granted gift points
    val welcomeBonusEnabled: Boolean = false,    // Auto-grant welcome points to new customers
    val welcomeBonusPoints: Int = 20,            // Points granted on first registration
    val minBillAmountToEarn: Double = 0.0,       // Minimum bill floor required to earn points
    val autoEnrollInVisitPass: Boolean = false,  // If false, new customers are NOT automatically enrolled in visit pass (Default: OFF)
    val welcomeBonusExpiryDays: Int = 30         // Expiry in days for welcome bonus points
) {
    companion object {
        val DEFAULT = PointsEngineRules(
            earnRatePercent = 1.0,
            pointValueRupees = 1.0,
            minRedemptionPoints = 50,
            maxDiscountCapPercent = 50.0,
            rewardPointsExpiryDays = 60,
            giftPointsExpiryDays = 30,
            welcomeBonusEnabled = false,
            welcomeBonusPoints = 20,
            minBillAmountToEarn = 0.0,
            autoEnrollInVisitPass = false,
            welcomeBonusExpiryDays = 30
        )

        val FESTIVE_2X = PointsEngineRules(
            earnRatePercent = 2.0,
            pointValueRupees = 1.0,
            minRedemptionPoints = 40,
            maxDiscountCapPercent = 50.0,
            rewardPointsExpiryDays = 45,
            giftPointsExpiryDays = 30,
            welcomeBonusEnabled = true,
            welcomeBonusPoints = 30,
            minBillAmountToEarn = 0.0,
            autoEnrollInVisitPass = false,
            welcomeBonusExpiryDays = 30
        )

        val VIP_MEGA = PointsEngineRules(
            earnRatePercent = 5.0,
            pointValueRupees = 1.0,
            minRedemptionPoints = 25,
            maxDiscountCapPercent = 75.0,
            rewardPointsExpiryDays = 30,
            giftPointsExpiryDays = 15,
            welcomeBonusEnabled = true,
            welcomeBonusPoints = 50,
            minBillAmountToEarn = 100.0,
            autoEnrollInVisitPass = false,
            welcomeBonusExpiryDays = 30
        )
    }
}
