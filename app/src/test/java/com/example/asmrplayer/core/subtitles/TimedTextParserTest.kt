package com.example.asmrplayer.core.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedTextParserTest {

    // region LRC

    @Test
    fun `lrc lines carry their own start and leave the end to the next line`() {
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            """
            [00:01.00]第一句
            [00:12.50]第二句
            [01:02.25]第三句
            """.trimIndent(),
        )

        assertEquals(listOf(1_000L, 12_500L, 62_250L), document.lines.map { it.startMillis })
        assertEquals(listOf("第一句", "第二句", "第三句"), document.lines.map { it.text })
        // Null means "until the next line", which is what every LRC line means and is
        // not the same as a line that ends immediately.
        assertTrue(document.lines.all { it.endMillis == null })
        assertTrue(document.isTimed)
    }

    @Test
    fun `one lrc line with several time tags shows at each of them`() {
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            "[00:10.00][01:20.00]副歌\n",
        )

        assertEquals(listOf(10_000L, 80_000L), document.lines.map { it.startMillis })
        assertEquals(listOf("副歌", "副歌"), document.lines.map { it.text })
    }

    @Test
    fun `the offset tag shifts every time in the file`() {
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            """
            [offset:-500]
            [00:01.00]晚半秒
            [00:02.00]也是
            """.trimIndent(),
        )

        assertEquals(listOf(500L, 1_500L), document.lines.map { it.startMillis })
    }

    @Test
    fun `metadata tags do not become lyrics`() {
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            """
            [ti:曲名]
            [ar:声优]
            [al:专辑]
            [00:01.00]正文
            """.trimIndent(),
        )

        assertEquals(listOf("正文"), document.lines.map { it.text })
    }

    @Test
    fun `a line with no time is folded into the lyric it belongs to`() {
        // The format wraps a long line by continuing it without a time tag, so
        // dropping these lines would drop half of what the listener is reading.
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            """
            [00:01.00]前半句
            后半句
            再接一句
            [00:05.00]下一句
            """.trimIndent(),
        )

        assertEquals(listOf("前半句 后半句 再接一句", "下一句"), document.lines.map { it.text })
        assertEquals(listOf(1_000L, 5_000L), document.lines.map { it.startMillis })
    }

    @Test
    fun `a bare time tag is a pause and does not become an empty lyric line`() {
        // Real files mark a pause with a bare tag -- `[00:28.34]` and nothing after
        // it. Taken as a lyric line each one is an empty row with nothing to read and
        // nothing to seek to, and the files in a real library carry enough of them
        // that the list fills with blanks.
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            """
            [00:03.00]第一句
            [00:28.34]
            [00:30.00]第二句
            """.trimIndent(),
        )

        assertEquals(listOf("第一句", "第二句"), document.lines.map { it.text })
        // The line before the pause stays current through it, so the gap is still
        // represented in time even though it has no row of its own.
        assertEquals(listOf(3_000L, 30_000L), document.lines.map { it.startMillis })
        assertEquals(0, document.lineIndexAt(15_000))
    }

    @Test
    fun `an lrc with no times at all is untimed text and says so`() {
        val document = parseTimedText(TimedTextFormat.Lrc, "第一行\n第二行\n")

        assertFalse(document.isTimed)
        assertTrue(document.lines.isEmpty())
        assertEquals(listOf("第一行", "第二行"), document.untimedText)
    }

    @Test
    fun `an enhanced lrc keeps word times inside the line`() {
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            "[00:01.00]<00:01.00>你<00:01.50>好<00:02.00>呀\n",
        )

        val line = document.lines.single()
        assertEquals(1_000L, line.startMillis)
        assertEquals("你 好 呀", line.text)
        assertEquals(listOf(1_000L, 1_500L, 2_000L), line.words.map { it.startMillis })
        // Each word runs until the next one starts; the last one has no stated end.
        assertEquals(listOf(1_500L, 2_000L, null), line.words.map { it.endMillis })
        assertEquals(listOf("你", "好", "呀"), line.words.map { it.text })
    }

    @Test
    fun `minutes may run past an hour and fractions are a fraction of a second`() {
        val document = parseTimedText(
            TimedTextFormat.Lrc,
            """
            [100:00.00]很久以后
            [00:01.5]一秒半
            [00:02.500]两秒半
            """.trimIndent(),
        )

        // `.5` is half a second, not five hundredths, and 100 minutes is 100 minutes
        // rather than wrapping to 40.
        assertEquals(listOf(1_500L, 2_500L, 6_000_000L), document.lines.map { it.startMillis })
    }

    // endregion

    // region SRT and VTT

    @Test
    fun `srt blocks become lines with an explicit end`() {
        val document = parseTimedText(
            TimedTextFormat.Srt,
            """
            1
            00:00:01,000 --> 00:00:04,500
            第一句

            2
            00:00:05,000 --> 00:00:08,000
            第二句
            """.trimIndent(),
        )

        assertEquals(listOf("第一句", "第二句"), document.lines.map { it.text })
        assertEquals(listOf(1_000L, 5_000L), document.lines.map { it.startMillis })
        assertEquals(listOf(4_500L, 8_000L), document.lines.map { it.endMillis })
    }

    @Test
    fun `multi-line cue text is joined and markup is removed`() {
        val document = parseTimedText(
            TimedTextFormat.Srt,
            """
            1
            00:00:01,000 --> 00:00:04,000
            <i>斜体</i>
            {\an8}第二行 &amp; 还有
            """.trimIndent(),
        )

        assertEquals("斜体 第二行 & 还有", document.lines.single().text)
    }

    @Test
    fun `a vtt file skips its header and its non-cue blocks`() {
        val document = parseTimedText(
            TimedTextFormat.Vtt,
            """
            WEBVTT - Some title
            Kind: captions

            NOTE this is a comment
            spanning two lines

            00:01.000 --> 00:04.000 align:start position:10%
            第一句

            cue-2
            00:05.000 --> 00:08.000
            第二句
            """.trimIndent(),
        )

        assertEquals(listOf("第一句", "第二句"), document.lines.map { it.text })
        // An hour-less VTT timestamp is minutes and seconds, not hours and minutes.
        assertEquals(listOf(1_000L, 5_000L), document.lines.map { it.startMillis })
    }

    @Test
    fun `a broken cue is skipped without losing the rest of the file`() {
        val document = parseTimedText(
            TimedTextFormat.Srt,
            """
            1
            00:00:01,000 --> 00:00:04,000
            保留

            2
            not a timestamp at all
            丢掉

            3
            00:00:09,000 --> 00:00:10,000
            也保留
            """.trimIndent(),
        )

        assertEquals(listOf("保留", "也保留"), document.lines.map { it.text })
    }

    @Test
    fun `cues out of order are sorted so a lookup can be a binary search`() {
        val document = parseTimedText(
            TimedTextFormat.Srt,
            """
            1
            00:00:09,000 --> 00:00:10,000
            后面

            2
            00:00:02,000 --> 00:00:03,000
            前面
            """.trimIndent(),
        )

        assertEquals(listOf(2_000L, 9_000L), document.lines.map { it.startMillis })
        assertEquals(listOf("前面", "后面"), document.lines.map { it.text })
    }

    @Test
    fun `a byte order mark does not end up in the first line`() {
        val lrc = parseTimedText(TimedTextFormat.Lrc, "\uFEFF[00:01.00]第一句\n")
        assertEquals("第一句", lrc.lines.single().text)

        val vtt = parseTimedText(TimedTextFormat.Vtt, "\uFEFFWEBVTT\n\n00:01.000 --> 00:02.000\n第一句\n")
        assertEquals("第一句", vtt.lines.single().text)
    }

    @Test
    fun `carriage returns do not survive into the text`() {
        val document = parseTimedText(
            TimedTextFormat.Srt,
            "1\r\n00:00:01,000 --> 00:00:04,000\r\n第一句\r\n",
        )

        assertEquals("第一句", document.lines.single().text)
    }

    @Test
    fun `an empty or unparseable file yields nothing rather than an error`() {
        for (source in listOf("", "   \n\n  \n", "just some words\nand more\n")) {
            val document = parseTimedText(TimedTextFormat.Srt, source)
            assertFalse(source, document.isTimed)
        }
        assertNull(parseTimedText(TimedTextFormat.Lrc, "").lines.firstOrNull())
    }

    // endregion
}
