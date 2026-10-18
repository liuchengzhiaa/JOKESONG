package com.example.asmrplayer.core.media

import androidx.media3.common.Player

/**
 * How the queue repeats, in the app's own terms.
 *
 * Media3 already has an int for this, and the temptation is to pass it around. It
 * is not passed around: `Player.REPEAT_MODE_*` is a wire value whose numbering is
 * someone else's to change, and every surface that has to *draw* the mode -- the
 * bottom row picks a different glyph for each -- would then be reading it. The enum
 * is the app's vocabulary; [toPlayerMode] and [ofPlayerMode] are the only two places
 * the translation happens, and they are inverses.
 *
 * [Off] is first so it is the default and so an unknown value degrades to "no
 * repeat" rather than to a mode nobody chose.
 */
enum class RepeatMode {
    Off,
    All,
    One,
    ;

    /** The mode a tap on the repeat control moves to. */
    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]

    fun toPlayerMode(): Int = when (this) {
        Off -> Player.REPEAT_MODE_OFF
        All -> Player.REPEAT_MODE_ALL
        One -> Player.REPEAT_MODE_ONE
    }

    companion object {
        /**
         * Anything unrecognised reads as [Off]. The alternative -- throwing, or
         * picking a mode by position -- would make a future Media3 addition show up
         * as a repeat mode the owner never asked for.
         */
        fun ofPlayerMode(playerMode: Int): RepeatMode = when (playerMode) {
            Player.REPEAT_MODE_ALL -> All
            Player.REPEAT_MODE_ONE -> One
            else -> Off
        }
    }
}
