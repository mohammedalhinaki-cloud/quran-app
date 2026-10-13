package com.borhji.mushaf.data

/** نوع السورة: مكية أو مدنية */
enum class Revelation(val label: String) {
    MEKKAN("مكية"),
    MADINAN("مدنية"),
}

/**
 * بيانات سورة من سور المصحف الشريف.
 *
 * @param number رقم السورة في ترتيب المصحف (1..114)
 * @param name الاسم العربي الشائع
 * @param englishName الاسم اللاتيني (يُستخدم لمطابقة أسماء الملفات المترجمة)
 * @param revelation مكية/مدنية
 * @param ayahCount عدد الآيات بحسب العدّ الكوفي
 * @param aliases أسماء بديلة شائعة تُستخدم عند مطابقة أسماء الملفات
 */
data class SurahInfo(
    val number: Int,
    val name: String,
    val englishName: String,
    val revelation: Revelation,
    val ayahCount: Int,
    val aliases: List<String> = emptyList(),
)

private fun surah(
    number: Int,
    name: String,
    englishName: String,
    revelation: Revelation,
    ayahCount: Int,
    vararg aliases: String,
) = SurahInfo(number, name, englishName, revelation, ayahCount, aliases.toList())

/**
 * السور الـ114 بترتيب المصحف الصحيح (من الفاتحة إلى الناس) — ثابت ولا يعتمد على أي ترتيب أبجدي.
 */
