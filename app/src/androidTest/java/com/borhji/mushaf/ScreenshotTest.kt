package com.borhji.mushaf

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * لقطات شاشة حقيقية تُلتقط **داخل عملية التطبيق أثناء التشغيل الفعلي**
 * (حتى يكون إشعار التشغيل والمشغل الموسّع حيّين وقت الالتقاط).
 *
 * الملفات تُكتب في مجلد ملفات التطبيق الخارجي ثم يسحبها الـCI إلى `screenshots/`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container get() = TestSupport.app.container
    private val playback get() = container.playback

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val outDir: File
        get() = File(TestSupport.app.getExternalFilesDir(null), "shots").apply { mkdirs() }

    private fun capture(name: String) {
        val file = File(outDir, name)
        val ok = device.takeScreenshot(file)
        println("SCREENSHOT $name ok=$ok size=${if (file.exists()) file.length() else 0}")
    }

    @Test
    fun capturesHomeExpandedPlayerAndNotification() {
        TestSupport.grantNotificationPermission()
        TestSupport.wipeLibrary()
        TestSupport.seedLibrary(surahs = listOf(2), seconds = 60)
        TestSupport.requireTrack(2)
        playback.connect()

        playback.playSurah(2, startPositionMs = 30_000L, autoPlay = true)
        assertTrue(
            "لم يبدأ التشغيل فلا يمكن التقاط لقطات حقيقية",
            TestSupport.waitUntil(30_000L) { playback.state.value.isPlaying },
        )
        Thread.sleep(2_500L)

        // 1) الشاشة الرئيسية مع المشغل المصغّر أسفل الشاشة
        capture("01-home-playing.png")

        // 2) المشغل الموسّع (بالضغط على المشغل المصغّر كما يفعل المستخدم)
        composeRule.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        Thread.sleep(2_000L)
        capture("02-player-expanded.png")

        // إغلاق المشغل الموسّع
        device.pressBack()
        Thread.sleep(1_500L)

        // 3) لوحة الإشعارات وفيها إشعار التشغيل (والتشغيل ما زال جاريًا)
        try {
            device.openNotification()
            Thread.sleep(2_500L)
            capture("03-notification-shade.png")
            device.pressBack()
        } catch (t: Throwable) {
            println("SCREENSHOT_SHADE_FAILED=${t.javaClass.simpleName}")
        }

        // نُنهي التشغيل بعد الالتقاط
        playback.stopAndClear()
    }
}
