package com.bluetooth.gamepad.layout.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.layout.ButtonBehavior
import com.bluetooth.gamepad.layout.ButtonConfig
import com.bluetooth.gamepad.layout.ControlShape
import com.bluetooth.gamepad.layout.MAX_LABEL_LENGTH
import com.bluetooth.gamepad.layout.MIN_OPACITY
import com.bluetooth.gamepad.layout.MacroColor
import com.bluetooth.gamepad.layout.PRESS_IDS
import com.bluetooth.gamepad.layout.TURBO_RATES
import com.bluetooth.gamepad.layout.baseId
import com.bluetooth.gamepad.layout.defaultShape
import com.bluetooth.gamepad.layout.isPressable
import com.bluetooth.gamepad.layout.isStick
import com.bluetooth.gamepad.ui.theme.BtnA
import com.bluetooth.gamepad.ui.theme.BtnB
import com.bluetooth.gamepad.ui.theme.BtnPrimary
import com.bluetooth.gamepad.ui.theme.BtnSecondary
import com.bluetooth.gamepad.ui.theme.BtnX
import com.bluetooth.gamepad.ui.theme.BtnY
import kotlin.math.roundToInt

private data class PaletteItem(
    val template: ButtonConfig,
    val displayLabel: String,
    val letter: String,
    val color: Color,
    val onColor: Color
)

private val paletteItems = listOf(
    PaletteItem(ButtonConfig("A",      "A",      0.5f, 0.5f, 0.09f), "Face button A", "A",   BtnA,                    Color.White),
    PaletteItem(ButtonConfig("B",      "B",      0.5f, 0.5f, 0.09f), "Face button B", "B",   BtnB,                    Color.White),
    PaletteItem(ButtonConfig("X",      "X",      0.5f, 0.5f, 0.09f), "Face button X", "X",   BtnX,                    Color.White),
    PaletteItem(ButtonConfig("Y",      "Y",      0.5f, 0.5f, 0.09f), "Face button Y", "Y",   BtnY,                    Color.White),
    PaletteItem(ButtonConfig("DPAD",   "D",      0.5f, 0.5f, 0.18f), "D-pad",         "+",   Color(0xFF2D4A3E),       EditorPrimary),
    PaletteItem(ButtonConfig("LSTICK", "L",      0.5f, 0.5f, 0.18f), "Left stick",    "L",   Color(0xFF1E2E3A),       Color(0xFF7EB8D4)),
    PaletteItem(ButtonConfig("RSTICK", "R",      0.5f, 0.5f, 0.18f), "Right stick",   "R",   Color(0xFF1E2E3A),       Color(0xFF7EB8D4)),
    PaletteItem(ButtonConfig("TPADL",  "L",      0.5f, 0.5f, 0.22f), "Pad · L stick", "TL",  Color(0xFF26323D),       Color(0xFF9DC9E0)),
    PaletteItem(ButtonConfig("TPADR",  "R",      0.5f, 0.5f, 0.22f), "Pad · R stick", "TR",  Color(0xFF26323D),       Color(0xFF9DC9E0)),
    PaletteItem(ButtonConfig("TPADD",  "+",      0.5f, 0.5f, 0.22f), "Pad · D-pad",   "TD",  Color(0xFF26323D),       Color(0xFF7FDCC4)),
    PaletteItem(ButtonConfig("LB",     "LB",     0.5f, 0.5f, 0.10f), "Shoulder LB",   "LB",  BtnPrimary,              Color.White),
    PaletteItem(ButtonConfig("RB",     "RB",     0.5f, 0.5f, 0.10f), "Shoulder RB",   "RB",  BtnPrimary,              Color.White),
    PaletteItem(ButtonConfig("LT",     "LT",     0.5f, 0.5f, 0.10f), "Trigger LT",    "LT",  BtnSecondary,            Color.White),
    PaletteItem(ButtonConfig("RT",     "RT",     0.5f, 0.5f, 0.10f), "Trigger RT",    "RT",  BtnSecondary,            Color.White),
    PaletteItem(ButtonConfig("LSB",    "LSB",    0.5f, 0.5f, 0.08f), "Stick click L", "LSB", Color(0xFF252A27),       Color.White),
    PaletteItem(ButtonConfig("RSB",    "RSB",    0.5f, 0.5f, 0.08f), "Stick click R", "RSB", Color(0xFF252A27),       Color.White),
    PaletteItem(ButtonConfig("SELECT", "SELECT", 0.5f, 0.5f, 0.08f), "Select",        "SEL", Color(0xFF252A27),       Color.White),
    PaletteItem(ButtonConfig("START",  "START",  0.5f, 0.5f, 0.08f), "Start",         "STA", Color(0xFF252A27),       Color.White),
    PaletteItem(ButtonConfig("MACRO",  "A+B",    0.5f, 0.5f, 0.12f, macro = listOf("A", "B")), "Macro", "M+", MacroColor, Color.White),
)

