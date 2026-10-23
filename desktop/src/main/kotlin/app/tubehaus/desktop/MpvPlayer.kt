package app.tubehaus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import app.tubehaus.engine.Caption
import app.tubehaus.engine.Format
import app.tubehaus.engine.Stream
import app.tubehaus.engine.YouTube
import app.tubehaus.ui.PlayOptions
import app.tubehaus.ui.PlayerState
import app.tubehaus.ui.VideoPlayer
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.awt.Canvas
import java.awt.Color as AwtColor
import kotlin.concurrent.thread

/**
 * mpv (libmpv) drawing into a window inside the app. mpv plays YouTube's separate video and
 * audio streams together (the audio as an external track), decodes with the GPU, and brings
 * its own on-screen controller with a seek bar, so the desktop gets a first-class player.
 *
 * YouTube gives one URL per quality, so changing quality reloads at the same position.
 */
class MpvPlayer : VideoPlayer {
    private val st = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = st

    private val lib = LibMpv.load()
    private var h: Pointer? = null
    private val lock = Any()

    private var stream: Stream? = null
    private var opts = PlayOptions()
    private var pinned = 0
    private var caption: Caption? = null
    private var translateTo: String? = null
    private var showControls = true

    /** the one surface mpv draws on; re-parented as the screens change */
    private val canvas = object : Canvas() {
        override fun addNotify() {
            super.addNotify()
            attach(Native.getComponentID(this))
        }

        override fun removeNotify() {
            detach()
            super.removeNotify()
        }
    }.apply { background = AwtColor.BLACK }

    val available get() = lib != null

    private fun mpv(): Pointer? {
        synchronized(lock) {
            h?.let { return it }
            val l = lib ?: return null
            val p = l.mpv_create() ?: return null
            fun o(k: String, v: String) = l.mpv_set_option_string(p, k, v)
            o("vo", "gpu-next"); o("hwdec", "auto-safe")
            o("osc", "yes"); o("input-default-bindings", "yes"); o("input-vo-keyboard", "yes")
            o("keep-open", "no"); o("idle", "yes"); o("force-window", "yes")
            o("ytdl", "no"); o("cache", "yes"); o("demuxer-max-bytes", "150MiB"); o("demuxer-readahead-secs", "60")
            o("sub-auto", "no"); o("audio-display", "no"); o("terminal", "no"); o("msg-level", "all=warn")
            o("vid", "no")              // no video window until the app gives one
            if (l.mpv_initialize(p) < 0) { l.mpv_terminate_destroy(p); return null }
            h = p
            thread(isDaemon = true, name = "mpv-events") { events(l, p) }
            thread(isDaemon = true, name = "mpv-state") { poll(l, p) }
            return p
        }
    }

    private fun attach(wid: Long) {
        val p = mpv() ?: return
        lib!!.mpv_set_property_string(p, "wid", wid.toString())
        lib.mpv_set_property_string(p, "vid", "auto")
    }

    private fun detach() {
        val p = h ?: return
        lib!!.mpv_set_property_string(p, "vid", "no")
    }

    private fun cmd(vararg a: String) {
        val p = mpv() ?: return
        lib!!.mpv_command(p, arrayOf(*a, null))
    }

    private fun set(k: String, v: String) {
        val p = mpv() ?: return
        lib!!.mpv_set_property_string(p, k, v)
    }

    private fun events(l: LibMpv, p: Pointer) {
        while (true) {
            val e = l.mpv_wait_event(p, 1.0)
            when (e.getInt(0)) {
                LibMpv.EVENT_SHUTDOWN -> return
                LibMpv.EVENT_END_FILE -> {
                    val data = e.getPointer(16)
                    val reason = data?.getInt(0) ?: -1
                    if (reason == LibMpv.END_FILE_REASON_EOF && st.value.videoId != null) st.update { it.copy(ended = true, playing = false) }
                    if (reason == LibMpv.END_FILE_REASON_ERROR) st.update { it.copy(error = "Playback failed") }
                }
                LibMpv.EVENT_FILE_LOADED -> {
                    st.update { it.copy(ended = false, error = null) }
                    caption?.let { c -> cmd("sub-add", YouTube.captionUrl(c, translateTo), "select", c.name) }
                }
            }
        }
    }

    private fun num(l: LibMpv, p: Pointer, name: String): Double? {
        val r = DoubleByReference()
        return if (l.mpv_get_property(p, name, LibMpv.FORMAT_DOUBLE, r) >= 0) r.value else null
    }

    private fun str(l: LibMpv, p: Pointer, name: String): String? {
        val r = l.mpv_get_property_string(p, name) ?: return null
        return try { r.getString(0, "UTF-8") } finally { l.mpv_free(r) }
    }

