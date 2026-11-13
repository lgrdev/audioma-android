package app.audioma

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { PlayerScreen(vm) }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(vm: PlayerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::onFolderPicked)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.folderName ?: "Audioma", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.tracks.isNotEmpty()) {
                            Text(
                                "${state.tracks.size} pistes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.folderName != null) {
                        IconButton(onClick = vm::rescan) {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Relire le dossier")
                        }
                    }
                    IconButton(onClick = { pickFolder.launch(null) }) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = "Choisir le dossier")
                    }
                },
            )
        },
        bottomBar = {
            if (state.tracks.isNotEmpty()) PlayerBar(state, vm)
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.scanning -> CircularProgressIndicator()
                state.error != null -> Message(state.error!!, "Choisir un autre dossier") { pickFolder.launch(null) }
                state.folderName == null -> Message("Aucun dossier choisi", "Choisir un dossier") { pickFolder.launch(null) }
                state.tracks.isEmpty() -> Message("Aucun fichier audio dans ce dossier", "Choisir un autre dossier") { pickFolder.launch(null) }
                else -> TrackList(state, onClick = vm::playAt)
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun TrackList(state: UiState, onClick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    // Amène la piste en cours à l'écran quand elle change et n'est pas visible.
    LaunchedEffect(state.currentIndex) {
        val i = state.currentIndex
        if (i < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == i }) listState.scrollToItem((i - 2).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
            val isCurrent = index == state.currentIndex
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick(index) }
                    .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
                if (track.folder.isNotEmpty()) {
                    Text(
                        track.folder,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(state: UiState, vm: PlayerViewModel) {
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // Rafraîchit la position affichée.
    LaunchedEffect(state.isPlaying, state.currentIndex) {
        while (true) {
            position = vm.positionMs()
            if (!state.isPlaying) break
            delay(500)
        }
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                state.current?.title ?: "—",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val duration = state.durationMs.coerceAtLeast(1L)
            Slider(
                value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    val target = (dragValue * duration).toLong()
                    vm.seekTo(target)
                    position = target
                    dragging = false
                },
                enabled = state.durationMs > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val shown = if (dragging) (dragValue * duration).toLong() else position
                Text(formatTime(shown), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = vm::toggleShuffle,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (state.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_shuffle),
                        contentDescription = if (state.shuffle) "Aléatoire activé" else "Aléatoire désactivé",
                    )
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Précédent")
                }
                FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Suivant")
                }
                val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                IconButton(
                    onClick = vm::cycleRepeat,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (repeatOn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Icon(
                        painterResource(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                        ),
                        contentDescription = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Relecture du morceau"
                            Player.REPEAT_MODE_ALL -> "Relecture de la playlist"
                            else -> "Pas de relecture"
                        },
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
