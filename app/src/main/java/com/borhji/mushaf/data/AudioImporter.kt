package com.borhji.mushaf.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * التعامل مع ملفات الصوت: قراءة الأسماء والمدد، نسخ الملفات إلى مساحة التطبيق،
 * والتحقق من صلاحية الوصول بعد إعادة تشغيل الجهاز.
 */
class AudioImporter(private val context: Context) {

    val libraryDir: File
        get() = File(context.filesDir, LIBRARY_DIR_NAME).apply { if (!exists()) mkdirs() }

    /** اسم الملف المعروض */
    fun displayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return File(uri.path ?: "").name
        }
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val value = cursor.getString(index)
                        if (!value.isNullOrBlank()) return value
                    }
                }
            }
        } catch (_: Throwable) {
            // نتجاهل ونعود للاسم البديل
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "ملف صوتي"
    }

    /** حجم الملف بالبايت (0 إذا تعذّر معرفته) */
    fun sizeOf(uri: Uri): Long {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return File(uri.path ?: "").length()
        }
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        } catch (_: Throwable) {
            // ignore
        }
        return 0L
    }

    /** هل يمكن قراءة الملف فعليًا الآن؟ (يكشف حذف الملف أو انتهاء صلاحية الوصول) */
    fun isReadable(uri: Uri): Boolean = try {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val f = File(uri.path ?: "")
                f.exists() && f.canRead() && f.length() > 0
            }
            ContentResolver.SCHEME_CONTENT -> {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                if (descriptor == null) false else {
                    descriptor.close()
                    true
                }
            }
            else -> false
        }
    } catch (_: Throwable) {
        false
    }

    /** قراءة مدة التلاوة بالمللي ثانية */
    fun readDurationMs(uri: Uri): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            raw?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            try {
                retriever?.release()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    /** تثبيت صلاحية الوصول الدائم لملف مختار عبر SAF */
    fun takePersistablePermission(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        } catch (_: Throwable) {
            // بعض موفري الملفات لا يسمحون بذلك — الملف يبقى صالحًا للاستخدام الحالي
            false
        }
    }

    /** نسخ ملف صوتي إلى مجلد التطبيق الخاص، وإرجاع الملف الجديد */
    fun copyToLibrary(uri: Uri, surah: Int, originalName: String): File {
        val extension = extensionOf(originalName).ifBlank { "mp3" }
        val dir = libraryDir
        val target = File(dir, String.format(Locale.US, "surah_%03d.%s", surah, extension))
        val temp = File(dir, target.name + ".part")

        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        } ?: throw IOException("تعذّر فتح الملف المصدر للقراءة")

        if (temp.length() <= 0L) {
            temp.delete()
            throw IOException("الملف فارغ أو غير قابل للقراءة")
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        // إزالة النسخ القديمة الأخرى لنفس السورة (بامتداد مختلف مثلًا)
        val prefix = String.format(Locale.US, "surah_%03d.", surah)
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix) && file.name != target.name) {
                file.delete()
            }
        }
        return target
    }

    /** حذف نسخة داخل مساحة التطبيق (مع التحقق من المسار للأمان) */
    fun deleteLibraryCopy(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_FILE) return false
        val path = uri.path ?: return false
        val file = File(path)
        val root = libraryDir.absolutePath
        if (!file.absolutePath.startsWith(root)) return false
        return file.delete()
    }

    /** حذف كل النسخ الداخلية لسورة */
    fun deleteLibraryCopyForSurah(surah: Int) {
        val prefix = String.format(Locale.US, "surah_%03d.", surah)
        libraryDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix)) file.delete()
        }
    }

    /** المساحة المتاحة في ذاكرة الجهاز */
    fun usableSpace(): Long = try {
        libraryDir.usableSpace
    } catch (_: Throwable) {
        0L
    }

    /** مجموع أحجام ملفات مختارة */
    fun totalSize(uris: List<Uri>): Long = uris.sumOf { sizeOf(it) }

    /** تعداد كل الملفات الصوتية داخل مجلد مختار (بشكل تكراري) */
    fun listAudioFilesInTree(treeUri: Uri, maxFiles: Int = 2000): List<Uri> {
        val result = ArrayList<Uri>()
        try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            walkTree(treeUri, rootId, result, 0, maxFiles)
        } catch (_: Throwable) {
            // ignore
        }
        if (result.isEmpty()) {
            // احتياطي: بعض الموفّرين لا يدعمون DocumentsContract
            try {
                context.contentResolver.query(
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri),
                    ),
                    null, null, null, null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        if (nameIndex >= 0 && idIndex >= 0) {
                            val name = cursor.getString(nameIndex) ?: continue
                            if (isAudioFileName(name)) {
                                result += DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri, cursor.getString(idIndex),
                                )
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // ignore
            }
        }
        return result.distinct()
    }

    private fun walkTree(
        treeUri: Uri,
        parentDocumentId: String,
        out: MutableList<Uri>,
        depth: Int,
        maxFiles: Int,
    ) {
        if (depth > MAX_DEPTH || out.size >= maxFiles) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < maxFiles) {
                    val documentId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: ""
                    val mime = cursor.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walkTree(treeUri, documentId, out, depth + 1, maxFiles)
                    } else if (mime.startsWith("audio") || isAudioFileName(name)) {
                        out += DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    }
                }
            }
        } catch (_: Throwable) {
            // نتجاهل المجلدات غير القابلة للقراءة
        }
    }

    companion object {
        private const val LIBRARY_DIR_NAME = "library"
        private const val BUFFER_SIZE = 256 * 1024
        private const val MAX_DEPTH = 6

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "mp4", "ogg", "oga", "opus", "wav", "flac", "amr", "3gp",
        )

        /** هل اسم الملف بامتداد صوتي مدعوم؟ */
        fun isAudioFileName(name: String): Boolean =
            extensionOf(name).lowercase(Locale.US) in AUDIO_EXTENSIONS

        /** امتداد الملف بدون نقطة */
        fun extensionOf(name: String): String {
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return ""
            return name.substring(dot + 1)
        }
    }
}
