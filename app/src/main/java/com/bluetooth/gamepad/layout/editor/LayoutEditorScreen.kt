package com.bluetooth.gamepad.layout.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import androidx.compose.ui.unit.IntSize
import com.bluetooth.gamepad.layout.ButtonConfig
import com.bluetooth.gamepad.layout.ControllerLayout
import com.bluetooth.gamepad.layout.EditorSession
import com.bluetooth.gamepad.layout.LayoutRepository
import com.bluetooth.gamepad.layout.MAX_CONTROLS
import kotlin.math.roundToInt

private const val MIN_SIZE = 0.04f

private const val MAX_SIZE = 0.6f

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
    val labelUndoFor = remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current.density
    val context = LocalContext.current

    fun pushUndo() {
        labelUndoFor.value = null
        if (undoStack.size >= 50) undoStack.removeAt(0)
        undoStack.add(buttons.toList())
        redoStack.clear()
        edited.value = true
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        labelUndoFor.value = null
        redoStack.add(buttons.toList())
        val prev = undoStack.removeAt(undoStack.lastIndex)
        buttons.clear()
        buttons.addAll(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        labelUndoFor.value = null
        if (undoStack.size >= 50) undoStack.removeAt(0)
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
        if (buttons.size >= MAX_CONTROLS) {
            Toast.makeText(context, "A layout can have up to $MAX_CONTROLS controls", Toast.LENGTH_SHORT).show()
            return
        }
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
            buttons[i] = cur.copy(sizeFrac = (cur.sizeFrac + delta).coerceIn(MIN_SIZE, MAX_SIZE))
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

    fun updateButton(next: ButtonConfig) {
        val i = buttons.indexOfFirst { it.id == next.id }
        if (i < 0 || buttons[i] == next) return
        pushUndo()
        buttons[i] = next
    }

    fun changeLabel(id: String, text: String) {
        val i = buttons.indexOfFirst { it.id == id }
        if (i < 0) return
        if (labelUndoFor.value != id) {
            pushUndo()
            labelUndoFor.value = id
        }
        buttons[i] = buttons[i].copy(label = text)
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
                labelUndoFor.value = null
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
                    buttons[i] = cur.copy(sizeFrac = (cur.sizeFrac * zoom).coerceIn(MIN_SIZE, MAX_SIZE))
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
                onUpdate = { updateButton(it) },
                onLabelChange = { changeLabel(sel.id, it) },
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
