package com.borhji.mushaf

import com.borhji.mushaf.data.SURAH_BY_NUMBER
import com.borhji.mushaf.data.SURAHS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** التحقق من بيانات السور: العدد، الترتيب، الأسماء، وعدد الآيات */
class SurahDataTest {

    @Test
    fun `عدد السور 114 بالضبط`() {
        assertEquals(114, SURAHS.size)
    }

    @Test
    fun `الترتيب من 1 إلى 114 بدون فراغات أو تكرار`() {
        val numbers = SURAHS.map { it.number }
        assertEquals((1..114).toList(), numbers)
        assertEquals(114, numbers.toSet().size)
    }

    @Test
    fun `الفاتحة أولًا والناس أخيرًا`() {
        assertEquals("الفاتحة", SURAHS.first().name)
        assertEquals("الناس", SURAHS.last().name)
    }

    @Test
    fun `أسماء ومواضع سور معروفة`() {
        assertEquals("البقرة", SURAH_BY_NUMBER[2]?.name)
        assertEquals("آل عمران", SURAH_BY_NUMBER[3]?.name)
        assertEquals("الكهف", SURAH_BY_NUMBER[18]?.name)
        assertEquals("يس", SURAH_BY_NUMBER[36]?.name)
        assertEquals("الرحمن", SURAH_BY_NUMBER[55]?.name)
        assertEquals("الملك", SURAH_BY_NUMBER[67]?.name)
        assertEquals("الإخلاص", SURAH_BY_NUMBER[112]?.name)
    }

    @Test
    fun `عدد الآيات الكوفي لسور مرجعية`() {
        assertEquals(7, SURAH_BY_NUMBER[1]?.ayahCount)
        assertEquals(286, SURAH_BY_NUMBER[2]?.ayahCount)
        assertEquals(83, SURAH_BY_NUMBER[36]?.ayahCount)
        assertEquals(6, SURAH_BY_NUMBER[114]?.ayahCount)
    }

    @Test
    fun `كل سورة لها اسم لاتيني غير فارغ`() {
        SURAHS.forEach { surah ->
            assertNotNull(surah.englishName)
            assertTrue("اسم لاتيني مفقود للسورة ${surah.number}", surah.englishName.isNotBlank())
            assertTrue("عدد الآيات غير صحيح للسورة ${surah.number}", surah.ayahCount > 0)
        }
    }

    @Test
    fun `ترتيب مكية ومدنية مكتمل`() {
        SURAHS.forEach { surah ->
            assertTrue(
                "نوع السورة غير معروف للسورة ${surah.number}",
                surah.revelation == com.borhji.mushaf.data.Revelation.MEKKAN ||
                    surah.revelation == com.borhji.mushaf.data.Revelation.MADINAN,
            )
        }
    }
}
