package com.bluetooth.gamepad.layout.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.R
import com.bluetooth.gamepad.layout.ButtonConfig
import com.bluetooth.gamepad.layout.ButtonFace
import com.bluetooth.gamepad.layout.MacroColor
import com.bluetooth.gamepad.layout.baseButtonId
import com.bluetooth.gamepad.layout.baseId
import com.bluetooth.gamepad.layout.isPressable
import com.bluetooth.gamepad.ui.theme.BtnA
import com.bluetooth.gamepad.ui.theme.BtnB
import com.bluetooth.gamepad.ui.theme.BtnPrimary
import com.bluetooth.gamepad.ui.theme.BtnSecondary
import com.bluetooth.gamepad.ui.theme.BtnX
import com.bluetooth.gamepad.ui.theme.BtnY
import com.bluetooth.gamepad.ui.theme.DpadNormal
import com.bluetooth.gamepad.ui.theme.StickBase
import com.bluetooth.gamepad.ui.theme.StickKnob
import kotlin.math.roundToInt

@Composable
internal fun EditorCanvas(
    buttons: SnapshotStateList<ButtonConfig>,
    selectedId: String?,
    snapEnabled: Boolean,
    density: Float,
    onSizeChanged: (IntSize) -> Unit,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onScale: (String, Float) -> Unit,
    onMoveStart: () -> Unit,
    onMoveEnd: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorCanvasBg)
            .onSizeChanged {
                canvasSize.value = it
                onSizeChanged(it)
            }
    ) {
        if (snapEnabled) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 16.dp.toPx()
                val cols = (size.width / step).toInt() + 1
                val rows = (size.height / step).toInt() + 1
                for (i in 0..cols) {
                    drawLine(EditorGridLine,
                        androidx.compose.ui.geometry.Offset(i * step, 0f),
                        androidx.compose.ui.geometry.Offset(i * step, size.height), 1f)
                }
                for (i in 0..rows) {
                    drawLine(EditorGridLine,
                        androidx.compose.ui.geometry.Offset(0f, i * step),
                        androidx.compose.ui.geometry.Offset(size.width, i * step), 1f)
                }
            }
        }

        val w = canvasSize.value.width.toFloat()
        val h = canvasSize.value.height.toFloat()

        if (w > 0 && h > 0) {
            buttons.forEach { btn ->
                val isSelected = btn.id == selectedId
                // Per-button so two elements dragged at once each arm their own undo entry, and
                // lifting one finger doesn't re-arm a still-active drag on another.
                val gesturePushed = remember(btn.id) { mutableStateOf(false) }
                val dim = minOf(w, h)
                val btnPx = btn.sizeFrac * dim
                val cx = btn.xFrac * w
                val cy = btn.yFrac * h
                val btnDp = (btnPx / density).dp
                val topLeftX = (cx - btnPx / 2f).roundToInt()
                val topLeftY = (cy - btnPx / 2f).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(topLeftX, topLeftY) }
                        .size(btnDp)
                        .alpha(btn.opacity)
                        .then(if (isSelected) Modifier.border(2.dp, EditorSelection, CircleShape) else Modifier)
                        .pointerInput(btn.id) {
                            detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                                if (!gesturePushed.value) {
                                    onMoveStart()
                                    gesturePushed.value = true
                                }
                                // Independent checks so a two-finger gesture can pan and scale at
                                // once — an else-if would freeze panning whenever zoom != 1.
                                if (zoom != 1f) onScale(btn.id, zoom)
                                if (pan.x != 0f || pan.y != 0f) onMove(btn.id, pan.x, pan.y)
                            }
                        }
                        .pointerInput(btn.id + "_tap") {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (e.changes.any { it.pressed && !it.previousPressed }) onSelect(btn.id)
                                    // detectTransformGestures never returns, so the gesture's end is
                                    // detected here: snap once when the last finger lifts.
                                    if (gesturePushed.value && e.changes.none { it.pressed }) {
                                        onMoveEnd(btn.id)
                                        gesturePushed.value = false
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    EditorButtonVisual(btn = btn, sizeDp = btnPx / density)
                }
            }
        }

    }
}

