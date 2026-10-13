package com.borhji.mushaf.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.borhji.mushaf.data.SURAH_BY_NUMBER
import com.borhji.mushaf.playback.PlaybackConstants
import com.borhji.mushaf.playback.PlayerState
import com.borhji.mushaf.util.ProgressMath
import com.borhji.mushaf.util.TimeFormat

/** سرعات التشغيل المتاحة — 1.0x هي الافتراضية */
val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

/** معرّف اختباري للمشغل المصغّر (يُستخدم في الاختبارات الحقيقية على الجهاز) */
const val MINI_PLAYER_TAG = "mini-player"

/** المشغل المصغّر الثابت أسفل الشاشة */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
) {
    val name = state.surahNumber?.let { SURAH_BY_NUMBER[it]?.name } ?: state.title
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MINI_PLAYER_TAG),
    ) {
        Column {
            LinearProgressIndicator(
                progress = { ProgressMath.progressFraction(state.positionMs, state.durationMs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (name.isNullOrBlank()) "التلاوة" else name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = TimeFormat.describe(state.positionMs, state.durationMs) +
                            if (state.isBuffering) " • جارٍ التحضير…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "إيقاف مؤقت" else "تشغيل",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "السورة التالية",
                    )
                }
            }
        }
    }
}

/** المشغل الموسّع */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleRepeat: () -> Unit,
    onStop: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val name = state.surahNumber?.let { SURAH_BY_NUMBER[it]?.name } ?: state.title
    var dragValue by remember { mutableFloatStateOf(-1f) }
    val duration = state.durationMs.coerceAtLeast(0L)
    val sliderMax = if (duration > 0L) duration.toFloat() else 1f
    val sliderValue = if (dragValue >= 0f) dragValue else state.positionMs.toFloat()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = if (name.isNullOrBlank()) "التلاوة" else "سورة $name",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = PlaybackConstants.READER_NAME,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "حفص عن عاصم بقصر المنفصل",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Slider(
                value = sliderValue.coerceIn(0f, sliderMax),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    if (dragValue >= 0f) {
                        onSeekTo(dragValue.toLong())
                        dragValue = -1f
                    }
                },
                valueRange = 0f..sliderMax,
                enabled = duration > 0L,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = TimeFormat.clock(if (dragValue >= 0f) dragValue.toLong() else state.positionMs),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = if (duration > 0L) TimeFormat.clock(duration) else "--:--",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "السورة السابقة")
                }
                FilledTonalIconButton(onClick = { onSeekBy(-10_000L) }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "تأخير 10 ثوانٍ")
                }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "إيقاف مؤقت" else "تشغيل",
                        modifier = Modifier.size(40.dp),
                    )
                }
                FilledTonalIconButton(onClick = { onSeekBy(10_000L) }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "تقديم 10 ثوانٍ")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "السورة التالية")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "سرعة التشغيل",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                SPEED_OPTIONS.forEach { speed ->
                    FilterChip(
                        selected = kotlin.math.abs(state.speed - speed) < 0.01f,
                        onClick = { onSetSpeed(speed) },
                        label = { Text(TimeFormat.formatSpeed(speed)) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            FilterChip(
                selected = state.repeatOne,
                onClick = { onToggleRepeat() },
                label = { Text(if (state.repeatOne) "تكرار السورة: مفعّل" else "تكرار السورة: متوقف") },
                leadingIcon = {
                    Icon(
                        imageVector = if (state.repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إيقاف وإخفاء المشغل")
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إغلاق")
                }
            }

            Text(
                text = "يُحفظ موضع الاستماع تلقائيًا لكل سورة، وتستطيع المتابعة من حيث توقفت في أي وقت.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** مؤشر متحرك يدل على السورة قيد التشغيل */
@Composable
fun PlayingBars(color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "playing")
    val bars = List(3) { index ->
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 480 + index * 140,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$index",
        )
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(18.dp),
    ) {
        bars.forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(heightFraction.value)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}
