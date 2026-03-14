package com.bluetoothpad

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class BluetoothHidGamepad(private val context: Context) {

    companion object {
        private const val TAG = "BtHidGamepad"

        // HID Report Descriptor for a minimal gamepad:
        // 4 buttons + 2 axes (X, Y) each 8-bit signed
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x05.toByte(), // USAGE (Game Pad)
            0xA1.toByte(), 0x01.toByte(), // COLLECTION (Application)

            // Buttons (4 buttons, 4 bits padding)
            0x05.toByte(), 0x09.toByte(), //   USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(), //   USAGE_MINIMUM (Button 1)
            0x29.toByte(), 0x04.toByte(), //   USAGE_MAXIMUM (Button 4)
            0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(), //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(), //   REPORT_SIZE (1)
            0x95.toByte(), 0x04.toByte(), //   REPORT_COUNT (4)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)
            // Padding (4 bits)
            0x75.toByte(), 0x04.toByte(), //   REPORT_SIZE (4)
            0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
            0x81.toByte(), 0x03.toByte(), //   INPUT (Cnst,Var,Abs)

            // X and Y axes (-127 to 127)
            0x05.toByte(), 0x01.toByte(), //   USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //   USAGE (X)
            0x09.toByte(), 0x31.toByte(), //   USAGE (Y)
            0x15.toByte(), 0x81.toByte(), //   LOGICAL_MINIMUM (-127)
            0x25.toByte(), 0x7F.toByte(), //   LOGICAL_MAXIMUM (127)
            0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
            0x95.toByte(), 0x02.toByte(), //   REPORT_COUNT (2)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)

            0xC0.toByte()                 // END_COLLECTION
        )
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var registered = false

    var isConnected = false
        private set

    var ownDeviceName: String = ""
        private set

    var connectedDeviceName: String = ""
        private set

    var onStatusChanged: ((String) -> Unit)? = null

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidGamepad.registered = registered
            val status = if (registered) {
                "HID app registered. Waiting for host to connect..."
            } else {
                "HID app unregistered"
            }
            Log.d(TAG, status)
            onStatusChanged?.invoke(status)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    isConnected = true
                    connectedDeviceName = try {
                        device?.name ?: device?.address ?: "Unknown"
                    } catch (_: SecurityException) {
                        device?.address ?: "Unknown"
                    }
                    val msg = "Device connected"
                    Log.d(TAG, msg)
                    onStatusChanged?.invoke(msg)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    isConnected = false
                    connectedDeviceName = ""
                    val msg = "Disconnected"
                    Log.d(TAG, msg)
                    onStatusChanged?.invoke(msg)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport type=$type id=$id")
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "onSetReport type=$type id=$id")
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                Log.d(TAG, "HID Device profile connected")
                onStatusChanged?.invoke("HID profile connected, registering app...")
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                registered = false
                Log.d(TAG, "HID Device profile disconnected")
                onStatusChanged?.invoke("HID profile disconnected")
            }
        }
    }

    fun start(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return "BluetoothHidDevice requires Android 9 (API 28) or higher"
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            return "Bluetooth not available on this device"
        }

        try {
            ownDeviceName = bluetoothAdapter?.name ?: "Unknown"
        } catch (_: SecurityException) {
            ownDeviceName = "Permission denied"
        }

        if (bluetoothAdapter?.isEnabled != true) {
            return "Please enable Bluetooth first"
        }

        try {
            val success = bluetoothAdapter!!.getProfileProxy(
                context,
                profileListener,
                BluetoothProfile.HID_DEVICE
            )
            return if (success) {
                "Connecting to HID profile..."
            } else {
                "Failed to get HID Device profile proxy"
            }
        } catch (e: SecurityException) {
            return "Bluetooth permission denied. Please grant permissions."
        }
    }

    private fun registerApp() {
        val hidDevice = this.hidDevice ?: return

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "BluetoothPad",
            "Virtual Gamepad",
            "Android",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            HID_REPORT_DESCRIPTOR
        )

        val qosOut = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )

        try {
            val result = hidDevice.registerApp(
                sdpSettings, null, qosOut,
                Executors.newSingleThreadExecutor(),
                hidCallback
            )
            Log.d(TAG, "registerApp result: $result")
            if (!result) {
                onStatusChanged?.invoke("Failed to register HID app")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registering app", e)
            onStatusChanged?.invoke("Permission denied when registering HID app")
        }
    }

    fun sendButtonPress(buttonMask: Int) {
        val device = connectedDevice ?: return
        val hid = hidDevice ?: return
        // Report: [buttons(1 byte), x-axis(1 byte), y-axis(1 byte)]
        val report = byteArrayOf(buttonMask.toByte(), 0, 0)
        try {
            hid.sendReport(device, 0, report)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied sending report", e)
        }
    }

    fun pressAndReleaseButton(buttonMask: Int) {
        sendButtonPress(buttonMask)
        Handler(Looper.getMainLooper()).postDelayed({
            sendButtonPress(0)
        }, 80)
    }

    fun sendAxis(x: Int, y: Int) {
        val device = connectedDevice ?: return
        val hid = hidDevice ?: return
        val report = byteArrayOf(0, x.toByte(), y.toByte())
        try {
            hid.sendReport(device, 0, report)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied sending report", e)
        }
    }

    fun stop() {
        try {
            hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied unregistering", e)
        }
        hidDevice?.let {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hidDevice = null
        connectedDevice = null
        registered = false
    }
}