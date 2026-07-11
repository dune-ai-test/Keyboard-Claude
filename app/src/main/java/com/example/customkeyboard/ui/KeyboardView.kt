package com.example.customkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.customkeyboard.R
import com.example.customkeyboard.data.KeyModel
import com.example.customkeyboard.data.KeyType
import com.example.customkeyboard.data.KeyboardLayout
import com.example.customkeyboard.gesture.KeyPoint
import kotlin.math.abs

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

    // --- Paint objects (allocated once) ---
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySpecialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val gesturePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textBounds = Rect()
    private val keyBounds = mutableListOf<KeyBoundsEntry>()

    private data class KeyBoundsEntry(val key: KeyModel, val rect: RectF)

    private var pressedKey: KeyModel? = null
    private var pressedRect: RectF? = null

    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var showingPopupFor: KeyBoundsEntry? = null
    private var popupSelectedIndex = -1

    // --- Gesture typing state ---
    private var isGesturing = false
    private var gesturePoints = mutableListOf<KeyPoint>()
    private var gestureStartKey: KeyModel? = null
    private var downX = 0f
    private var downY = 0f

    init {
        applyThemeColors()
    }

    fun applyThemeColors() {
        keyBgPaint.color = ContextCompat.getColor(context, R.color.kb_key_background)
        keyBgPressedPaint.color = ContextCompat.getColor(context, R.color.kb_key_background_pressed)
        keySpecialBgPaint.color = ContextCompat.getColor(context, R.color.kb_key_special_background)
        keyTextPaint.color = ContextCompat.getColor(context, R.color.kb_key_text)
        popupBgPaint.color = ContextCompat.getColor(context, R.color.kb_popup_background)
        popupTextPaint.color = ContextCompat.getColor(context, R.color.kb_key_text)
        gesturePathPaint.color = ContextCompat.getColor(context, R.color.kb_accent)
        setBackgroundColor(ContextCompat.getColor(context, R.color.kb_background))
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyBounds()
    }

    private fun computeKeyBounds() {
        keyBounds.clear()
        val layout = layoutModel ?: return
        if (width == 0 || height == 0) return

        val rows = layout.rows
        val rowHeight = height.toFloat() / rows.size
        val padding = resources.getDimension(R.dimen.keyboard_padding)

        for ((rowIndex, row) in rows.withIndex()) {
            val totalWeight = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
            var xCursor = 0f
            val top = rowIndex * rowHeight + padding
            val bottom = (rowIndex + 1) * rowHeight - padding
            for (key in row.keys) {
                val keyWidth = (width.toFloat() / totalWeight) * key.widthWeight
                val rect = RectF(xCursor + padding, top, xCursor + keyWidth - padding, bottom)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = resources.getDimension(R.dimen.key_corner_radius)
        keyTextPaint.textSize = resources.getDimension(R.dimen.key_text_size)

        for (entry in keyBounds) {
            val isSpecial = entry.key.type != KeyType.CHARACTER
            val paint = when {
                entry.key == pressedKey -> keyBgPressedPaint
                isSpecial -> keySpecialBgPaint
                else -> keyBgPaint
            }
            canvas.drawRoundRect(entry.rect, cornerRadius, cornerRadius, paint)

            val displayLabel = displayLabelFor(entry.key)
            if (entry.key.type == KeyType.SHIFT) {
                keyTextPaint.color = if (capsLockActive || shiftActive)
                    ContextCompat.getColor(context, R.color.kb_accent)
                else
                    ContextCompat.getColor(context, R.color.kb_key_text)
            } else {
                keyTextPaint.color = ContextCompat.getColor(context, R.color.kb_key_text)
            }

            keyTextPaint.getTextBounds(displayLabel, 0, displayLabel.length, textBounds)
            val cx = entry.rect.centerX()
            val cy = entry.rect.centerY() - textBounds.exactCenterY()
            canvas.drawText(displayLabel, cx, cy, keyTextPaint)
        }

        // Draw the live gesture trail while swiping.
        if (isGesturing && gesturePoints.size > 1) {
            val path = android.graphics.Path()
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
            KeyType.SHIFT -> if (capsLockActive) "⇪" else "⇧"
            else -> key.label
        }
    }

    private fun drawPopup(canvas: Canvas, entry: KeyBoundsEntry) {
        val options = listOf(entry.key.label) + entry.key.longPressLabels
        if (options.size <= 1) return
        val optionWidth = entry.rect.width() * 1.2f
        val popupHeight = entry.rect.height() * 1.4f
        val totalWidth = optionWidth * options.size
        var left = entry.rect.centerX() - totalWidth / 2f
        val top = entry.rect.top - popupHeight - 12f
        popupTextPaint.textSize = resources.getDimension(R.dimen.key_text_size)

        val bgRect = RectF(left, top, left + totalWidth, top + popupHeight)
        canvas.drawRoundRect(bgRect, 12f, 12f, popupBgPaint)

        for ((i, opt) in options.withIndex()) {
            val optRect = RectF(left, top, left + optionWidth, top + popupHeight)
            if (i == popupSelectedIndex) {
                canvas.drawRoundRect(optRect, 10f, 10f, keyBgPressedPaint)
            }
            popupTextPaint.color = ContextCompat.getColor(context, R.color.kb_key_text)
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

        if (entry.key.type == KeyType.CHARACTER && entry.key.label.length == 1 && entry.key.label[0].isLetter()) {
            gesturePoints = mutableListOf(KeyPoint(entry.key.label[0], entry.rect.centerX(), entry.rect.centerY()))
        }

        if (entry.key.longPressLabels.isNotEmpty()) {
            val runnable = Runnable {
                longPressTriggered = true
                showingPopupFor = entry
                popupSelectedIndex = 0
                invalidate()
            }
            longPressRunnable = runnable
            postDelayed(runnable, LONG_PRESS_TIMEOUT_MS)
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
            // Update which key is visually "pressed" as the finger drags (without lifting).
            val entry = keyAt(event.x, event.y)
            if (entry != null && entry.key != pressedKey) {
                pressedKey = entry.key
                pressedRect = entry.rect
                invalidate()
            }
        }
    }

    private fun handleUp(event: MotionEvent) {
        longPressRunnable?.let { removeCallbacks(it) }

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

        if (!longPressTriggered) {
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
        val left = entry.rect.centerX() - totalWidth / 2f
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
        invalidate()
    }

    private fun keyAt(x: Float, y: Float): KeyBoundsEntry? =
        keyBounds.firstOrNull { it.rect.contains(x, y) }

    companion object {
        private const val LONG_PRESS_TIMEOUT_MS = 350L
        private const val TOUCH_SLOP = 16f
        private const val GESTURE_START_THRESHOLD = 40f
    }
}
