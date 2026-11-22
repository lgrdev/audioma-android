package app.audioma

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.Normalizer

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Thème toujours sombre : icônes claires dans les barres système.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AppTheme { AppRoot(vm) }
        }
    }
}

@Composable
private fun AppRoot(vm: PlayerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::onFolderPicked)
    }
    val position = rememberPlaybackPosition(state, vm)
    var playerRequested by rememberSaveable { mutableStateOf(false) }
    val playerOpen = playerRequested && state.current != null

    // Égaliseur du téléphone, s'il en propose un.
    val context = LocalContext.current
    val hasEqualizer = remember { SystemEqualizer.isAvailable(context) }
    val equalizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    Box(
        Modifier
            .fillMaxSize()
            .background(Nocturne.Background)
    ) {
        if (state.folderName == null && !state.scanning) {
            Onboarding(onPickFolder = { pickFolder.launch(null) })
        } else {
            Library(
                state = state,
                position = position.longValue,
                vm = vm,
                onPickFolder = { pickFolder.launch(null) },
                onOpenPlayer = { playerRequested = true },
            )
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            NowPlaying(
                state = state,
                position = position.longValue,
                folderName = state.folderName,
                vm = vm,
                onSeek = { target ->
                    vm.seekTo(target)
                    position.longValue = target
                },
                onClose = { playerRequested = false },
                onEqualizer = if (hasEqualizer) {
                    {
                        val intent = SystemEqualizer.panelIntent(context, PlaybackService.audioSessionId)
                        try { equalizer.launch(intent) } catch (_: ActivityNotFoundException) {}
                    }
                } else null,
            )
        }
    }
}

/** Position de lecture, rafraîchie deux fois par seconde pendant la lecture. */
@Composable
private fun rememberPlaybackPosition(state: UiState, vm: PlayerViewModel): MutableLongState {
    val position = remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isPlaying, state.currentIndex) {
        while (true) {
            position.longValue = vm.positionMs()
            if (!state.isPlaying) break
            delay(500)
        }
    }
    return position
}

// ---------------------------------------------------------------------------
// Bibliothèque
// ---------------------------------------------------------------------------

@Composable
private fun Library(
    state: UiState,
    position: Long,
    vm: PlayerViewModel,
    onPickFolder: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val closeSearch = { searching = false; query = "" }
    BackHandler(enabled = searching, onBack = closeSearch)

    // Pistes affichées : toutes, ou celles qui contiennent chaque mot cherché (titre ou sous-dossier).
    val keys = remember(state.tracks) { state.tracks.map { searchKey("${it.title} ${it.folder}") } }
    val shown = remember(state.tracks, keys, query) {
        val words = searchKey(query).split(' ').filter(String::isNotEmpty)
        state.tracks.withIndex().filter { (i, _) -> words.all { it in keys[i] } }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            if (searching) SearchBar(query, onQueryChange = { query = it }, onClose = closeSearch)
            else LibraryHeader(state, vm, onPickFolder, onSearch = { searching = true })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.scanning -> CircularProgressIndicator(color = Nocturne.Accent)
                    state.error != null -> Message(state.error, "Choisir un autre dossier", onPickFolder)
                    state.tracks.isEmpty() -> Message("Aucun fichier audio dans ce dossier", "Choisir un autre dossier", onPickFolder)
                    shown.isEmpty() -> Text(
                        "Aucun morceau ne correspond à « ${query.trim()} »",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Nocturne.Muted,
                        modifier = Modifier.padding(24.dp),
                    )
                    else -> TrackList(state, shown, filtered = query.isNotBlank(), onClick = vm::playAt)
                }
            }
        }
        if (state.current != null && !state.scanning) {
            MiniPlayer(
                state = state,
                position = position,
                onOpen = onOpenPlayer,
                onTogglePlay = vm::togglePlay,
                onNext = { vm.next() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun BrandLabel(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = Nocturne.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("AUDIOMA", style = MaterialTheme.typography.labelMedium, color = Nocturne.Muted)
    }
}

@Composable
private fun LibraryHeader(state: UiState, vm: PlayerViewModel, onPickFolder: () -> Unit, onSearch: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandLabel(Modifier.weight(1f))
            if (state.tracks.isNotEmpty()) {
                IconButton(onClick = onSearch) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "Rechercher", tint = Nocturne.TextSoft)
                }
            }
            IconButton(onClick = vm::rescan) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Relire le dossier", tint = Nocturne.TextSoft)
            }
            FilledIconButton(
                onClick = onPickFolder,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Nocturne.Surface,
                    contentColor = Nocturne.TextSoft,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_folder), contentDescription = "Choisir le dossier")
            }
        }
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                state.folderName ?: "Audioma",
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.tracks.isNotEmpty()) {
                Text(librarySummary(state.tracks), style = MaterialTheme.typography.bodyMedium, color = Nocturne.Muted)
            }
        }
        if (state.tracks.isNotEmpty()) {
            ShuffleChip(on = state.shuffle, onToggle = vm::toggleShuffle)
        }
    }
}

