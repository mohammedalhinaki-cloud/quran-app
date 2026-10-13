package com.borhji.mushaf.data

import android.net.Uri

/** مصدر ملف التلاوة المخزَّن */
enum class TrackSource {
    /** الملف على ذاكرة الهاتف مع صلاحية وصول دائمة (SAF) */
    DEVICE_LINK,

    /** نسخة داخل مساحة تخزين التطبيق الخاصة */
    APP_COPY,

    /** تعيين يدوي من المستخدم */
    MANUAL_ASSIGN,
}

/** صف سورة في واجهة قائمة السور */
data class SurahUiModel(
    val number: Int,
    val name: String,
    val revelation: String,
    val ayahCount: Int,
    val fileName: String?,
    val uri: Uri?,
    val available: Boolean,
    val durationMs: Long,
    val positionMs: Long,
)

/** مقطع صوتي جاهز للتشغيل */
data class PlayableTrack(
    val surah: Int,
    val name: String,
    val uri: Uri,
    val durationMs: Long,
)

/** نتيجة عملية استيراد كاملة */
data class ImportOutcome(
    val imported: Int,
    val duplicates: List<Int>,
    val unmatched: List<UnmatchedFile>,
    val missingSurahs: List<Int>,
    val failures: List<String>,
    val copyIntoApp: Boolean,
    val error: String? = null,
)

/** ملف لم يتم التعرف على سورته تلقائيًا */
data class UnmatchedFile(
    val uri: Uri,
    val name: String,
)

/** تقدّم عملية الاستيراد */
data class ImportProgress(
    val processed: Int,
    val total: Int,
    val currentName: String,
)

/** حالة الاستيراد المعروضة في الواجهة */
sealed interface ImportState {
    data class Running(val processed: Int, val total: Int, val currentName: String) : ImportState
    data class Finished(val outcome: ImportOutcome) : ImportState
    data class Failed(val message: String) : ImportState
}
