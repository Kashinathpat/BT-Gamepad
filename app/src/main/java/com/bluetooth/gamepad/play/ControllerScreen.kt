package com.bluetooth.gamepad.play

import android.bluetooth.BluetoothProfile
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.R
import com.bluetooth.gamepad.bluetooth.BluetoothHidGamepad
import com.bluetooth.gamepad.bluetooth.BtAccess
import com.bluetooth.gamepad.layout.ControllerLayout
import com.bluetooth.gamepad.layout.baseId
import com.bluetooth.gamepad.layout.isPressable
import com.bluetooth.gamepad.layout.isStick
import com.bluetooth.gamepad.motion.MotionMode
import com.bluetooth.gamepad.motion.MotionSensorManager
import com.bluetooth.gamepad.ui.theme.ControllerBg
import com.bluetooth.gamepad.ui.theme.ControllerOnBtn
import com.bluetooth.gamepad.ui.theme.OverlayPillLight
import com.bluetooth.gamepad.ui.theme.StatusConnected
import com.bluetooth.gamepad.ui.theme.StatusConnecting
import com.bluetooth.gamepad.ui.theme.StatusError
import com.bluetooth.gamepad.util.HapticIntensity
import com.bluetooth.gamepad.util.obtainVibrator
import kotlin.math.roundToInt

private const val EDGE_EXCLUSION_DP = 48f

