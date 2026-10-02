package com.example.remoteinput.relay

import com.github.promeg.pinyinhelper.Pinyin

/**
 * 汉字 -> 拼音字母流的轻量转换层。
 *
 * 设计要点：
 *  1. 只输出 a-z 的 ASCII 字母，不带声调、不带分隔符 —— 这样受控端（Mac / Windows）
 *     的中文输入法可以直接把它们当成拼音串吃进去，最后由一个 Space 触发候选上屏。
 *  2. 对于多音字，TinyPinyin 给出的是最常用读音；如需精确可换 pinyin4j 并做词库消歧。
 *  3. 非汉字（英文/数字/标点）一律返回 null，由上层按原样按键处理。
 */
object PinyinRelay {

    /** 是否为需要转拼音的汉字（含中日韩统一表意文字基本区，不含标点/假名）。 */
    fun isChinese(c: Char): Boolean = Pinyin.isChinese(c)

    /**
     * 返回该汉字对应的拼音字母流（小写，纯 a-z）。
     * 非汉字、或库中无该字读音时返回 null。
     */
    fun pinyinOf(c: Char): String? {
        if (!Pinyin.isChinese(c)) return null
        val raw = try {
            Pinyin.toPinyin(c)
        } catch (_: Throwable) {
            null
        } ?: return null
        val letters = raw.lowercase().filter { it in 'a'..'z' }
        return letters.ifEmpty { null }
    }

    /**
     * 整句转拼音字母流（不区分汉字/非汉字，非汉字原样保留）。
     * 仅用于调试与日志展示，真正发送走 [RelayPlanner]。
     */
    fun toPinyinStream(text: CharSequence): String = buildString {
        for (c in text) {
            append(pinyinOf(c) ?: c)
        }
    }
}
