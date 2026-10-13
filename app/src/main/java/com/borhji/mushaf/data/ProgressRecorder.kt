package com.borhji.mushaf.data

import android.os.SystemClock
import com.borhji.mushaf.util.ProgressMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * حافظ موضع الاستماع.
 *
 * القواعد:
 * - تحديث دوري أثناء التشغيل (كل ~3.5 ثانية لقاعدة البيانات، وكل ~20 ثانية لحالة الجلسة).
 * - تحديث فوري عند: الإيقاف المؤقت، تحريك شريط التقدم، تغيير السورة، إغلاق المشغل.
 * - الموضع يُحفظ لكل سورة على حدة، ولا يُصفَّر بسبب إغلاق التطبيق.
 */
class ProgressRecorder(
    private val progressDao: ProgressDao,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var lastDbWrite = 0L
    private var lastSettingsWrite = 0L

    /** نداء دوري أثناء التشغيل (مُقيَّد زمنيًا) */
    fun onTick(surah: Int, positionMs: Long, durationMs: Long) {
        record(surah, positionMs, durationMs, force = false, updateLast = true)
    }

    /** إيقاف مؤقت أو إنهاء تشغيل: حفظ فوري */
    fun onPause(surah: Int, positionMs: Long, durationMs: Long) {
        record(surah, positionMs, durationMs, force = true, updateLast = true)
    }

    /** تحريك شريط التقدم: حفظ فوري */
    fun onSeek(surah: Int, positionMs: Long, durationMs: Long) {
        record(surah, positionMs, durationMs, force = true, updateLast = true)
    }

    /** الانتقال إلى سورة أخرى: حفظ فوري وربط الجلسة بالسورة الجديدة */
    fun onSurahChanged(surah: Int, positionMs: Long, durationMs: Long) {
        record(surah, positionMs, durationMs, force = true, updateLast = true)
    }

    /**
     * انتهاء سورة والانتقال لسورة تالية: نحفظ موضع السورة السابقة في قاعدة البيانات
     * دون تغيير «آخر سورة» في الجلسة حتى لا يضيع مؤشر المتابعة.
     */
    fun onSurahFinished(surah: Int, positionMs: Long, durationMs: Long) {
        record(surah, positionMs, durationMs, force = true, updateLast = false)
    }

    /** إيقاف التشغيل نهائيًا: حفظ فوري */
    fun onStop(surah: Int, positionMs: Long, durationMs: Long) {
        record(surah, positionMs, durationMs, force = true, updateLast = true)
    }

    /** تصفير موضع سورة واحدة */
    fun resetSurah(surah: Int) {
        scope.launch {
            progressDao.delete(surah)
            settings.clearLastIfSurah(surah)
        }
    }

    private fun record(
        surah: Int,
        positionMs: Long,
        durationMs: Long,
        force: Boolean,
        updateLast: Boolean,
    ) {
        if (surah !in 1..114) return
        val now = clock()
        val writeDb = force || now - lastDbWrite >= DB_INTERVAL_MS
        val writeSession = updateLast && (force || now - lastSettingsWrite >= SESSION_INTERVAL_MS || force)
        if (!writeDb && !writeSession) return
        if (writeDb) lastDbWrite = now
        if (writeSession) lastSettingsWrite = now

        val safeDuration = durationMs.coerceAtLeast(0L)
        val position = ProgressMath.normalizePosition(positionMs, safeDuration)

        scope.launch {
            try {
                if (writeDb) {
                    progressDao.upsert(
                        ProgressEntity(
                            surahNumber = surah,
                            positionMs = position,
                            durationMs = safeDuration,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
                if (writeSession) {
                    settings.setLast(surah, position, safeDuration)
                }
            } catch (_: Throwable) {
                // لا نُسقط التطبيق إذا فشل الحفظ
            }
        }
    }

    companion object {
        const val DB_INTERVAL_MS = 3_500L
        const val SESSION_INTERVAL_MS = 20_000L
    }
}
