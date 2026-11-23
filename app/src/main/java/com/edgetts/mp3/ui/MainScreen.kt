package com.edgetts.mp3.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgetts.mp3.GeneratedAudio
import com.edgetts.mp3.MainViewModel
import com.edgetts.mp3.SourceMode
import com.edgetts.mp3.UiState
import com.edgetts.mp3.io.DocKind
import com.edgetts.mp3.tts.Gender
import com.edgetts.mp3.tts.SynthesisEngine
import com.edgetts.mp3.tts.Voice
import com.edgetts.mp3.tts.Voices
import com.edgetts.mp3.ui.theme.Palette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::loadDocument)
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        if (uri != null) vm.saveTo(uri) else vm.pendingSave = null
    }
    val onSaveAs: (File) -> Unit = { f -> vm.pendingSave = f; saveAs.launch(f.name) }
    val onShare: (File) -> Unit = { f -> context.startActivity(vm.shareIntent(f)) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        containerColor = Palette.Bg,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.navigationBarsPadding()) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            AmbientGlow()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Header()
                SourceCard(
                    state = state,
                    onMode = vm::setMode,
                    onText = vm::setText,
                    onPick = {
                        openDoc.launch(arrayOf("text/*", "application/pdf", "application/octet-stream"))
                    },
                    onClearDoc = vm::clearDocument,
                )
                VoiceCard(state, vm::setVoice, vm::previewVoice)
                TuningCard(state, vm::setRate, vm::setPitch, vm::resetTuning)
                OutputCard(state, vm::setOutputName)
                GenerateSection(state, vm::generate, vm::cancel)
                AnimatedVisibility(state.result != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    state.result?.let {
                        ResultCard(it, state.playingPath, vm::togglePlay, onShare, onSaveAs)
                    }
                }
                LibraryCard(state, vm::togglePlay, onShare, onSaveAs, vm::delete)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------- Building blocks ----------

@Composable
private fun AmbientGlow() {
    val t = rememberInfiniteTransition(label = "glow")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    Box(
        Modifier
            .size(420.dp)
            .offset(x = (-120 + 160 * shift).dp, y = (-140).dp)
            .background(Brush.radialGradient(listOf(Palette.Violet.copy(alpha = 0.22f), Color.Transparent)), CircleShape),
    )
    Box(
        Modifier
            .size(360.dp)
            .offset(x = (220 - 120 * shift).dp, y = 60.dp)
            .background(Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.12f), Color.Transparent)), CircleShape),
    )
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.GraphicEq, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text(
                "Edge TTS Studio",
                style = MaterialTheme.typography.displaySmall.copy(brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFC4B5FD), Palette.Cyan))),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Neural voices from Microsoft Edge. Text, Markdown or PDF in, one clean MP3 out.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
    }
}

@Composable
private fun GlassCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Palette.SurfaceHigh.copy(alpha = 0.92f), Palette.Surface.copy(alpha = 0.92f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))), shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Palette.Violet, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Pill(text: String, color: Color = Palette.Muted, bg: Color = Palette.SurfaceHigher) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun SegmentedToggle(options: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, (label, icon) ->
            val active = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Palette.accentHorizontal else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (active) Color.White else Palette.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Color.White else Palette.Muted)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Violet,
    unfocusedBorderColor = Palette.Stroke,
    focusedContainerColor = Palette.Bg.copy(alpha = 0.6f),
    unfocusedContainerColor = Palette.Bg.copy(alpha = 0.6f),
    cursorColor = Palette.Cyan,
    focusedLabelColor = Palette.Violet,
    unfocusedLabelColor = Palette.Muted,
)

// ---------- Source ----------

