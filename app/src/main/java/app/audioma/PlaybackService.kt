package app.audioma

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.random.Random

/**
 * Service de lecture : garde la musique en arrière-plan, expose une MediaSession
 * (notification, écran de verrouillage, boutons Bluetooth) et sauvegarde la progression.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var previousIndex = C.INDEX_UNSET

    private val saveTicker = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause si on débranche le casque
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.repeatMode = prefs.repeatMode // aucune, morceau ou playlist en boucle
        player.addListener(listener)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Appli balayée des récents : on ne garde le service que si la musique joue.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            saveProgress()
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveTicker)
        saveProgress()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun saveProgress() {
        val item = player.currentMediaItem ?: return
        prefs.saveProgress(item.mediaId, player.currentPosition)
    }

    /**
     * Nouveau mélange aléatoire qui commence par la piste en cours :
     * toutes les autres pistes passent une fois avant que la boucle ne recommence.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun reshuffle() {
        val count = player.mediaItemCount
        if (count < 2) return
        val current = player.currentMediaItemIndex
        val order = intArrayOf(current) + (0 until count).filter { it != current }.shuffled().toIntArray()
        player.setShuffleOrder(DefaultShuffleOrder(order, Random.nextLong()))
    }

    private fun currentIsFirstInShuffle(): Boolean =
        player.currentTimeline.getFirstWindowIndex(true) == player.currentMediaItemIndex

    private val listener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            prefs.shuffle = shuffleModeEnabled
            if (shuffleModeEnabled) reshuffle()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            prefs.repeatMode = repeatMode
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Nouvelle liste chargée en mode aléatoire : mélange à partir de la piste courante.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED &&
                player.shuffleModeEnabled && !timeline.isEmpty && !currentIsFirstInShuffle()
            ) reshuffle()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val timeline = player.currentTimeline
            // Fin de liste atteinte en aléatoire (playlist en boucle) : on remélange pour le tour suivant.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                player.shuffleModeEnabled && !timeline.isEmpty &&
                previousIndex == timeline.getLastWindowIndex(true)
            ) reshuffle()
            previousIndex = player.currentMediaItemIndex
            mediaItem?.let { prefs.saveProgress(it.mediaId, 0L) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(saveTicker)
            if (isPlaying) handler.postDelayed(saveTicker, 5_000) else saveProgress()
        }
    }

    private object SessionCallback : MediaSession.Callback {
        // Les MediaItem envoyés par un contrôleur perdent leur URI : on la reconstruit depuis le mediaId.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                if (item.localConfiguration != null) item
                else item.buildUpon().setUri(Uri.parse(item.mediaId)).build()
            }.toMutableList()
        )
    }
}