// A glass overlay panel that floats over the canvas and can be dragged by its header. The drag
// offset is kept in state and applied via offset {}, so the panel never resizes the canvas.
@Composable
private fun BoxScope.DraggablePanel(
    width: Dp,
    initialAlignment: Alignment,
    containerSize: IntSize,
    header: @Composable (dragModifier: Modifier) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // Absolute position from top-left. Drag accumulates raw deltas into pos; clamping is applied at
    // render time so the panel can't be lost off screen but the drag still tracks the finger 1:1.
    // pos starts unset and is placed from initialAlignment once both sizes are known.
    val pos = remember { mutableStateOf<Offset?>(null) }
    val panelSize = remember { mutableStateOf(IntSize.Zero) }
    val marginPx = with(LocalDensity.current) { 10.dp.toPx() }

    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val pw = panelSize.value.width.toFloat()
    val ph = panelSize.value.height.toFloat()

    val align = initialAlignment as? androidx.compose.ui.BiasAlignment
    val startX = when {
        cw <= 0 || pw <= 0 -> marginPx
        align != null && align.horizontalBias > 0f -> cw - pw - marginPx // right-aligned
        align != null && align.horizontalBias == 0f -> (cw - pw) / 2f     // centred
        else -> marginPx                                                  // left-aligned
    }
    val startY = when {
        ch <= 0 || ph <= 0 -> marginPx
        align != null && align.verticalBias == 0f -> (ch - ph) / 2f
        else -> marginPx
    }
    val base = pos.value ?: Offset(startX, startY)
    val maxX = (cw - pw).coerceAtLeast(0f)
    val maxY = (ch - ph).coerceAtLeast(0f)
    val clampedX = if (cw > 0 && pw > 0) base.x.coerceIn(0f, maxX) else base.x
    val clampedY = if (ch > 0 && ph > 0) base.y.coerceIn(0f, maxY) else base.y

    // Key on containerSize so the gesture lambda re-captures fresh cw/ch once the container is
    // measured (it is 0 on first composition).
    val dragModifier = Modifier.pointerInput(containerSize) {
        detectDragGestures { change, drag ->
            change.consume()
            // Accumulate deltas onto the live position, seeding from the current (clamped) spot on
            // the first move. Reading pos.value here — not a value captured at composition — is what
            // keeps movement from collapsing back to the start each frame. Clamp as we store so the
            // panel tracks the finger 1:1 with no overshoot dead-zone when dragging back.
            val cur = pos.value ?: Offset(clampedX, clampedY)
            val mx = (cw - panelSize.value.width).coerceAtLeast(0f)
            val my = (ch - panelSize.value.height).coerceAtLeast(0f)
            pos.value = Offset(
                (cur.x + drag.x).coerceIn(0f, if (mx > 0f) mx else cur.x),
                (cur.y + drag.y).coerceIn(0f, if (my > 0f) my else cur.y)
            )
        }
    }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
            .onSizeChanged { panelSize.value = it }
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .background(EditorGlass)
            .border(1.dp, EditorGlassBorder, RoundedCornerShape(16.dp))
            // Catch taps/drags that land on empty panel areas so they don't fall through to canvas
            // elements beneath. Child buttons are hit-tested first, so they still work normally.
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } }
    ) {
        header(dragModifier)
        content()
    }
}

@Composable
internal fun GripDots() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { Box(Modifier.size(2.dp).background(EditorGlassOn.copy(alpha = 0.4f))) }
            }
        }
    }
}

@Composable
private fun PanelCloseBtn(onClose: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
            .background(EditorGlassFill).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = EditorGlassOn, modifier = Modifier.size(12.dp))
    }
}

