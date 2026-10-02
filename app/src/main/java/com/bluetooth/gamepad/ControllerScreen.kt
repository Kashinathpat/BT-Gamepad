package com.bluetooth.gamepad

import android.bluetooth.BluetoothProfile
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.ui.theme.ControllerBg
import com.bluetooth.gamepad.ui.theme.ControllerOnBtn
import com.bluetooth.gamepad.ui.theme.DpadNormal
import com.bluetooth.gamepad.ui.theme.DpadPressed
import com.bluetooth.gamepad.ui.theme.OverlayPillLight
import com.bluetooth.gamepad.ui.theme.StatusConnected
import com.bluetooth.gamepad.ui.theme.StatusConnecting
import com.bluetooth.gamepad.ui.theme.StatusError
import com.bluetooth.gamepad.ui.theme.StickBase
import com.bluetooth.gamepad.ui.theme.StickKnob
import com.bluetooth.gamepad.ui.theme.StickLabel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val FLOATING_STICK_FRAC = 0.24f

private const val EDGE_EXCLUSION_DP = 48f

// A single full-screen pointer dispatcher reads every pointer and routes each, by PointerId, to the
// control whose region it first landed on. The control keeps the pointer until that finger lifts, so
// any number of controls can be held at once. Controls are pure visuals driven by the snapshot state
// the dispatcher owns.
private class RuntimeControl(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val onDown: (localX: Float, localY: Float) -> Unit,
    val onMove: (localX: Float, localY: Float) -> Unit,
    val onUp: () -> Unit,
    // Full release, including latched toggles and running turbo; used when the input surface restarts.
    val onReset: () -> Unit = onUp,
    // Claimed only by a real touch-down, never a finger sliding in or left over from another control.
    val freshDownOnly: Boolean = false
) {
    var pointerId: PointerId? = null
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
    val area get() = (right - left) * (bottom - top)
}

// Press count per HID button, so a button shared by several controls is released by the last holder only.
private class ButtonMixer(private val gamepad: BluetoothHidGamepad?) {
    private val counts = IntArray(16)

    fun press(index: Int) {
        if (counts[index]++ == 0) gamepad?.setButtonState(index, true)
    }

    fun release(index: Int) {
        if (counts[index] == 0) return
        if (--counts[index] == 0) gamepad?.setButtonState(index, false)
    }

    fun clear() = counts.fill(0)
}

// Sensor thread emits through here; the lock orders the final zero after any in-flight sample.
private class GyroGate {
    private var allowed = true
    var userOn = true
    var gated = false
    var holds = 0

    fun update(motion: MotionSensorManager, gamepad: BluetoothHidGamepad?) {
        val next = userOn && (!gated || holds > 0)
        synchronized(this) {
            if (next == allowed) return
            allowed = next
            motion.recenter()
            if (!next) gamepad?.setRightStickMotion(0f, 0f)
        }
    }

    fun close(gamepad: BluetoothHidGamepad?) {
        synchronized(this) {
            allowed = false
            gamepad?.setRightStickMotion(0f, 0f)
        }
    }

    fun emit(gamepad: BluetoothHidGamepad?, x: Float, y: Float) {
        synchronized(this) {
            if (allowed) gamepad?.setRightStickMotion(x, y)
        }
    }
}

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

private class ControlEnv(
    val w: Float,
    val h: Float,
    val dim: Float,
    val gamepad: BluetoothHidGamepad?,
    val isWindowsMode: Boolean,
    val mixer: ButtonMixer,
    val handler: Handler,
    val haptic: () -> Unit,
    val onGateHold: (Boolean) -> Unit,
    val pressedButtons: SnapshotStateMap<String, Boolean>,
    val stickOffsets: SnapshotStateMap<String, Offset>,
    val stickOrigins: SnapshotStateMap<String, Offset>,
    val dpadDirs: SnapshotStateMap<String, DpadState>
) {
    // Sticks currently held, by control id, so a duplicate stick is not centred while another holds it.
    val stickHolds = LinkedHashMap<String, StickHold>()
}

private class StickHold(val isRight: Boolean, val x: Float, val y: Float)

