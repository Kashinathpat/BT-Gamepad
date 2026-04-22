package com.bluetoothpad

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bluetoothpad.ui.theme.AppTheme
import com.bluetoothpad.ui.theme.BluetoothPadTheme
import java.lang.reflect.Method

class MainActivity : ComponentActivity() {

    private val connected = mutableStateOf(false)
    private val hidProfileConnected = mutableStateOf(false)
    private val hidAppRegistered = mutableStateOf(false)
    private val hidConnectionState = mutableStateOf(BluetoothProfile.STATE_DISCONNECTED)
    private val connectedDeviceName = mutableStateOf("")
    private val ownDeviceName = mutableStateOf("")
    private val isWindowsMode = mutableStateOf(false)
    private val appTheme = mutableStateOf(AppTheme.SYSTEM)
    private val showSettings = mutableStateOf(false)

    private var gamepad: BluetoothHidGamepad? = null
    private var userCancelledConnect = false
    lateinit var prefs: SharedPreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            initGamepad()
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(_context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            if (state == BluetoothDevice.BOND_BONDED) {
                gamepad?.connectDevice(device)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("data", MODE_PRIVATE)
        isWindowsMode.value = prefs.getBoolean("isWindowsDInputMode", false)
        appTheme.value = when (prefs.getString("appTheme", "SYSTEM")) {
            "LIGHT"  -> AppTheme.LIGHT
            "DARK"   -> AppTheme.DARK
            else     -> AppTheme.SYSTEM
        }

        registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), RECEIVER_EXPORTED)

        enableEdgeToEdge()
        setContent {
            BluetoothPadTheme(appTheme = appTheme.value) {
                requestedOrientation = if (connected.value) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }

                if (connected.value) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    ControllerScreen(
                        gamepad = gamepad,
                        isWindowsMode = isWindowsMode.value,
                        connectedDeviceName = connectedDeviceName.value,
                        onStopClick = { stopGamepad() }
                    )
                } else {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (showSettings.value) {
                        SettingsScreen(
                            appTheme = appTheme.value,
                            appVersion = "1.0",
                            onThemeChange = { theme ->
                                appTheme.value = theme
                                prefs.edit().putString("appTheme", theme.name).apply()
                            },
                            onBack = { showSettings.value = false }
                        )
                    } else {
                        ConnectionScreen(
                            activity = this,
                            hidProfileConnected = hidProfileConnected.value,
                            hidAppRegistered = hidAppRegistered.value,
                            hidConnectionState = hidConnectionState.value,
                            connectedDeviceName = connectedDeviceName.value,
                            ownDeviceName = ownDeviceName.value,
                            isWindowsMode = isWindowsMode.value,
                            onStartClick = { requestPermissionsAndInit(); reconnectLastDevice() },
                            onWindowsModeToggle = { value ->
                                isWindowsMode.value = value
                                prefs.edit().putBoolean("isWindowsDInputMode", value).apply()
                            },
                            onPairDevice = { device -> pairDevice(device) },
                            onUnpairDevice = { device -> unpairDevice(device) },
                            onConnectDevice = { device -> gamepad?.connectDevice(device) },
                            onCancelConnect = { device ->
                                userCancelledConnect = true
                                gamepad?.cancelConnect(device)
                            },
                            onSettingsClick = { showSettings.value = true }
                        )
                    }
                }
            }
        }

        if (hasRequiredPermissions()) {
            initGamepad()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsAndInit() {
        if (hasRequiredPermissions()) {
            initGamepad()
            return
        }
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun initGamepad() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter != null && !adapter.isEnabled) {
            try {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }

        if (gamepad == null) {
            gamepad = BluetoothHidGamepad(this).also { gp ->
                gp.isWindowsDInputMode = isWindowsMode.value
                gp.onStatusChanged = {
                    runOnUiThread {
                        val prevState = hidConnectionState.value
                        hidProfileConnected.value = gp.isAppRegistered || gp.connectionState != BluetoothProfile.STATE_DISCONNECTED
                        hidAppRegistered.value = gp.isAppRegistered
                        hidConnectionState.value = gp.connectionState
                        connected.value = gp.isConnected
                        connectedDeviceName.value = gp.connectedDeviceName
                        ownDeviceName.value = gp.ownDeviceName

                        when (gp.connectionState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                prefs.edit().putString("lastDeviceAddress", gp.connectedDevice?.address).apply()
                                Toast.makeText(this, "Connected to ${gp.connectedDeviceName}", Toast.LENGTH_SHORT).show()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (!userCancelledConnect) {
                                    when (prevState) {
                                        BluetoothProfile.STATE_CONNECTING ->
                                            Toast.makeText(this, "Connecting device failed", Toast.LENGTH_SHORT).show()
                                        BluetoothProfile.STATE_CONNECTED ->
                                            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                userCancelledConnect = false
                            }
                        }
                    }
                }
                gp.start()
            }
        }
        ownDeviceName.value = gamepad?.ownDeviceName ?: ""
    }

    private fun stopGamepad() {
        gamepad?.stop()
        gamepad = null
        connected.value = false
        hidProfileConnected.value = false
        hidAppRegistered.value = false
        hidConnectionState.value = BluetoothProfile.STATE_DISCONNECTED
        connectedDeviceName.value = ""
    }

    private fun reconnectLastDevice() {
        val address = prefs.getString("lastDeviceAddress", null) ?: return
        val gp = gamepad ?: return
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val device = manager?.adapter?.bondedDevices?.find { it.address == address }
            if (device != null) gp.connectDevice(device)
        } catch (_: SecurityException) { }
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun startDiscovery() {
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.startDiscovery()
        } catch (_: SecurityException) { }
    }

    fun cancelDiscovery() {
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.cancelDiscovery()
        } catch (_: SecurityException) { }
    }

    private fun pairDevice(device: BluetoothDevice) {
        try {
            device.createBond()
        } catch (_: SecurityException) { }
    }

    fun unpairDevice(device: BluetoothDevice) {
        try {
            val method: Method = device.javaClass.getMethod("removeBond")
            method.isAccessible = true
            method.invoke(device)
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        gamepad?.stop()
        unregisterReceiver(bondReceiver)
    }
}
