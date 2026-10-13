package com.borhji.mushaf.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.borhji.mushaf.MainActivity
import com.borhji.mushaf.MushafApp
import com.borhji.mushaf.R
import com.borhji.mushaf.di.AppContainer
import kotlinx.coroutines.launch

/**
 * خدمة تشغيل التلاوة.
 *
 * - تعمل كخدمة أمامية من نوع mediaPlayback، فيستمر الصوت عند قفل الشاشة
 *   أو الانتقال لتطبيق آخر أو إزالة واجهة التطبيق من التطبيقات الأخيرة.
 * - تُظهر إشعار المشغل بأزرار التحكم، وتستجيب لأزرار السماعات السلكية والبلوتوث.
 * - ExoPlayer يتولى التركيز الصوتي (Audio Focus) فيتوقف الصوت عند المكالمات.
 * - تحفظ موضع الاستماع دوريًا كل 3 ثوانٍ حتى لو أُغلقت الواجهة.
 */
@OptIn(UnstableApi::class)
class QuranPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private lateinit var container: AppContainer

    private val handler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0
    private var lastErrorAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            recordProgress(force = false)
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
                recordProgress(force = true)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                recordProgress(force = true)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val oldSurah = PlaybackConstants.surahFromMediaId(oldPosition.mediaItem?.mediaId)
            val newSurah = PlaybackConstants.surahFromMediaId(newPosition.mediaItem?.mediaId)
            if (oldSurah != null && newSurah != null && oldSurah != newSurah) {
                // حفظ موضع السورة المنتهية دون تغيير مؤشر المتابعة
                container.recorder.onSurahFinished(
                    oldSurah,
                    oldPosition.positionMs,
                    0L, // المدة غير متوفرة هنا؛ يُحتفظ بالمدة المحفوظة سابقًا
                )
                container.recorder.onSurahChanged(newSurah, newPosition.positionMs, 0L)
            } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                recordProgress(force = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = (application as MushafApp).container

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exoPlayer.addListener(playerListener)
        player = exoPlayer

        // استعادة سرعة التشغيل ووضع التكرار المحفوظين
        container.appScope.launch {
            val prefs = container.settings.snapshot()
            exoPlayer.setPlaybackSpeed(prefs.speed)
            exoPlayer.repeatMode =
                if (prefs.repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(PlaybackConstants.NOTIFICATION_CHANNEL_ID)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        // إشعار التشغيل: يُدار من الخدمة نفسها (MediaSessionService)
        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * عند إزالة واجهة التطبيق من التطبيقات الأخيرة:
     * - إذا كان التشغيل جاريًا نُبقي الخدمة تعمل ليستمر الصوت.
     * - إذا كان متوقفًا نُنهي الخدمة لتحرير الموارد.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopTicker()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_INTERVAL_MS)
    }

    private fun stopTicker() {
        handler.removeCallbacks(ticker)
    }

    /** حفظ دوري لموضع الاستماع (يعمل حتى لو أُغلقت واجهة التطبيق) */
    private fun recordProgress(force: Boolean) {
        val current = player ?: return
        val surah = PlaybackConstants.surahFromMediaId(current.currentMediaItem?.mediaId) ?: return
        val duration = resolveDuration(current)
        val position = current.currentPosition.coerceAtLeast(0L)
        if (force) {
            container.recorder.onPause(surah, position, duration)
        } else if (current.isPlaying) {
            container.recorder.onTick(surah, position, duration)
        }
    }

    private fun resolveDuration(current: Player): Long {
        val fromPlayer = current.duration
        if (fromPlayer != C.TIME_UNSET && fromPlayer > 0L) return fromPlayer
        val fromMetadata = current.currentMediaItem?.mediaMetadata?.durationMs ?: C.TIME_UNSET
        return if (fromMetadata != C.TIME_UNSET && fromMetadata > 0L) fromMetadata else 0L
    }

    /**
     * معالجة الأخطاء دون انهيار التطبيق: إذا تعذّر تشغيل ملف (مفقود أو تالف)
     * ننتقل تلقائيًا إلى السورة التالية بدلًا من التوقف الصامت.
     */
    private fun handlePlaybackError(error: PlaybackException) {
        val current = player ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastErrorAt > ERROR_WINDOW_MS) consecutiveErrors = 0
        lastErrorAt = now
        consecutiveErrors++

        val hasNext = current.currentMediaItemIndex < current.mediaItemCount - 1
        if (consecutiveErrors <= MAX_AUTO_SKIPS && hasNext) {
            current.seekToNextMediaItem()
            current.prepare()
            current.play()
        } else {
            current.pause()
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TICK_INTERVAL_MS = 3_000L
        const val ERROR_WINDOW_MS = 60_000L
        const val MAX_AUTO_SKIPS = 5
    }
}
