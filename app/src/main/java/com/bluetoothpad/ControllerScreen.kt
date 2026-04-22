package com.bluetoothpad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ControllerScreen(
    gamepad: BluetoothHidGamepad?,
    isWindowsMode: Boolean,
    connectedDeviceName: String,
    onStopClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Device name + disconnect centered at top
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (connectedDeviceName.isNotEmpty()) connectedDeviceName else "Not connected",
                fontSize = 11.sp,
                color = if (connectedDeviceName.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
            )
            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880000)),
                modifier = Modifier.height(26.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text("Disconnect", fontSize = 10.sp)
            }
        }

        // LB / LT stacked top-left
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 34.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GamepadBtn(
                label = "LB",
                modifier = Modifier.width(80.dp).height(36.dp),
                onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LB, true) },
                onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LB, false) }
            )
            GamepadBtn(
                label = "LT",
                modifier = Modifier.width(80.dp).height(36.dp),
                color = Color(0xFF2A2A4A),
                onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LT, true) },
                onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LT, false) }
            )
        }

        // RB / RT stacked top-right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 34.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GamepadBtn(
                label = "RB",
                modifier = Modifier.width(80.dp).height(36.dp),
                onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RB, true) },
                onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RB, false) }
            )
            GamepadBtn(
                label = "RT",
                modifier = Modifier.width(80.dp).height(36.dp),
                color = Color(0xFF2A2A4A),
                onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RT, true) },
                onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RT, false) }
            )
        }

        // LSB - left edge, vertically centered
        GamepadBtn(
            label = "LSB",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(44.dp),
            shape = CircleShape,
            fontSize = 10,
            color = Color(0xFF2A2A4A),
            onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_L3, true) },
            onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_L3, false) }
        )

        // RSB - right edge, vertically centered
        GamepadBtn(
            label = "RSB",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(44.dp),
            shape = CircleShape,
            fontSize = 10,
            color = Color(0xFF2A2A4A),
            onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_R3, true) },
            onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_R3, false) }
        )

        // Bottom-left: left stick + D-pad side by side
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 60.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnalogStick(
                size = 120,
                knobSize = 48,
                onMove = { x, y -> gamepad?.setLeftStick(x, y) }
            )
            DpadControl(isWindowsMode = isWindowsMode, gamepad = gamepad)
        }

        // Bottom-right: ABXY + right stick side by side
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 60.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AbxyButtons(
                onButton = { index, pressed -> gamepad?.setButtonState(index, pressed) }
            )
            AnalogStick(
                size = 120,
                knobSize = 48,
                onMove = { x, y -> gamepad?.setRightStick(x, y) }
            )
        }

        // Bottom center: SELECT + START
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GamepadBtn(
                label = "SELECT",
                modifier = Modifier.width(64.dp).height(30.dp),
                fontSize = 9,
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFF2A2A4A),
                onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_SELECT, true) },
                onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_SELECT, false) }
            )
            GamepadBtn(
                label = "START",
                modifier = Modifier.width(64.dp).height(30.dp),
                fontSize = 9,
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFF2A2A4A),
                onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_START, true) },
                onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_START, false) }
            )
        }
    }
}

