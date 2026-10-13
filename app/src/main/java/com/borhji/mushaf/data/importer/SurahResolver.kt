package com.borhji.mushaf.data.importer

import com.borhji.mushaf.data.SURAHS
import com.borhji.mushaf.data.SurahInfo

/** نوع المطابقة التي تم التعرف بها على السورة */
enum class MatchKind(val label: String) {
    ARABIC_EXACT("اسم مطابق"),
    ARABIC_TOKENS("اسم السورة"),
    ARABIC_CONTAINS("اسم داخل الملف"),
    ENGLISH_NAME("اسم لاتيني"),
    NUMBER("رقم السورة"),
}

/** نتيجة التعرف على السورة من اسم الملف */
data class SurahMatch(
    val surah: Int,
    val kind: MatchKind,
    val score: Double,
)

/**
 * مُطابِق أسماء ملفات التلاوات بالرقم الصحيح للسورة.
 *
 * يعتمد على:
 * 1. الاسم العربي للسورة (مع توحيد الهمزات وإزالة التشكيل).
 * 2. الأسماء البديلة الشائعة (مثل «تبارك» للملك، «قل هو الله أحد» للإخلاص).
 * 3. الاسم اللاتيني (Al-Baqarah, baqara, ...) للمجموعات المترجمة.
 * 4. أرقام السور في اسم الملف (1..114) مع تمييز الأرقام المزودة بأصفار.
 */
class SurahResolver(private val surahs: List<SurahInfo> = SURAHS) {

    private data class Candidate(val surah: Int, val kind: MatchKind, val score: Double, val weight: Int)

    private companion object {
        /**
         * كلمات شائعة في أسماء ملفات التلاوات ولا تدل على السورة نفسها.
         * تُكتب بصيغتها الموحّدة (بدون تشكيل، وبعد توحيد الهمزات والتاء المربوطة).
         */
        val FILLER_TOKENS = setOf(
            "سوره", "سور", "القران", "قران", "المصحف", "مصحف",
            "تلاوه", "تلاوات", "المرتل", "مرتل", "تسجيل", "نسخه", "كامل", "كامله",
            "قارئ", "الشيخ", "شيخ", "الاستاذ", "استاذ", "دكتور", "برهجي", "برهجى",
            "حفص", "عاصم", "الرواية", "روايه",
            "surah", "sura", "surat", "sourat", "quran", "quranic", "koran",
            "tilawa", "tilawat", "talawat", "tilawah", "recitation", "recitations",
            "audio", "mp3", "m4a", "wav", "ogg", "borhji", "borhgy", "burhji",
        )
    }

    fun resolve(fileName: String): SurahMatch? {
        val base = ArabicText.stripExtension(fileName)
        if (base.isBlank()) return null

        val rawTokens = ArabicText.tokens(base)
        if (rawTokens.isEmpty()) return null

        // إزالة الكلمات غير الدالة (كلمة «سورة»، اسم القارئ، كلمات مثل tilawa/quran)
        // لتبقى الكلمات المهمة فقط، مع العودة للنص الأصلي إذا فُرّغ تمامًا.
        val filtered = rawTokens.filterNot { it in FILLER_TOKENS }
        val tokens = filtered.ifEmpty { rawTokens }
        val compact = tokens.joinToString("")

        val candidates = ArrayList<Candidate>(8)

        // 1) مطابقة الأسماء العربية (والأسماء البديلة)
        for (info in surahs) {
            val names = buildList {
                add(info.name)
                addAll(info.aliases)
            }
            for (name in names) {
                val nameTokens = ArabicText.tokens(name)
                val nameCompact = ArabicText.compact(name)
                if (nameTokens.isEmpty() || nameCompact.isEmpty()) continue

                when {
                    // مطابقة تامة (اسم السورة فقط في اسم الملف)
                    compact == nameCompact -> candidates += Candidate(
                        info.number, MatchKind.ARABIC_EXACT, 1.0, nameCompact.length
                    )

                    // اسم السورة ككلمات متتابعة داخل اسم الملف
                    ArabicText.containsSequence(tokens, nameTokens) -> candidates += Candidate(
                        info.number, MatchKind.ARABIC_TOKENS, 0.94, nameCompact.length
                    )

                    // اسم السورة (4 أحرف فأكثر) مضمّن داخل النص بعد إزالة الفراغات
                    nameCompact.length >= 4 && compact.contains(nameCompact) -> candidates += Candidate(
                        info.number, MatchKind.ARABIC_CONTAINS, 0.82, nameCompact.length
                    )
                }
            }
        }

        // 2) مطابقة الاسم اللاتيني
        if (candidates.none { it.kind == MatchKind.ARABIC_EXACT }) {
            for (info in surahs) {
                val englishTokens = ArabicText.tokens(info.englishName)
                val englishCompact = ArabicText.compact(info.englishName)
                if (englishTokens.isEmpty()) continue

                // مطابقة تامة للنص المضغوط
                if (compact == englishCompact) {
                    candidates += Candidate(info.number, MatchKind.ENGLISH_NAME, 0.9, englishCompact.length)
                    continue
                }
                // كلمة لاتينية مطابقة أو قريبة جدًا داخل اسم الملف
                for (et in englishTokens) {
                    if (et.length < 3 || et.all { it.isDigit() }) continue
                    for (ft in tokens) {
                        if (ft == et) {
                            candidates += Candidate(info.number, MatchKind.ENGLISH_NAME, 0.86, et.length)
                        } else if (et.length >= 4 && ft.length >= 4) {
                            val sim = ArabicText.similarity(et, ft)
                            val dist = ArabicText.levenshtein(et, ft)
                            val nearMatch = (dist <= 1 && et.length >= 5) ||
                                (et.length >= 5 && ft.startsWith(et.take(4))) ||
                                (ft.length >= 5 && et.startsWith(ft.take(4)))
                            if (nearMatch) {
                                candidates += Candidate(
                                    info.number, MatchKind.ENGLISH_NAME, 0.72 + 0.1 * sim, et.length
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3) مطابقة الرقم
        val numericTokens = tokens.filter { t -> t.isNotEmpty() && t.all { it.isDigit() } }
        val inRange = numericTokens
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 1..surahs.size }
        val paddedToken = numericTokens.firstOrNull { it.length == 3 }
            ?.toIntOrNull()?.takeIf { it in 1..surahs.size }
        val numberGuess = paddedToken ?: inRange.lastOrNull()

        if (numberGuess != null) {
            val score = if (paddedToken != null) 0.85 else 0.7
            candidates += Candidate(numberGuess, MatchKind.NUMBER, score, 1)
        }

        if (candidates.isEmpty()) return null

        val best = candidates.sortedWith(
            compareByDescending<Candidate> { it.score }
                .thenByDescending { it.weight }
                .thenBy { it.surah }
        ).first()

        return SurahMatch(best.surah, best.kind, best.score)
    }
}
