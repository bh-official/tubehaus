package app.tubehaus.android

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Puts the app's one player in a media session: notification, lock screen, headset buttons, background play. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) })
        session = MediaSession.Builder(this, tube.platform.androidPlayer.exo).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // swiped away from recents while paused: nothing left to do
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            p?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run { release() }   // the player itself belongs to the app and stays
        session = null
        super.onDestroy()
    }
}
