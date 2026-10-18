package com.example.asmrplayer.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.asmrplayer.R
import kotlinx.coroutines.delay

/**
 * The card's own page, hosted in a WebView.
 *
 * Hosting the reference viewer rather than reimplementing it is the decision this screen
 * exists to serve: the card is a WebGL scene with per-layer parallax, four depth planes
 * and four finishes, and a Compose rewrite would be a second and worse version of it.
 *
 * All that is here is what surrounds a card that has been opened: a way back, a message
 * when it will not come up, and a line saying what happened to a save. The card itself,
 * and everything that is true of one wherever it appears, is [CardSurface].
 */
@Composable
fun CardViewerScreen(
    cardId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember(cardId) { mutableStateOf<CardViewerStatus>(CardViewerStatus.Loading) }
    var notice by remember(cardId) { mutableStateOf<Int?>(null) }
    var lines by remember(cardId) { mutableStateOf(emptyList<String>()) }
    var slow by remember(cardId) { mutableStateOf(false) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NoticeMillis)
            notice = null
        }
    }

    // A page that is going to come up does so well inside this; one that is still blank
    // afterwards is one whose reason for being blank is worth putting on screen.
    LaunchedEffect(cardId) {
        delay(SlowLoadMillis)
        slow = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ShellBackground),
    ) {
        CardSurface(
            cardId = cardId,
            onStatus = { status = it },
            onLog = { lines = it },
            onSaveResult = { saved ->
                notice = if (saved) R.string.card_saved else R.string.card_save_failed
                if (saved) onSaved()
            },
        )

        val panel = when (val current = status) {
            CardViewerStatus.Ready -> null
            // Saying nothing while the page is still coming up is right; saying
            // nothing for as long as it takes to wonder whether it ever will is not.
            CardViewerStatus.Loading -> if (slow) {
                stringResource(R.string.card_viewer_slow) to
                    stringResource(R.string.card_viewer_slow_detail)
            } else {
                null
            }
            is CardViewerStatus.Unsupported ->
                stringResource(R.string.card_viewer_unsupported) to
                    stringResource(R.string.card_viewer_unsupported_detail, current.engine)
            is CardViewerStatus.Failed ->
                stringResource(R.string.card_viewer_failed) to
                    stringResource(R.string.card_viewer_failed_detail, current.reason)
            CardViewerStatus.Crashed ->
                stringResource(R.string.card_viewer_crashed) to
                    stringResource(R.string.card_viewer_crashed_detail)
        }
        panel?.let { (title, detail) -> ViewerMessage(title, detail, lines) }

        notice?.let { message ->
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // Native chrome stays recognisably part of the player while the card itself is
        // still rendered by the reference WebGL scene. Keeping this outside the WebView
        // also gives the owner a reliable way back if the renderer is slow or crashes.
        Surface(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(14.dp)
                .size(44.dp),
            shape = CircleShape,
            color = ViewerGlass,
            contentColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.card_back),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * What went wrong, and everything the page said on its way there.
 *
 * The colours here are fixed rather than taken from the theme, and that is the whole
 * point of the block: this sits on top of the vendored shell, which paints itself a
 * light `#fafafa` whatever the app's theme is doing. Theme colours would be the app's
 * own -- near-white in a dark theme -- drawn on a light page, which is not a message,
 * it is a slightly different shade of blank.
 */
@Composable
private fun ViewerMessage(title: String, detail: String, log: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PanelBackground)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = PanelDetail,
            textAlign = TextAlign.Center,
        )
        if (log.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.card_viewer_diagnostics),
                style = MaterialTheme.typography.labelMedium,
                color = PanelDetail,
            )
            log.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = PanelLog,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val NoticeMillis = 2600L
private const val SlowLoadMillis = 4000L

/** The embedded viewer clears to this too, so opening it never flashes white. */
private val ShellBackground = Color(0xFF0D0E13)
private val ViewerGlass = Color(0x991D1E26)

/** Fixed colours for the message panel, for the reason given on it. */
private val PanelBackground = Color(0xF00E1013)
private val PanelDetail = Color(0xFFC5CBD3)
private val PanelLog = Color(0xFF8C949E)
