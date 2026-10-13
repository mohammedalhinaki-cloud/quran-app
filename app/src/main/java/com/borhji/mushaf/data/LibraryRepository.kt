package com.borhji.mushaf.data

import android.content.Context
import android.net.Uri
import com.borhji.mushaf.data.importer.SurahResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * مصدر الحقيقة لمكتبة التلاوات: يجمع الملفات المستوردة + مواضع الاستماع + حالة التوفّر.
 * كل شيء محلي تمامًا، ولا يوجد أي اتصال بخادم.
 */
class LibraryRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val progressDao: ProgressDao,
    private val importer: AudioImporter,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val resolver = SurahResolver()

    /** حالة توفّر كل ملف (يُعاد حسابها عند بدء التطبيق/الاستيراد/التعيين) */
    private val availability = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    private val _importState = MutableStateFlow<ImportState?>(null)
    val importState: StateFlow<ImportState?> = _importState.asStateFlow()

    /** قائمة السور الـ114 مرتبة ترتيب المصحف مع حالة كل سورة */
    val surahList: StateFlow<List<SurahUiModel>> = combine(
        trackDao.observeAll(),
        progressDao.observeAll(),
        availability,
    ) { tracks, progress, availabilityMap ->
        val trackMap = tracks.associateBy { it.surahNumber }
        val progressMap = progress.associateBy { it.surahNumber }
        SURAHS.map { info ->
            val track = trackMap[info.number]
            val prog = progressMap[info.number]
            SurahUiModel(
                number = info.number,
                name = info.name,
                revelation = info.revelation.label,
                ayahCount = info.ayahCount,
                fileName = track?.displayName,
                uri = track?.uri?.let { Uri.parse(it) },
                available = track != null && (availabilityMap[info.number] ?: false),
                durationMs = (track?.durationMs ?: 0L).takeIf { it > 0L } ?: (prog?.durationMs ?: 0L),
                positionMs = prog?.positionMs ?: 0L,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun refreshAvailability() = withContext(Dispatchers.IO) {
        val tracks = trackDao.getAll()
        val map = tracks.associate { it.surahNumber to importer.isReadable(Uri.parse(it.uri)) }
        availability.value = map
    }

    suspend fun trackFor(surah: Int): TrackEntity? = withContext(Dispatchers.IO) { trackDao.get(surah) }

    suspend fun progressFor(surah: Int): ProgressEntity? =
        withContext(Dispatchers.IO) { progressDao.get(surah) }

    /** كل السور المتوفرة فعليًا، مرتبة ترتيب المصحف — تُستخدم كقائمة تشغيل */
    suspend fun playableTracks(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        trackDao.getAll().mapNotNull { track ->
            val uri = Uri.parse(track.uri)
            if (!importer.isReadable(uri)) return@mapNotNull null
            val info = SURAH_BY_NUMBER[track.surahNumber] ?: return@mapNotNull null
            PlayableTrack(
                surah = track.surahNumber,
                name = info.name,
                uri = uri,
                durationMs = track.durationMs,
            )
        }.sortedBy { it.surah }
    }

    /** تعيين ملف يدويًا لسورة معينة */
    suspend fun assignFile(surah: Int, uri: Uri, copyIntoApp: Boolean): Result<File?> =
        withContext(Dispatchers.IO) {
            try {
                val name = importer.displayName(uri)
                val previous = trackDao.get(surah)
                val storedUri: Uri
                val source: TrackSource
                if (copyIntoApp) {
                    val file = importer.copyToLibrary(uri, surah, name)
                    storedUri = Uri.fromFile(file)
                    source = TrackSource.APP_COPY
                } else {
                    importer.takePersistablePermission(uri)
                    storedUri = uri
                    source = TrackSource.MANUAL_ASSIGN
                }
                if (!importer.isReadable(storedUri)) {
                    return@withContext Result.failure(Exception("تعذّر الوصول إلى الملف المختار"))
                }
                val duration = importer.readDurationMs(storedUri)
                if (previous != null && previous.source == TrackSource.APP_COPY.name &&
                    previous.uri != storedUri.toString()
                ) {
                    importer.deleteLibraryCopy(Uri.parse(previous.uri))
                }
                trackDao.upsert(
                    TrackEntity(
                        surahNumber = surah,
                        uri = storedUri.toString(),
                        displayName = name,
                        source = source.name,
                        sizeBytes = importer.sizeOf(storedUri),
                        durationMs = duration,
                        addedAt = System.currentTimeMillis(),
                    )
                )
                refreshAvailability()
                Result.success(null)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /** حذف ملف سورة (مع الاحتفاظ أو حذف الموضع) */
    suspend fun removeTrack(surah: Int, deleteProgress: Boolean = false) = withContext(Dispatchers.IO) {
        val track = trackDao.get(surah)
        if (track != null && track.source != TrackSource.DEVICE_LINK.name) {
            importer.deleteLibraryCopy(Uri.parse(track.uri))
        }
        trackDao.delete(surah)
        if (deleteProgress) {
            progressDao.delete(surah)
            settings.clearLastIfSurah(surah)
        }
        refreshAvailability()
    }

    suspend fun resetProgress(surah: Int) = withContext(Dispatchers.IO) {
        progressDao.delete(surah)
        settings.clearLastIfSurah(surah)
    }

    suspend fun clearAllProgress() = withContext(Dispatchers.IO) {
        progressDao.clear()
        settings.clearLast()
    }

    suspend fun missingSurahs(): List<Int> = withContext(Dispatchers.IO) {
        val present = trackDao.getAll().map { it.surahNumber }.toSet()
        (1..SURAH_COUNT).filterNot { it in present }
    }

    // ---------------------------------------------------------------- الاستيراد

    /** بدء استيراد ملفات مختارة في الخلفية (يستمر حتى لو تغيّرت الواجهة) */
    fun startImport(uris: List<Uri>, copyIntoApp: Boolean) {
        if (_importState.value is ImportState.Running) return
        scope.launch {
            _importState.value = ImportState.Running(0, uris.size, "")
            try {
                val outcome = importFiles(uris, copyIntoApp) { progress ->
                    _importState.value = ImportState.Running(
                        progress.processed, progress.total, progress.currentName,
                    )
                }
                _importState.value = ImportState.Finished(outcome)
            } catch (t: Throwable) {
                _importState.value = ImportState.Failed(t.message ?: "خطأ غير معروف أثناء الاستيراد")
            }
        }
    }

    fun dismissImport() {
        _importState.value = null
    }

    /** استيراد كل الملفات الصوتية داخل مجلد مختار عبر SAF (مع صلاحية دائمة) */
    fun startFolderImport(treeUri: Uri, copyIntoApp: Boolean) {
        if (_importState.value is ImportState.Running) return
        scope.launch {
            _importState.value = ImportState.Running(0, 0, "جارٍ فحص المجلد المختار…")
            val uris = try {
                withContext(Dispatchers.IO) {
                    importer.takePersistablePermission(treeUri)
                    importer.listAudioFilesInTree(treeUri)
                }
            } catch (_: Throwable) {
                emptyList()
            }
            _importState.value = null
            if (uris.isEmpty()) {
                _importState.value = ImportState.Failed(
                    "لم يتم العثور على ملفات صوتية (MP3/M4A) داخل المجلد المختار."
                )
                return@launch
            }
            startImport(uris, copyIntoApp)
        }
    }

    private suspend fun importFiles(
        uris: List<Uri>,
        copyIntoApp: Boolean,
        onProgress: (ImportProgress) -> Unit,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        val existing = trackDao.getAll().associateBy { it.surahNumber }
        val handled = HashSet<Int>()
        val duplicates = ArrayList<Int>()
        val unmatched = ArrayList<UnmatchedFile>()
        val failures = ArrayList<String>()
        var imported = 0

        // التحقق من المساحة قبل النسخ
        if (copyIntoApp) {
            val needed = importer.totalSize(uris)
            val available = importer.usableSpace()
            if (needed > 0L && available in 1 until needed + SPACE_MARGIN) {
                return@withContext ImportOutcome(
                    imported = 0,
                    duplicates = emptyList(),
                    unmatched = emptyList(),
                    missingSurahs = missingSurahs(),
                    failures = emptyList(),
                    copyIntoApp = copyIntoApp,
                    error = "المساحة غير كافية لنسخ الملفات " +
                        "(المطلوب ${needed / (1024 * 1024)} م.ب، المتاح ${available / (1024 * 1024)} م.ب). " +
                        "يمكنك إلغاء خيار النسخ والاعتماد على ربط الملفات مباشرة.",
                )
            }
        }

        uris.forEachIndexed { index, uri ->
            onProgress(ImportProgress(index, uris.size, importer.displayName(uri)))
            try {
                val name = importer.displayName(uri)
                val match = resolver.resolve(name)
                if (match == null) {
                    importer.takePersistablePermission(uri)
                    unmatched += UnmatchedFile(uri, name)
                    return@forEachIndexed
                }
                if (!handled.add(match.surah)) {
                    duplicates += match.surah
                    return@forEachIndexed
                }

                val storedUri: Uri
                val source: TrackSource
                if (copyIntoApp) {
                    val file = importer.copyToLibrary(uri, match.surah, name)
                    storedUri = Uri.fromFile(file)
                    source = TrackSource.APP_COPY
                } else {
                    importer.takePersistablePermission(uri)
                    storedUri = uri
                    source = TrackSource.DEVICE_LINK
                }

                if (!importer.isReadable(storedUri)) {
                    failures += "$name (تعذّر الوصول للملف)"
                    return@forEachIndexed
                }

                val duration = importer.readDurationMs(storedUri)
                val previous = existing[match.surah]
                if (previous != null && previous.source == TrackSource.APP_COPY.name &&
                    previous.uri != storedUri.toString()
                ) {
                    importer.deleteLibraryCopy(Uri.parse(previous.uri))
                }

                trackDao.upsert(
                    TrackEntity(
                        surahNumber = match.surah,
                        uri = storedUri.toString(),
                        displayName = name,
                        source = source.name,
                        sizeBytes = importer.sizeOf(storedUri),
                        durationMs = duration,
                        addedAt = System.currentTimeMillis(),
                    )
                )
                imported++
            } catch (t: Throwable) {
                failures += "${importer.displayName(uri)} (${t.message ?: "خطأ"})"
            }
        }

        availability.value = trackDao.getAll()
            .associate { it.surahNumber to importer.isReadable(Uri.parse(it.uri)) }

        val present = trackDao.getAll().map { it.surahNumber }.toSet()
        ImportOutcome(
            imported = imported,
            duplicates = duplicates.distinct().sorted(),
            unmatched = unmatched,
            missingSurahs = (1..SURAH_COUNT).filterNot { it in present },
            failures = failures,
            copyIntoApp = copyIntoApp,
        )
    }

    companion object {
        private const val SPACE_MARGIN = 50L * 1024 * 1024
    }
}
