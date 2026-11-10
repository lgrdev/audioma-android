package app.audioma

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val uri: Uri,
    val title: String,
    /** Chemin du sous-dossier relatif au dossier choisi ("" à la racine). */
    val folder: String,
) {
    val id: String get() = uri.toString()

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(folder.ifEmpty { null })
                .build()
        )
        .build()
}

object TrackScanner {
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "mka", "amr", "3gp",
    )

    /** Parcourt récursivement le dossier SAF [treeUri] et renvoie les fichiers audio triés par chemin. */
    fun scan(context: Context, treeUri: Uri): List<Track> {
        val resolver = context.contentResolver
        val result = mutableListOf<Track>()
        val pending = ArrayDeque<Pair<String, String>>() // (documentId, cheminRelatif)
        pending.add(DocumentsContract.getTreeDocumentId(treeUri) to "")

        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
        )

        while (pending.isNotEmpty()) {
            val (docId, path) = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (name.startsWith(".")) continue
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending.add(id to if (path.isEmpty()) name else "$path/$name")
                    } else if (isAudio(name, mime)) {
                        result += Track(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            title = name.substringBeforeLast('.'),
                            folder = path,
                        )
                    }
                }
            }
        }
        return result.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { if (it.folder.isEmpty()) it.title else "${it.folder}/${it.title}" }
        )
    }

    private fun isAudio(name: String, mime: String): Boolean =
        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    /** Nom lisible du dossier choisi, ex. "primary:Music/Rock" -> "Rock". */
    fun folderName(treeUri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val path = docId.substringAfter(':')
        return path.substringAfterLast('/').ifEmpty { docId.substringBefore(':') }
    }
}
