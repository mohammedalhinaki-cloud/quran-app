package com.borhji.mushaf

import com.borhji.mushaf.data.importer.ArabicText
import com.borhji.mushaf.data.importer.MatchKind
import com.borhji.mushaf.data.importer.SurahResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبارات التعرف التلقائي على السور من أسماء الملفات */
class SurahResolverTest {

    private val resolver = SurahResolver()

    private fun resolve(name: String) = resolver.resolve(name)

    // ------------------------------------------------------------------ توحيد النص

    @Test
    fun `توحيد النص العربي يزيل التشكيل ويوحّد الهمزات`() {
        assertEquals("الفاتحه", ArabicText.compact("الفَاتِحَة"))
        assertEquals("ابراهيم", ArabicText.compact("إِبْرَاهِيم"))
        assertEquals("ال عمران", ArabicText.normalize("آل عمران"))
        assertEquals("الاسرا", ArabicText.compact("الإسراء"))
    }

    @Test
    fun `تحويل الأرقام العربية إلى إنجليزية`() {
        assertEquals("12", ArabicText.compact("١٢"))
        assertEquals("114", ArabicText.compact("۱۱۴"))
    }

    // ------------------------------------------------------------------ التعرف بالرقم

    @Test
    fun `التعرف برقم السورة فقط`() {
        assertEquals(1, resolve("001.mp3")?.surah)
        assertEquals(2, resolve("002.mp3")?.surah)
        assertEquals(114, resolve("114.mp3")?.surah)
        assertEquals(36, resolve("036.m4a")?.surah)
    }

    @Test
    fun `التعرف برقم السورة غير المزود بأصفار`() {
        assertEquals(18, resolve("18.mp3")?.surah)
        assertEquals(55, resolve("55.m4a")?.surah)
    }

    @Test
    fun `الرقم خارج النطاق لا يُعتمد`() {
        // 2024 و 999 خارج نطاق السور، فيبقى الرقم 7 هو المطابق
        assertEquals(7, resolve("2024-07-tilawa.mp3")?.surah)
    }

    // ------------------------------------------------------------ التعرف بالاسم العربي

    @Test
    fun `التعرف على الاسم العربي الصريح`() {
        assertEquals(1, resolve("سورة الفاتحة.mp3")?.surah)
        assertEquals(18, resolve("الكهف.mp3")?.surah)
        assertEquals(36, resolve("سورة يس.mp3")?.surah)
        assertEquals(50, resolve("ق.mp3")?.surah)
        assertEquals(38, resolve("ص.mp3")?.surah)
    }

    @Test
    fun `التعرف مع اسم القارئ والأرقام في اسم الملف`() {
        val name = "محمد برهجي - 002 - البقرة.mp3"
        val match = resolve(name)
        assertEquals(2, match?.surah)
    }

    @Test
    fun `التعرف على الأسماء البديلة الشائعة`() {
        assertEquals(67, resolve("تبارك.mp3")?.surah)
        assertEquals(111, resolve("تبت يدا أبي لهب.mp3")?.surah)
        assertEquals(112, resolve("قل هو الله أحد.mp3")?.surah)
        assertEquals(17, resolve("بني إسرائيل.mp3")?.surah)
        assertEquals(99, resolve("الزلزال.mp3")?.surah)
        assertEquals(94, resolve("ألم نشرح.mp3")?.surah)
        assertEquals(78, resolve("عم يتساءلون.mp3")?.surah)
    }

    @Test
    fun `لا تختلط الحج بالحجرات`() {
        assertEquals(22, resolve("الحج.mp3")?.surah)
        assertEquals(49, resolve("الحجرات.mp3")?.surah)
    }

    @Test
    fun `التاء المربوطة والهمزات لا تمنع التعرف`() {
        assertEquals(5, resolve("المائده.mp3")?.surah)
        assertEquals(6, resolve("الانعام.mp3")?.surah)
        assertEquals(9, resolve("التوبه.mp3")?.surah)
    }

    @Test
    fun `الأسماء المتشابهة لا تختلط`() {
        assertEquals(15, resolve("الحجر.mp3")?.surah)
        assertEquals(16, resolve("النحل.mp3")?.surah)
        assertEquals(27, resolve("النمل.mp3")?.surah)
        assertEquals(24, resolve("النور.mp3")?.surah)
        assertEquals(109, resolve("الكافرون.mp3")?.surah)
        assertEquals(108, resolve("الكوثر.mp3")?.surah)
    }

    // ------------------------------------------------------------ التعرف بالاسم اللاتيني

    @Test
    fun `التعرف على الاسم اللاتيني`() {
        assertEquals(2, resolve("002_Al-Baqarah.mp3")?.surah)
        assertEquals(18, resolve("Al-Kahf.mp3")?.surah)
        assertEquals(36, resolve("Ya-Sin.mp3")?.surah)
        assertEquals(112, resolve("Al-Ikhlas.m4a")?.surah)
    }

    @Test
    fun `التعرف على الاسم اللاتيني بترتيب مختلف`() {
        assertEquals(114, resolve("An-Nas 114.mp3")?.surah)
        assertEquals(1, resolve("001-fateha.mp3")?.surah)
    }

    @Test
    fun `الملفات غير المعروفة لا تُسند لسورة`() {
        assertNull(resolve("تلاوة نادرة للشيخ.mp3"))
        assertNull(resolve("123.mp3"))
    }

    @Test
    fun `درجة الثقة مرتفعة عند تطابق الاسم`() {
        val match = resolve("سورة الفاتحة.mp3")
        assertTrue(match != null && match.score >= 0.9)
        assertEquals(MatchKind.ARABIC_EXACT, match?.kind)
    }
}
