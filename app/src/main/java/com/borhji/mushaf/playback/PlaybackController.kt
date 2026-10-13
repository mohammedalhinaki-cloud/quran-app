package com.borhji.mushaf.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.borhji.mushaf.data.LibraryRepository
import com.borhji.mushaf.data.PlayableTrack
import com.borhji.mushaf.data.PlayerPrefs
import com.borhji.mushaf.data.ProgressRecorder
import com.borhji.mushaf.data.SURAHS
import com.borhji.mushaf.data.SettingsRepository
import com.borhji.mushaf.util.ProgressMath
import com.borhji.mushaf.util.TimeFormat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * الجسر بين واجهة المستخدم وخدمة التشغيل (MediaController).
 *
 * ملاحظات مهمة:
 * - لا يبدأ التشغيل تلقائيًا عند فتح التطبيق؛ الاستئناف يحتاج ضغط المستخدم.
 * - يُبنى بترتيب أرقام السور لمكتبة الملفات المتوفرة فقط، فيتخطى الانتقال التالي/السابق
 *   السور غير المستوردة تلقائيًا.
 * - يحفظ الموضع دوريًا وفوريًا عند كل حدث (إيقاف/تحريك/تغيير سورة).
 */
@OptIn(UnstableApi::class)
class PlaybackController(
    private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val recorder: ProgressRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionToken = SessionToken(
        context,
        ComponentName(context, QuranPlaybackService::class.java),
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var prefs: PlayerPrefs = PlayerPrefs()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            sync()
            if (isPlaying) ensureTicker() else {
                stopTicker()
                persist(force = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            sync()
            val surah = PlaybackConstants.surahFromMediaId(controller?.currentMediaItem?.mediaId)
            val name = surah?.let { SURAHS.getOrNull(it - 1)?.name } ?: ""
            _messages.tryEmit(
                "تعذّر تشغيل ملف سورة $name (الملف مفقود أو تالف). " +
                    "يمكنك تعيين ملف آخر من القائمة."
            )
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            sync()
        }
    }

    init {
        scope.launch {
            prefs = settings.snapshot()
            _state.update { it.copy(speed = prefs.speed, repeatOne = prefs.repeatOne) }
        }
    }

    /** الاتصال بخدمة التشغيل (بدون بدء أي صوت) */
    fun connect() {
        if (controllerFuture != null) return
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(listener)
                    mediaController.setPlaybackSpeed(prefs.speed)
                    mediaController.repeatMode =
                        if (prefs.repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    _state.update { it.copy(connected = true) }
                    sync()
                } catch (_: Throwable) {
                    _state.update { it.copy(connected = false) }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        stopTicker()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    // ------------------------------------------------------------- أوامر التشغيل

    /**
     * تشغيل سورة.
     * @param startPositionMs موضع البداية، أو null للاستئناف من الموضع المحفوظ
     * @param autoPlay هل يبدأ الصوت فورًا؟
     */
    fun playSurah(surah: Int, startPositionMs: Long? = null, autoPlay: Boolean = true) {
        scope.launch {
            val tracks = library.playableTracks()
            if (tracks.isEmpty()) {
                _messages.emit("لم يتم استيراد أي تلاوات بعد. اضغط «استيراد التلاوات» أولًا.")
                return@launch
            }
            val index = tracks.indexOfFirst { it.surah == surah }
            if (index < 0) {
                val name = SURAHS.firstOrNull { it.number == surah }?.name ?: "$surah"
                _messages.emit("ملف «سورة $name» غير متوفر على الجهاز. يمكنك تعيين ملفها يدويًا.")
                return@launch
            }
            val mediaController = awaitController() ?: run {
                _messages.emit("تعذّر الاتصال بخدمة التشغيل. حاول مرة أخرى.")
                return@launch
            }

            val knownDuration = tracks[index].durationMs.takeIf { it > 0L }
                ?: (library.progressFor(surah)?.durationMs ?: 0L)
            val savedPosition = library.progressFor(surah)?.positionMs ?: 0L
            val startPosition = startPositionMs
                ?: ProgressMath.startPosition(savedPosition, knownDuration)

            try {
                mediaController.setMediaItems(
                    tracks.map { it.toMediaItem() },
                    index,
                    startPosition.coerceAtLeast(0L),
                )
                mediaController.prepare()
                if (autoPlay) mediaController.play() else mediaController.pause()
            } catch (_: Throwable) {
                _messages.emit("تعذّر بدء التشغيل. حاول مرة أخرى.")
                _state.update { it.copy(connected = false) }
                return@launch
            }
            recorder.onSurahChanged(surah, startPosition, knownDuration)
            _state.update { it.copy(errorMessage = null) }
            sync()
        }
    }

    /** تشغيل/إيقاف مؤقت */
    fun togglePlayPause() {
        scope.launch {
            val mediaController = controller
            if (mediaController == null || mediaController.mediaItemCount == 0) {
                val target = prefs.lastSurah ?: 1
                playSurah(target)
                return@launch
            }
            val playing = try {
                mediaController.isPlaying
            } catch (_: Throwable) {
                false
            }
            if (playing) {
                withController { it.pause() }
                persist(force = true)
            } else {
                withController { it.play() }
            }
            sync()
        }
    }

    fun pause() {
        scope.launch {
            withController { it.pause() }
            persist(force = true)
            sync()
        }
    }

    /** تحريك شريط التقدم إلى موضع محدد */
    fun seekTo(positionMs: Long) {
        scope.launch {
            if (!withController { it.seekTo(positionMs.coerceAtLeast(0L)) }) return@launch
            persist(force = true)
            sync()
        }
    }

    /** تقديم/تأخير بمقدار ثوانٍ (سالب للتأخير) */
    fun seekBy(deltaMs: Long) {
        scope.launch {
            val mediaController = controller ?: return@launch
            val duration = resolveDuration(mediaController)
            val target = (mediaController.currentPosition + deltaMs)
                .coerceAtLeast(0L)
                .let { if (duration > 0L) it.coerceAtMost(duration) else it }
            if (!withController { it.seekTo(target) }) return@launch
            persist(force = true)
            sync()
        }
    }

    /** السورة التالية في قائمة السور المتوفرة */
    fun next() {
        scope.launch {
            val mediaController = controller ?: return@launch
            if (mediaController.currentMediaItemIndex < mediaController.mediaItemCount - 1) {
                persist(force = true)
                withController { it.seekToNextMediaItem() }
                withController { it.prepare() }
                if (mediaController.playWhenReady) withController { it.play() }
                sync()
            } else {
                _messages.emit("هذه آخر سورة متوفرة في المكتبة.")
            }
        }
    }

    /** السورة السابقة في قائمة السور المتوفرة */
    fun previous() {
        scope.launch {
            val mediaController = controller ?: return@launch
            if (mediaController.currentMediaItemIndex > 0) {
                persist(force = true)
                withController { it.seekToPreviousMediaItem() }
                withController { it.prepare() }
                if (mediaController.playWhenReady) withController { it.play() }
                sync()
            } else {
                _messages.emit("هذه أول سورة في المكتبة.")
            }
        }
    }

    /** تغيير سرعة التشغيل (1.0x هي الافتراضية) */
    fun setSpeed(speed: Float) {
        scope.launch {
            val safe = speed.coerceIn(0.5f, 2.0f)
            withController { it.setPlaybackSpeed(safe) }
            settings.setSpeed(safe)
            _state.update { it.copy(speed = safe) }
        }
    }

    /** تكرار السورة الحالية */
    fun toggleRepeatOne() {
        scope.launch {
            val mediaController = controller ?: return@launch
            val enabled = mediaController.repeatMode != Player.REPEAT_MODE_ONE
            withController {
                it.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
            settings.setRepeatOne(enabled)
            _state.update { it.copy(repeatOne = enabled) }
        }
    }

    /** إيقاف التشغيل وإخفاء المشغل مع الاحتفاظ بالموضع المحفوظ */
    fun stopAndClear() {
        scope.launch {
            val mediaController = controller
            val surah = try {
                PlaybackConstants.surahFromMediaId(mediaController?.currentMediaItem?.mediaId)
            } catch (_: Throwable) {
                null
            }
            if (mediaController != null && surah != null) {
                try {
                    recorder.onStop(
                        surah,
                        mediaController.currentPosition,
                        resolveDuration(mediaController),
                    )
                } catch (_: Throwable) {
                    // نتجاهل: المهم ألا يفشل الإيقاف
                }
            }
            withController { it.stop() }
            withController { it.clearMediaItems() }
            stopTicker()
            _state.update {
                PlayerState(connected = true, speed = prefs.speed, repeatOne = prefs.repeatOne)
            }
        }
    }

    // ------------------------------------------------------------------ أدوات داخلية

    /**
     * تنفيذ أمر على المشغل بأمان: إذا كانت الخدمة قد أُوقفت من النظام وصار المتحكم
     * غير متصل، لا نُسقط الواجهة ولا نفقد الموضع المحفوظ — نتجاهل الأمر ونحدّث الحالة.
     */
    private inline fun withController(block: (MediaController) -> Unit): Boolean {
        val mediaController = controller ?: return false
        return try {
            block(mediaController)
            true
        } catch (_: Throwable) {
            _state.update { it.copy(connected = false) }
            false
        }
    }

    private suspend fun awaitController(timeoutMs: Long = 3_000L): MediaController? {
        controller?.let { return it }
        connect()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (controller == null && SystemClock.elapsedRealtime() < deadline) {
            delay(50L)
        }
        return controller
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                sync()
                persist(force = false)
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** مزامنة الحالة المعروضة مع المشغل */
    private fun sync() {
        val mediaController = controller
        if (mediaController == null) {
            _state.update { it.copy(connected = false) }
            return
        }
        val item = try {
            mediaController.currentMediaItem
        } catch (_: Throwable) {
            _state.update { it.copy(connected = false) }
            return
        }
        val surah = PlaybackConstants.surahFromMediaId(item?.mediaId)
        val duration = resolveDuration(mediaController)
        _state.update {
            it.copy(
                connected = true,
                surahNumber = surah,
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                isPlaying = mediaController.isPlaying,
                isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
                isEnded = mediaController.playbackState == Player.STATE_ENDED,
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                speed = mediaController.playbackParameters.speed,
                repeatOne = mediaController.repeatMode == Player.REPEAT_MODE_ONE,
            )
        }
    }

    private fun resolveDuration(mediaController: Player): Long {
        val fromPlayer = mediaController.duration
        if (fromPlayer != C.TIME_UNSET && fromPlayer > 0L) return fromPlayer
        val fromMetadata = mediaController.currentMediaItem?.mediaMetadata?.durationMs ?: C.TIME_UNSET
        return if (fromMetadata != C.TIME_UNSET && fromMetadata > 0L) fromMetadata else 0L
    }

    private fun persist(force: Boolean) {
        val mediaController = controller ?: return
        val surah = PlaybackConstants.surahFromMediaId(mediaController.currentMediaItem?.mediaId)
            ?: return
        val duration = resolveDuration(mediaController)
        val position = mediaController.currentPosition.coerceAtLeast(0L)
        if (force) recorder.onSeek(surah, position, duration) else recorder.onTick(surah, position, duration)
    }

    private fun PlayableTrack.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(PlaybackConstants.READER_NAME)
            .setAlbumTitle(PlaybackConstants.ALBUM_TITLE)
            .setIsBrowsable(false)
            .setIsPlayable(true)
        if (durationMs > 0L) metadataBuilder.setDurationMs(durationMs)

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(PlaybackConstants.mediaIdFor(surah))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /** وصف نصي مختصر لحالة المشغل (يُستخدم في الإشعارات الداخلية) */
    fun describePosition(): String {
        val current = _state.value
        return TimeFormat.describe(current.positionMs, current.durationMs)
    }

    @Suppress("unused")
    private fun Uri.label(): String = lastPathSegment ?: toString()

    private companion object {
        const val TICK_MS = 400L
    }
}
