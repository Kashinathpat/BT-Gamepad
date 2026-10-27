package com.bluetooth.gamepad.layout.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.layout.MAX_LABEL_LENGTH

@Composable
internal fun GlassDivider(vertical: Boolean = false) {
    if (vertical) {
        Box(Modifier.height(1.dp).width(22.dp).background(EditorGlassBorder))
    } else {
        Box(Modifier.width(1.dp).height(22.dp).background(EditorGlassBorder))
    }
}

@Composable
internal fun GlassIconBtn(
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

@Composable
internal fun <T> ChipGrid(items: List<T>, perRow: Int, chip: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { chip(it, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) EditorPrimary else EditorGlassFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (selected) EditorOnPrimary else EditorGlassOn, maxLines = 1)
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanelLabel(label, Modifier.weight(1f))
        Chip(if (checked) "On" else "Off", checked, Modifier.width(52.dp), onToggle)
    }
}

@Composable
internal fun LabelField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(MAX_LABEL_LENGTH)) },
        singleLine = true,
        textStyle = TextStyle(color = EditorGlassOn, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(EditorPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EditorGlassFill)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    )
}

@Composable
internal fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp, color = EditorGlassOnVar, modifier = modifier)
}

@Composable
internal fun MiniBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
