package com.example.remoteinput

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.remoteinput.bluetooth.BluetoothHidManager
import com.example.remoteinput.ui.CompactKeyboardView
import com.example.remoteinput.ui.TrackpadView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var hidManager: BluetoothHidManager
    private lateinit var trackpadView: TrackpadView
    private lateinit var keyboardView: CompactKeyboardView
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var connectButton: Button
    private lateinit var fullscreenKbButton: Button
    private lateinit var leftClickButton: View
    private lateinit var rightClickButton: View
    private lateinit var trackpadContainer: FrameLayout

    private var keyboardFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        connectButton = findViewById(R.id.connectButton)
        fullscreenKbButton = findViewById(R.id.fullscreenKbButton)
        trackpadView = findViewById(R.id.trackpadView)
        keyboardView = findViewById(R.id.keyboardView)
        leftClickButton = findViewById(R.id.leftClickButton)
        rightClickButton = findViewById(R.id.rightClickButton)
        trackpadContainer = findViewById(R.id.trackpadContainer)

        // Singleton — survives activity recreation
        hidManager = BluetoothHidManager.getInstance(this)

        setupTrackpad()
        setupKeyboard()
        setupMouseButtons()
        setupConnectButton()
        setupFullscreenKbButton()

        if (checkPermissions()) {
            initBluetooth()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (::hidManager.isInitialized) {
            // Re-attach listener and re-init if the profile proxy was lost
            // while in the background. init() is a no-op if already ready.
            initBluetooth()
        }
    }

    private fun hideSystemUI() {
        window.decorView.windowInsetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                connectButton.text = getString(R.string.disconnect)
            }

            override fun onDisconnected() {
                statusText.text = getString(R.string.not_connected)
                statusDot.setBackgroundColor(getColor(R.color.status_disconnected))
                connectButton.text = getString(R.string.connect)
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
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hidManager.sendMouseButton(1, true)
                    v.setBackgroundColor(getColor(R.color.key_pressed))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hidManager.sendMouseButton(1, false)
                    v.setBackgroundColor(getColor(R.color.key_bg))
                }
            }
            true
        }

        rightClickButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hidManager.sendMouseButton(2, true)
                    v.setBackgroundColor(getColor(R.color.key_pressed))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hidManager.sendMouseButton(2, false)
                    v.setBackgroundColor(getColor(R.color.key_bg))
                }
            }
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupConnectButton() {
        connectButton.setOnClickListener {
            if (hidManager.isConnected) {
                hidManager.disconnect()
            } else {
                showDevicePicker()
            }
        }
    }

    private fun setupFullscreenKbButton() {
        fullscreenKbButton.setOnClickListener {
            keyboardFullscreen = !keyboardFullscreen

            val kbParams = keyboardView.layoutParams as LinearLayout.LayoutParams
            val tpParams = trackpadContainer.layoutParams as LinearLayout.LayoutParams

            if (keyboardFullscreen) {
                kbParams.weight = 1f
                kbParams.marginEnd = 0
                trackpadContainer.visibility = View.GONE
                fullscreenKbButton.text = "TP"
            } else {
                kbParams.weight = 1f
                kbParams.marginEnd = (4 * resources.displayMetrics.density).toInt()
                trackpadContainer.visibility = View.VISIBLE
                tpParams.weight = 1f
                trackpadContainer.layoutParams = tpParams
                fullscreenKbButton.text = "KB"
            }

            keyboardView.layoutParams = kbParams
        }
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

    // Don't destroy HID manager on activity finish — it's a singleton
    // that survives across activity restarts. Only kill it if the whole
    // process dies.
}
