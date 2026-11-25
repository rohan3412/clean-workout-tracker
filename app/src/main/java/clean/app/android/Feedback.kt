package clean.app.android

import clean.app.*

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants

/**
 * The app's own sounds and buzzes. The sounds are played at the media volume, so they follow it and come
 * through headphones; they are Clean's own, made by `tools/bell/make_bell.py`.
 */
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
            .also { p -> tones = RestTone.entries.associateWith { p.load(app, it.raw, 1) }; chime = p.load(app, CHIME_SOUND, 1) }
    }

    /** The end of a rest, in the sound chosen. */
    fun bell(tone: RestTone) { tones[tone]?.let { pool?.play(it, 1f, 1f, 1, 0, 1f) } }

    /** Two soft rising notes: a new PR. */
    fun chime(volume: Float = 0.8f) { pool?.play(chime, volume, volume, 1, 0, 1f) }


    /** One vibration of [millis]. */
    fun pulse(millis: Long) = vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))


    private fun vibrate(effect: VibrationEffect) {
        runCatching { context?.getSystemService(Vibrator::class.java)?.vibrate(effect) }
    }
}

/** The phone's own effect for each buzz of the app's vocabulary ([Haptic]), `HapticFeedbackConstants` with a fall-back for older phones. */
object AndroidHaptics {
    fun constantFor(kind: Haptic, sdk: Int): Int = when (kind) {
        Haptic.Tick -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else if (sdk >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK
        Haptic.Detent -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        Haptic.Confirm -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Heavy -> HapticFeedbackConstants.LONG_PRESS
        Haptic.Reject -> if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
}
