package app.tubehaus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.tubehaus.engine.Caption
import app.tubehaus.engine.Stream
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** What each platform (Android, Windows) supplies; everything else is shared. */
interface Platform {
    val name: String               // "android" / "desktop"
    val dataDir: File
    val cacheDir: File
    /** phones get the bottom bar and portrait layouts; desktops a wider grid */
    val touch: Boolean
    val player: VideoPlayer

    /** a share sheet where there is one; false when the link was copied to the clipboard instead */
    fun share(title: String, url: String): Boolean
    fun openExternal(url: String)
    /** asks where to save [bytes]; calls [done] with true when written */
    fun saveFile(name: String, mime: String, bytes: ByteArray, done: (Boolean) -> Unit)
    /** lets the user pick a file; calls [done] with its text (null when cancelled) */
    fun openTextFile(done: (String?) -> Unit)
    fun setFullscreen(on: Boolean)
    /** true on a metered connection (mobile data): the lower quality cap applies */
    fun metered(): Boolean = false
    /** the device language, e.g. "ko" */
    fun deviceLanguage(): String = java.util.Locale.getDefault().language
    fun deviceRegion(): String = java.util.Locale.getDefault().country.ifEmpty { "US" }
}

data class PlayerState(
    val videoId: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** how far ahead is already downloaded */
    val bufferedMs: Long = 0,
    val ended: Boolean = false,
    val error: String? = null,
    /** heights the current video offers */
    val heights: List<Int> = emptyList(),
    /** chosen height, 0 = automatic */
    val height: Int = 0,
    val speed: Float = 1f,
    val captions: Caption? = null,
)

data class PlayOptions(
    val startMs: Long = 0,
    val maxHeight: Int = 1080,
    val autoplay: Boolean = true,
    val speed: Float = 1f,
    val captions: Caption? = null,
    val captionLang: String? = null,
    val loop: Boolean = false,
    /** for the system media controls */
    val title: String = "",
    val artist: String = "",
    val artwork: String = "",
)

interface VideoPlayer {
    val state: StateFlow<PlayerState>
    fun load(s: Stream, o: PlayOptions)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setSpeed(x: Float)
    /** 0 = automatic */
    fun setHeight(h: Int)
    fun setCaptions(c: Caption?, translateTo: String?)
    fun setLoop(on: Boolean)
    fun stop()

    /** The video picture. [controls]: the platform's own play/seek controls on top of it. */
    @Composable
    fun View(modifier: Modifier, controls: Boolean)
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("no platform") }
