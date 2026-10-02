package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BillEntity
import com.example.ui.theme.*
import com.example.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * Visual Summary Dashboard Component
 * Modeled after modern Recharts dashboards for restaurant POS:
 * 1. Daily Revenue Trend (Recharts-style Rounded Bar Chart with Grid & Values)
 * 2. Top-Selling Menu Items (Recharts-style Ranked Horizontal Bar Chart)
 * 3. Order Volume Trends (Recharts-style Gradient Area Spline Chart)
 * 4. Executive KPI Summary Cards (Revenue, Order Volume, AOV, Rush Hours)
 */
@Composable
fun VisualSummaryDashboard(
    bills: List<BillEntity>,
    modifier: Modifier = Modifier
) {
    var selectedTimeframeDays by remember { mutableIntStateOf(7) } // 7, 14, 30
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    val activeBills = remember(bills) {
        bills.filter { it.isSettled }
    }

    // Filter bills according to the selected timeframe
    val cutoffTimestamp = remember(selectedTimeframeDays) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -selectedTimeframeDays)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    }

    val filteredBills = remember(activeBills, cutoffTimestamp) {
        activeBills.filter { it.billTimestamp >= cutoffTimestamp }
    }

    // Aggregate daily statistics for charts
    val dailyMetrics = remember(filteredBills, selectedTimeframeDays) {
        val cal = Calendar.getInstance()
        val list = mutableListOf<DailyTrendPoint>()
        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfDisplay = SimpleDateFormat("dd MMM", Locale.getDefault())
        val sdfDayName = SimpleDateFormat("EEE", Locale.getDefault())

        val billsByDate = filteredBills.groupBy { sdfKey.format(Date(it.billTimestamp)) }

        for (i in (selectedTimeframeDays - 1) downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val key = sdfKey.format(dayCal.time)
            val dayBills = billsByDate[key] ?: emptyList()
            val dayRevenue = dayBills.sumOf { it.totalAmount }
            val dayOrders = dayBills.size
            val dayDineIn = dayBills.count { it.orderType == "DINE_IN" }
            val dayTakeaway = dayBills.count { it.orderType == "TAKEAWAY" }

            list.add(
                DailyTrendPoint(
                    dateKey = key,
                    dateLabel = sdfDisplay.format(dayCal.time),
                    dayName = sdfDayName.format(dayCal.time),
                    revenue = dayRevenue,
                    orderCount = dayOrders,
                    dineInCount = dayDineIn,
                    takeawayCount = dayTakeaway
                )
            )
        }
        list
    }

    // Top-selling menu items aggregation
    val topSellingItems = remember(filteredBills) {
        val itemMap = mutableMapOf<String, Pair<Int, Double>>() // dishName -> (qty, revenue)
        filteredBills.forEach { bill ->
            bill.items.forEach { item ->
                val current = itemMap[item.dishName] ?: Pair(0, 0.0)
                itemMap[item.dishName] = Pair(current.first + item.quantity, current.second + item.totalPrice)
            }
        }

        val totalRevenue = itemMap.values.sumOf { it.second }
        itemMap.entries
            .map { (name, stats) ->
                TopItemMetric(
                    dishName = name,
                    quantity = stats.first,
                    revenue = stats.second,
                    percentage = if (totalRevenue > 0) (stats.second / totalRevenue).toFloat() else 0f
                )
            }
            .sortedByDescending { it.quantity }
            .take(5)
    }

    // KPI Summary Calculations
    val totalRevenue = remember(filteredBills) { filteredBills.sumOf { it.totalAmount } }
    val totalOrders = filteredBills.size
    val averageOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0
    val maxRevenueDay = remember(dailyMetrics) { dailyMetrics.maxByOrNull { it.revenue } }
    val maxOrdersDay = remember(dailyMetrics) { dailyMetrics.maxByOrNull { it.orderCount } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("visual_summary_dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Timeframe Selection Bar (7D, 14D, 30D)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CrispWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, CrispCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = WarmAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Analytics Range",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "7 Days", 14 to "14 Days", 30 to "30 Days").forEach { (days, label) ->
                        FilterChip(
                            selected = selectedTimeframeDays == days,
                            onClick = {
                                selectedTimeframeDays = days
                                selectedBarIndex = null
                            },
                            label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmAmber,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("filter_chip_$days")
                        )
                    }
                }
            }
        }

        // 2. Executive KPI Cards Row (Recharts Metric Badges)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = "Total Revenue",
                value = "₹${String.format(Locale.getDefault(), "%,.0f", totalRevenue)}",
                subtitle = "${dailyMetrics.size} Days Trend",
                icon = Icons.Default.CurrencyRupee,
                accentColor = WarmAmber,
                modifier = Modifier.weight(1f)
            )

            KpiMetricCard(
                title = "Total Orders",
                value = "$totalOrders",
                subtitle = "Avg. ${(totalOrders / max(1, dailyMetrics.size))} / day",
                icon = Icons.Default.ReceiptLong,
                accentColor = Color(0xFF2563EB),
                modifier = Modifier.weight(1f)
            )

            KpiMetricCard(
                title = "Avg Order Value",
                value = "₹${String.format(Locale.getDefault(), "%.0f", averageOrderValue)}",
                subtitle = "Per Bill (AOV)",
                icon = Icons.Default.TrendingUp,
                accentColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Daily Revenue Trend Chart (Recharts Rounded Bar Chart)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CrispWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, CrispCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(WarmAmber)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daily Revenue Trend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Tap any bar to inspect date & exact sales",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    if (maxRevenueDay != null && maxRevenueDay.revenue > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarmAmber.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Peak: ₹${String.format(Locale.getDefault(), "%,.0f", maxRevenueDay.revenue)} (${maxRevenueDay.dayName})",
                                color = DeepAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selected Bar Detail Callout Box
                if (selectedBarIndex != null && selectedBarIndex in dailyMetrics.indices) {
                    val selected = dailyMetrics[selectedBarIndex!!]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${selected.dateLabel} (${selected.dayName})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${selected.orderCount} Orders (${selected.dineInCount} Dine-In • ${selected.takeawayCount} Parcel)",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.0f", selected.revenue)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DeepAmber
                            )
                        }
                    }
                }

                // Canvas Recharts-style Bar Chart
                RechartsBarChartCanvas(
                    metrics = dailyMetrics,
                    selectedIdx = selectedBarIndex,
                    onBarSelected = { idx -> selectedBarIndex = idx },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }

        // 4. Order Volume Trend Chart (Recharts Spline Area Chart)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CrispWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, CrispCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Order Volume Trends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "$totalOrders Total Orders",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Canvas Recharts-style Area Curve Chart
                RechartsAreaChartCanvas(
                    metrics = dailyMetrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )
            }
        }

        // 5. Top-Selling Menu Items (Recharts Horizontal Ranked Bar Chart)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CrispWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, CrispCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Top-Selling Menu Items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = "By Quantity",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (topSellingItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No item sales recorded in this period yet",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    val maxQty = topSellingItems.firstOrNull()?.quantity ?: 1
                    topSellingItems.forEachIndexed { index, item ->
                        RechartsHorizontalBarItem(
                            rank = index + 1,
                            metric = item,
                            maxQty = maxQty
                        )
                        if (index < topSellingItems.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * KPI Metric Card component
 */
@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CrispWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, CrispCardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * Recharts-style Canvas Bar Chart for Daily Revenue
 */
@Composable
private fun RechartsBarChartCanvas(
    metrics: List<DailyTrendPoint>,
    selectedIdx: Int?,
    onBarSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxRev = remember(metrics) { max(1.0, metrics.maxOfOrNull { it.revenue } ?: 1.0) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(metrics) {
                    detectTapGestures { offset ->
                        val barWidthWithSpacing = size.width / metrics.size.toFloat()
                        val tappedIndex = (offset.x / barWidthWithSpacing).toInt()
                        if (tappedIndex in metrics.indices) {
                            onBarSelected(tappedIndex)
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val bottomLabelHeight = 45f
            val chartHeight = canvasHeight - bottomLabelHeight

            // Draw horizontal dotted grid lines (Recharts CartesianGrid)
            val gridSteps = 4
            for (i in 0..gridSteps) {
                val y = chartHeight * (i / gridSteps.toFloat())
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
            }

            // Draw bars
            val totalBars = metrics.size
            val slotWidth = canvasWidth / totalBars.toFloat()
            val barWidth = (slotWidth * 0.58f).coerceAtLeast(6f).coerceAtMost(36f)

            metrics.forEachIndexed { i, point ->
                val barFraction = (point.revenue / maxRev).toFloat()
                val barHeight = (chartHeight * barFraction).coerceAtLeast(if (point.revenue > 0) 6f else 2f)
                val xCenter = (i * slotWidth) + (slotWidth / 2f)
                val left = xCenter - (barWidth / 2f)
                val top = chartHeight - barHeight

                val isSelected = selectedIdx == i
                val barBrush = if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFEA580C), WarmAmber)
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(SaffronGold, WarmAmber)
                    )
                }

                // Bar rectangle with rounded top corners
                drawRoundRect(
                    brush = barBrush,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                )

                // Selection highlight ring if tapped
                if (isSelected) {
                    drawRoundRect(
                        color = DeepAmber,
                        topLeft = Offset(left - 2f, top - 2f),
                        size = Size(barWidth + 4f, barHeight + 4f),
                        cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }

        // Bottom Date/Day Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            metrics.forEachIndexed { idx, point ->
                val showLabel = if (metrics.size > 14) (idx % 4 == 0 || idx == metrics.lastIndex) else true
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLabel) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = point.dayName,
                                fontSize = 9.sp,
                                fontWeight = if (selectedIdx == idx) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedIdx == idx) DeepAmber else TextSecondary
                            )
                            Text(
                                text = point.dateLabel.split(" ").firstOrNull() ?: "",
                                fontSize = 8.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recharts-style Smooth Area Curve Chart for Order Volume
 */
@Composable
private fun RechartsAreaChartCanvas(
    metrics: List<DailyTrendPoint>,
    modifier: Modifier = Modifier
) {
    val maxOrders = remember(metrics) { max(1, metrics.maxOfOrNull { it.orderCount } ?: 1) }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val bottomLabelHeight = 40f
            val chartHeight = canvasHeight - bottomLabelHeight

            // Cartesian grid lines
            for (i in 0..3) {
                val y = chartHeight * (i / 3f)
                drawLine(
                    color = Color(0xFFF1F5F9),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1.5f
                )
            }

            if (metrics.isNotEmpty()) {
                val slotWidth = canvasWidth / (metrics.size - 1).coerceAtLeast(1)
                val points = metrics.mapIndexed { i, point ->
                    val fraction = point.orderCount / maxOrders.toFloat()
                    val y = chartHeight - (chartHeight * fraction).coerceAtLeast(4f)
                    Offset(i * slotWidth, y)
                }

                // Build smooth cubic bezier curve
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx1 = (p0.x + p1.x) / 2f
                        val cy1 = p0.y
                        val cx2 = (p0.x + p1.x) / 2f
                        val cy2 = p1.y
                        cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    }
                }

                // Gradient Area Fill under the curve
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(points.last().x, chartHeight)
                    lineTo(points.first().x, chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2563EB).copy(alpha = 0.35f),
                            Color(0xFF2563EB).copy(alpha = 0.02f)
                        ),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                // Line Stroke
                drawPath(
                    path = path,
                    color = Color(0xFF2563EB),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )

                // Draw dots on data points
                points.forEach { pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 4.5f,
                        center = pt
                    )
                    drawCircle(
                        color = Color(0xFF2563EB),
                        radius = 3f,
                        center = pt
                    )
                }
            }
        }

        // Bottom Date Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            metrics.forEachIndexed { idx, point ->
                val showLabel = if (metrics.size > 14) (idx % 5 == 0 || idx == metrics.lastIndex) else (idx % 2 == 0)
                if (showLabel) {
                    Text(
                        text = "${point.dayName} ${point.dateLabel.split(" ").firstOrNull() ?: ""}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

/**
 * Recharts Horizontal Bar Item for Top-Selling Menu Items
 */
@Composable
private fun RechartsHorizontalBarItem(
    rank: Int,
    metric: TopItemMetric,
    maxQty: Int
) {
    val barFraction = (metric.quantity / maxQty.toFloat()).coerceIn(0.05f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = barFraction,
        animationSpec = tween(durationMillis = 600),
        label = "progressBar"
    )

    val rankColor = when (rank) {
        1 -> Color(0xFFE65100) // Gold-Orange #1
        2 -> Color(0xFF0F766E) // Teal #2
        3 -> Color(0xFF2563EB) // Blue #3
        else -> Color(0xFF64748B) // Slate #4, #5
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = rankColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "#$rank",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = rankColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = metric.dishName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${metric.quantity} orders",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "₹${String.format(Locale.getDefault(), "%,.0f", metric.revenue)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF1F5F9))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(rankColor.copy(alpha = 0.8f), rankColor)
                        )
                    )
            )
        }
    }
}

