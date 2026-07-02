package com.bluetooth.gamepad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.ui.theme.BtnA
import com.bluetooth.gamepad.ui.theme.BtnB
import com.bluetooth.gamepad.ui.theme.BtnPrimary
import com.bluetooth.gamepad.ui.theme.ControllerOnBtn
import com.bluetooth.gamepad.ui.theme.OverlayPillLight
import com.bluetooth.gamepad.ui.theme.StickLabel
import com.bluetooth.gamepad.ui.theme.BtnSecondary
import com.bluetooth.gamepad.ui.theme.BtnX
import com.bluetooth.gamepad.ui.theme.BtnY
import com.bluetooth.gamepad.ui.theme.ControllerBg
import com.bluetooth.gamepad.ui.theme.DpadNormal
import com.bluetooth.gamepad.ui.theme.DpadPressed
import com.bluetooth.gamepad.ui.theme.StatusConnected
import com.bluetooth.gamepad.ui.theme.StickBase
import com.bluetooth.gamepad.ui.theme.StickKnob
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    val onUp: () -> Unit
) {
    var pointerId: PointerId? = null
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
    val area get() = (right - left) * (bottom - top)
}

@Composable
fun ControllerScreen(
    gamepad: BluetoothHidGamepad?,
    isWindowsMode: Boolean,
    connectedDeviceName: String,
    layout: ControllerLayout = ControllerLayout.default(),
    hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    motionEnabled: Boolean = false,
    motionSensitivity: MotionSensitivity = MotionSensitivity.MEDIUM,
    onStopClick: () -> Unit
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current

    val gyroX = remember { mutableFloatStateOf(0f) }
    val gyroY = remember { mutableFloatStateOf(0f) }
    val motionManager = remember { MotionSensorManager(context) }
    val vibrator = remember { obtainVibrator(context) }

    DisposableEffect(motionEnabled, motionSensitivity) {
        if (motionEnabled && motionManager.isSupported) {
            val scale = MotionSensorManager.sensitivityScale(motionSensitivity)
            motionManager.onMotion = { x, y ->
                // Sensor fires on a background thread; marshal to main before touching Compose state.
                mainHandler.post {
                    gyroX.floatValue = (x * scale).coerceIn(-1f, 1f)
                    gyroY.floatValue = (y * scale).coerceIn(-1f, 1f)
                }
            }
            motionManager.start(motionSensitivity)
        }
        onDispose {
            motionManager.stop()
            gyroX.floatValue = 0f
            gyroY.floatValue = 0f
            gamepad?.setRightStickMotion(0f, 0f)
        }
    }

    // Gyro is the sole writer of the right stick's motion component; touch writes its touch component
    // separately and the gamepad composes the two.
    LaunchedEffect(gyroX.floatValue, gyroY.floatValue) {
        if (motionEnabled) gamepad?.setRightStickMotion(gyroX.floatValue, gyroY.floatValue)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ControllerBg)
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val dim = minOf(w, h)

        // Per-control visual state, keyed by control id and shared with the drawing pass.
        val pressedButtons = remember { mutableStateMapOf<String, Boolean>() }
        val stickOffsets = remember { mutableStateMapOf<String, Offset>() }
        val dpadDirs = remember { mutableStateMapOf<String, DpadState>() }

        val controls = remember(layout, isWindowsMode, hapticIntensity, w, h) {
            buildControls(
                layout, w, h, dim, gamepad, isWindowsMode, hapticIntensity, vibrator,
                pressedButtons, stickOffsets, dpadDirs
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controls) {
                    // On (re)start, release everything: a finger held across a layout/orientation
                    // change is not re-adopted (it cannot be told apart from a finger sliding in), so
                    // releasing here guarantees nothing stays stuck.
                    controls.forEach { it.onUp() }
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
                            if (it.pointerId != null) { it.pointerId = null; it.onUp() }
                        }
                        gamepad?.resetAll()
                    }
                }
        )

        // Drawing pass: pure visuals, positioned to match the hit-regions in buildControls.
        layout.buttons.forEach { btn ->
            val btnPx = btn.sizeFrac * dim
            val btnDp = (btnPx / density).dp
            val topLeftX = (btn.xFrac * w - btnPx / 2f).roundToInt()
            val topLeftY = (btn.yFrac * h - btnPx / 2f).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(topLeftX, topLeftY) }
                    .size(btnDp),
                contentAlignment = Alignment.Center
            ) {
                when (btn.baseId) {
                    "LSTICK", "RSTICK" -> StickVisual(
                        size = btnDp,
                        label = if (btn.baseId == "LSTICK") "L" else "R",
                        offset = stickOffsets[btn.id] ?: Offset.Zero
                    )
                    "DPAD" -> DpadVisual(dir = dpadDirs[btn.id], size = btnDp)
                    "TPADL", "TPADR", "TPADD" -> TouchpadVisual(
                        size = btnDp,
                        mode = btn.baseId,
                        dir = if (btn.baseId == "TPADD") dpadDirs[btn.id] else null
                    )
                    else -> ButtonVisual(
                        spec = buttonSpec(btn.baseId, btn.label, btnDp.value),
                        size = btnDp,
                        pressed = pressedButtons[btn.id] == true
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 4.dp, start = 4.dp)
                .background(OverlayPillLight, RoundedCornerShape(24.dp))
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onStopClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ControllerOnBtn)
            }
            if (connectedDeviceName.isNotEmpty()) {
                Text(text = connectedDeviceName, fontSize = 11.sp, color = StatusConnected)
            }
        }
    }
}

