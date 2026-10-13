package com.borhji.mushaf

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبارات تشغيل حقيقية على الجهاز/المحاكي:
 * - تشغيل فعلي لملف صوتي عبر Media3/ExoPlayer وقياس تقدّم الموضع.
 * - استمرار الصوت في الخلفية وفي شاشة أخرى.
 * - استمرار الصوت بعد إزالة واجهة التطبيق من التطبيقات الأخيرة.
 * - حفظ موضع الاستماع أثناء التشغيل.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackTest {

    private val container get() = TestSupport.app.container
    private val playback get() = container.playback
    private val library get() = container.library

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        TestSupport.grantNotificationPermission()
        TestSupport.wipeLibrary()
        TestSupport.seedLibrary(surahs = listOf(2), seconds = 60)
        TestSupport.requireTrack(2)
        playback.connect()
    }

    @After
    fun tearDown() {
        playback.stopAndClear()
    }

    @Test
    fun playsAudioAndAdvancesPosition() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)

        assertTrue(
            "لم يبدأ الصوت خلال المهلة",
            TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying },
        )
        assertTrue(
            "موضع التشغيل لم يتقدّم",
            TestSupport.waitUntil(15_000L) { playback.state.value.positionMs > 500L },
        )
        assertEquals(2, playback.state.value.surahNumber)
    }

    @Test
    fun seeksToRequestedPositionAndSavesProgress() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)
        assertTrue(TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying })

        playback.seekTo(10_000L)
        assertTrue(
            "لم يتم الانتقال إلى الموضع المطلوب",
            TestSupport.waitUntil(10_000L) { playback.state.value.positionMs >= 9_000L },
        )

        // الحفظ الفوري عند تحريك شريط التقدم
        assertTrue(
            "لم يُحفظ الموضع بعد التحريك",
            TestSupport.waitUntil(10_000L) { runBlocking { library.progressFor(2) } != null },
        )
        val saved = runBlocking { library.progressFor(2) }
        assertNotNull("لا يوجد موضع محفوظ للسورة", saved)
        assertTrue("الموضع المحفوظ أقل من المتوقع: ${saved?.positionMs}", (saved?.positionMs ?: 0L) >= 8_000L)
    }

    @Test
    fun keepsPlayingWhenAppGoesToBackgroundAndWhenTaskIsRemoved() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)
        assertTrue(TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying })

        // الخروج إلى الشاشة الرئيسية (تطبيق آخر أمام المستخدم)
        device.pressHome()
        Thread.sleep(5_000L)
        assertTrue("توقف الصوت بعد الخروج من التطبيق", playback.state.value.isPlaying)
        val positionInBackground = playback.state.value.positionMs
        assertTrue("الموضع توقف عن التقدم في الخلفية", positionInBackground > 0L)

        // إزالة واجهة التطبيق من التطبيقات الأخيرة
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity -> activity.finishAndRemoveTask() }
        Thread.sleep(6_000L)

        assertTrue(
            "توقف الصوت بعد إزالة التطبيق من التطبيقات الأخيرة",
            playback.state.value.isPlaying,
        )
        assertTrue(
            "الموضع لم يتقدّم بعد إزالة الواجهة",
            playback.state.value.positionMs > positionInBackground,
        )

        // الموضع محفوظ تلقائيًا أثناء التشغيل في الخلفية
        val saved = runBlocking { library.progressFor(2) }
        assertNotNull("لم يُحفظ الموضع أثناء تشغيل الخلفية", saved)
        assertTrue("الموضع المحفوظ غير منطقي: ${saved?.positionMs}", (saved?.positionMs ?: 0L) > 0L)
    }

    @Test
    fun showsPlaybackNotificationWithSurahName() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)
        assertTrue(TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying })
        Thread.sleep(3_000L)

        device.openNotification()
        val found = TestSupport.waitUntil(10_000L, 500L) {
            device.findObject(By.textContains("البقرة")) != null
        }
        device.pressBack()
        assertTrue("لم يظهر إشعار التشغيل باسم السورة", found)
    }

    @Test
    fun canBeStoppedAndClearedWhileKeepingSavedPosition() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)
        assertTrue(TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying })
        Thread.sleep(2_000L)

        playback.stopAndClear()
        assertTrue(TestSupport.waitUntil(10_000L) { !playback.state.value.isPlaying })
        assertTrue(playback.state.value.surahNumber == null)

        val saved = runBlocking { library.progressFor(2) }
        assertNotNull("ضاع الموضع المحفوظ بعد إيقاف المشغل", saved)
    }
}