/** Minuscules sans accents : « electro » trouve « Électro ». */
private fun searchKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Fermer la recherche", tint = Nocturne.TextSoft)
        }
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(Nocturne.Surface)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Nocturne.Muted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Rechercher un morceau", fontSize = 16.sp, color = Nocturne.Muted)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = Nocturne.Text),
                    cursorBrush = SolidColor(Nocturne.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = "Rechercher un morceau" },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Effacer", tint = Nocturne.TextSoft)
                }
            }
        }
    }
}

private fun librarySummary(tracks: List<Track>): String {
    val n = tracks.size
    val folders = tracks.distinctBy { it.folder }.size
    val pistes = if (n > 1) "$n pistes" else "$n piste"
    return if (folders > 1) "$pistes · $folders dossiers" else pistes
}

@Composable
private fun ShuffleChip(on: Boolean, onToggle: () -> Unit) {
    Surface(
        checked = on,
        onCheckedChange = { onToggle() },
        shape = CircleShape,
        color = if (on) Nocturne.Accent else Color.Transparent,
        contentColor = if (on) Nocturne.OnAccent else Nocturne.TextSoft,
        border = if (on) null else BorderStroke(1.dp, Nocturne.Track),
    ) {
        Row(
            Modifier
                .height(40.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Aléatoire", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TrackList(
    state: UiState,
    /** Pistes à afficher, avec leur position dans la liste complète. */
    items: List<IndexedValue<Track>>,
    filtered: Boolean,
    onClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    // Nouvelle recherche : on repart du haut des résultats.
    LaunchedEffect(items) { if (filtered) listState.scrollToItem(0) }
    // Amène la piste en cours à l'écran quand elle change et n'est pas visible (liste complète seulement).
    LaunchedEffect(state.currentIndex, filtered) {
        val i = state.currentIndex
        if (i < 0 || filtered) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == i }) listState.scrollToItem((i - 2).coerceAtLeast(0))
    }
    val grouped = remember(state.tracks) { state.tracks.distinctBy { it.folder }.size > 1 }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Laisse la dernière piste visible au-dessus du mini-lecteur.
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 120.dp),
    ) {
        itemsIndexed(items, key = { _, t -> t.value.id }) { pos, (index, track) ->
            Column {
                if (grouped && (pos == 0 || items[pos - 1].value.folder != track.folder)) {
                    Text(
                        track.folder.ifEmpty { state.folderName.orEmpty() }.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Nocturne.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
                    )
                }
                TrackRow(
                    track = track,
                    isCurrent = index == state.currentIndex,
                    isPlaying = state.isPlaying,
                    onClick = {
                        keyboard?.hide()
                        onClick(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Nocturne.Selected else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { selected = isCurrent }
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FolderTile(track.folder, size = 44.dp, corner = 10.dp) { glyph ->
            if (isCurrent) EqualizerBars(animated = isPlaying, color = glyph)
            else Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = glyph, modifier = Modifier.size(20.dp))
        }
        Text(
            track.title,
            fontSize = 15.sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isCurrent) Nocturne.Accent else Nocturne.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Vignette carrée colorée selon le sous-dossier ; [content] reçoit la couleur du pictogramme. */
@Composable
fun FolderTile(folder: String, size: Dp, corner: Dp, content: @Composable (Color) -> Unit) {
    val tint = folderTint(folder)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(tint.background),
        contentAlignment = Alignment.Center,
    ) { content(tint.glyph) }
}

@Composable
private fun EqualizerBars(animated: Boolean, color: Color) {
    Row(
        Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (animated) {
            val transition = rememberInfiniteTransition(label = "eq")
            listOf(0, 300, 600).forEach { offset ->
                val h by transition.animateFloat(
                    initialValue = 5f,
                    targetValue = 16f,
                    animationSpec = infiniteRepeatable(
                        tween(450, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                        initialStartOffset = StartOffset(offset),
                    ),
                    label = "bar",
                )
                Bar(h.dp, color)
            }
        } else {
            listOf(6.dp, 11.dp, 8.dp).forEach { Bar(it, color) }
        }
    }
}

@Composable
private fun Bar(height: Dp, color: Color) {
    Box(
        Modifier
            .width(3.dp)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun MiniPlayer(
    state: UiState,
    position: Long,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val progress = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Nocturne.SurfaceHigh,
        shadowElevation = 12.dp,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Nocturne.Track)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Nocturne.Accent)
                )
            }
            Row(
                Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Ouvrir le lecteur", onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FolderTile(track.folder, size = 44.dp, corner = 10.dp) { glyph ->
                        Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = glyph, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (track.folder.isNotEmpty()) {
                            Text(track.folder, fontSize = 13.sp, color = Nocturne.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                PlayPauseButton(isPlaying = state.isPlaying, onClick = onTogglePlay, size = 48.dp, iconSize = 24.dp)
                IconButton(onClick = onNext) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Suivant", tint = Nocturne.Text)
                }
            }
        }
    }
}

@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, size: Dp, iconSize: Dp) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Nocturne.Accent,
            contentColor = Nocturne.OnAccent,
        ),
    ) {
        Icon(
            painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (isPlaying) "Pause" else "Lecture",
            modifier = Modifier.size(iconSize),
        )
    }
}