// Shoulders honour the Windows/standard swap (LB<->LT, RB<->RT).
private fun hidIndex(baseId: String, isWindowsMode: Boolean): Int? = when (baseId) {
    "A" -> BluetoothHidGamepad.BUTTON_A
    "B" -> BluetoothHidGamepad.BUTTON_B
    "X" -> BluetoothHidGamepad.BUTTON_X
    "Y" -> BluetoothHidGamepad.BUTTON_Y
    "LB" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_LB else BluetoothHidGamepad.BUTTON_LT
    "RB" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_RB else BluetoothHidGamepad.BUTTON_RT
    "LT" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_LT else BluetoothHidGamepad.BUTTON_LB
    "RT" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_RT else BluetoothHidGamepad.BUTTON_RB
    "LSB" -> BluetoothHidGamepad.BUTTON_L3
    "RSB" -> BluetoothHidGamepad.BUTTON_R3
    "SELECT" -> BluetoothHidGamepad.BUTTON_SELECT
    "START" -> BluetoothHidGamepad.BUTTON_START
    else -> null
}

private fun buildControls(env: ControlEnv, layout: ControllerLayout): List<RuntimeControl> {
    val list = ArrayList<RuntimeControl>(layout.buttons.size)
    layout.buttons.forEach { btn ->
        val btnPx = btn.sizeFrac * env.dim
        val cx = btn.xFrac * env.w
        val cy = btn.yFrac * env.h
        val left = cx - btnPx / 2f
        val top = cy - btnPx / 2f
        val rect = floatArrayOf(left, top, cx + btnPx / 2f, cy + btnPx / 2f)
        val control = when {
            btn.isStick && btn.floating -> floatingStickControl(btn, rect, env)
            btn.isStick -> stickControl(btn, rect, btnPx, env)
            btn.baseId == "DPAD" -> dpadControl(btn, rect, btnPx, env)
            btn.baseId == "TPADD" -> touchDpadControl(btn, rect, btnPx, env)
            btn.baseId == "TPADL" || btn.baseId == "TPADR" -> touchStickControl(btn, rect, btnPx, env)
            btn.isPressable -> pressControl(btn, rect, env)
            else -> null
        }
        control?.let { list.add(it) }
    }
    // Hit-testing picks the first match, so smaller controls go first: a small button overlapping a
    // stick/d-pad claims the touch, not the larger control beneath it.
    list.sortBy { it.area }
    return list
}

private fun holdStick(env: ControlEnv, id: String, isRight: Boolean, x: Float, y: Float) {
    val hold = StickHold(isRight, x, y)
    env.stickHolds[id] = hold
    sendHeldStick(env, isRight, hold)
}

private fun releaseStick(env: ControlEnv, id: String, isRight: Boolean) {
    env.stickHolds.remove(id)
    sendHeldStick(env, isRight, null)
}

// A (near-)centred copy never overrides another copy of the same stick that is still deflected.
private fun sendHeldStick(env: ControlEnv, isRight: Boolean, latest: StickHold?) {
    fun StickHold.deflected() = abs(x) > 0.1f || abs(y) > 0.1f
    val sameStick = env.stickHolds.values.filter { it.isRight == isRight }
    val active = latest?.takeIf { it.deflected() }
        ?: sameStick.lastOrNull { it.deflected() }
        ?: latest
        ?: sameStick.lastOrNull()
    sendStick(env, isRight, active?.x ?: 0f, active?.y ?: 0f)
}

private fun sendStick(env: ControlEnv, isRight: Boolean, x: Float, y: Float) {
    if (isRight) env.gamepad?.setRightStickTouch(x, y) else env.gamepad?.setLeftStick(x, y)
}

private fun stickControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    val isRight = btn.baseId == "RSTICK"
    val radius = btnPx / 2f
    val maxOffset = (btnPx - btnPx * 0.4f) / 2f  // knob is 0.4 of base
    fun apply(localX: Float, localY: Float) {
        // Absolute position from centre, vector clamped to the rim: a finger outside the circle pins
        // to the rim and stays there until it crosses back, rather than jumping to the opposite side.
        var ox = localX - radius
        var oy = localY - radius
        val dist = sqrt(ox * ox + oy * oy)
        if (dist > maxOffset && dist > 0f) {
            ox = ox / dist * maxOffset
            oy = oy / dist * maxOffset
        }
        env.stickOffsets[btn.id] = Offset(ox, oy)
        val nx = if (maxOffset > 0f) (ox / maxOffset).coerceIn(-1f, 1f) else 0f
        val ny = if (maxOffset > 0f) (oy / maxOffset).coerceIn(-1f, 1f) else 0f
        holdStick(env, btn.id, isRight, nx, ny)
    }
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly -> apply(lx, ly) },
        onMove = { lx, ly -> apply(lx, ly) },
        onUp = {
            env.stickOffsets[btn.id] = Offset.Zero
            releaseStick(env, btn.id, isRight)
        }
    )
}

