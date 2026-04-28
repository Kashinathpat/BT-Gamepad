package com.bluetooth.gamepad

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private enum class EditorTool { SELECT, ADD, LAYERS }

private val EditorBg           = Color(0xFF0B0C0D)
private val EditorRailBg       = Color(0xFF141618)
private val EditorRailBorder   = Color(0xFF232527)
private val EditorPanelBg      = Color(0xFF141618)
private val EditorTopBarColor  = Color(0xFF1A1C1E)
private val EditorCanvasBg     = Color(0xFF0E1012)
private val EditorGridLine     = Color(0x0AFFFFFF)
private val EditorOnSurface    = Color(0xFFE2E4E1)
private val EditorOnVariant    = Color(0xFF8C9288)
private val EditorBorderColor  = Color(0xFF2A2D2B)
private val EditorPrimary      = Color(0xFF7FDCC4)
private val EditorOnPrimary    = Color(0xFF003830)
private val EditorSecContainer = Color(0xFF1E2A24)
private val EditorSelection    = Color(0xFF7FDCC4)
private val EditorError        = Color(0xFFCF6679)
private val EditorErrorBg      = Color(0xFF2A1218)

@Composable
fun LayoutEditorScreen(
    layout: ControllerLayout,
    repo: LayoutRepository,
    onBack: () -> Unit,
    onTest: ((ControllerLayout) -> Unit)? = null
) {
    val buttons = remember { mutableStateListOf<ButtonConfig>().also { it.addAll(layout.buttons) } }
    val selectedId = remember { mutableStateOf<String?>(null) }
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val activeTool = remember { mutableStateOf(EditorTool.SELECT) }
    val snapEnabled = remember { mutableStateOf(true) }
    // Undo/redo stacks — each entry is a full snapshot of buttons
    val undoStack = remember { mutableStateListOf<List<ButtonConfig>>() }
    val redoStack = remember { mutableStateListOf<List<ButtonConfig>>() }
    val density = LocalDensity.current.density
    val context = LocalContext.current

    fun pushUndo() {
        undoStack.add(buttons.toList())
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(buttons.toList())
        val prev = undoStack.removeLast()
        buttons.clear()
        buttons.addAll(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(buttons.toList())
        val next = redoStack.removeLast()
        buttons.clear()
        buttons.addAll(next)
    }

    Row(modifier = Modifier.fillMaxSize().background(EditorBg)) {

        EditorRail(
            activeTool = activeTool.value,
            onBack = onBack,
            onToolChange = { activeTool.value = it }
        )

        Column(modifier = Modifier.weight(1f)) {
            EditorTopBar(
                layoutName = layout.name,
                snapEnabled = snapEnabled.value,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onUndo = { undo() },
                onRedo = { redo() },
                onSnapToggle = { snapEnabled.value = !snapEnabled.value },
                onTest = { onTest?.invoke(layout.copy(buttons = buttons.toList())) },
                onSave = {
                    repo.save(layout.copy(buttons = buttons.toList()))
                    Toast.makeText(context, "Layout saved", Toast.LENGTH_SHORT).show()
                }
            )
            EditorCanvas(
                buttons = buttons,
                selectedId = selectedId.value,
                snapEnabled = snapEnabled.value,
                density = density,
                onSizeChanged = { canvasSize.value = it },
                onSelect = { id ->
                    selectedId.value = id
                    activeTool.value = EditorTool.SELECT
                },
                onMove = { id, dx, dy ->
                    val i = buttons.indexOfFirst { it.id == id }
                    if (i >= 0) {
                        val w = canvasSize.value.width.toFloat()
                        val h = canvasSize.value.height.toFloat()
                        val cur = buttons[i]
                        buttons[i] = cur.copy(
                            xFrac = (cur.xFrac + dx / w).coerceIn(0.02f, 0.98f),
                            yFrac = (cur.yFrac + dy / h).coerceIn(0.02f, 0.98f)
                        )
                    }
                },
                onScale = { id, zoom ->
                    val i = buttons.indexOfFirst { it.id == id }
                    if (i >= 0) {
                        val cur = buttons[i]
                        buttons[i] = cur.copy(sizeFrac = (cur.sizeFrac * zoom).coerceIn(0.04f, 0.35f))
                    }
                },
                onMoveStart = { pushUndo() },
                onDelete = { id ->
                    pushUndo()
                    buttons.removeAll { it.id == id }
                    if (selectedId.value == id) selectedId.value = null
                }
            )
        }

        EditorRightPanel(
            tool = activeTool.value,
            selectedId = selectedId.value,
            buttons = buttons,
            onSelect = { selectedId.value = it },
            onAdd = { template ->
                val w = canvasSize.value.width.toFloat()
                val h = canvasSize.value.height.toFloat()
                if (w > 0 && h > 0) {
                    pushUndo()
                    val existing = buttons.count { it.id.startsWith(template.id) }
                    val newId = if (existing == 0) template.id else "${template.id}_${existing + 1}"
                    buttons.add(template.copy(id = newId, xFrac = 0.5f, yFrac = 0.5f))
                    selectedId.value = newId
                    activeTool.value = EditorTool.SELECT
                }
            },
            onDelete = { id ->
                pushUndo()
                buttons.removeAll { it.id == id }
                if (selectedId.value == id) selectedId.value = null
            },
            onSizeChange = { id, delta ->
                val i = buttons.indexOfFirst { it.id == id }
                if (i >= 0) {
                    pushUndo()
                    val cur = buttons[i]
                    buttons[i] = cur.copy(sizeFrac = (cur.sizeFrac + delta).coerceIn(0.04f, 0.35f))
                }
            },
            onPositionChange = { id, dx, dy ->
                val i = buttons.indexOfFirst { it.id == id }
                if (i >= 0) {
                    pushUndo()
                    val cur = buttons[i]
                    buttons[i] = cur.copy(
                        xFrac = (cur.xFrac + dx).coerceIn(0.02f, 0.98f),
                        yFrac = (cur.yFrac + dy).coerceIn(0.02f, 0.98f)
                    )
                }
            }
        )

    }
}

// ── LEFT RAIL ─────────────────────────────────────────────────────────────────

@Composable
private fun EditorRail(
    activeTool: EditorTool,
    onBack: () -> Unit,
    onToolChange: (EditorTool) -> Unit
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(EditorRailBg)
            .border(width = 1.dp, color = EditorRailBorder, shape = RoundedCornerShape(0)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        RailIconBtn(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back", onClick = onBack)
        RailDivider()
        RailIconBtn(
            icon = Icons.Default.Edit, label = "Select",
            active = activeTool == EditorTool.SELECT,
            onClick = { onToolChange(EditorTool.SELECT) }
        )
        Spacer(Modifier.height(4.dp))
        RailIconBtn(
            icon = Icons.Default.Add, label = "Add",
            active = activeTool == EditorTool.ADD,
            onClick = { onToolChange(EditorTool.ADD) }
        )
        Spacer(Modifier.height(4.dp))
        RailIconBtn(
            icon = Icons.Default.Layers, label = "Layers",
            active = activeTool == EditorTool.LAYERS,
            onClick = { onToolChange(EditorTool.LAYERS) }
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RailDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(1.dp)
            .background(EditorRailBorder)
    )
}

@Composable
private fun RailIconBtn(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> EditorOnVariant.copy(alpha = 0.3f)
        active   -> EditorPrimary
        else     -> EditorOnVariant
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) EditorSecContainer else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// ── TOP BAR ───────────────────────────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    layoutName: String,
    snapEnabled: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSnapToggle: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(EditorTopBarColor)
            .border(width = 1.dp, color = EditorRailBorder, shape = RoundedCornerShape(0))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = layoutName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = EditorOnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Undo
        TopBarIconBtn(icon = Icons.AutoMirrored.Filled.Undo, enabled = canUndo, onClick = onUndo)
        Spacer(Modifier.width(2.dp))
        // Redo
        TopBarIconBtn(icon = Icons.AutoMirrored.Filled.Redo, enabled = canRedo, onClick = onRedo)
        Spacer(Modifier.width(2.dp))
        // Snap grid toggle
        TopBarIconBtn(icon = Icons.Default.GridOn, active = snapEnabled, onClick = onSnapToggle)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .border(1.dp, EditorBorderColor, RoundedCornerShape(99.dp))
                .clickable(onClick = onTest)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = EditorOnSurface, modifier = Modifier.size(14.dp))
                Text("Test", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EditorOnSurface)
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(EditorPrimary)
                .clickable(onClick = onSave)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EditorOnPrimary)
        }
    }
}

