@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package clean.app.ios

import clean.app.RestTone
import clean.app.generated.resources.Res
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * The app's own sounds and buzzes on the iPhone. The sounds are the Android app's files, played with `AVAudioPlayer` on the ambient
 * audio session, so they mix with music and follow the silent switch. The buzzes are the Taptic Engine's: a tick for a tick, impacts of
 * different weight for the pulses (UIKit has no vibration of a chosen length).
 */
object IosFeedback {
    private var players = emptyMap<String, AVAudioPlayer>()
    private var prepared = false

    /** The file name the rest notification and the player use for a tone; the notification's copy is in Library/Sounds. */
    fun soundFile(tone: RestTone): String = "clean_${resource(tone)}.wav"

    private fun resource(tone: RestTone) = when (tone) {
        RestTone.Deep -> "bell_deep"
        RestTone.Bright -> "bell"
        RestTone.Gong -> "gong"
        RestTone.Soft -> "tone_soft"
        RestTone.Wood -> "wood_block"
    }

    private val ALL = RestTone.entries.map { resource(it) } + "chime"

    /** Loads the sounds once, and puts a copy of each where a notification can name it (`Library/Sounds`). */
    suspend fun prepare() {
        if (prepared) return
        prepared = true
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, null)
            session.setActive(true, null)
        }
        val library = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true).first() as String
        val loaded = HashMap<String, AVAudioPlayer>()
        for (name in ALL) {
            val bytes = runCatching { Res.readBytes("files/$name.wav") }.getOrNull() ?: continue
            val copy = Fs.join(library, "Sounds", "clean_$name.wav")
            if (!Fs.exists(copy)) Fs.write(copy, bytes)
            AVAudioPlayer(data = bytes.toNSData(), error = null)?.let { it.prepareToPlay(); loaded[name] = it }
        }
        players = loaded
    }

    private fun play(name: String, volume: Float) = onMain {
        players[name]?.let { it.volume = volume; it.currentTime = 0.0; it.play() }
    }

    fun bell(tone: RestTone) = play(resource(tone), 1f)
    fun chime(volume: Float) = play("chime", volume)


    /** One buzz: a light, medium or heavy tap by how long Android's would run. */
    fun pulse(millis: Long) = onMain {
        val style = when {
            millis < 60 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            millis < 200 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            else -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        if (millis >= 400) UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        else UIImpactFeedbackGenerator(style).impactOccurred()
    }

    /** The Taptic Engine's effect for each buzz of the app's vocabulary: selection and light impact for the ticks, success, heavy impact and error for the rest. */
    fun haptic(kind: clean.app.Haptic) = onMain {
        when (kind) {
            clean.app.Haptic.Tick -> UISelectionFeedbackGenerator().selectionChanged()
            clean.app.Haptic.Detent -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
            clean.app.Haptic.Confirm -> UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            clean.app.Haptic.Heavy -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()
            clean.app.Haptic.Reject -> UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
        }
    }
}
