package com.example.asmrplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asmrplayer.core.subtitles.TimedTextDocument

/**
 * The player page's own lyric green.
 *
 * Not a theme colour and not derived from the cover: it is a fixed signal colour that
 * has to stay readable and unmistakable over a background that is now whatever the
 * cover happens to be. A cover-derived green would vanish into a green cover, which
 * is exactly the case it would be least able to afford.
 */
internal val PlayerFloatingLyric = Color(0xFF4CE86A)

/**
 * The two lines the player page shows under the cover: what is being sung, and what
 * comes next.
 *
 * Both are nullable rather than empty strings, because "there is no line" and "the
 * line is blank" are different: the first means the block should not be drawn at
 * all, and a blank LRC line is a real pause in the song that should hold its place.
 */
internal data class LyricPair(val current: String?, val next: String?, val previous: String? = null) {
    val isEmpty: Boolean get() = previous == null && current == null && next == null

    companion object {
        val Empty = LyricPair(current = null, next = null)
    }
}

/**
 * The pair to draw for [document] at [positionMs].
 *
 * Before the first line starts, [TimedTextDocument.lineIndexAt] answers -1 and there
 * is no current line -- but there is still something worth showing, which is the line
 * about to start. So the upcoming line is taken from `currentIndex + 1` and the pair
 * is shifted back by one in that case rather than the block going blank for the whole
 * intro of every song.
 *
 * A null position means playback is not on this track, and a null document means this
 * track has no timed lyrics. Both produce [LyricPair.Empty], and neither invents a
 * line: highlighting words against a clock that is not running would be a lie the
 * next track inherits.
 */
internal fun lyricPairAt(document: TimedTextDocument?, positionMs: Long?): LyricPair {
    if (document == null || positionMs == null || document.lines.isEmpty()) return LyricPair.Empty
    val index = document.lineIndexAt(positionMs)
    return if (index < 0) {
        LyricPair(current = null, next = document.lines.first().text)
    } else {
        LyricPair(
            previous = document.lines.getOrNull(index - 1)?.text,
            current = document.lines.getOrNull(index)?.text,
            next = document.lines.getOrNull(index + 1)?.text,
        )
    }
}

/**
 * The current line, in the reference's green, across the top of the player.
 *
 * Drawn with a hard shadow rather than a scrim behind it: the backdrop is a cover
 * colour now, so any fixed plate would only be right for some covers. A shadow
 * follows the glyphs and is right for all of them.
 *
 * An absent line renders nothing at all -- no empty band, no placeholder -- so a song
 * without lyrics looks like a song without lyrics rather than like a screen that
 * failed to load something.
 */
@Composable
internal fun FloatingLyricBanner(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        color = PlayerFloatingLyric,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 1.5f),
                blurRadius = 5f,
            ),
        ),
    )
}

/**
 * The pair as it sits under the cover: the current line in full white, the next one
 * dimmed. The dimming is the whole affordance -- there is no marker, no highlight
 * box, no colour change -- because the two lines are read as "now" and "then" and
 * anything more would compete with the cover above them.
 */
@Composable
internal fun PlayerLyricPairBlock(pair: LyricPair, modifier: Modifier = Modifier) {
    if (pair.isEmpty) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp),
    ) {
        pair.current?.let { line ->
            Text(
                text = line,
                color = PlayerPrimaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        pair.next?.let { line ->
            Text(
                text = line,
                color = PlayerSecondaryText,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
