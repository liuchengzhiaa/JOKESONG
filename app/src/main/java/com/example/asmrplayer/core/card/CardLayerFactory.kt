package com.example.asmrplayer.core.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the three layer images a card is made of, out of one cover.
 *
 * Everything here is deterministic and off the main thread, and every layer comes out
 * at exactly [CardLayerGeometry.CanvasWidth] x [CardLayerGeometry.CanvasHeight]. The
 * shared canvas is not a convention to be tidy about: the viewer composites the layers
 * with per-layer parallax, so a layer one pixel off is a layer that slides visibly
 * against the others.
 *
 * No AI image generation is involved. The cover is the artwork; this only takes it
 * apart into the planes the viewer needs.
 */
internal class CardLayerFactory(private val context: Context) {

    /** The three files just written, in the order the viewer loads them, and the preview. */
    data class Layers(
        val subjectFile: File,
        val backgroundFile: File,
        val textFile: File,
        val previewFile: File,
    )

    /**
     * Writes all three layers into [directory]'s `assets/`, or returns null if the
     * cover could not be read at all.
     *
     * Files are landed through a temporary name and then renamed, so a run that is
     * killed partway leaves no half-written PNG behind: the viewer loads the three
     * layers with one `Promise.all`, and a truncated image rejects the whole page --
     * a blank viewer is a much worse failure than a missing card.
     */
    suspend fun build(
        artworkUri: Uri?,
        fields: CardTextFields,
        directory: File,
    ): Layers? = withContext(Dispatchers.Default) {
        val assets = File(directory, CardRepository.AssetsDirectoryName).apply { mkdirs() }
        val cover = artworkUri?.let(::decodeCover) ?: return@withContext null
        val canvas = coverCanvas(cover)
        cover.recycle()
        try {
            val subject = File(assets, SubjectName)
            val background = File(assets, BackgroundName)
            val text = File(assets, TextName)
            val preview = File(directory, CardRepository.PreviewName)

            // All three are held at once so the preview can be struck from them before
            // `writePng` recycles each one out from under the next draw.
            val backgroundBitmap = backgroundLayer(canvas)
            val subjectBitmap = subjectLayer(canvas)
            val textBitmap = CardTypography.render(fields, cardTypeface())
            writePng(preview, previewOf(backgroundBitmap, subjectBitmap, textBitmap))

            writePng(background, backgroundBitmap)
            writePng(subject, subjectBitmap)
            writePng(text, textBitmap)

            Layers(
                subjectFile = subject,
                backgroundFile = background,
                textFile = text,
                previewFile = preview,
            )
        } finally {
            canvas.recycle()
        }
    }

