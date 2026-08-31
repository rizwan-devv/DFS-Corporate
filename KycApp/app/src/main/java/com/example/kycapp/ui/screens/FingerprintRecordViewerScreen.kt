package com.example.kycapp.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kycapp.biometrics.fingerprint.decodeBase64Png
import com.example.kycapp.biometrics.fingerprint.decodeIso19794
import com.example.kycapp.biometrics.fingerprint.decodeWsq
import com.example.kycapp.data.AppDatabase
import com.example.kycapp.data.FingerprintTemplateEntity
import com.example.kycapp.data.FingerprintTemplateRepository
import com.example.kycapp.ui.components.DfsAnimatedSection
import com.example.kycapp.ui.components.DfsCard
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsProgressIndicator
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsScreenHeader
import com.example.kycapp.ui.theme.DfsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ExportFormat(val label: String, val description: String) {
    ORIGINAL(
        "Original",
        "The plain photo as captured, no compression -- decoded losslessly from the Base64 record."
    ),
    WSQ(
        "WSQ",
        "NIST/FBI wavelet-compressed fingerprint format -- lossy, so this may look very slightly softer than Original."
    ),
    BASE64(
        "Base64",
        "A PNG of the captured photo, Base64-encoded as text -- losslessly identical to Original."
    ),
    ISO(
        "ISO 19794-4",
        "A simplified ISO/IEC 19794-4-inspired finger image record (header + raw pixels) -- losslessly identical to Original."
    )
}

/**
 * Lets the user inspect an enrolled fingerprint both as the plain captured
 * photo and in the same three export formats mod_fingerprint.py's
 * export_fingerprint_formats() produces (WSQ / Base64 PNG / ISO
 * 19794-4-inspired record). These formats are export-only -- matching still
 * runs on the ORB/AKAZE templates stored alongside them.
 */
@Composable
fun FingerprintRecordViewerScreen(
    personNameFilter: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember {
        FingerprintTemplateRepository(AppDatabase.getInstance(context).fingerprintTemplateDao())
    }

    var records by remember { mutableStateOf<List<FingerprintTemplateEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<FingerprintTemplateEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<FingerprintTemplateEntity?>(null) }

    suspend fun reload() {
        records = if (personNameFilter.isBlank()) {
            repository.allRecords()
        } else {
            repository.recordsForPerson(personNameFilter)
        }
    }

    LaunchedEffect(personNameFilter) {
        reload()
        loading = false
    }

    DfsScreen(scrollable = false) {
        DfsAnimatedSection {
            DfsScreenHeader(
                title = if (personNameFilter.isBlank()) "Stored Fingerprint Records" else "$personNameFilter's Records",
                subtitle = "Tap a record to preview export formats.",
                badge = "Records",
                onBack = onBack
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    DfsProgressIndicator()
                }
            }
            records.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No enrolled fingerprints yet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records) { record ->
                    DfsCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = record }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val handLabel = record.hand.lowercase().replaceFirstChar { it.uppercase() }
                                Text(
                                    "${record.personName} — $handLabel hand",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DfsColors.OnBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Sample ${record.sampleSlot} · ${record.orbFeatureCount} ORB + ${record.akazeFeatureCount} AKAZE features",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { pendingDelete = record }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete this record",
                                    tint = DfsColors.Danger
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        DfsPrimaryButton(text = "Done", onClick = onBack)
    }

    selected?.let { record ->
        RecordDetailDialog(record = record, onDismiss = { selected = null })
    }

    pendingDelete?.let { record ->
        val handLabel = record.hand.lowercase().replaceFirstChar { it.uppercase() }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = DfsColors.SurfaceElevated,
            titleContentColor = DfsColors.OnBackground,
            textContentColor = DfsColors.MutedText,
            title = { Text("Delete record?") },
            text = {
                Text(
                    "This permanently deletes ${record.personName}'s $handLabel hand, sample ${record.sampleSlot} " +
                        "— the fingerprint template and all its WSQ/Base64/ISO exports. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteRecord(record)
                        if (selected?.id == record.id) selected = null
                        pendingDelete = null
                        reload()
                    }
                }) {
                    Text("Delete", color = DfsColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = DfsColors.MutedText)
                }
            }
        )
    }
}

