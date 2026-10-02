package com.example.remoteinput.relay

import com.example.remoteinput.ui.CompactKeyboardView.HidKeyCodes
import com.example.remoteinput.ui.HidKeyMapper

/** 中继发送模式。 */
enum class RelayMode(val label: String, val desc: String) {
    /**
     * 拼音逐字：每个汉字单独打拼音 + Space 上屏。
     *
     * ⚠️ 发出去的是**全拼**，所以受控端输入法必须也是**全拼**。
     * 如果受控端用的是双拼（小鹤/自然码等），这些字母会被解析成完全不同的音节，
     * 输入法不上屏，屏幕上只会剩一串生字母。
     */
    PINYIN_PER_CHAR("拼音·逐字上屏", "逐字打全拼+空格；受控端须为全拼输入法，双拼会解析错"),

    /** 拼音整句：连续汉字合并成一串拼音，整段打完再敲一次空格。同样要求受控端是全拼。 */
    PINYIN_BATCH("拼音·整句连打", "合并成全拼串末尾敲一次空格；受控端须为全拼输入法"),

    /**
     * Unicode 原样直发：按码点逐字送，不做任何拼音转换。
     * 受控端需切到 macOS「Unicode 十六进制输入」输入源 —— 按住 Option 敲 4 位码位即插入字符。
     * 这是唯一能"原样"送达任意字符（含中文/emoji）的纯 HID 路径，且不依赖受控端中文输入法。
     *
     * 因为全程不经过任何拼音，**受控端用全拼还是双拼都无所谓**。
     */
    UNICODE_HEX("Unicode·原样直发", "按码点逐字发送，受控端切到「Unicode 十六进制输入」；与双拼/全拼无关"),

    /** 直通：不做任何转换，按 HID 键码原样发送（等价于原版行为）。 */
    LITERAL("直通·原样按键", "不转换，字符直接映射 HID 键码（英文场景）")
}

/** 剪贴板粘贴宏的目标平台，决定用 Cmd+V 还是 Ctrl+V。 */
enum class PasteTarget(val label: String, val modifier: Int) {
    MAC("macOS (Cmd+V)", HidKeyCodes.MOD_LGUI),
    WINDOWS("Windows / Android (Ctrl+V)", HidKeyCodes.MOD_LCTRL)
}

/**
 * 纯函数式的「文本 -> HID 按键序列」规划器。
 *
 * 不做任何 IO / 线程操作，便于单元测试；真正的时序发送交给
 * [com.example.remoteinput.bluetooth.BluetoothHidManager.sendKeySequence]。
 */
object RelayPlanner {

    /** 一个按键动作：修饰键掩码 + HID 键码。 */
    data class Step(val modifier: Int, val keyCode: Int)

    private val SPACE = Step(0, HidKeyCodes.KEY_SPACE)
    private val ENTER = Step(0, HidKeyCodes.KEY_ENTER)
    private val TAB = Step(0, HidKeyCodes.KEY_TAB)

    /** 中文全角标点 -> 半角 ASCII，避免受控端输入法把全角标点当成未识别字符。 */
    private val CJK_PUNCT: Map<Char, Char> = mapOf(
        '，' to ',', '。' to '.', '、' to ',', '；' to ';', '：' to ':',
        '？' to '?', '！' to '!', '“' to '"', '”' to '"', '‘' to '\'', '’' to '\'',
        '（' to '(', '）' to ')', '【' to '[', '】' to ']', '《' to '<', '》' to '>',
        '〔' to '[', '〕' to ']', '「' to '[', '」' to ']', '『' to '[', '』' to ']',
        '—' to '-', '－' to '-', '～' to '~', '％' to '%', '＃' to '#', '＠' to '@',
        '＆' to '&', '＊' to '*', '＋' to '+', '＝' to '=', '／' to '/', '＼' to '\\',
        '｜' to '|', '＜' to '<', '＞' to '>', '　' to ' ', '·' to '.', '…' to '.'
    )

    /**
     * 生成发送计划。
     *
     * 示例：plan("你好 world", PINYIN_PER_CHAR)
     *   -> N I H A O <Space> N I H A O <Space> <Space> W O R L D
     *      （"你"+"好" 各自上屏，原文空格原样发送）
     */
    fun plan(text: CharSequence, mode: RelayMode): List<Step> {
        val steps = ArrayList<Step>(text.length * 4)
        when (mode) {
            RelayMode.LITERAL -> appendLiteral(text, steps)
            RelayMode.PINYIN_PER_CHAR -> appendPinyin(text, steps, perChar = true)
            RelayMode.PINYIN_BATCH -> appendPinyin(text, steps, perChar = false)
            // Unicode 直发不是「键码序列」模型（修饰键要跨键保持），
            // 由 [UnicodeHexRelay] 单独规划、[BluetoothHidManager.sendHeldGroups] 发送。
            RelayMode.UNICODE_HEX -> Unit
        }
        return steps
    }

    /** 预估发送耗时（毫秒），用于 UI 提示。 */
    fun estimateDurationMs(stepCount: Int, holdMs: Long, gapMs: Long): Long =
        stepCount.toLong() * (holdMs + gapMs)

    // ------------------------------------------------------------------

    private fun appendLiteral(text: CharSequence, out: MutableList<Step>) {
        for (c in text) {
            mapSingleChar(c, out)?.let { out.add(it) }
        }
    }

    private fun appendPinyin(text: CharSequence, out: MutableList<Step>, perChar: Boolean) {
        val run = StringBuilder()

        fun flushRun() {
            if (run.isEmpty()) return
            appendLetters(run.toString(), out)
            out.add(SPACE)          // 触发受控端输入法候选上屏
            run.setLength(0)
        }

        for (c in text) {
            val pinyin = PinyinRelay.pinyinOf(c)
            if (pinyin != null) {
                if (perChar) {
                    appendLetters(pinyin, out)
                    out.add(SPACE)
                } else {
                    run.append(pinyin)
                }
            } else {
                if (!perChar) flushRun()
                mapSingleChar(c, out)?.let { out.add(it) }
            }
        }
        if (!perChar) flushRun()
    }

    /** 拼音字母流 -> 逐字母按键（小写，无 Shift）。 */
    private fun appendLetters(letters: String, out: MutableList<Step>) {
        for (ch in letters) {
            val mapped = HidKeyMapper.mapChar(ch) ?: continue
            out.add(Step(mapped.modifier, mapped.keyCode))
        }
    }

    /** 单个非汉字字符 -> 按键；无法映射时返回 null。 */
    private fun mapSingleChar(c: Char, out: MutableList<Step>): Step? {
        when (c) {
            '\n', '\r' -> return ENTER
            '\t' -> return TAB
        }
        val mapped = HidKeyMapper.mapChar(normalize(c)) ?: return null
        return Step(mapped.modifier, mapped.keyCode)
    }

    /** 全角字符归一化为半角；中文标点查表转 ASCII。 */
    fun normalize(c: Char): Char {
        // 全角 ASCII（！ 到 ～）整体偏移 0xFEE0
        if (c.code in 0xFF01..0xFF5E) return (c.code - 0xFEE0).toChar()
        if (c.code == 0x3000) return ' '   // 全角空格
        return CJK_PUNCT[c] ?: c
    }
}
