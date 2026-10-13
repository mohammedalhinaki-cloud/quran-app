package com.borhji.mushaf.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * الجذر الرئيسي لواجهة التطبيق.
 * اللغة العربية بالكامل مع اتجاه RTL، ولا توجد أي تبويبات أو حسابات أو إعلانات.
 */
@Composable
fun MushafRoot(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val unmatched by viewModel.unmatchedFile.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var showPlayerSheet by rememberSaveable { mutableStateOf(false) }
    var pendingSurahForFile by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val audioMimeTypes = remember { arrayOf("audio/*", "video/mp4", "application/ogg") }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val pickFilesLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                showImportSheet = true
                viewModel.onStartImport(uris)
            }
        }

    val pickFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                showImportSheet = true
                viewModel.onStartFolderImport(uri)
            }
        }

    val pickSingleFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val surah = pendingSurahForFile
            pendingSurahForFile = null
            if (uri != null && surah != null) {
                viewModel.onAssignFile(surah, uri)
            }
        }

    fun startAssign(surah: Int) {
        pendingSurahForFile = surah
        pickSingleFileLauncher.launch(audioMimeTypes)
    }

    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.permissionRequest.collect {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (player.surahNumber != null) {
                    MiniPlayer(
                        state = player,
                        onOpen = { showPlayerSheet = true },
                        onTogglePlay = { viewModel.onTogglePlayPause() },
                        onNext = { viewModel.onNext() },
                    )
                }
            },
        ) { padding ->
            HomeScreen(
                state = state,
                player = player,
                contentPadding = padding,
                onOpenImport = { showImportSheet = true },
                onPickFiles = { pickFilesLauncher.launch(audioMimeTypes) },
                onPickFolder = { pickFolderLauncher.launch(null) },
                onQueryChange = { viewModel.onQueryChange(it) },
                onToggleMissingOnly = { viewModel.onToggleMissingFilter() },
                onPlaySurah = { viewModel.onPlaySurah(it) },
                onResume = { viewModel.onResumeLast() },
                onAssignFile = { startAssign(it) },
                onRemoveTrack = { viewModel.onRemoveTrack(it) },
                onResetProgress = { viewModel.onResetProgress(it) },
            )
        }

        if (showImportSheet) {
            ImportSheet(
                importState = importState,
                copyIntoApp = state.copyIntoApp,
                onCopyIntoAppChange = { viewModel.onCopyIntoAppChange(it) },
                onPickFiles = { pickFilesLauncher.launch(audioMimeTypes) },
                onPickFolder = { pickFolderLauncher.launch(null) },
                onDismiss = {
                    showImportSheet = false
                    viewModel.onDismissImport()
                },
                onAssignUnmatched = { uri, name ->
                    showImportSheet = false
                    viewModel.onUnmatchedFileSelected(uri, name)
                },
                onShowMissing = {
                    showImportSheet = false
                    viewModel.onShowMissingOnly()
                },
            )
        }

        if (showPlayerSheet && player.surahNumber != null) {
            PlayerSheet(
                state = player,
                onDismiss = { showPlayerSheet = false },
                onTogglePlay = { viewModel.onTogglePlayPause() },
                onSeekTo = { viewModel.onSeekTo(it) },
                onSeekBy = { viewModel.onSeekBy(it) },
                onNext = { viewModel.onNext() },
                onPrevious = { viewModel.onPrevious() },
                onSetSpeed = { viewModel.onSetSpeed(it) },
                onToggleRepeat = { viewModel.onToggleRepeatOne() },
                onStop = {
                    viewModel.onStopPlayback()
                    showPlayerSheet = false
                },
            )
        }

        val pendingUnmatched = unmatched
        if (pendingUnmatched != null) {
            SurahPickerDialog(
                title = "تعيين السورة الصحيحة للملف",
                subtitle = pendingUnmatched.second,
                onPick = { viewModel.onUnmatchedFileResolved(it) },
                onDismiss = { viewModel.onCancelUnmatched() },
            )
        }
    }
}
