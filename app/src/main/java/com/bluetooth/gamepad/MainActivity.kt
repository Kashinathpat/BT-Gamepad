package com.bluetooth.gamepad

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bluetooth.gamepad.ui.theme.AppTheme
import com.bluetooth.gamepad.ui.theme.BtGamepadTheme
import java.lang.reflect.Method

enum class NavTab { CONNECT, LAYOUTS, SETTINGS }

class MainActivity : ComponentActivity() {

    private val controllerVisible = mutableStateOf(false)
    private val hidProfileConnected = mutableStateOf(false)
    private val hidAppRegistered = mutableStateOf(false)
    private val hidConnectionState = mutableStateOf(BluetoothProfile.STATE_DISCONNECTED)
    private val connectedDeviceName = mutableStateOf("")
    private val ownDeviceName = mutableStateOf("")
    private val isWindowsMode = mutableStateOf(false)
    private val appTheme = mutableStateOf(AppTheme.SYSTEM)
    private val hapticIntensity = mutableStateOf(HapticIntensity.MEDIUM)
    private val motionEnabled = mutableStateOf(false)
    private val motionSensitivity = mutableStateOf(MotionSensitivity.MEDIUM)
    private val currentTab = mutableStateOf(NavTab.CONNECT)
    private val activeLayoutId = mutableStateOf(ControllerLayout.DEFAULT_ID)
    private val editingLayout = mutableStateOf<ControllerLayout?>(null)
    // Working state for the active edit session (buttons, undo/redo, selection, dirty flag). Held
    // here so it survives the editor leaving composition during Test; discarded only on editor exit.
    private val editorSession = mutableStateOf<EditorSession?>(null)
    // Transient layout shown by the editor's "Test" action. Held in memory only — never persisted —
    // so previewing unsaved edits does not overwrite the stored layout.
    private val previewLayout = mutableStateOf<ControllerLayout?>(null)
    private val layoutsRefreshKey = mutableStateOf(0)
    private val autoReconnect = mutableStateOf(true)

