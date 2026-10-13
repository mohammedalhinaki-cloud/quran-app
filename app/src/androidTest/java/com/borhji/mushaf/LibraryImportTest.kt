package com.borhji.mushaf

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * اختبار فعلي لعملية الاستيراد على الجهاز: يكتب ملفات WAV حقيقية بأسماء تشبه
 * ملفات التلاوات، ثم يتحقق من التعرف على السورة الصحيحة وترتيب المصحف.
 */
@RunWith(AndroidJUnit4::class)
class LibraryImportTest {

    private val library get() = TestSupport.app.container.library

    @Before
    fun setUp() {
        TestSupport.wipeLibrary()
    }

    @Test
    fun importsRealFilesAndMapsThemToCorrectSurahs() = runBlocking {
        val sourceDir = File(TestSupport.app.cacheDir, "import-test").apply { mkdirs() }
        val expectations = listOf(
            1 to "001 - الفاتحة.wav",
            2 to "002_Al-Baqarah.wav",
            3 to "سورة آل عمران.wav",
            18 to "الكهف.wav",
            36 to "يس.wav",
            67 to "تبارك.wav",
            112 to "قل هو الله أحد.wav",
            114 to "An-Nas 114.wav",
        )
        val uris = expectations.map { (_, fileName) ->
            Uri.fromFile(TestSupport.writeWav(File(sourceDir, fileName), 3))
        }

        val outcome = library.importNow(uris, copyIntoApp = true)

        assertEquals("عدد السور المستوردة", expectations.size, outcome.imported)
        assertTrue(
            "ملفات لم يتم التعرف عليها: ${outcome.unmatched.map { it.name }}",
            outcome.unmatched.isEmpty(),
        )
        assertTrue("أخطاء استيراد: ${outcome.failures}", outcome.failures.isEmpty())

        expectations.forEach { (surah, fileName) ->
            assertNotNull("لم يتم ربط الملف «$fileName» بالسورة $surah", library.trackFor(surah))
        }

        // الترتيب في قائمة التشغيل يجب أن يكون ترتيب المصحف
        val playable = library.playableTracks()
        assertEquals(expectations.size, playable.size)
        assertEquals(listOf(1, 2, 3, 18, 36, 67, 112, 114), playable.map { it.surah })

        // الملفات نُسخت إلى مساحة التطبيق ويمكن قراءتها
        playable.forEach { track ->
            assertEquals("file", track.uri.scheme)
            val copied = File(track.uri.path ?: "")
            assertTrue("الملف غير موجود: ${copied.absolutePath}", copied.exists() && copied.length() > 0L)
            assertTrue("مدة التلاوة غير مقروءة للسورة ${track.surah}", track.durationMs > 0L)
        }

        // السور الناقصة معلومة للتطبيق
        assertEquals(114 - expectations.size, outcome.missingSurahs.size)
    }

    @Test
    fun surahListIsCompleteInMushafOrder() = runBlocking {
        val list = library.surahList.first { it.isNotEmpty() }
        assertEquals(114, list.size)
        assertEquals((1..114).toList(), list.map { it.number })
        assertEquals("الفاتحة", list.first().name)
        assertEquals("الناس", list.last().name)
        assertEquals("البقرة", list[1].name)
    }

    @Test
    fun manualAssignmentFixesUnrecognizedFile() = runBlocking {
        val sourceDir = File(TestSupport.app.cacheDir, "assign-test").apply { mkdirs() }
        val weird = File(sourceDir, "تسجيل 2026 ملف غريب.wav")
        TestSupport.writeWav(weird, 2)

        // ملف لا يمكن التعرف على سورته تلقائيًا
        val outcome = library.importNow(listOf(Uri.fromFile(weird)), copyIntoApp = true)
        assertEquals(0, outcome.imported)
        assertEquals(1, outcome.unmatched.size)

        // التعيين اليدوي
        val result = library.assignFile(114, outcome.unmatched.first().uri, copyIntoApp = true)
        assertTrue("فشل التعيين اليدوي: ${result.exceptionOrNull()?.message}", result.isSuccess)

        assertNotNull(library.trackFor(114))

        // نتحقق من نفس التدفق الذي تعرضه الواجهة (يتحدّث لحظيًا بعد التعيين)
        val becameAvailable = TestSupport.waitUntil(15_000L) {
            library.surahList.value.firstOrNull { it.number == 114 }?.available == true
        }
        assertTrue("السورة 114 يجب أن تظهر كمتوفرة بعد التعيين", becameAvailable)
    }

    @Test
    fun progressIsSavedPerSurahAndIsIndependent() = runBlocking {
        TestSupport.seedLibrary(surahs = listOf(2, 18), seconds = 30)
        val container = TestSupport.app.container
        val baqarahPosition = 47L * 60_000L + 35_000L
        val kahfPosition = 12L * 60_000L + 10_000L
        val baqarahDuration = 2L * 3_600_000L
        val kahfDuration = 30L * 60_000L

        container.recorder.onSeek(2, baqarahPosition, baqarahDuration)
        container.recorder.onSeek(18, kahfPosition, kahfDuration)

        // الحفظ يجري في الخلفية
        assertTrue(
            "لم يُحفظ الموضع خلال المهلة",
            TestSupport.waitUntil(10_000L) {
                val a = runBlocking { container.library.progressFor(2) }
                val b = runBlocking { container.library.progressFor(18) }
                a?.positionMs == baqarahPosition && b?.positionMs == kahfPosition
            },
        )

        // كل سورة لها موضع مستقل
        assertEquals(baqarahPosition, container.library.progressFor(2)?.positionMs)
        assertEquals(kahfPosition, container.library.progressFor(18)?.positionMs)

        val list = container.library.surahList.first { it.isNotEmpty() }
        assertEquals(baqarahPosition, list.first { it.number == 2 }.positionMs)
        assertEquals(kahfPosition, list.first { it.number == 18 }.positionMs)

        // حالة الجلسة الأخيرة تُحفظ أيضًا (تُستخدم في بطاقة «متابعة الاستماع»)
        val prefs = container.settings.snapshot()
        assertEquals(18, prefs.lastSurah)
        assertEquals(kahfPosition, prefs.lastPositionMs)

        // العرض بالدقيقة والثانية كما يتوقعه المستخدم: 47:35
        assertEquals("47:35", com.borhji.mushaf.util.TimeFormat.clock(baqarahPosition))
    }
}