@Composable
private fun TopBarIconBtn(
    icon: ImageVector,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> EditorOnVariant.copy(alpha = 0.3f)
        active   -> EditorPrimary
        else     -> EditorOnVariant
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) EditorSecContainer else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

// ── CANVAS ────────────────────────────────────────────────────────────────────

@Composable
private fun EditorCanvas(
    buttons: SnapshotStateList<ButtonConfig>,
    selectedId: String?,
    snapEnabled: Boolean,
    density: Float,
    onSizeChanged: (IntSize) -> Unit,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onScale: (String, Float) -> Unit,
    onMoveStart: () -> Unit,
    onDelete: (String) -> Unit
) {
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    // Track whether the current gesture has pushed an undo entry already
    val gesturePushed = remember { mutableStateOf(false) }

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
                        .then(if (isSelected) Modifier.border(2.dp, EditorSelection, CircleShape) else Modifier)
                        .pointerInput(btn.id) {
                            detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                                if (!gesturePushed.value) {
                                    onMoveStart()
                                    gesturePushed.value = true
                                }
                                if (zoom != 1f) onScale(btn.id, zoom)
                                else if (pan.x != 0f || pan.y != 0f) onMove(btn.id, pan.x, pan.y)
                            }
                        }
                        .pointerInput(btn.id + "_tap") {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (e.changes.any { it.pressed && !it.previousPressed }) {
                                        onSelect(btn.id)
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

// ── RIGHT PANEL ───────────────────────────────────────────────────────────────

@Composable
private fun EditorRightPanel(
    tool: EditorTool,
    selectedId: String?,
    buttons: SnapshotStateList<ButtonConfig>,
    onSelect: (String) -> Unit,
    onAdd: (ButtonConfig) -> Unit,
    onDelete: (String) -> Unit,
    onSizeChange: (String, Float) -> Unit,
    onPositionChange: (String, Float, Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(EditorPanelBg)
            .border(width = 1.dp, color = EditorRailBorder, shape = RoundedCornerShape(0))
    ) {
        when (tool) {
            EditorTool.ADD     -> AddPanel(onAdd = onAdd)
            EditorTool.LAYERS  -> LayersPanel(buttons = buttons, selectedId = selectedId, onSelect = onSelect)
            EditorTool.SELECT  -> InspectorPanel(
                selectedId = selectedId,
                buttons = buttons,
                onDelete = onDelete,
                onSizeChange = onSizeChange,
                onPositionChange = onPositionChange
            )
        }
    }
}

// ── ADD PANEL ─────────────────────────────────────────────────────────────────

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
    PaletteItem(ButtonConfig("LB",     "LB",     0.5f, 0.5f, 0.10f), "Shoulder LB",   "LB",  BtnPrimary,              Color.White),
    PaletteItem(ButtonConfig("RB",     "RB",     0.5f, 0.5f, 0.10f), "Shoulder RB",   "RB",  BtnPrimary,              Color.White),
    PaletteItem(ButtonConfig("LT",     "LT",     0.5f, 0.5f, 0.10f), "Trigger LT",    "LT",  BtnSecondary,            Color.White),
    PaletteItem(ButtonConfig("RT",     "RT",     0.5f, 0.5f, 0.10f), "Trigger RT",    "RT",  BtnSecondary,            Color.White),
    PaletteItem(ButtonConfig("LSB",    "LSB",    0.5f, 0.5f, 0.08f), "Stick click L", "LSB", Color(0xFF252A27),       Color.White),
    PaletteItem(ButtonConfig("RSB",    "RSB",    0.5f, 0.5f, 0.08f), "Stick click R", "RSB", Color(0xFF252A27),       Color.White),
    PaletteItem(ButtonConfig("SELECT", "SELECT", 0.5f, 0.5f, 0.08f), "Select",        "SEL", Color(0xFF252A27),       Color.White),
    PaletteItem(ButtonConfig("START",  "START",  0.5f, 0.5f, 0.08f), "Start",         "STA", Color(0xFF252A27),       Color.White),
)

@Composable
private fun AddPanel(onAdd: (ButtonConfig) -> Unit) {
    PanelSectionLabel("Add element")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        paletteItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1C1E))
                    .clickable { onAdd(item.template) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(item.color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.letter, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = item.onColor)
                }
                Text(item.displayLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EditorOnSurface)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── LAYERS PANEL ──────────────────────────────────────────────────────────────

