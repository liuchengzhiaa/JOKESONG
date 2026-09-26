package com.example.asmrplayer.feature.focuswall

sealed interface FocusWallIntent {
    data class Focus(val itemId: String) : FocusWallIntent
    data class Activate(val itemId: String) : FocusWallIntent
}

/**
 * A single tap is the only gesture that resolves to an intent, and it is what
 * moves focus.
 *
 * Tapping a card that does not hold focus focuses it; tapping the card that
 * already holds focus activates it. Because the second tap of a quick double tap
 * lands on a card that the first tap has just focused, "double tap to open" falls
 * out of this rule for free -- which is why no double-tap gesture is bound, and
 * therefore why a single tap is delivered immediately instead of waiting out the
 * double-tap timeout.
 *
 * There is deliberately no drag-release path. Focus used to follow whichever card
 * came nearest the viewport centre when the drag ended, and the canvas then
 * re-centred on it, so every scroll reflowed a block and moved the wall. Panning
 * now never changes focus.
 */
fun resolveCardTap(focusedItemId: String?, tappedItemId: String): FocusWallIntent =
    if (tappedItemId == focusedItemId) {
        FocusWallIntent.Activate(tappedItemId)
    } else {
        FocusWallIntent.Focus(tappedItemId)
    }

/**
 * Whether the wall can move focus onto the item that is playing.
 *
 * Nothing playing means nothing to focus. A playing item that is on this wall is
 * one tap away. Anything else has to be reached by following the queue back to the
 * folder it was started from, which only works while that folder is still known --
 * a queue that outlived the process cannot be traced back, so the affordance stays
 * disabled rather than silently doing nothing.
 */
fun canRevealPlayingItem(
    playingItemId: String?,
    listedItemIds: Set<String>,
    playbackOriginKnown: Boolean,
): Boolean = when {
    playingItemId == null -> false
    playingItemId in listedItemIds -> true
    else -> playbackOriginKnown
}

/** Lights-out never hides the visual subject or the active media controls. */
fun focusWallPosterAlpha(
    normalAlpha: Float,
    lightsOut: Boolean,
    isFocused: Boolean,
    isCurrent: Boolean,
): Float = if (lightsOut && !isFocused && !isCurrent) {
    normalAlpha.coerceAtMost(0.22f)
} else {
    normalAlpha
}

/** Reduced motion removes transitions rather than merely making them slightly shorter. */
fun wallMotionDuration(normalDurationMillis: Int, motionPercent: Int): Int =
    (normalDurationMillis.coerceAtLeast(0) * motionPercent.coerceIn(0, 100) / 100f).toInt()

/** Auto-focus responds to an actual Media3 item change, never merely opening the wall. */
fun shouldAutoFocusPlayingItem(
    enabled: Boolean,
    foliaVisible: Boolean,
    previousItemId: String?,
    currentItemId: String?,
    reachable: Boolean,
): Boolean = enabled &&
    foliaVisible &&
    currentItemId != null &&
    currentItemId != previousItemId &&
    reachable
