package com.barhaji.quran

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore(name = "barhaji_prefs")

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller by mutableStateOf<MediaController?>(null)

    companion object {
        val KEY_FOLDER_URI = stringPreferencesKey("folder_uri")
        val KEY_LAST_SURAH_NUM = intPreferencesKey("last_surah_num")
        val KEY_LAST_SURAH_NAME = stringPreferencesKey("last_surah_name")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_pos_ms")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
        }, ContextCompat.getMainExecutor(this))

        setContent {
            val scope = rememberCoroutineScope()
            var folderUriString by remember { mutableStateOf<String?>(null) }
            var lastSurahNum by remember { mutableStateOf(1) }
            var lastSurahName by remember { mutableStateOf("الفاتحة") }
            var lastPosMs by remember { mutableStateOf(0L) }
            var isPlaying by remember { mutableStateOf(false) }

            // قراءة البيانات المحفوظة
            LaunchedEffect(Unit) {
                val prefs = dataStore.data.first()
                folderUriString = prefs[KEY_FOLDER_URI]
                lastSurahNum = prefs[KEY_LAST_SURAH_NUM] ?: 1
                lastSurahName = prefs[KEY_LAST_SURAH_NAME] ?: "الفاتحة"
                lastPosMs = prefs[KEY_LAST_POSITION_MS] ?: 0L
            }

            // مراقبة حالة التشغيل وحفظ الموضع دوريًا
            LaunchedEffect(controller) {
                while (true) {
                    controller?.let { c ->
                        isPlaying = c.isPlaying
                        if (c.isPlaying) {
                            val currentMs = c.currentPosition
                            dataStore.edit { it[KEY_LAST_POSITION_MS] = currentMs }
                            lastPosMs = currentMs
                        }
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }

            // لاقط اختيار مجلد التلاوات من الهاتف
            val folderPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri?.let {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    folderUriString = it.toString()
                    scope.launch {
                        dataStore.edit { prefs -> prefs[KEY_FOLDER_URI] = it.toString() }
                    }
                }
            }

            QuranMainScreen(
                folderSelected = folderUriString != null,
                onSelectFolder = { folderPicker.launch(null) },
                lastSurahName = lastSurahName,
                lastSurahNum = lastSurahNum,
                isPlaying = isPlaying,
                onPlaySurah = { surahNum, surahName, startMs ->
                    playSurah(surahNum, folderUriString, startMs)
                    lastSurahNum = surahNum
                    lastSurahName = surahName
                    scope.launch {
                        dataStore.edit { prefs ->
                            prefs[KEY_LAST_SURAH_NUM] = surahNum
                            prefs[KEY_LAST_SURAH_NAME] = surahName
                        }
                    }
                },
                onTogglePlay = {
                    controller?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                }
            )
        }
    }

    private fun playSurah(surahNum: Int, folderUriStr: String?, startMs: Long) {
        val folderUri = folderUriStr?.let { Uri.parse(it) } ?: return
        val docFolder = DocumentFile.fromTreeUri(this, folderUri) ?: return
        
        // البحث عن ملف السورة (مثلاً 001.mp3 أو 1.mp3)
        val numPadded = "%03d".format(surahNum)
        val file = docFolder.listFiles().firstOrNull { f ->
            val name = f.name?.lowercase() ?: ""
            name.contains(numPadded) || name.startsWith("$surahNum.")
        } ?: return

        controller?.apply {
            setMediaItem(MediaItem.fromUri(file.uri))
            seekTo(startMs)
            prepare()
            play()
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranMainScreen(
    folderSelected: Boolean,
    onSelectFolder: () -> Unit,
    lastSurahName: String,
    lastSurahNum: Int,
    isPlaying: Boolean,
    onPlaySurah: (Int, String, Long) -> Unit,
    onTogglePlay: () -> Unit
) {
    val surahs = remember {
        listOf(
            "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
            "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
            "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
            "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
            "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
            "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
            "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
            "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
            "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
            "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
            "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
            "المسد", "الإخلاص", "الفلق", "الناس"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المصحف المرتل — د. محمد برهجي") },
                actions = {
                    IconButton(onClick = onSelectFolder) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "تحديد مجلد الصوت")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!folderSelected) {
                Button(
                    onClick = onSelectFolder,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("اضغط هنا لتحديد مجلد تلاوات الشيخ من هاتفك")
                }
            }

            // بطاقة متابعة الاستماع
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onPlaySurah(lastSurahNum, lastSurahName, 0L) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("متابعة الاستماع", style = MaterialTheme.typography.titleMedium)
                        Text("سورة $lastSurahName", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            HorizontalDivider()

            // قائمة السور الـ 114 كاملة
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(surahs) { index, name ->
                    val surahNumber = index + 1
                    ListItem(
                        headlineContent = { Text("سورة $name") },
                        leadingContent = { Text("$surahNumber", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.clickable {
                            onPlaySurah(surahNumber, name, 0L)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}