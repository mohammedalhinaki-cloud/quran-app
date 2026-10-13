package com.borhji.mushaf.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.borhji.mushaf.MushafApp
import com.borhji.mushaf.data.ImportState
import com.borhji.mushaf.data.LibraryRepository
import com.borhji.mushaf.data.PlayerPrefs
import com.borhji.mushaf.data.ProgressRecorder
import com.borhji.mushaf.data.SURAH_BY_NUMBER
import com.borhji.mushaf.data.SettingsRepository
import com.borhji.mushaf.data.SurahUiModel
import com.borhji.mushaf.data.importer.ArabicText
import com.borhji.mushaf.playback.PlaybackController
import com.borhji.mushaf.playback.PlayerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** حالة الشاشة الرئيسية */
data class HomeUiState(
    val loading: Boolean = true,
    val all: List<SurahUiModel> = emptyList(),
    val filtered: List<SurahUiModel> = emptyList(),
    val importedCount: Int = 0,
    val missingCount: Int = 0,
    val query: String = "",
    val showMissingOnly: Boolean = false,
    val copyIntoApp: Boolean = true,
    val resumeSurah: Int? = null,
    val resumeName: String = "",
    val resumePositionMs: Long = 0L,
    val resumeDurationMs: Long = 0L,
    val resumeAvailable: Boolean = false,
)

