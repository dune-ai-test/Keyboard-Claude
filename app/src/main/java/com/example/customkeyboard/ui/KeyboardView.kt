package com.example.customkeyboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.customkeyboard.R
import com.example.customkeyboard.data.KeyModel
import com.example.customkeyboard.data.KeyType
import com.example.customkeyboard.data.KeyboardLayout
import com.example.customkeyboard.data.KeyboardThemeColors
import com.example.customkeyboard.data.KeyboardThemePresets
import com.example.customkeyboard.gesture.KeyPoint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A high-performance custom keyboard view.
 *
 * Performance notes:
 *  - All drawing happens with pre-allocated [Paint]/[RectF] objects reused every frame (no
 *    allocations inside onDraw, which is invoked at up to 60fps during touch/animation).
 *  - Key hit-testing uses a precomputed grid of [RectF] bounds rather than iterating views,
 *    since this is a single custom View, not one View per key (drastically fewer objects,
 *    less layout/measure/draw overhead than a ViewGroup-of-buttons approach).
 *  - Long-press popups and shift/caps state changes trigger [invalidate] only for the affected
 *    region conceptually (kept simple here with full invalidate, which is still cheap because
 *    there are only ~30-40 keys and no bitmaps).
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onKeyTap(key: KeyModel)
        fun onKeyLongPressChar(char: String)
        fun onShiftToggled(caps: Boolean, capsLock: Boolean)
        fun onGestureTypingResult(path: List<KeyPoint>, keyLayout: Map<Char, KeyPoint>)
        fun onKeyDownFeedback()
    }

    var listener: Listener? = null

    var layoutModel: KeyboardLayout? = null
        set(value) {
            field = value
            computeKeyBounds()
            invalidate()
        }

    var shiftActive: Boolean = false
        set(value) { field = value; invalidate() }
    var capsLockActive: Boolean = false
        set(value) { field = value; invalidate() }

    /** Enables/disables swipe (gesture) typing at runtime from settings. */
    var swipeTypingEnabled: Boolean = true

    /** Draws a thin outline around every key when enabled, toggled live from Settings. */
    var keyBordersEnabled: Boolean = false
        set(value) { field = value; invalidate() }

    /** When enabled, keys render with no background rectangle at all — just the label floating
     *  over whatever's behind it (a press highlight/ripple still shows for tap feedback). */
    var transparentKeysEnabled: Boolean = false
        set(value) { field = value; invalidate() }

    // --- Paint objects (allocated once) ---
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySpecialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPrimaryBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPrimaryBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val gesturePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textBounds = Rect()
    private val keyBounds = mutableListOf<KeyBoundsEntry>()

    /** The active color palette; defaults to the built-in dark preset until [applyCustomColors]
     *  is called with whatever the user picked in Settings. */
    private var currentTheme: KeyboardThemeColors = KeyboardThemePresets.MIDNIGHT_TEAL

    /** Optional custom background picture (set via [setBackgroundImage]), drawn center-cropped
     *  behind the (translucent, when an image is active) keys. */
    private var backgroundBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapSrcRect = Rect()
    private val bitmapDstRect = RectF()
    private val scrimPaint = Paint()
    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private data class KeyBoundsEntry(val key: KeyModel, val rect: RectF)

    private var pressedKey: KeyModel? = null
    private var pressedRect: RectF? = null

    // Smooth press-highlight crossfade (replaces an instant color swap so key taps feel
    // responsive/soft rather than an abrupt on/off flip).
    private var pressAnimator: ValueAnimator? = null
    private var pressAlpha: Float = 0f
    private var animatingKey: KeyModel? = null

    // A separate "press-in / bounce-out" scale value: eases toward a slight inset while held,
    // then overshoots past rest on release for a springy, elastic pop rather than a flat stop.
    private var pressScaleAnimator: ValueAnimator? = null
    private var pressScale: Float = 0f

    // Material-style expanding ink ripple centered on the actual touch point.
    private var rippleAnimator: ValueAnimator? = null
    private var rippleProgress: Float = 1f
    private var rippleKey: KeyModel? = null
    private var rippleCenterX = 0f
    private var rippleCenterY = 0f

    // Enlarged "key preview" bubble that pops up above the finger while a character key is
    // held — the classic tactile-feeling feedback most keyboards use, absent before now.
    private var previewAnimator: ValueAnimator? = null
    private var previewAlpha: Float = 0f
    private var previewEntry: KeyBoundsEntry? = null

    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var showingPopupFor: KeyBoundsEntry? = null
    private var popupSelectedIndex = -1

    // Backspace long-press auto-repeat.
    private var backspaceRepeatRunnable: Runnable? = null
    private var isBackspaceRepeating = false

    // --- Gesture typing state ---
    private var isGesturing = false
    private var gesturePoints = mutableListOf<KeyPoint>()
    private var gestureStartKey: KeyModel? = null
    private var downX = 0f
    private var downY = 0f

    init {
        applyCustomColors(currentTheme)
    }

    /** Applies a full color palette at runtime — no APK resources involved, so switching themes
     *  (including the 6 built-in presets and the custom-image overlay palette) never requires
     *  recreating the view. */
    fun applyCustomColors(theme: KeyboardThemeColors) {
        currentTheme = theme
        keyBgPaint.color = theme.keyBackground
        keyBgPressedPaint.color = theme.keyPressedBackground
        keySpecialBgPaint.color = theme.specialKeyBackground
        keyPrimaryBgPaint.color = theme.accent
        keyPrimaryBgPressedPaint.color = theme.accent
        keyTextPaint.color = theme.keyText
        hintTextPaint.color = theme.keyHint
        popupBgPaint.color = theme.popupBackground
        popupTextPaint.color = theme.keyText
        gesturePathPaint.color = theme.accent
        ripplePaint.color = theme.keyText
        keyBorderPaint.color = theme.divider
        setBackgroundColor(theme.background)
        invalidate()
    }

    /** Sets (or clears, when [bitmap] is null) the custom background picture. The bitmap is
     *  expected to already be appropriately downsampled by the caller — this view only scales
     *  and center-crops it to fit, it never decodes or resizes the source image itself. */
    fun setBackgroundImage(bitmap: Bitmap?) {
        backgroundBitmap = bitmap
        invalidate()
    }

    /** Center-crops [backgroundBitmap] to fill the view exactly, matching how a phone wallpaper
     *  or a photo app's "fill" mode behaves, so odd-aspect-ratio pictures never look stretched. */
    private fun drawBackgroundImageIfNeeded(canvas: Canvas) {
        val bitmap = backgroundBitmap ?: return
        if (width == 0 || height == 0) return

        val viewAspect = width.toFloat() / height.toFloat()
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        if (bitmapAspect > viewAspect) {
            // Bitmap is relatively wider than the view: crop its left/right edges.
            val cropWidth = (bitmap.height * viewAspect).toInt().coerceAtMost(bitmap.width)
            val left = (bitmap.width - cropWidth) / 2
            bitmapSrcRect.set(left, 0, left + cropWidth, bitmap.height)
        } else {
            // Bitmap is relatively taller than the view: crop its top/bottom edges.
            val cropHeight = (bitmap.width / viewAspect).toInt().coerceAtMost(bitmap.height)
            val top = (bitmap.height - cropHeight) / 2
            bitmapSrcRect.set(0, top, bitmap.width, top + cropHeight)
        }
        bitmapDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, bitmapSrcRect, bitmapDstRect, bitmapPaint)

        // A flat, even scrim over the whole picture guarantees a baseline of contrast no matter
        // how bright or busy the photo is — without it, keys and text could disappear into
        // light or high-detail areas of the image.
        scrimPaint.color = SCRIM_COLOR
        canvas.drawRect(bitmapDstRect, scrimPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyBounds()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pressAnimator?.cancel()
        pressScaleAnimator?.cancel()
        rippleAnimator?.cancel()
        previewAnimator?.cancel()
        longPressRunnable?.let { removeCallbacks(it) }
        backspaceRepeatRunnable?.let { removeCallbacks(it) }
    }

    private fun computeKeyBounds() {
        keyBounds.clear()
        val layout = layoutModel ?: return
        if (width == 0 || height == 0) return

        val rows = layout.rows
        val rowHeight = height.toFloat() / rows.size
        // Separate, tighter margins for the gap between keys (horizontal) vs. the gap between
        // rows (vertical) — using one larger shared value for both wasted a lot of space and
        // made the actual tappable key rectangles noticeably smaller than they need to be.
        val hMargin = resources.getDimension(R.dimen.key_horizontal_margin)
        val vMargin = resources.getDimension(R.dimen.key_vertical_margin)

        for ((rowIndex, row) in rows.withIndex()) {
            val totalWeight = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
            var xCursor = 0f
            val top = rowIndex * rowHeight + vMargin
            val bottom = (rowIndex + 1) * rowHeight - vMargin
            for (key in row.keys) {
                val keyWidth = (width.toFloat() / totalWeight) * key.widthWeight
                val rect = RectF(xCursor + hMargin, top, xCursor + keyWidth - hMargin, bottom)
                keyBounds.add(KeyBoundsEntry(key, rect))
                xCursor += keyWidth
            }
        }
    }

    /** Exposes the current on-screen center of every visible character key, for the gesture decoder. */
    fun currentKeyLayoutMap(): Map<Char, KeyPoint> {
        val map = HashMap<Char, KeyPoint>()
        for (entry in keyBounds) {
            if (entry.key.type == KeyType.CHARACTER && entry.key.label.length == 1) {
                val c = entry.key.label.lowercase()[0]
                map[c] = KeyPoint(c, entry.rect.centerX(), entry.rect.centerY())
            }
        }
        return map
    }

    /** Starts (or restarts, if the finger slid onto a new key) the smooth press-highlight
     *  crossfade and, for ordinary character keys, the enlarged preview bubble. */
    private fun startPressAnimation(entry: KeyBoundsEntry, touchX: Float, touchY: Float) {
        pressAnimator?.cancel()
        animatingKey = entry.key
        pressAnimator = ValueAnimator.ofFloat(pressAlpha, 1f).apply {
            duration = PRESS_FADE_IN_MS
            addUpdateListener {
                pressAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pressScaleAnimator?.cancel()
        pressScaleAnimator = ValueAnimator.ofFloat(pressScale, 1f).apply {
            duration = PRESS_SCALE_IN_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        startRipple(entry, touchX, touchY)

        val previewEligible = entry.key.type == KeyType.CHARACTER || entry.key.type == KeyType.COMMA ||
            entry.key.type == KeyType.PERIOD || entry.key.type == KeyType.SHIFT || entry.key.type == KeyType.BACKSPACE
        if (previewEligible) {
            showPreview(entry)
        } else {
            hidePreview()
        }
    }

    /** Kicks off a Material-style expanding ink ripple centered on the actual touch point,
     *  clipped to the key's own rounded-rect bounds. Plays to completion on its own timeline
     *  regardless of when the finger lifts, like a normal ripple. */
    private fun startRipple(entry: KeyBoundsEntry, touchX: Float, touchY: Float) {
        rippleAnimator?.cancel()
        rippleKey = entry.key
        rippleCenterX = touchX
        rippleCenterY = touchY
        rippleProgress = 0f
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RIPPLE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                rippleProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Fades the press-highlight back out (from whatever alpha it's currently at) and hides
     *  the preview bubble — called once the finger lifts or the gesture is cancelled. */
    private fun endPressAnimation() {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressAlpha, 0f).apply {
            duration = PRESS_FADE_OUT_MS
            addUpdateListener {
                pressAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        // The bounce: scale overshoots slightly past rest before settling, giving the key an
        // elastic "pop" on release instead of stopping dead.
        pressScaleAnimator?.cancel()
        pressScaleAnimator = ValueAnimator.ofFloat(pressScale, 0f).apply {
            duration = PRESS_SCALE_OUT_MS
            interpolator = OvershootInterpolator(BOUNCE_TENSION)
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        hidePreview()
    }

    private fun showPreview(entry: KeyBoundsEntry) {
        previewAnimator?.cancel()
        previewEntry = entry
        previewAnimator = ValueAnimator.ofFloat(previewAlpha, 1f).apply {
            duration = PREVIEW_FADE_IN_MS
            addUpdateListener {
                previewAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun hidePreview() {
        if (previewEntry == null && previewAlpha == 0f) return
        previewAnimator?.cancel()
        previewAnimator = ValueAnimator.ofFloat(previewAlpha, 0f).apply {
            duration = PREVIEW_FADE_OUT_MS
            addUpdateListener {
                previewAlpha = it.animatedValue as Float
                if (previewAlpha <= 0.01f) previewEntry = null
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackgroundImageIfNeeded(canvas)
        val cornerRadius = resources.getDimension(R.dimen.key_corner_radius)
        keyTextPaint.textSize = resources.getDimension(R.dimen.key_text_size)
        hintTextPaint.textSize = resources.getDimension(R.dimen.key_subtext_size)

        val hasImageBackground = backgroundBitmap != null
        // A soft text shadow keeps key labels readable no matter what's directly behind them in
        // the photo (light sky, dark shadow, busy detail — all still legible with this).
        if (hasImageBackground) {
            keyTextPaint.setShadowLayer(3f, 0f, 1f, 0xB3000000.toInt())
        } else {
            keyTextPaint.clearShadowLayer()
        }

        for (entry in keyBounds) {
            val isSpecial = entry.key.type != KeyType.CHARACTER
            val basePaint = if (entry.key.isPrimary) keyPrimaryBgPaint else if (isSpecial) keySpecialBgPaint else keyBgPaint

            val isAnimatingThisKey = entry.key == animatingKey && (pressAlpha > 0f || pressScale != 0f)
            // The inset drives the "pushed in" press feel; on release pressScale briefly
            // overshoots slightly negative (via OvershootInterpolator), which pops the key
            // just past its resting size for an elastic bounce instead of a dead stop. Clamped
            // so that pop never eats into the (small) gap between adjacent keys.
            val inset = (pressScale * 2.5f).coerceAtLeast(-1f)
            val drawRect = if (isAnimatingThisKey) {
                RectF(entry.rect).apply { inset(inset, inset) }
            } else {
                entry.rect
            }

            if (!transparentKeysEnabled) {
                if (hasImageBackground) {
                    // A simple flat offset shadow (no blur — cheap, and renders identically on
                    // every API level) so each key visibly separates from the photo behind it.
                    val shadowRect = RectF(drawRect).apply { offset(0f, 2f) }
                    canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, keyShadowPaint)
                }
                canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, basePaint)

                if (keyBordersEnabled) {
                    canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, keyBorderPaint)
                }
            }

            if (isAnimatingThisKey && pressAlpha > 0f) {
                val overlay = if (entry.key.isPrimary) keyPrimaryBgPressedPaint else keyBgPressedPaint
                overlay.alpha = (pressAlpha * 255).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, overlay)
                overlay.alpha = 255
            }

            // Expanding ink ripple, clipped to this key's rounded bounds.
            if (rippleKey == entry.key && rippleProgress < 1f) {
                val maxRadius = hypot(entry.rect.width(), entry.rect.height()) / 2f
                val radius = maxRadius * rippleProgress
                canvas.save()
                val clip = Path().apply { addRoundRect(entry.rect, cornerRadius, cornerRadius, Path.Direction.CW) }
                canvas.clipPath(clip)
                ripplePaint.alpha = ((1f - rippleProgress) * RIPPLE_MAX_ALPHA).toInt().coerceIn(0, 255)
                canvas.drawCircle(rippleCenterX, rippleCenterY, radius, ripplePaint)
                canvas.restore()
            }

            val displayLabel = displayLabelFor(entry.key)
            keyTextPaint.color = when {
                entry.key.isPrimary && !transparentKeysEnabled -> currentTheme.background
                entry.key.isPrimary -> currentTheme.accent
                entry.key.type == KeyType.SHIFT && (capsLockActive || shiftActive) -> currentTheme.accent
                else -> currentTheme.keyText
            }

            keyTextPaint.getTextBounds(displayLabel, 0, displayLabel.length, textBounds)
            val cx = entry.rect.centerX()
            val cy = entry.rect.centerY() - textBounds.exactCenterY()
            canvas.drawText(displayLabel, cx, cy, keyTextPaint)

            // Small top-right corner hint (preview of the long-press character), letters only.
            if (!entry.key.isPrimary && entry.key.type == KeyType.CHARACTER && entry.key.hintLabel != null) {
                val hint = entry.key.hintLabel
                val hintX = entry.rect.right - (entry.rect.width() * 0.22f)
                val hintY = entry.rect.top + (entry.rect.height() * 0.30f)
                canvas.drawText(hint, hintX, hintY, hintTextPaint)
            }
        }

        // Enlarged key-preview bubble that rises above the finger while a character is held.
        if (previewEntry != null && previewAlpha > 0f && showingPopupFor == null && !isGesturing) {
            drawKeyPreview(canvas)
        }

        // Draw the live gesture trail while swiping.
        if (isGesturing && gesturePoints.size > 1) {
            val path = Path()
            path.moveTo(gesturePoints[0].cx, gesturePoints[0].cy)
            for (i in 1 until gesturePoints.size) path.lineTo(gesturePoints[i].cx, gesturePoints[i].cy)
            canvas.drawPath(path, gesturePathPaint)
        }

        // Draw long-press popup on top.
        showingPopupFor?.let { drawPopup(canvas, it) }
    }

    private fun displayLabelFor(key: KeyModel): String {
        if (key.type == KeyType.CHARACTER && key.label.length == 1 && key.label[0].isLetter()) {
            return if (shiftActive || capsLockActive) key.label.uppercase() else key.label
        }
        return when (key.type) {
            KeyType.SPACE -> ""
            KeyType.SHIFT -> if (capsLockActive) "⬆" else "⬆"
            else -> key.label
        }
    }

    /** Computes the popup's left edge, clamped so the whole popup (including the first option —
     *  always the key's own base character) stays fully on-screen. Without this, keys near the
     *  left/right edge (Q, A, Z / P, L, M) would render their popup partly off-canvas, silently
     *  clipping away whichever option landed outside the view's bounds. */
    private fun clampedPopupLeft(entry: KeyBoundsEntry, totalWidth: Float): Float {
        val ideal = entry.rect.centerX() - totalWidth / 2f
        val maxLeft = (width - totalWidth).coerceAtLeast(0f)
        return ideal.coerceIn(0f, maxLeft)
    }

    /** Draws the enlarged, fading-in bubble showing what's currently under the finger. */
    private fun drawKeyPreview(canvas: Canvas) {
        val entry = previewEntry ?: return
        val bubbleWidth = entry.rect.width() * 1.3f
        val bubbleHeight = entry.rect.height() * 1.6f
        val rise = (1f - previewAlpha) * 14f
        val bottom = entry.rect.top - 8f + rise
        val top = bottom - bubbleHeight
        val left = entry.rect.centerX() - bubbleWidth / 2f
        val rect = RectF(left, top, left + bubbleWidth, bottom)

        val alpha255 = (previewAlpha * 255).toInt().coerceIn(0, 255)
        popupBgPaint.alpha = alpha255
        canvas.drawRoundRect(rect, 14f, 14f, popupBgPaint)
        popupBgPaint.alpha = 255

        val label = displayLabelFor(entry.key)
        val previousTextSize = popupTextPaint.textSize
        popupTextPaint.textSize = resources.getDimension(R.dimen.key_text_size) * 1.3f
        popupTextPaint.color = currentTheme.keyText
        popupTextPaint.alpha = alpha255
        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2,
            popupTextPaint
        )
        popupTextPaint.alpha = 255
        popupTextPaint.textSize = previousTextSize
    }

    private fun drawPopup(canvas: Canvas, entry: KeyBoundsEntry) {
        val options = listOf(entry.key.label) + entry.key.longPressLabels
        if (options.size <= 1) return
        val optionWidth = entry.rect.width() * 1.2f
        val popupHeight = entry.rect.height() * 1.4f
        val totalWidth = optionWidth * options.size
        var left = clampedPopupLeft(entry, totalWidth)
        val top = entry.rect.top - popupHeight - 12f
        popupTextPaint.textSize = resources.getDimension(R.dimen.key_text_size)

        val bgRect = RectF(left, top, left + totalWidth, top + popupHeight)
        canvas.drawRoundRect(bgRect, 12f, 12f, popupBgPaint)

        for ((i, opt) in options.withIndex()) {
            val optRect = RectF(left, top, left + optionWidth, top + popupHeight)
            if (i == popupSelectedIndex) {
                canvas.drawRoundRect(optRect, 10f, 10f, keyBgPressedPaint)
            }
            popupTextPaint.color = currentTheme.keyText
            canvas.drawText(
                opt,
                optRect.centerX(),
                optRect.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2,
                popupTextPaint
            )
            left += optionWidth
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> resetTouchState()
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        downX = event.x
        downY = event.y
        longPressTriggered = false
        val entry = keyAt(event.x, event.y) ?: return
        pressedKey = entry.key
        pressedRect = entry.rect
        gestureStartKey = entry.key
        listener?.onKeyDownFeedback()
        startPressAnimation(entry, event.x, event.y)

        if (entry.key.type == KeyType.CHARACTER && entry.key.label.length == 1 && entry.key.label[0].isLetter()) {
            gesturePoints = mutableListOf(KeyPoint(entry.key.label[0], entry.rect.centerX(), entry.rect.centerY()))
        }

        if (entry.key.longPressLabels.isNotEmpty()) {
            val runnable = Runnable {
                longPressTriggered = true
                showingPopupFor = entry
                popupSelectedIndex = 0
                hidePreview()
                invalidate()
            }
            longPressRunnable = runnable
            postDelayed(runnable, LONG_PRESS_TIMEOUT_MS)
        }

        if (entry.key.type == KeyType.BACKSPACE) {
            val repeat = object : Runnable {
                override fun run() {
                    isBackspaceRepeating = true
                    listener?.onKeyTap(entry.key)
                    listener?.onKeyDownFeedback()
                    postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
                }
            }
            backspaceRepeatRunnable = repeat
            postDelayed(repeat, BACKSPACE_INITIAL_REPEAT_DELAY_MS)
        }
        invalidate()
    }

    private fun handleMove(event: MotionEvent) {
        val dx = abs(event.x - downX)
        val dy = abs(event.y - downY)

        if (showingPopupFor != null) {
            updatePopupSelection(event.x)
            return
        }

        // Cancel long-press if finger moves far enough (likely a swipe starting).
        if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
            longPressRunnable?.let { removeCallbacks(it) }
        }

        val startsOnLetter = gestureStartKey?.type == KeyType.CHARACTER
        if (swipeTypingEnabled && startsOnLetter && (dx > GESTURE_START_THRESHOLD || dy > GESTURE_START_THRESHOLD)) {
            if (!isGesturing) hidePreview()
            isGesturing = true
        }

        if (isGesturing) {
            val entry = keyAt(event.x, event.y)
            if (entry != null && entry.key.type == KeyType.CHARACTER && entry.key.label.length == 1) {
                val last = gesturePoints.lastOrNull()
                if (last == null || last.char != entry.key.label[0]) {
                    gesturePoints.add(KeyPoint(entry.key.label[0], event.x, event.y))
                }
            } else {
                gesturePoints.add(KeyPoint(' ', event.x, event.y))
            }
            invalidate()
        } else {
            // Update which key is visually "pressed" as the finger drags (without lifting),
            // restarting the smooth press animation for whichever key it lands on.
            val entry = keyAt(event.x, event.y)
            if (entry != null && entry.key != pressedKey) {
                pressedKey = entry.key
                pressedRect = entry.rect
                startPressAnimation(entry, event.x, event.y)
                invalidate()
            }
        }
    }

    private fun handleUp(event: MotionEvent) {
        longPressRunnable?.let { removeCallbacks(it) }
        backspaceRepeatRunnable?.let { removeCallbacks(it) }
        backspaceRepeatRunnable = null

        if (showingPopupFor != null) {
            val entry = showingPopupFor!!
            val options = listOf(entry.key.label) + entry.key.longPressLabels
            val chosen = options.getOrNull(popupSelectedIndex).takeIf { popupSelectedIndex >= 0 } ?: entry.key.label
            listener?.onKeyLongPressChar(chosen)
            resetTouchState()
            return
        }

        if (isGesturing && gesturePoints.size > 3) {
            listener?.onGestureTypingResult(gesturePoints, currentKeyLayoutMap())
            resetTouchState()
            return
        }

        // If backspace already auto-repeated while held, the release itself shouldn't also
        // fire a normal tap (that would delete one extra character).
        if (!longPressTriggered && !isBackspaceRepeating) {
            val entry = keyAt(event.x, event.y) ?: keyAt(downX, downY)
            entry?.let { listener?.onKeyTap(it.key) }
        }
        resetTouchState()
    }

    private fun updatePopupSelection(x: Float) {
        val entry = showingPopupFor ?: return
        val options = listOf(entry.key.label) + entry.key.longPressLabels
        val optionWidth = entry.rect.width() * 1.2f
        val totalWidth = optionWidth * options.size
        val left = clampedPopupLeft(entry, totalWidth)
        val relativeX = x - left
        val index = (relativeX / optionWidth).toInt().coerceIn(0, options.size - 1)
        if (index != popupSelectedIndex) {
            popupSelectedIndex = index
            invalidate()
        }
    }

    private fun resetTouchState() {
        pressedKey = null
        pressedRect = null
        showingPopupFor = null
        popupSelectedIndex = -1
        isGesturing = false
        gesturePoints = mutableListOf()
        gestureStartKey = null
        backspaceRepeatRunnable?.let { removeCallbacks(it) }
        backspaceRepeatRunnable = null
        isBackspaceRepeating = false
        endPressAnimation()
        invalidate()
    }

    private fun keyAt(x: Float, y: Float): KeyBoundsEntry? =
        keyBounds.firstOrNull { it.rect.contains(x, y) }

    companion object {
        private const val LONG_PRESS_TIMEOUT_MS = 350L
        private const val TOUCH_SLOP = 16f
        private const val GESTURE_START_THRESHOLD = 40f
        private const val SCRIM_COLOR = 0x26000000 // ~15% black — just enough to steady contrast, not hide the photo
        private const val BACKSPACE_INITIAL_REPEAT_DELAY_MS = 400L
        private const val BACKSPACE_REPEAT_INTERVAL_MS = 60L
        private const val PRESS_FADE_IN_MS = 55L
        private const val PRESS_FADE_OUT_MS = 110L
        private const val PRESS_SCALE_IN_MS = 55L
        private const val PRESS_SCALE_OUT_MS = 200L
        private const val BOUNCE_TENSION = 3.5f
        private const val RIPPLE_DURATION_MS = 260L
        private const val RIPPLE_MAX_ALPHA = 70
        private const val PREVIEW_FADE_IN_MS = 60L
        private const val PREVIEW_FADE_OUT_MS = 90L
    }
}
