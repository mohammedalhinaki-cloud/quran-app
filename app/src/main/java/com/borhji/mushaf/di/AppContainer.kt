package com.borhji.mushaf.di

import android.content.Context
import com.borhji.mushaf.data.AudioImporter
import com.borhji.mushaf.data.LibraryRepository
import com.borhji.mushaf.data.MushafDatabase
import com.borhji.mushaf.data.ProgressRecorder
import com.borhji.mushaf.data.SettingsRepository
import com.borhji.mushaf.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * حاوية الاعتماديات (حقن يدوي بدون مكتبات خارجية).
 * كل الكائنات تعيش مع عمر التطبيق، ولا يوجد أي اتصال بالشبكة.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** نطاق عام للعمليات الطويلة (استيراد، حفظ تقدم) */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: MushafDatabase by lazy { MushafDatabase.build(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val importer: AudioImporter by lazy { AudioImporter(appContext) }

    val recorder: ProgressRecorder by lazy {
        ProgressRecorder(database.progressDao(), settings, appScope)
    }

    val library: LibraryRepository by lazy {
        LibraryRepository(
            context = appContext,
            trackDao = database.trackDao(),
            progressDao = database.progressDao(),
            importer = importer,
            settings = settings,
            scope = appScope,
        )
    }

    val playback: PlaybackController by lazy {
        PlaybackController(appContext, library, settings, recorder)
    }
}