private data class ButtonSpec(
    val label: String,
    val shape: Shape,
    val color: Color,
    val fontSize: Int,
    val buttonIndex: Int?  // HID index; null for decorative buttons or shoulders (see shoulderIndex)
)

private fun buttonSpec(baseId: String, fallbackLabel: String, btnDpValue: Float): ButtonSpec = when (baseId) {
    "A"  -> ButtonSpec("A",  CircleShape, BtnA, (btnDpValue * 0.3f).toInt(), BluetoothHidGamepad.BUTTON_A)
    "B"  -> ButtonSpec("B",  CircleShape, BtnB, (btnDpValue * 0.3f).toInt(), BluetoothHidGamepad.BUTTON_B)
    "X"  -> ButtonSpec("X",  CircleShape, BtnX, (btnDpValue * 0.3f).toInt(), BluetoothHidGamepad.BUTTON_X)
    "Y"  -> ButtonSpec("Y",  CircleShape, BtnY, (btnDpValue * 0.3f).toInt(), BluetoothHidGamepad.BUTTON_Y)
    "LB" -> ButtonSpec("LB", RoundedCornerShape(8.dp), BtnPrimary,   (btnDpValue * 0.25f).toInt(), null)
    "RB" -> ButtonSpec("RB", RoundedCornerShape(8.dp), BtnPrimary,   (btnDpValue * 0.25f).toInt(), null)
    "LT" -> ButtonSpec("LT", RoundedCornerShape(8.dp), BtnSecondary, (btnDpValue * 0.25f).toInt(), null)
    "RT" -> ButtonSpec("RT", RoundedCornerShape(8.dp), BtnSecondary, (btnDpValue * 0.25f).toInt(), null)
    "LSB"-> ButtonSpec("LSB", CircleShape, BtnSecondary, (btnDpValue * 0.22f).toInt(), BluetoothHidGamepad.BUTTON_L3)
    "RSB"-> ButtonSpec("RSB", CircleShape, BtnSecondary, (btnDpValue * 0.22f).toInt(), BluetoothHidGamepad.BUTTON_R3)
    "SELECT" -> ButtonSpec("SEL",   RoundedCornerShape(50), BtnSecondary, (btnDpValue * 0.2f).toInt(), BluetoothHidGamepad.BUTTON_SELECT)
    "START"  -> ButtonSpec("START", RoundedCornerShape(50), BtnSecondary, (btnDpValue * 0.2f).toInt(), BluetoothHidGamepad.BUTTON_START)
    else -> ButtonSpec(fallbackLabel, CircleShape, BtnPrimary, (btnDpValue * 0.25f).toInt(), null)
}

// Shoulder HID index, honouring the Windows/standard swap (LB<->LT, RB<->RT).
private fun shoulderIndex(baseId: String, isWindowsMode: Boolean): Int = when (baseId) {
    "LB" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_LB else BluetoothHidGamepad.BUTTON_LT
    "RB" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_RB else BluetoothHidGamepad.BUTTON_RT
    "LT" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_LT else BluetoothHidGamepad.BUTTON_LB
    else -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_RT else BluetoothHidGamepad.BUTTON_RB
}

