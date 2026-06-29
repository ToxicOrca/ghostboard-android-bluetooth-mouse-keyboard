package com.example.remoteinput.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("MissingPermission")
class BluetoothHidManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothHID"
        const val REQUEST_DISCOVERABLE = 200
        private const val MOUSE_REPORT_INTERVAL_MS = 8L // ~125Hz max report rate

        @Volatile
        private var instance: BluetoothHidManager? = null

        fun getInstance(context: Context): BluetoothHidManager {
            return instance ?: synchronized(this) {
                instance ?: BluetoothHidManager(context.applicationContext).also { instance = it }
            }
        }

        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // --- Keyboard ---
            0x05.toByte(), 0x01,
            0x09.toByte(), 0x06,
            0xA1.toByte(), 0x01,
            0x85.toByte(), 0x01,
            0x05.toByte(), 0x07,
            0x19.toByte(), 0xE0.toByte(),
            0x29.toByte(), 0xE7.toByte(),
            0x15.toByte(), 0x00,
            0x25.toByte(), 0x01,
            0x75.toByte(), 0x01,
            0x95.toByte(), 0x08,
            0x81.toByte(), 0x02,
            0x75.toByte(), 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x01,
            0x75.toByte(), 0x08,
            0x95.toByte(), 0x06,
            0x15.toByte(), 0x00,
            0x25.toByte(), 0x65,
            0x05.toByte(), 0x07,
            0x19.toByte(), 0x00,
            0x29.toByte(), 0x65,
            0x81.toByte(), 0x00,
            0xC0.toByte(),

            // --- Mouse ---
            0x05.toByte(), 0x01,
            0x09.toByte(), 0x02,
            0xA1.toByte(), 0x01,
            0x85.toByte(), 0x02,
            0x09.toByte(), 0x01,
            0xA1.toByte(), 0x00,
            0x05.toByte(), 0x09,
            0x19.toByte(), 0x01,
            0x29.toByte(), 0x03,
            0x15.toByte(), 0x00,
            0x25.toByte(), 0x01,
            0x75.toByte(), 0x01,
            0x95.toByte(), 0x03,
            0x81.toByte(), 0x02,
            0x75.toByte(), 0x05,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x01,
            0x05.toByte(), 0x01,
            0x09.toByte(), 0x30,
            0x09.toByte(), 0x31,
            0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7F,
            0x75.toByte(), 0x08,
            0x95.toByte(), 0x02,
            0x81.toByte(), 0x06,
            0x09.toByte(), 0x38,
            0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7F,
            0x75.toByte(), 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x06,
            0xC0.toByte(),
            0xC0.toByte()
        )
    }

    interface ConnectionListener {
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected()
        fun onAppRegistered()
        fun onError(message: String)
        fun onStatusUpdate(message: String)
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var listener: ConnectionListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    var appRegistered = false
        private set
    private var pendingDevice: BluetoothDevice? = null
    private var profileProxyConnected = false
    private var pendingErrorRunnable: Runnable? = null
    private val connectFailedDevices = mutableSetOf<String>()

    // Remember last connected device for auto-reconnect
    private val prefs: SharedPreferences =
        context.getSharedPreferences("remote_input_prefs", Context.MODE_PRIVATE)
    private val PREF_LAST_DEVICE = "last_device_address"

    // Dedicated thread for sending HID reports
    private val sendThread = HandlerThread("HID-Send").also { it.start() }
    private val sendHandler = Handler(sendThread.looper)

    // Tracked button state so mouse moves preserve held buttons
    @Volatile
    var currentButtons: Int = 0
        private set

    // Mouse move accumulator for throttling
    private val accumDx = AtomicInteger(0)
    private val accumDy = AtomicInteger(0)
    @Volatile
    private var mouseSendScheduled = false

    // Idle wake-up: track last send time to detect BT sniff mode
    @Volatile
    private var lastSendTimeMs = 0L
    private val idleThresholdMs = 2000L // BT enters sniff after ~2s

    val isConnected: Boolean get() = connectedDevice != null
    val pairedDevices: Set<BluetoothDevice> get() = bluetoothAdapter?.bondedDevices ?: emptySet()

    fun setListener(listener: ConnectionListener) {
        this.listener = listener
        // Notify current state immediately so UI is correct on resume
        if (appRegistered && connectedDevice != null) {
            listener.onConnected(connectedDevice!!)
        } else if (appRegistered) {
            listener.onAppRegistered()
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "App status changed: registered=$registered")
            appRegistered = registered
            if (registered) {
                mainHandler.post {
                    listener?.onAppRegistered()
                    if (pendingDevice != null) {
                        val device = pendingDevice
                        pendingDevice = null
                        connectToDevice(device!!)
                    } else if (connectedDevice == null) {
                        // Auto-reconnect to last known device
                        autoReconnectLastDevice()
                    }
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "Connection state: $state for ${device.name}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    currentButtons = 0
                    lastSendTimeMs = System.currentTimeMillis()
                    connectFailedDevices.clear()
                    pendingErrorRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingErrorRunnable = null
                    // Remember this device for auto-reconnect
                    prefs.edit().putString(PREF_LAST_DEVICE, device.address).apply()
                    mainHandler.post { listener?.onConnected(device) }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    mainHandler.post { listener?.onStatusUpdate("Connecting to ${device.name}...") }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    currentButtons = 0
                    mainHandler.post { listener?.onDisconnected() }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (id.toInt() == 1) {
                hidDevice?.replyReport(device, type, id, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            } else if (id.toInt() == 2) {
                hidDevice?.replyReport(device, type, id, byteArrayOf(0, 0, 0, 0))
            } else {
                hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {}
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "HID profile proxy connected")
            hidDevice = proxy as BluetoothHidDevice
            profileProxyConnected = true

            if (!appRegistered) {
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "HID profile proxy disconnected")
            hidDevice = null
            connectedDevice = null
            appRegistered = false
            profileProxyConnected = false
            mainHandler.post { listener?.onDisconnected() }
        }
    }

    private fun registerHidApp() {
        val hid = hidDevice ?: return

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "GhostBoard",
            "Phone as Mouse & Keyboard",
            "GhostBoard",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HID_REPORT_DESCRIPTOR
        )

        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        val result = hid.registerApp(sdp, null, qos, executor, hidCallback)
        Log.d(TAG, "registerApp result: $result")
        if (result != true) {
            mainHandler.post { listener?.onError("Failed to register HID app") }
        }
    }

    fun init(): Boolean {
        if (bluetoothAdapter == null) return false
        if (!bluetoothAdapter.isEnabled) {
            listener?.onError("Please enable Bluetooth")
            return false
        }

        // Already initialized and registered
        if (profileProxyConnected && appRegistered) {
            if (connectedDevice != null) {
                mainHandler.post { listener?.onConnected(connectedDevice!!) }
            } else {
                mainHandler.post { listener?.onAppRegistered() }
                // Try to reconnect to last device
                autoReconnectLastDevice()
            }
            return true
        }

        // Profile proxy lost — re-acquire
        return bluetoothAdapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun connectToDevice(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null || !appRegistered) {
            listener?.onStatusUpdate("Waiting for HID service...")
            pendingDevice = device
            if (!profileProxyConnected) {
                init()
            }
            return
        }

        val currentState = hid.getConnectionState(device)
        if (currentState == BluetoothProfile.STATE_CONNECTED) {
            connectedDevice = device
            listener?.onConnected(device)
            return
        }

        // Cancel any previous pending error
        pendingErrorRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingErrorRunnable = null

        val deviceAddr = device.address

        // If connect() previously failed for this device, don't call it again —
        // Android shows a system "Can't connect" toast each time. Just wait
        // for the PC to connect to us.
        if (deviceAddr in connectFailedDevices) {
            listener?.onStatusUpdate("Waiting for ${device.name} to connect...")
            return
        }

        listener?.onStatusUpdate("Connecting to ${device.name}...")
        val result = hid.connect(device)

        if (!result) {
            connectFailedDevices.add(deviceAddr)
            // Don't show an error immediately — the PC often connects on its own.
            // Just show a waiting message.
            listener?.onStatusUpdate("Waiting for ${device.name} to connect...")
            // After 8 seconds, if still not connected, offer help
            val errorRunnable = Runnable {
                if (connectedDevice == null) {
                    listener?.onError(
                        "Still waiting for connection. Try:\n" +
                        "1. On your PC, go to Bluetooth settings\n" +
                        "2. Remove/forget this phone\n" +
                        "3. Make phone discoverable (tap button below)\n" +
                        "4. On PC, add a new Bluetooth device\n" +
                        "5. Pair and connect from the PC side"
                    )
                }
                pendingErrorRunnable = null
            }
            pendingErrorRunnable = errorRunnable
            mainHandler.postDelayed(errorRunnable, 8000)
        }
    }

    fun getDiscoverableIntent(durationSeconds: Int = 300): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
        }
    }

    private fun autoReconnectLastDevice() {
        val lastAddr = prefs.getString(PREF_LAST_DEVICE, null) ?: return
        val device = pairedDevices.firstOrNull { it.address == lastAddr } ?: return
        Log.d(TAG, "Auto-reconnecting to ${device.name} ($lastAddr)")
        connectToDevice(device)
    }

    fun disconnect() {
        val device = connectedDevice ?: return
        hidDevice?.disconnect(device)
    }

    // --- Idle wake-up ---

    /**
     * After idle, BT link enters sniff mode causing the first report to be delayed.
     * Send a no-op report to wake the link before real input.
     */
    private fun wakeIfIdle() {
        val now = System.currentTimeMillis()
        if (now - lastSendTimeMs > idleThresholdMs) {
            val device = connectedDevice ?: return
            // Send empty mouse report to wake BT link
            hidDevice?.sendReport(device, 2, byteArrayOf(currentButtons.toByte(), 0, 0, 0))
        }
        lastSendTimeMs = now
    }

    // --- Mouse input ---

    fun sendMouseMove(dx: Int, dy: Int) {
        if (connectedDevice == null) return
        accumDx.addAndGet(dx)
        accumDy.addAndGet(dy)
        scheduleMouseSend()
    }

    private fun scheduleMouseSend() {
        if (mouseSendScheduled) return
        mouseSendScheduled = true
        sendHandler.postDelayed(mouseFlushRunnable, MOUSE_REPORT_INTERVAL_MS)
    }

    private val mouseFlushRunnable = Runnable {
        mouseSendScheduled = false
        val dx = accumDx.getAndSet(0)
        val dy = accumDy.getAndSet(0)
        if (dx != 0 || dy != 0) {
            wakeIfIdle()
            sendMouseReportNow(currentButtons, dx, dy, 0)
        }
    }

    private fun sendMouseReportNow(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        val device = connectedDevice ?: return
        val report = byteArrayOf(
            buttons.toByte(),
            clampByte(dx),
            clampByte(dy),
            clampByte(wheel)
        )
        hidDevice?.sendReport(device, 2, report)
        lastSendTimeMs = System.currentTimeMillis()
    }

    fun sendMouseClick(button: Int) {
        val device = connectedDevice ?: return
        sendHandler.post {
            wakeIfIdle()
            val dx = accumDx.getAndSet(0)
            val dy = accumDy.getAndSet(0)
            if (dx != 0 || dy != 0) {
                sendMouseReportNow(currentButtons, dx, dy, 0)
            }
            val btnMask = currentButtons or button
            hidDevice?.sendReport(device, 2, byteArrayOf(btnMask.toByte(), 0, 0, 0))
            try { Thread.sleep(50) } catch (_: InterruptedException) {}
            hidDevice?.sendReport(device, 2, byteArrayOf(currentButtons.toByte(), 0, 0, 0))
            lastSendTimeMs = System.currentTimeMillis()
        }
    }

    fun sendMouseButton(button: Int, pressed: Boolean) {
        currentButtons = if (pressed) {
            currentButtons or button
        } else {
            currentButtons and button.inv()
        }
        sendHandler.post {
            wakeIfIdle()
            val dx = accumDx.getAndSet(0)
            val dy = accumDy.getAndSet(0)
            sendMouseReportNow(currentButtons, dx, dy, 0)
        }
    }

    fun sendScroll(amount: Int) {
        sendHandler.post {
            wakeIfIdle()
            sendMouseReportNow(currentButtons, 0, 0, amount)
        }
    }

    // --- Keyboard input ---

    fun sendKeyPress(modifier: Int, keyCode: Int) {
        val device = connectedDevice ?: return
        sendHandler.post {
            wakeIfIdle()
            hidDevice?.sendReport(device, 1,
                byteArrayOf(modifier.toByte(), 0, keyCode.toByte(), 0, 0, 0, 0, 0))
            try { Thread.sleep(50) } catch (_: InterruptedException) {}
            hidDevice?.sendReport(device, 1, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            lastSendTimeMs = System.currentTimeMillis()
        }
    }

    fun sendKeyDown(modifier: Int, keyCode: Int) {
        val device = connectedDevice ?: return
        sendHandler.post {
            wakeIfIdle()
            hidDevice?.sendReport(device, 1,
                byteArrayOf(modifier.toByte(), 0, keyCode.toByte(), 0, 0, 0, 0, 0))
            lastSendTimeMs = System.currentTimeMillis()
        }
    }

    fun sendKeyUp() {
        val device = connectedDevice ?: return
        sendHandler.post {
            hidDevice?.sendReport(device, 1, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            lastSendTimeMs = System.currentTimeMillis()
        }
    }

    // Don't destroy on activity lifecycle — only on explicit app kill
    fun destroy() {
        disconnect()
        hidDevice?.unregisterApp()
        appRegistered = false
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        profileProxyConnected = false
        hidDevice = null
        sendThread.quitSafely()
        instance = null
    }

    private fun clampByte(value: Int): Byte {
        return value.coerceIn(-127, 127).toByte()
    }
}
