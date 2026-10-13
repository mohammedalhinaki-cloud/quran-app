package com.borhji.mushaf.util

import java.util.Locale

/** تنسيق الأوقات بصيغة mm:ss أو h:mm:ss */
object TimeFormat {

    fun clock(millis: Long): String {
        if (millis <= 0L) return "00:00"
        val totalSeconds = millis / 1000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3600L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** وصف نصي عربي لموضع الاستماع */
    fun describe(positionMs: Long, durationMs: Long): String {
        return if (durationMs > 0L) {
            "${clock(positionMs)} / ${clock(durationMs)}"
        } else {
            clock(positionMs)
        }
    }

    /** صيغة السرعة: 1.0x و 1.5x و 0.75x (بدون أصفار زائدة) */
    fun formatSpeed(speed: Float): String {
        val rounded = Math.round(speed * 100f) / 100f
        val text = String.format(Locale.US, "%.2f", rounded)
        val trimmed = if (text.endsWith("0")) text.dropLast(1) else text
        return "${trimmed}x"
    }

    /** عدد الثواني بصيغة عربية مختصرة: 10 ثوانٍ */
    fun secondsLabel(seconds: Int): String = "$seconds ث"
}