private fun buildControls(
    layout: ControllerLayout,
    w: Float,
    h: Float,
    dim: Float,
    gamepad: BluetoothHidGamepad?,
    isWindowsMode: Boolean,
    hapticIntensity: HapticIntensity,
    vibrator: Vibrator,
    pressedButtons: SnapshotStateMap<String, Boolean>,
    stickOffsets: SnapshotStateMap<String, Offset>,
    dpadDirs: SnapshotStateMap<String, DpadState>
): List<RuntimeControl> {
    val list = ArrayList<RuntimeControl>(layout.buttons.size)
    layout.buttons.forEach { btn ->
        val btnPx = btn.sizeFrac * dim
        val cx = btn.xFrac * w
        val cy = btn.yFrac * h
        val left = cx - btnPx / 2f
        val top = cy - btnPx / 2f
        val right = cx + btnPx / 2f
        val bottom = cy + btnPx / 2f
        val radius = btnPx / 2f

        when (btn.baseId) {
            "LSTICK", "RSTICK" -> {
                val isRight = btn.baseId == "RSTICK"
                val maxOffset = (btnPx - btnPx * 0.4f) / 2f  // knob is 0.4 of base
                fun apply(localX: Float, localY: Float) {
                    // Absolute position from centre, vector clamped to the rim: a finger outside the
                    // circle pins to the rim and stays there until it crosses back, rather than
                    // jumping to the opposite side.
                    var ox = localX - radius
                    var oy = localY - radius
                    val dist = sqrt(ox * ox + oy * oy)
                    if (dist > maxOffset && dist > 0f) {
                        ox = ox / dist * maxOffset
                        oy = oy / dist * maxOffset
                    }
                    stickOffsets[btn.id] = Offset(ox, oy)
                    val nx = if (maxOffset > 0f) (ox / maxOffset).coerceIn(-1f, 1f) else 0f
                    val ny = if (maxOffset > 0f) (oy / maxOffset).coerceIn(-1f, 1f) else 0f
                    if (isRight) gamepad?.setRightStickTouch(nx, ny) else gamepad?.setLeftStick(nx, ny)
                }
                list.add(
                    RuntimeControl(
                        left, top, right, bottom,
                        onDown = { lx, ly -> apply(lx, ly) },
                        onMove = { lx, ly -> apply(lx, ly) },
                        onUp = {
                            stickOffsets[btn.id] = Offset.Zero
                            if (isRight) gamepad?.setRightStickTouch(0f, 0f) else gamepad?.setLeftStick(0f, 0f)
                        }
                    )
                )
            }
            "DPAD" -> {
                val dead = radius * 0.25f
                fun apply(localX: Float, localY: Float) {
                    val dx = localX - radius
                    val dy = localY - radius
                    val newH = when { dx > dead -> DpadDir.RIGHT; dx < -dead -> DpadDir.LEFT; else -> null }
                    val newV = when { dy < -dead -> DpadDir.UP;  dy > dead  -> DpadDir.DOWN; else -> null }
                    val prev = dpadDirs[btn.id]
                    if (prev == null || prev.h != newH || prev.v != newV) {
                        val isNewPress = (newH != null && newH != prev?.h) || (newV != null && newV != prev?.v)
                        if (isNewPress) vibrateForIntensity(vibrator, hapticIntensity)
                        dpadDirs[btn.id] = DpadState(newH, newV)
                        sendDpad(gamepad, isWindowsMode, newH, newV)
                    }
                }
                list.add(
                    RuntimeControl(
                        left, top, right, bottom,
                        onDown = { lx, ly -> apply(lx, ly) },
                        onMove = { lx, ly -> apply(lx, ly) },
                        onUp = {
                            dpadDirs[btn.id] = DpadState(null, null)
                            sendDpad(gamepad, isWindowsMode, null, null)
                        }
                    )
                )
            }
            "TPADL", "TPADR", "TPADD" -> {
                // Touchpad: a plain surface mimicking a chosen control. The mapped mode is baked into
                // the base id at placement (TPADL=left stick, TPADR=right stick, TPADD=d-pad).
                when (btn.baseId) {
                    "TPADD" -> {
                        // D-pad mode: 8-way direction taken from where the finger first lands, so the
                        // initial touch sets the origin and dragging away from it picks a direction.
                        var originX = 0f
                        var originY = 0f
                        val dead = btnPx * 0.12f
                        fun apply(localX: Float, localY: Float) {
                            val dx = localX - originX
                            val dy = localY - originY
                            val newH = when { dx > dead -> DpadDir.RIGHT; dx < -dead -> DpadDir.LEFT; else -> null }
                            val newV = when { dy < -dead -> DpadDir.UP;  dy > dead  -> DpadDir.DOWN; else -> null }
                            val prev = dpadDirs[btn.id]
                            if (prev == null || prev.h != newH || prev.v != newV) {
                                val isNewPress = (newH != null && newH != prev?.h) || (newV != null && newV != prev?.v)
                                if (isNewPress) vibrateForIntensity(vibrator, hapticIntensity)
                                dpadDirs[btn.id] = DpadState(newH, newV)
                                sendDpad(gamepad, isWindowsMode, newH, newV)
                            }
                        }
                        list.add(
                            RuntimeControl(
                                left, top, right, bottom,
                                onDown = { lx, ly -> originX = lx; originY = ly; apply(lx, ly) },
                                onMove = { lx, ly -> apply(lx, ly) },
                                onUp = {
                                    dpadDirs[btn.id] = DpadState(null, null)
                                    sendDpad(gamepad, isWindowsMode, null, null)
                                }
                            )
                        )
                    }
                    else -> {
                        // Stick mode (trackpad style): the point where the finger first lands is the
                        // origin ("centre") for that touch. The stick value is the offset from that
                        // origin, so the same physical drag gives the same deflection wherever you
                        // started. Releasing springs to centre; the next touch sets a fresh origin.
                        val isRight = btn.baseId == "TPADR"
                        // Full deflection when dragged ~40% of the pad width from the origin.
                        val range = btnPx * 0.4f
                        var originX = 0f
                        var originY = 0f
                        fun apply(localX: Float, localY: Float) {
                            val nx = if (range > 0f) ((localX - originX) / range).coerceIn(-1f, 1f) else 0f
                            val ny = if (range > 0f) ((localY - originY) / range).coerceIn(-1f, 1f) else 0f
                            if (isRight) gamepad?.setRightStickTouch(nx, ny) else gamepad?.setLeftStick(nx, ny)
                        }
                        list.add(
                            RuntimeControl(
                                left, top, right, bottom,
                                onDown = { lx, ly -> originX = lx; originY = ly; apply(lx, ly) },
                                onMove = { lx, ly -> apply(lx, ly) },
                                onUp = {
                                    if (isRight) gamepad?.setRightStickTouch(0f, 0f) else gamepad?.setLeftStick(0f, 0f)
                                }
                            )
                        )
                    }
                }
            }
            else -> {
                val index = when (btn.baseId) {
                    "LB", "RB", "LT", "RT" -> shoulderIndex(btn.baseId, isWindowsMode)
                    else -> buttonSpec(btn.baseId, btn.label, 0f).buttonIndex
                }
                if (index != null) {
                    list.add(
                        RuntimeControl(
                            left, top, right, bottom,
                            onDown = { _, _ ->
                                pressedButtons[btn.id] = true
                                vibrateForIntensity(vibrator, hapticIntensity)
                                gamepad?.setButtonState(index, true)
                            },
                            onMove = { _, _ -> },
                            onUp = {
                                pressedButtons[btn.id] = false
                                gamepad?.setButtonState(index, false)
                            }
                        )
                    )
                }
            }
        }
    }
    // Hit-testing picks the first match, so smaller controls go first: a small button overlapping a
    // stick/d-pad claims the touch, not the larger control beneath it.
    list.sortBy { it.area }
    return list
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
private fun ButtonVisual(spec: ButtonSpec, size: Dp, pressed: Boolean) {
    val bgColor = if (pressed) spec.color.copy(alpha = 0.6f) else spec.color
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 60 else 120),
        label = "btnScale"
    )
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .background(bgColor, spec.shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = spec.label,
            fontSize = spec.fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = ControllerOnBtn,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun StickVisual(size: Dp, label: String, offset: Offset) {
    val knobSize = size * 0.4f
    Box(
        modifier = Modifier
            .size(size)
            .background(StickBase, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StickLabel)
        }
        Box(
            modifier = Modifier
                .size(knobSize)
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .background(StickKnob, CircleShape)
        )
    }
}

@Composable
private fun DpadVisual(dir: DpadState?, size: Dp) {
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
            val tint = if (d == h || d == v) DpadPressed else DpadNormal
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

// Touchpad: a plain surface. No moving knob — like a laptop trackpad, where you first touch is the
// origin for that touch. D-pad mode lights the border while a direction is held.
@Composable
private fun TouchpadVisual(size: Dp, mode: String, dir: DpadState?) {
    val label = when (mode) { "TPADL" -> "L"; "TPADR" -> "R"; else -> "+" }
    val active = mode == "TPADD" && (dir?.h != null || dir?.v != null)
    val border = if (active) DpadPressed.copy(alpha = 0.5f) else StickKnob.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(size)
            .background(StickBase.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.5.dp, border, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = (size.value * 0.16f).sp, fontWeight = FontWeight.Bold, color = StickLabel)
    }
}

enum class DpadDir { UP, DOWN, LEFT, RIGHT }

data class DpadState(val h: DpadDir?, val v: DpadDir?)

private fun obtainVibrator(context: Context): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            ?: @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

private val mainHandler = Handler(Looper.getMainLooper())

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
    mainHandler.post {
        vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude))
    }
}
