package com.borhji.mushaf

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
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
    fun showsPlaybackNotificationWithSurahNameAndControls() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)
        assertTrue(
            "لم يبدأ التشغيل فلا يمكن التحقق من الإشعار",
            TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying },
        )

        // نضع التطبيق في الخلفية — وهو السيناريو المطلوب (استخدام تطبيقات أخرى)
        device.pressHome()
        Thread.sleep(2_500L)

        // (1) تحقق فعلي من وجود إشعار التشغيل ومحتواه وأزرار التحكم عبر خدمة الإشعارات
        val manager =
            TestSupport.app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var title = ""
        var actionCount = 0
        val found = TestSupport.waitUntil(25_000L, 500L) {
            val active = manager.activeNotifications
                .firstOrNull { it.packageName == TestSupport.app.packageName }
            if (active != null) {
                title = active.notification.extras
                    ?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                actionCount = active.notification.actions?.size ?: 0
                title.contains("البقرة")
            } else {
                false
            }
        }
        println("NOTIFICATION_TITLE=$title ACTIONS=$actionCount")
        assertTrue("لم يظهر إشعار تشغيل باسم السورة (العنوان الظاهر: «$title»)", found)
        assertTrue(
            "الإشعار لا يحتوي أزرار تحكم كافية (عدد الأزرار: $actionCount)",
            actionCount >= 2,
        )

        // (2) محاولة تحقق بصري من ظهور الإشعار على الشاشة.
        //     النتيجة تُسجَّل في السجل؛ قد لا تُصيّر صورة المحاكي المستخدمة في CI لوحة الإشعارات.
        try {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            device.openNotification()
            val visible = TestSupport.waitUntil(8_000L, 400L) {
                device.findObject(By.textContains("البقرة")) != null
            }
            println("NOTIFICATION_VISIBLE_ON_SCREEN=$visible")
            device.pressBack()
        } catch (t: Throwable) {
            println("NOTIFICATION_VISIBLE_ON_SCREEN=unknown (${t.javaClass.simpleName})")
        }
    }

    @Test
    fun canBeStoppedAndClearedWhileKeepingSavedPosition() {
        playback.playSurah(2, startPositionMs = 0L, autoPlay = true)
        assertTrue(TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying })
        Thread.sleep(2_000L)

        playback.stopAndClear()
        assertTrue(
            "ظل المشغل في حالة تشغيل بعد إيقافه",
            TestSupport.waitUntil(15_000L) { !playback.state.value.isPlaying },
        )
        assertTrue(
            "لم يفرغ المشغل بعد الإيقاف (السورة الحالية: ${playback.state.value.surahNumber})",
            TestSupport.waitUntil(10_000L) { playback.state.value.surahNumber == null },
        )

        val saved = runBlocking { library.progressFor(2) }
        assertNotNull("ضاع الموضع المحفوظ بعد إيقاف المشغل", saved)
    }
}
