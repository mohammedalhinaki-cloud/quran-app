package com.borhji.mushaf

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.borhji.mushaf.data.ImportOutcome
import com.borhji.mushaf.data.SURAH_BY_NUMBER
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * أدوات مشتركة لاختبارات الجهاز/المحاكي.
 * تُنشئ ملفات صوتية حقيقية (WAV صالح) على الجهاز لاختبار الاستيراد والتشغيل فعليًا.
 */
object TestSupport {

    val app: MushafApp get() = ApplicationProvider.getApplicationContext()

    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Throwable) {
                // تجاهل: بعض صور النظام لا تسمح بالمنح البرمجي
            }
        }
    }

    /** كتابة ملف WAV صالح (نغمة بسيطة) لاستخدامه كتلاوة اختبارية */
    fun writeWav(file: File, seconds: Int, sampleRate: Int = 16_000): File {
        file.parentFile?.mkdirs()
        val totalSamples = sampleRate * seconds
        val dataSize = totalSamples * 2
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
            fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
            fun le32(v: Int) {
                out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
                out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
            }
            fun le16(v: Int) {
                out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            }
            ascii("RIFF"); le32(36 + dataSize); ascii("WAVE")
            ascii("fmt "); le32(16); le16(1); le16(1)
            le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
            ascii("data"); le32(dataSize)
            for (i in 0 until totalSamples) {
                val sample = (sin(2.0 * PI * 440.0 * i / sampleRate) * 6000.0).toInt()
                le16(sample and 0xFFFF)
            }
        }
        return file
    }

    fun waitUntil(timeoutMs: Long = 20_000L, stepMs: Long = 250L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(stepMs)
        }
        return condition()
    }

    /** تفريغ المكتبة بالكامل قبل اختبار جديد (بداية نظيفة) */
    fun wipeLibrary() = runBlocking {
        val container = app.container
        container.library.playableTracks().forEach { track ->
            container.library.removeTrack(track.surah, deleteProgress = true)
        }
        container.library.clearAllProgress()
    }

    /** استيراد ملفات اختبار بأسماء سور حقيقية */
    fun seedLibrary(
        surahs: List<Int> = listOf(1, 2, 18),
        seconds: Int = 30,
    ): ImportOutcome = runBlocking {
        val sourceDir = File(app.cacheDir, "test-source").apply { mkdirs() }
        val uris = surahs.map { number ->
            val name = SURAH_BY_NUMBER[number]?.name ?: "$number"
            val fileName = String.format(Locale.US, "%03d - %s.wav", number, name)
            Uri.fromFile(writeWav(File(sourceDir, fileName), seconds))
        }
        app.container.library.importNow(uris, copyIntoApp = true)
    }

    /** مسار مهمل: يضمن أن الاختبار لا يمر على مكتبة فارغة */
    fun requireTrack(surah: Int) {
        val track = runBlocking { app.container.library.trackFor(surah) }
        checkNotNull(track) { "السورة $surah غير مستوردة — تعذّر تجهيز بيئة الاختبار" }
    }
}