class MainViewModel(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackController,
    private val recorder: ProgressRecorder,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showMissingOnly = MutableStateFlow(false)
    private val prefs: StateFlow<PlayerPrefs> =
        settings.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerPrefs())

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _permissionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val permissionRequest: SharedFlow<Unit> = _permissionRequest.asSharedFlow()

    private val _assignSurah = MutableStateFlow<Int?>(null)
    val assignSurah: StateFlow<Int?> = _assignSurah.asStateFlow()

    private val _unmatchedFile = MutableStateFlow<Pair<Uri, String>?>(null)
    val unmatchedFile: StateFlow<Pair<Uri, String>?> = _unmatchedFile.asStateFlow()

    val playerState: StateFlow<PlayerState> = playback.state
    val importState: StateFlow<ImportState?> = library.importState

    val uiState: StateFlow<HomeUiState> = combine(
        library.surahList,
        query,
        showMissingOnly,
        prefs,
    ) { surahs, q, missingOnly, prefs ->
        val normalized = ArabicText.compact(q)
        val filtered = surahs.filter { model ->
            val matchesQuery = normalized.isEmpty() ||
                ArabicText.compact(model.name).contains(normalized) ||
                ArabicText.compact(SURAH_BY_NUMBER[model.number]?.englishName ?: "").contains(normalized) ||
                model.number.toString() == normalized
            val matchesFilter = !missingOnly || !model.available
            matchesQuery && matchesFilter
        }
        val resumeSurah = prefs.lastSurah
        val resumeModel = resumeSurah?.let { n -> surahs.firstOrNull { it.number == n } }
        HomeUiState(
            loading = false,
            all = surahs,
            filtered = filtered,
            importedCount = surahs.count { it.available },
            missingCount = surahs.count { !it.available },
            query = q,
            showMissingOnly = missingOnly,
            copyIntoApp = prefs.copyIntoApp,
            resumeSurah = resumeSurah,
            resumeName = resumeModel?.name ?: resumeSurah?.let { SURAH_BY_NUMBER[it]?.name }.orEmpty(),
            resumePositionMs = resumeModel?.positionMs?.takeIf { it > 0L } ?: prefs.lastPositionMs,
            resumeDurationMs = resumeModel?.durationMs?.takeIf { it > 0L } ?: prefs.lastDurationMs,
            resumeAvailable = resumeModel?.available == true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        playback.connect()
        viewModelScope.launch { library.refreshAvailability() }
        viewModelScope.launch { prefs.collect { showMissingOnly.value = it.showMissingOnly } }
        viewModelScope.launch {
            playback.messages.collect { _uiMessage.emit(it) }
        }
    }

    // --------------------------------------------------------------- أحداث الواجهة

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onToggleMissingFilter() {
        val next = !showMissingOnly.value
        showMissingOnly.value = next
        viewModelScope.launch { settings.setShowMissingOnly(next) }
    }

    fun onCopyIntoAppChange(copy: Boolean) {
        viewModelScope.launch { settings.setCopyIntoApp(copy) }
    }

    fun onPlaySurah(surah: Int) {
        requestNotifications()
        playback.playSurah(surah, startPositionMs = null, autoPlay = true)
    }

    fun onResumeLast() {
        val target = prefs.value.lastSurah
        if (target == null) {
            _uiMessage.tryEmit("لا يوجد موضع محفوظ بعد.")
            return
        }
        requestNotifications()
        playback.playSurah(target, startPositionMs = null, autoPlay = true)
    }

    fun onTogglePlayPause() {
        requestNotifications()
        playback.togglePlayPause()
    }

    fun onNext() = playback.next()

    fun onPrevious() = playback.previous()

    fun onSeekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun onSeekBy(deltaMs: Long) = playback.seekBy(deltaMs)

    fun onSetSpeed(speed: Float) = playback.setSpeed(speed)

    fun onToggleRepeatOne() = playback.toggleRepeatOne()

    fun onStopPlayback() = playback.stopAndClear()

    // ---------------------------------------------------------------- الاستيراد

    fun onStartImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        library.startImport(uris, prefs.value.copyIntoApp)
    }

    fun onStartFolderImport(treeUri: Uri) {
        library.startFolderImport(treeUri, prefs.value.copyIntoApp)
    }

    fun onDismissImport() = library.dismissImport()

    fun onRequestAssign(surah: Int) {
        _assignSurah.value = surah
    }

    fun onCancelAssign() {
        _assignSurah.value = null
    }

    fun onAssignFile(surah: Int, uri: Uri) {
        viewModelScope.launch {
            val result = library.assignFile(surah, uri, prefs.value.copyIntoApp)
            result.onSuccess {
                val name = SURAH_BY_NUMBER[surah]?.name ?: "$surah"
                _uiMessage.emit("تم تعيين ملف سورة $name.")
            }.onFailure {
                _uiMessage.emit("تعذّر تعيين الملف: ${it.message ?: "خطأ غير معروف"}")
            }
            _assignSurah.value = null
        }
    }

    fun onUnmatchedFileSelected(uri: Uri, name: String) {
        _unmatchedFile.value = uri to name
    }

    fun onUnmatchedFileResolved(targetSurah: Int) {
        val pending = _unmatchedFile.value ?: return
        _unmatchedFile.value = null
        onAssignFile(targetSurah, pending.first)
    }

    fun onCancelUnmatched() {
        _unmatchedFile.value = null
    }

    // ---------------------------------------------------------------- إدارة المكتبة

    fun onRemoveTrack(surah: Int) {
        viewModelScope.launch {
            library.removeTrack(surah, deleteProgress = true)
            _uiMessage.emit("تم حذف ملف سورة ${SURAH_BY_NUMBER[surah]?.name ?: surah} من المكتبة.")
        }
    }

    fun onResetProgress(surah: Int) {
        recorder.resetSurah(surah)
        _uiMessage.tryEmit("تم تصفير موضع سورة ${SURAH_BY_NUMBER[surah]?.name ?: surah}.")
    }

    fun onShowMissingOnly() {
        showMissingOnly.value = true
        viewModelScope.launch { settings.setShowMissingOnly(true) }
        query.value = ""
    }

    fun onMessage(message: String) {
        _uiMessage.tryEmit(message)
    }

    private fun requestNotifications() {
        _permissionRequest.tryEmit(Unit)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MushafApp
                MainViewModel(
                    library = app.container.library,
                    settings = app.container.settings,
                    playback = app.container.playback,
                    recorder = app.container.recorder,
                )
            }
        }

    }
}