private fun floatingStickControl(btn: ButtonConfig, r: FloatArray, env: ControlEnv): RuntimeControl {
    val isRight = btn.baseId == "RSTICK"
    val stickPx = FLOATING_STICK_FRAC * env.dim
    val maxOffset = stickPx * 0.3f
    var originX = 0f
    var originY = 0f
    fun apply(localX: Float, localY: Float) {
        var ox = localX - originX
        var oy = localY - originY
        val dist = sqrt(ox * ox + oy * oy)
        if (dist > maxOffset && dist > 0f) {
            ox = ox / dist * maxOffset
            oy = oy / dist * maxOffset
        }
        env.stickOffsets[btn.id] = Offset(ox, oy)
        val nx = if (maxOffset > 0f) (ox / maxOffset).coerceIn(-1f, 1f) else 0f
        val ny = if (maxOffset > 0f) (oy / maxOffset).coerceIn(-1f, 1f) else 0f
        holdStick(env, btn.id, isRight, nx, ny)
    }
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly ->
            originX = lx
            originY = ly
            env.stickOrigins[btn.id] = Offset(lx, ly)
            apply(lx, ly)
        },
        onMove = { lx, ly -> apply(lx, ly) },
        onUp = {
            env.stickOrigins.remove(btn.id)
            env.stickOffsets[btn.id] = Offset.Zero
            releaseStick(env, btn.id, isRight)
        }
    )
}

private fun dpadDirection(dx: Float, dy: Float, dead: Float): DpadState = DpadState(
    h = when { dx > dead -> DpadDir.RIGHT; dx < -dead -> DpadDir.LEFT; else -> null },
    v = when { dy < -dead -> DpadDir.UP; dy > dead -> DpadDir.DOWN; else -> null }
)

private fun applyDpad(btn: ButtonConfig, next: DpadState, env: ControlEnv) {
    val prev = env.dpadDirs[btn.id]
    if (prev != null && prev.h == next.h && prev.v == next.v) return
    val isNewPress = (next.h != null && next.h != prev?.h) || (next.v != null && next.v != prev?.v)
    env.dpadDirs[btn.id] = next
    sendEffectiveDpad(next, env)
    if (isNewPress) env.haptic()
}

private fun releaseDpad(btn: ButtonConfig, env: ControlEnv) {
    val neutral = DpadState(null, null)
    env.dpadDirs[btn.id] = neutral
    sendEffectiveDpad(neutral, env)
}

// D-pad controls share one hat: each axis comes from whichever control is holding it.
private fun sendEffectiveDpad(state: DpadState, env: ControlEnv) {
    val held = env.dpadDirs.values
    val h = state.h ?: held.firstNotNullOfOrNull { it.h }
    val v = state.v ?: held.firstNotNullOfOrNull { it.v }
    sendDpad(env.gamepad, env.isWindowsMode, h, v)
}

private fun dpadControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    val radius = btnPx / 2f
    val dead = radius * 0.25f
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly -> applyDpad(btn, dpadDirection(lx - radius, ly - radius, dead), env) },
        onMove = { lx, ly -> applyDpad(btn, dpadDirection(lx - radius, ly - radius, dead), env) },
        onUp = { releaseDpad(btn, env) }
    )
}

// Touchpad in d-pad mode: 8-way direction from where the finger first lands, so the initial touch
// sets the origin and dragging away from it picks a direction.
private fun touchDpadControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    var originX = 0f
    var originY = 0f
    val dead = btnPx * 0.12f
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly ->
            originX = lx
            originY = ly
            applyDpad(btn, dpadDirection(0f, 0f, dead), env)
        },
        onMove = { lx, ly -> applyDpad(btn, dpadDirection(lx - originX, ly - originY, dead), env) },
        onUp = { releaseDpad(btn, env) }
    )
}

