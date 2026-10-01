package com.bluetooth.gamepad

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

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
    val menuFor = remember { mutableStateOf<String?>(null) }
    val pendingExportId = rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun reload() {
        layouts.clear()
        layouts.addAll(repo.getAll())
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun shareLayout(layout: ControllerLayout) {
        val uri = try {
            val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, exportFileName(layout))
            file.writeText(layout.toJson().toString(2))
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (_: Exception) {
            toast("Could not share")
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    fun importText(text: String?) {
        val layout = text?.let { repo.importJson(it) }
        if (layout == null) {
            toast("Not a valid layout")
        } else {
            reload()
            toast("Imported \"${layout.name}\"")
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val layout = pendingExportId.value?.let { repo.load(it) }
        pendingExportId.value = null
        if (uri == null || layout == null) return@rememberLauncherForActivityResult
        val saved = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(layout.toJson().toString(2).toByteArray()) } != null
        } catch (_: Exception) { false }
        toast(if (saved) "Exported" else "Could not export")
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Read one byte past the limit so an oversized file is rejected without loading it all.
                val bytes = input.readAtMost(MAX_IMPORT_BYTES + 1)
                bytes.takeIf { it.size <= MAX_IMPORT_BYTES }?.toString(Charsets.UTF_8)
            }
        } catch (_: Exception) { null }
        importText(text)
    }

    Scaffold(
        containerColor = cs.background,
        floatingActionButton = {
            // Hidden while a card's action row is open so it cannot cover Export and Delete.
            AnimatedVisibility(visible = menuFor.value == null, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        newName.value = ""
                        showNewDialog.value = true
                    },
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                    modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New layout", fontWeight = FontWeight.Bold) }
                )
            }
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
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { openLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import", fontWeight = FontWeight.Bold)
                    }
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
                val expanded = menuFor.value == layout.id

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerLow)
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStart(layout) }
                            .padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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

                        IconButton(onClick = { menuFor.value = if (expanded) null else layout.id }) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = cs.onSurface
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        ) {
                            val action = Modifier.weight(1f)
                            LayoutAction(Icons.Default.Edit, "Edit", cs.onSurface, action) {
                                menuFor.value = null
                                onEdit(layout)
                            }
                            LayoutAction(Icons.Default.ContentCopy, "Duplicate", cs.onSurface, action) {
                                menuFor.value = null
                                repo.duplicate(layout)
                                reload()
                            }
                            LayoutAction(Icons.Default.Share, "Share", cs.onSurface, action) {
                                menuFor.value = null
                                shareLayout(layout)
                            }
                            LayoutAction(Icons.Default.Upload, "Export", cs.onSurface, action) {
                                menuFor.value = null
                                pendingExportId.value = layout.id
                                saveLauncher.launch(exportFileName(layout))
                            }
                            if (!layout.isDefault) {
                                LayoutAction(Icons.Default.Delete, "Delete", cs.error, action) {
                                    menuFor.value = null
                                    deleteTarget.value = layout
                                }
                            }
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
                            reload()
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
                        reload()
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

@Composable
private fun LayoutAction(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 11.sp, color = tint, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun exportFileName(layout: ControllerLayout) = layout.name.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".json"

private const val MAX_IMPORT_BYTES = 256 * 1024

private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
