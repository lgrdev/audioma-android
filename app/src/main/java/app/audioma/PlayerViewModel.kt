package app.audioma

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val folderName: String? = null,
    val tracks: List<Track> = emptyList(),
    val scanning: Boolean = false,
    val error: String? = null,
    val currentIndex: Int = C.INDEX_UNSET,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val durationMs: Long = 0L,
) {
    val current: Track? get() = tracks.getOrNull(currentIndex)
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState(shuffle = prefs.shuffle))
    val state: StateFlow<UiState> = _state

    private var controller: MediaController? = null
    private var scanJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()?.also {
                it.addListener(listener)
                loadIntoPlayerIfNeeded()
                syncFromPlayer()
            }
        }, ContextCompat.getMainExecutor(app))

        // Recharge le dossier mémorisé si la permission est toujours accordée.
        val saved = prefs.folderUri
        if (saved != null && app.contentResolver.persistedUriPermissions.any { it.uri == saved && it.isReadPermission }) {
            scan(saved)
        } else if (saved != null) {
            prefs.folderUri = null
        }
    }

    fun onFolderPicked(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        // Libère l'ancien dossier, garde l'accès au nouveau après redémarrage.
        prefs.folderUri?.takeIf { it != uri }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.folderUri = uri
        scan(uri)
    }

    fun rescan() {
        prefs.folderUri?.let { scan(it) }
    }

    private fun scan(uri: Uri) {
        scanJob?.cancel()
        _state.update { it.copy(scanning = true, error = null, folderName = TrackScanner.folderName(uri)) }
        scanJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { TrackScanner.scan(getApplication(), uri) }
            }
            result.onSuccess { tracks ->
                _state.update { it.copy(tracks = tracks, scanning = false) }
                loadIntoPlayerIfNeeded()
            }.onFailure { e ->
                _state.update { it.copy(scanning = false, error = e.message ?: "Lecture du dossier impossible") }
            }
        }
    }

    /**
     * Envoie la liste au lecteur, sauf s'il contient déjà exactement les mêmes pistes
     * (service resté actif pendant que l'appli était fermée).
     */
    private fun loadIntoPlayerIfNeeded() {
        val c = controller ?: return
        val tracks = _state.value.tracks
        if (_state.value.scanning) return

        val sameList = c.mediaItemCount == tracks.size &&
            tracks.indices.all { c.getMediaItemAt(it).mediaId == tracks[it].id }
        if (sameList) return

        if (tracks.isEmpty()) {
            c.clearMediaItems()
            return
        }
        // Reprise : piste en cours (ou dernière mémorisée) si elle existe encore dans ce dossier.
        val wasPlaying = c.playWhenReady
        val (lastId, lastPos) = c.currentMediaItem?.let { it.mediaId to c.currentPosition }
            ?: (prefs.lastMediaId to prefs.lastPositionMs)
        val lastIndex = tracks.indexOfFirst { it.id == lastId }
        val startIndex = lastIndex.coerceAtLeast(0)
        val startPos = if (lastIndex >= 0) lastPos else 0L
        c.setMediaItems(tracks.map(Track::toMediaItem), startIndex, startPos)
        c.shuffleModeEnabled = prefs.shuffle
        c.prepare()
        c.playWhenReady = wasPlaying && lastIndex >= 0
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        _state.update {
            it.copy(
                currentIndex = if (c.mediaItemCount == it.tracks.size) c.currentMediaItemIndex else C.INDEX_UNSET,
                isPlaying = c.isPlaying,
                shuffle = c.shuffleModeEnabled,
                durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
            )
        }
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun playAt(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}
