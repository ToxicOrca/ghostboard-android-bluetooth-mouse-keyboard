package com.example.remoteinput.ui

import com.example.remoteinput.ui.CompactKeyboardView.HidKeyCodes

/**
 * Maps characters from the soft keyboard to HID keycode + modifier pairs.
 */
object HidKeyMapper {

    data class HidKeyEvent(val modifier: Int, val keyCode: Int)

    private val charMap = HashMap<Char, HidKeyEvent>().apply {
        // Lowercase letters
        for (c in 'a'..'z') {
            put(c, HidKeyEvent(0, HidKeyCodes.KEY_A + (c - 'a')))
        }
        // Uppercase letters (shift + letter)
        for (c in 'A'..'Z') {
            put(c, HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_A + (c - 'A')))
        }
        // Numbers
        put('1', HidKeyEvent(0, HidKeyCodes.KEY_1))
        put('2', HidKeyEvent(0, HidKeyCodes.KEY_2))
        put('3', HidKeyEvent(0, HidKeyCodes.KEY_3))
        put('4', HidKeyEvent(0, HidKeyCodes.KEY_4))
        put('5', HidKeyEvent(0, HidKeyCodes.KEY_5))
        put('6', HidKeyEvent(0, HidKeyCodes.KEY_6))
        put('7', HidKeyEvent(0, HidKeyCodes.KEY_7))
        put('8', HidKeyEvent(0, HidKeyCodes.KEY_8))
        put('9', HidKeyEvent(0, HidKeyCodes.KEY_9))
        put('0', HidKeyEvent(0, HidKeyCodes.KEY_0))
        // Shifted number symbols
        put('!', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_1))
        put('@', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_2))
        put('#', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_3))
        put('$', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_4))
        put('%', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_5))
        put('^', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_6))
        put('&', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_7))
        put('*', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_8))
        put('(', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_9))
        put(')', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_0))
        // Punctuation (unshifted)
        put('-', HidKeyEvent(0, HidKeyCodes.KEY_MINUS))
        put('=', HidKeyEvent(0, HidKeyCodes.KEY_EQUAL))
        put('[', HidKeyEvent(0, HidKeyCodes.KEY_LEFT_BRACKET))
        put(']', HidKeyEvent(0, HidKeyCodes.KEY_RIGHT_BRACKET))
        put('\\', HidKeyEvent(0, HidKeyCodes.KEY_BACKSLASH))
        put(';', HidKeyEvent(0, HidKeyCodes.KEY_SEMICOLON))
        put('\'', HidKeyEvent(0, HidKeyCodes.KEY_APOSTROPHE))
        put('`', HidKeyEvent(0, HidKeyCodes.KEY_GRAVE))
        put(',', HidKeyEvent(0, HidKeyCodes.KEY_COMMA))
        put('.', HidKeyEvent(0, HidKeyCodes.KEY_PERIOD))
        put('/', HidKeyEvent(0, HidKeyCodes.KEY_SLASH))
        // Punctuation (shifted)
        put('_', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_MINUS))
        put('+', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_EQUAL))
        put('{', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_LEFT_BRACKET))
        put('}', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_RIGHT_BRACKET))
        put('|', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_BACKSLASH))
        put(':', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_SEMICOLON))
        put('"', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_APOSTROPHE))
        put('~', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_GRAVE))
        put('<', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_COMMA))
        put('>', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_PERIOD))
        put('?', HidKeyEvent(HidKeyCodes.MOD_LSHIFT, HidKeyCodes.KEY_SLASH))
        // Whitespace
        put(' ', HidKeyEvent(0, HidKeyCodes.KEY_SPACE))
        put('\t', HidKeyEvent(0, HidKeyCodes.KEY_TAB))
        put('\n', HidKeyEvent(0, HidKeyCodes.KEY_ENTER))
    }

    fun mapChar(c: Char): HidKeyEvent? = charMap[c]
}
