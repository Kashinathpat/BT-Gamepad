package com.bluetooth.gamepad

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import java.util.concurrent.Executors

class BluetoothHidGamepad(private val context: Context) {

    companion object {
        private const val TAG = "BtHidGamepad"

        // DInput descriptor: 16 flat buttons + 4 axes (-127..127)
        private val DESCRIPTOR_DINPUT = byteArrayOf(
            0x05, 0x01,                     // Usage Page (Generic Desktop)
            0x09, 0x05,                     // Usage (Game Pad)
            0xA1.toByte(), 0x01,            // Collection (Application)
            0x05, 0x09,                     //   Usage Page (Button)
            0x19, 0x01,                     //   Usage Minimum (1)
            0x29, 0x10,                     //   Usage Maximum (16)
            0x15, 0x00,                     //   Logical Minimum (0)
            0x25, 0x01,                     //   Logical Maximum (1)
            0x75, 0x01,                     //   Report Size (1)
            0x95.toByte(), 0x10,            //   Report Count (16)
            0x81.toByte(), 0x02,            //   Input (Data,Var,Abs)
            0x05, 0x01,                     //   Usage Page (Generic Desktop)
            0x15, 0x81.toByte(),            //   Logical Minimum (-127)
            0x25, 0x7F,                     //   Logical Maximum (127)
            0x09, 0x30,                     //   Usage (X)
            0x09, 0x31,                     //   Usage (Y)
            0x09, 0x32,                     //   Usage (Z)
            0x09, 0x33,                     //   Usage (Rx)
            0x75, 0x08,                     //   Report Size (8)
            0x95.toByte(), 0x04,            //   Report Count (4)
            0x81.toByte(), 0x02,            //   Input (Data,Var,Abs)
            0xC0.toByte()                   // End Collection
        )

        // HID descriptor: 12 buttons + hat switch (4 bits) + 4 axes (-127..127)
        // Report layout (6 bytes): [btn0-7][btn8-11(lo4)+hat(hi4)][lx][ly][rx][ry]
        private val DESCRIPTOR_HID = byteArrayOf(
            0x05, 0x01,                     // Usage Page (Generic Desktop)
            0x09, 0x05,                     // Usage (Game Pad)
            0xA1.toByte(), 0x01,            // Collection (Application)
            0x05, 0x09,                     //   Usage Page (Button)
            0x09, 0x01,                     //   Usage (Button 1) A
            0x09, 0x02,                     //   Usage (Button 2) B
            0x09, 0x04,                     //   Usage (Button 4) X
            0x09, 0x05,                     //   Usage (Button 5) Y
            0x09, 0x09,                     //   Usage (Button 9) LB  -> bit4, browser maps by usage# so LT(7)<LB(9)
            0x09, 0x0A,                     //   Usage (Button 10) RB -> bit5
            0x09, 0x07,                     //   Usage (Button 7) LT  -> bit6
            0x09, 0x08,                     //   Usage (Button 8) RT  -> bit7
            0x09, 0x0B,                     //   Usage (Button 11) Select
            0x09, 0x0C,                     //   Usage (Button 12) Start
            0x09, 0x0E,                     //   Usage (Button 14) L3
            0x09, 0x0F,                     //   Usage (Button 15) R3
            0x15, 0x00,                     //   Logical Minimum (0)
            0x25, 0x01,                     //   Logical Maximum (1)
            0x75, 0x01,                     //   Report Size (1)
            0x95.toByte(), 0x0C,            //   Report Count (12)
            0x81.toByte(), 0x02,            //   Input (Data,Var,Abs)
            0x05, 0x01,                     //   Usage Page (Generic Desktop)
            0x09, 0x39,                     //   Usage (Hat switch)
            0x15, 0x00,                     //   Logical Minimum (0)
            0x25, 0x08,                     //   Logical Maximum (8)
            0x35, 0x00,                     //   Physical Minimum (0)
            0x46, 0x3B, 0x01,              //   Physical Maximum (315) degrees
            0x65, 0x14,                     //   Unit (Eng Rot: Degree)
            0x75, 0x04,                     //   Report Size (4)
            0x95.toByte(), 0x01,            //   Report Count (1)
            0x81.toByte(), 0x02,            //   Input (Data,Var,Abs)
            0x05, 0x01,                     //   Usage Page (Generic Desktop)
            0x15, 0x81.toByte(),            //   Logical Minimum (-127)
            0x25, 0x7F,                     //   Logical Maximum (127)
            0x09, 0x30,                     //   Usage (X)
            0x09, 0x31,                     //   Usage (Y)
            0x09, 0x32,                     //   Usage (Z)
            0x09, 0x35,                     //   Usage (Rz)
            0x75, 0x08,                     //   Report Size (8)
            0x95.toByte(), 0x04,            //   Report Count (4)
            0x81.toByte(), 0x02,            //   Input (Data,Var,Abs)
            0xC0.toByte()                   // End Collection
        )

        // Button bit indices for DInput (16-button flat layout)
        const val BUTTON_A = 0
        const val BUTTON_B = 1
        const val BUTTON_X = 2
        const val BUTTON_Y = 3
        const val BUTTON_LB = 4
        const val BUTTON_RB = 5
        const val BUTTON_LT = 6
        const val BUTTON_RT = 7
        const val BUTTON_SELECT = 8
        const val BUTTON_START = 9
        const val BUTTON_L3 = 10
        const val BUTTON_R3 = 11
        const val BUTTON_DPAD_UP = 12
        const val BUTTON_DPAD_DOWN = 13
        const val BUTTON_DPAD_LEFT = 14
        const val BUTTON_DPAD_RIGHT = 15

        // Hat switch values for HID mode (1=N,2=NE..8=NW, 9=neutral — matches Logical Max 8, value 9 = no-direction)
        const val HAT_UP        = 1
        const val HAT_UP_RIGHT  = 2
        const val HAT_RIGHT     = 3
        const val HAT_DOWN_RIGHT= 4
        const val HAT_DOWN      = 5
        const val HAT_DOWN_LEFT = 6
        const val HAT_LEFT      = 7
        const val HAT_UP_LEFT   = 8
        const val HAT_NEUTRAL   = 9
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                hidDevice = null
                connectedDevice = null
                isConnected = false
                isAppRegistered = false
                connectionState = BluetoothProfile.STATE_DISCONNECTED
                Log.d(TAG, "BT turned off — state reset")
                onStatusChanged?.invoke()
            }
        }
    }
    var connectedDevice: BluetoothDevice? = null
        private set

    var isConnected = false
        private set
    var isAppRegistered = false
        private set
    var connectionState = BluetoothProfile.STATE_DISCONNECTED
        private set
    var ownDeviceName: String = ""
        private set
    var connectedDeviceName: String = ""
        private set
    var isWindowsDInputMode: Boolean = false

    // 6-byte report: [buttons_lo, buttons_hi_with_hat, lx, ly, rx, ry]
    private val report = ByteArray(6)

    var onStatusChanged: (() -> Unit)? = null

    private var pendingReRegister = false

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isAppRegistered = registered
            Log.d(TAG, "onAppStatusChanged registered=$registered device=${pluggedDevice?.address}")
            if (!registered && pendingReRegister) {
                pendingReRegister = false
                registerApp()
            } else {
                onStatusChanged?.invoke()
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            connectionState = state
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                isConnected = true
                connectedDeviceName = try {
                    device?.name ?: device?.address ?: "Unknown"
                } catch (_: SecurityException) {
                    device?.address ?: "Unknown"
                }
                report.fill(0)
                if (!isWindowsDInputMode) report[1] = ((HAT_NEUTRAL and 0x0F) shl 4).toByte()
                sendReport()
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
                isConnected = false
                connectedDeviceName = ""
            }
            Log.d(TAG, "onConnectionStateChanged state=$state device=${device?.address}")
            onStatusChanged?.invoke()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                Log.d(TAG, "HID profile service connected")
                onStatusChanged?.invoke()
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                isAppRegistered = false
                isConnected = false
                connectionState = BluetoothProfile.STATE_DISCONNECTED
                Log.d(TAG, "HID profile service disconnected")
                onStatusChanged?.invoke()
            }
        }
    }

    fun start(): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter ?: return false
        ownDeviceName = try {
            bluetoothAdapter?.name ?: "Unknown"
        } catch (_: SecurityException) {
            "Unknown"
        }
        context.registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        return try {
            bluetoothAdapter!!.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting HID", e)
            false
        }
    }

    fun switchMode(windowsDInput: Boolean) {
        if (isWindowsDInputMode == windowsDInput) return
        isWindowsDInputMode = windowsDInput
        report.fill(0)
        val hid = hidDevice ?: return
        pendingReRegister = true
        try {
            hid.unregisterApp()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException unregisterApp in switchMode", e)
            pendingReRegister = false
            registerApp()
        }
    }

    private fun registerApp() {
        val hid = hidDevice ?: return
        val descriptor = if (isWindowsDInputMode) DESCRIPTOR_DINPUT else DESCRIPTOR_HID
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Bluetooth Gamepad",
            "Bluetooth Gamepad",
            "Android",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            descriptor
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 10, 50
        )
        try {
            hid.registerApp(sdp, qos, qos, Executors.newCachedThreadPool(), hidCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registerApp", e)
        }
    }

    fun connectDevice(device: BluetoothDevice) {
        val hid = hidDevice ?: run {
            Toast.makeText(context, "HID profile not ready, tap Start / Reconnect", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAppRegistered) {
            Toast.makeText(context, "App not registered yet, please wait", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            hid.connect(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connect", e)
        }
    }

    fun cancelConnect(device: BluetoothDevice) {
        val hid = hidDevice ?: return
        try {
            hid.disconnect(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException disconnect", e)
        }
    }

    fun setButtonState(index: Int, pressed: Boolean) {
        require(index in 0..15) { "Button index out of range: $index" }
        // Bits 12-15 are DPAD — only valid in DInput mode; HID mode uses hat switch
        if (index > 11 && !isWindowsDInputMode) return
        val byteIndex = index / 8
        val bitMask = 1 shl (index % 8)
        if (pressed) {
            report[byteIndex] = (report[byteIndex].toInt() or bitMask).toByte()
        } else {
            report[byteIndex] = (report[byteIndex].toInt() and bitMask.inv()).toByte()
        }
        sendReport()
    }

    fun setHat(hatValue: Int) {
        if (isWindowsDInputMode) return
        // hat in upper 4 bits of report[1]; lower 4 bits are buttons 8-11
        report[1] = ((report[1].toInt() and 0x0F) or ((hatValue and 0x0F) shl 4)).toByte()
        sendReport()
    }

    fun setLeftStick(x: Float, y: Float) {
        report[2] = floatToByte(x)
        report[3] = floatToByte(y)
        sendReport()
    }

    fun setRightStick(x: Float, y: Float) {
        report[4] = floatToByte(x)
        report[5] = floatToByte(y)
        sendReport()
    }

    private fun floatToByte(f: Float): Byte {
        val clamped = f.coerceIn(-1f, 1f)
        return (clamped * 127f).toInt().toByte()
    }

    private fun sendReport() {
        val device = connectedDevice ?: return
        val hid = hidDevice ?: return
        try {
            hid.sendReport(device, 0, report)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sendReport", e)
        }
    }

    fun stop() {
        try { context.unregisterReceiver(btStateReceiver) } catch (_: Exception) {}
        try {
            hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException unregisterApp", e)
        }
        hidDevice?.let {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hidDevice = null
        connectedDevice = null
        isConnected = false
        isAppRegistered = false
        connectionState = BluetoothProfile.STATE_DISCONNECTED
    }
}
