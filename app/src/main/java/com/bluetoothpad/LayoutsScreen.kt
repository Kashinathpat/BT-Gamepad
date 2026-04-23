package com.bluetoothpad

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetoothpad.ui.theme.StatusConnected
import com.bluetoothpad.ui.theme.TopBarBg
import com.bluetoothpad.ui.theme.TopBarBgDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutsScreen(
    repo: LayoutRepository,
    connectedDeviceName: String = "",
    onStart: (ControllerLayout) -> Unit,
    onEdit: (ControllerLayout) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.5f

    val layouts = remember { mutableStateListOf<ControllerLayout>().also { it.addAll(repo.getAll()) } }
    val showNewDialog = remember { mutableStateOf(false) }
    val newName = remember { mutableStateOf("") }
    val deleteTarget = remember { mutableStateOf<ControllerLayout?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Layouts", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) TopBarBgDark else TopBarBg
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { newName.value = ""; showNewDialog.value = true },
                containerColor = cs.primary,
                contentColor = cs.onPrimary,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
            ) {
                Icon(Icons.Default.Add, contentDescription = "New layout")
            }
        },
        containerColor = cs.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            if (connectedDeviceName.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusConnected.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(StatusConnected, RoundedCornerShape(50))
                    )
                    Text(
                        "Connected: $connectedDeviceName",
                        fontSize = 13.sp,
                        color = StatusConnected,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "Your Layouts",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
            Text(
                "Select a layout or create a custom one.",
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(20.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(layouts) { layout ->
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(cs.surfaceContainer, shape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    layout.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface
                                )
                                Text(
                                    "${layout.buttons.size} buttons",
                                    fontSize = 12.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { onEdit(layout) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp, vertical = 0.dp
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Edit", fontSize = 13.sp)
                            }

                            Button(
                                onClick = { onStart(layout) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = cs.primary,
                                    contentColor = cs.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp, vertical = 0.dp
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Start", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(36.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp + contentPadding.calculateBottomPadding())) }
            }
        }
    }

    // New layout dialog
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

    // Delete confirmation dialog
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