@Composable
private fun LayersPanel(
    buttons: SnapshotStateList<ButtonConfig>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Layers · ${buttons.size}",
            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp, color = EditorOnVariant
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        buttons.forEach { btn ->
            val isOn = btn.id == selectedId
            val (badgeColor, badgeOn) = btnBadgeColors(btn.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOn) EditorSecContainer else Color.Transparent)
                    .clickable { onSelect(btn.id) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(btn.label.take(2), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = badgeOn)
                }
                Text(
                    text = btnDisplayName(btn.id),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isOn) EditorPrimary else EditorOnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.Visibility, contentDescription = null,
                    tint = if (isOn) EditorPrimary else EditorOnVariant,
                    modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── INSPECTOR PANEL ───────────────────────────────────────────────────────────

@Composable
private fun InspectorPanel(
    selectedId: String?,
    buttons: SnapshotStateList<ButtonConfig>,
    onDelete: (String) -> Unit,
    onSizeChange: (String, Float) -> Unit,
    onPositionChange: (String, Float, Float) -> Unit
) {
    val sel = selectedId?.let { id -> buttons.firstOrNull { it.id == id } }

    if (sel == null) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text("Tap a button\nto select it", fontSize = 12.sp, color = EditorOnVariant, lineHeight = 18.sp)
        }
        return
    }

    val (badgeColor, badgeOn) = btnBadgeColors(sel.id)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Selected", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp, color = EditorOnVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(sel.label.take(2), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = badgeOn)
                }
                Column {
                    Text(btnDisplayName(sel.id), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EditorOnSurface)
                    Text(sel.id, fontSize = 10.sp, color = EditorOnVariant)
                }
            }
        }

        InspectorDivider()

        // Size steppers
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                InspectorLabel("Size")
                Text("${(sel.sizeFrac * 1000).roundToInt()}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditorPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("−" to -0.01f, "+" to 0.01f).forEach { (lbl, delta) ->
                    Box(
                        modifier = Modifier
                            .weight(1f).height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E2220))
                            .border(1.dp, EditorBorderColor, RoundedCornerShape(8.dp))
                            .clickable { onSizeChange(sel.id, delta) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EditorOnSurface)
                    }
                }
            }
        }

        InspectorDivider()

        // Position steppers
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                InspectorLabel("Position")
                Text("X ${"%.2f".format(sel.xFrac)}  Y ${"%.2f".format(sel.yFrac)}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditorPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("X −" to (-0.02f to 0f), "X +" to (0.02f to 0f),
                       "Y −" to (0f to -0.02f), "Y +" to (0f to 0.02f)
                ).forEach { (lbl, delta) ->
                    Box(
                        modifier = Modifier
                            .weight(1f).height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1E2A))
                            .border(1.dp, EditorBorderColor, RoundedCornerShape(8.dp))
                            .clickable { onPositionChange(sel.id, delta.first, delta.second) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EditorOnSurface)
                    }
                }
            }
        }

        InspectorDivider()

        // Toggles
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111412))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Haptic on press", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EditorOnSurface)
                MiniToggle(on = true)
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth().height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(EditorErrorBg)
                .border(1.dp, Color(0xFF3D1820), RoundedCornerShape(10.dp))
                .clickable { onDelete(sel.id) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = EditorError, modifier = Modifier.size(14.dp))
                Text("Delete element", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EditorError)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MiniToggle(on: Boolean, activeColor: Color = EditorPrimary) {
    Box(
        modifier = Modifier
            .width(32.dp).height(20.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) activeColor else Color(0xFF2A2D2B))
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .offset(x = if (on) 14.dp else 2.dp, y = 2.dp)
                .clip(CircleShape)
                .background(if (on) EditorOnPrimary else EditorOnVariant)
        )
    }
}

