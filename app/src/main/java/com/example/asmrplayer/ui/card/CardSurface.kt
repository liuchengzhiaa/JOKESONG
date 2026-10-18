package com.example.asmrplayer.ui.card

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.asmrplayer.core.card.CardRepository
import java.io.File
import kotlin.math.roundToInt

/**
 * One card, rendered, with no chrome of its own.
 *
 * The WebGL scene is the whole of what this draws: no title, no way out, no failure
 * message. Those belong to whichever surface is hosting it -- the viewer screen puts a
 * back control and a diagnostic panel over it, the player's artwork slot puts nothing
 * over it at all -- and keeping them out of here is what lets the same card be both the
 * thing you opened and the cover you are playing.
 *
 * What is left is everything that is true of a card wherever it appears: the navigation
 * and its teardown, the app's own lifecycle, the four safe-area insets the page cannot
 * ask Android for, the gestures, and the bridge that gets a saved PNG out of the
 * renderer.
 *
 * @param onStatus reported whenever the page's state changes, including the reset to
 *   [CardViewerStatus.Loading] when [cardId] changes.
 * @param onLog the page's console and its request log, newest last. Handed over on every
 *   line rather than on demand because the only surface that shows it does so at the
 *   moment the page is failing, which is not a moment that can be scheduled.
 * @param onSaveResult whether the page's own save button got the card into the picture
 *   library. Reported rather than assumed successful: a full disk and a refused
 *   MediaStore insert are both silent on the page.
 */
@Composable
internal fun CardSurface(
    cardId: String,
    modifier: Modifier = Modifier,
    bare: Boolean = false,
    onStatus: (CardViewerStatus) -> Unit = {},
    onLog: (List<String>) -> Unit = {},
    onSaveResult: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember(cardId) { mutableStateOf<WebView?>(null) }
    var status by remember(cardId) { mutableStateOf<CardViewerStatus>(CardViewerStatus.Loading) }

    // The page draws edge to edge under this app's status bar, and the controls it puts
    // there are the only chrome in the app that Compose does not position. CSS cannot ask
    // Android where that bar is: `env(safe-area-inset-*)` is a convenience a WebView is
    // not required to resolve, and outside a browser it generally resolves to zero. The
    // host knows the answer, so the host publishes it -- as the same `safeDrawing` inset
    // a native control beside the WebView would use, which is what puts the two rows of
    // controls on one line.
    val safeArea = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val safeTop = safeArea.calculateTopPadding().value.roundToInt()
    val safeRight = safeArea.calculateRightPadding(layoutDirection).value.roundToInt()
    val safeBottom = safeArea.calculateBottomPadding().value.roundToInt()
    val safeLeft = safeArea.calculateLeftPadding(layoutDirection).value.roundToInt()
    val insetScript = remember(safeTop, safeRight, safeBottom, safeLeft) {
        safeAreaScript(safeTop, safeRight, safeBottom, safeLeft)
    }

    // The page runs in a renderer this app cannot attach a debugger to, on a device with
    // no console to read. Everything the page or the request path says about itself is
    // therefore collected here, because the alternative is a card that does not appear
    // and no way to tell a missing texture from a missing WebGL.
    val log = remember(cardId) { ViewerLog() }

    val cardUrl = remember(cardId, bare) { CardAssetServer.viewerUrl(cardId, bare = bare) }
    val server = remember(cardId) { CardAssetServer(context, cardId) }
    val cardDirectory = remember(cardId, context) {
        File(File(context.filesDir, CardRepository.RootDirectoryName), cardId)
    }
    val bridge = remember(cardId) {
        CardSaveBridge(context, cardDirectory) { saved ->
            // The bridge runs on the WebView's own thread, so anything that touches
            // composition has to be handed back to the main one.
            webView?.post { onSaveResult(saved) }
        }
    }

    LaunchedEffect(cardId, status) { onStatus(status) }
    LaunchedEffect(cardId) { onLog(emptyList()) }

    // Re-published rather than published once: the properties live in the document, so a
    // page that has just loaded has none of them, and the insets themselves change when
    // the device rotates or the window is resized.
    LaunchedEffect(webView, status, insetScript) {
        webView?.evaluateJavascript(insetScript, null)
    }

    // The page runs a requestAnimationFrame loop for the card's idle drift, which is the
    // point of it -- but a WebView that is off-screen or in a backgrounded app would keep
    // that loop running for a picture nobody is looking at.
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.onPause()
        }
    }

    if (status !is CardViewerStatus.Crashed) {
        key(cardId, bare) {
            AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = { ctx ->
                    createViewer(
                        context = ctx,
                        cardUrl = cardUrl,
                        server = server,
                        bridge = bridge,
                        onStatus = { status = it },
                        onRecord = { view, line ->
                            // The interceptor answers on a background thread, so the
                            // log is filled from the main one.
                            view.post {
                                log.add(line)
                                onLog(log.snapshot())
                            }
                        },
                    ).also { webView = it }
                },
                onRelease = { view ->
                    webView = null
                    view.tearDown()
                },
            )
        }
    }
}

/**
 * The insets, as CSS custom properties the embedded shell's own rules consume.
 *
 * Four properties rather than one string of padding because the stylesheet needs them
 * separately: the top two place the page's glass actions, the bottom two place the
 * workbench and the panel that opens over it, and each is offset from the safe edge by
 * a different amount. Every value is in CSS pixels, which in a WebView are dp -- the
 * same unit Compose measured them in, so no conversion is owed here.
 *
 * The document is checked for rather than assumed. This runs from a coroutine that can
 * land between the navigation and the first parse, and `documentElement` is null for
 * exactly that long.
 */
