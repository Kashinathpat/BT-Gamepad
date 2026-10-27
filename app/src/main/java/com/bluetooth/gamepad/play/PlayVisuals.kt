package com.bluetooth.gamepad.play

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.R
import com.bluetooth.gamepad.layout.ButtonConfig
import com.bluetooth.gamepad.layout.ButtonFace
import com.bluetooth.gamepad.layout.OledBright
import com.bluetooth.gamepad.layout.OledDim
import com.bluetooth.gamepad.ui.theme.DpadNormal
import com.bluetooth.gamepad.ui.theme.DpadPressed
import com.bluetooth.gamepad.ui.theme.StickBase
import com.bluetooth.gamepad.ui.theme.StickKnob
import com.bluetooth.gamepad.ui.theme.StickLabel
import kotlin.math.roundToInt

@Composable
internal fun ButtonVisual(btn: ButtonConfig, size: Dp, pressed: Boolean, oled: Boolean) {
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
internal fun StickVisual(size: Dp, label: String, offset: Offset, oled: Boolean) {
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
internal fun FloatingStickVisual(
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
internal fun DpadVisual(dir: DpadState?, size: Dp, oled: Boolean) {
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
internal fun TouchpadVisual(size: Dp, mode: String, dir: DpadState?, oled: Boolean) {
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
