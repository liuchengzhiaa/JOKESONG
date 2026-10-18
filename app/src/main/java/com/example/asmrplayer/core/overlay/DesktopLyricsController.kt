package com.example.asmrplayer.core.overlay

import android.app.KeyguardManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.asmrplayer.core.storage.AppPreferences
import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextReader
import com.example.asmrplayer.core.subtitles.parseTimedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Playback-service owned: continues without the Activity/controller; never starts another FGS. */
class DesktopLyricsController(private val context: Context, private val player: Player) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = AppPreferences(context)
    private val wm = context.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private var settings = DesktopLyricsSettings()
    private var ticker: Job? = null
    private var reader: Job? = null
    private var loadedKey: String? = null
    private var document: TimedTextDocument? = null
    private var view: DesktopLyricsView? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowConfiguration: android.content.res.Configuration? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) removeWindow()
        }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { loadCurrentLyrics() }
    }

    init {
        ContextCompat.registerReceiver(context, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        player.addListener(listener)
        scope.launch {
            prefs.settings.map { it.desktopLyrics }.distinctUntilChanged().collect { value ->
                settings = value
                ticker?.cancel()
                ticker = null
                removeWindow()
                if (settings.enabled) loadCurrentLyrics() else {
                    reader?.cancel(); reader = null; loadedKey = null; document = null
                }
                if (settings.enabled) ticker = scope.launch {
                    while (true) {
                        updateWindow()
                        delay(if (view != null && player.isPlaying && settings.wordByWord) 50L else 250L)
                    }
                }
            }
        }
    }

    private fun loadCurrentLyrics() {
        if (!settings.enabled) return
        val item = player.currentMediaItem
        val extras = item?.mediaMetadata?.extras
        val uri = extras?.getString("desktop_lyric_uri")
        val key = item?.mediaId?.let { "$it|$uri|${extras?.getString("desktop_lyric_version")}" }
        if (key == loadedKey) return
        loadedKey = key
        reader?.cancel()
        document = null
        removeWindow() // Never show the old song while reading the next one.
        val format = extras?.getString("desktop_lyric_format")?.let {
            runCatching { TimedTextFormat.valueOf(it) }.getOrNull()
        } ?: return
        if (uri == null) return
        reader = scope.launch {
            val source = TimedTextReader(context.contentResolver).read(uri.toUri())
            val parsed = source?.let { withContext(Dispatchers.Default) { parseTimedText(format, it) } }
            if (loadedKey == key) document = parsed?.takeIf { it.isTimed }
        }
    }

    @android.annotation.SuppressLint("RtlHardcoded") // Drag coordinates are absolute screen-left, not layout-start.
    private fun updateWindow() {
        if (!shouldShowDesktopLyrics(settings.enabled, Settings.canDrawOverlays(context),
                power.isInteractive, keyguard.isKeyguardLocked,
                document?.isTimed == true && player.playbackState != Player.STATE_ENDED && player.mediaItemCount > 0)) {
            removeWindow()
            return
        }
        val metrics = context.resources.displayMetrics
        // Same window across app/Home transitions. Query window metrics only when
        // creating or reconfiguring, not on every word-sweep tick.
        if (view != null && windowConfiguration == context.resources.configuration) {
            view?.update(document ?: return, player.currentPosition.coerceAtLeast(0L))
            return
        }
        removeWindow()
        val windowMetrics = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics else null
        val bounds = if (Build.VERSION.SDK_INT >= 30) windowMetrics?.bounds else null
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels
        if (view == null) {
            val insets = if (Build.VERSION.SDK_INT >= 30) windowMetrics?.windowInsets
                ?.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()) else null
            val top = insets?.top ?: (32 * metrics.density).toInt()
            val bottom = insets?.bottom ?: (32 * metrics.density).toInt()
            val left = insets?.left ?: 0
            val right = insets?.right ?: 0
            val windowWidth = ((width - left - right) * .9f).toInt().coerceAtLeast(1)
            val fontPixels = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                settings.fontSizeSp.toFloat(), metrics)
            val windowHeight = ((fontPixels * 2.65f * settings.lineCount) +
                (if (settings.locked) 24 else 62) * metrics.density).toInt().coerceAtMost((height - top - bottom).coerceAtLeast(1))
            val maxX = (width - left - right - windowWidth).coerceAtLeast(0)
            val maxY = (height - bottom - windowHeight - top).coerceAtLeast(0)
            val p = WindowManager.LayoutParams(windowWidth, windowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    (if (settings.locked) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0), PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                // Android 12's untrusted-touch rule is based on WINDOW alpha, not background paint.
                alpha = if (Build.VERSION.SDK_INT >= 31) minOf(.75f,
                    context.getSystemService(android.hardware.input.InputManager::class.java).maximumObscuringOpacityForTouch) else .75f
                x = left + maxX * settings.xPerMille / 1000
                y = top + maxY * settings.yPerMille / 1000
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            val v = DesktopLyricsView(context, settings,
                onClose = { scope.launch { prefs.setDesktopLyricsEnabled(false) } },
                onLock = { scope.launch { prefs.setDesktopLyrics(settings.copy(locked = true)) } },
                onDrag = { dx, dy, finished ->
                    p.x = (p.x + dx).coerceIn(left, left + maxX)
                    p.y = (p.y + dy).coerceIn(top, top + maxY)
                    if (view != null) runCatching { wm.updateViewLayout(view, p) }.onFailure { removeWindow() }
                    if (finished) scope.launch { prefs.setDesktopLyricsPosition(
                        if (maxX == 0) 0 else (p.x - left) * 1000 / maxX,
                        if (maxY == 0) 0 else (p.y - top) * 1000 / maxY) }
                })
            try {
                wm.addView(v, p); view = v; params = p
                windowConfiguration = android.content.res.Configuration(context.resources.configuration)
            }
            catch (_: WindowManager.BadTokenException) { return }
            catch (_: SecurityException) { return } // Permission may be revoked between check and add.
        }
        view?.update(document ?: return, player.currentPosition.coerceAtLeast(0L))
    }

    private fun removeWindow() {
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null; params = null
        windowConfiguration = null
    }

    fun close() {
        runCatching { context.unregisterReceiver(screenReceiver) }
        player.removeListener(listener)
        scope.cancel()
        removeWindow()
    }
}
