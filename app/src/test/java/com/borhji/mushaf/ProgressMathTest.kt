package com.borhji.mushaf

import com.borhji.mushaf.util.ProgressMath
import com.borhji.mushaf.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق حفظ/استعادة موضع الاستماع.
 * المثال المرجعي: التوقف في سورة البقرة عند 47:35 ثم استعادة الموضع نفسه.
 */
class ProgressMathTest {

    private val surahAlBaqarahDurationMs = (2L * 60L + 1L) * 60L * 1000L // ساعتان وثانية تقريبًا

    @Test
    fun `استعادة الموضع بدقة المللي ثانية عند 47 دقيقة و35 ثانية`() {
        val position = 47L * 60L * 1000L + 35L * 1000L + 480L // 47:35.480
        val restored = ProgressMath.startPosition(position, surahAlBaqarahDurationMs)
        assertEquals(position, restored)
        assertEquals("47:35", TimeFormat.clock(restored))
    }

    @Test
    fun `الموضع المنخفض لا يُعتبر استئنافًا`() {
        assertFalse(ProgressMath.shouldResume(500L, surahAlBaqarahDurationMs))
        assertEquals(0L, ProgressMath.startPosition(500L, surahAlBaqarahDurationMs))
    }

    @Test
    fun `الموضع قرب نهاية السورة يُصفَّر للبداية`() {
        val nearEnd = surahAlBaqarahDurationMs - 3_000L
        assertEquals(0L, ProgressMath.normalizePosition(nearEnd, surahAlBaqarahDurationMs))
        assertEquals(0L, ProgressMath.startPosition(nearEnd, surahAlBaqarahDurationMs))
    }

    @Test
    fun `الموضع السالب يُصحّح إلى صفر`() {
        assertEquals(0L, ProgressMath.normalizePosition(-5_000L, surahAlBaqarahDurationMs))
    }

    @Test
    fun `الموضع أكبر من المدة يُعتبر منتهيًا`() {
        assertEquals(0L, ProgressMath.normalizePosition(surahAlBaqarahDurationMs + 1_000L, surahAlBaqarahDurationMs))
    }

    @Test
    fun `موضع محفوظ بدون معرفة المدة يبقى كما هو`() {
        val position = 1_234_567L
        assertEquals(position, ProgressMath.normalizePosition(position, 0L))
        assertEquals(position, ProgressMath.startPosition(position, 0L))
    }

    @Test
    fun `الانتقال للسورة التالية والسابقة`() {
        assertEquals(2, ProgressMath.nextSurah(1))
        assertEquals(114, ProgressMath.nextSurah(113))
        assertNull(ProgressMath.nextSurah(114))
        assertEquals(1, ProgressMath.previousSurah(2))
        assertNull(ProgressMath.previousSurah(1))
    }

    @Test
    fun `نسبة التقدم محسوبة بدقة`() {
        assertEquals(0.5f, ProgressMath.progressFraction(500L, 1000L), 0.0001f)
        assertEquals(0f, ProgressMath.progressFraction(500L, 0L), 0.0001f)
        assertEquals(1f, ProgressMath.progressFraction(2_000L, 1000L), 0.0001f)
    }

    @Test
    fun `تنسيق الأوقات`() {
        assertEquals("00:00", TimeFormat.clock(0L))
        assertEquals("00:09", TimeFormat.clock(9_000L))
        assertEquals("47:35", TimeFormat.clock(47L * 60_000L + 35_000L))
        assertEquals("1:02:03", TimeFormat.clock(3_723_000L))
        assertEquals("47:35 / 2:01:00", TimeFormat.describe(47L * 60_000L + 35_000L, 2L * 3_600_000L + 60_000L))
    }

    @Test
    fun `تنسيق سرعة التشغيل`() {
        assertEquals("1.0x", TimeFormat.formatSpeed(1.0f))
        assertEquals("1.5x", TimeFormat.formatSpeed(1.5f))
        assertEquals("0.75x", TimeFormat.formatSpeed(0.75f))
    }

    @Test
    fun `حفظ موضع مستقل لكل سورة`() {
        // محاكاة: البقرة عند 47:35، الكهف عند 12:10 — لا يتأثر أحدهما بالآخر
        val progress = mutableMapOf(2 to 47L * 60_000L + 35_000L, 18 to 12L * 60_000L + 10_000L)
        progress[36] = 3L * 60_000L
        assertEquals(47L * 60_000L + 35_000L, progress[2])
        assertEquals(12L * 60_000L + 10_000L, progress[18])
        assertTrue(ProgressMath.shouldResume(progress[36] ?: 0L, 0L))
    }
}
