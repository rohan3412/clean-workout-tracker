package clean.app.android

import clean.app.R
import clean.app.RestTone

/*
 * The raw sound files, which stay in the app's own resources: the shared screens never touch them, only the phone's sound pool and
 * the rest notification channel do. (The font is a Compose resource in `shared`.)
 */

/** The sound file of a rest tone. */
internal val RestTone.raw: Int
    get() = when (this) {
        RestTone.Deep -> R.raw.bell_deep
        RestTone.Bright -> R.raw.bell
        RestTone.Gong -> R.raw.gong
        RestTone.Soft -> R.raw.tone_soft
        RestTone.Wood -> R.raw.wood_block
    }

/** The sound a new record makes. */
internal val CHIME_SOUND: Int = R.raw.chime
