package app.tubehaus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tubehaus.engine.Stream
import app.tubehaus.engine.Video
import app.tubehaus.engine.YouTube
import app.tubehaus.engine.YtError
import app.tubehaus.rec.Learn
import kotlinx.coroutines.launch

/**
 * Shorts, one after another. The Shorts engine learns from every one: swiping away early,
 * watching to the end, watching it loop, liking - and picks the next ones from your
 * subscriptions, channels and words you like, and a share of popular ones to find new things.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(app: AppState) {
    val list = remember { mutableStateListOf<Video>() }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val player = app.platform.player
    suspend fun more() {
        if (loading) return
        loading = true
        try {
            val got = app.rec.shorts(10, list.map { it.id }.toSet())
            list.addAll(got.filter { g -> list.none { it.id == g.id } })
            error = if (list.isEmpty()) tr("No Shorts found") else null
        } catch (e: Exception) {
            if (list.isEmpty()) error = e.message
        } finally { loading = false }
    }
    LaunchedEffect(Unit) { more() }
    val pager = rememberPagerState { list.size }
    val streams = remember { HashMap<String, Stream>() }
    var current by remember { mutableStateOf<Video?>(null) }
    var watchStart by remember { mutableStateOf(0L) }

    fun learnFrom(v: Video) {
        val s = streams[v.id] ?: return
        val st = player.state.value
        if (st.videoId != v.id) return
        // time actually spent on it, which counts the loops
        val spent = (System.currentTimeMillis() - watchStart) / 1000.0
        Learn.learn(
            app.store, v.copy(channelId = s.channelId, author = s.author), s.keywords, spent, s.lengthSec.toDouble().coerceAtLeast(1.0),
            liked = app.store.inList("likes", v.id), disliked = app.store.inList("dislikes", v.id), short = true,
        )
    }

    LaunchedEffect(pager, list.size) {
        snapshotFlow { pager.currentPage }.collect { page ->
            val v = list.getOrNull(page) ?: return@collect
            if (current?.id == v.id) return@collect
            current?.let { learnFrom(it) }
            current = v
            if (page >= list.size - 3) scope.launch { more() }
            try {
                val s = streams[v.id] ?: YouTube.stream(v.id).also { streams[v.id] = it }
                if (current?.id != v.id) return@collect
                watchStart = System.currentTimeMillis()
                player.load(s, PlayOptions(maxHeight = 720, loop = true, title = s.title, artist = s.author, artwork = v.thumb))
                // fetch the next one ahead so the swipe starts at once
                list.getOrNull(page + 1)?.let { n -> if (n.id !in streams) scope.launch { runCatching { streams[n.id] = YouTube.stream(n.id) } } }
            } catch (e: Exception) {
                app.toast(if ((e as? YtError)?.kind == YtError.Kind.LOGIN) tr(BOT_CHECK) else e.message ?: tr("This video can't be played"))
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            current?.let { learnFrom(it) }
            player.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            list.isEmpty() && error != null -> Problem(error!!) { scope.launch { more() } }
            list.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else -> VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0, key = { list[it].id }) { page ->
                val v = list[page]
                ShortPage(app, v, streams[v.id], active = current?.id == v.id)
            }
        }
    }
}

@Composable
private fun ShortPage(app: AppState, v: Video, s: Stream?, active: Boolean) {
    val player = app.platform.player
    val st by player.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    if (st.playing) player.pause() else player.play()
                },
        ) {
            Img(v.thumb, Modifier.fillMaxSize())
            if (active && st.videoId == v.id) player.View(Modifier.fillMaxSize(), controls = false)
            if (active && st.videoId == v.id && !st.playing && !st.buffering) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(72.dp).align(Alignment.Center))
            }
            // the bottom: title, channel; the right: like, dislike, share
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x55000000)).padding(12.dp)) {
                val author = s?.author ?: v.author
                val cid = s?.channelId ?: v.channelId
                if (author.isNotEmpty()) Text(
                    "@$author", color = Color.White, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { if (cid.isNotEmpty()) app.go(Screen.Channel(cid, author)) },
                )
                Text(s?.title ?: v.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (st.videoId == v.id && st.durationMs > 0) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp).background(Color(0x66FFFFFF))) {
                        Box(Modifier.fillMaxWidth((st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f)).height(2.dp).background(Red))
                    }
                }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val full = s?.asVideo() ?: v
                val liked = app.store.lib.state.collectAsState().value.likes.any { it.id == v.id }
                val disliked = app.store.lib.state.collectAsState().value.dislikes.contains(v.id)
                RoundAction(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, tr("Like")) { app.store.setInList("likes", full, !liked) }
                RoundAction(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, tr("Dislike")) { app.store.setInList("dislikes", full, !disliked) }
                RoundAction(Icons.Outlined.Share, tr("Share")) { app.share(v.title, "https://youtube.com/shorts/${v.id}") }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row {
        IconButton(onClick = onClick, modifier = Modifier.background(Color(0x55000000), androidx.compose.foundation.shape.CircleShape)) {
            Icon(icon, label, tint = Color.White)
        }
    }
}
