package app.audioma

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Égaliseur fourni par le téléphone (ou par une appli d'égaliseur installée).
 * Audioma n'a pas d'écran d'égaliseur à lui : il ouvre celui du système.
 */
object SystemEqualizer {

    fun isAvailable(context: Context): Boolean =
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .resolveActivity(context.packageManager) != null

    /** À lancer avec startActivityForResult, comme le demande la documentation d'AudioEffect. */
    fun panelIntent(context: Context, audioSessionId: Int): Intent =
        withSession(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), context, audioSessionId)

    /** Prévient les égaliseurs qu'une session de musique s'ouvre ou se ferme, pour qu'ils s'y appliquent. */
    fun announce(context: Context, audioSessionId: Int, open: Boolean) {
        val action = if (open) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        context.sendBroadcast(withSession(Intent(action), context, audioSessionId))
    }

    private fun withSession(intent: Intent, context: Context, audioSessionId: Int) = intent
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
}
