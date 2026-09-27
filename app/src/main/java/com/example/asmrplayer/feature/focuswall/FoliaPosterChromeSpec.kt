package com.example.asmrplayer.feature.focuswall

/**
 * Normalized geometry for the focused Folia poster chrome.
 *
 * These values were measured from the owner's 470 x 467 reference frame instead
 * of being expressed as phone dp. Keeping them normalized makes the composition
 * hold when the focused wall preset is square, portrait or landscape.
 */
internal object FoliaPosterChromeSpec {
    const val HorizontalInset = 0.043f
    const val ContextInset = 0.085f

    const val StatusTop = 0.024f
    const val HeaderTop = 0.096f
    // These are line centres, not top edges. The active line is truly centred and
    // both context lines are the same distance from it even when their fonts differ.
    const val PreviousLyricCenter = 0.355f
    const val CurrentLyricCenter = 0.500f
    const val FollowingLyricCenter = 0.645f
    const val ControlsTop = 0.878f
    const val ProgressTop = 0.946f

    const val HeaderTitleWidth = 0.56f
    const val PreviousLyricWidth = 0.82f
    const val CurrentLyricWidth = 0.90f
    const val FollowingLyricWidth = 0.76f

    const val HeaderTitleFont = 0.035f
    const val HeaderMetadataFont = 0.018f
    const val PreviousLyricFont = 0.043f
    const val CurrentLyricFont = 0.070f
    const val FollowingLyricFont = 0.043f

    const val OrdinaryInset = 0.052f
    const val OrdinaryBottom = 0.055f
    const val OrdinaryTitleFont = 0.072f
    const val OrdinaryMetadataFont = 0.033f
    const val OrdinaryTypeBadge = 0.105f

    const val ControlVisualSize = 0.034f
    const val ControlTouchSize = 0.066f
}
