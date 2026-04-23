package com.bluetoothpad

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bluetoothpad.ui.theme.BtnA
import com.bluetoothpad.ui.theme.BtnB
import com.bluetoothpad.ui.theme.BtnPrimary
import com.bluetoothpad.ui.theme.ControllerOnBtn
import com.bluetoothpad.ui.theme.OverlayPillLight
import com.bluetoothpad.ui.theme.StickLabel
import com.bluetoothpad.ui.theme.BtnSecondary
import com.bluetoothpad.ui.theme.BtnX
import com.bluetoothpad.ui.theme.BtnY
import com.bluetoothpad.ui.theme.ControllerBg
import com.bluetoothpad.ui.theme.DpadNormal
import com.bluetoothpad.ui.theme.DpadPressed
import com.bluetoothpad.ui.theme.StatusConnected
import com.bluetoothpad.ui.theme.StickBase
import com.bluetoothpad.ui.theme.StickKnob
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ControllerScreen(
    gamepad: BluetoothHidGamepad?,
    isWindowsMode: Boolean,
    connectedDeviceName: String,
    layout: ControllerLayout = ControllerLayout.default(),
    onStopClick: () -> Unit
) {
    val density = LocalDensity.current.density

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ControllerBg)
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val dim = minOf(w, h)

        layout.buttons.forEach { btn ->
            val btnPx = btn.sizeFrac * dim
            val cx = btn.xFrac * w
            val cy = btn.yFrac * h
            val btnDp = (btnPx / density).dp
            val topLeftX = (cx - btnPx / 2f).roundToInt()
            val topLeftY = (cy - btnPx / 2f).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(topLeftX, topLeftY) }
                    .size(btnDp),
                contentAlignment = Alignment.Center
            ) {
                when (btn.id) {
                    "LSTICK" -> AnalogStick(
                        size = btnDp,
                        label = "L",
                        onMove = { x, y -> gamepad?.setLeftStick(x, y) }
                    )
                    "RSTICK" -> AnalogStick(
                        size = btnDp,
                        label = "R",
                        onMove = { x, y -> gamepad?.setRightStick(x, y) }
                    )
                    "DPAD" -> DpadControl(isWindowsMode = isWindowsMode, gamepad = gamepad, size = btnDp)
                    "A"  -> GamepadBtn(label = "A",  modifier = Modifier.size(btnDp), shape = CircleShape, color = BtnA,       fontSize = (btnDp.value * 0.3f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_A, true) },      onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_A, false) })
                    "B"  -> GamepadBtn(label = "B",  modifier = Modifier.size(btnDp), shape = CircleShape, color = BtnB,       fontSize = (btnDp.value * 0.3f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_B, true) },      onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_B, false) })
                    "X"  -> GamepadBtn(label = "X",  modifier = Modifier.size(btnDp), shape = CircleShape, color = BtnX,       fontSize = (btnDp.value * 0.3f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_X, true) },      onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_X, false) })
                    "Y"  -> GamepadBtn(label = "Y",  modifier = Modifier.size(btnDp), shape = CircleShape, color = BtnY,       fontSize = (btnDp.value * 0.3f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_Y, true) },      onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_Y, false) })
                    "LB" -> GamepadBtn(label = "LB", modifier = Modifier.size(btnDp), color = BtnPrimary,  fontSize = (btnDp.value * 0.25f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LB, true) },     onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LB, false) })
                    "RB" -> GamepadBtn(label = "RB", modifier = Modifier.size(btnDp), color = BtnPrimary,  fontSize = (btnDp.value * 0.25f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RB, true) },     onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RB, false) })
                    "LT" -> GamepadBtn(label = "LT", modifier = Modifier.size(btnDp), color = BtnSecondary,fontSize = (btnDp.value * 0.25f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LT, true) },     onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_LT, false) })
                    "RT" -> GamepadBtn(label = "RT", modifier = Modifier.size(btnDp), color = BtnSecondary,fontSize = (btnDp.value * 0.25f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RT, true) },     onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_RT, false) })
                    "LSB"-> GamepadBtn(label = "LSB",modifier = Modifier.size(btnDp), shape = CircleShape, color = BtnSecondary,fontSize = (btnDp.value * 0.22f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_L3, true) },      onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_L3, false) })
                    "RSB"-> GamepadBtn(label = "RSB",modifier = Modifier.size(btnDp), shape = CircleShape, color = BtnSecondary,fontSize = (btnDp.value * 0.22f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_R3, true) },      onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_R3, false) })
                    "SELECT" -> GamepadBtn(label = "SEL", modifier = Modifier.size(btnDp), shape = RoundedCornerShape(50), color = BtnSecondary, fontSize = (btnDp.value * 0.2f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_SELECT, true) }, onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_SELECT, false) })
                    "START"  -> GamepadBtn(label = "START",modifier = Modifier.size(btnDp), shape = RoundedCornerShape(50), color = BtnSecondary, fontSize = (btnDp.value * 0.2f).toInt(), onDown = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_START, true) },  onUp = { gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_START, false) })
                    else -> GamepadBtn(label = btn.label, modifier = Modifier.size(btnDp), shape = CircleShape, color = BtnPrimary, fontSize = (btnDp.value * 0.25f).toInt(), onDown = {}, onUp = {})
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
                Text(
                    text = connectedDeviceName,
                    fontSize = 11.sp,
                    color = StatusConnected
                )
            }
        }
    }
}

