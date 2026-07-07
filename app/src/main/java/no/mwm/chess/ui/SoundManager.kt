package no.mwm.chess.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import no.mwm.chess.R

enum class SoundEvent { MOVE, CAPTURE, CASTLE, PROMOTE, CHECK, GAMEOVER }

/** Loads the short SFX into a [SoundPool] and plays them by event. */
class SoundManager(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<SoundEvent, Int> = mapOf(
        SoundEvent.MOVE to pool.load(context, R.raw.snd_move, 1),
        SoundEvent.CAPTURE to pool.load(context, R.raw.snd_capture, 1),
        SoundEvent.CASTLE to pool.load(context, R.raw.snd_castle, 1),
        SoundEvent.PROMOTE to pool.load(context, R.raw.snd_promote, 1),
        SoundEvent.CHECK to pool.load(context, R.raw.snd_check, 1),
        SoundEvent.GAMEOVER to pool.load(context, R.raw.snd_gameover, 1),
    )

    fun play(event: SoundEvent) {
        val id = ids[event] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
