package com.example.asmrplayer.core.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.example.asmrplayer.R
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.feature.subtitles.LyricGlowTimeline
import com.example.asmrplayer.feature.subtitles.LyricsWordGlowMode
import com.example.asmrplayer.feature.subtitles.lyricGlowTimeline

/** Bounded native overlay canvas: no Activity ViewTree owner and no off-screen Compose clock. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic service window only; never inflated from XML.
internal class DesktopLyricsView(context: Context, private val style: DesktopLyricsSettings,
    private val onClose: () -> Unit, private val onLock: () -> Unit,
    private val onDrag: (Int, Int, Boolean) -> Unit) : View(context) {
    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
            this@DesktopLyricsView.style.fontSizeSp.toFloat(), resources.displayMetrics)
        typeface = if (this@DesktopLyricsView.style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setShadowLayer(2 * dp, 0f, dp, Color.BLACK)
    }
    private data class Row(val layout: StaticLayout, val active: Boolean, val timeline: LyricGlowTimeline?)
    private var rows = emptyList<Row>()
    private var lastDocument: TimedTextDocument? = null
    private var lastIndex = Int.MIN_VALUE
    private var lastWidth = 0
    private var position = 0L
    private var rawX = 0f
    private var rawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    init { contentDescription = context.getString(R.string.desktop_lyrics) }

    fun update(document: TimedTextDocument, positionMs: Long) {
        val moved = position != positionMs
        position = positionMs
        val active = document.lineIndexAt(positionMs)
        if (document !== lastDocument || active != lastIndex || width != lastWidth) {
            lastDocument = document; lastIndex = active; lastWidth = width
            rows = desktopLyricIndices(document, positionMs, style.lineCount).map { index ->
                val line = document.lines[index]
                val layout = StaticLayout.Builder.obtain(line.text, 0, line.text.length, textPaint,
                    (width - 24 * dp).toInt().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
                    .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
                Row(layout, index == active, if (style.wordByWord && index == active)
                    line.lyricGlowTimeline(document.format, document.lines.getOrNull(index + 1)?.startMillis,
                        LyricsWordGlowMode.Smart) else null)
            }
            invalidate()
        } else if (style.wordByWord && moved) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = Color.argb(style.backgroundPercent * 255 / 100, 15, 19, 27)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 14 * dp, 14 * dp, paint)
        val header = if (style.locked) 0f else 38 * dp
        if (!style.locked) {
            paint.color = Color.WHITE
            paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                12f, resources.displayMetrics)
            canvas.drawText(context.getString(R.string.desktop_drag_hint), 12 * dp, 24 * dp, paint)
            canvas.drawText(context.getString(R.string.desktop_lock_short), width - 90 * dp, 24 * dp, paint)
            canvas.drawText("×", width - 27 * dp, 25 * dp, paint)
        }
        var y = header + ((height - header - rows.sumOf { it.layout.height } - (rows.size - 1).coerceAtLeast(0) * 8 * dp) / 2).coerceAtLeast(4 * dp)
        rows.forEach { row ->
            canvas.withTranslation(12 * dp, y) {
            textPaint.color = style.textArgb
            textPaint.alpha = if (row.active && row.timeline == null) 255 else 140
            if (row.active && row.timeline == null) textPaint.color = style.highlightArgb
            row.layout.draw(canvas)
            row.timeline?.let { timeline ->
                val front = timeline.wipeFrontAt(position)
                textPaint.color = style.highlightArgb; textPaint.alpha = 255
                for (line in 0 until row.layout.lineCount) {
                    val start = row.layout.getLineStart(line)
                    val end = row.layout.getLineEnd(line)
                    val offset = front.fromOffset.coerceIn(start, end)
                    val next = front.toOffset.coerceIn(start, end)
                    if (front.fromOffset < start && front.toOffset < start) continue
                    fun horizontal(at: Int): Float = when {
                        at >= end -> row.layout.getLineRight(line)
                        at <= start -> row.layout.getLineLeft(line)
                        else -> row.layout.getPrimaryHorizontal(at)
                    }
                    val x = horizontal(offset) + (horizontal(next) - horizontal(offset)) * front.fraction
                    canvas.withClip(0f, row.layout.getLineTop(line).toFloat(),
                        if (front.fromOffset >= end) row.layout.width.toFloat() else x,
                        row.layout.getLineBottom(line).toFloat()) { row.layout.draw(this) }
                }
            }
            }
            y += row.layout.height + 8 * dp
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (style.locked) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rawX = event.rawX; rawY = event.rawY; downX = rawX; downY = rawY; dragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.rawX - downX) + kotlin.math.abs(event.rawY - downY) > slop) dragged = true
                if (dragged) onDrag((event.rawX - rawX).toInt(), (event.rawY - rawY).toInt(), false)
                rawX = event.rawX; rawY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                if (dragged) onDrag(0, 0, true) else {
                    performClick()
                    if (event.y < 38 * dp && event.x > width - 45 * dp) onClose()
                    else if (event.y < 38 * dp && event.x > width - 105 * dp) onLock()
                }
            }
            MotionEvent.ACTION_CANCEL -> if (dragged) onDrag(0, 0, true)
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