@Composable
fun GamepadBtn(
    label: String? = null,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    color: Color = BtnPrimary,
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
                color = ControllerOnBtn,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun AnalogStick(
    size: androidx.compose.ui.unit.Dp = 120.dp,
    label: String = "",
    onMove: (Float, Float) -> Unit
) {
    val knobSize = size * 0.4f
    val density = LocalDensity.current
    val maxOffset = with(density) { ((size - knobSize) / 2).toPx() }
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(size)
            .background(StickBase, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = StickLabel
            )
        }
        Box(
            modifier = Modifier
                .size(knobSize)
                .offset {
                    IntOffset(offsetX.floatValue.roundToInt(), offsetY.floatValue.roundToInt())
                }
                .background(StickKnob, CircleShape)
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

@Composable
fun DpadControl(isWindowsMode: Boolean, gamepad: BluetoothHidGamepad?, size: androidx.compose.ui.unit.Dp = 124.dp) {
    // Track H and V axes independently so diagonals work
    val activeH = remember { mutableStateOf<DpadDir?>(null) } // LEFT or RIGHT
    val activeV = remember { mutableStateOf<DpadDir?>(null) } // UP or DOWN

    fun sendState(h: DpadDir?, v: DpadDir?) {
        if (isWindowsMode) {
            gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_UP,    v == DpadDir.UP)
            gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_DOWN,  v == DpadDir.DOWN)
            gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_LEFT,  h == DpadDir.LEFT)
            gamepad?.setButtonState(BluetoothHidGamepad.BUTTON_DPAD_RIGHT, h == DpadDir.RIGHT)
        } else {
            val hat = when {
                v == DpadDir.UP    && h == null             -> BluetoothHidGamepad.HAT_UP
                v == DpadDir.UP    && h == DpadDir.RIGHT    -> BluetoothHidGamepad.HAT_UP_RIGHT
                v == null          && h == DpadDir.RIGHT    -> BluetoothHidGamepad.HAT_RIGHT
                v == DpadDir.DOWN  && h == DpadDir.RIGHT    -> BluetoothHidGamepad.HAT_DOWN_RIGHT
                v == DpadDir.DOWN  && h == null             -> BluetoothHidGamepad.HAT_DOWN
                v == DpadDir.DOWN  && h == DpadDir.LEFT     -> BluetoothHidGamepad.HAT_DOWN_LEFT
                v == null          && h == DpadDir.LEFT     -> BluetoothHidGamepad.HAT_LEFT
                v == DpadDir.UP    && h == DpadDir.LEFT     -> BluetoothHidGamepad.HAT_UP_LEFT
                else                                        -> BluetoothHidGamepad.HAT_NEUTRAL
            }
            gamepad?.setHat(hat)
        }
    }

    fun update(x: Float, y: Float, cx: Float, cy: Float) {
        val dx = x - cx
        val dy = y - cy
        val dead = cx * 0.25f
        val newH = when { dx > dead -> DpadDir.RIGHT; dx < -dead -> DpadDir.LEFT; else -> null }
        val newV = when { dy < -dead -> DpadDir.UP;   dy > dead  -> DpadDir.DOWN; else -> null }
        if (newH != activeH.value || newV != activeV.value) {
            activeH.value = newH
            activeV.value = newV
            sendState(newH, newV)
        }
    }

    fun release() {
        if (activeH.value != null || activeV.value != null) {
            activeH.value = null
            activeV.value = null
            sendState(null, null)
        }
    }

    val arms = listOf(
        DpadDir.UP    to Alignment.TopCenter,
        DpadDir.DOWN  to Alignment.BottomCenter,
        DpadDir.LEFT  to Alignment.CenterStart,
        DpadDir.RIGHT to Alignment.CenterEnd
    )
    val rotations = mapOf(
        DpadDir.UP    to 180f,
        DpadDir.DOWN  to 0f,
        DpadDir.LEFT  to 90f,
        DpadDir.RIGHT to 270f
    )

    Box(
        modifier = Modifier
            .size(size)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed) {
                            val pos = event.changes.first { it.pressed }.position
                            update(pos.x, pos.y, this.size.width / 2f, this.size.height / 2f)
                        } else {
                            release()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val h = activeH.value
        val v = activeV.value
        val arrowSize = size * 0.45f
        val inset = size * 0.065f
        arms.forEach { (dir, anchor) ->
            val tint = if (dir == h || dir == v) DpadPressed else DpadNormal
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
            color = BtnY, fontSize = 17,
            onDown = { onButton(BluetoothHidGamepad.BUTTON_Y, true) },
            onUp = { onButton(BluetoothHidGamepad.BUTTON_Y, false) }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GamepadBtn(
                label = "X", modifier = Modifier.size(btnSize), shape = CircleShape,
                color = BtnX, fontSize = 17,
                onDown = { onButton(BluetoothHidGamepad.BUTTON_X, true) },
                onUp = { onButton(BluetoothHidGamepad.BUTTON_X, false) }
            )
            Spacer(modifier = Modifier.size(btnSize - 10.dp))
            GamepadBtn(
                label = "B", modifier = Modifier.size(btnSize), shape = CircleShape,
                color = BtnB, fontSize = 17,
                onDown = { onButton(BluetoothHidGamepad.BUTTON_B, true) },
                onUp = { onButton(BluetoothHidGamepad.BUTTON_B, false) }
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        GamepadBtn(
            label = "A", modifier = Modifier.size(btnSize), shape = CircleShape,
            color = BtnA, fontSize = 17,
            onDown = { onButton(BluetoothHidGamepad.BUTTON_A, true) },
            onUp = { onButton(BluetoothHidGamepad.BUTTON_A, false) }
        )
    }
}