@Composable
fun ControllerScreen(
    gamepad: BluetoothHidGamepad?,
    isWindowsMode: Boolean,
    connectedDeviceName: String,
    connectionState: Int = BluetoothProfile.STATE_CONNECTED,
    bluetoothOn: Boolean = true,
    btAccess: BtAccess = BtAccess.GRANTED,
    onRequestAccess: () -> Unit = {},
    onReconnect: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {},
    layout: ControllerLayout = ControllerLayout.default(),
    layouts: List<ControllerLayout> = emptyList(),
    onLayoutSelect: (String) -> Unit = {},
    hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    motionEnabled: Boolean = false,
    motionMode: MotionMode = MotionMode.AIM,
    motionSensitivity: Float = 90f,
    motionInvertX: Boolean = false,
    motionInvertY: Boolean = false,
    oledMode: Boolean = false,
    blockEdgeGestures: Boolean = false,
    onStopClick: () -> Unit
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current

    val motionManager = remember { MotionSensorManager(context) }
    val vibrator = remember { obtainVibrator(context) }
    val gate = remember { GyroGate() }
    val gyroOn = remember { mutableStateOf(true) }
    val motionAvailable = motionEnabled && motionManager.isModeAvailable(motionMode)
    val isConnected = connectionState == BluetoothProfile.STATE_CONNECTED

    // Finger moves are otherwise held until the next frame; sticks want every touch sample as it arrives.
    val view = LocalView.current
    DisposableEffect(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER)
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_NONE)
        }
    }

    // Keyed only on what changes the sensor or where it sends; tunables go through updateTuning below so
    // a slider drag does not restart the sensor thread and rerun bias calibration.
    DisposableEffect(motionEnabled, motionMode, gamepad) {
        if (motionAvailable) {
            motionManager.onMotion = { x, y -> gate.emit(gamepad, x, y) }
            motionManager.start(motionMode, motionSensitivity, motionInvertX, motionInvertY)
        }
        onDispose {
            motionManager.stop()
            gate.close(gamepad)
        }
    }

    LaunchedEffect(motionSensitivity, motionInvertX, motionInvertY) {
        motionManager.updateTuning(motionSensitivity, motionInvertX, motionInvertY)
    }

    // Hold-to-aim applies to Aim mode only; steering needs the sensor all the time.
    val gated = motionMode == MotionMode.AIM && layout.buttons.any { it.gyroGate && it.isPressable }
    // Read here, not inside SideEffect, so toggling it recomposes this scope and reruns the effect.
    val userOn = gyroOn.value
    SideEffect {
        gate.gated = gated
        gate.userOn = userOn
        gate.update(motionManager, gamepad)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (oledMode) Color.Black else ControllerBg)
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val dim = minOf(w, h)

        // Per-control visual state, keyed by control id and shared with the drawing pass.
        val pressedButtons = remember { mutableStateMapOf<String, Boolean>() }
        val stickOffsets = remember { mutableStateMapOf<String, Offset>() }
        val stickOrigins = remember { mutableStateMapOf<String, Offset>() }
        val dpadDirs = remember { mutableStateMapOf<String, DpadState>() }
        val mixer = remember(gamepad) { ButtonMixer(gamepad) }
        val handler = remember { Handler(Looper.getMainLooper()) }

        // Rebuilt on every (re)connect: the link clears the HID report, so controls and press counts restart released.
        val controls = remember(layout, isWindowsMode, hapticIntensity, w, h, gamepad, isConnected) {
            buildControls(
                ControlEnv(
                    w = w, h = h, dim = dim, gamepad = gamepad, isWindowsMode = isWindowsMode,
                    mixer = mixer, handler = handler,
                    haptic = { vibrateForIntensity(vibrator, hapticIntensity) },
                    onGateHold = { held ->
                        gate.holds = (gate.holds + if (held) 1 else -1).coerceAtLeast(0)
                        gate.update(motionManager, gamepad)
                    },
                    pressedButtons = pressedButtons, stickOffsets = stickOffsets,
                    stickOrigins = stickOrigins, dpadDirs = dpadDirs
                ),
                layout
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controls) {
                    // On (re)start, release everything: a finger held across a layout/orientation
                    // change is not re-adopted (it cannot be told apart from a finger sliding in), so
                    // releasing here guarantees nothing stays stuck.
                    controls.forEach { it.onReset() }
                    mixer.clear()
                    gamepad?.resetAll()
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                // Pass 1: route moves and releases for already-owned pointers. Doing
                                // releases first frees a control so a new finger landing on it in the
                                // same frame can claim it in pass 2.
                                for (change in event.changes) {
                                    val owner = controls.firstOrNull { it.pointerId == change.id } ?: continue
                                    change.consume()
                                    if (change.pressed) {
                                        owner.onMove(change.position.x - owner.left, change.position.y - owner.top)
                                    } else {
                                        owner.pointerId = null
                                        owner.onUp()
                                    }
                                }
                                // Pass 2: claim newly pressed pointers. Require pressed (or a down edge
                                // for a tap batched into one frame) so a hovering mouse never claims.
                                // Hit-test the topmost containing control, then take it only if free --
                                // a second finger on a held button is dropped, not leaked to whatever
                                // overlaps beneath it.
                                for (change in event.changes) {
                                    if (change.isConsumed) continue
                                    if (!change.pressed && !change.changedToDown()) continue
                                    val target = controls.firstOrNull {
                                        it.contains(change.position.x, change.position.y)
                                    } ?: continue
                                    if (target.pointerId != null) continue
                                    if (target.freshDownOnly && !change.changedToDown()) continue
                                    target.pointerId = change.id
                                    change.consume()
                                    target.onDown(change.position.x - target.left, change.position.y - target.top)
                                    // A tap whose down and up batched into this frame: release now.
                                    if (!change.pressed) {
                                        target.pointerId = null
                                        target.onUp()
                                    }
                                }
                            }
                        }
                    } finally {
                        // Cancelled (composable detached, app paused): release so nothing stays pressed.
                        controls.forEach {
                            it.pointerId = null
                            it.onReset()
                        }
                        mixer.clear()
                        gamepad?.resetAll()
                    }
                }
        )

        // Drawing pass: pure visuals, positioned to match the hit-regions in buildControls.
        // Android honours only ~200dp of exclusion per edge, so it is spent on controls near an edge.
        val edgePx = EDGE_EXCLUSION_DP * density
        layout.buttons.forEach { btn ->
            val btnPx = btn.sizeFrac * dim
            val btnDp = (btnPx / density).dp
            val topLeftX = (btn.xFrac * w - btnPx / 2f).roundToInt()
            val topLeftY = (btn.yFrac * h - btnPx / 2f).roundToInt()
            val nearLeft = topLeftX < edgePx
            val nearRight = topLeftX + btnPx > w - edgePx

            Box(
                modifier = Modifier
                    .offset { IntOffset(topLeftX, topLeftY) }
                    .size(btnDp)
                    .then(
                        if (blockEdgeGestures && (nearLeft || nearRight)) {
                            // Stretched to the screen edge: the back gesture starts in the edge strip, not on the control.
                            Modifier.systemGestureExclusion { coords ->
                                Rect(
                                    left = if (nearLeft) -topLeftX.toFloat() else 0f,
                                    top = 0f,
                                    right = if (nearRight) w - topLeftX else coords.size.width.toFloat(),
                                    bottom = coords.size.height.toFloat()
                                )
                            }
                        } else Modifier
                    )
                    .alpha(btn.opacity),
                contentAlignment = Alignment.Center
            ) {
                when {
                    btn.isStick && btn.floating -> FloatingStickVisual(
                        zoneSize = btnDp,
                        stickPx = FLOATING_STICK_FRAC * dim,
                        density = density,
                        label = if (btn.baseId == "LSTICK") "L" else "R",
                        origin = stickOrigins[btn.id],
                        offset = stickOffsets[btn.id] ?: Offset.Zero,
                        oled = oledMode
                    )
                    btn.isStick -> StickVisual(
                        size = btnDp,
                        label = if (btn.baseId == "LSTICK") "L" else "R",
                        offset = stickOffsets[btn.id] ?: Offset.Zero,
                        oled = oledMode
                    )
                    btn.baseId == "DPAD" -> DpadVisual(dir = dpadDirs[btn.id], size = btnDp, oled = oledMode)
                    btn.baseId.startsWith("TPAD") -> TouchpadVisual(
                        size = btnDp,
                        mode = btn.baseId,
                        dir = if (btn.baseId == "TPADD") dpadDirs[btn.id] else null,
                        oled = oledMode
                    )
                    btn.isPressable -> ButtonVisual(btn, btnDp, pressedButtons[btn.id] == true, oledMode)
                }
            }
        }

        QuickPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Sits below system overlays that some phones draw over the top centre.
                .padding(top = 34.dp),
            connectedDeviceName = connectedDeviceName,
            layouts = layouts,
            activeLayoutId = layout.id,
            onLayoutSelect = onLayoutSelect,
            motionAvailable = motionAvailable,
            gyroOn = gyroOn.value,
            onGyroToggle = { gyroOn.value = !gyroOn.value },
            onRecenter = { motionManager.recenter() },
            onBack = onStopClick
        )

        if (!isConnected) {
            ConnectionBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 78.dp),
                btAccess = btAccess,
                bluetoothOn = bluetoothOn,
                connecting = connectionState == BluetoothProfile.STATE_CONNECTING,
                onRequestAccess = onRequestAccess,
                onReconnect = onReconnect,
                onEnableBluetooth = onEnableBluetooth
            )
        }
    }
}

