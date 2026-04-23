package com.bluetoothpad

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.bluetoothpad.ui.theme.StatusConnected
import com.bluetoothpad.ui.theme.StatusConnecting
import com.bluetoothpad.ui.theme.StatusError
import com.bluetoothpad.ui.theme.TopBarBg
import com.bluetoothpad.ui.theme.TopBarBgDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    activity: MainActivity,
    hidProfileConnected: Boolean,
    hidAppRegistered: Boolean,
    hidConnectionState: Int,
    connectedDeviceName: String,
    ownDeviceName: String,
    isWindowsMode: Boolean,
    onStartClick: () -> Unit,
    onWindowsModeToggle: (Boolean) -> Unit,
    onPairDevice: (BluetoothDevice) -> Unit,
    onUnpairDevice: (BluetoothDevice) -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onCancelConnect: (BluetoothDevice) -> Unit,
    onSettingsClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.5f
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    val showUnpairConfirm = remember { mutableStateOf<BluetoothDevice?>(null) }
    val connectingAddress = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hidConnectionState) {
        if (hidConnectionState == BluetoothProfile.STATE_CONNECTED ||
            hidConnectionState == BluetoothProfile.STATE_DISCONNECTED
        ) {
            connectingAddress.value = null
        }
    }

    DisposableEffect(Unit) {
        devices.clear()
        devices.addAll(activity.getBondedDevices())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && !devices.contains(device)) {
                            try { if (device.name != null) devices.add(device) }
                            catch (_: SecurityException) { devices.add(device) }
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        if (device != null && state == BluetoothDevice.BOND_NONE) devices.remove(device)
                    }
                }
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).also {
            it.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        activity.startDiscovery()
        onDispose {
            activity.cancelDiscovery()
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bluetooth Gamepad",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) TopBarBgDark else TopBarBg
                )
            )
        },
        containerColor = cs.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Status banner
            val statusColor = when (hidConnectionState) {
                BluetoothProfile.STATE_CONNECTED  -> StatusConnected
                BluetoothProfile.STATE_CONNECTING -> StatusConnecting
                else -> if (hidAppRegistered) StatusConnecting else StatusError
            }
            val statusText = buildStatusText(hidProfileConnected, hidAppRegistered, hidConnectionState, connectedDeviceName)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = if (isDark) 0.2f else 0.1f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Text(statusText, fontSize = 13.sp, color = statusColor, fontWeight = FontWeight.Medium)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // DInput card
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Windows DInput Mode",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onPrimaryContainer
                            )
                            Text(
                                "Legacy controller compatibility",
                                fontSize = 12.sp,
                                color = cs.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isWindowsMode,
                            onCheckedChange = onWindowsModeToggle
                        )
                    }
                }

                // Section header
                item {
                    if (ownDeviceName.isNotEmpty()) {
                        Text(
                            "NEARBY DEVICES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    } else {
                        Text(
                            "NEARBY DEVICES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }

                // Device cards
                items(devices) { device ->
                    val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                    val isPaired = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
                    val address = try { device.address } catch (_: SecurityException) { "" }
                    val isConnecting = connectingAddress.value == address

                    val cardBg = if (isConnecting) cs.primaryContainer.copy(alpha = 0.4f) else cs.surfaceContainer
                    val cardShape = RoundedCornerShape(16.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(cardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Device icon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isPaired) cs.primaryContainer else cs.secondaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (isPaired) cs.onPrimaryContainer else cs.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name ?: "Unknown",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = address,
                                    fontSize = 12.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }

                            if (isPaired) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            if (isConnecting) {
                                                connectingAddress.value = null
                                                onCancelConnect(device)
                                            } else {
                                                connectingAddress.value = address
                                                onConnectDevice(device)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isConnecting) cs.error else cs.primary,
                                            contentColor = if (isConnecting) cs.onError else cs.onPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 14.dp, vertical = 0.dp
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(
                                            if (isConnecting) "Cancel" else "Connect",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    OutlinedButton(
                                        onClick = { showUnpairConfirm.value = device },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 10.dp, vertical = 0.dp
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Unpair", fontSize = 13.sp)
                                    }
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { onPairDevice(device) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 14.dp, vertical = 0.dp
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Pair", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (isConnecting) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = cs.primary,
                                trackColor = cs.primaryContainer
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // Bottom action area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (ownDeviceName.isNotEmpty()) {
                    Text(
                        "Broadcasting as: $ownDeviceName",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start / Reconnect", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    showUnpairConfirm.value?.let { device ->
        val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
        AlertDialog(
            onDismissRequest = { showUnpairConfirm.value = null },
            title = { Text("Unpair Device") },
            text = { Text("Are you sure you want to unpair from $name?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnpairConfirm.value = null
                    onUnpairDevice(device)
                }) { Text("Unpair", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairConfirm.value = null }) { Text("Cancel") }
            }
        )
    }
}

private fun buildStatusText(
    profileConnected: Boolean,
    appRegistered: Boolean,
    connectionState: Int,
    connectedName: String
): String {
    if (!profileConnected) return "HID profile not connected — tap Start / Reconnect"
    if (!appRegistered) return "HID profile connected — registering app..."
    return when (connectionState) {
        BluetoothProfile.STATE_CONNECTING   -> "Connecting to device..."
        BluetoothProfile.STATE_CONNECTED    -> "Connected to: $connectedName"
        BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
        else -> "Ready — select a device to connect"
    }
}