@Composable
private fun InspectorDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(EditorBorderColor))
}

@Composable
private fun InspectorLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp, color = EditorOnVariant)
}

@Composable
private fun PanelSectionLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp, color = EditorOnVariant,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 6.dp))
}

// ── BUTTON VISUALS ────────────────────────────────────────────────────────────

@Composable
private fun EditorButtonVisual(btn: ButtonConfig, sizeDp: Float) {
    when (btn.id) {
        "LSTICK", "RSTICK" -> {
            Box(modifier = Modifier.fillMaxSize().background(StickBase, CircleShape),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size((sizeDp * 0.4f).dp).background(StickKnob, CircleShape))
                Text(if (btn.id == "LSTICK") "L" else "R",
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
        "A"  -> EditorCircleBtn(BtnA, "A", sizeDp)
        "B"  -> EditorCircleBtn(BtnB, "B", sizeDp)
        "X"  -> EditorCircleBtn(BtnX, "X", sizeDp)
        "Y"  -> EditorCircleBtn(BtnY, "Y", sizeDp)
        "LSB", "RSB" -> EditorCircleBtn(BtnSecondary, btn.label, sizeDp, 0.22f)
        "LB", "RB", "LT", "RT" -> {
            val color = if (btn.id == "LT" || btn.id == "RT") BtnSecondary else BtnPrimary
            Box(Modifier.fillMaxSize().background(color, RoundedCornerShape(8.dp)), Alignment.Center) {
                Text(btn.label, fontSize = (sizeDp * 0.28f).sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        "SELECT", "START" -> {
            Box(Modifier.fillMaxSize().background(BtnSecondary, RoundedCornerShape(50)), Alignment.Center) {
                Text(btn.label, fontSize = (sizeDp * 0.18f).sp, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 1, softWrap = false)
            }
        }
        else -> EditorCircleBtn(BtnPrimary, btn.label, sizeDp)
    }
}

@Composable
private fun EditorCircleBtn(color: Color, label: String, sizeDp: Float, fontScale: Float = 0.28f) {
    Box(Modifier.fillMaxSize().background(color, CircleShape), Alignment.Center) {
        Text(label, fontSize = (sizeDp * fontScale).sp, fontWeight = FontWeight.Bold,
            color = Color.White, maxLines = 1, softWrap = false)
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

private fun btnDisplayName(id: String): String = when {
    id == "A"      -> "A button";       id == "B"      -> "B button"
    id == "X"      -> "X button";       id == "Y"      -> "Y button"
    id == "LSTICK" -> "Left stick";     id == "RSTICK" -> "Right stick"
    id == "DPAD"   -> "D-pad"
    id == "LB"     -> "LB shoulder";   id == "RB"     -> "RB shoulder"
    id == "LT"     -> "LT trigger";    id == "RT"     -> "RT trigger"
    id == "LSB"    -> "L stick click"; id == "RSB"    -> "R stick click"
    id == "SELECT" -> "Select";         id == "START"  -> "Start"
    else           -> id
}

private fun btnBadgeColors(id: String): Pair<Color, Color> = when {
    id == "A"  -> BtnA to Color.White
    id == "B"  -> BtnB to Color.White
    id == "X"  -> BtnX to Color.White
    id == "Y"  -> BtnY to Color.White
    id.startsWith("LSTICK") || id.startsWith("RSTICK") ||
        id.startsWith("LSB") || id.startsWith("RSB") -> Color(0xFF1E2E3A) to Color(0xFF7EB8D4)
    id.startsWith("DPAD")   -> Color(0xFF2D4A3E) to Color(0xFF7FDCC4)
    id.startsWith("LB")  || id.startsWith("RB")  -> BtnPrimary  to Color.White
    id.startsWith("LT")  || id.startsWith("RT")  -> BtnSecondary to Color.White
    else -> Color(0xFF252A27) to Color.White
}
