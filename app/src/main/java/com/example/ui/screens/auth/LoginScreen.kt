package com.example.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CreamBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding (Logo & Title)
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.bbc_food_hub_logo),
                    contentDescription = "BBC POS",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "BBC POS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = EspressoBrown,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "FOOD HUB • POS & Billing",
                style = MaterialTheme.typography.bodyMedium,
                color = CaramelWarm,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (errorMessage != null) {
                Surface(
                    color = Color(0xFFFDE8E8),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Authentication Notice",
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = EspressoDark,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = { errorMessage = null }
                        ) {
                            Text("Dismiss", color = EspressoDark.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Exclusive Primary "Sign in with Google" button
            Surface(
                onClick = {
                    if (isLoading) return@Surface
                    isLoading = true
                    errorMessage = null
                    viewModel.signInWithGoogleNative(
                        context = context,
                        onSuccess = {
                            isLoading = false
                            Toast.makeText(context, "Welcome to BBC POS!", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        },
                        onError = { err ->
                            isLoading = false
                            if (err != "CANCELLED") {
                                errorMessage = err
                                Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFF747775)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = CaramelWarm,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Signing in...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = EspressoDark
                        )
                    } else {
                        GoogleLogoIcon(modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Sign in with Google",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F1F1F)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Security & Cloud Sync information badge
            Surface(
                color = GoldAccent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Google Account Cloud Sync",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = EspressoDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sign in with your Google account to access your cafe POS data, sales history, and real-time cloud backup.",
                            fontSize = 11.sp,
                            color = EspressoDark.copy(alpha = 0.8f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard 4-color Google G Logo rendered with Jetpack Compose Canvas
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f

        // Blue, Red, Yellow, Green official Google colors
        val blue = Color(0xFF4285F4)
        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)

        // Draw segmented arc
        // Top right & top (Red)
        drawArc(
            color = red,
            startAngle = 180f + 45f,
            sweepAngle = 100f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2)
        )
        // Right & bottom right (Blue)
        drawArc(
            color = blue,
            startAngle = -35f,
            sweepAngle = 100f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2)
        )
        // Bottom (Green)
        drawArc(
            color = green,
            startAngle = 45f,
            sweepAngle = 110f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2)
        )
        // Left & bottom left (Yellow)
        drawArc(
            color = yellow,
            startAngle = 145f,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2)
        )

        // Inner circle cutout to form the 'G' loop
        drawCircle(
            color = Color.White,
            radius = r * 0.58f,
            center = Offset(cx, cy)
        )

        // Horizontal bar of the 'G'
        drawRect(
            color = blue,
            topLeft = Offset(cx - 0.05f * r, cy - 0.22f * r),
            size = Size(r * 1.05f, r * 0.44f)
        )

        // Inner mask to clean up center
        drawCircle(
            color = Color.White,
            radius = r * 0.25f,
            center = Offset(cx - 0.22f * r, cy)
        )
    }
}

