package com.bluetooth.gamepad

import android.app.ActivityManager
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
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.Modifier
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
    private val motionMode = mutableStateOf(MotionMode.AIM)
    // Gyro sensitivity: degrees/second of rotation for full stick deflection (lower = faster).
    private val motionSensitivity = mutableStateOf(90f)
    private val motionInvertX = mutableStateOf(false)
    private val motionInvertY = mutableStateOf(false)
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
    private val showAllDevices = mutableStateOf(false)
    private val oledMode = mutableStateOf(false)
    private val blockEdgeGestures = mutableStateOf(true)
    private val pinScreen = mutableStateOf(false)

    private var gamepad: BluetoothHidGamepad? = null
    private var userCancelledConnect = false
    lateinit var prefs: SharedPreferences
    lateinit var layoutRepo: LayoutRepository

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            reconnectWhenOn = false
        } else if (reconnectWhenOn && isBluetoothEnabled()) {
            // Already on, so no STATE_ON broadcast will follow.
            reconnectWhenOn = false
            reconnectLastDevice()
        }
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var notificationsAsked = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (allowed) {
            btAccess.value = BtAccess.GRANTED
            initGamepad()
            requestNotificationsIfNeeded()
        } else {
            btAccess.value = if (isPermanentlyDenied(Manifest.permission.BLUETOOTH_CONNECT)) BtAccess.BLOCKED else BtAccess.NEEDED
        }
    }

    private var afterPermission: (() -> Unit)? = null
    private var pendingPermission = ""
    private val actionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        val action = afterPermission
        afterPermission = null
        when {
            allowed -> action?.invoke()
            isPermanentlyDenied(pendingPermission) -> {
                Toast.makeText(this, "Allow it in app settings", Toast.LENGTH_SHORT).show()
                openAppSettings()
            }
            else -> Toast.makeText(this, "Permission is needed for this", Toast.LENGTH_SHORT).show()
        }
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
                connectTo(device)
            }
        }
    }
    private var pendingPairAddress: String? = null

    private val bluetoothOn = mutableStateOf(true)
    private val btAccess = mutableStateOf(BtAccess.NEEDED)
    private val connectingName = mutableStateOf("")
    private val connectFailedName = mutableStateOf("")
    private var connectingDevice: BluetoothDevice? = null
    private var switchingTo: BluetoothDevice? = null
    // Set when the user turns Bluetooth on from the app, so the last device is resumed once it is up.
    private var reconnectWhenOn = false

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(_context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            bluetoothOn.value = state == BluetoothAdapter.STATE_ON
            if (state == BluetoothAdapter.STATE_ON && reconnectWhenOn) {
                reconnectWhenOn = false
                reconnectLastDevice()
            }
        }
    }

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
        motionMode.value = if (prefs.getString("motionMode", "AIM") == "STEERING")
            MotionMode.STEERING else MotionMode.AIM
        motionSensitivity.value = prefs.getFloat("motionSensDps", 90f)
        motionInvertX.value = prefs.getBoolean("motionInvertX", false)
        motionInvertY.value = prefs.getBoolean("motionInvertY", false)
        autoReconnect.value = prefs.getBoolean("autoReconnect", true)
        showAllDevices.value = prefs.getBoolean("showAllDevices", false)
        oledMode.value = prefs.getBoolean("oledMode", false)
        blockEdgeGestures.value = prefs.getBoolean("blockEdgeGestures", true)
        pinScreen.value = prefs.getBoolean("pinScreen", false)

        // Bond state changes are sent by the Bluetooth system — must be EXPORTED
        androidx.core.content.ContextCompat.registerReceiver(
            this, bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        bluetoothOn.value = isBluetoothEnabled()
        btAccess.value = if (hasConnectPermission()) BtAccess.GRANTED else BtAccess.NEEDED
        androidx.core.content.ContextCompat.registerReceiver(
            this, btStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )

        enableEdgeToEdge()
        setContent {
            BtGamepadTheme(appTheme = appTheme.value) {
                val isFullScreen = controllerVisible.value || editingLayout.value != null
                androidx.compose.runtime.LaunchedEffect(isFullScreen, controllerVisible.value, pinScreen.value) {
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
                    val pinned = getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
                    val wantPinned = controllerVisible.value && pinScreen.value
                    try {
                        if (wantPinned && !pinned) startLockTask()
                        if (!wantPinned && pinned) stopLockTask()
                    } catch (_: Exception) {}
                }

                if (controllerVisible.value) {
                    val closeController = {
                        controllerVisible.value = false
                        previewLayout.value = null
                    }
                    // Some phones deliver an edge swipe as Back despite exclusion, so Back is ignored while blocking.
                    BackHandler { if (!blockEdgeGestures.value) closeController() }
                    val playLayouts = androidx.compose.runtime.remember(layoutsRefreshKey.value, activeLayoutId.value) {
                        layoutRepo.getAll()
                    }
                    ControllerScreen(
                        gamepad = gamepad,
                        isWindowsMode = isWindowsMode.value,
                        connectedDeviceName = connectedDeviceName.value,
                        connectionState = hidConnectionState.value,
                        bluetoothOn = bluetoothOn.value,
                        btAccess = btAccess.value,
                        onRequestAccess = { requestBluetoothAccess() },
                        onReconnect = { connectLastDevice() },
                        onEnableBluetooth = { requestEnableBluetooth() },
                        layout = previewLayout.value
                            ?: layoutRepo.load(activeLayoutId.value)
                            ?: ControllerLayout.default(),
                        // A Test preview shows unsaved edits, so switching layouts is not offered there.
                        layouts = if (previewLayout.value == null) playLayouts else emptyList(),
                        onLayoutSelect = { id ->
                            activeLayoutId.value = id
                            prefs.edit().putString("activeLayoutId", id).apply()
                        },
                        hapticIntensity = hapticIntensity.value,
                        motionEnabled = motionEnabled.value,
                        motionMode = motionMode.value,
                        motionSensitivity = motionSensitivity.value,
                        motionInvertX = motionInvertX.value,
                        motionInvertY = motionInvertY.value,
                        oledMode = oledMode.value,
                        blockEdgeGestures = blockEdgeGestures.value,
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
                            // Each tab has its own Scaffold; consume the bottom inset so it is not applied twice.
                            Box(Modifier.consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))) {
                                when (currentTab.value) {
                                    NavTab.CONNECT -> ConnectionScreen(
                                        activity = this@MainActivity,
                                        hidProfileConnected = hidProfileConnected.value,
                                        hidAppRegistered = hidAppRegistered.value,
                                        hidConnectionState = hidConnectionState.value,
                                        connectedDeviceName = connectedDeviceName.value,
                                        ownDeviceName = ownDeviceName.value,
                                        onStartClick = { connectLastDevice() },
                                        btAccess = btAccess.value,
                                        onRequestAccess = { requestBluetoothAccess() },
                                        connectingName = connectingName.value,
                                        onCancelConnecting = { cancelConnecting() },
                                        connectFailedName = connectFailedName.value,
                                        onScan = { scanForDevices() },
                                        onMakeVisible = { makeVisible() },
                                        bluetoothOn = bluetoothOn.value,
                                        onEnableBluetooth = { requestEnableBluetooth() },
                                        onPairDevice = { device -> pairDevice(device) },
                                        onUnpairDevice = { device -> unpairDevice(device) },
                                        connectedDeviceAddress = gamepad?.connectedDevice?.address ?: "",
                                        connectedDevice = gamepad?.connectedDevice,
                                        lastDeviceAddress = prefs.getString("lastDeviceAddress", null) ?: "",
                                        showAllDevices = showAllDevices.value,
                                        onShowAllDevicesChange = { value ->
                                            showAllDevices.value = value
                                            prefs.edit().putBoolean("showAllDevices", value).apply()
                                        },
                                        activeDInputMode = gamepad?.isWindowsDInputMode ?: false,
                                        onConnectDevice = { device -> connectTo(device) },
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
                                        motionMode = motionMode.value,
                                        motionSensitivity = motionSensitivity.value,
                                                    motionInvertX = motionInvertX.value,
                                        motionInvertY = motionInvertY.value,
                                        autoReconnect = autoReconnect.value,
                                        oledMode = oledMode.value,
                                        blockEdgeGestures = blockEdgeGestures.value,
                                        pinScreen = pinScreen.value,
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
                                        onMotionModeChange = { value ->
                                            motionMode.value = value
                                            prefs.edit().putString("motionMode", value.name).apply()
                                        },
                                        onMotionSensitivityChange = { value ->
                                            motionSensitivity.value = value
                                            prefs.edit().putFloat("motionSensDps", value).apply()
                                        },
                                        onMotionInvertXChange = { value ->
                                            motionInvertX.value = value
                                            prefs.edit().putBoolean("motionInvertX", value).apply()
                                        },
                                        onMotionInvertYChange = { value ->
                                            motionInvertY.value = value
                                            prefs.edit().putBoolean("motionInvertY", value).apply()
                                        },
                                        onAutoReconnectChange = { value ->
                                            autoReconnect.value = value
                                            prefs.edit().putBoolean("autoReconnect", value).apply()
                                        },
                                        onOledModeChange = { value ->
                                            oledMode.value = value
                                            prefs.edit().putBoolean("oledMode", value).apply()
                                        },
                                        onBlockEdgeGesturesChange = { value ->
                                            blockEdgeGestures.value = value
                                            prefs.edit().putBoolean("blockEdgeGestures", value).apply()
                                        },
                                        onPinScreenChange = { value ->
                                            pinScreen.value = value
                                            prefs.edit().putBoolean("pinScreen", value).apply()
                                        },
                                        contentPadding = innerPadding
                                    )
                                }
                            }
                        }
                }
            }
        }

        if (btAccess.value == BtAccess.GRANTED) initGamepad()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun requestBluetoothAccess() {
        if (btAccess.value == BtAccess.BLOCKED) {
            openAppSettings()
            return
        }
        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun withPermission(permission: String, action: () -> Unit) {
        if (granted(permission)) {
            action()
        } else {
            afterPermission = action
            pendingPermission = permission
            actionPermissionLauncher.launch(permission)
        }
    }

    private fun scanForDevices() = withPermission(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
        else Manifest.permission.ACCESS_FINE_LOCATION
    ) { startDiscovery() }

    private fun makeVisible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) withPermission(Manifest.permission.BLUETOOTH_ADVERTISE) { makeDiscoverable() }
        else makeDiscoverable()
    }

    private fun connectLastDevice() {
        if (!reconnectLastDevice()) Toast.makeText(this, "Pick a device below", Toast.LENGTH_SHORT).show()
    }

    private fun connectTo(device: BluetoothDevice) {
        connectingDevice = device
        connectingName.value = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
        connectFailedName.value = ""
        val gp = gamepad ?: return
        val current = gp.connectedDevice
        // The stack serves one host and ignores a connect while linked, so drop the current PC first.
        if (gp.connectionState == BluetoothProfile.STATE_CONNECTED && current != null && current.address != device.address) {
            switchingTo = device
            userCancelledConnect = true
            gp.cancelConnect(current)
            return
        }
        gp.connectDevice(device)
    }

    private fun cancelConnecting() {
        val device = connectingDevice ?: return
        if (switchingTo != null) {
            switchingTo = null
            connectingName.value = ""
            connectingDevice = null
            return
        }
        userCancelledConnect = true
        gamepad?.cancelConnect(device)
    }

    private fun requestNotificationsIfNeeded() {
        if (notificationsAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (granted(Manifest.permission.POST_NOTIFICATIONS)) return
        notificationsAsked = true
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // A denial without rationale means "don't ask again" only if it was denied before;
    // a first dialog dismissed by tapping outside looks the same.
    private fun isPermanentlyDenied(permission: String): Boolean {
        val key = "denied_$permission"
        if (shouldShowRequestPermissionRationale(permission)) {
            prefs.edit().putBoolean(key, true).apply()
            return false
        }
        return prefs.getBoolean(key, false)
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun isBluetoothEnabled(): Boolean = try {
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
    } catch (_: Exception) { false }

    // Lint ties ACTION_REQUEST_ENABLE to BLUETOOTH_CONNECT; a missing grant throws and falls back to settings.
    @SuppressLint("MissingPermission")
    private fun requestEnableBluetooth() {
        reconnectWhenOn = true
        try {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
            reconnectWhenOn = false
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    fun initGamepad() {
        // The service can stop the gamepad without this activity being destroyed.
        if (gamepad !== BluetoothHidGamepad.current) gamepad = null
        if (gamepad == null) {
            gamepad = BluetoothHidGamepad(this).also { gp ->
                gp.isWindowsDInputMode = isWindowsMode.value
                gp.onStatusChanged = {
                    runOnUiThread {
                        val prevState = hidConnectionState.value
                        hidProfileConnected.value = gp.isAppRegistered || gp.connectionState != BluetoothProfile.STATE_DISCONNECTED
                        hidAppRegistered.value = gp.isAppRegistered
                        hidConnectionState.value = gp.connectionState
                        updateGamepadService(gp.connectionState)
                        val attemptName = connectingName.value
                        val next = switchingTo
                        if (next != null && gp.connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                            switchingTo = null
                            userCancelledConnect = false
                            gp.connectDevice(next)
                            return@runOnUiThread
                        }
                        // Cleared only when an attempt ends; a queued connect passes through DISCONNECTED first.
                        if (gp.connectionState == BluetoothProfile.STATE_CONNECTED ||
                            (gp.connectionState == BluetoothProfile.STATE_DISCONNECTED && prevState != BluetoothProfile.STATE_DISCONNECTED)
                        ) {
                            connectingName.value = ""
                            connectingDevice = null
                        }
                        connectedDeviceName.value = gp.connectedDeviceName
                        ownDeviceName.value = gp.ownDeviceName

                        when (gp.connectionState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                prefs.edit().putString("lastDeviceAddress", gp.connectedDevice?.address).apply()
                                connectFailedName.value = ""
                                Toast.makeText(this, "Connected to ${gp.connectedDeviceName}", Toast.LENGTH_SHORT).show()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (!userCancelledConnect) {
                                    when (prevState) {
                                        BluetoothProfile.STATE_CONNECTING ->
                                            connectFailedName.value = attemptName.ifEmpty { "the device" }
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
            if (prefs.getBoolean("autoReconnect", true)) {
                reconnectLastDevice()
            }
        }
        ownDeviceName.value = gamepad?.ownDeviceName ?: ""
    }

    // The stack unregisters a HID app whose process drops below foreground-service importance, so a link
    // that should survive leaving the app needs the service; with no link the notification would only mislead.
    private fun updateGamepadService(state: Int) {
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            stopService(Intent(this, GamepadForegroundService::class.java))
            return
        }
        try {
            startForegroundService(Intent(this, GamepadForegroundService::class.java))
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        bluetoothOn.value = isBluetoothEnabled()
        if (hasConnectPermission()) {
            if (btAccess.value != BtAccess.GRANTED) {
                btAccess.value = BtAccess.GRANTED
                requestNotificationsIfNeeded()
            }
            if (gamepad == null || gamepad !== BluetoothHidGamepad.current) initGamepad()
        }
        val gp = gamepad ?: return
        updateGamepadService(gp.connectionState)
        // Leaving the app without a link lets the stack drop the registration, so take it back on return.
        if (!gp.isAppRegistered) gp.retryRegister()
    }

    private fun stopGamepad() {
        val app = applicationContext
        // Deferred: skip if the app was relaunched meanwhile and a new instance owns the service.
        val stopService: () -> Unit = {
            if (BluetoothHidGamepad.current == null) app.stopService(Intent(app, GamepadForegroundService::class.java))
        }
        val gp = gamepad
        gamepad = null
        controllerVisible.value = false
        hidProfileConnected.value = false
        hidAppRegistered.value = false
        hidConnectionState.value = BluetoothProfile.STATE_DISCONNECTED
        connectedDeviceName.value = ""
        if (gp != null) gp.stop(stopService) else stopService()
    }

    private fun reconnectLastDevice(): Boolean {
        val address = prefs.getString("lastDeviceAddress", null) ?: return false
        if (gamepad == null) return false
        return try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val device = manager?.adapter?.bondedDevices?.find { it.address == address } ?: return false
            connectTo(device)
            true
        } catch (_: SecurityException) { false }
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
    private fun makeDiscoverable() {
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
        try {
            unregisterReceiver(btStateReceiver)
        } catch (_: Exception) {}
    }
}
