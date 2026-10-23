package app.tubehaus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The video with its controls. On touch devices the controls are drawn here, the YouTube way:
 * tap to show or hide, double-tap a side to jump 10 seconds, a red seek bar, full screen and a
 * settings sheet for quality, speed and captions. On the desktop mpv draws its own controller
 * (Compose can't draw over the native video window there).
 */
@Composable
fun VideoArea(app: AppState, modifier: Modifier = Modifier, fullscreen: Boolean = false) {
    val player = app.platform.player
    Box(modifier.background(Color.Black)) {
        player.View(Modifier.fillMaxSize(), controls = !app.platform.touch)
        if (app.platform.touch) Controls(app, fullscreen)
    }
}

@Composable
private fun Controls(app: AppState, fullscreen: Boolean) {
    val player = app.platform.player
    val st by player.state.collectAsState()
    var shown by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }          // any touch restarts the hide timer
    var dragging by remember { mutableStateOf<Float?>(null) }
    var jump by remember { mutableStateOf<Pair<Int, Int>?>(null) }   // side (-1/1), seconds
    var sheet by remember { mutableStateOf(false) }

    LaunchedEffect(shown, poke, st.playing, dragging, sheet) {
        if (shown && st.playing && dragging == null && !sheet) {
            delay(3000)
            shown = false
        }
    }
    LaunchedEffect(jump) {
        if (jump != null) { delay(700); jump = null }
    }

    fun seekBy(sec: Int) {
        val to = (st.positionMs + sec * 1000L).coerceIn(0, maxOf(0, st.durationMs - 500))
        player.seekTo(to)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        // taps and double-taps anywhere on the video
        Box(
            Modifier.fillMaxSize().pointerInput(st.durationMs) {
                detectTapGestures(
                    onTap = { shown = !shown; poke++ },
                    onDoubleTap = { o ->
                        val side = when {
                            o.x < w * 0.4f -> -1
                            o.x > w * 0.6f -> 1
                            else -> 0
                        }
                        if (side == 0) { if (st.playing) player.pause() else player.play() }
                        else {
                            seekBy(side * 10)
                            jump = side to ((if (jump?.first == side) jump!!.second else 0) + 10)
                        }
                    },
                )
            },
        )
        // the "10 seconds" bubble of a double-tap
        jump?.let { (side, secs) ->
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(0.35f).align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.background(Color(0x66000000), CircleShape).padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (side < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10, null, tint = Color.White)
                    Text(tr("{n} seconds").replace("{n}", "$secs"), color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (st.buffering && !shown) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White, strokeWidth = 3.dp)
        }
        AnimatedVisibility(shown || st.ended, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                // top: back and title in full screen; captions and settings on the right
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 4.dp, vertical = if (fullscreen) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = Color.White) }
                    Text(
                        if (fullscreen) app.playback.video?.title ?: "" else "",
                        color = Color.White, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    val caps = app.playback.stream?.captions ?: emptyList()
                    if (caps.isNotEmpty()) IconButton(onClick = {
                        poke++
                        if (st.captions != null) {
                            player.setCaptions(null, null); app.store.settings.update { it.copy(captions = false) }
                        } else {
                            val lang = app.store.settings.value.lang.ifEmpty { "en" }
                            val c = app.playback.pickCaption(caps, lang)
                            player.setCaptions(c, lang.takeIf { c != null && c.lang.substringBefore('-') != it })
                            app.store.settings.update { it.copy(captions = true) }
                        }
                    }) {
                        Icon(if (st.captions != null) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff, tr("Captions"), tint = Color.White)
                    }
                    IconButton(onClick = { sheet = true }) { Icon(Icons.Filled.Settings, tr("Settings"), tint = Color.White) }
                }
                // centre: back 10s, play/pause, forward 10s (or next when it has ended)
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    CenterButton(Icons.Filled.Replay10, tr("Back 10 seconds"), 40) { seekBy(-10); poke++ }
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        when {
                            st.buffering && !st.ended -> CircularProgressIndicator(Modifier.size(48.dp), color = Color.White, strokeWidth = 3.dp)
                            st.ended -> CenterButton(Icons.Filled.Replay, tr("Replay"), 56) { player.seekTo(0); player.play() }
                            else -> CenterButton(if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, tr(if (st.playing) "Pause" else "Play"), 56) {
                                if (st.playing) player.pause() else player.play(); poke++
                            }
                        }
                    }
                    if (st.ended && app.playback.next != null) CenterButton(Icons.Filled.SkipNext, tr("Next"), 40) { app.playback.skipNext() }
                    else CenterButton(Icons.Filled.Forward10, tr("Forward 10 seconds"), 40) { seekBy(10); poke++ }
                }
                // bottom: time, full screen, seek bar
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .let { if (fullscreen) it.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp) else it },
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val pos = dragging?.let { (it * st.durationMs).toLong() } ?: st.positionMs
                        Text(
                            if (app.playback.video?.live == true) "● " + tr("LIVE") else "${durationMs(pos)} / ${durationMs(st.durationMs)}",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        if (st.speed != 1f) Text("${st.speed}x", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                        IconButton(onClick = { app.setFull(!fullscreen) }) {
                            Icon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, tr("Full screen"), tint = Color.White)
                        }
                    }
                    SeekBar(st, dragging, { dragging = it; poke++ }) { f ->
                        player.seekTo((f * st.durationMs).toLong()); dragging = null
                    }
                }
            }
        }
        // a thin progress line while the controls are hidden (not in full screen)
        if (!shown && !fullscreen && st.durationMs > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxWidth((st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f)).height(2.dp).background(Red))
            }
        }
    }
    if (sheet) SettingsSheet(app) { sheet = false }
}

