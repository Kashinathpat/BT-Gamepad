package com.bluetooth.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.ui.theme.BtnA
import com.bluetooth.gamepad.ui.theme.BtnB
import com.bluetooth.gamepad.ui.theme.BtnPrimary
import com.bluetooth.gamepad.ui.theme.BtnSecondary
import com.bluetooth.gamepad.ui.theme.BtnX
import com.bluetooth.gamepad.ui.theme.BtnY
import com.bluetooth.gamepad.ui.theme.ControllerOnBtn

val MacroColor = Color(0xFF5E4B8B)
val OledDim = Color.White.copy(alpha = 0.28f)
val OledBright = Color.White.copy(alpha = 0.8f)

fun controlColor(baseId: String): Color = when (baseId) {
    "A" -> BtnA
    "B" -> BtnB
    "X" -> BtnX
    "Y" -> BtnY
    "LB", "RB" -> BtnPrimary
    "MACRO" -> MacroColor
    else -> BtnSecondary
}

fun defaultShape(baseId: String): ControlShape = when (baseId) {
    "LB", "RB", "LT", "RT" -> ControlShape.ROUNDED
    "MACRO" -> ControlShape.PILL
    else -> ControlShape.CIRCLE
}

private fun ControlShape.toShape(): Shape = when (this) {
    ControlShape.CIRCLE, ControlShape.OUTLINE -> CircleShape
    ControlShape.ROUNDED -> RoundedCornerShape(22)
    ControlShape.PILL -> RoundedCornerShape(50)
}

private fun labelScale(length: Int): Float = when {
    length <= 2 -> 0.3f
    length == 3 -> 0.25f
    length <= 5 -> 0.2f
    length == 6 -> 0.17f
    else -> 0.14f
}

@Composable
fun ButtonFace(btn: ButtonConfig, size: Dp, pressed: Boolean, oled: Boolean) {
    val shape = btn.shape ?: defaultShape(btn.baseId)
    val color = controlColor(btn.baseId)
    val height = if (shape == ControlShape.PILL) size * 0.6f else size
    val outline = oled || shape == ControlShape.OUTLINE
    val lineColor = if (oled) (if (pressed) OledBright else OledDim) else color
    val fg = when {
        oled -> lineColor
        outline -> if (pressed) ControllerOnBtn else color
        else -> ControllerOnBtn
    }
    val fill = when {
        outline -> if (pressed && !oled) color.copy(alpha = 0.35f) else Color.Transparent
        pressed -> color.copy(alpha = 0.6f)
        else -> color
    }
    Box(
        modifier = Modifier
            .width(size)
            .height(height)
            .background(fill, shape.toShape())
            .then(if (outline) Modifier.border(2.dp, lineColor, shape.toShape()) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val text = btn.label.ifBlank { btn.baseId }
        val fontSize = (size.value * labelScale(text.length)).coerceAtMost(height.value * 0.5f)
        Text(text, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false)
    }
}
