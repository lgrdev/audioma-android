package app.audioma

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Palette « Nocturne » : fond encre, un seul accent ambre. */
object Nocturne {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF151B26)
    val SurfaceHigh = Color(0xFF1A2130)
    val Selected = Color(0xFF161E2B)
    val Track = Color(0xFF2A3344)
    val Text = Color(0xFFEEF1F6)
    val TextSoft = Color(0xFFC9D1DC)
    val Muted = Color(0xFF9AA4B2)
    val Inactive = Color(0xFF8792A2)
    val Accent = Color(0xFFF5B544)
    val OnAccent = Color(0xFF1A1205)
    val AccentTint = Color(0xFF2A2416)
}

/** Couleur de vignette d'un sous-dossier : fond + pictogramme. */
@Immutable
data class FolderTint(val background: Color, val glyph: Color)

private val folderTints = listOf(
    FolderTint(Color(0xFF24406B), Color(0xFFB9CCEB)),
    FolderTint(Color(0xFF4A3A78), Color(0xFFD3C8F2)),
    FolderTint(Color(0xFF245A55), Color(0xFFB7E0DA)),
    FolderTint(Color(0xFF6B3A4A), Color(0xFFEEC4D0)),
    FolderTint(Color(0xFF5A4A24), Color(0xFFEBDDB4)),
    FolderTint(Color(0xFF2E4F2F), Color(0xFFC3E3BF)),
)

/** Même dossier → même couleur, d'un lancement à l'autre. */
fun folderTint(folder: String): FolderTint =
    folderTints[Math.floorMod(folder.hashCode(), folderTints.size)]

private val colors = darkColorScheme(
    primary = Nocturne.Accent,
    onPrimary = Nocturne.OnAccent,
    secondaryContainer = Nocturne.Selected,
    onSecondaryContainer = Nocturne.Text,
    background = Nocturne.Background,
    onBackground = Nocturne.Text,
    surface = Nocturne.Background,
    onSurface = Nocturne.Text,
    surfaceVariant = Nocturne.Surface,
    onSurfaceVariant = Nocturne.Muted,
    surfaceContainer = Nocturne.Surface,
    surfaceContainerHigh = Nocturne.SurfaceHigh,
    outline = Nocturne.Track,
)

private val typography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.02).em),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
        labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.08.em),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
