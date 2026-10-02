package com.example.remoteinput.relay

import android.content.Context
import com.example.remoteinput.bluetooth.BluetoothHidManager
import com.example.remoteinput.ui.CompactKeyboardView.HidKeyCodes

/**
 * 文本中继编排器：把用户输入的文本（含中文）翻译成一串 HID 按键动作，
 * 交给 [BluetoothHidManager] 的专用发送线程按序发送。
 *
 * 时序模型（需求 B.1）：
 *      Key Down  ->  holdMs (默认 18ms，落在要求的 15~20ms 区间)  ->  Key Up  ->  gapMs
 *
 * 中文处理（需求 B.2）：
 *      "你好" -> nihao -> N I H A O -> Space（触发受控端输入法上屏）
 *
 * 剪贴板模式（需求 B.3）：
 *      [sendPasteMacro] 发送 Cmd+V (macOS) 或 Ctrl+V (Windows/Android)
 */
class TextRelayController(
    context: Context,
    private val hid: BluetoothHidManager
) {

    interface Listener {
        /** 开始发送，totalSteps 为按键动作总数，estimatedMs 为预估耗时。 */
        fun onRelayStarted(totalSteps: Int, estimatedMs: Long)
        /** 进度回调（每 8 个按键一次，避免刷屏）。 */
        fun onRelayProgress(sent: Int, total: Int)
        /** 发送结束，completed=false 表示被取消或连接中断。 */
        fun onRelayFinished(completed: Boolean)
        /** 参数校验/连接状态类错误。 */
        fun onRelayError(message: String)
    }

    private companion object {
        const val PREFS = "hid_relay_prefs"
        const val KEY_MODE = "relay_mode"
        const val KEY_HOLD = "relay_hold_ms"
        const val KEY_GAP = "relay_gap_ms"
        const val KEY_TARGET = "relay_paste_target"

        /** 需求规定的 15~20ms 区间，取中值 18ms。 */
        const val DEFAULT_HOLD_MS = 18L
        const val DEFAULT_GAP_MS = 18L

        /**
         * Unicode 直发的下限节奏。
         * 十六进制输入法只是一个码位缓冲，没有联想计算，但受控端丢一个码位
         * 就会输出完全错误的字符，所以比拼音模式更保守。
         */
        const val HEX_KEY_HOLD_MS = 40L

        /**
         * 组内相邻键之间的间隔。组内必须「按下 -> 抬起 -> 再按下」，
         * 否则连续相同键码（如 U+4E00 的两个 0）会被受控端合并成一次按键。
         */
        const val HEX_INTER_KEY_MS = 20L
        const val HEX_GROUP_GAP_MS = 60L
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var listener: Listener? = null

    /** 当前发送模式，持久化到 SharedPreferences。 */
    var mode: RelayMode = readEnum(KEY_MODE, RelayMode.PINYIN_PER_CHAR)
        set(value) {
            field = value
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    /** 粘贴宏目标平台（决定 Cmd+V 还是 Ctrl+V）。 */
    var pasteTarget: PasteTarget = readEnum(KEY_TARGET, PasteTarget.MAC)
        set(value) {
            field = value
            prefs.edit().putString(KEY_TARGET, value.name).apply()
        }

    /** 单键按下保持时间（毫秒）。 */
    var holdMs: Long = prefs.getLong(KEY_HOLD, DEFAULT_HOLD_MS)
        set(value) {
            val v = value.coerceIn(8L, 120L)
            field = v
            prefs.edit().putLong(KEY_HOLD, v).apply()
        }

    /** 相邻按键之间的间隔（毫秒）。 */
    var gapMs: Long = prefs.getLong(KEY_GAP, DEFAULT_GAP_MS)
        set(value) {
            val v = value.coerceIn(8L, 200L)
            field = v
            prefs.edit().putLong(KEY_GAP, v).apply()
        }

    @Volatile
    var isBusy: Boolean = false
        private set

    // ---------------------------------------------------------------

    /**
     * 发送一段文本。空文本或未连接时通过 [Listener.onRelayError] 报错。
     */
    fun sendText(text: CharSequence) {
        val raw = text.toString()
        if (raw.isEmpty()) {
            listener?.onRelayError("请输入要发送的内容")
            return
        }
        if (!hid.isConnected) {
            listener?.onRelayError("尚未连接受控设备，请先在设置里连接")
            return
        }

        // Unicode 原样直发走独立通道：修饰键要跨键保持，不是「键码序列」模型
        if (mode == RelayMode.UNICODE_HEX) {
            sendUnicodeHex(raw)
            return
        }

        val steps = RelayPlanner.plan(raw, mode)
        if (steps.isEmpty()) {
            listener?.onRelayError("当前内容无法映射为按键（可能包含不支持的字符）")
            return
        }

        val hidSteps = steps.map { BluetoothHidManager.HidKeyStep(it.modifier, it.keyCode) }
        val estimated = RelayPlanner.estimateDurationMs(hidSteps.size, holdMs, gapMs)

        isBusy = true
        listener?.onRelayStarted(hidSteps.size, estimated)

        hid.sendKeySequence(
            steps = hidSteps,
            holdMs = holdMs,
            gapMs = gapMs,
            onProgress = { sent, total -> listener?.onRelayProgress(sent, total) },
            onFinished = { completed ->
                isBusy = false
                listener?.onRelayFinished(completed)
            }
        )
    }

    /**
     * Unicode 原样直发：逐字符按住 Option 敲 4 位十六进制码位。
     *
     * 用于受控端是 macOS 且已切到「Unicode 十六进制输入」输入源的场景 ——
     * 中文、emoji 都能原样送达，且完全不依赖受控端的中文输入法。
     */
    private fun sendUnicodeHex(raw: String) {
        val groups = UnicodeHexRelay.plan(raw)
        val total = groups.sumOf { it.keys.size }
        if (total == 0) {
            listener?.onRelayError("当前内容无法映射为按键（可能包含不支持的字符）")
            return
        }

        // 十六进制直发没有输入法联想缓冲，节奏可以稳一点，避免受控端丢码
        val keyHold = maxOf(holdMs, HEX_KEY_HOLD_MS)
        val interKey = HEX_INTER_KEY_MS
        val groupGap = maxOf(gapMs, HEX_GROUP_GAP_MS)
        val estimated = total * (keyHold + interKey) + groups.size * groupGap

        isBusy = true
        listener?.onRelayStarted(total, estimated)

        hid.sendHeldGroups(
            groups = groups.map { BluetoothHidManager.HeldGroup(it.modifier, it.keys) },
            keyHoldMs = keyHold,
            interKeyMs = interKey,
            groupGapMs = groupGap,
            onProgress = { sent, all -> listener?.onRelayProgress(sent, all) },
            onFinished = { completed ->
                isBusy = false
                listener?.onRelayFinished(completed)
            }
        )
    }

    /**
     * 剪贴板模式：发送 Cmd+V / Ctrl+V，配合受控端剪贴板同步工具使用。
     * 这是标准 HID 无法直传 Unicode 时的兜底方案（需求 B.3）。
     */
    fun sendPasteMacro() {
        if (!hid.isConnected) {
            listener?.onRelayError("尚未连接受控设备，请先在设置里连接")
            return
        }
        isBusy = true
        listener?.onRelayStarted(1, holdMs + gapMs)
        hid.sendKeyCombo(
            modifier = pasteTarget.modifier,
            keyCode = HidKeyCodes.KEY_V,
            holdMs = maxOf(holdMs, 30L),
            gapMs = gapMs
        ) { completed ->
            isBusy = false
            listener?.onRelayFinished(completed)
        }
    }

    /** 单独补发一个回车（例如确认输入法候选）。 */
    fun sendEnter() {
        if (!hid.isConnected) {
            listener?.onRelayError("尚未连接受控设备，请先在设置里连接")
            return
        }
        hid.sendKeyCombo(0, HidKeyCodes.KEY_ENTER, holdMs, gapMs)
    }

    /** 中断正在进行的发送序列。 */
    fun cancel() {
        hid.cancelKeySequence()
        isBusy = false
    }

    /** 预演将要发出的内容，用于输入框下方的实时提示。 */
    fun preview(text: CharSequence): String = when (mode) {
        RelayMode.UNICODE_HEX -> UnicodeHexRelay.preview(text)
        else -> PinyinRelay.toPinyinStream(text)
    }

    /** 预估发送耗时文案。 */
    fun estimateLabel(text: CharSequence): String {
        if (mode == RelayMode.UNICODE_HEX) {
            val count = UnicodeHexRelay.keyCount(text)
            val groups = UnicodeHexRelay.groupCount(text)
            val keyHold = maxOf(holdMs, HEX_KEY_HOLD_MS)
            val groupGap = maxOf(gapMs, HEX_GROUP_GAP_MS)
            val ms = count * (keyHold + HEX_INTER_KEY_MS) + groups * groupGap
            return "$count 键 / 约 ${"%.1f".format(ms / 1000f)} 秒"
        }
        val count = RelayPlanner.plan(text, mode).size
        val ms = RelayPlanner.estimateDurationMs(count, holdMs, gapMs)
        return "$count 键 / 约 ${"%.1f".format(ms / 1000f)} 秒"
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, def: T): T {
        val name = prefs.getString(key, null) ?: return def
        return runCatching { enumValueOf<T>(name) }.getOrDefault(def)
    }
}
