package com.borhji.mushaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.borhji.mushaf.data.SurahUiModel
import com.borhji.mushaf.playback.PlayerState
import com.borhji.mushaf.util.ProgressMath
import com.borhji.mushaf.util.TimeFormat

@Composable
fun HomeScreen(
    state: HomeUiState,
    player: PlayerState,
    contentPadding: PaddingValues,
    onOpenImport: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleMissingOnly: () -> Unit,
    onPlaySurah: (Int) -> Unit,
    onResume: () -> Unit,
    onAssignFile: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onResetProgress: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item(key = "header") { HeaderSection() }

        val resumeSurah = state.resumeSurah
        if (resumeSurah != null) {
            item(key = "resume") {
                ResumeCard(
                    state = state,
                    onResume = onResume,
                    onAssign = { onAssignFile(resumeSurah) },
                )
            }
        }

        item(key = "import") {
            ImportSection(
                imported = state.importedCount,
                missing = state.missingCount,
                onOpenImport = onOpenImport,
            )
        }

        if (state.importedCount == 0) {
            item(key = "empty") {
                EmptyLibraryCard(onPickFiles = onPickFiles, onPickFolder = onPickFolder)
            }
        }

        item(key = "search") {
            SearchSection(
                query = state.query,
                missingOnly = state.showMissingOnly,
                missingCount = state.missingCount,
                onQueryChange = onQueryChange,
                onToggleMissingOnly = onToggleMissingOnly,
            )
        }

        items(items = state.filtered, key = { it.number }) { model ->
            SurahRow(
                model = model,
                isCurrent = player.surahNumber == model.number,
                isPlaying = player.surahNumber == model.number && player.isPlaying,
                onPlay = { onPlaySurah(model.number) },
                onAssign = { onAssignFile(model.number) },
                onRemove = { onRemoveTrack(model.number) },
                onResetProgress = { onResetProgress(model.number) },
            )
        }

        item(key = "footer") { FooterNote() }
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "المصحف المرتل",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "أ.د. محمد برهجي",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "حفص عن عاصم بقصر المنفصل",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResumeCard(
    state: HomeUiState,
    onResume: () -> Unit,
    onAssign: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "متابعة الاستماع",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "سورة ${state.resumeName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.resumePositionMs > 0L) {
                    "الموضع المحفوظ: ${TimeFormat.clock(state.resumePositionMs)}" +
                        if (state.resumeDurationMs > 0L) {
                            " من ${TimeFormat.clock(state.resumeDurationMs)}"
                        } else ""
                } else {
                    "لم يبدأ الاستماع لهذه السورة بعد"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.resumeAvailable) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "ملف هذه السورة غير متوفر حاليًا",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, enabled = state.resumeAvailable) {
                    Text(if (state.resumePositionMs > 0L) "متابعة الاستماع" else "الاستماع من البداية")
                }
                if (!state.resumeAvailable) {
                    OutlinedButton(onClick = onAssign) {
                        Text("تعيين ملف")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportSection(imported: Int, missing: Int, onOpenImport: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "المكتبة: $imported سورة مستوردة",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (missing > 0) "$missing سورة بلا ملفات" else "المصحف كامل — 114 سورة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onOpenImport) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("استيراد التلاوات")
        }
    }
}

@Composable
private fun EmptyLibraryCard(onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = "لم تُستورد أي تلاوات بعد",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "اختر ملفات تلاوات الشيخ محمد برهجي من ذاكرة الهاتف (حتى 114 ملفًا في المرة " +
                    "الواحدة بصيغة MP3 أو M4A). يتعرّف التطبيق على السور من أسماء الملفات أو أرقامها " +
                    "ويرتّبها ترتيب المصحف من الفاتحة إلى الناس، وتبقى الملفات على جهازك بدون رفعها لأي خادم.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPickFiles) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("استيراد التلاوات")
            }
            TextButton(onClick = onPickFolder) {
                Text("أو اختر مجلدًا كاملًا")
            }
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    missingOnly: Boolean,
    missingCount: Int,
    onQueryChange: (String) -> Unit,
    onToggleMissingOnly: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("ابحث باسم السورة أو رقمها") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = missingOnly,
            onClick = onToggleMissingOnly,
            label = { Text("غير المُستوردة فقط ($missingCount)") },
        )
    }
}

@Composable
private fun SurahRow(
    model: SurahUiModel,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
    onResetProgress: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        onClick = onPlay,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // رقم السورة
                Surface(
                    shape = CircleShape,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = model.number.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildSubtitle(model),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (model.available) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model.available && model.positionMs > 0L) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "الموضع المحفوظ: " +
                                TimeFormat.describe(model.positionMs, model.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (isCurrent && isPlaying) {
                    PlayingBars()
                    Spacer(Modifier.width(4.dp))
                }

                if (!model.available) {
                    IconButton(onClick = onAssign) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "تعيين ملف للسورة",
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "خيارات السورة")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (model.available) "تغيير ملف السورة" else "تعيين ملف") },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onAssign()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("تصفير الموضع المحفوظ") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onResetProgress()
                            },
                        )
                        if (model.fileName != null) {
                            DropdownMenuItem(
                                text = { Text("حذف الملف من المكتبة") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }

            if (model.available && model.positionMs > 0L && model.durationMs > 0L) {
                LinearProgressIndicator(
                    progress = {
                        ProgressMath.progressFraction(model.positionMs, model.durationMs)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

private fun buildSubtitle(model: SurahUiModel): String {
    val parts = mutableListOf<String>()
    if (!model.available) parts += "غير مُستورَدة"
    parts += model.revelation
    parts += "آياتها ${model.ayahCount}"
    if (model.available && model.durationMs > 0L) parts += TimeFormat.clock(model.durationMs)
    return parts.joinToString(" • ")
}

@Composable
private fun FooterNote() {
    Text(
        text = "جميع الملفات محلية على جهازك — لا يرفع التطبيق أي صوت إلى الإنترنت، " +
            "ويعمل بالكامل دون اتصال. يُحفظ موضع الاستماع تلقائيًا لكل سورة.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    )
}
