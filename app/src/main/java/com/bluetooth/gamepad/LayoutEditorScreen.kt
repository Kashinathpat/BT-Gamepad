package com.bluetooth.gamepad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val EditorBg           = Color(0xFF0B0C0D)
private val EditorCanvasBg     = Color(0xFF0E1012)
private val EditorGridLine     = Color(0x0AFFFFFF)
private val EditorPrimary      = Color(0xFF7FDCC4)
private val EditorOnPrimary    = Color(0xFF003830)
private val EditorSelection    = Color(0xFF7FDCC4)
private val EditorError        = Color(0xFFCF6679)
private val EditorErrorBg      = Color(0xFF2A1218)

// Floating-overlay (glass) palette — translucent panels that sit on top of the full-bleed canvas.
private val EditorGlass        = Color(0xF2141619) // ~95% opaque dark
private val EditorGlassBorder  = Color(0x24FFFFFF)
private val EditorGlassFill    = Color(0x14FFFFFF)
private val EditorGlassOn      = Color(0xFFFFFFFF)
private val EditorGlassOnVar   = Color(0x80FFFFFF)

@Composable
fun LayoutEditorScreen(
    layout: ControllerLayout,
    session: EditorSession,
    repo: LayoutRepository,
    onBack: () -> Unit,
    onTest: ((ControllerLayout) -> Unit)? = null
) {
    // Working state lives in the session (owned by the host) so undo/redo and edits survive a Test
    // preview and are discarded only when the editor is left entirely.
    val buttons = session.buttons
    val selectedId = session.selectedId
    val edited = session.edited
    val undoStack = session.undoStack
    val redoStack = session.redoStack
    // Transient view state — resets each time the editor is shown; need not survive Test.
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val addOpen = remember { mutableStateOf(false) }
    val snapEnabled = remember { mutableStateOf(true) }
    val showExitDialog = remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val context = LocalContext.current

    fun pushUndo() {
        undoStack.add(buttons.toList())
        redoStack.clear()
        edited.value = true
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(buttons.toList())
        val prev = undoStack.removeAt(undoStack.lastIndex)
        buttons.clear()
        buttons.addAll(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(buttons.toList())
        val next = redoStack.removeAt(redoStack.lastIndex)
        buttons.clear()
        buttons.addAll(next)
    }

    fun saveLayout() {
        repo.save(layout.copy(buttons = buttons.toList()))
        edited.value = false
        Toast.makeText(context, "Layout saved", Toast.LENGTH_SHORT).show()
    }

    // Back request: close any open overlay first; otherwise prompt to save unsaved edits, or exit.
    fun requestBack() {
        when {
            addOpen.value -> addOpen.value = false
            selectedId.value != null -> selectedId.value = null
            edited.value -> showExitDialog.value = true
            else -> onBack()
        }
    }

    fun addElement(template: ButtonConfig) {
        val w = canvasSize.value.width.toFloat()
        val h = canvasSize.value.height.toFloat()
        if (w <= 0 || h <= 0) return
        pushUndo()
        // Generate a unique id: the bare template id for the first instance, then "<id>_N" for
        // duplicates. Probe for the next free suffix so deleting an earlier duplicate can never
        // produce a colliding id.
        val existingIds = buttons.mapTo(HashSet()) { it.id }
        val newId = if (template.id !in existingIds) {
            template.id
        } else {
            var n = 2
            while ("${template.id}_$n" in existingIds) n++
            "${template.id}_$n"
        }
        buttons.add(template.copy(id = newId, xFrac = 0.5f, yFrac = 0.5f))
        selectedId.value = newId
        addOpen.value = false
    }

    fun deleteElement(id: String) {
        pushUndo()
        buttons.removeAll { it.id == id }
        if (selectedId.value == id) selectedId.value = null
    }

    fun changeSize(id: String, delta: Float) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i >= 0) {
            pushUndo()
            val cur = buttons[i]
            buttons[i] = cur.copy(sizeFrac = (cur.sizeFrac + delta).coerceIn(0.04f, 0.35f))
        }
    }

    fun changePosition(id: String, dx: Float, dy: Float) {
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

    // Hardware back closes an open overlay first, then prompts for unsaved edits, then exits.
    BackHandler { requestBack() }

    // Full-bleed canvas with all editor chrome floating on top as overlays — the canvas is never
    // resized by panels, so what you place is what you play.
    Box(modifier = Modifier.fillMaxSize().background(EditorBg)) {
        EditorCanvas(
            buttons = buttons,
            selectedId = selectedId.value,
            snapEnabled = snapEnabled.value,
            density = density,
            onSizeChanged = { canvasSize.value = it },
            onSelect = { id ->
                selectedId.value = id
                addOpen.value = false
            },
            onMove = { id, dx, dy ->
                // Move freely during the drag; snapping happens on release (onMoveEnd) so small
                // movements are not pulled back to the same grid cell mid-drag.
                val i = buttons.indexOfFirst { it.id == id }
                if (i >= 0) {
                    val w = canvasSize.value.width.toFloat()
                    val h = canvasSize.value.height.toFloat()
                    val cur = buttons[i]
                    if (w > 0 && h > 0) {
                        buttons[i] = cur.copy(
                            xFrac = (cur.xFrac + dx / w).coerceIn(0.02f, 0.98f),
                            yFrac = (cur.yFrac + dy / h).coerceIn(0.02f, 0.98f)
                        )
                    }
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
            onMoveEnd = { id ->
                if (snapEnabled.value) {
                    val i = buttons.indexOfFirst { it.id == id }
                    val w = canvasSize.value.width.toFloat()
                    val h = canvasSize.value.height.toFloat()
                    if (i >= 0 && w > 0 && h > 0) {
                        val cur = buttons[i]
                        // Snap the button centre to the same 16.dp grid the canvas draws.
                        val stepPx = 16f * density
                        val cx = (cur.xFrac * w / stepPx).roundToInt() * stepPx
                        val cy = (cur.yFrac * h / stepPx).roundToInt() * stepPx
                        buttons[i] = cur.copy(
                            xFrac = (cx / w).coerceIn(0.02f, 0.98f),
                            yFrac = (cy / h).coerceIn(0.02f, 0.98f)
                        )
                    }
                }
            },
            onDelete = { id -> deleteElement(id) }
        )

        EditorTopBar(
            layoutName = layout.name,
            snapEnabled = snapEnabled.value,
            addOpen = addOpen.value,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            containerSize = canvasSize.value,
            onBack = { requestBack() },
            onUndo = { undo() },
            onRedo = { redo() },
            onSnapToggle = { snapEnabled.value = !snapEnabled.value },
            onAddToggle = {
                addOpen.value = !addOpen.value
                if (addOpen.value) selectedId.value = null
            },
            onTest = { onTest?.invoke(layout.copy(buttons = buttons.toList())) },
            onSave = { saveLayout() }
        )

        val sel = selectedId.value?.let { id -> buttons.firstOrNull { it.id == id } }
        if (sel != null && !addOpen.value) {
            InspectorPanel(
                button = sel,
                containerSize = canvasSize.value,
                onClose = { selectedId.value = null },
                onSizeChange = { delta -> changeSize(sel.id, delta) },
                onPositionChange = { dx, dy -> changePosition(sel.id, dx, dy) },
                onDelete = { deleteElement(sel.id) }
            )
        }

        if (addOpen.value) {
            AddPanel(
                onAdd = { template -> addElement(template) },
                onClose = { addOpen.value = false },
                containerSize = canvasSize.value
            )
        }
    }

    if (showExitDialog.value) {
        AlertDialog(
            onDismissRequest = { showExitDialog.value = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes to \"${layout.name}\" before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog.value = false
                    saveLayout()
                    onBack()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExitDialog.value = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showExitDialog.value = false
                        onBack()
                    }) { Text("Discard") }
                }
            }
        )
    }
}

