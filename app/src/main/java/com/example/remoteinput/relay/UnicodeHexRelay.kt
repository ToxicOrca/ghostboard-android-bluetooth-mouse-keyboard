package com.example.remoteinput.relay

import com.example.remoteinput.ui.CompactKeyboardView.HidKeyCodes
import com.example.remoteinput.ui.HidKeyMapper

/**
 * 「原样直发」规划器 —— 基于 macOS 自带的「Unicode 十六进制输入」输入法。
 *
 * 背景：蓝牙 HID 是 Boot Keyboard，协议层只能送 keycode，**没有**任何 Unicode 通道，
 * 所以"原样发送中文"没法直接做。但 macOS 内置了一个后门：
 *
 *   > 添加「Unicode 十六进制输入」输入源后，**按住 Option 依次敲 4 位十六进制码位**，
 *   > 松开 Option 的瞬间立即插入对应字符。
 *
 * 该输入法内部是 UTF-16BE 语义，所以：
 *   - 基本多文种平面（BMP）字符  -> 1 段 4 位码
 *   - 补充平面字符（emoji / 生僻字）-> 自动拆成 2 段 4 位码（代理对），连续敲即可
 *
 * 由于 Kotlin 的 [Char] 本身就是 UTF-16 码元，逐 Char 取码即可，代理对天然相邻。
 *
 * 这条路**不依赖受控端中文输入法**、不做拼音猜测，字符原样送达；
 * 代价是受控端必须把输入源切到「Unicode 十六进制输入」。
 */
object UnicodeHexRelay {

    /**
     * 一段「按住修饰键连续敲键」的按键组。
     * 组内所有键码都在 [modifier] 按住的状态下依次敲出，组末松开修饰键触发提交。
     */
    data class Group(val modifier: Int, val keys: List<Int>)

    /** macOS 的 Option 键。 */
    private const val OPTION = HidKeyCodes.MOD_LALT

    /** 文本 -> 按键组序列。无法映射的字符会被跳过。 */
    fun plan(text: CharSequence): List<Group> {
        val out = ArrayList<Group>(text.length)
        for (c in text) {
            when (c) {
                '\n', '\r' -> out.add(Group(0, listOf(HidKeyCodes.KEY_ENTER)))
                '\t' -> out.add(Group(0, listOf(HidKeyCodes.KEY_TAB)))
                else -> {
                    val keys = hexKeysOf(c) ?: continue
                    out.add(Group(OPTION, keys))
                }
            }
        }
        return out
    }

    /**
     * 单字符 -> 4 位十六进制对应的 HID 键码。
     * 用 `%04x` 得到小写十六进制，这样经 [HidKeyMapper] 映射出来的修饰键为 0
     * —— 大写字母在映射表里会带上 Shift，会把 Option 组合键搞坏。
     */
    private fun hexKeysOf(c: Char): List<Int>? {
        val hex = "%04x".format(c.code)
        val keys = ArrayList<Int>(hex.length)
        for (h in hex) {
            val mapped = HidKeyMapper.mapChar(h) ?: return null
            keys.add(mapped.keyCode)
        }
        return keys
    }

    /** 预览文案，例如 `U+4F60 U+597D`。 */
    fun preview(text: CharSequence): String = buildString {
        for (c in text) {
            when (c) {
                '\n', '\r' -> append("⏎ ")
                '\t' -> append("⇥ ")
                else -> append("U+").append("%04X".format(c.code)).append(' ')
            }
        }
    }.trim()

    /** 预估按键总数（用于耗时提示）。 */
    fun keyCount(text: CharSequence): Int = plan(text).sumOf { it.keys.size }

    /** 按键组数量（= 实际会提交的字符数，用于更准的耗时预估）。 */
    fun groupCount(text: CharSequence): Int = plan(text).size
}
