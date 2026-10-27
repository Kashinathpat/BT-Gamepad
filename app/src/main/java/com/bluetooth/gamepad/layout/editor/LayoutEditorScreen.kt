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
    // Transient view state — resets each time the editor is shown; need not survive Test.
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val addOpen = remember { mutableStateOf(false) }
    val snapEnabled = remember { mutableStateOf(true) }
    val showExitDialog = remember { mutableStateOf(false) }
    // Label edits made before a Test preview start a fresh undo step.
    remember(session) { session.endLabelEdit() }
    val density = LocalDensity.current.density
    val context = LocalContext.current

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
        session.addElement(template)
        addOpen.value = false
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
                session.select(id)
                addOpen.value = false
            },
            onMove = { id, dx, dy ->
                session.dragBy(id, dx, dy, canvasSize.value.width.toFloat(), canvasSize.value.height.toFloat())
            },
            onScale = { id, zoom -> session.scaleBy(id, zoom) },
            onMoveStart = { session.pushUndo() },
            onMoveEnd = { id ->
                // Snap the button centre to the same 16.dp grid the canvas draws.
                if (snapEnabled.value) {
                    session.snapToGrid(id, canvasSize.value.width.toFloat(), canvasSize.value.height.toFloat(), 16f * density)
                }
            },
            onDelete = { id -> session.deleteElement(id) }
        )

        EditorTopBar(
            layoutName = layout.name,
            snapEnabled = snapEnabled.value,
            addOpen = addOpen.value,
            canUndo = session.undoStack.isNotEmpty(),
            canRedo = session.redoStack.isNotEmpty(),
            containerSize = canvasSize.value,
            onBack = { requestBack() },
            onUndo = { session.undo() },
            onRedo = { session.redo() },
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
                onSizeChange = { delta -> session.changeSize(sel.id, delta) },
                onPositionChange = { dx, dy -> session.changePosition(sel.id, dx, dy) },
                onUpdate = { session.updateButton(it) },
                onLabelChange = { session.changeLabel(sel.id, it) },
                onDelete = { session.deleteElement(sel.id) }
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
