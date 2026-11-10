package app.audioma

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/** Réglages persistants : dossier choisi, mode aléatoire, dernière piste et position. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("audioma", Context.MODE_PRIVATE)

    var folderUri: Uri?
        get() = sp.getString(KEY_FOLDER, null)?.let(Uri::parse)
        set(value) = sp.edit { putString(KEY_FOLDER, value?.toString()) }

    var shuffle: Boolean
        get() = sp.getBoolean(KEY_SHUFFLE, false)
        set(value) = sp.edit { putBoolean(KEY_SHUFFLE, value) }

    val lastMediaId: String? get() = sp.getString(KEY_LAST_ID, null)
    val lastPositionMs: Long get() = sp.getLong(KEY_LAST_POS, 0L)

    fun saveProgress(mediaId: String?, positionMs: Long) = sp.edit {
        putString(KEY_LAST_ID, mediaId)
        putLong(KEY_LAST_POS, positionMs)
    }

    private companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_LAST_ID = "last_id"
        const val KEY_LAST_POS = "last_pos"
    }
}
