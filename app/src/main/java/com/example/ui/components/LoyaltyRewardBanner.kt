package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.OfferEntity
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun LoyaltyRewardBanner(
    offer: OfferEntity?,
    visitNumber: Int,
    subtotal: Double,
    isRewardClaimed: Boolean,
    claimedRewardItemName: String? = null,
    onClaimClick: () -> Unit,
    onRemoveRewardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (offer == null) return

    val qualifies = subtotal >= offer.minBillAmount
    val shortfall = (offer.minBillAmount - subtotal).coerceAtLeast(0.0)

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (qualifies) {
            val rewardBadgeText = when (offer.rewardType) {
                "FLAT_DISCOUNT" -> "Flat ₹${offer.rewardValue.toInt()} OFF"
                "PERCENT_DISCOUNT" -> "${offer.rewardValue.toInt()}% OFF"
                else -> "Free Item (${offer.freeItemQuantity}x)"
            }
            val buttonText = when (offer.rewardType) {
                "FLAT_DISCOUNT" -> "Apply ₹${offer.rewardValue.toInt()} OFF"
                "PERCENT_DISCOUNT" -> "Apply ${offer.rewardValue.toInt()}% OFF"
                else -> "Claim Free Item"
            }

            val isDark = isSystemInDarkTheme()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isRewardClaimed) {
                    if (isDark) Color(0xFF0F2E1B) else SuccessGreen.copy(alpha = 0.12f)
                } else {
                    if (isDark) Color(0xFF2A1E0D) else GoldAccent.copy(alpha = 0.18f)
                },
                border = BorderStroke(
                    1.5.dp,
                    if (isRewardClaimed) {
                        if (isDark) Color(0xFF10B981) else SuccessGreen
                    } else {
                        if (isDark) Color(0xFFF59E0B) else GoldAccent
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isRewardClaimed) SuccessGreen else GoldAccent,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isRewardClaimed) Icons.Default.Check else Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = if (isRewardClaimed) Color.White else GoldTextDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = if (isRewardClaimed) {
                                    "✓ Reward Applied: $rewardBadgeText"
                                } else {
                                    "🎉 Visit #$visitNumber Reward Unlocked!"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isRewardClaimed) {
                                    if (isDark) Color(0xFF6EE7B7) else SuccessGreen
                                } else MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = if (isRewardClaimed) {
                                    claimedRewardItemName ?: offer.name
                                } else {
                                    "${offer.name} • $rewardBadgeText" + if (offer.minBillAmount > 0) " (Min bill ₹${offer.minBillAmount.toInt()})" else ""
                                },
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFFCBD5E1) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isRewardClaimed) {
                        OutlinedButton(
                            onClick = onRemoveRewardClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onClaimClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAccent,
                                contentColor = GoldTextDark
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(buttonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (subtotal > 0 && shortfall > 0) {
            val benefitText = when (offer.rewardType) {
                "FLAT_DISCOUNT" -> "Flat ₹${offer.rewardValue.toInt()} OFF"
                "PERCENT_DISCOUNT" -> "${offer.rewardValue.toInt()}% OFF"
                else -> "Free Item"
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add ₹${String.format(Locale.US, "%.0f", shortfall)} more to unlock Visit #$visitNumber Reward ($benefitText - ${offer.name})!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