@Composable
private fun EditorButtonVisual(btn: ButtonConfig, sizeDp: Float) {
    if (btn.isPressable) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ButtonFace(btn, sizeDp.dp, pressed = false, oled = false)
        }
        return
    }
    when (val base = btn.baseId) {
        "LSTICK", "RSTICK" -> if (btn.floating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, StickKnob.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FLOAT", fontSize = (sizeDp * 0.1f).sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f))
                    Text(if (base == "LSTICK") "L" else "R", fontSize = (sizeDp * 0.2f).sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF9DC9E0))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(StickBase, CircleShape),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size((sizeDp * 0.4f).dp).background(StickKnob, CircleShape))
                Text(if (base == "LSTICK") "L" else "R",
                    fontSize = (sizeDp * 0.22f).sp, fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f))
            }
        }
        "DPAD" -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val arrowSize = (sizeDp * 0.5f).dp
                val insetDp = (sizeDp * 0.06f).dp
                listOf(180f to Alignment.TopCenter, 0f to Alignment.BottomCenter,
                    90f to Alignment.CenterStart, 270f to Alignment.CenterEnd
                ).forEach { (rotation, anchor) ->
                    val offsetMod = when (anchor) {
                        Alignment.TopCenter    -> Modifier.offset(y = insetDp)
                        Alignment.BottomCenter -> Modifier.offset(y = -insetDp)
                        Alignment.CenterStart  -> Modifier.offset(x = insetDp)
                        else                   -> Modifier.offset(x = -insetDp)
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = anchor) {
                        Image(painter = painterResource(id = R.drawable.dpad_arrow),
                            contentDescription = null,
                            modifier = Modifier.size(arrowSize).then(offsetMod).rotate(rotation),
                            colorFilter = ColorFilter.tint(DpadNormal))
                    }
                }
            }
        }
        "TPADL", "TPADR", "TPADD" -> {
            val tag = when (base) { "TPADL" -> "L"; "TPADR" -> "R"; else -> "+" }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A2229), RoundedCornerShape(14.dp))
                    .border(1.5.dp, StickKnob.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PAD", fontSize = (sizeDp * 0.13f).sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f))
                    Text(tag, fontSize = (sizeDp * 0.26f).sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF9DC9E0))
                }
            }
        }
    }
}

internal fun btnDisplayName(id: String): String = when (id.baseButtonId()) {
    "A"      -> "A button";       "B"      -> "B button"
    "X"      -> "X button";       "Y"      -> "Y button"
    "LSTICK" -> "Left stick";     "RSTICK" -> "Right stick"
    "DPAD"   -> "D-pad"
    "TPADL"  -> "Touchpad → L stick"
    "TPADR"  -> "Touchpad → R stick"
    "TPADD"  -> "Touchpad → D-pad"
    "LB"     -> "LB shoulder";   "RB"     -> "RB shoulder"
    "LT"     -> "LT trigger";    "RT"     -> "RT trigger"
    "LSB"    -> "L stick click"; "RSB"    -> "R stick click"
    "SELECT" -> "Select";         "START"  -> "Start"
    "MACRO"  -> "Macro"
    else           -> id
}

internal fun btnBadgeColors(id: String): Pair<Color, Color> = when (id.baseButtonId()) {
    "A"  -> BtnA to Color.White
    "B"  -> BtnB to Color.White
    "X"  -> BtnX to Color.White
    "Y"  -> BtnY to Color.White
    "LSTICK", "RSTICK", "LSB", "RSB" -> Color(0xFF1E2E3A) to Color(0xFF7EB8D4)
    "TPADL", "TPADR", "TPADD"        -> Color(0xFF26323D) to Color(0xFF9DC9E0)
    "DPAD"   -> Color(0xFF2D4A3E) to Color(0xFF7FDCC4)
    "LB", "RB"  -> BtnPrimary  to Color.White
    "LT", "RT"  -> BtnSecondary to Color.White
    "MACRO"  -> MacroColor to Color.White
    else -> Color(0xFF252A27) to Color.White
}
