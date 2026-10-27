package com.bluetooth.gamepad.layout.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.EditorTopBar(
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