// ---------------------------------------------------------------------------
// Premier lancement et messages
// ---------------------------------------------------------------------------

@Composable
private fun Onboarding(onPickFolder: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
    ) {
        BrandLabel()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                FolderBadge(size = 120.dp)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Tu choisis un dossier.\nAudioma joue ta musique.",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 36.sp),
                    )
                    Text(
                        "Les sous-dossiers sont inclus automatiquement. Tu peux changer de dossier à tout moment.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Nocturne.Muted,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Promise("Aucune publicité, aucun compte")
                    Promise("Rien ne quitte ton téléphone")
                    Promise("MP3, FLAC, OGG, Opus, M4A, WAV…")
                }
            }
        }
        AccentButton("Choisir un dossier", onPickFolder, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FolderBadge(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Nocturne.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(1.5.dp.toPx())
            listOf(0.28f, 0.40f).forEach { r ->
                drawCircle(Nocturne.Accent.copy(alpha = 0.18f), radius = this.size.minDimension * r, style = stroke)
            }
        }
        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = Nocturne.Accent, modifier = Modifier.size(size * 0.4f))
    }
}

@Composable
private fun Promise(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Nocturne.Accent)
        )
        Text(text, fontSize = 14.sp, color = Nocturne.TextSoft)
    }
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Nocturne.Accent, contentColor = Nocturne.OnAccent),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        FolderBadge(size = 88.dp)
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = Nocturne.TextSoft)
        AccentButton(action, onAction)
    }
}
