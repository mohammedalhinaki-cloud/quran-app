package com.borhji.mushaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.borhji.mushaf.data.SURAH_BY_NUMBER
import com.borhji.mushaf.util.TimeFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * اختبارات ما بعد إعادة تشغيل الجهاز (تُشغَّل في مرحلة ثانية بعد `adb reboot`).
 * تتحقق من بقاء ملفات التلاوات والموضع المحفوظ وظهر بطاقة «متابعة الاستماع».
 */
@RunWith(AndroidJUnit4::class)
class RebootPersistenceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container get() = TestSupport.app.container

    @Test
    fun importedAudioFilesSurviveReboot() = runBlocking {
        val tracks = container.library.playableTracks()
        assertTrue("فُقدت المكتبة بعد إعادة تشغيل الجهاز", tracks.isNotEmpty())
        assertTrue("السورة 2 (اختبار التشغيل) مفقودة بعد إعادة التشغيل", tracks.any { it.surah == 2 })
        tracks.forEach { track ->
            val file = File(track.uri.path ?: "")
            assertTrue(
                "الملف ${file.absolutePath} غير موجود أو فارغ بعد إعادة التشغيل",
                file.exists() && file.length() > 0L,
            )
        }
    }

    @Test
    fun savedListeningPositionSurvivesReboot() = runBlocking {
        val saved = container.library.progressFor(2)
        assertNotNull("فُقد موضع الاستماع بعد إعادة تشغيل الجهاز", saved)
        assertTrue("الموضع المحفوظ صفر بعد إعادة التشغيل", (saved?.positionMs ?: 0L) > 0L)

        val prefs = container.settings.snapshot()
        assertEquals("آخر سورة محفوظة غير صحيحة", 2, prefs.lastSurah)
        assertTrue("موضع الجلسة الأخيرة صفر", prefs.lastPositionMs > 0L)
    }

    @Test
    fun resumeCardIsShownWithSavedPositionAfterReboot() {
        val prefs = runBlocking { container.settings.snapshot() }
        val surahName = SURAH_BY_NUMBER[prefs.lastSurah ?: 2]?.name ?: "البقرة"
        val expectedPosition = TimeFormat.clock(prefs.lastPositionMs)

        composeRule.onAllNodesWithText("متابعة الاستماع").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("سورة $surahName").assertIsDisplayed()
        composeRule.onAllNodesWithText("الموضع المحفوظ: $expectedPosition", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }
}