// Shown while no PC is receiving input, so a dropped link is never silent mid-game.
@Composable
private fun ConnectionBanner(
    modifier: Modifier,
    btAccess: BtAccess,
    bluetoothOn: Boolean,
    connecting: Boolean,
    onRequestAccess: () -> Unit,
    onReconnect: () -> Unit,
    onEnableBluetooth: () -> Unit
) {
    val (message, actionLabel, action) = when {
        btAccess == BtAccess.NEEDED -> Triple("Bluetooth access needed", "Allow", onRequestAccess)
        btAccess == BtAccess.BLOCKED -> Triple("Bluetooth access blocked", "Open settings", onRequestAccess)
        !bluetoothOn -> Triple("Bluetooth is off", "Turn on", onEnableBluetooth)
        connecting -> Triple("Connecting…", null, null)
        else -> Triple("Not connected", "Reconnect", onReconnect)
    }
    Row(
        modifier = modifier
            .background(OverlayPillLight, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).background(if (action == null) StatusConnecting else StatusError, CircleShape))
        Text(message, fontSize = 12.sp, color = ControllerOnBtn, modifier = Modifier.padding(vertical = 10.dp))
        if (actionLabel != null && action != null) {
            TextButton(onClick = action) { Text(actionLabel, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun QuickPill(
    modifier: Modifier,
    connectedDeviceName: String,
    layouts: List<ControllerLayout>,
    activeLayoutId: String,
    onLayoutSelect: (String) -> Unit,
    motionAvailable: Boolean,
    gyroOn: Boolean,
    onGyroToggle: () -> Unit,
    onRecenter: () -> Unit,
    onBack: () -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    val layoutMenu = remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .alpha(if (expanded.value) 1f else 0.6f)
            .background(OverlayPillLight, RoundedCornerShape(24.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!expanded.value) {
            IconButton(onClick = { expanded.value = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "Quick menu", tint = ControllerOnBtn)
            }
        } else {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ControllerOnBtn)
            }
            if (layouts.size > 1) {
                Box {
                    IconButton(onClick = { layoutMenu.value = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Layers, contentDescription = "Switch layout", tint = ControllerOnBtn)
                    }
                    DropdownMenu(expanded = layoutMenu.value, onDismissRequest = { layoutMenu.value = false }) {
                        layouts.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.name) },
                                trailingIcon = if (l.id == activeLayoutId) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = {
                                    layoutMenu.value = false
                                    onLayoutSelect(l.id)
                                }
                            )
                        }
                    }
                }
            }
            if (motionAvailable) {
                IconButton(onClick = onGyroToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (gyroOn) Icons.Default.Explore else Icons.Default.ExploreOff,
                        contentDescription = if (gyroOn) "Turn gyro off" else "Turn gyro on",
                        tint = ControllerOnBtn
                    )
                }
                IconButton(onClick = onRecenter, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter motion", tint = ControllerOnBtn)
                }
            }
            if (connectedDeviceName.isNotEmpty()) {
                Text(
                    text = connectedDeviceName,
                    fontSize = 11.sp,
                    color = StatusConnected,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            IconButton(onClick = { expanded.value = false }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Collapse", tint = ControllerOnBtn)
            }
        }
    }
}

private fun vibrateForIntensity(vibrator: Vibrator, intensity: HapticIntensity) {
    if (intensity == HapticIntensity.OFF || !vibrator.hasVibrator()) return
    val ms = when (intensity) {
        HapticIntensity.LIGHT  -> 20L
        HapticIntensity.MEDIUM -> 40L
        HapticIntensity.STRONG -> 70L
        HapticIntensity.OFF    -> return
    }
    val amplitude = when (intensity) {
        HapticIntensity.LIGHT  -> 60
        HapticIntensity.MEDIUM -> 120
        HapticIntensity.STRONG -> 255
        HapticIntensity.OFF    -> return
    }
    vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude))
}
