package com.borhji.mushaf.data.importer

/**
 * أدوات معالجة النصوص العربية لتسهيل مطابقة أسماء ملفات التلاوات.
 * لا تعتمد على أي مكتبة خارجية، وقابلة للاختبار بالكامل بوحدة اختبارات JVM.
 */
object ArabicText {

    /** التشكيل والعلامات القرآنية والتطويل */
    private val DIACRITICS = Regex(
        "[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06DC\\u06DF-\\u06E8\\u06EA-\\u06ED\\u0640]"
    )

    private val ARABIC_INDIC = Regex("[\\u0660-\\u0669]")
    private val EXTENDED_ARABIC_INDIC = Regex("[\\u06F0-\\u06F9]")

    /**
     * توحيد النص: إزالة التشكيل، توحيد الهمزات والألفات، توحيد التاء المربوطة،
     * تحويل الأرقام العربية إلى إنجليزية، وتحويل الأحرف إلى صغيرة.
     */
    fun normalize(input: String): String {
        var s = input.lowercase()
        s = DIACRITICS.replace(s, "")
        s = ARABIC_INDIC.replace(s) { (it.value[0].code - 0x0660 + '0'.code).toChar().toString() }
        s = EXTENDED_ARABIC_INDIC.replace(s) { (it.value[0].code - 0x06F0 + '0'.code).toChar().toString() }
        s = s.replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .replace('ئ', 'ي')
            .replace('ؤ', 'و')
            .replace('ة', 'ه')
            .replace("ء", "")
            .replace('\u200f', ' ')
            .replace('\u200e', ' ')
        return s
    }

    /** إزالة كل ما ليس حرفًا أو رقمًا (للمقارنة المضغوطة) */
    fun compact(input: String): String =
        normalize(input).filter { it.isLetterOrDigit() }

    /** تقسيم النص إلى كلمات (حروف/أرقام فقط) */
    fun tokens(input: String): List<String> =
        normalize(input)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }

    /** إزالة امتداد الملف */
    fun stripExtension(fileName: String): String {
        val name = fileName.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** مسافة ليفنشتاين بين نصين */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    /** نسبة التشابه بين نصين (0.0 - 1.0) */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val max = maxOf(a.length, b.length)
        if (max == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / max.toDouble()
    }

    /** هل تحتوي قائمة الرموز على التتابع المطلوب؟ */
    fun containsSequence(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (start in 0..(haystack.size - needle.size)) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }
}