// ── TOP BAR (floating) ────────────────────────────────────────────────────────

@Composable
private fun BoxScope.EditorTopBar(
    layoutName: String,
    snapEnabled: Boolean,
    addOpen: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    containerSize: IntSize,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSnapToggle: () -> Unit,
    onAddToggle: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    // The bar floats and is draggable by its grip / name area. It clamps inside the container and
    // turns vertical when parked near the left or right edge, horizontal otherwise.
    val offset = remember { mutableStateOf<Offset?>(null) } // null until first placed (top-centred)
    val barSize = remember { mutableStateOf(IntSize.Zero) }
    // The bar's width while horizontal, captured once. Edge-zone math uses this stable value so the
    // orientation decision never depends on the *current* (flipping) width and cannot oscillate.
    val horizontalWidth = remember { mutableStateOf(0) }
    val vertical = remember { mutableStateOf(false) }

    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val bw = barSize.value.width.toFloat()
    val bh = barSize.value.height.toFloat()

    // Default position: horizontally centred, 10dp from the top.
    val topPad = with(LocalDensity.current) { 10.dp.toPx() }
    val base = offset.value ?: Offset(if (cw > 0 && bw > 0) (cw - bw) / 2f else 0f, topPad)

    // Clamp so the whole bar stays on screen.
    val clampedX = if (cw > 0 && bw > 0) base.x.coerceIn(0f, (cw - bw).coerceAtLeast(0f)) else base.x
    val clampedY = if (ch > 0 && bh > 0) base.y.coerceIn(0f, (ch - bh).coerceAtLeast(0f)) else base.y

    val edgePx = with(LocalDensity.current) { 24.dp.toPx() }
    // Key on containerSize so the gesture lambda re-captures fresh cw/ch once the container is
    // measured (it is 0 on first composition).
    val dragModifier = Modifier.pointerInput(containerSize) {
        detectDragGestures { change, drag ->
            change.consume()
            // Seed from the live offset (or the current clamped spot before the first move), then
            // accumulate raw deltas. Reading offset.value here rather than a value captured at
            // composition is what lets the bar actually follow the finger instead of snapping back.
            val cur = offset.value ?: Offset(clampedX, clampedY)
            val maxX = (cw - barSize.value.width).coerceAtLeast(0f)
            val maxY = (ch - barSize.value.height).coerceAtLeast(0f)
            val nx = (cur.x + drag.x).coerceIn(0f, if (maxX > 0f) maxX else cur.x)
            val ny = (cur.y + drag.y).coerceIn(0f, if (maxY > 0f) maxY else cur.y)
            offset.value = Offset(nx, ny)
            // Dock vertical when the (horizontal) bar's left or right edge reaches a screen edge.
            // Uses the stable horizontal width so flipping can't change the inputs and oscillate.
            val hw = horizontalWidth.value.toFloat()
            if (cw > 0 && hw > 0) {
                vertical.value = nx <= edgePx || nx + hw >= cw - edgePx
            }
        }
    }

    val grip: @Composable () -> Unit = {
        Box(
            modifier = Modifier.then(dragModifier).size(width = 18.dp, height = 32.dp),
            contentAlignment = Alignment.Center
        ) { GripDots() }
    }
    val backBtn: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                tint = EditorGlassOn, modifier = Modifier.size(17.dp))
        }
    }
    val undoBtn: @Composable () -> Unit = { GlassIconBtn(Icons.AutoMirrored.Filled.Undo, enabled = canUndo, onClick = onUndo) }
    val redoBtn: @Composable () -> Unit = { GlassIconBtn(Icons.AutoMirrored.Filled.Redo, enabled = canRedo, onClick = onRedo) }
    val snapBtn: @Composable () -> Unit = { GlassIconBtn(Icons.Default.GridOn, active = snapEnabled, onClick = onSnapToggle) }
    val addBtn: @Composable () -> Unit = {
        GlassIconBtn(Icons.Default.Add, active = addOpen, onClick = onAddToggle)
    }
    val testBtn: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                .border(1.dp, EditorGlassBorder, RoundedCornerShape(9.dp))
                .clickable(onClick = onTest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Test", tint = EditorGlassOn, modifier = Modifier.size(15.dp))
        }
    }
    val saveBtn: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                .background(EditorPrimary).clickable(onClick = onSave),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = "Save", tint = EditorOnPrimary, modifier = Modifier.size(16.dp))
        }
    }

    val barModifier = Modifier
        .align(Alignment.TopStart)
        .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
        .onSizeChanged {
            barSize.value = it
            // Remember the width only in the horizontal layout — the stable reference for docking.
            if (!vertical.value) horizontalWidth.value = it.width
        }
        .clip(RoundedCornerShape(14.dp))
        .background(EditorGlass)
        .border(1.dp, EditorGlassBorder, RoundedCornerShape(14.dp))
        .padding(6.dp)

    if (vertical.value) {
        Column(
            modifier = barModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grip(); backBtn(); GlassDivider(vertical = true)
            undoBtn(); redoBtn(); GlassDivider(vertical = true)
            snapBtn(); addBtn(); testBtn(); saveBtn()
        }
    } else {
        Row(
            modifier = barModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grip(); backBtn()
            Box(modifier = Modifier.then(dragModifier)) {
                Text(
                    text = layoutName,
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = EditorGlassOn,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = 2.dp)
                )
            }
            GlassDivider(); undoBtn(); redoBtn(); GlassDivider()
            snapBtn(); addBtn(); testBtn(); saveBtn()
        }
    }
}

