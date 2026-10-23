package app.tubehaus.android

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import app.tubehaus.data.Store
import app.tubehaus.ui.AppState
import app.tubehaus.ui.Platform
import app.tubehaus.ui.VideoPlayer
import java.io.File

class TubeApp : Application() {
    lateinit var platform: AndroidPlatform
        private set
    val state: AppState by lazy { AppState(platform, Store(platform.dataDir)) }

    override fun onCreate() {
        super.onCreate()
        platform = AndroidPlatform(this)
    }
}

val Context.tube: TubeApp get() = applicationContext as TubeApp

class AndroidPlatform(private val app: Application) : Platform {
    override val name = "android"
    override val dataDir: File = File(app.filesDir, "tubehaus")
    override val cacheDir: File = app.cacheDir
    override val touch = true
    val androidPlayer by lazy { AndroidPlayer(app) }
    override val player: VideoPlayer get() = androidPlayer

    /** set by the activity: runs file pickers and full screen, which need an Activity */
    var activity: MainActivity? = null

    override fun share(title: String, url: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, url)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { app.startActivity(chooser); true } catch (e: ActivityNotFoundException) { false }
    }

    override fun openExternal(url: String) {
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {}
    }

    override fun saveFile(name: String, mime: String, bytes: ByteArray, done: (Boolean) -> Unit) {
        activity?.saveFile(name, mime, bytes, done) ?: done(false)
    }

    override fun openTextFile(done: (String?) -> Unit) {
        activity?.openTextFile(done) ?: done(null)
    }

    override fun setFullscreen(on: Boolean) {
        activity?.setFullscreen(on)
    }

    override fun metered(): Boolean =
        (app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
}