// Touchpad in stick mode (trackpad style): the stick value is the offset from where the finger first
// landed, so the same drag gives the same deflection wherever it started.
private fun touchStickControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    val isRight = btn.baseId == "TPADR"
    // Full deflection when dragged ~40% of the pad width from the origin.
    val range = btnPx * 0.4f
    var originX = 0f
    var originY = 0f
    fun apply(localX: Float, localY: Float) {
        val nx = if (range > 0f) ((localX - originX) / range).coerceIn(-1f, 1f) else 0f
        val ny = if (range > 0f) ((localY - originY) / range).coerceIn(-1f, 1f) else 0f
        holdStick(env, btn.id, isRight, nx, ny)
    }
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly -> originX = lx; originY = ly; apply(lx, ly) },
        onMove = { lx, ly -> apply(lx, ly) },
        onUp = { releaseStick(env, btn.id, isRight) }
    )
}

// "active" is held or latched by the user; "engaged" is the HID state, which turbo flips while active.
private fun pressControl(btn: ButtonConfig, r: FloatArray, env: ControlEnv): RuntimeControl {
    val indices = (if (btn.baseId == "MACRO") btn.macro else listOf(btn.baseId))
        .mapNotNull { hidIndex(it, env.isWindowsMode) }
    val halfPeriodMs = 500L / btn.turboHz
    var engaged = false
    var active = false

    fun engage(on: Boolean) {
        if (on == engaged) return
        engaged = on
        indices.forEach { if (on) env.mixer.press(it) else env.mixer.release(it) }
    }

    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        env.pressedButtons[btn.id] = on
        if (btn.gyroGate) env.onGateHold(on)
    }

    val pulse = object : Runnable {
        override fun run() {
            engage(!engaged)
            env.handler.postDelayed(this, halfPeriodMs)
        }
    }

    fun release() {
        env.handler.removeCallbacks(pulse)
        engage(false)
        setActive(false)
    }

    fun hold() {
        setActive(true)
        engage(true)
        env.haptic()
    }

    return when (btn.behavior) {
        ButtonBehavior.NORMAL -> RuntimeControl(
            r[0], r[1], r[2], r[3],
            onDown = { _, _ -> hold() },
            onMove = { _, _ -> },
            onUp = { release() }
        )
        ButtonBehavior.TOGGLE -> RuntimeControl(
            r[0], r[1], r[2], r[3],
            onDown = { _, _ -> if (active) { release(); env.haptic() } else hold() },
            onMove = { _, _ -> },
            onUp = {},
            onReset = { release() },
            freshDownOnly = true
        )
        ButtonBehavior.TURBO -> RuntimeControl(
            r[0], r[1], r[2], r[3],
            onDown = { _, _ ->
                hold()
                env.handler.postDelayed(pulse, halfPeriodMs)
            },
            onMove = { _, _ -> },
            onUp = { release() }
        )
    }
}

private fun sendDpad(gamepad: BluetoothHidGamepad?, isWindowsMode: Boolean, h: DpadDir?, v: DpadDir?) {
    if (isWindowsMode) {
        gamepad?.setDpadState(
            up = v == DpadDir.UP,
            down = v == DpadDir.DOWN,
            left = h == DpadDir.LEFT,
            right = h == DpadDir.RIGHT
        )
    } else {
        val hat = when {
            v == DpadDir.UP   && h == null          -> BluetoothHidGamepad.HAT_UP
            v == DpadDir.UP   && h == DpadDir.RIGHT -> BluetoothHidGamepad.HAT_UP_RIGHT
            v == null         && h == DpadDir.RIGHT -> BluetoothHidGamepad.HAT_RIGHT
            v == DpadDir.DOWN && h == DpadDir.RIGHT -> BluetoothHidGamepad.HAT_DOWN_RIGHT
            v == DpadDir.DOWN && h == null          -> BluetoothHidGamepad.HAT_DOWN
            v == DpadDir.DOWN && h == DpadDir.LEFT  -> BluetoothHidGamepad.HAT_DOWN_LEFT
            v == null         && h == DpadDir.LEFT  -> BluetoothHidGamepad.HAT_LEFT
            v == DpadDir.UP   && h == DpadDir.LEFT  -> BluetoothHidGamepad.HAT_UP_LEFT
            else                                    -> BluetoothHidGamepad.HAT_NEUTRAL
        }
        gamepad?.setHat(hat)
    }
}

