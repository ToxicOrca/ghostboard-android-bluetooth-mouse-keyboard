package com.example.remoteinput

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.remoteinput.bluetooth.BluetoothHidManager
import com.example.remoteinput.relay.PasteTarget
import com.example.remoteinput.relay.RelayMode
import com.example.remoteinput.relay.TextRelayController
import com.example.remoteinput.settings.AppTheme
import com.example.remoteinput.settings.SettingsDialog
import com.example.remoteinput.settings.SettingsManager
import com.example.remoteinput.ui.CompactKeyboardView
import com.example.remoteinput.ui.HidKeyMapper
import com.example.remoteinput.ui.TrackpadView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var hidManager: BluetoothHidManager
    private lateinit var settings: SettingsManager
    private lateinit var trackpadView: TrackpadView
    private lateinit var keyboardView: CompactKeyboardView
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var fullscreenKbButton: Button
    private lateinit var settingsButton: Button
    private lateinit var exitButton: Button
    private lateinit var leftClickButton: View
    private lateinit var rightClickButton: View
    private lateinit var trackpadContainer: FrameLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var hiddenInput: EditText
    private lateinit var rootLayout: LinearLayout
    private lateinit var statusBar: FrameLayout

    // --- 文本中继栏 ---
    private lateinit var relayBar: LinearLayout
    private lateinit var relayInput: EditText
    private lateinit var relaySendButton: Button
    private lateinit var relayPreview: TextView
    private lateinit var relayModeLabel: TextView
    private lateinit var relayController: TextRelayController
    private var lastRelayStepCount = 0

    private var keyboardFullscreen = false
    private var isPortrait = false
    private var softKeyboardShowing = false
    private var settingsDialog: SettingsDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUI()
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)

        rootLayout = findViewById(R.id.rootLayout)
        statusBar = findViewById(R.id.statusBar)
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        fullscreenKbButton = findViewById(R.id.fullscreenKbButton)
        settingsButton = findViewById(R.id.settingsButton)
        exitButton = findViewById(R.id.exitButton)
        trackpadView = findViewById(R.id.trackpadView)
        keyboardView = findViewById(R.id.keyboardView)
        leftClickButton = findViewById(R.id.leftClickButton)
        rightClickButton = findViewById(R.id.rightClickButton)
        trackpadContainer = findViewById(R.id.trackpadContainer)
        mainContent = findViewById(R.id.mainContent)
        hiddenInput = findViewById(R.id.hiddenInput)
        relayBar = findViewById(R.id.relayBar)
        relayInput = findViewById(R.id.relayInput)
        relaySendButton = findViewById(R.id.relaySendButton)
        relayPreview = findViewById(R.id.relayPreview)
        relayModeLabel = findViewById(R.id.relayModeLabel)

        // Singleton — survives activity recreation
        hidManager = BluetoothHidManager.getInstance(this)

        setupTrackpad()
        setupKeyboard()
        setupMouseButtons()
        setupFullscreenKbButton()
        setupSettingsButton()
        setupExitButton()
        setupHiddenInput()
        setupRelayBar()

        // Detect initial orientation
        isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        applyOrientationLayout()
        applyTheme(settings.currentTheme)

        if (checkPermissions()) {
            initBluetooth()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (::hidManager.isInitialized) {
            initBluetooth()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        settingsDialog?.dismiss()
        settingsDialog = null
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        applyOrientationLayout()
    }

    private fun applyOrientationLayout() {
        if (isPortrait) {
            // Portrait: hide custom keyboard, show trackpad full-width
            keyboardView.visibility = View.GONE
            trackpadContainer.visibility = View.VISIBLE
            mainContent.orientation = LinearLayout.VERTICAL

            val tpParams = trackpadContainer.layoutParams as LinearLayout.LayoutParams
            tpParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            tpParams.height = 0
            tpParams.weight = 1f
            trackpadContainer.layoutParams = tpParams

            // Update buttons
            keyboardFullscreen = false
            softKeyboardShowing = false

            fullscreenKbButton.visibility = View.VISIBLE
            fullscreenKbButton.text = "KB"
        } else {
            // Landscape: restore split view
            hideSoftKeyboard()
            keyboardView.visibility = View.VISIBLE
            trackpadContainer.visibility = View.VISIBLE
            mainContent.orientation = LinearLayout.HORIZONTAL

            applyTrackpadPosition()

            // Update buttons
            fullscreenKbButton.visibility = View.VISIBLE
            fullscreenKbButton.text = "KB"
            keyboardFullscreen = false
            softKeyboardShowing = false
        }
    }

    private fun applyTrackpadPosition() {
        if (isPortrait) return // Only applies to landscape

        val trackpadOnLeft = settings.trackpadOnLeft

        // Remove both views from mainContent
        mainContent.removeView(keyboardView)
        mainContent.removeView(trackpadContainer)

        if (trackpadOnLeft) {
            mainContent.addView(trackpadContainer, 0)
            mainContent.addView(keyboardView, 1)
        } else {
            mainContent.addView(keyboardView, 0)
            mainContent.addView(trackpadContainer, 1)
        }

        val kbParams = keyboardView.layoutParams as LinearLayout.LayoutParams
        kbParams.width = 0
        kbParams.height = LinearLayout.LayoutParams.MATCH_PARENT
        kbParams.weight = 1f

        val tpParams = trackpadContainer.layoutParams as LinearLayout.LayoutParams
        tpParams.width = 0
        tpParams.height = LinearLayout.LayoutParams.MATCH_PARENT
        tpParams.weight = 1f

        if (trackpadOnLeft) {
            tpParams.marginEnd = (4 * resources.displayMetrics.density).toInt()
            tpParams.marginStart = 0
            kbParams.marginEnd = 0
            kbParams.marginStart = 0
        } else {
            kbParams.marginEnd = (4 * resources.displayMetrics.density).toInt()
            kbParams.marginStart = 0
            tpParams.marginEnd = 0
            tpParams.marginStart = 0
        }

        keyboardView.layoutParams = kbParams
        trackpadContainer.layoutParams = tpParams
    }

    private fun applyTheme(theme: AppTheme) {
        // Apply to root layout, system status bar, and app status bar
        rootLayout.setBackgroundColor(theme.background)
        statusBar.setBackgroundColor(theme.surface)
        window.statusBarColor = theme.surface

        // Apply to status text
        statusText.setTextColor(theme.textSecondary)

        // Apply to buttons
        fullscreenKbButton.setTextColor(theme.accent)
        settingsButton.setTextColor(theme.accent)
        exitButton.setTextColor(theme.textSecondary)

        // Apply to mouse buttons
        leftClickButton.setBackgroundColor(theme.keyBg)
        rightClickButton.setBackgroundColor(theme.keyBg)

        // Apply to relay bar
        if (::relayBar.isInitialized) {
            relayBar.setBackgroundColor(theme.surface)
            relayInput.setBackgroundColor(theme.keyBg)
            relayInput.setTextColor(theme.keyText)
            relayInput.setHintTextColor(theme.textSecondary)
            relaySendButton.setTextColor(theme.accent)
            relayPreview.setTextColor(theme.textSecondary)
            relayModeLabel.setTextColor(theme.accent)
            relayModeLabel.setBackgroundColor(theme.keyBg)
            refreshRelayModeLabel()
        }

        // Apply to custom views
        trackpadView.applyTheme(theme)
        keyboardView.applyTheme(theme)

        // Update status dot color based on connection state
        if (hidManager.isConnected) {
            statusDot.setBackgroundColor(getColor(R.color.status_connected))
        } else {
            statusDot.setBackgroundColor(getColor(R.color.status_disconnected))
        }
    }

    private fun hideSystemUI() {
        // Make status bar blend with app - don't hide it, so content stays below the cutout
        if (::settings.isInitialized) {
            window.statusBarColor = settings.currentTheme.surface
        }
        window.decorView.windowInsetsController?.let {
            // Only hide navigation bar - keep status bar visible to avoid cutout clipping
            it.hide(WindowInsets.Type.navigationBars())
            it.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Use light/dark status bar icons based on dark theme
            it.setSystemBarsAppearance(0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }

        return if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initBluetooth()
            } else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun attachListener() {
        hidManager.setListener(object : BluetoothHidManager.ConnectionListener {
            override fun onConnected(device: BluetoothDevice) {
                @SuppressLint("MissingPermission")
                val name = device.name ?: device.address
                statusText.text = getString(R.string.connected_to, name)
                statusDot.setBackgroundColor(getColor(R.color.status_connected))
            }

            override fun onDisconnected() {
                statusText.text = getString(R.string.not_connected)
                statusDot.setBackgroundColor(getColor(R.color.status_disconnected))
            }

            override fun onAppRegistered() {
                statusText.text = "HID Ready \u2014 Tap Connect"
            }

            override fun onError(message: String) {
                if (message.contains("\n")) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Connection Help")
                        .setMessage(message)
                        .setPositiveButton("Make Discoverable") { _, _ -> makeDiscoverable() }
                        .setNegativeButton("OK", null)
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            override fun onStatusUpdate(message: String) {
                statusText.text = message
            }
        })
    }

    private fun initBluetooth() {
        attachListener()

        if (!hidManager.init()) {
            Toast.makeText(this, R.string.bt_hid_not_supported, Toast.LENGTH_LONG).show()
        }
    }

    private fun makeDiscoverable() {
        @Suppress("DEPRECATION")
        startActivityForResult(
            hidManager.getDiscoverableIntent(300),
            BluetoothHidManager.REQUEST_DISCOVERABLE
        )
    }

    private fun setupTrackpad() {
        trackpadView.listener = object : TrackpadView.TrackpadListener {
            override fun onMove(dx: Int, dy: Int) {
                hidManager.sendMouseMove(dx, dy)
            }

            override fun onTap() {
                hidManager.sendMouseClick(1)
            }

            override fun onTwoFingerTap() {
                hidManager.sendMouseClick(2)
            }

            override fun onScroll(amount: Int) {
                hidManager.sendScroll(amount)
            }
        }
    }

    private fun setupKeyboard() {
        keyboardView.listener = object : CompactKeyboardView.KeyboardListener {
            override fun onKeyPress(modifier: Int, keyCode: Int) {
                hidManager.sendKeyPress(modifier, keyCode)
            }

            override fun onKeyDown(modifier: Int, keyCode: Int) {
                hidManager.sendKeyDown(modifier, keyCode)
            }

            override fun onKeyUp() {
                hidManager.sendKeyUp()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMouseButtons() {
        leftClickButton.setOnTouchListener { v, event ->
            val theme = settings.currentTheme
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hidManager.sendMouseButton(1, true)
                    v.setBackgroundColor(theme.keyPressed)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hidManager.sendMouseButton(1, false)
                    v.setBackgroundColor(theme.keyBg)
                }
            }
            true
        }

        rightClickButton.setOnTouchListener { v, event ->
            val theme = settings.currentTheme
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hidManager.sendMouseButton(2, true)
                    v.setBackgroundColor(theme.keyPressed)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hidManager.sendMouseButton(2, false)
                    v.setBackgroundColor(theme.keyBg)
                }
            }
            true
        }
    }

    private fun setupFullscreenKbButton() {
        fullscreenKbButton.setOnClickListener {
            if (isPortrait) {
                toggleSoftKeyboard()
            } else {
                // In landscape: toggle fullscreen keyboard vs split view
                keyboardFullscreen = !keyboardFullscreen

                val kbParams = keyboardView.layoutParams as LinearLayout.LayoutParams
                val tpParams = trackpadContainer.layoutParams as LinearLayout.LayoutParams

                if (keyboardFullscreen) {
                    kbParams.weight = 1f
                    kbParams.marginEnd = 0
                    trackpadContainer.visibility = View.GONE
                    fullscreenKbButton.text = "TP"
                } else {
                    trackpadContainer.visibility = View.VISIBLE
                    applyTrackpadPosition()
                    fullscreenKbButton.text = "KB"
                }

                keyboardView.layoutParams = kbParams
            }
        }
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            val dialog = SettingsDialog(
                context = this,
                settings = settings,
                isConnected = hidManager.isConnected,
                isPortrait = isPortrait,
                onConnect = {
                    showDevicePicker()
                },
                onDisconnect = {
                    hidManager.disconnect()
                },
                onThemeChanged = { theme ->
                    applyTheme(theme)
                },
                onTrackpadPositionChanged = { _ ->
                    if (!isPortrait) {
                        applyTrackpadPosition()
                    }
                },
                onOrientationChanged = { portrait ->
                    settingsDialog?.dismiss()
                    settingsDialog = null
                    if (portrait) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
            )
            dialog.setOnDismissListener { settingsDialog = null }
            settingsDialog = dialog
            dialog.show()
        }
    }

    private fun setupExitButton() {
        exitButton.setOnClickListener {
            finishAffinity()
        }
    }

    private fun toggleSoftKeyboard() {
        if (softKeyboardShowing) {
            hideSoftKeyboard()
        } else {
            showSoftKeyboard()
        }
    }

    private fun showSoftKeyboard() {
        hiddenInput.visibility = View.VISIBLE
        hiddenInput.requestFocus()
        hiddenInput.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
        }, 100)
        softKeyboardShowing = true
        fullscreenKbButton.text = "TP"
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
        hiddenInput.clearFocus()
        softKeyboardShowing = false
        if (isPortrait) {
            fullscreenKbButton.text = "KB"
        }
    }

    private fun setupHiddenInput() {
        // Capture text input from soft keyboard
        hiddenInput.addTextChangedListener(object : TextWatcher {
            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                previousText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val newText = s?.toString() ?: ""
                if (newText.length > previousText.length) {
                    // Characters were added
                    val added = newText.substring(previousText.length)
                    for (c in added) {
                        val event = HidKeyMapper.mapChar(c)
                        if (event != null) {
                            hidManager.sendKeyPress(event.modifier, event.keyCode)
                        }
                    }
                }
                // Keep the EditText from growing indefinitely
                if (newText.length > 50) {
                    hiddenInput.removeTextChangedListener(this)
                    hiddenInput.setText("")
                    hiddenInput.addTextChangedListener(this)
                }
            }
        })

        // Capture backspace and enter from soft keyboard via key events
        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        hidManager.sendKeyPress(0, CompactKeyboardView.HidKeyCodes.KEY_BACKSPACE)
                        true
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        hidManager.sendKeyPress(0, CompactKeyboardView.HidKeyCodes.KEY_ENTER)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    // =================================================================
    //  文本输入中继（需求 A / B）
    // =================================================================

    private fun setupRelayBar() {
        relayController = TextRelayController(this, hidManager)

        relayController.listener = object : TextRelayController.Listener {
            override fun onRelayStarted(totalSteps: Int, estimatedMs: Long) {
                lastRelayStepCount = totalSteps
                relaySendButton.text = "停止"
                relaySendButton.setTextColor(getColor(R.color.status_disconnected))
                statusText.text = getString(R.string.relay_sending, 0, totalSteps)
            }

            override fun onRelayProgress(sent: Int, total: Int) {
                statusText.text = getString(R.string.relay_sending, sent, total)
            }

            override fun onRelayFinished(completed: Boolean) {
                relaySendButton.text = getString(R.string.relay_send)
                relaySendButton.setTextColor(settings.currentTheme.accent)
                statusText.text = if (completed) {
                    getString(R.string.relay_done, lastRelayStepCount)
                } else {
                    getString(R.string.relay_cancelled)
                }
            }

            override fun onRelayError(message: String) {
                relaySendButton.text = getString(R.string.relay_send)
                relaySendButton.setTextColor(settings.currentTheme.accent)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        // 实时展示「中文 -> 拼音流」的预演结果
        relayInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!relayController.isBusy) {
                    updateRelayPreview(s?.toString().orEmpty())
                }
            }
        })

        // 点击输入框 -> 主动唤起系统默认输入法（搜狗 / 微信输入法 / 系统拼音 / 语音）
        relayInput.setOnClickListener {
            relayInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(relayInput, InputMethodManager.SHOW_IMPLICIT)
        }

        // 回车直接发送
        relayInput.setOnEditorActionListener { _, actionId, event ->
            val enterDown = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || enterDown) {
                relaySendButton.performClick()
                true
            } else {
                false
            }
        }

        // 单击发送 / 中断，长按打开中继模式菜单
        relaySendButton.setOnClickListener { dispatchRelay() }
        relaySendButton.setOnLongClickListener {
            showRelayOptionsDialog()
            true
        }
        relayModeLabel.setOnClickListener { showRelayOptionsDialog() }

        refreshRelayModeLabel()
        updateRelayPreview("")
    }

    /** 发送当前输入框内容；若正在发送则改为中断。 */
    private fun dispatchRelay() {
        if (relayController.isBusy) {
            relayController.cancel()
            return
        }
        val text = relayInput.text?.toString().orEmpty()
        relayController.sendText(text)

        // 仅在真正开始发送后才清空输入框，失败时保留内容便于重试
        if (relayController.isBusy) {
            updateRelayPreview(text)
            relayInput.setText("")
        }
    }

    private fun updateRelayPreview(text: String) {
        if (!::relayPreview.isInitialized) return
        relayPreview.text = if (text.isEmpty()) {
            ""
        } else {
            "→ " + relayController.preview(text) + "  (" + relayController.estimateLabel(text) + ")"
        }
    }

    private fun refreshRelayModeLabel() {
        if (!::relayModeLabel.isInitialized) return
        val tag = when (relayController.mode) {
            RelayMode.PINYIN_PER_CHAR -> "拼音"
            RelayMode.PINYIN_BATCH -> "整句"
            RelayMode.LITERAL -> "直通"
        }
        relayModeLabel.text = "$tag · ${relayController.holdMs}ms"
    }

    private fun showRelayOptionsDialog() {
        val entries = arrayOf(
            "中继模式：${relayController.mode.label}",
            "粘贴宏目标：${relayController.pasteTarget.label}",
            "按键时序：${relayController.holdMs}ms 保持 / ${relayController.gapMs}ms 间隔",
            "立即发送粘贴宏（Cmd/Ctrl + V）"
        )
        AlertDialog.Builder(this)
            .setTitle("中继设置")
            .setItems(entries) { _, which ->
                when (which) {
                    0 -> showModeChooser()
                    1 -> showPasteTargetChooser()
                    2 -> showTimingChooser()
                    3 -> relayController.sendPasteMacro()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showModeChooser() {
        val modes = RelayMode.values()
        val labels = modes.map { "${it.label}\n${it.desc}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.relay_mode_title)
            .setSingleChoiceItems(labels, modes.indexOf(relayController.mode)) { dialog, which ->
                relayController.mode = modes[which]
                refreshRelayModeLabel()
                updateRelayPreview(relayInput.text?.toString().orEmpty())
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPasteTargetChooser() {
        val targets = PasteTarget.values()
        val labels = targets.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("粘贴宏目标平台")
            .setSingleChoiceItems(labels, targets.indexOf(relayController.pasteTarget)) { dialog, which ->
                relayController.pasteTarget = targets[which]
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTimingChooser() {
        // 需求 B.1 要求 15~20ms，这里给出几档预设 + 更保守的慢速档
        val holds = longArrayOf(12, 15, 18, 20, 30, 50)
        val gaps = longArrayOf(12, 15, 18, 20, 30, 50)
        val labels = holds.mapIndexed { i, h ->
            "保持 ${h}ms / 间隔 ${gaps[i]}ms" + if (h in 15..20) "  (推荐)" else ""
        }.toTypedArray()
        val current = holds.indexOfFirst { it == relayController.holdMs }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.relay_timing_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                relayController.holdMs = holds[which]
                relayController.gapMs = gaps[which]
                refreshRelayModeLabel()
                updateRelayPreview(relayInput.text?.toString().orEmpty())
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePicker() {
        val devices = hidManager.pairedDevices.toList()

        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Paired Devices")
                .setMessage(
                    "No paired Bluetooth devices found.\n\n" +
                    "To connect:\n" +
                    "1. Tap 'Make Discoverable' below\n" +
                    "2. On your PC, go to Bluetooth settings\n" +
                    "3. Add new device and select your phone\n" +
                    "4. Once paired, the PC should auto-connect"
                )
                .setPositiveButton("Make Discoverable") { _, _ -> makeDiscoverable() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val names = devices.map { it.name ?: it.address }.toTypedArray()
        val options = names + "Make Discoverable (connect from PC)"

        AlertDialog.Builder(this)
            .setTitle(R.string.select_device)
            .setItems(options) { _, which ->
                if (which < devices.size) {
                    statusText.text = getString(R.string.connecting)
                    hidManager.connectToDevice(devices[which])
                } else {
                    makeDiscoverable()
                    statusText.text = "Discoverable \u2014 connect from your PC"
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // If soft keyboard is showing in portrait, hide it instead of exiting
        if (isPortrait && softKeyboardShowing) {
            hideSoftKeyboard()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