    private fun poll(l: LibMpv, p: Pointer) {
        while (true) {
            Thread.sleep(250)
            val s = stream ?: continue
            val pos = num(l, p, "time-pos")
            val dur = num(l, p, "duration")
            val paused = str(l, p, "pause") == "yes"
            val idle = str(l, p, "idle-active") == "yes"
            val cache = str(l, p, "paused-for-cache") == "yes"
            val speed = num(l, p, "speed")
            val cached = num(l, p, "demuxer-cache-time")
            st.update {
                it.copy(
                    videoId = s.id,
                    playing = !paused && !idle && !it.ended,
                    buffering = cache,
                    positionMs = pos?.let { x -> (x * 1000).toLong() } ?: it.positionMs,
                    durationMs = dur?.let { x -> (x * 1000).toLong() } ?: (s.lengthSec * 1000L),
                    speed = speed?.toFloat() ?: it.speed,
                    bufferedMs = cached?.let { x -> (x * 1000).toLong() } ?: 0,
                )
            }
        }
    }

    // ------------------------------------------------------------------ choosing streams
    /** Windows GPUs decode VP9 almost everywhere; AV1 only on recent ones, H.264 everywhere. */
    private fun pickVideo(s: Stream, cap: Int): Format? {
        val ok = s.video.filter { it.height <= cap }.ifEmpty { s.video.sortedBy { it.height }.take(1) }
        val best = ok.maxOfOrNull { it.height } ?: return null
        val at = ok.filter { it.height == best }
        return at.firstOrNull { it.codecFamily == "vp9" } ?: at.firstOrNull { it.codecFamily == "h264" } ?: at.first()
    }

    private fun pickAudio(s: Stream): Format? {
        val main = s.audio.filter { it.audioDefault }.ifEmpty { s.audio }
        return main.filter { it.codecFamily == "opus" }.maxByOrNull { it.bitrate } ?: main.maxByOrNull { it.bitrate }
    }

    private fun open(startMs: Long) {
        val s = stream ?: return
        if (mpv() == null) {
            st.update { it.copy(error = "mpv is missing: put libmpv-2.dll next to TubeHaus (see the README)") }
            return
        }
        val heights = s.video.map { it.height }.distinct().sorted()
        st.update { it.copy(heights = heights, height = pinned) }
        val url: String
        cmd("change-list", "audio-files", "clr", "")
        if (s.live || s.video.isEmpty()) {
            url = s.hls ?: return
        } else {
            val cap = if (pinned > 0) pinned else opts.maxHeight
            val v = pickVideo(s, cap) ?: return
            url = v.url
            pickAudio(s)?.let { a -> cmd("change-list", "audio-files", "append", a.url) }
        }
        set("start", if (startMs > 0) "+%.1f".format(java.util.Locale.ROOT, startMs / 1000.0) else "none")
        set("loop-file", if (opts.loop) "inf" else "no")
        set("speed", opts.speed.toString())
        set("pause", if (opts.autoplay) "no" else "yes")
        set("force-media-title", opts.title.ifEmpty { s.title })
        cmd("loadfile", url, "replace")
        controls(showControls)
    }

    // ------------------------------------------------------------------ VideoPlayer
    override fun load(s: Stream, o: PlayOptions) {
        stream = s
        opts = o
        pinned = 0
        caption = o.captions
        translateTo = if (o.captions != null && o.captionLang != null && o.captions.lang.substringBefore('-') != o.captionLang) o.captionLang else null
        st.value = PlayerState(videoId = s.id, buffering = true, speed = o.speed, captions = caption)
        open(o.startMs)
    }

    override fun play() {
        if (st.value.ended) { st.update { it.copy(ended = false) }; open(0); return }
        set("pause", "no")
    }

    override fun pause() = set("pause", "yes")
    override fun seekTo(ms: Long) = cmd("seek", "%.1f".format(java.util.Locale.ROOT, ms / 1000.0), "absolute")
    override fun setSpeed(x: Float) { opts = opts.copy(speed = x); set("speed", x.toString()) }

    override fun setHeight(h: Int) {
        pinned = h
        open(st.value.positionMs)
    }

    override fun setCaptions(c: Caption?, translateTo: String?) {
        caption = c
        this.translateTo = translateTo
        st.update { it.copy(captions = c) }
        cmd("sub-remove")
        if (c != null) cmd("sub-add", YouTube.captionUrl(c, translateTo), "select", c.name)
    }

    override fun setLoop(on: Boolean) { opts = opts.copy(loop = on); set("loop-file", if (on) "inf" else "no") }

    override fun stop() {
        stream = null
        cmd("stop")
        st.value = PlayerState()
    }

    private fun controls(on: Boolean) {
        showControls = on
        cmd("script-message", "osc-visibility", if (on) "auto" else "never", "no_osd")
    }

    @Composable
    override fun View(modifier: Modifier, controls: Boolean) {
        if (!available) {
            Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                Text("mpv is missing: put libmpv-2.dll next to TubeHaus", color = Color.White)
            }
            return
        }
        if (controls != showControls) controls(controls)
        SwingPanel(background = Color.Black, factory = { canvas }, modifier = modifier)
    }

    fun shutdown() {
        val p = h ?: return
        h = null
        lib?.mpv_terminate_destroy(p)
    }
}
