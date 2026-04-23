package com.bluetoothpad

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetoothpad.ui.theme.BtnA
import com.bluetoothpad.ui.theme.BtnB
import com.bluetoothpad.ui.theme.BtnPrimary
import com.bluetoothpad.ui.theme.BtnSecondary
import com.bluetoothpad.ui.theme.BtnX
import com.bluetoothpad.ui.theme.BtnY
import com.bluetoothpad.ui.theme.ControllerBg
import com.bluetoothpad.ui.theme.ControllerOnBtn
import com.bluetoothpad.ui.theme.DpadNormal
import com.bluetoothpad.ui.theme.EditorDelete
import com.bluetoothpad.ui.theme.EditorSave
import com.bluetoothpad.ui.theme.EditorSelected
import com.bluetoothpad.ui.theme.OverlayPill
import com.bluetoothpad.ui.theme.StickBase
import com.bluetoothpad.ui.theme.StickKnob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LayoutEditorScreen(
    layout: ControllerLayout,
    repo: LayoutRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    val buttons = remember { mutableStateListOf<ButtonConfig>().also { it.addAll(layout.buttons) } }
    val selectedId = remember { mutableStateOf<String?>(null) }
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ControllerBg)
            .onSizeChanged { canvasSize.value = it }
    ) {
        val w = canvasSize.value.width.toFloat()
        val h = canvasSize.value.height.toFloat()

        if (w > 0 && h > 0) {
            buttons.forEachIndexed { _, btn ->
                val isSelected = btn.id == selectedId.value
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
                        .then(
                            if (isSelected) Modifier.border(2.dp, EditorSelected, CircleShape)
                            else Modifier
                        )
                        .pointerInput(btn.id) {
                            detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                                val i = buttons.indexOfFirst { it.id == btn.id }
                                if (i < 0) return@detectTransformGestures
                                selectedId.value = btn.id
                                val cur = buttons[i]
                                val newX = (cur.xFrac + pan.x / w).coerceIn(0.02f, 0.98f)
                                val newY = (cur.yFrac + pan.y / h).coerceIn(0.02f, 0.98f)
                                val newSize = (cur.sizeFrac * zoom).coerceIn(0.04f, 0.35f)
                                buttons[i] = cur.copy(xFrac = newX, yFrac = newY, sizeFrac = newSize)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    EditorButton(btn = btn, sizeDp = (btnPx / density))
                }
            }
        }

        // Floating controls at top-center
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(OverlayPill, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ControllerOnBtn)
            }

            Text(
                layout.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ControllerOnBtn,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            val sel = selectedId.value?.let { id -> buttons.firstOrNull { it.id == id } }
            if (sel != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "${sel.label}  ${"%.0f".format(sel.sizeFrac * 100)}%",
                    fontSize = 11.sp,
                    color = EditorSelected
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        buttons.removeAll { it.id == sel.id }
                        selectedId.value = null
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = EditorDelete, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = {
                    repo.save(layout.copy(buttons = buttons.toList()))
                    scope.launch { snackbarState.showSnackbar("Saved") }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save", tint = EditorSave)
            }
        }

        SnackbarHost(
            hostState = snackbarState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun EditorButton(btn: ButtonConfig, sizeDp: Float) {
    val labelFontSp = (sizeDp * 0.28f).sp

    when (btn.id) {
        "LSTICK", "RSTICK" -> {
            // Analog stick: base circle with knob and L/R label
            val knobFraction = 0.4f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StickBase, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((sizeDp * knobFraction).dp)
                        .background(StickKnob, CircleShape)
                )
                Text(
                    if (btn.id == "LSTICK") "L" else "R",
                    fontSize = (sizeDp * 0.22f).sp,
                    fontWeight = FontWeight.Bold,
                    color = ControllerOnBtn.copy(alpha = 0.7f)
                )
            }
        }

        "DPAD" -> {
            // D-pad: four arrows
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val arrowSize = (sizeDp * 0.5f).dp
                val insetDp = (sizeDp * 0.06f).dp
                listOf(
                    0f to Alignment.TopCenter,
                    180f to Alignment.BottomCenter,
                    90f to Alignment.CenterStart,
                    270f to Alignment.CenterEnd
                ).forEach { (rotation, anchor) ->
                    val offsetMod = when (anchor) {
                        Alignment.TopCenter    -> Modifier.offset(y = insetDp)
                        Alignment.BottomCenter -> Modifier.offset(y = -insetDp)
                        Alignment.CenterStart  -> Modifier.offset(x = insetDp)
                        else                   -> Modifier.offset(x = -insetDp)
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = anchor) {
                        Image(
                            painter = painterResource(id = R.drawable.dpad_arrow),
                            contentDescription = null,
                            modifier = Modifier
                                .size(arrowSize)
                                .then(offsetMod)
                                .rotate(rotation),
                            colorFilter = ColorFilter.tint(DpadNormal)
                        )
                    }
                }
            }
        }

        "A" -> CircleBtn(color = BtnA, label = "A", sizeDp = sizeDp)
        "B" -> CircleBtn(color = BtnB, label = "B", sizeDp = sizeDp)
        "X" -> CircleBtn(color = BtnX, label = "X", sizeDp = sizeDp)
        "Y" -> CircleBtn(color = BtnY, label = "Y", sizeDp = sizeDp)

        "LSB", "RSB" -> CircleBtn(color = BtnSecondary, label = btn.label, sizeDp = sizeDp, fontScale = 0.22f)

        "LB", "RB", "LT", "RT" -> {
            val color = if (btn.id == "LT" || btn.id == "RT") BtnSecondary else BtnPrimary
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(btn.label, fontSize = labelFontSp, fontWeight = FontWeight.Bold, color = ControllerOnBtn)
            }
        }

        "SELECT", "START" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BtnSecondary, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(btn.label, fontSize = (sizeDp * 0.18f).sp, fontWeight = FontWeight.Bold, color = ControllerOnBtn, maxLines = 1, softWrap = false)
            }
        }

        else -> CircleBtn(color = BtnPrimary, label = btn.label, sizeDp = sizeDp)
    }
}

@Composable
private fun CircleBtn(color: Color, label: String, sizeDp: Float, fontScale: Float = 0.28f) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = (sizeDp * fontScale).sp, fontWeight = FontWeight.Bold, color = ControllerOnBtn, maxLines = 1, softWrap = false)
    }
}
