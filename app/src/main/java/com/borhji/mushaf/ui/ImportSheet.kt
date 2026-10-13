package com.borhji.mushaf.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.borhji.mushaf.data.ImportOutcome
import com.borhji.mushaf.data.ImportState
import com.borhji.mushaf.data.SURAH_BY_NUMBER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    importState: ImportState?,
    copyIntoApp: Boolean,
    onCopyIntoAppChange: (Boolean) -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit,
    onAssignUnmatched: (Uri, String) -> Unit,
    onShowMissing: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "استيراد التلاوات",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "اختر ملفات تلاوات الشيخ محمد برهجي من ذاكرة الهاتف (حتى 114 ملفًا في المرة " +
                    "الواحدة، بصيغة MP3 أو M4A). يتعرّف التطبيق تلقائيًا على السور من أسماء الملفات أو " +
                    "أرقامها ويرتّبها ترتيب المصحف من الفاتحة إلى الناس. لا يتم رفع أي ملف إلى أي خادم.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "نسخ الملفات إلى مساحة التطبيق",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "موصى به: يضمن الوصول للملفات بعد إعادة تشغيل الهاتف أو نقلها. " +
                                "عند إلغائه يربط التطبيق الملفات من مكانها الأصلي بدون نسخ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = copyIntoApp, onCheckedChange = onCopyIntoAppChange)
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = onPickFiles, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("اختيار ملفات التلاوات (MP3 / M4A)")
            }
            TextButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                Text("أو اختر مجلدًا كاملًا يحتوي التلاوات")
            }

            when (importState) {
                is ImportState.Running -> {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (importState.total > 0) {
                            "جارٍ الاستيراد: ${importState.processed} من ${importState.total}"
                        } else {
                            "جارٍ الاستيراد…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (importState.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                importState.processed.toFloat() /
                                    importState.total.toFloat().coerceAtLeast(1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = importState.currentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is ImportState.Finished -> {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    ImportSummary(
                        outcome = importState.outcome,
                        onAssignUnmatched = onAssignUnmatched,
                        onShowMissing = onShowMissing,
                    )
                }

                is ImportState.Failed -> {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = importState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                null -> Unit
            }
        }
    }
}

@Composable
private fun ImportSummary(
    outcome: ImportOutcome,
    onAssignUnmatched: (Uri, String) -> Unit,
    onShowMissing: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (outcome.error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = outcome.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        SummaryRow(
            icon = Icons.Filled.Check,
            title = "تم استيراد ${outcome.imported} سورة",
            detail = if (outcome.copyIntoApp) "نُسخت الملفات إلى مساحة التطبيق." else "تم ربط الملفات من مكانها.",
        )

        if (outcome.duplicates.isNotEmpty()) {
            val names = outcome.duplicates.take(5).joinToString("، ") {
                SURAH_BY_NUMBER[it]?.name ?: it.toString()
            }
            SummaryRow(
                icon = Icons.Filled.Info,
                title = "تم تجاهل ${outcome.duplicates.size} ملف مكرر",
                detail = "السور المكررة: $names",
            )
        }

        if (outcome.failures.isNotEmpty()) {
            SummaryRow(
                icon = Icons.Filled.Warning,
                title = "${outcome.failures.size} ملف تعذّرت قراءته",
                detail = outcome.failures.take(3).joinToString("\n"),
            )
        }

        if (outcome.unmatched.isNotEmpty()) {
            Text(
                text = "ملفات لم يتم التعرف على سورتها (${outcome.unmatched.size}) — " +
                    "اضغط «تعيين» لاختيار السورة الصحيحة:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            outcome.unmatched.take(8).forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAssignUnmatched(file.uri, file.name) }) {
                        Text("تعيين")
                    }
                }
            }
            if (outcome.unmatched.size > 8) {
                Text(
                    text = "و ${outcome.unmatched.size - 8} ملفًا آخر… يمكنك تعيينها لاحقًا من قائمة السور.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (outcome.missingSurahs.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${outcome.missingSurahs.size} سورة بدون ملفات",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "يمكنك استيراد ملفاتها لاحقًا في أي وقت، وسيظهر رقمها في القائمة دائمًا.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onShowMissing) {
                        Text("عرض السور غير المستوردة")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
