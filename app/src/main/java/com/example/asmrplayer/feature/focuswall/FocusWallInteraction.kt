package com.example.asmrplayer.feature.focuswall

sealed interface FocusWallIntent {
    data class Focus(val itemId: String) : FocusWallIntent
    data class Activate(val itemId: String) : FocusWallIntent
}

fun resolveCardTap(focusedItemId: String?, tappedItemId: String): FocusWallIntent =
    if (focusedItemId == tappedItemId) {
        FocusWallIntent.Activate(tappedItemId)
    } else {
        FocusWallIntent.Focus(tappedItemId)
    }