    private var gamepad: BluetoothHidGamepad? = null
    private var userCancelledConnect = false
    lateinit var prefs: SharedPreferences
    lateinit var layoutRepo: LayoutRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Notification permission is optional — only require BT permissions for gamepad init
        val btGranted = results.entries
            .filter { it.key != Manifest.permission.POST_NOTIFICATIONS }
            .all { it.value }
        if (btGranted) initGamepad()
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(_context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            device ?: return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            // Only auto-connect to a device we paired from the Pair button, not e.g. earbuds.
            if (state == BluetoothDevice.BOND_BONDED && device.address == pendingPairAddress) {
                pendingPairAddress = null
                gamepad?.connectDevice(device)
            }
        }
    }
    private var pendingPairAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("data", MODE_PRIVATE)
        layoutRepo = LayoutRepository(prefs)
        isWindowsMode.value = prefs.getBoolean("isWindowsDInputMode", false)
        activeLayoutId.value = prefs.getString("activeLayoutId", ControllerLayout.DEFAULT_ID) ?: ControllerLayout.DEFAULT_ID
        appTheme.value = when (prefs.getString("appTheme", "SYSTEM")) {
            "LIGHT"  -> AppTheme.LIGHT
            "DARK"   -> AppTheme.DARK
            "AMOLED" -> AppTheme.AMOLED
            else     -> AppTheme.SYSTEM
        }
        hapticIntensity.value = when (prefs.getString("hapticIntensity", "MEDIUM")) {
            "OFF"    -> HapticIntensity.OFF
            "LIGHT"  -> HapticIntensity.LIGHT
            "STRONG" -> HapticIntensity.STRONG
            else     -> HapticIntensity.MEDIUM
        }
        motionEnabled.value = prefs.getBoolean("motionEnabled", false)
        motionSensitivity.value = when (prefs.getString("motionSensitivity", "MEDIUM")) {
            "LOW"  -> MotionSensitivity.LOW
            "HIGH" -> MotionSensitivity.HIGH
            else   -> MotionSensitivity.MEDIUM
        }
        autoReconnect.value = prefs.getBoolean("autoReconnect", true)

        // Bond state changes are sent by the Bluetooth system — must be EXPORTED
        androidx.core.content.ContextCompat.registerReceiver(
            this, bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )

        enableEdgeToEdge()
        setContent {
            BtGamepadTheme(appTheme = appTheme.value) {
                val isFullScreen = controllerVisible.value || editingLayout.value != null
                androidx.compose.runtime.LaunchedEffect(isFullScreen, controllerVisible.value) {
                    requestedOrientation = if (isFullScreen)
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    if (isFullScreen) {
                        insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    }
                    if (controllerVisible.value)
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                if (controllerVisible.value) {
                    val closeController = {
                        controllerVisible.value = false
                        previewLayout.value = null
                    }
                    BackHandler { closeController() }
                    ControllerScreen(
                        gamepad = gamepad,
                        isWindowsMode = isWindowsMode.value,
                        connectedDeviceName = connectedDeviceName.value,
                        layout = previewLayout.value
                            ?: layoutRepo.load(activeLayoutId.value)
                            ?: ControllerLayout.default(),
                        hapticIntensity = hapticIntensity.value,
                        motionEnabled = motionEnabled.value,
                        motionSensitivity = motionSensitivity.value,
                        onStopClick = closeController
                    )
                } else if (editingLayout.value != null && editorSession.value != null) {
                    LayoutEditorScreen(
                        layout = editingLayout.value!!,
                        session = editorSession.value!!,
                        repo = layoutRepo,
                        onBack = {
                            editingLayout.value = null
                            editorSession.value = null
                            layoutsRefreshKey.value++
                        },
                        onTest = { testLayout ->
                            // Preview only — never persisted. The session keeps the in-progress edits
                            // (and undo/redo) alive while the controller is shown, so returning to the
                            // editor restores everything exactly.
                            previewLayout.value = testLayout
                            controllerVisible.value = true
                        }
                    )
                } else {
                    val cs = MaterialTheme.colorScheme
                    Scaffold(
                            bottomBar = {
                                NavigationBar(containerColor = cs.surfaceContainer) {
                                    NavigationBarItem(
                                        selected = currentTab.value == NavTab.CONNECT,
                                        onClick = { currentTab.value = NavTab.CONNECT },
                                        icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                                        label = { Text("Connect", fontWeight = FontWeight.Medium) }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab.value == NavTab.LAYOUTS,
                                        onClick = { currentTab.value = NavTab.LAYOUTS },
                                        icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                        label = { Text("Layouts", fontWeight = FontWeight.Medium) }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab.value == NavTab.SETTINGS,
                                        onClick = { currentTab.value = NavTab.SETTINGS },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        label = { Text("Settings", fontWeight = FontWeight.Medium) }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            when (currentTab.value) {
                                NavTab.CONNECT -> ConnectionScreen(
                                    activity = this@MainActivity,
                                    hidProfileConnected = hidProfileConnected.value,
                                    hidAppRegistered = hidAppRegistered.value,
                                    hidConnectionState = hidConnectionState.value,
                                    connectedDeviceName = connectedDeviceName.value,
                                    ownDeviceName = ownDeviceName.value,
                                    onStartClick = { requestPermissionsAndInit() },
                                    onPairDevice = { device -> pairDevice(device) },
                                    onUnpairDevice = { device -> unpairDevice(device) },
                                    connectedDeviceAddress = gamepad?.connectedDevice?.address ?: "",
                                    connectedDevice = gamepad?.connectedDevice,
                                    activeDInputMode = gamepad?.isWindowsDInputMode ?: false,
                                    onConnectDevice = { device -> gamepad?.connectDevice(device) },
                                    onCancelConnect = { device ->
                                        userCancelledConnect = true
                                        gamepad?.cancelConnect(device)
                                    },
                                    onDisconnectDevice = { device ->
                                        userCancelledConnect = true
                                        gamepad?.cancelConnect(device)
                                    },
                                    contentPadding = innerPadding
                                )
                                NavTab.LAYOUTS -> androidx.compose.runtime.key(layoutsRefreshKey.value) {
                                    LayoutsScreen(
                                        repo = layoutRepo,
                                        connectedDeviceName = connectedDeviceName.value,
                                        onStart = { layout ->
                                            activeLayoutId.value = layout.id
                                            prefs.edit().putString("activeLayoutId", layout.id).apply()
                                            previewLayout.value = null
                                            controllerVisible.value = true
                                        },
                                        onEdit = { layout ->
                                            // Fresh working session for this edit; discarded on exit.
                                            editorSession.value = EditorSession(layout)
                                            editingLayout.value = layout
                                        },
                                        contentPadding = innerPadding
                                    )
                                }
                                NavTab.SETTINGS -> SettingsScreen(
                                    appTheme = appTheme.value,
                                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "",
                                    isWindowsMode = isWindowsMode.value,
                                    hapticIntensity = hapticIntensity.value,
                                    motionEnabled = motionEnabled.value,
                                    motionSensitivity = motionSensitivity.value,
                                    autoReconnect = autoReconnect.value,
                                    onThemeChange = { theme ->
                                        appTheme.value = theme
                                        prefs.edit().putString("appTheme", theme.name).apply()
                                    },
                                    onWindowsModeToggle = { value ->
                                        isWindowsMode.value = value
                                        prefs.edit().putBoolean("isWindowsDInputMode", value).apply()
                                        gamepad?.switchMode(value)
                                    },
                                    onHapticIntensityChange = { value ->
                                        hapticIntensity.value = value
                                        prefs.edit().putString("hapticIntensity", value.name).apply()
                                    },
                                    onMotionEnabledChange = { value ->
                                        motionEnabled.value = value
                                        prefs.edit().putBoolean("motionEnabled", value).apply()
                                    },
                                    onMotionSensitivityChange = { value ->
                                        motionSensitivity.value = value
                                        prefs.edit().putString("motionSensitivity", value.name).apply()
                                    },
                                    onAutoReconnectChange = { value ->
                                        autoReconnect.value = value
                                        prefs.edit().putBoolean("autoReconnect", value).apply()
                                    },
                                    contentPadding = innerPadding
                                )
                            }
                        }
                }
            }
        }

        requestPermissionsAndInit()
    }

    private fun requestPermissionsAndInit() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            initGamepad()
        }
    }

    // ACTION_REQUEST_ENABLE is associated with BLUETOOTH_CONNECT by lint. This runs only after that
    // permission is granted (requestPermissionsAndInit gates initGamepad), and the call is wrapped
    // to fall back to Bluetooth settings if it is ever denied — so the warning is a false positive.
    @SuppressLint("MissingPermission")
    fun initGamepad() {
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
                        connectedDeviceName.value = gp.connectedDeviceName
                        ownDeviceName.value = gp.ownDeviceName

                        when (gp.connectionState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                prefs.edit().putString("lastDeviceAddress", gp.connectedDevice?.address).apply()
                                Toast.makeText(this, "Connected to ${gp.connectedDeviceName}", Toast.LENGTH_SHORT).show()
                                // Protect the live HID session from background termination. Started
                                // per-connection (not once at init) so reconnections are covered too.
                                startForegroundService(Intent(this, GamepadForegroundService::class.java).apply {
                                    action = GamepadForegroundService.ACTION_START
                                })
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                stopService(Intent(this, GamepadForegroundService::class.java))
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
            // The foreground service is started on connection (STATE_CONNECTED), not here, so it
            // tracks the actual HID session and is correctly re-started on every reconnect.
            if (prefs.getBoolean("autoReconnect", true)) {
                reconnectLastDevice()
            }
        }
        ownDeviceName.value = gamepad?.ownDeviceName ?: ""
    }

    private fun stopGamepad() {
        gamepad?.stop()
        gamepad = null
        controllerVisible.value = false
        hidProfileConnected.value = false
        hidAppRegistered.value = false
        hidConnectionState.value = BluetoothProfile.STATE_DISCONNECTED
        connectedDeviceName.value = ""
        stopService(Intent(this, GamepadForegroundService::class.java))
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

    // Make the phone discoverable so a PC can find and pair with it (system consent dialog).
    fun makeDiscoverable() {
        try {
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
            )
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { }
        }
    }

    private fun pairDevice(device: BluetoothDevice) {
        try {
            pendingPairAddress = device.address
            device.createBond()
        } catch (_: SecurityException) { }
    }

    fun unpairDevice(device: BluetoothDevice) {
        try {
            @Suppress("DiscouragedPrivateApi")
            val method: Method = device.javaClass.getMethod("removeBond")
            method.isAccessible = true
            method.invoke(device)
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGamepad()
        try {
            unregisterReceiver(bondReceiver)
        } catch (_: Exception) {}
    }
}
