package clean.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * The app's own sounds and buzzes. The sounds are played at the media volume, so they follow it and come
 * through headphones; they are Clean's own, made by `tools/bell/make_bell.py`.
 */
/** A sound a rest can end with, chosen in Settings; [key] is what is stored (kv `rest.tone`), and the first is used until one is picked. */
enum class RestTone(val key: String, val label: String, val raw: Int) {
    Deep("deep", "Deep bell", R.raw.bell_deep),
    Gong("gong", "Gong", R.raw.gong),
    Soft("soft", "Soft tone", R.raw.tone_soft),
    Wood("wood", "Wood block", R.raw.wood_block),
    Bright("bright", "Bright bell", R.raw.bell),
    ;

    companion object {
        fun of(key: String?): RestTone = entries.firstOrNull { it.key == key } ?: Deep
    }
}

object Feedback {
    private var pool: SoundPool? = null
    private var tones = emptyMap<RestTone, Int>()
    private var chime = 0
    private var context: Context? = null

    /** Loads the sounds once; asked for again it does nothing. */
    @Synchronized
    fun init(app: Context) {
        if (pool != null) return
        context = app.applicationContext
        pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
            .also { p -> tones = RestTone.entries.associateWith { p.load(app, it.raw, 1) }; chime = p.load(app, R.raw.chime, 1) }
    }

    /** The end of a rest, in the sound chosen. */
    fun bell(tone: RestTone) { tones[tone]?.let { pool?.play(it, 1f, 1f, 1, 0, 1f) } }

    /** Two soft rising notes: a new PR. */
    fun chime() { pool?.play(chime, 0.8f, 0.8f, 1, 0, 1f) }

    /** One vibration of [millis]. */
    fun pulse(millis: Long) = vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))

    /** Two quick taps. */
    fun doubleTick() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40), -1))

    private fun vibrate(effect: VibrationEffect) {
        runCatching { context?.getSystemService(Vibrator::class.java)?.vibrate(effect) }
    }
}