@Composable
private fun RecordDetailDialog(record: FingerprintTemplateEntity, onDismiss: () -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.ORIGINAL) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var sizeLabel by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enlarged by remember { mutableStateOf(false) }

    LaunchedEffect(format, record) {
        preview = null
        error = null
        try {
            val (bitmap, sizeText) = withContext(Dispatchers.Default) { decodePreview(record, format) }
            preview = bitmap
            sizeLabel = sizeText
        } catch (e: Exception) {
            error = "Failed to decode ${format.label}: ${e.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DfsColors.SurfaceElevated,
        titleContentColor = DfsColors.OnBackground,
        textContentColor = DfsColors.MutedText,
        title = { Text("${record.personName} — ${record.hand} hand, sample ${record.sampleSlot}") },
        text = {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormat.entries.forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DfsColors.Primary.copy(alpha = 0.25f),
                                selectedLabelColor = DfsColors.OnBackground,
                                containerColor = DfsColors.SurfaceElevated,
                                labelColor = DfsColors.MutedText
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(format.description, fontSize = 11.sp, color = DfsColors.MutedText)
                Spacer(modifier = Modifier.height(12.dp))
                val currentPreview = preview
                val currentError = error
                when {
                    currentError != null -> Text(currentError, color = DfsColors.Danger)
                    currentPreview != null -> {
                        Image(
                            bitmap = currentPreview.asImageBitmap(),
                            contentDescription = "${format.label} preview — tap to enlarge",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, DfsColors.Border, RoundedCornerShape(12.dp))
                                .clickable { enlarged = true }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap image to enlarge", fontSize = 11.sp, color = DfsColors.MutedText)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(sizeLabel, fontSize = 13.sp, color = DfsColors.OnBackground)
                        if (format == ExportFormat.BASE64) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = record.base64Export.take(300) +
                                    if (record.base64Export.length > 300) "…" else "",
                                fontSize = 11.sp,
                                color = DfsColors.MutedText
                            )
                        }
                    }
                    else -> DfsProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = DfsColors.Primary) }
        }
    )

    if (enlarged && preview != null) {
        Dialog(onDismissRequest = { enlarged = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DfsColors.Background.copy(alpha = 0.95f))
                    .clickable { enlarged = false },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = preview!!.asImageBitmap(),
                    contentDescription = "${format.label} preview, enlarged",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                )
            }
        }
    }
}

private data class PreviewData(val pixels: ByteArray, val width: Int, val height: Int, val sizeBytes: Int)

private fun decodePreview(record: FingerprintTemplateEntity, format: ExportFormat): Pair<Bitmap, String> {
    val data = when (format) {
        ExportFormat.ORIGINAL -> {
            val (p, w, h) = decodeBase64Png(record.base64Export)
            PreviewData(p, w, h, record.base64Export.length)
        }
        ExportFormat.WSQ -> {
            val (p, w, h) = decodeWsq(record.wsqExport)
            PreviewData(p, w, h, record.wsqExport.size)
        }
        ExportFormat.ISO -> {
            val (p, w, h) = decodeIso19794(record.isoExport)
            PreviewData(p, w, h, record.isoExport.size)
        }
        ExportFormat.BASE64 -> {
            val (p, w, h) = decodeBase64Png(record.base64Export)
            PreviewData(p, w, h, record.base64Export.length)
        }
    }
    val sizeLabel = if (format == ExportFormat.ORIGINAL) {
        "${data.width}x${data.height} pixels"
    } else {
        "${data.sizeBytes} bytes"
    }
    return grayBytesToBitmap(data.pixels, data.width, data.height) to sizeLabel
}

private fun grayBytesToBitmap(pixels: ByteArray, width: Int, height: Int): Bitmap {
    val argb = IntArray(width * height)
    for (i in argb.indices) {
        val g = pixels[i].toInt() and 0xFF
        argb[i] = AndroidColor.rgb(g, g, g)
    }
    return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
}
