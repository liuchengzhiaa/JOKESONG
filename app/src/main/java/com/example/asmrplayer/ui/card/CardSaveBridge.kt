package com.example.asmrplayer.ui.card

import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import com.example.asmrplayer.core.card.CardExporter
import com.example.asmrplayer.core.card.CardRepository
import java.io.File

/**
 * Receives the viewer's saved PNG, which has no other way out of the WebView.
 *
 * The viewer's save button renders the card at 1400x1800 and hands the result to a
 * synthetic `<a download href="data:image/png;base64,...">`. In a browser that is a
 * download; in a WebView it is nothing. `DownloadListener` does not fire for it, and the
 * `data:` URL is refused by the download machinery -- a pair of long-standing Chromium
 * defects, not a configuration mistake to be found and fixed. So the click is intercepted
 * on the page and the payload is handed over here instead. The vendored viewer is not
 * touched: it still builds the same anchor and still calls the same `click()`.
 *
 * The payload arrives in chunks because it is several megabytes of base64. Marshalling
 * that as one string across the bridge allocates a second copy of it in the Java heap on
 * top of the copy the page already holds, which is the version of this that fails on a
 * low-memory phone with an error that looks nothing like an out-of-memory.
 */
internal class CardSaveBridge(
    private val context: Context,
    private val cardDirectory: File,
    /** Called off the main thread once a save has finished, one way or the other. */
    private val onResult: (Boolean) -> Unit,
) {

    private val payload = StringBuilder()
    private var fileName: String? = null

    @JavascriptInterface
    fun begin(name: String, length: Int) {
        synchronized(this) {
            payload.setLength(0)
            fileName = name
        }
    }

    @JavascriptInterface
    fun chunk(part: String) {
        synchronized(this) {
            // A page that overran its own declared length is not one to keep feeding.
            if (payload.length + part.length > MaxPayloadChars) return
            payload.append(part)
        }
    }

    @JavascriptInterface
    fun end() {
        val encoded = synchronized(this) {
            val text = payload.toString()
            payload.setLength(0)
            text
        }
        onResult(write(encoded))
    }

    @JavascriptInterface
    fun failed(reason: String) {
        synchronized(this) {
            payload.setLength(0)
            fileName = null
        }
        onResult(false)
    }

    /**
     * Decodes and stores the payload, in both places it is wanted.
     *
     * The card's own directory gets a copy because that is what the warehouse shows and
     * what reopening the card displays; Pictures gets a copy because that is what the
     * owner asked for. Either one alone would be a worse answer than both.
     */
    private fun write(encoded: String): Boolean {
        if (encoded.isEmpty()) return false
        val bytes = runCatching {
            Base64.decode(encoded, Base64.DEFAULT)
        }.getOrNull() ?: return false
        if (bytes.isEmpty()) return false

        val named = synchronized(this) { fileName } ?: CardRepository.ScreenshotName
        val stored = runCatching {
            File(cardDirectory, CardRepository.ScreenshotName).writeBytes(bytes)
        }.isSuccess
        val exported = CardExporter.toPictures(
            context,
            CardExporter.displayName(named),
            bytes,
        )
        return stored || exported
    }

    companion object {
        /** The name the page's own bridge object is reachable under. */
        const val InterfaceName = "CardSave"

        /** A ceiling well above a 1400x1800 PNG and far below anything that OOMs. */
        private const val MaxPayloadChars = 48 * 1024 * 1024

        /** How much of the payload crosses the bridge per call. */
        const val ChunkChars = 512 * 1024

        /**
         * Installed into the page once its document has loaded.
         *
         * It replaces `HTMLAnchorElement.prototype.click` rather than listening for a
         * click: the anchor the viewer builds is never added to the document, so a
         * listener on the document would never see it. Everything that is not the save
         * anchor goes straight through to the original method, so the rest of the page's
         * behaviour is untouched.
         *
         * Idempotent, because `onPageFinished` can fire more than once.
         */
        val HookScript: String = """
            (function () {
              if (window.__cardSaveHooked) return;
              window.__cardSaveHooked = true;
              var original = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function () {
                var href = typeof this.href === "string" ? this.href : "";
                if (!this.download || href.indexOf("data:image/png;base64,") !== 0) {
                  return original.apply(this, arguments);
                }
                try {
                  var body = href.slice("data:image/png;base64,".length);
                  var bridge = window.$InterfaceName;
                  bridge.begin(this.download, body.length);
                  for (var at = 0; at < body.length; at += $ChunkChars) {
                    bridge.chunk(body.slice(at, at + $ChunkChars));
                  }
                  bridge.end();
                } catch (error) {
                  try { window.$InterfaceName.failed(String(error)); } catch (ignored) {}
                }
              };
            })();
        """.trimIndent()

        /**
         * Asks the page whether it can run the viewer at all, and what it would draw on.
         *
         * The viewer's bundle is three.js r180 and uses optional chaining and nullish
         * coalescing. Those are syntax, not API, so a WebView whose Chromium predates
         * them does not fail a call -- it refuses to parse the module, and the page sits
         * on "正在装裱作品" with the error visible only in a console nobody can open on a
         * phone. Android 10 shipped Chromium 74, so this is a real device and not a
         * hypothetical one.
         *
         * The test is a feature test rather than a version comparison because the
         * version string of a vendor WebView is the vendor's own. `new Function` is used
         * so the syntax is only ever compiled if the engine can compile it.
         *
         * The WebGL half is not a gate -- the answer is only ever read out into the
         * diagnostic log. The viewer has its own 2D fallback for an engine without
         * WebGL, so a card on such a device is drawn differently rather than not at all,
         * and the one thing worth knowing while looking at a card that did not appear is
         * which of the two paths was even available.
         */
        val SupportProbeScript: String = """
            (function () {
              var syntax = "no";
              try {
                syntax = new Function("var a = {}; return a?.b ?? 1")() === 1 ? "ok" : "no";
              } catch (error) {
                return "no " + String(error);
              }
              var canvas = document.createElement("canvas");
              var gl = canvas.getContext("webgl2") || canvas.getContext("webgl");
              var version = "none";
              if (gl) {
                version = String(gl.getParameter(gl.VERSION));
                var lose = gl.getExtension("WEBGL_lose_context");
                if (lose) lose.loseContext();
              }
              return syntax + " webgl=" + version;
            })();
        """.trimIndent()
    }
}