// Data models for the Visual Dashboard
data class DailyTrendPoint(
    val dateKey: String,
    val dateLabel: String,
    val dayName: String,
    val revenue: Double,
    val orderCount: Int,
    val dineInCount: Int,
    val takeawayCount: Int
)

data class TopItemMetric(
    val dishName: String,
    val quantity: Int,
    val revenue: Double,
    val percentage: Float
)

/**
 * Recharts-based Daily Sales Summary Chart for Main Dashboard
 * Visualizes revenue trends for the last 7 days with interactive touch inspection.
 */
@Composable
fun RechartsDailySalesSummaryCard(
    bills: List<BillEntity>,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = true,
    onOpenFullReports: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(initialExpanded) }
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    val activeBills = remember(bills) {
        bills.filter { it.isSettled }
    }

    // 7-day metric aggregation
    val dailyMetrics = remember(activeBills) {
        val list = mutableListOf<DailyTrendPoint>()
        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfDisplay = SimpleDateFormat("dd MMM", Locale.getDefault())
        val sdfDayName = SimpleDateFormat("EEE", Locale.getDefault())

        val cutoffCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val recentBills = activeBills.filter { it.billTimestamp >= cutoffCal.timeInMillis }
        val billsByDate = recentBills.groupBy { sdfKey.format(Date(it.billTimestamp)) }

        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val key = sdfKey.format(dayCal.time)
            val dayBills = billsByDate[key] ?: emptyList()
            val dayRevenue = dayBills.sumOf { it.totalAmount }
            val dayOrders = dayBills.size
            val dayDineIn = dayBills.count { it.orderType == "DINE_IN" }
            val dayTakeaway = dayBills.count { it.orderType == "TAKEAWAY" }

            list.add(
                DailyTrendPoint(
                    dateKey = key,
                    dateLabel = sdfDisplay.format(dayCal.time),
                    dayName = sdfDayName.format(dayCal.time),
                    revenue = dayRevenue,
                    orderCount = dayOrders,
                    dineInCount = dayDineIn,
                    takeawayCount = dayTakeaway
                )
            )
        }
        list
    }

    val total7DayRevenue = remember(dailyMetrics) { dailyMetrics.sumOf { it.revenue } }
    val total7DayOrders = remember(dailyMetrics) { dailyMetrics.sumOf { it.orderCount } }
    val maxRevenueDay = remember(dailyMetrics) { dailyMetrics.maxByOrNull { it.revenue } }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CrispWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, CrispCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_daily_sales_chart_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row with 7-Day Revenue & Expand/Collapse Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarmAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Recharts Daily Sales",
                            tint = DeepAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "7-Day Sales Trend (Recharts)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Total: ₹${String.format(Locale.getDefault(), "%,.0f", total7DayRevenue)} • $total7DayOrders Orders",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (maxRevenueDay != null && maxRevenueDay.revenue > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SuccessGreen.copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "Peak: ₹${String.format(Locale.getDefault(), "%,.0f", maxRevenueDay.revenue)}",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = TextSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Selected Bar Tooltip / Detail Callout
                    if (selectedBarIndex != null && selectedBarIndex in dailyMetrics.indices) {
                        val selected = dailyMetrics[selectedBarIndex!!]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${selected.dateLabel} (${selected.dayName})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${selected.orderCount} Bills (${selected.dineInCount} Dine-In • ${selected.takeawayCount} Parcel)",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = "₹${String.format(Locale.getDefault(), "%,.0f", selected.revenue)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = DeepAmber
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Tap any bar to inspect daily revenue & orders",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Recharts Canvas Chart
                    RechartsBarChartCanvas(
                        metrics = dailyMetrics,
                        selectedIdx = selectedBarIndex,
                        onBarSelected = { idx -> selectedBarIndex = idx },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )

                    if (onOpenFullReports != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenFullReports,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Insights,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = WarmAmber
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Open Full Analytics & Reports",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WarmAmber
                            )
                        }
                    }
                }
            }
        }
    }
}
