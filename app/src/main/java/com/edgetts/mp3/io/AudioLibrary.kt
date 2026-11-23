package com.edgetts.mp3.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where finished MP3s live.
 *  - Working copy: app-specific external storage (Android/data/<pkg>/files/Music), always writable.
 *  - Public copy (Android 10+): Music/Edge TTS via MediaStore, so files show up in any music app
 *    and survive uninstall. No storage permission needed.
 *  - "Save as": any location the user picks through the system file picker.
 */
class AudioLibrary(private val context: Context) {

    val dir: File = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music"))
        .apply { mkdirs() }

    val workDir: File get() = File(context.cacheDir, "tts").apply { mkdirs() }

    fun list(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".mp3") }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun uniqueFile(baseName: String): File {
        val safe = baseName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "speech" }.take(80)
        var f = File(dir, "$safe.mp3")
        var n = 2
        while (f.exists()) f = File(dir, "$safe ($n).mp3").also { n++ }
        return f
    }

    /** Copies into the shared Music/Edge TTS folder. Returns a human-readable location, or null below Android 10. */
    suspend fun publishToMusic(file: File): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Edge TTS")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Music/Edge TTS/${file.name}"
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    suspend fun copyTo(file: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Could not open destination")
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Share MP3") }
    }
}