@Composable
private fun CenterButton(icon: ImageVector, label: String, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).background(Color(0x44000000), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size((size * 0.62f).dp)) }
}

@Composable
private fun SeekBar(st: PlayerState, dragging: Float?, onDrag: (Float) -> Unit, onDone: (Float) -> Unit) {
    if (st.durationMs <= 0) { Spacer(Modifier.height(24.dp)); return }
    val value = dragging ?: (st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        // what is already buffered, under the slider's track
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(3.dp).background(Color(0x44FFFFFF))) {
            Box(Modifier.fillMaxWidth((st.bufferedMs.toFloat() / st.durationMs).coerceIn(0f, 1f)).height(3.dp).background(Color(0x88FFFFFF)))
        }
        Slider(
            value = value,
            onValueChange = onDrag,
            onValueChangeFinished = { onDone(dragging ?: value) },
            colors = SliderDefaults.colors(
                thumbColor = Red, activeTrackColor = Red,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
    }
}

/** Quality, speed and captions, as a sheet from the bottom. */
@Composable
private fun SettingsSheet(app: AppState, dismiss: () -> Unit) {
    val player = app.platform.player
    val st by player.state.collectAsState()
    var page by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            when (page) {
                "" -> {
                    SheetRow(Icons.Outlined.HighQuality, tr("Quality"), if (st.height > 0) "${st.height}p" else tr("Auto")) { page = "q" }
                    SheetRow(Icons.Outlined.Speed, tr("Speed"), if (st.speed == 1f) tr("Normal") else "${st.speed}x") { page = "s" }
                    if (!app.playback.stream?.captions.isNullOrEmpty()) {
                        SheetRow(Icons.Filled.ClosedCaption, tr("Captions"), st.captions?.name ?: tr("Off")) { page = "c" }
                    }
                }
                "q" -> {
                    Pick(tr("Auto"), st.height == 0) { player.setHeight(0); dismiss() }
                    for (h in st.heights.sortedDescending()) Pick("${h}p", st.height == h) { player.setHeight(h); dismiss() }
                }
                "s" -> for (x in listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) {
                    Pick(if (x == 1f) tr("Normal") else "${x}x", st.speed == x) {
                        player.setSpeed(x); app.store.settings.update { it.copy(speed = x) }; dismiss()
                    }
                }
                "c" -> {
                    val caps = app.playback.stream?.captions ?: emptyList()
                    val lang = app.store.settings.value.lang.ifEmpty { "en" }
                    Pick(tr("Off"), st.captions == null) {
                        player.setCaptions(null, null); app.store.settings.update { it.copy(captions = false) }; dismiss()
                    }
                    for (c in caps) Pick(c.name, st.captions == c) {
                        player.setCaptions(c, null); app.store.settings.update { it.copy(captions = true) }; dismiss()
                    }
                    if (caps.none { it.lang.substringBefore('-') == lang }) caps.firstOrNull()?.let { c ->
                        Pick(tr("Auto-translate") + " (" + (LANGS.firstOrNull { it.first == lang }?.second ?: lang) + ")", false) {
                            player.setCaptions(c, lang); app.store.settings.update { it.copy(captions = true) }; dismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Text(label, Modifier.weight(1f).padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pick(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp)) { if (on) Icon(Icons.Filled.Check, null) }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
