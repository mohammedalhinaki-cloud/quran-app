package com.borhji.mushaf.playback

/** حالة المشغل كما تعرضها الواجهة */
data class PlayerState(
    val connected: Boolean = false,
    val surahNumber: Int? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val repeatOne: Boolean = false,
    val errorMessage: String? = null,
)

/** ثوابت المشغل */
object PlaybackConstants {
    const val READER_NAME = "أ.د. محمد برهجي"
    const val ALBUM_TITLE = "المصحف المرتل — حفص عن عاصم بقصر المنفصل"
    const val NOTIFICATION_CHANNEL_ID = "borhji_playback"

    fun mediaIdFor(surah: Int): String = "surah_$surah"

    fun surahFromMediaId(mediaId: String?): Int? {
        if (mediaId == null) return null
        val value = mediaId.removePrefix("surah_").toIntOrNull() ?: return null
        return if (value in 1..114) value else null
    }
}