internal fun safeAreaScript(top: Int, right: Int, bottom: Int, left: Int): String =
    "(function(){" +
        "var root=document.documentElement;if(!root)return;" +
        "var style=root.style;" +
        "style.setProperty('--safe-top','${top}px');" +
        "style.setProperty('--safe-right','${right}px');" +
        "style.setProperty('--safe-bottom','${bottom}px');" +
        "style.setProperty('--safe-left','${left}px');" +
        "})()"

/**
 * Kept out of the composable so the WebView's configuration reads as one block.
 *
 * JavaScript is on because the viewer *is* a script -- there is no non-scripting way to
 * render the card. The review the lint check asks for is the rest of this file: every
 * request the page can make is answered from an allow-list of paths inside the app's own
 * assets and one card's own directory, `allowFileAccess` and `allowContentAccess` are
 * both off, and the app holds no INTERNET permission, so there is no origin the script
 * could reach even if it were hostile. It is also not third-party code reaching out: the
 * page is vendored, served from an app-private scheme, and never updated at runtime.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createViewer(
    context: Context,
    cardUrl: String,
    server: CardAssetServer,
    bridge: CardSaveBridge,
    onStatus: (CardViewerStatus) -> Unit,
    onRecord: (WebView, String) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    // The viewer is entirely self-contained; it has no business reaching a file URL or
    // a content provider, and neither does anything that manages to run inside it.
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setSupportZoom(false)
    settings.displayZoomControls = false
    // `settings.blockNetworkLoads` is deliberately not set. The app has no INTERNET
    // permission, which already forces it on; repeating it here would only make this
    // screen look like the thing keeping the viewer offline, and it is not.
    overScrollMode = WebView.OVER_SCROLL_NEVER
    isVerticalScrollBarEnabled = false
    setBackgroundColor(AndroidColor.TRANSPARENT)

    addJavascriptInterface(bridge, CardSaveBridge.InterfaceName)
    webChromeClient = object : WebChromeClient() {
        /**
         * The page's own console, which is where it says what it is doing and where the
         * vendored bundle reports a scene it could not build. There is no way to attach
         * to a WebView's console from a phone, so this is the only copy of it that exists.
         */
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            onRecord(
                this@apply,
                "${message.messageLevel()} ${message.message()} " +
                    "(${message.sourceId()}:${message.lineNumber()})",
            )
            return true
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse = server.respond(request).also { response ->
            // Every request, not only the ones that failed. The question this log exists
            // to answer is "how far did the page get", and a list of the requests that
            // were answered says that directly: no entry for `style.css` means the page
            // never asked for its stylesheet, which is a different fault from asking and
            // being refused.
            onRecord(view, "${response.statusCode} ${request.url.path}")
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            onRecord(view, "start $url")
        }

        override fun onPageFinished(view: WebView, url: String) {
            onRecord(view, "finish $url")
            if (url != cardUrl) return
            view.evaluateJavascript(CardSaveBridge.HookScript, null)
            view.evaluateJavascript(CardGesture.Script, null)
            view.evaluateJavascript(CardSaveBridge.SupportProbeScript) { result ->
                onRecord(view, "probe $result")
                if (result?.contains(ProbeOk) == true) {
                    onStatus(CardViewerStatus.Ready)
                } else {
                    onStatus(CardViewerStatus.Unsupported(engineDescription()))
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            onRecord(view, "error ${error.errorCode} ${request.url.path}")
            if (request.isForMainFrame) {
                onStatus(CardViewerStatus.Failed(error.errorCode.toString()))
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            onRecord(view, "http ${errorResponse.statusCode} ${request.url.path}")
            if (request.isForMainFrame) {
                onStatus(CardViewerStatus.Failed(errorResponse.statusCode.toString()))
            }
        }

        /**
         * Returning true is not optional. The default is to let the renderer's death take
         * the process with it, and a card is not worth a crash: the card is dropped from
         * composition instead, which runs the same teardown as leaving the screen.
         */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            onStatus(CardViewerStatus.Crashed)
            return true
        }
    }
    loadUrl(cardUrl)
}

/**
 * Ends the WebView in the order that actually releases the page.
 *
 * `destroy()` alone leaves the page's timers and its renderer process to be reclaimed
 * whenever the system gets round to it; navigating away first is what stops the card's
 * animation loop before the object that owns it goes away.
 */
private fun WebView.tearDown() {
    stopLoading()
    loadUrl(BlankPage)
    removeAllViews()
    destroy()
}

/** Names the engine in the failure message, because "too old" needs a number to act on. */
private fun engineDescription(): String {
    // Branched rather than written as `?: Unknown`, which would widen the value to `Any`
    // and lose the two fields the message is made of.
    val packageInfo = WebView.getCurrentWebViewPackage() ?: return Unknown
    return "${packageInfo.packageName} ${packageInfo.versionName}"
}

/** What the page has been doing, as far as this side can see it. */
internal sealed interface CardViewerStatus {
    data object Loading : CardViewerStatus
    data object Ready : CardViewerStatus
    data class Unsupported(val engine: String) : CardViewerStatus
    data class Failed(val reason: String) : CardViewerStatus
    data object Crashed : CardViewerStatus
}

/**
 * Bounded, because a page that fails in a loop fails once per frame, and synchronised
 * because the requests that fill it are answered off the main thread.
 */
private class ViewerLog {
    private val lock = Any()
    private val lines = ArrayDeque<String>()

    fun add(line: String) {
        synchronized(lock) {
            if (lines.size == MaxLogLines) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }
}

private const val ProbeOk = "ok"
private const val BlankPage = "about:blank"
private const val Unknown = "unknown"
private const val MaxLogLines = 40
