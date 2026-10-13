package com.borhji.mushaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * يجهّز حالة استماع حقيقية **قبل** إعادة تشغيل الجهاز:
 * استيراد ملفات فعلية → تشغيل سورة البقرة → الانتقال إلى موضع محدد → إيقاف مؤقت (حفظ فوري).
 *
 * هذه المرحلة تُنفَّذ في نهاية المرحلة الأولى، ثم يُعاد تشغيل الجهاز، ثم تتحقق
 * `RebootPersistenceTest` من بقاء الملفات والموضع وظهور بطاقة «متابعة الاستماع».
 */
@RunWith(AndroidJUnit4::class)
class RebootPrepareTest {

    private val container get() = TestSupport.app.container
    private val library get() = container.library
    private val playback get() = container.playback

    private val preparedPositionMs = 12_000L

    @Before
    fun setUp() {
        TestSupport.wipeLibrary()
        TestSupport.seedLibrary(surahs = listOf(2), seconds = 60)
        TestSupport.requireTrack(2)
        playback.connect()
    }

    @Test
    fun preparesResumeStateBeforeReboot() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)
        assertTrue(
            "لم يبدأ التشغيل فلا يمكن تجهيز موضع محفوظ",
            TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying },
        )
        assertTrue(
            "موضع التشغيل لم يتقدّم فعليًا",
            TestSupport.waitUntil(20_000L) { playback.state.value.positionMs > 2_000L },
        )

        // الانتقال إلى موضع محدد (كما لو أن المستخدم سحب شريط التقدم)
        playback.seekTo(preparedPositionMs)
        assertTrue(
            "لم يُطبَّق الانتقال إلى الموضع المحدد",
            TestSupport.waitUntil(10_000L) { playback.state.value.positionMs >= preparedPositionMs - 1_000L },
        )

        // إيقاف مؤقت → حفظ فوري للموضع في قاعدة البيانات وفي إعدادات الجلسة
        playback.pause()
        assertTrue(
            "لم يتوقف التشغيل بعد الإيقاف المؤقت",
            TestSupport.waitUntil(10_000L) { !playback.state.value.isPlaying },
        )
        Thread.sleep(1_500L)

        val row = runBlocking { library.progressFor(2) }
        assertNotNull("لم يُحفظ موضع السورة في قاعدة البيانات", row)
        assertTrue(
            "الموضع المحفوظ في قاعدة البيانات غير منطقي: ${row?.positionMs}",
            (row?.positionMs ?: 0L) > 0L,
        )
        val prefs = runBlocking { container.settings.snapshot() }
        assertEquals("آخر سورة محفوظة غير صحيحة", 2, prefs.lastSurah)
        assertTrue("موضع الجلسة المحفوظ صفر", prefs.lastPositionMs > 0L)

        println("PREPARED_DB_POSITION_MS=${row?.positionMs}")
        println("PREPARED_SESSION_POSITION_MS=${prefs.lastPositionMs}")
        println("PREPARED_DURATION_MS=${row?.durationMs}")

        // ملاحظة: لا نُنظّف المشغل هنا — نترك الحالة كما هي ليتم التحقق منها بعد إعادة التشغيل.
    }
}