@Composable
fun GamepadBtn(
    label: String? = null,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    color: Color = Color(0xFF333355),
    fontSize: Int = 14,
    fontWeight: FontWeight = FontWeight.Bold,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    val pressed = remember { mutableStateOf(false) }
    val bgColor = if (pressed.value) color.copy(alpha = 0.5f) else color

    Box(
        modifier = modifier
            .background(bgColor, shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val isDown = event.changes.any { it.pressed }
                        if (isDown && !pressed.value) {
                            pressed.value = true
                            onDown()
                        } else if (!isDown && pressed.value) {
                            pressed.value = false
                            onUp()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun AnalogStick(
    size: Int = 120,
    knobSize: Int = 48,
    onMove: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val maxOffset = with(density) { ((size.dp - knobSize.dp) / 2).toPx() }
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(size.dp)
            .background(Color(0xFF222244), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(knobSize.dp)
                .offset {
                    IntOffset(offsetX.floatValue.roundToInt(), offsetY.floatValue.roundToInt())
                }
                .background(Color(0xFF555588), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            offsetX.floatValue = 0f
                            offsetY.floatValue = 0f
                            onMove(0f, 0f)
                        },
                        onDragCancel = {
                            offsetX.floatValue = 0f
                            offsetY.floatValue = 0f
                            onMove(0f, 0f)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        var newX = offsetX.floatValue + dragAmount.x
                        var newY = offsetY.floatValue + dragAmount.y
                        val dist = sqrt(newX * newX + newY * newY)
                        if (dist > maxOffset) {
                            newX = newX / dist * maxOffset
                            newY = newY / dist * maxOffset
                        }
                        offsetX.floatValue = newX
                        offsetY.floatValue = newY
                        onMove(newX / maxOffset, newY / maxOffset)
                    }
                }
        )
    }
}

// Single canvas D-pad: 4 arrow arms meeting at center, no middle square.
// Touch direction is determined by which arm the finger lands on.
@Composable
fun DpadControl(isWindowsMode: Boolean, gamepad: BluetoothHidGamepad?) {
    // Which direction is currently pressed (null = none)
    val activeDir = remember { mutableStateOf<DpadDir?>(null) }

    fun press(dir: DpadDir) {
        if (activeDir.value == dir) return
        // release previous
        activeDir.value?.let { prev ->
            if (isWindowsMode) when (prev) {
                DpadDir.UP -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_UP, false)
                DpadDir.DOWN -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_DOWN, false)
                DpadDir.LEFT -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_LEFT, false)
                DpadDir.RIGHT -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_RIGHT, false)
            } else gamepad?.setHat(BluetoothHidGamepad.HAT_NEUTRAL)
        }
        activeDir.value = dir
        if (isWindowsMode) when (dir) {
            DpadDir.UP -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_UP, true)
            DpadDir.DOWN -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_DOWN, true)
            DpadDir.LEFT -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_LEFT, true)
            DpadDir.RIGHT -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_RIGHT, true)
        } else when (dir) {
            DpadDir.UP -> gamepad?.setHat(BluetoothHidGamepad.HAT_UP)
            DpadDir.DOWN -> gamepad?.setHat(BluetoothHidGamepad.HAT_DOWN)
            DpadDir.LEFT -> gamepad?.setHat(BluetoothHidGamepad.HAT_LEFT)
            DpadDir.RIGHT -> gamepad?.setHat(BluetoothHidGamepad.HAT_RIGHT)
        }
    }

    fun release() {
        activeDir.value?.let { prev ->
            if (isWindowsMode) when (prev) {
                DpadDir.UP -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_UP, false)
                DpadDir.DOWN -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_DOWN, false)
                DpadDir.LEFT -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_LEFT, false)
                DpadDir.RIGHT -> gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_RIGHT, false)
            } else gamepad?.setHat(BluetoothHidGamepad.HAT_NEUTRAL)
        }
        activeDir.value = null
    }

    fun dirFromOffset(x: Float, y: Float, cx: Float, cy: Float): DpadDir? {
        val dx = x - cx
        val dy = y - cy
        // ignore center dead zone (inner 20% of radius)
        val totalSize = cx * 2
        val dead = totalSize * 0.15f
        if (abs(dx) < dead && abs(dy) < dead) return null
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) DpadDir.RIGHT else DpadDir.LEFT
        } else {
            if (dy > 0) DpadDir.DOWN else DpadDir.UP
        }
    }

    // Map each direction to the alignment anchor where its image should sit
    val arms = listOf(
        DpadDir.UP    to Alignment.TopCenter,
        DpadDir.DOWN  to Alignment.BottomCenter,
        DpadDir.LEFT  to Alignment.CenterStart,
        DpadDir.RIGHT to Alignment.CenterEnd
    )
    // Drawable tip points UP. Rotate so tip points inward for each direction:
    // UP arm sits at top   → tip must point down (180°)
    // DOWN arm sits at bot → tip must point up   (0°)
    // LEFT arm sits left   → tip must point right (90°)
    // RIGHT arm sits right → tip must point left  (270°)
    val rotations = mapOf(
        DpadDir.UP    to 180f,
        DpadDir.DOWN  to 0f,
        DpadDir.LEFT  to 90f,
        DpadDir.RIGHT to 270f
    )

    Box(
        modifier = Modifier
            .size(124.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed) {
                            val pos = event.changes.first { it.pressed }.position
                            val dir = dirFromOffset(pos.x, pos.y, size.width / 2f, size.height / 2f)
                            if (dir != null) press(dir) else release()
                        } else {
                            release()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val active = activeDir.value
        // inset pulls arrows toward center; drawable has ~15% padding at base so bump inward
        val arrowSize = 56.dp
        val inset = 8.dp
        arms.forEach { (dir, anchor) ->
            val tint = if (active == dir) Color(0xFF8888CC) else Color(0xFF555577)
            val offsetMod = when (dir) {
                DpadDir.UP    -> Modifier.offset(y = inset)
                DpadDir.DOWN  -> Modifier.offset(y = -inset)
                DpadDir.LEFT  -> Modifier.offset(x = inset)
                DpadDir.RIGHT -> Modifier.offset(x = -inset)
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = anchor
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dpad_arrow),
                    contentDescription = null,
                    modifier = Modifier
                        .size(arrowSize)
                        .then(offsetMod)
                        .rotate(rotations[dir]!!),
                    colorFilter = ColorFilter.tint(tint)
                )
            }
        }
    }
}

enum class DpadDir { UP, DOWN, LEFT, RIGHT }

@Composable
fun AbxyButtons(onButton: (Int, Boolean) -> Unit) {
    val btnSize = 52.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GamepadBtn(
            label = "Y", modifier = Modifier.size(btnSize), shape = CircleShape,
            color = Color(0xFFCCA000), fontSize = 17,
            onDown = { onButton(BluetoothHidGamepad.BUTTON_Y, true) },
            onUp = { onButton(BluetoothHidGamepad.BUTTON_Y, false) }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GamepadBtn(
                label = "X", modifier = Modifier.size(btnSize), shape = CircleShape,
                color = Color(0xFF0060CC), fontSize = 17,
                onDown = { onButton(BluetoothHidGamepad.BUTTON_X, true) },
                onUp = { onButton(BluetoothHidGamepad.BUTTON_X, false) }
            )
            Spacer(modifier = Modifier.size(btnSize - 10.dp))
            GamepadBtn(
                label = "B", modifier = Modifier.size(btnSize), shape = CircleShape,
                color = Color(0xFFCC0000), fontSize = 17,
                onDown = { onButton(BluetoothHidGamepad.BUTTON_B, true) },
                onUp = { onButton(BluetoothHidGamepad.BUTTON_B, false) }
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        GamepadBtn(
            label = "A", modifier = Modifier.size(btnSize), shape = CircleShape,
            color = Color(0xFF00AA00), fontSize = 17,
            onDown = { onButton(BluetoothHidGamepad.BUTTON_A, true) },
            onUp = { onButton(BluetoothHidGamepad.BUTTON_A, false) }
        )
    }
}