@Composable
private fun ButtonVisual(btn: ButtonConfig, size: Dp, pressed: Boolean, oled: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 60 else 120),
        label = "btnScale"
    )
    Box(modifier = Modifier.size(size).scale(scale), contentAlignment = Alignment.Center) {
        ButtonFace(btn, size, pressed, oled)
    }
}

@Composable
private fun StickVisual(size: Dp, label: String, offset: Offset, oled: Boolean) {
    val knobSize = size * 0.4f
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (oled) Modifier.border(2.dp, OledDim, CircleShape)
                else Modifier.background(StickBase, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (oled) OledDim else StickLabel)
        }
        Box(
            modifier = Modifier
                .size(knobSize)
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .then(
                    if (oled) Modifier.border(2.dp, OledBright, CircleShape)
                    else Modifier.background(StickKnob, CircleShape)
                )
        )
    }
}

@Composable
private fun FloatingStickVisual(
    zoneSize: Dp,
    stickPx: Float,
    density: Float,
    label: String,
    origin: Offset?,
    offset: Offset,
    oled: Boolean
) {
    val line = if (oled) OledDim else StickKnob.copy(alpha = 0.25f)
    Box(
        modifier = Modifier
            .size(zoneSize)
            .border(1.5.dp, line, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (origin == null) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (oled) OledDim else StickLabel)
        } else {
            val half = stickPx / 2f
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset((origin.x - half).roundToInt(), (origin.y - half).roundToInt()) }
            ) {
                StickVisual(size = (stickPx / density).dp, label = "", offset = offset, oled = oled)
            }
        }
    }
}

@Composable
private fun DpadVisual(dir: DpadState?, size: Dp, oled: Boolean) {
    val arms = listOf(
        DpadDir.UP    to Alignment.TopCenter,
        DpadDir.DOWN  to Alignment.BottomCenter,
        DpadDir.LEFT  to Alignment.CenterStart,
        DpadDir.RIGHT to Alignment.CenterEnd
    )
    val rotations = mapOf(DpadDir.UP to 180f, DpadDir.DOWN to 0f, DpadDir.LEFT to 90f, DpadDir.RIGHT to 270f)
    val h = dir?.h
    val v = dir?.v
    val arrowSize = size * 0.45f
    val inset = size * 0.065f
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        arms.forEach { (d, anchor) ->
            val held = d == h || d == v
            val tint = when {
                oled -> if (held) OledBright else OledDim
                held -> DpadPressed
                else -> DpadNormal
            }
            val offsetMod = when (d) {
                DpadDir.UP    -> Modifier.offset(y = inset)
                DpadDir.DOWN  -> Modifier.offset(y = -inset)
                DpadDir.LEFT  -> Modifier.offset(x = inset)
                DpadDir.RIGHT -> Modifier.offset(x = -inset)
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = anchor) {
                Image(
                    painter = painterResource(id = R.drawable.dpad_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(arrowSize).then(offsetMod).rotate(rotations[d]!!),
                    colorFilter = ColorFilter.tint(tint)
                )
            }
        }
    }
}

// Touchpad: a plain surface with no knob; d-pad mode lights the border while a direction is held.
@Composable
private fun TouchpadVisual(size: Dp, mode: String, dir: DpadState?, oled: Boolean) {
    val label = when (mode) { "TPADL" -> "L"; "TPADR" -> "R"; else -> "+" }
    val active = mode == "TPADD" && (dir?.h != null || dir?.v != null)
    val border = when {
        oled -> if (active) OledBright else OledDim
        active -> DpadPressed.copy(alpha = 0.5f)
        else -> StickKnob.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(if (oled) Color.Transparent else StickBase.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.5.dp, border, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = (size.value * 0.16f).sp, fontWeight = FontWeight.Bold, color = if (oled) OledDim else StickLabel)
    }
}

enum class DpadDir { UP, DOWN, LEFT, RIGHT }

data class DpadState(val h: DpadDir?, val v: DpadDir?)

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