@Composable
private fun GlassDivider(vertical: Boolean = false) {
    if (vertical) {
        Box(Modifier.height(1.dp).width(22.dp).background(EditorGlassBorder))
    } else {
        Box(Modifier.width(1.dp).height(22.dp).background(EditorGlassBorder))
    }
}

@Composable
private fun GlassIconBtn(
    icon: ImageVector,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> EditorGlassOn.copy(alpha = 0.35f)
        active   -> EditorOnPrimary
        else     -> EditorGlassOn
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) EditorPrimary else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
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
                            // Gesture finished — snap the final resting position to the grid (if on),
                            // then arm the next gesture for its own undo entry. Snapping per-frame
                            // instead would fight small drags and feel stuck.
                            if (gesturePushed.value) {
                                onMoveEnd(btn.id)
                                gesturePushed.value = false
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

// ── ADD PANEL DATA ────────────────────────────────────────────────────────────

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
private fun GripDots() {
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

// ── INSPECTOR PANEL (floating) ────────────────────────────────────────────────

@Composable
private fun BoxScope.InspectorPanel(
    button: ButtonConfig,
    containerSize: IntSize,
    onClose: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    val (badgeColor, badgeOn) = btnBadgeColors(button.id)
    DraggablePanel(
        width = 188.dp,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
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

@Composable
private fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp, color = EditorGlassOnVar, modifier = modifier)
}

@Composable
private fun MiniBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(EditorGlassFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EditorGlassOn)
    }
}