@Composable
internal fun BoxScope.InspectorPanel(
    button: ButtonConfig,
    containerSize: IntSize,
    onClose: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    onUpdate: (ButtonConfig) -> Unit,
    onLabelChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val (badgeColor, badgeOn) = btnBadgeColors(button.id)
    val maxBody = with(LocalDensity.current) { (containerSize.height.toDp() - 70.dp).coerceAtLeast(120.dp) }
    DraggablePanel(
        width = 220.dp,
        initialAlignment = Alignment.TopEnd,
        containerSize = containerSize,
        header = { dragModifier ->
            Row(
                modifier = Modifier
                    .then(dragModifier)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(button.label.take(2), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = badgeOn)
                }
                Text(btnDisplayName(button.id), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EditorGlassOn,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                GripDots()
                PanelCloseBtn(onClose)
            }
        }
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(EditorGlassBorder))
        Column(
            modifier = Modifier
                .heightIn(max = maxBody)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Size: label + [−] value [+] on one compact row.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelLabel("Size", Modifier.weight(1f))
                MiniBtn("−") { onSizeChange(-0.01f) }
                Text("${(button.sizeFrac * 1000).roundToInt()}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditorPrimary,
                    modifier = Modifier.widthIn(min = 26.dp), textAlign = TextAlign.Center)
                MiniBtn("+") { onSizeChange(0.01f) }
            }
            // Position: X row then Y row, each [axis −] value [axis +].
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelLabel("X", Modifier.width(14.dp))
                MiniBtn("−") { onPositionChange(-0.02f, 0f) }
                Text("%.2f".format(button.xFrac),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditorPrimary,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                MiniBtn("+") { onPositionChange(0.02f, 0f) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelLabel("Y", Modifier.width(14.dp))
                MiniBtn("−") { onPositionChange(0f, -0.02f) }
                Text("%.2f".format(button.yFrac),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditorPrimary,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                MiniBtn("+") { onPositionChange(0f, 0.02f) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelLabel("Opacity", Modifier.weight(1f))
                MiniBtn("−") { onUpdate(button.copy(opacity = stepOpacity(button.opacity, -0.1f))) }
                Text("${(button.opacity * 100).roundToInt()}%",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditorPrimary,
                    modifier = Modifier.widthIn(min = 34.dp), textAlign = TextAlign.Center)
                MiniBtn("+") { onUpdate(button.copy(opacity = stepOpacity(button.opacity, 0.1f))) }
            }

            if (button.isStick) {
                ToggleRow("Floating", button.floating) { onUpdate(button.copy(floating = !button.floating)) }
            }

            if (button.isPressable) {
                PanelLabel("Label")
                LabelField(button.label, onLabelChange)

                PanelLabel("Shape")
                val shape = button.shape ?: defaultShape(button.baseId)
                ChipGrid(ControlShape.entries, perRow = 2) { s, mod ->
                    Chip(s.title, shape == s, mod) { onUpdate(button.copy(shape = s)) }
                }

                PanelLabel("Behaviour")
                ChipGrid(ButtonBehavior.entries, perRow = 3) { b, mod ->
                    Chip(b.title, button.behavior == b, mod) { onUpdate(button.copy(behavior = b)) }
                }
                if (button.behavior == ButtonBehavior.TURBO) {
                    ChipGrid(TURBO_RATES, perRow = 3) { hz, mod ->
                        Chip("$hz Hz", button.turboHz == hz, mod) { onUpdate(button.copy(turboHz = hz)) }
                    }
                }

                ToggleRow("Hold for gyro", button.gyroGate) { onUpdate(button.copy(gyroGate = !button.gyroGate)) }

                if (button.baseId == "MACRO") {
                    PanelLabel("Presses")
                    ChipGrid(PRESS_IDS, perRow = 4) { id, mod ->
                        Chip(shortName(id), id in button.macro, mod) {
                            val next = PRESS_IDS.filter { (it == id) != (it in button.macro) }
                            if (next.isNotEmpty()) {
                                val autoLabel = button.label == macroLabel(button.macro)
                                onUpdate(button.copy(macro = next, label = if (autoLabel) macroLabel(next) else button.label))
                            }
                        }
                    }
                }
            }

            // Delete (icon-led, compact).
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(EditorErrorBg)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = EditorError, modifier = Modifier.size(13.dp))
                    Text("Delete", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = EditorError)
                }
            }
        }
    }
}

private fun stepOpacity(current: Float, delta: Float): Float =
    (((current + delta) * 10).roundToInt() / 10f).coerceIn(MIN_OPACITY, 1f)

private fun shortName(id: String): String = when (id) { "SELECT" -> "SEL"; "START" -> "STA"; else -> id }

private fun macroLabel(targets: List<String>): String = targets.joinToString("+") { shortName(it) }.take(MAX_LABEL_LENGTH)

@Composable
internal fun BoxScope.AddPanel(onAdd: (ButtonConfig) -> Unit, onClose: () -> Unit, containerSize: IntSize) {
    DraggablePanel(
        width = 360.dp,
        initialAlignment = Alignment.Center,
        containerSize = containerSize,
        header = { dragModifier ->
            Row(
                modifier = Modifier
                    .then(dragModifier)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Text("Add element", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = EditorGlassOn)
                    Text(" · tap to add", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = EditorGlassOnVar)
                }
                GripDots()
                PanelCloseBtn(onClose)
            }
        }
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(EditorGlassBorder))
        Column(
            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val perRow = 6
            paletteItems.chunked(perRow).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EditorGlassFill)
                                .clickable { onAdd(item.template) }
                                .padding(horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(item.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.letter, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = item.onColor)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(item.displayLabel, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
                                color = EditorGlassOn, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, lineHeight = 9.sp)
                        }
                    }
                    // Pad the final partial row so tiles keep a consistent width.
                    repeat(perRow - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
