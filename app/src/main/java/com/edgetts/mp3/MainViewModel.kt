package com.edgetts.mp3

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edgetts.mp3.io.AudioLibrary
import com.edgetts.mp3.io.DocumentReader
import com.edgetts.mp3.io.LoadedDocument
import com.edgetts.mp3.tts.Prosody
import com.edgetts.mp3.tts.SpeechCleaner
import com.edgetts.mp3.tts.SynthesisEngine
import com.edgetts.mp3.tts.Voice
import com.edgetts.mp3.tts.Voices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SourceMode { Type, File }

data class GeneratedAudio(val file: File, val seconds: Long, val publicLocation: String?)

data class UiState(
    val mode: SourceMode = SourceMode.Type,
    val inputText: String = "",
    val document: LoadedDocument? = null,
    val loadingDocument: Boolean = false,
    val voice: Voice = Voices.default,
    val ratePercent: Int = 0,
    val pitchHz: Int = 0,
    val outputName: String = "",
    val generating: Boolean = false,
    val chunksDone: Int = 0,
    val chunksTotal: Int = 0,
    val previewing: Boolean = false,
    val result: GeneratedAudio? = null,
    val library: List<File> = emptyList(),
    val playingPath: String? = null,
    val message: String? = null,
    /** Strip Markdown, symbols, URLs and emoji before synthesis. */
    val cleanForSpeech: Boolean = true,
    /** Exactly what will be sent to the voice (cleaned or raw), refreshed in the background. */
    val speechText: String = "",
    val wordCount: Int = 0,
) {
    val sourceText: String get() = if (mode == SourceMode.File) document?.text.orEmpty() else inputText
    val canGenerate: Boolean get() = sourceText.isNotBlank() && !generating && !loadingDocument

    /** Rough length at ~155 words per minute, scaled by the speed slider. */
    val estimatedSeconds: Long
        get() = (wordCount / 155.0 * 60 / (1 + ratePercent / 100.0)).toLong()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val reader = DocumentReader(app)
    private val library = AudioLibrary(app)
    private val engine = SynthesisEngine()

    private val _state = MutableStateFlow(
        UiState(
            voice = Voices.byId(prefs.getString("voice", null)),
            ratePercent = prefs.getInt("rate", 0),
            pitchHz = prefs.getInt("pitch", 0),
            cleanForSpeech = prefs.getBoolean("clean", true),
            library = library.list(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Keep the "what will be read" preview and word count in sync without blocking typing.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _state.map { it.sourceText to it.cleanForSpeech }
                .distinctUntilChanged()
                .debounce(250)
                .collectLatest { (text, clean) ->
                    val spoken = withContext(Dispatchers.Default) { speechTextFor(text, clean) }
                    val words = spoken.split(Regex("\\s+")).count { it.isNotBlank() }
                    _state.update { it.copy(speechText = spoken, wordCount = words) }
                }
        }
    }

    private fun speechTextFor(text: String, clean: Boolean) = if (clean) SpeechCleaner.clean(text) else text

    private var job: Job? = null
    private var player: MediaPlayer? = null
    /** File waiting for the "Save as" picker to return a destination. */
    var pendingSave: File? = null

    fun setMode(mode: SourceMode) = _state.update { it.copy(mode = mode) }
    fun setText(text: String) = _state.update { it.copy(inputText = text) }
    fun setOutputName(name: String) = _state.update { it.copy(outputName = name) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun setVoice(voice: Voice) {
        prefs.edit().putString("voice", voice.id).apply()
        _state.update { it.copy(voice = voice) }
    }

    fun setRate(value: Int) {
        prefs.edit().putInt("rate", value).apply()
        _state.update { it.copy(ratePercent = value) }
    }

    fun setPitch(value: Int) {
        prefs.edit().putInt("pitch", value).apply()
        _state.update { it.copy(pitchHz = value) }
    }

    fun resetTuning() { setRate(0); setPitch(0) }

    fun setCleanForSpeech(enabled: Boolean) {
        prefs.edit().putBoolean("clean", enabled).apply()
        _state.update { it.copy(cleanForSpeech = enabled) }
    }

    fun loadDocument(uri: Uri) {
        _state.update { it.copy(mode = SourceMode.File, loadingDocument = true) }
        viewModelScope.launch {
            try {
                val doc = reader.load(uri)
                _state.update {
                    it.copy(
                        document = doc,
                        loadingDocument = false,
                        outputName = doc.name.substringBeforeLast('.'),
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loadingDocument = false, message = "Could not read file: ${e.message}") }
            }
        }
    }

    fun loadSharedText(text: String) {
        _state.update { it.copy(mode = SourceMode.Type, inputText = text) }
    }

    fun clearDocument() = _state.update { it.copy(document = null, outputName = "") }

    fun generate() {
        val s = _state.value
        if (!s.canGenerate) return
        stopPlayback()
        val baseName = s.outputName.ifBlank {
            s.document?.name?.substringBeforeLast('.')
                ?: ("speech " + SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(Date()))
        }
        val out = library.uniqueFile(baseName)
        _state.update { it.copy(generating = true, chunksDone = 0, chunksTotal = 0, result = null) }
        job = viewModelScope.launch {
            try {
                val spoken = withContext(Dispatchers.Default) { speechTextFor(s.sourceText, s.cleanForSpeech) }
                val file = engine.synthesizeToFile(
                    text = spoken,
                    voice = s.voice,
                    prosody = Prosody(s.ratePercent, s.pitchHz),
                    output = out,
                    workDir = library.workDir,
                    title = baseName,
                ) { p -> _state.update { it.copy(chunksDone = p.done, chunksTotal = p.total) } }
                val publicPath = library.publishToMusic(file)
                val result = GeneratedAudio(file, SynthesisEngine.estimateSeconds(file.length()), publicPath)
                _state.update {
                    it.copy(
                        generating = false,
                        result = result,
                        library = library.list(),
                        message = "Saved " + (publicPath ?: file.name),
                    )
                }
            } catch (e: CancellationException) {
                out.delete()
                _state.update { it.copy(generating = false, message = "Cancelled") }
            } catch (e: Throwable) {
                out.delete()
                _state.update { it.copy(generating = false, message = e.message ?: "Generation failed") }
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun previewVoice() {
        val s = _state.value
        if (s.previewing) return
        _state.update { it.copy(previewing = true) }
        viewModelScope.launch {
            try {
                val sample = "Hi, I'm ${s.voice.name}. This is how your document will sound with these settings."
                val file = File(library.workDir, "preview.mp3")
                engine.preview(sample, s.voice, Prosody(s.ratePercent, s.pitchHz), file)
                play(file)
            } catch (e: Throwable) {
                _state.update { it.copy(message = "Preview failed: ${e.message}") }
            } finally {
                _state.update { it.copy(previewing = false) }
            }
        }
    }

    fun togglePlay(file: File) {
        if (_state.value.playingPath == file.absolutePath) stopPlayback() else play(file)
    }

    private fun play(file: File) {
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            _state.update { it.copy(playingPath = file.absolutePath) }
        } catch (e: Exception) {
            _state.update { it.copy(message = "Playback failed: ${e.message}") }
        }
    }

    fun stopPlayback() {
        player?.runCatching { stop(); release() }
        player = null
        _state.update { it.copy(playingPath = null) }
    }

    fun delete(file: File) {
        if (_state.value.playingPath == file.absolutePath) stopPlayback()
        file.delete()
        _state.update {
            it.copy(library = library.list(), result = it.result?.takeIf { r -> r.file != file })
        }
    }

    fun shareIntent(file: File) = library.shareIntent(file)

    fun saveTo(target: Uri) {
        val file = pendingSave ?: return
        pendingSave = null
        viewModelScope.launch {
            try {
                library.copyTo(file, target)
                _state.update { it.copy(message = "Saved a copy of ${file.name}") }
            } catch (e: Throwable) {
                _state.update { it.copy(message = "Save failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
