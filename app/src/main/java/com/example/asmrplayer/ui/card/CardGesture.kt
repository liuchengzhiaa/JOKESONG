package com.example.asmrplayer.ui.card

/**
 * Pinch to magnify and double-tap to reset, for the vendored card page.
 *
 * The page already has a zoom: a `wheel` listener writes a `zoom` value that `resize()`
 * divides the camera frustum by, so magnifying re-renders the scene at full resolution
 * rather than scaling a bitmap. What the page does not have is any way to reach that
 * from a phone. A WebView never delivers a wheel event, and the page's own gesture
 * handler turns the card under one finger -- including under a second one, because it
 * tracks "a finger is down" in a single flag.
 *
 * So this adds no renderer and no second transform. It translates a two-finger pinch
 * into the wheel events the page already handles, in the page's own unit, and it takes
 * the card out of the drag before it does -- which is what lets one finger keep turning
 * the card and two fingers change how close it is.
 */
internal object CardGesture {

    /**
     * The magnification range, which the page's own wheel clamp must match.
     *
     * Duplicated rather than owned: the clamp lives in the vendored bundle and this
     * cannot read it, so [CardGestureTest] asserts the two agree. Changing one without
     * the other would otherwise show up as a pinch that stops moving partway, with
     * nothing on screen to say why.
     */
    const val MinZoom = 0.72
    const val MaxZoom = 2.4

    /** The page's own wheel unit: `zoom` moves by `deltaY * 1e-3`. */
    private const val WheelPerZoom = 1000.0

    /** How close together two taps must land to count as one double-tap. */
    private const val DoubleTapMillis = 320

    /** How far a tap may wander and still be a tap rather than a drag. */
    private const val TapSlopPx = 18.0

    /**
     * Installed into the page once its document has loaded.
     *
     * Every listener is on `document` in the capture phase rather than on the stage,
     * because the page's own handlers are on the stage: a capture listener on an
     * ancestor runs first and can stop the event reaching them, while a listener on the
     * same element would be queued behind the ones already registered there.
     *
     * Nothing here is allowed to break a page that cannot zoom. Every read of the page's
     * state is guarded, and if `__holo` is not there yet -- the scene is built
     * asynchronously and this is installed as soon as the document finishes -- the
     * gesture simply does not move the card, which is what it did before.
     *
     * Idempotent, because `onPageFinished` can fire more than once.
     */
    val Script: String = """
        (function () {
          if (window.__cardPinch) return;
          window.__cardPinch = true;

          var stage = document.getElementById("stage");
          if (!stage || !stage.dispatchEvent) return;

          var points = {};
          var pinching = false;
          var startGap = 0;
          var startZoom = 1;
          var lastTapAt = 0;
          var lastTapX = 0;
          var lastTapY = 0;

          function ids() { return Object.keys(points); }

          function gap() {
            var list = ids();
            if (list.length < 2) return 0;
            var a = points[list[0]];
            var b = points[list[1]];
            var dx = a.x - b.x;
            var dy = a.y - b.y;
            return Math.sqrt(dx * dx + dy * dy);
          }

          function clamp(value) {
            return Math.min($MaxZoom, Math.max($MinZoom, value));
          }

          // The page's own state, or a neutral value if the scene is not up yet.
          function zoom() {
            try {
              var state = window.__holo && window.__holo.getState && window.__holo.getState();
              return state && state.zoom ? state.zoom : 1;
            } catch (error) {
              return 1;
            }
          }

          // Dispatching the page's own wheel event, rather than reaching into its
          // variables, is what keeps this to one zoom implementation: whatever the page
          // does with a wheel -- clamping, re-fitting the frustum, re-rendering -- it
          // does here too.
          function magnifyTo(target) {
            var delta = (zoom() - clamp(target)) * $WheelPerZoom;
            if (!delta) return;
            stage.dispatchEvent(new WheelEvent("wheel", {
              deltaY: delta,
              bubbles: true,
              cancelable: true
            }));
          }

          // The page keeps one "dragging" flag for any finger. Left alone, the second
          // finger would keep turning the card while it was being magnified, so the drag
          // is ended here -- the same settle to the nearest face that lifting a finger
          // would have caused.
          function endDrag() {
            try {
              stage.dispatchEvent(new PointerEvent("pointerup", { bubbles: true }));
            } catch (error) {
            }
          }

          function distanceFromStart() {
            return gap() / startGap;
          }

          document.addEventListener("pointerdown", function (event) {
            if (event.pointerType === "mouse") return;
            points[event.pointerId] = { x: event.clientX, y: event.clientY };

            if (ids().length >= 2) {
              if (!pinching) {
                pinching = true;
                startGap = gap();
                startZoom = zoom();
                endDrag();
              }
              event.stopPropagation();
              event.preventDefault();
              return;
            }

            // One finger: a second tap in the same place resets the magnification, which
            // is the only way back out of a card magnified into its own corner.
            var now = Date.now();
            var moved = Math.abs(event.clientX - lastTapX) > $TapSlopPx ||
              Math.abs(event.clientY - lastTapY) > $TapSlopPx;
            if (now - lastTapAt < $DoubleTapMillis && !moved) {
              lastTapAt = 0;
              magnifyTo(1);
              event.stopPropagation();
              return;
            }
            lastTapAt = now;
            lastTapX = event.clientX;
            lastTapY = event.clientY;
          }, true);

          document.addEventListener("pointermove", function (event) {
            if (!(event.pointerId in points)) return;
            points[event.pointerId] = { x: event.clientX, y: event.clientY };
            if (!pinching) return;
            // Swallowed rather than merely ignored. The page's drag handler reads
            // movement in pixels, so letting this through would turn the card by exactly
            // the amount the fingers spread.
            event.stopPropagation();
            event.preventDefault();
            if (startGap > 0) magnifyTo(startZoom * distanceFromStart());
          }, true);

          function lifted(event) {
            if (!(event.pointerId in points)) return;
            delete points[event.pointerId];
            if (pinching && ids().length < 2) {
              pinching = false;
              event.stopPropagation();
              event.preventDefault();
            }
          }

          document.addEventListener("pointerup", lifted, true);
          document.addEventListener("pointercancel", lifted, true);
        })();
    """.trimIndent()
}