    /**
     * Reads the cover at a bounded sample size.
     *
     * The bounds pass comes first for the same reason `CoverAccentLoader` does it: a
     * cover may be several thousand pixels on a side, and decoding it whole only to
     * draw it into a 1024-wide canvas costs tens of megabytes for no visible gain.
     */
    private fun decodeCover(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= CardLayerGeometry.CanvasWidth &&
            bounds.outHeight / (sample * 2) >= CardLayerGeometry.CanvasHeight
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
        }
    }.getOrNull()

    /**
     * The one crop every layer is taken from.
     *
     * A source smaller than a whole pixel of crop, or one whose crop rounds to an empty
     * rectangle, is copied whole rather than scaled: the alternative is a zero-width
     * `drawBitmap` that silently paints nothing and leaves a transparent card.
     */
    private fun coverCanvas(cover: Bitmap): Bitmap {
        val canvas = Bitmap.createBitmap(
            CardLayerGeometry.CanvasWidth,
            CardLayerGeometry.CanvasHeight,
            Bitmap.Config.ARGB_8888,
        )
        val crop = CardLayerGeometry.centreCrop(cover.width, cover.height)
        val source = Rect(
            crop.left.roundToInt(),
            crop.top.roundToInt(),
            crop.right.roundToInt(),
            crop.bottom.roundToInt(),
        ).takeIf { it.width() > 0 && it.height() > 0 && it.right <= cover.width && it.bottom <= cover.height }
            ?: Rect(0, 0, cover.width, cover.height)
        Canvas(canvas).drawBitmap(
            cover,
            source,
            Rect(0, 0, canvas.width, canvas.height),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true },
        )
        return canvas
    }

    /**
     * The subject plane: the cover's own pixels through the segmented alpha.
     *
     * Taking the pixels from the cover rather than from the segmentation result is
     * deliberate -- the model's own foreground bitmap is a re-composite, and the card
     * should show the artwork's colours, not the model's idea of them.
     *
     * A cover with no separable subject is not an edge case in this library: much of it
     * is a room, an object, or flat graphic art. The fallback is not a failure state --
     * the whole cover becomes the subject at full alpha, the card simply has a still
     * hero instead of a floating one, and the background is blurred either way so the
     * two planes still read as two.
     */
    private fun subjectLayer(canvas: Bitmap): Bitmap {
        val mask = segmentSubject(canvas) ?: return canvas.copy(Bitmap.Config.ARGB_8888, false) ?: canvas
        val out = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
        val cut = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        Canvas(out).apply {
            drawBitmap(canvas, 0f, 0f, null)
            drawBitmap(mask, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), cut)
        }
        mask.recycle()
        return out
    }

    /**
     * Runs the subject segmenter and returns an alpha mask, or null.
     *
     * Null has to cover every way this can decline to answer -- no Play Services, the
     * model not yet downloaded, no subject found, a task that fails or times out --
     * because the caller treats all of them identically. The *confidence* mask is used
     * rather than a hard threshold so hair and translucent edges keep their softness;
     * [softenAlpha] only pushes values that are already nearly transparent to zero,
     * which is what removes the faint wash a bare threshold leaves over the whole frame.
     */
    private fun segmentSubject(canvas: Bitmap): Bitmap? = runCatching {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val result = Tasks.await(
                segmenter.process(InputImage.fromBitmap(canvas, 0)),
                ProcessTimeoutSeconds,
                TimeUnit.SECONDS,
            )
            val confidence = result.foregroundConfidenceMask ?: return@runCatching null
            val (maskWidth, maskHeight) = SubjectMask.size(confidence, canvas.width, canvas.height)
                ?: return@runCatching null
            val pixels = SubjectMask.pixels(confidence, maskWidth, maskHeight)
            Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
            }
        } finally {
            segmenter.close()
        }
    }.getOrNull()

    /**
     * The background plane: the same cover, magnified, heavily blurred, and darkened
     * where the typography sits.
     *
     * Blurring is not decoration. The background and the subject sit on different depth
     * planes and slide against each other as the card tilts; a sharp copy of the cover
     * behind a cut-out of that same cover reads as a ghosted double image the moment
     * the two move apart. Removing the sharp features is what makes the layer read as
     * something *behind* the subject instead of as a second copy of it.
     *
     * The blur is a reduction and re-expansion rather than a render effect: same result
     * for a layer this size, no GPU needed, and it cannot vary between devices. It goes
     * down in two steps because a single bilinear 16x reduction undersamples and leaves
     * the aliasing of the original in the result.
     */
    private fun backgroundLayer(canvas: Bitmap): Bitmap {
        val destination = RectF(
            canvas.width * (1f - CardLayerGeometry.BackgroundOverscan) / 2f,
            canvas.height * (1f - CardLayerGeometry.BackgroundOverscan) / 2f,
            canvas.width * (1f + CardLayerGeometry.BackgroundOverscan) / 2f,
            canvas.height * (1f + CardLayerGeometry.BackgroundOverscan) / 2f,
        )
        val out = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
        val band = canvas.height * CardLayerGeometry.TypographyBandFraction
        Canvas(out).apply {
            val blurred = blurred(canvas)
            drawBitmap(blurred, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))
            blurred.recycle()

            drawRect(
                0f, 0f, width.toFloat(), band,
                Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, band,
                        TintTop, Color.TRANSPARENT,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            drawRect(
                0f, height - band, width.toFloat(), height.toFloat(),
                Paint().apply {
                    shader = LinearGradient(
                        0f, height - band, 0f, height.toFloat(),
                        Color.TRANSPARENT, TintBottom,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            // A light vignette: the card's edge is where the foil catches the light, and
            // a bright background right under it would fight the finish.
            drawRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                Paint().apply {
                    shader = RadialGradient(
                        width / 2f,
                        height / 2f,
                        maxOf(width, height) * 0.75f,
                        intArrayOf(Color.TRANSPARENT, Vignette),
                        floatArrayOf(0.55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }
        return out
    }

    /**
     * The card's face, flattened, at preview size.
     *
     * The three layers composited in the order the viewer stacks them, and nothing else:
     * no foil sweep, no parallax, no camera. Those belong to a card being handled, and
     * this is what a card looks like lying still -- which is what a tile in a grid of
     * cards is. Reproducing the finish here would be a second renderer to keep in step
     * with the first, and it would still be a still picture of something that moves.
     *
     * Drawn at [CardLayerGeometry.PreviewWidth] rather than at the face's own size, in
     * one filtered pass, so a card costs a quarter of the disk for the picture nothing
     * reads at full size.
     */
    private fun previewOf(background: Bitmap, subject: Bitmap, text: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(
            CardLayerGeometry.PreviewWidth,
            CardLayerGeometry.PreviewHeight,
            Bitmap.Config.ARGB_8888,
        )
        val destination = RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        Canvas(out).apply {
            drawBitmap(background, null, destination, paint)
            drawBitmap(subject, null, destination, paint)
            drawBitmap(text, null, destination, paint)
        }
        return out
    }

    /** Reduces [source] to [CardLayerGeometry.BlurSourceEdge] wide and expands it back. */
    private fun blurred(source: Bitmap): Bitmap {
        val edge = CardLayerGeometry.BlurSourceEdge
        val down = Bitmap.createScaledBitmap(source, edge * 2, edge * 2 * source.height / source.width, true)
        val tiny = Bitmap.createScaledBitmap(down, edge, edge * source.height / source.width, true)
        if (down !== source) down.recycle()
        // Back up in one step: at this size bilinear upscaling is a smooth ramp, with no
        // detail left to alias.
        val up = Bitmap.createScaledBitmap(tiny, source.width, source.height, true)
        if (tiny !== source) tiny.recycle()
        return up
    }

    /**
     * The card face uses a kai (regular-script) face, chained to the system's own.
     *
     * The bundled font is subset to GB2312, which is all of common simplified Chinese
     * but not kana, not traditional-only forms, and not emoji -- all of which turn up in
     * this library's titles. The fallback chain means the worst case is a change of face
     * for one glyph instead of a blank box in the middle of the title.
     *
     * The three levels of `runCatching` are not defensive noise: they are three
     * genuinely different failure modes ending in three different faces -- chained kai,
     * plain kai, and the system's own -- so a font problem degrades the card rather than
     * failing the build of it.
     */
    private fun cardTypeface(): Typeface = runCatching {
        val kai = Typeface.createFromAsset(context.assets, FontAssetPath)
        runCatching {
            val family = FontFamily.Builder(Font.Builder(context.assets, FontAssetPath).build()).build()
            Typeface.CustomFallbackBuilder(family).setSystemFallback(FallbackFamily).build()
        }.getOrDefault(kai)
    }.getOrDefault(Typeface.SERIF)

    private fun writePng(file: File, bitmap: Bitmap) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                // The quality argument is read only by the lossy encoders; PNG ignores it.
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        } finally {
            bitmap.recycle()
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    companion object {
        const val SubjectName = "subject.png"
        const val BackgroundName = "background.png"
        const val TextName = "text.png"
        const val FontAssetPath = "card/font/kai.ttf"

        private const val ProcessTimeoutSeconds = 20L
        private const val FallbackFamily = "sans-serif"

        private val TintTop = Color.argb(150, 10, 12, 18)
        private val TintBottom = Color.argb(170, 8, 10, 15)
        private val Vignette = Color.argb(120, 6, 8, 12)
    }
}
