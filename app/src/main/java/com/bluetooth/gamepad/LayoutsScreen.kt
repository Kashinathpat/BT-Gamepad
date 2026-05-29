package com.bluetooth.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LayoutsScreen(
    repo: LayoutRepository,
    connectedDeviceName: String = "",
    onStart: (ControllerLayout) -> Unit,
    onEdit: (ControllerLayout) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val cs = MaterialTheme.colorScheme

    val layouts = remember { mutableStateListOf<ControllerLayout>().also { it.addAll(repo.getAll()) } }
    val showNewDialog = remember { mutableStateOf(false) }
    val newName = remember { mutableStateOf("") }
    val deleteTarget = remember { mutableStateOf<ControllerLayout?>(null) }

    Scaffold(
        containerColor = cs.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newName.value = ""; showNewDialog.value = true },
                containerColor = cs.primary,
                contentColor = cs.onPrimary,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New layout", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 12.dp,
                bottom = 100.dp + contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Inline header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Layouts",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = cs.onSurface
                    )
                }
                Text(
                    "Pick a control layout or tailor one to your game.",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            // Connected device banner
            if (connectedDeviceName.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(cs.primary, RoundedCornerShape(50))
                        )
                        Text(
                            "Connected: $connectedDeviceName",
                            fontSize = 13.sp,
                            color = cs.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // YOUR LAYOUTS section label
            item {
                Text(
                    "YOUR LAYOUTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
            }

            // Layout list
            items(layouts, key = { it.id }) { layout ->
                val initials = layout.name.take(2).uppercase()
                val isAlt = layout.name.startsWith("FC", ignoreCase = true)
                val badgeBg = if (isAlt) cs.tertiaryContainer else cs.primaryContainer
                val badgeFg = if (isAlt) cs.onTertiaryContainer else cs.onPrimaryContainer

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerLow)
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Letter badge
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(badgeBg, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initials,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = badgeFg
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            layout.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${layout.buttons.size} buttons",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Button(
                        onClick = { onStart(layout) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Start", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { onEdit(layout) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(cs.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = cs.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (!layout.isDefault) {
                        IconButton(
                            onClick = { deleteTarget.value = layout },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = cs.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

        }
    }

    if (showNewDialog.value) {
        AlertDialog(
            onDismissRequest = { showNewDialog.value = false },
            title = { Text("New Layout") },
            text = {
                OutlinedTextField(
                    value = newName.value,
                    onValueChange = { newName.value = it },
                    label = { Text("Layout name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newName.value.trim()
                        if (name.isNotEmpty()) {
                            val layout = repo.newCustom(name)
                            repo.save(layout)
                            layouts.clear()
                            layouts.addAll(repo.getAll())
                            showNewDialog.value = false
                        }
                    },
                    enabled = newName.value.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog.value = false }) { Text("Cancel") }
            }
        )
    }

    deleteTarget.value?.let { layout ->
        AlertDialog(
            onDismissRequest = { deleteTarget.value = null },
            title = { Text("Delete Layout") },
            text = { Text("Delete \"${layout.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        repo.delete(layout.id)
                        layouts.clear()
                        layouts.addAll(repo.getAll())
                        deleteTarget.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error)
                ) { Text("Delete", color = cs.onError) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget.value = null }) { Text("Cancel") }
            }
        )
    }
}