val SURAHS: List<SurahInfo> = listOf(
    surah(1, "الفاتحة", "Al-Fatihah", Revelation.MEKKAN, 7, "الحمد", "فاتحة الكتاب", "أم الكتاب"),
    surah(2, "البقرة", "Al-Baqarah", Revelation.MADINAN, 286),
    surah(3, "آل عمران", "Aal-Imran", Revelation.MADINAN, 200, "العمران", "ال عمران"),
    surah(4, "النساء", "An-Nisa", Revelation.MADINAN, 176),
    surah(5, "المائدة", "Al-Maidah", Revelation.MADINAN, 120),
    surah(6, "الأنعام", "Al-Anam", Revelation.MEKKAN, 165),
    surah(7, "الأعراف", "Al-Araf", Revelation.MEKKAN, 206),
    surah(8, "الأنفال", "Al-Anfal", Revelation.MADINAN, 75),
    surah(9, "التوبة", "At-Tawbah", Revelation.MADINAN, 129, "براءة"),
    surah(10, "يونس", "Yunus", Revelation.MEKKAN, 109),
    surah(11, "هود", "Hud", Revelation.MEKKAN, 123),
    surah(12, "يوسف", "Yusuf", Revelation.MEKKAN, 111),
    surah(13, "الرعد", "Ar-Rad", Revelation.MADINAN, 43),
    surah(14, "إبراهيم", "Ibrahim", Revelation.MEKKAN, 52),
    surah(15, "الحجر", "Al-Hijr", Revelation.MEKKAN, 99),
    surah(16, "النحل", "An-Nahl", Revelation.MEKKAN, 128),
    surah(17, "الإسراء", "Al-Isra", Revelation.MEKKAN, 111, "بني إسرائيل", "بني اسرائيل"),
    surah(18, "الكهف", "Al-Kahf", Revelation.MEKKAN, 110),
    surah(19, "مريم", "Maryam", Revelation.MEKKAN, 98),
    surah(20, "طه", "Ta-Ha", Revelation.MEKKAN, 135, "طاها"),
    surah(21, "الأنبياء", "Al-Anbiya", Revelation.MEKKAN, 112),
    surah(22, "الحج", "Al-Hajj", Revelation.MADINAN, 78),
    surah(23, "المؤمنون", "Al-Muminun", Revelation.MEKKAN, 118),
    surah(24, "النور", "An-Nur", Revelation.MADINAN, 64),
    surah(25, "الفرقان", "Al-Furqan", Revelation.MEKKAN, 77),
    surah(26, "الشعراء", "Ash-Shuara", Revelation.MEKKAN, 227),
    surah(27, "النمل", "An-Naml", Revelation.MEKKAN, 93),
    surah(28, "القصص", "Al-Qasas", Revelation.MEKKAN, 88),
    surah(29, "العنكبوت", "Al-Ankabut", Revelation.MEKKAN, 69),
    surah(30, "الروم", "Ar-Rum", Revelation.MEKKAN, 60),
    surah(31, "لقمان", "Luqman", Revelation.MEKKAN, 34),
    surah(32, "السجدة", "As-Sajdah", Revelation.MEKKAN, 30, "الم تنزيل"),
    surah(33, "الأحزاب", "Al-Ahzab", Revelation.MADINAN, 73),
    surah(34, "سبأ", "Saba", Revelation.MEKKAN, 54, "سبا"),
    surah(35, "فاطر", "Fatir", Revelation.MEKKAN, 45, "الملائكة"),
    surah(36, "يس", "Ya-Sin", Revelation.MEKKAN, 83, "ياسين"),
    surah(37, "الصافات", "As-Saffat", Revelation.MEKKAN, 182),
    surah(38, "ص", "Sad", Revelation.MEKKAN, 88),
    surah(39, "الزمر", "Az-Zumar", Revelation.MEKKAN, 75),
    surah(40, "غافر", "Ghafir", Revelation.MEKKAN, 85, "المؤمن", "الطول"),
    surah(41, "فصلت", "Fussilat", Revelation.MEKKAN, 54, "حم السجدة"),
    surah(42, "الشورى", "Ash-Shura", Revelation.MEKKAN, 53),
    surah(43, "الزخرف", "Az-Zukhruf", Revelation.MEKKAN, 89),
    surah(44, "الدخان", "Ad-Dukhan", Revelation.MEKKAN, 59),
    surah(45, "الجاثية", "Al-Jathiyah", Revelation.MEKKAN, 37),
    surah(46, "الأحقاف", "Al-Ahqaf", Revelation.MEKKAN, 35),
    surah(47, "محمد", "Muhammad", Revelation.MADINAN, 38, "القتال"),
    surah(48, "الفتح", "Al-Fath", Revelation.MADINAN, 29),
    surah(49, "الحجرات", "Al-Hujurat", Revelation.MADINAN, 18),
    surah(50, "ق", "Qaf", Revelation.MEKKAN, 45),
    surah(51, "الذاريات", "Adh-Dhariyat", Revelation.MEKKAN, 60),
    surah(52, "الطور", "At-Tur", Revelation.MEKKAN, 49),
    surah(53, "النجم", "An-Najm", Revelation.MEKKAN, 62),
    surah(54, "القمر", "Al-Qamar", Revelation.MEKKAN, 55),
    surah(55, "الرحمن", "Ar-Rahman", Revelation.MADINAN, 78),
    surah(56, "الواقعة", "Al-Waqiah", Revelation.MEKKAN, 96),
    surah(57, "الحديد", "Al-Hadid", Revelation.MADINAN, 29),
    surah(58, "المجادلة", "Al-Mujadila", Revelation.MADINAN, 22),
    surah(59, "الحشر", "Al-Hashr", Revelation.MADINAN, 24),
    surah(60, "الممتحنة", "Al-Mumtahanah", Revelation.MADINAN, 13),
    surah(61, "الصف", "As-Saff", Revelation.MADINAN, 14),
    surah(62, "الجمعة", "Al-Jumuah", Revelation.MADINAN, 11),
    surah(63, "المنافقون", "Al-Munafiqun", Revelation.MADINAN, 11),
    surah(64, "التغابن", "At-Taghabun", Revelation.MADINAN, 18),
    surah(65, "الطلاق", "At-Talaq", Revelation.MADINAN, 12),
    surah(66, "التحريم", "At-Tahrim", Revelation.MADINAN, 12),
    surah(67, "الملك", "Al-Mulk", Revelation.MEKKAN, 30, "تبارك", "المانعة", "المنجية"),
    surah(68, "القلم", "Al-Qalam", Revelation.MEKKAN, 52, "ن"),
    surah(69, "الحاقة", "Al-Haqqah", Revelation.MEKKAN, 52),
    surah(70, "المعارج", "Al-Maarij", Revelation.MEKKAN, 44),
    surah(71, "نوح", "Nuh", Revelation.MEKKAN, 28),
    surah(72, "الجن", "Al-Jinn", Revelation.MEKKAN, 28),
    surah(73, "المزمل", "Al-Muzzammil", Revelation.MEKKAN, 20),
    surah(74, "المدثر", "Al-Muddaththir", Revelation.MEKKAN, 56),
    surah(75, "القيامة", "Al-Qiyamah", Revelation.MEKKAN, 40),
    surah(76, "الإنسان", "Al-Insan", Revelation.MADINAN, 31, "الدهر", "هل أتى"),
    surah(77, "المرسلات", "Al-Mursalat", Revelation.MEKKAN, 50),
    surah(78, "النبأ", "An-Naba", Revelation.MEKKAN, 40, "عم", "التساؤل"),
    surah(79, "النازعات", "An-Naziat", Revelation.MEKKAN, 46),
    surah(80, "عبس", "Abasa", Revelation.MEKKAN, 42),
    surah(81, "التكوير", "At-Takwir", Revelation.MEKKAN, 29),
    surah(82, "الانفطار", "Al-Infitar", Revelation.MEKKAN, 19),
    surah(83, "المطففين", "Al-Mutaffifin", Revelation.MEKKAN, 36),
    surah(84, "الانشقاق", "Al-Inshiqaq", Revelation.MEKKAN, 25),
    surah(85, "البروج", "Al-Buruj", Revelation.MEKKAN, 22),
    surah(86, "الطارق", "At-Tariq", Revelation.MEKKAN, 17),
    surah(87, "الأعلى", "Al-Ala", Revelation.MEKKAN, 19, "سبح"),
    surah(88, "الغاشية", "Al-Ghashiyah", Revelation.MEKKAN, 26),
    surah(89, "الفجر", "Al-Fajr", Revelation.MEKKAN, 30),
    surah(90, "البلد", "Al-Balad", Revelation.MEKKAN, 20),
    surah(91, "الشمس", "Ash-Shams", Revelation.MEKKAN, 15),
    surah(92, "الليل", "Al-Layl", Revelation.MEKKAN, 21),
    surah(93, "الضحى", "Ad-Duha", Revelation.MEKKAN, 11),
    surah(94, "الشرح", "Ash-Sharh", Revelation.MEKKAN, 8, "الانشراح", "ألم نشرح"),
    surah(95, "التين", "At-Tin", Revelation.MEKKAN, 8),
    surah(96, "العلق", "Al-Alaq", Revelation.MEKKAN, 19, "اقرأ"),
    surah(97, "القدر", "Al-Qadr", Revelation.MEKKAN, 5),
    surah(98, "البينة", "Al-Bayyinah", Revelation.MADINAN, 8, "لم يكن"),
    surah(99, "الزلزلة", "Az-Zalzalah", Revelation.MADINAN, 8, "الزلزال"),
    surah(100, "العاديات", "Al-Adiyat", Revelation.MEKKAN, 11),
    surah(101, "القارعة", "Al-Qariah", Revelation.MEKKAN, 11),
    surah(102, "التكاثر", "At-Takathur", Revelation.MEKKAN, 8),
    surah(103, "العصر", "Al-Asr", Revelation.MEKKAN, 3),
    surah(104, "الهمزة", "Al-Humazah", Revelation.MEKKAN, 9),
    surah(105, "الفيل", "Al-Fil", Revelation.MEKKAN, 5),
    surah(106, "قريش", "Quraysh", Revelation.MEKKAN, 4),
    surah(107, "الماعون", "Al-Maun", Revelation.MEKKAN, 7),
    surah(108, "الكوثر", "Al-Kawthar", Revelation.MEKKAN, 3),
    surah(109, "الكافرون", "Al-Kafirun", Revelation.MEKKAN, 6),
    surah(110, "النصر", "An-Nasr", Revelation.MADINAN, 3),
    surah(111, "المسد", "Al-Masad", Revelation.MEKKAN, 5, "اللهب", "تبت", "أبي لهب"),
    surah(112, "الإخلاص", "Al-Ikhlas", Revelation.MEKKAN, 4, "التوحيد", "قل هو الله أحد"),
    surah(113, "الفلق", "Al-Falaq", Revelation.MEKKAN, 5),
    surah(114, "الناس", "An-Nas", Revelation.MEKKAN, 6, "قل أعوذ برب الناس"),
)

/** خريطة سريعة: رقم السورة ← بياناتها */
val SURAH_BY_NUMBER: Map<Int, SurahInfo> = SURAHS.associateBy { it.number }

/** عدد سور المصحف */
const val SURAH_COUNT = 114
