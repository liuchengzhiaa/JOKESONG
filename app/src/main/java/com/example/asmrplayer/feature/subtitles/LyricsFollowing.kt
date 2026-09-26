package com.example.asmrplayer.feature.subtitles

/**
 * Where the lyric list scrolls to keep the current line readable.
 *
 * The current line is not put at the very top of the viewport: a reader wants the
 * line they are hearing to be at eye level with what comes next already visible, not
 * pressed against the edge with the whole screen spent on what has already been sung.
 * So the list anchors a few lines earlier and the current line lands below the top.
 *
 * The clamping is the part worth having in one place. The anchor is an index into a
 * list that can be shorter than the lead-in -- a six-line file opened at its third
 * line would otherwise ask to scroll to a negative index -- and a scroll request past
 * either end is an exception from `LazyListState`, not a no-op.
 */
object LyricsFollowing {
    /** Lines of context kept above the current one. */
    const val LeadInLines: Int = 3

    /**
     * The item index to scroll to so that [centreIndex] sits [LeadInLines] lines down
     * from the top, clamped to the list.
     *
     * Returns 0 for an empty list so a caller can scroll unconditionally.
     */
    fun anchorIndex(centreIndex: Int, lineCount: Int): Int {
        if (lineCount <= 0) return 0
        return (centreIndex - LeadInLines).coerceIn(0, lineCount - 1)
    }

    /**
     * True when the list should be moved to follow a line change.
     *
     * A negative centre is "playback has not reached the first line yet", which is a
     * real state at the start of every track and not something to scroll for.
     */
    fun shouldFollow(centreIndex: Int, isFollowing: Boolean): Boolean =
        isFollowing && centreIndex >= 0
}
