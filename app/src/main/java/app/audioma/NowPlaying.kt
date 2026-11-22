package app.audioma

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.roundToInt

/** Écran « Lecture en cours » plein écran, ouvert depuis le mini-lecteur. */
@Composable
fun NowPlaying(
    state: UiState,
    position: Long,
    folderName: String?,
    vm: PlayerViewModel,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    /** Ouvre l'égaliseur du téléphone ; null s'il n'y en a pas. */
    onEqualizer: (() -> Unit)?,
) {
    val track = state.current ?: return
    BackHandler(onBack = onClose)
    // Glisser vers le bas ferme l'écran.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { dy -> dragOffset = (dragOffset + dy).coerceAtLeast(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .background(Nocturne.Background)
            .draggable(
                dragState,
                Orientation.Vertical,
                onDragStopped = {
                    if (dragOffset > 160f) onClose()
                    dragOffset = 0f
                },
            )
            .systemBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TopBar(folderName, track.folder, onClose, onEqualizer)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Cover(track.folder)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val where = "Piste ${state.currentIndex + 1} sur ${state.tracks.size}"
            Text(
                if (track.folder.isEmpty()) where else "${track.folder} · $where",
                fontSize = 15.sp,
                color = Nocturne.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SeekBar(state.durationMs, position, onSeek)
        Controls(state, vm)

        Text(
            modeLine(state),
            fontSize = 13.sp,
            color = Nocturne.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
        )

        state.next?.let { UpNext(it) }
    }
}

@Composable
private fun TopBar(folderName: String?, subfolder: String, onClose: () -> Unit, onEqualizer: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(R.drawable.ic_expand_more, "Fermer le lecteur", onClose)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("DOSSIER", style = MaterialTheme.typography.labelMedium, color = Nocturne.Muted)
            Text(
                listOfNotNull(folderName, subfolder.ifEmpty { null }).joinToString(" / "),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onEqualizer != null) RoundButton(R.drawable.ic_equalizer, "Égaliseur", onEqualizer)
        else Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun RoundButton(icon: Int, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Nocturne.Surface,
            contentColor = Nocturne.TextSoft,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

/** Grande vignette du sous-dossier, avec cercles concentriques discrets. */
@Composable
private fun Cover(folder: String) {
    val tint = folderTint(folder)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(tint.background)
            .drawBehind {
                val stroke = Stroke(1.5.dp.toPx())
                listOf(0.17f, 0.27f, 0.37f, 0.47f, 0.57f).forEach { r ->
                    drawCircle(tint.glyph.copy(alpha = 0.12f), radius = size.minDimension * r, style = stroke)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = tint.glyph,
            modifier = Modifier.fillMaxSize(0.26f),
        )
    }
}

@Composable
private fun SeekBar(durationMs: Long, position: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = durationMs.coerceAtLeast(1L)

    Column {
        Slider(
            value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                onSeek((dragValue * duration).toLong())
                dragging = false
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Nocturne.Text,
                activeTrackColor = Nocturne.Accent,
                inactiveTrackColor = Nocturne.Track,
            ),
            modifier = Modifier.semantics { contentDescription = "Position dans le morceau" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging) (dragValue * duration).toLong() else position
            Text(formatTime(shown), fontSize = 13.sp, color = Nocturne.Muted)
            Text(formatTime(durationMs), fontSize = 13.sp, color = Nocturne.Muted)
        }
    }
}

@Composable
private fun Controls(state: UiState, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(
            on = state.shuffle,
            icon = R.drawable.ic_shuffle,
            description = if (state.shuffle) "Aléatoire activé" else "Aléatoire désactivé",
            onClick = vm::toggleShuffle,
        )
        IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Précédent", tint = Nocturne.Text, modifier = Modifier.size(32.dp))
        }
        PlayPauseButton(isPlaying = state.isPlaying, onClick = vm::togglePlay, size = 80.dp, iconSize = 38.dp)
        IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Suivant", tint = Nocturne.Text, modifier = Modifier.size(32.dp))
        }
        ModeButton(
            on = state.repeatMode != Player.REPEAT_MODE_OFF,
            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
            description = repeatLabel(state.repeatMode),
            onClick = vm::cycleRepeat,
        )
    }
}

@Composable
private fun ModeButton(on: Boolean, icon: Int, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (on) Nocturne.AccentTint else Color.Transparent,
            contentColor = if (on) Nocturne.Accent else Nocturne.Inactive,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

@Composable
private fun UpNext(track: Track) {
    Surface(shape = RoundedCornerShape(18.dp), color = Nocturne.Surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FolderTile(track.folder, size = 40.dp, corner = 10.dp) { glyph ->
                Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = glyph, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("À SUIVRE", style = MaterialTheme.typography.labelMedium, color = Nocturne.Muted)
                Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun repeatLabel(mode: Int): String = when (mode) {
    Player.REPEAT_MODE_ONE -> "Relecture du morceau"
    Player.REPEAT_MODE_ALL -> "Relecture de la playlist"
    else -> "Pas de relecture"
}

private fun modeLine(state: UiState): String = buildList {
    if (state.shuffle) add("Aléatoire")
    if (state.repeatMode != Player.REPEAT_MODE_OFF) add(repeatLabel(state.repeatMode))
}.joinToString(" · ")

fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