// ── ADD PANEL (floating) ──────────────────────────────────────────────────────

@Composable
private fun BoxScope.AddPanel(onAdd: (ButtonConfig) -> Unit, onClose: () -> Unit, containerSize: IntSize) {
    DraggablePanel(
        width = 300.dp,
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
            paletteItems.chunked(4).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EditorGlassFill)
                                .clickable { onAdd(item.template) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(item.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.letter, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = item.onColor)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(item.displayLabel, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                color = EditorGlassOn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // Pad the final partial row so tiles keep a consistent width.
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ── BUTTON VISUALS ────────────────────────────────────────────────────────────

@Composable
private fun EditorButtonVisual(btn: ButtonConfig, sizeDp: Float) {
    when (val base = btn.baseId) {
        "LSTICK", "RSTICK" -> {
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
        "A"  -> EditorCircleBtn(BtnA, "A", sizeDp)
        "B"  -> EditorCircleBtn(BtnB, "B", sizeDp)
        "X"  -> EditorCircleBtn(BtnX, "X", sizeDp)
        "Y"  -> EditorCircleBtn(BtnY, "Y", sizeDp)
        "LSB", "RSB" -> EditorCircleBtn(BtnSecondary, btn.label, sizeDp, 0.22f)
        "LB", "RB", "LT", "RT" -> {
            val color = if (base == "LT" || base == "RT") BtnSecondary else BtnPrimary
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

private fun btnDisplayName(id: String): String = when (id.baseButtonId()) {
    "A"      -> "A button";       "B"      -> "B button"
    "X"      -> "X button";       "Y"      -> "Y button"
    "LSTICK" -> "Left stick";     "RSTICK" -> "Right stick"
    "DPAD"   -> "D-pad"
    "LB"     -> "LB shoulder";   "RB"     -> "RB shoulder"
    "LT"     -> "LT trigger";    "RT"     -> "RT trigger"
    "LSB"    -> "L stick click"; "RSB"    -> "R stick click"
    "SELECT" -> "Select";         "START"  -> "Start"
    else           -> id
}

private fun btnBadgeColors(id: String): Pair<Color, Color> = when (id.baseButtonId()) {
    "A"  -> BtnA to Color.White
    "B"  -> BtnB to Color.White
    "X"  -> BtnX to Color.White
    "Y"  -> BtnY to Color.White
    "LSTICK", "RSTICK", "LSB", "RSB" -> Color(0xFF1E2E3A) to Color(0xFF7EB8D4)
    "DPAD"   -> Color(0xFF2D4A3E) to Color(0xFF7FDCC4)
    "LB", "RB"  -> BtnPrimary  to Color.White
    "LT", "RT"  -> BtnSecondary to Color.White
    else -> Color(0xFF252A27) to Color.White
}