@Composable
private fun SourceCard(
    state: UiState,
    onMode: (SourceMode) -> Unit,
    onText: (String) -> Unit,
    onPick: () -> Unit,
    onClearDoc: () -> Unit,
) {
    GlassCard("Source", Icons.Rounded.Description) {
        SegmentedToggle(
            listOf("Type text" to Icons.Rounded.TextFields, "Import file" to Icons.Rounded.UploadFile),
            selected = state.mode.ordinal,
        ) { onMode(SourceMode.entries[it]) }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(state.mode, label = "source") { mode ->
            when (mode) {
                SourceMode.Type -> OutlinedTextField(
                    value = state.inputText,
                    onValueChange = onText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text("Paste or type anything you want read aloud...", color = Palette.Muted) },
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                SourceMode.File -> FilePane(state, onPick, onClearDoc)
            }
        }
        if (state.sourceText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("%,d words".format(state.wordCount))
                Pill("~${formatDuration(state.estimatedSeconds)} audio")
            }
        }
    }
}

@Composable
private fun FilePane(state: UiState, onPick: () -> Unit, onClear: () -> Unit) {
    val doc = state.document
    when {
        state.loadingDocument -> Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = Palette.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Extracting text...", color = Palette.Muted)
        }
        doc == null -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, Brush.linearGradient(listOf(Palette.Violet.copy(alpha = 0.7f), Palette.Cyan.copy(alpha = 0.5f))), RoundedCornerShape(18.dp))
                .background(Palette.Bg.copy(alpha = 0.5f))
                .clickable(onClick = onPick)
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.UploadFile, null, tint = Palette.Cyan, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text("Choose a file", style = MaterialTheme.typography.titleMedium)
            Text(".txt  .md  .pdf", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        else -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Bg.copy(alpha = 0.6f)).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigher),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (doc.kind == DocKind.Pdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                        null,
                        tint = if (doc.kind == DocKind.Pdf) Palette.Pink else Palette.Cyan,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        doc.kind.label + (doc.pages?.let { " · $it pages" } ?: "") + " · %,d characters".format(doc.text.length),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Remove", tint = Palette.Muted) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                doc.text.take(320).replace('\n', ' ') + if (doc.text.length > 320) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onPick) { Text("Choose another file", color = Palette.Cyan) }
        }
    }
}

