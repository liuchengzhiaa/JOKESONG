package com.example.asmrplayer.ui.card

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.asmrplayer.core.card.CardConfig
import com.example.asmrplayer.core.card.CardLayerFactory
import com.example.asmrplayer.core.card.CardRepository
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Answers every request the card viewer makes, out of the app's own assets and files.
 *
 * The viewer is a static Three.js page that wants to `fetch` its config and load four
 * binaries. It cannot be pointed at `file://`: `fetch()` rejects any scheme that is not
 * http(s), and the texture loader goes through `fetch` too, so a file-URL page fails
 * before it draws anything. It cannot be pointed at a local HTTP server either -- the app
 * has no `INTERNET` permission, so it cannot open a listening socket at all. Interception
 * is what is left, and it is also the best of the three: nothing leaves the process.
 *
 * The host is `appassets.androidplatform.net` because that is the address the framework's
 * own asset loader uses, and it is one that can never resolve anywhere else. If a request
 * ever escaped interception the page would fail cleanly rather than reach a real server
 * that happens to be named the same thing.
 */
internal class CardAssetServer(
    private val context: Context,
    /** The only card this instance will serve. See [respond]. */
    private val cardId: String,
) {

    /**
     * The response for [request], or a 404.
     *
     * Two rules here are load-bearing rather than stylistic:
     *
     * - **Never return null.** Returning null does not mean "pass this one through"; the
     *   WebView answers its own cache, fails, and reports `ERR_CACHE_MISS`. Turning that
     *   into an explicit 404 is what makes a missing layer show up as a missing layer.
     * - **Never answer a path by joining strings.** The path is matched against a fixed
     *   table, so `..` is not rejected, it is simply unmatched. Only the card id is data,
     *   and it is checked to be one path segment before it is used.
     */
    fun respond(request: WebResourceRequest): WebResourceResponse {
        val segments = request.url.path.orEmpty().trim('/').split('/')
        if (segments.size < 2 || segments[0] != RootSegment || segments[1] != cardId) {
            return notFound()
        }
        if (!CardRepository.isSafeSegment(cardId)) return notFound()

        // `./` -- the wordmark's home link -- is the document itself.
        val path = segments.drop(2).joinToString("/").ifEmpty { IndexPath }

        AssetRoutes[path]?.let { route -> return asset(route) }
        CardRoutes[path]?.let { mime -> return cardFile(path, mime) }
        return notFound()
    }

    private fun asset(route: Route): WebResourceResponse = runCatching {
        response(route.mime, context.assets.open("$AssetRoot/${route.asset}").readBytes())
    }.getOrElse { notFound() }

    private fun cardFile(path: String, mime: String): WebResourceResponse = runCatching {
        val directory = File(context.filesDir, CardRepository.RootDirectoryName)
        val file = File(File(directory, cardId), path)
        // Belt and braces over the table match above: a card whose directory was deleted
        // from under the viewer must read as missing, not as an empty document.
        if (!file.isFile) return@runCatching notFound()
        response(mime, file.readBytes())
    }.getOrElse { notFound() }

    private fun response(mime: String, bytes: ByteArray): WebResourceResponse =
        WebResourceResponse(
            // Bare, with no `; charset=` on it. See [AssetRoutes] for what that costs.
            mime,
            // A charset on a binary type is not just noise: the WebView may transcode.
            if (mime.startsWith("image/") || mime == MimeGltf) null else Charset,
            HttpOk,
            ReasonOk,
            // Nothing may be cached. Each card lives at its own path precisely so one
            // card can never be answered with another's texture, and `no-store` is what
            // stops a rebuilt card from being answered with its own previous version.
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(bytes),
        )

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        MimeText,
        Charset,
        HttpNotFound,
        ReasonNotFound,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

    companion object {
        /** The URL prefix the viewer is loaded from, and the origin every request uses. */
        const val Origin = "https://appassets.androidplatform.net"

        private const val RootSegment = "cards"
        private const val IndexPath = "index.html"
        private const val AssetRoot = "card"

        private const val MimeText = "text/plain"
        private const val MimeHtml = "text/html"
        private const val MimeCss = "text/css"
        private const val MimeJs = "text/javascript"
        private const val MimeJson = "application/json"
        private const val MimePng = "image/png"
        private const val MimeGltf = "model/gltf-binary"
        private const val Charset = "utf-8"
        private const val HttpOk = 200
        private const val ReasonOk = "OK"
        private const val HttpNotFound = 404
        private const val ReasonNotFound = "Not Found"

        /**
         * The URL of one card's document. Its own path is what keeps cards apart.
         *
         * `embedded` selects the Android shell over the desktop atelier. `bare` goes one
         * further and strips the shell's own chrome as well, for the one host that is not
         * showing a card as a page about itself: the player's artwork slot, where the
         * save button and the depth panel would be controls drawn on top of the cover
         * they are about.
         */
        fun viewerUrl(cardId: String, bare: Boolean = false): String =
            "$Origin/$RootSegment/$cardId/$IndexPath?embedded=1" +
                if (bare) "&$BareParam=1" else ""

        /** The query parameter `index.html` looks for before CSS is parsed. */
        const val BareParam = "bare"

        /**
         * The vendored viewer, served from the app's assets.
         *
         * `app.bundle.js` is `text/javascript` and not a byte off that: the page loads it
         * as a module, and a module served with the wrong type is refused outright. The
         * symptom is a page that sits on "正在装裱作品" with nothing but a console error,
         * which is a hard failure to diagnose from a phone.
         *
         * **Every type here is bare, and that is load-bearing.** Putting the charset in
         * these strings as well as in the `encoding` argument is not a harmless
         * duplication: the WebView passes the mime string through to the document, and a
         * main frame whose type reads `text/html; charset=utf-8` rather than `text/html`
         * is not rendered as HTML at all. It is rendered as *text* -- the document that
         * results is `<body><pre>&lt;!doctype html&gt;...`, the card's own source on a
         * white page. That is the blank viewer, and it looks exactly like a viewer that
         * never loaded. The charset belongs in `encoding`, which is the argument the
         * platform has for it; see [response].
         *
         * The asset path is spelled out rather than derived from the request path,
         * because the two differ in exactly one place and that place used to 404. The
         * viewer asks for the model as `./assets/card.glb`, the same shape as the layer
         * paths next to it in `card-config.json`, while the vendored template keeps the
         * model at the top of its folder like its other files. Answering the request from
         * a table that says where the bytes are is what keeps a path typo a compile-time
         * question rather than a message the page prints about itself.
         */
        private val AssetRoutes = mapOf(
            "index.html" to Route("index.html", MimeHtml),
            "style.css" to Route("style.css", MimeCss),
            "app.bundle.js" to Route("app.bundle.js", MimeJs),
            "assets/card.glb" to Route("card.glb", MimeGltf),
        )

        /**
         * One request path's answer: which file in the assets holds it, and what it is.
         *
         * `asset` is relative to [AssetRoot]; it is not the request path, and assuming it
         * was is what the comment on [AssetRoutes] is about.
         */
        private class Route(val asset: String, val mime: String)

        /**
         * One card's own files, where the request path *is* the path inside the card's
         * directory -- that directory is ours, so there is nothing to translate.
         */
        private val CardRoutes = mapOf(
            CardConfig.ConfigFileName to MimeJson,
            "assets/${CardLayerFactory.SubjectName}" to MimePng,
            "assets/${CardLayerFactory.BackgroundName}" to MimePng,
            "assets/${CardLayerFactory.TextName}" to MimePng,
        )
    }
}
