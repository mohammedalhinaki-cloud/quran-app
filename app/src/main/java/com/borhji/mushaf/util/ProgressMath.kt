package com.borhji.mushaf.util

/**
 * حسابات موضع الاستماع — منطق نقي (بدون اعتماديات أندرويد) وقابل للاختبار.
 */
object ProgressMath {

    /** إذا وصل المستمع إلى آخر 10 ثوانٍ من التلاوة تُعتبر منتهية ويُحفظ الموضع صفرًا */
    const val END_EPSILON_MS = 10_000L

    /** أقل من ثانية واحدة لا يُعتبر موضعًا محفوظًا */
    const val MIN_RESUME_MS = 1_000L

    /**
     * توحيد الموضع قبل الحفظ: يُقصّ إلى مدة التلاوة، ويُصفَّر عند نهايتها.
     */
    fun normalizePosition(positionMs: Long, durationMs: Long): Long {
        val pos = positionMs.coerceAtLeast(0L)
        if (durationMs > 0L && pos >= durationMs - END_EPSILON_MS) return 0L
        return pos
    }

    /** هل الموضع يستحق الاستئناف (وليس البداية)؟ */
    fun shouldResume(positionMs: Long, durationMs: Long): Boolean {
        val normalized = normalizePosition(positionMs, durationMs)
        return normalized >= MIN_RESUME_MS
    }

    /** موضع بدء التشغيل الفعلي (0 إذا كان الموضع غير محفوظ أو منتهيًا) */
    fun startPosition(positionMs: Long, durationMs: Long): Long =
        if (shouldResume(positionMs, durationMs)) normalizePosition(positionMs, durationMs) else 0L

    /** السورة التالية في ترتيب المصحف (114 → null) */
    fun nextSurah(current: Int, total: Int = 114): Int? =
        if (current in 1 until total) current + 1 else null

    /** السورة السابقة في ترتيب المصحف (1 → null) */
    fun previousSurah(current: Int): Int? =
        if (current > 1) current - 1 else null

    /** نسبة التقدم (0.0 - 1.0) */
    fun progressFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}
