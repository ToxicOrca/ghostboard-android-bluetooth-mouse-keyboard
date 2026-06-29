package com.example.remoteinput.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.remoteinput.R

class CompactKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // USB HID key codes
    object HidKeyCodes {
        const val KEY_A = 0x04
        const val KEY_B = 0x05
        const val KEY_C = 0x06
        const val KEY_D = 0x07
        const val KEY_E = 0x08
        const val KEY_F = 0x09
        const val KEY_G = 0x0A
        const val KEY_H = 0x0B
        const val KEY_I = 0x0C
        const val KEY_J = 0x0D
        const val KEY_K = 0x0E
        const val KEY_L = 0x0F
        const val KEY_M = 0x10
        const val KEY_N = 0x11
        const val KEY_O = 0x12
        const val KEY_P = 0x13
        const val KEY_Q = 0x14
        const val KEY_R = 0x15
        const val KEY_S = 0x16
        const val KEY_T = 0x17
        const val KEY_U = 0x18
        const val KEY_V = 0x19
        const val KEY_W = 0x1A
        const val KEY_X = 0x1B
        const val KEY_Y = 0x1C
        const val KEY_Z = 0x1D
        const val KEY_1 = 0x1E
        const val KEY_2 = 0x1F
        const val KEY_3 = 0x20
        const val KEY_4 = 0x21
        const val KEY_5 = 0x22
        const val KEY_6 = 0x23
        const val KEY_7 = 0x24
        const val KEY_8 = 0x25
        const val KEY_9 = 0x26
        const val KEY_0 = 0x27
        const val KEY_ENTER = 0x28
        const val KEY_ESCAPE = 0x29
        const val KEY_BACKSPACE = 0x2A
        const val KEY_TAB = 0x2B
        const val KEY_SPACE = 0x2C
        const val KEY_MINUS = 0x2D
        const val KEY_EQUAL = 0x2E
        const val KEY_LEFT_BRACKET = 0x2F
        const val KEY_RIGHT_BRACKET = 0x30
        const val KEY_SEMICOLON = 0x33
        const val KEY_APOSTROPHE = 0x34
        const val KEY_GRAVE = 0x35
        const val KEY_COMMA = 0x36
        const val KEY_PERIOD = 0x37
        const val KEY_SLASH = 0x38
        const val KEY_DELETE = 0x4C
        const val KEY_RIGHT = 0x4F
        const val KEY_LEFT = 0x50
        const val KEY_DOWN = 0x51
        const val KEY_UP = 0x52

        // Modifiers (bit flags)
        const val MOD_LCTRL = 0x01
        const val MOD_LSHIFT = 0x02
        const val MOD_LALT = 0x04
        const val MOD_LGUI = 0x08  // Windows/Super key
    }

    data class Key(
        val label: String,
        val shiftLabel: String = label.uppercase(),
        val keyCode: Int,
        val modifier: Int = 0,
        val widthMultiplier: Float = 1f,
        val isModifier: Boolean = false,
        val isToggle: Boolean = false
    )

    interface KeyboardListener {
        fun onKeyPress(modifier: Int, keyCode: Int)
        fun onKeyDown(modifier: Int, keyCode: Int)
        fun onKeyUp()
    }

    var listener: KeyboardListener? = null

    // Modifier state
    private var shiftActive = false
    private var ctrlActive = false
    private var altActive = false
    private var winActive = false

    // Track whether a non-modifier key was pressed while modifier was held
    // Used to detect standalone modifier taps (e.g. Win alone opens Start menu)
    private var modifierUsedWithKey = false

    // Keyboard layout - 5 rows
    private val rows: List<List<Key>> = listOf(
        // Row 1: numbers
        listOf(
            Key("`", "~", HidKeyCodes.KEY_GRAVE),
            Key("1", "!", HidKeyCodes.KEY_1),
            Key("2", "@", HidKeyCodes.KEY_2),
            Key("3", "#", HidKeyCodes.KEY_3),
            Key("4", "$", HidKeyCodes.KEY_4),
            Key("5", "%", HidKeyCodes.KEY_5),
            Key("6", "^", HidKeyCodes.KEY_6),
            Key("7", "&", HidKeyCodes.KEY_7),
            Key("8", "*", HidKeyCodes.KEY_8),
            Key("9", "(", HidKeyCodes.KEY_9),
            Key("0", ")", HidKeyCodes.KEY_0),
            Key("-", "_", HidKeyCodes.KEY_MINUS),
            Key("Bksp", "Bksp", HidKeyCodes.KEY_BACKSPACE, widthMultiplier = 1.3f),
        ),
        // Row 2: QWERTY top
        listOf(
            Key("Tab", "Tab", HidKeyCodes.KEY_TAB, widthMultiplier = 1.3f),
            Key("q", "Q", HidKeyCodes.KEY_Q),
            Key("w", "W", HidKeyCodes.KEY_W),
            Key("e", "E", HidKeyCodes.KEY_E),
            Key("r", "R", HidKeyCodes.KEY_R),
            Key("t", "T", HidKeyCodes.KEY_T),
            Key("y", "Y", HidKeyCodes.KEY_Y),
            Key("u", "U", HidKeyCodes.KEY_U),
            Key("i", "I", HidKeyCodes.KEY_I),
            Key("o", "O", HidKeyCodes.KEY_O),
            Key("p", "P", HidKeyCodes.KEY_P),
            Key("[", "{", HidKeyCodes.KEY_LEFT_BRACKET),
            Key("]", "}", HidKeyCodes.KEY_RIGHT_BRACKET),
        ),
        // Row 3: ASDF middle
        listOf(
            Key("Esc", "Esc", HidKeyCodes.KEY_ESCAPE, widthMultiplier = 1.3f),
            Key("a", "A", HidKeyCodes.KEY_A),
            Key("s", "S", HidKeyCodes.KEY_S),
            Key("d", "D", HidKeyCodes.KEY_D),
            Key("f", "F", HidKeyCodes.KEY_F),
            Key("g", "G", HidKeyCodes.KEY_G),
            Key("h", "H", HidKeyCodes.KEY_H),
            Key("j", "J", HidKeyCodes.KEY_J),
            Key("k", "K", HidKeyCodes.KEY_K),
            Key("l", "L", HidKeyCodes.KEY_L),
            Key(";", ":", HidKeyCodes.KEY_SEMICOLON),
            Key("'", "\"", HidKeyCodes.KEY_APOSTROPHE),
            Key("Enter", "Enter", HidKeyCodes.KEY_ENTER, widthMultiplier = 1.3f),
        ),
        // Row 4: ZXCV bottom
        listOf(
            Key("Shift", "Shift", 0, isModifier = true, isToggle = true, widthMultiplier = 1.8f),
            Key("z", "Z", HidKeyCodes.KEY_Z),
            Key("x", "X", HidKeyCodes.KEY_X),
            Key("c", "C", HidKeyCodes.KEY_C),
            Key("v", "V", HidKeyCodes.KEY_V),
            Key("b", "B", HidKeyCodes.KEY_B),
            Key("n", "N", HidKeyCodes.KEY_N),
            Key("m", "M", HidKeyCodes.KEY_M),
            Key(",", "<", HidKeyCodes.KEY_COMMA),
            Key(".", ">", HidKeyCodes.KEY_PERIOD),
            Key("/", "?", HidKeyCodes.KEY_SLASH),
            Key("Del", "Del", HidKeyCodes.KEY_DELETE, widthMultiplier = 1.5f),
        ),
        // Row 5: modifiers and space
        listOf(
            Key("Ctrl", "Ctrl", 0, isModifier = true, isToggle = true, widthMultiplier = 1.5f),
            Key("Win", "Win", 0, isModifier = true, isToggle = true, widthMultiplier = 1.2f),
            Key("Alt", "Alt", 0, isModifier = true, isToggle = true, widthMultiplier = 1.2f),
            Key("Space", "Space", HidKeyCodes.KEY_SPACE, widthMultiplier = 4f),
            Key("\u2190", "\u2190", HidKeyCodes.KEY_LEFT),
            Key("\u2193", "\u2193", HidKeyCodes.KEY_DOWN),
            Key("\u2191", "\u2191", HidKeyCodes.KEY_UP),
            Key("\u2192", "\u2192", HidKeyCodes.KEY_RIGHT),
        )
    )

    // Cached key rects for hit testing
    private data class KeyRect(val key: Key, val rect: RectF)
    private var keyRects = mutableListOf<KeyRect>()

    private var pressedKey: Key? = null
    private val density = resources.displayMetrics.density
    private val keyPadding = 2f * density
    private val keyRadius = 4f * density

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_bg)
        style = Paint.Style.FILL
    }

    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_pressed)
        style = Paint.Style.FILL
    }

    private val keyActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.FILL
    }

    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.key_text)
        textAlign = Paint.Align.CENTER
    }

    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    private fun layoutKeys() {
        keyRects.clear()
        val rowHeight = height.toFloat() / rows.size

        for ((rowIndex, row) in rows.withIndex()) {
            val totalWeight = row.sumOf { it.widthMultiplier.toDouble() }.toFloat()
            val unitWidth = width.toFloat() / totalWeight
            var x = 0f
            val y = rowIndex * rowHeight

            for (key in row) {
                val keyWidth = unitWidth * key.widthMultiplier
                val rect = RectF(
                    x + keyPadding,
                    y + keyPadding,
                    x + keyWidth - keyPadding,
                    y + rowHeight - keyPadding
                )
                keyRects.add(KeyRect(key, rect))
                x += keyWidth
            }
        }

        // Set text sizes based on key height
        keyTextPaint.textSize = rowHeight * 0.35f
        smallTextPaint.textSize = rowHeight * 0.25f
    }

    override fun onDraw(canvas: Canvas) {
        for ((key, rect) in keyRects) {
            // Determine background
            val paint = when {
                key == pressedKey -> keyPressedPaint
                key.isToggle && isModifierActive(key) -> keyActivePaint
                else -> keyBgPaint
            }
            canvas.drawRoundRect(rect, keyRadius, keyRadius, paint)

            // Draw label
            val label = if (shiftActive && !key.isModifier) key.shiftLabel else key.label
            val textY = rect.centerY() + keyTextPaint.textSize / 3f
            canvas.drawText(label, rect.centerX(), textY, keyTextPaint)
        }
    }

    private fun isModifierActive(key: Key): Boolean {
        return when (key.label) {
            "Shift" -> shiftActive
            "Ctrl" -> ctrlActive
            "Alt" -> altActive
            "Win" -> winActive
            else -> false
        }
    }

    private fun getActiveModifiers(): Int {
        var mod = 0
        if (shiftActive) mod = mod or HidKeyCodes.MOD_LSHIFT
        if (ctrlActive) mod = mod or HidKeyCodes.MOD_LCTRL
        if (altActive) mod = mod or HidKeyCodes.MOD_LALT
        if (winActive) mod = mod or HidKeyCodes.MOD_LGUI
        return mod
    }

    private fun findKeyAt(x: Float, y: Float): Key? {
        for ((key, rect) in keyRects) {
            if (rect.contains(x, y)) return key
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val key = findKeyAt(event.getX(idx), event.getY(idx)) ?: return true
                pressedKey = key

                if (key.isToggle) {
                    val wasActive = isModifierActive(key)
                    // Toggle modifier
                    when (key.label) {
                        "Shift" -> shiftActive = !shiftActive
                        "Ctrl" -> ctrlActive = !ctrlActive
                        "Alt" -> altActive = !altActive
                        "Win" -> winActive = !winActive
                    }

                    if (!wasActive) {
                        // Modifier just turned ON — reset the "used with key" flag
                        modifierUsedWithKey = false
                    } else {
                        // Modifier toggled OFF — if no key was pressed while it was
                        // active, send a standalone modifier press (e.g. Win alone)
                        if (!modifierUsedWithKey) {
                            val modBit = when (key.label) {
                                "Shift" -> HidKeyCodes.MOD_LSHIFT
                                "Ctrl" -> HidKeyCodes.MOD_LCTRL
                                "Alt" -> HidKeyCodes.MOD_LALT
                                "Win" -> HidKeyCodes.MOD_LGUI
                                else -> 0
                            }
                            if (modBit != 0) {
                                listener?.onKeyPress(modBit, 0)
                            }
                        }
                    }
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val key = pressedKey ?: return true
                pressedKey = null

                if (!key.isModifier) {
                    modifierUsedWithKey = true
                    val mod = getActiveModifiers()
                    listener?.onKeyPress(mod, key.keyCode)

                    // Auto-release one-shot modifiers after key press
                    if (shiftActive) { shiftActive = false }
                    if (ctrlActive) { ctrlActive = false }
                    if (altActive) { altActive = false }
                    if (winActive) { winActive = false }
                }

                invalidate()
                return true
            }
        }
        return true
    }
}