// ---------- Voice ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceCard(state: UiState, onVoice: (Voice) -> Unit, onPreview: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<Gender?>(null) }
    val voice = state.voice
    val options = remember(filter) { Voices.all.filter { filter == null || it.gender == filter } }

    GlassCard("Voice", Icons.Rounded.RecordVoiceOver, trailing = {
        Text("${Voices.all.size} English voices", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Pair<String, Gender?>>("All" to null, "Female" to Gender.Female, "Male" to Gender.Male).forEach { (label, g) ->
                val active = filter == g
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) Color.White else Palette.Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Palette.Violet.copy(alpha = 0.35f) else Palette.SurfaceHigher)
                        .border(1.dp, if (active) Palette.Violet else Color.Transparent, RoundedCornerShape(50))
                        .clickable { filter = g; expanded = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            Row(
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Bg.copy(alpha = 0.6f))
                    .border(1.dp, if (expanded) Palette.Violet else Palette.Stroke, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoiceAvatar(voice, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(voice.name, style = MaterialTheme.typography.titleMedium)
                    Text("${voice.gender.label} · ${voice.country}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Palette.Muted, modifier = Modifier.rotate(rot))
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = Palette.SurfaceHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                var lastCountry = ""
                options.forEach { v ->
                    if (v.country != lastCountry) {
                        lastCountry = v.country
                        Text(
                            v.country.uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                            color = Palette.Violet,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    DropdownMenuItem(
                        leadingIcon = { VoiceAvatar(v, 30.dp) },
                        text = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(v.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                    Spacer(Modifier.width(8.dp))
                                    Text(v.gender.label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                }
                                Text(v.tone, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        onClick = { onVoice(v); expanded = false },
                        modifier = if (v.id == voice.id) Modifier.background(Palette.Violet.copy(alpha = 0.15f)) else Modifier,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Bg.copy(alpha = 0.45f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(voice.gender.label, color = if (voice.gender == Gender.Female) Palette.Pink else Palette.Cyan)
                Pill(voice.country)
                Pill(voice.locale)
            }
            DetailLine("Tone", voice.tone)
            DetailLine("Best for", voice.bestFor)
        }
        Spacer(Modifier.height(12.dp))
        OutlineAction(
            label = if (state.previewing) "Generating preview..." else "Preview this voice",
            icon = Icons.Rounded.Headphones,
            busy = state.previewing,
            onClick = onPreview,
        )
    }
}

@Composable
private fun VoiceAvatar(v: Voice, size: Dp) {
    val brush = if (v.gender == Gender.Female) {
        Brush.linearGradient(listOf(Palette.Pink, Palette.Violet))
    } else {
        Brush.linearGradient(listOf(Palette.Cyan, Palette.Indigo))
    }
    Box(Modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(v.countryCode, style = TextStyle(fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold, color = Color.White))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontSize = 10.sp), color = Palette.Violet)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
}

@Composable
private fun OutlineAction(label: String, icon: ImageVector, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Palette.Stroke, RoundedCornerShape(14.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(color = Palette.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        else Icon(icon, null, tint = Palette.Cyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

// ---------- Tuning ----------

@Composable
private fun TuningCard(state: UiState, onRate: (Int) -> Unit, onPitch: (Int) -> Unit, onReset: () -> Unit) {
    GlassCard("Speed and pitch", Icons.Rounded.Tune, trailing = {
        if (state.ratePercent != 0 || state.pitchHz != 0) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = Palette.Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }) {
        val speedFactor = 1 + state.ratePercent / 100f
        LabeledSlider(
            icon = Icons.Rounded.Speed,
            label = "Speed",
            valueText = "%.2fx".format(speedFactor),
            value = state.ratePercent.toFloat(),
            range = -50f..100f,
            steps = 29,
            leftHint = "0.5x",
            rightHint = "2.0x",
            onChange = { onRate((it / 5).roundToInt() * 5) },
        )
        Spacer(Modifier.height(18.dp))
        LabeledSlider(
            icon = Icons.Rounded.GraphicEq,
            label = "Pitch",
            valueText = (if (state.pitchHz > 0) "+" else "") + "${state.pitchHz} Hz",
            value = state.pitchHz.toFloat(),
            range = -50f..50f,
            steps = 19,
            leftHint = "Deeper",
            rightHint = "Higher",
            onChange = { onPitch((it / 5).roundToInt() * 5) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSlider(
    icon: ImageVector,
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    leftHint: String,
    rightHint: String,
    onChange: (Float) -> Unit,
) {
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Palette.Violet,
        inactiveTrackColor = Palette.SurfaceHigher,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.titleMedium.copy(brush = Palette.accentHorizontal, fontWeight = FontWeight.Bold),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = colors,
            track = { sliderState ->
                SliderDefaults.Track(sliderState = sliderState, colors = colors, drawStopIndicator = null)
            },
        )
        Row {
            Text(leftHint, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.weight(1f))
            Text(rightHint, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

// ---------- Output + generate ----------

@Composable
private fun OutputCard(state: UiState, onName: (String) -> Unit) {
    GlassCard("File name", Icons.Rounded.Download) {
        OutlinedTextField(
            value = state.outputName,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(state.document?.name?.substringBeforeLast('.') ?: "speech (date and time)", color = Palette.Muted) },
            suffix = { Text(".mp3", color = Palette.Muted) },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Saved to Music/Edge TTS on your phone. Use Save as to pick another folder.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
    }
}

@Composable
private fun GenerateSection(state: UiState, onGenerate: () -> Unit, onCancel: () -> Unit) {
    AnimatedContent(state.generating, label = "gen") { generating ->
        if (!generating) {
            val enabled = state.canGenerate
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (enabled) Palette.accent else Brush.linearGradient(listOf(Palette.SurfaceHigher, Palette.SurfaceHigher)))
                    .clickable(enabled = enabled, onClick = onGenerate),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = if (enabled) Color.White else Palette.Muted)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Generate MP3",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = if (enabled) Color.White else Palette.Muted,
                )
            }
        } else {
            GenerationProgress(state, onCancel)
        }
    }
}

@Composable
private fun GenerationProgress(state: UiState, onCancel: () -> Unit) {
    val fraction = if (state.chunksTotal == 0) 0f else state.chunksDone / state.chunksTotal.toFloat()
    val animated by animateFloatAsState(fraction, tween(400), label = "progress")
    val t = rememberInfiniteTransition(label = "shimmer")
    val shimmer by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "s")

    GlassCard("Generating", Icons.Rounded.GraphicEq, trailing = {
        TextButton(onClick = onCancel) { Text("Cancel", color = Palette.Danger) }
    }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${(animated * 100).roundToInt()}%",
                style = MaterialTheme.typography.displaySmall.copy(brush = Palette.accentHorizontal),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (state.chunksTotal == 0) "Splitting text..." else "Chunk ${state.chunksDone} of ${state.chunksTotal}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Palette.SurfaceHigher)) {
            Box(
                Modifier
                    .fillMaxWidth(animated.coerceAtLeast(0.02f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Palette.Violet, Palette.Cyan, Palette.Violet),
                            startX = -600f + 1200f * shimmer,
                            endX = 600f + 1200f * shimmer,
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Chunks are synthesised in parallel, then stitched into a single MP3. Keep the app open for long documents.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
    }
}

// ---------- Result + library ----------

@Composable
private fun ResultCard(
    result: GeneratedAudio,
    playingPath: String?,
    onPlay: (File) -> Unit,
    onShare: (File) -> Unit,
    onSaveAs: (File) -> Unit,
) {
    val playing = playingPath == result.file.absolutePath
    GlassCard("Ready", Icons.Rounded.AutoAwesome) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Palette.accent).clickable { onPlay(result.file) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(result.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatDuration(result.seconds)} · ${formatSize(result.file.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Muted,
                )
            }
        }
        result.publicLocation?.let {
            Spacer(Modifier.height(10.dp))
            Text("Saved to $it", style = MaterialTheme.typography.bodySmall, color = Palette.Success)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { OutlineAction("Share", Icons.Rounded.Share) { onShare(result.file) } }
            Box(Modifier.weight(1f)) { OutlineAction("Save as", Icons.Rounded.Download) { onSaveAs(result.file) } }
        }
    }
}

@Composable
private fun LibraryCard(
    state: UiState,
    onPlay: (File) -> Unit,
    onShare: (File) -> Unit,
    onSaveAs: (File) -> Unit,
    onDelete: (File) -> Unit,
) {
    if (state.library.isEmpty()) return
    GlassCard("Library", Icons.Rounded.LibraryMusic, trailing = {
        Text("${state.library.size}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }) {
        state.library.take(30).forEachIndexed { i, file ->
            if (i > 0) HorizontalDivider(color = Palette.Stroke, modifier = Modifier.padding(vertical = 4.dp))
            val playing = state.playingPath == file.absolutePath
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (playing) Palette.accent else Brush.linearGradient(listOf(Palette.SurfaceHigher, Palette.SurfaceHigher)))
                        .clickable { onPlay(file) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatDuration(SynthesisEngine.estimateSeconds(file.length()))} · ${formatSize(file.length())} · ${formatDate(file.lastModified())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                }
                IconButton(onClick = { onShare(file) }) { Icon(Icons.Rounded.Share, "Share", tint = Palette.Muted, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { onSaveAs(file) }) { Icon(Icons.Rounded.Download, "Save as", tint = Palette.Muted, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { onDelete(file) }) { Icon(Icons.Rounded.Delete, "Delete", tint = Palette.Muted, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

// ---------- Formatting ----------

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "${s}s"
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%d KB".format((bytes / 1000).coerceAtLeast(1))
}

private fun formatDate(millis: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
