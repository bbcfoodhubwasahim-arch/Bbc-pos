package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.ReportExportUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Universal In-App PDF & Interactive Report Preview Dialog.
 * Allows instant document viewing inside the app, custom date range selection,
 * 1-click PDF download, 1-click Excel CSV export, and 1-click WhatsApp sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppReportViewerDialog(
    reportTitle: String,
    periodSubTitle: String,
    restaurantName: String,
    restaurantPhone: String,
    summaryMetrics: List<Pair<String, String>>,
    tableHeaders: List<String>,
    tableRows: List<List<String>>,
    warningNotes: List<String> = emptyList(),
    pdfFileGenerator: (Context) -> File,
    csvFileGenerator: (Context) -> File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("in_app_pdf_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // TOP BAR WITH ACTIONS
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = reportTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = periodSubTitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_pdf_dialog")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        // WhatsApp Share Button
                        IconButton(
                            onClick = {
                                try {
                                    isExporting = true
                                    val pdfFile = pdfFileGenerator(context)
                                    ReportExportUtil.shareReportFile(
                                        context = context,
                                        file = pdfFile,
                                        mimeType = "application/pdf",
                                        title = "Share $reportTitle via WhatsApp"
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isExporting = false
                                }
                            },
                            modifier = Modifier.testTag("btn_share_whatsapp_pdf")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share WhatsApp", tint = SuccessGreen)
                        }

                        // Export PDF Button
                        IconButton(
                            onClick = {
                                try {
                                    isExporting = true
                                    val pdfFile = pdfFileGenerator(context)
                                    val (success, path) = ReportExportUtil.saveToDownloads(context, pdfFile, "application/pdf")
                                    if (success) {
                                        Toast.makeText(context, "PDF Saved to Downloads!\n$path", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isExporting = false
                                }
                            },
                            modifier = Modifier.testTag("btn_export_pdf")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = ErrorRed)
                        }

                        // Export Excel CSV Button
                        IconButton(
                            onClick = {
                                try {
                                    isExporting = true
                                    val csvFile = csvFileGenerator(context)
                                    val (success, path) = ReportExportUtil.saveToDownloads(context, csvFile, "text/csv")
                                    if (success) {
                                        Toast.makeText(context, "Excel CSV Saved to Downloads!\n$path", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save Excel file", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error saving Excel file: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isExporting = false
                                }
                            },
                            modifier = Modifier.testTag("btn_export_excel")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = "Export Excel CSV", tint = CaramelWarm)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                if (isExporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // IN-APP DOCUMENT PREVIEW CANVAS CONTAINER
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0)) // Neutral slate paper background
                        .padding(12.dp)
                ) {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        // DOCUMENT HEADER STYLING
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = restaurantName.uppercase(Locale.getDefault()),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Contact: $restaurantPhone",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "OFFICIAL REPORT",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = reportTitle.uppercase(Locale.getDefault()),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Period: $periodSubTitle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // SUMMARY METRICS GRID
                        if (summaryMetrics.isNotEmpty()) {
                            Text(
                                text = "EXECUTIVE SUMMARY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                summaryMetrics.chunked(2).forEach { pairList ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pairList.forEach { (label, value) ->
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                color = Color(0xFFF8FAFC),
                                                shape = RoundedCornerShape(6.dp),
                                                border = CardDefaults.outlinedCardBorder()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF64748B),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = value,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                }
                                            }
                                        }
                                        if (pairList.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // WARNING / LEAKAGE NOTES IF ANY
                        if (warningNotes.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AUDIT & LEAKAGE NOTICES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    warningNotes.forEach { note ->
                                        Text("• $note", fontSize = 11.sp, color = Color(0xFF7F1D1D))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // REPORT DATA TABLE
                        if (tableHeaders.isNotEmpty() && tableRows.isNotEmpty()) {
                            Text(
                                text = "DETAILED BREAKDOWN LEDGER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                tableHeaders.forEachIndexed { index, header ->
                                    val weight = if (index == 0) 1.8f else 1.0f
                                    Text(
                                        text = header,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(weight),
                                        textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Table Data Rows
                            tableRows.forEachIndexed { rowIndex, rowData ->
                                val bg = if (rowIndex % 2 == 0) Color.White else Color(0xFFF8FAFC)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowData.forEachIndexed { colIndex, cell ->
                                        val weight = if (colIndex == 0) 1.8f else 1.0f
                                        val isAlert = cell.contains("CRITICAL") || cell.contains("HIGH LEAKAGE")
                                        val textColor = if (isAlert) Color(0xFFDC2626) else Color(0xFF1E293B)
                                        val fontWeight = if (colIndex == 0 || isAlert) FontWeight.Bold else FontWeight.Normal

                                        Text(
                                            text = cell,
                                            fontSize = 10.sp,
                                            fontWeight = fontWeight,
                                            color = textColor,
                                            modifier = Modifier.weight(weight),
                                            textAlign = if (colIndex == 0) TextAlign.Start else TextAlign.End,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Divider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // FOOTER DISCLOSURE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Generated automatically via POS System",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // BOTTOM ACTION TOOLBAR
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val csvFile = csvFileGenerator(context)
                                    val (success, path) = ReportExportUtil.saveToDownloads(context, csvFile, "text/csv")
                                    if (success) {
                                        Toast.makeText(context, "Excel CSV Downloaded: $path", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("btn_bottom_excel")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel (CSV)", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                try {
                                    val pdfFile = pdfFileGenerator(context)
                                    ReportExportUtil.shareReportFile(
                                        context = context,
                                        file = pdfFile,
                                        mimeType = "application/pdf",
                                        title = "Share $reportTitle"
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.weight(1.2f).testTag("btn_bottom_whatsapp")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share WhatsApp", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
