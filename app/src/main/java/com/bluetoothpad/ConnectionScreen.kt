package com.bluetoothpad

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onConnectDevice: (BluetoothDevice) -> Unit
) {
    val showDeviceDialog = remember { mutableStateOf(false) }

    val statusText = buildStatusText(
        hidProfileConnected, hidAppRegistered, hidConnectionState, connectedDeviceName
    )
    val statusColor = when (hidConnectionState) {
        BluetoothProfile.STATE_CONNECTED -> Color(0xFF4CAF50)
        BluetoothProfile.STATE_CONNECTING -> Color(0xFFFFC107)
        else -> if (hidAppRegistered) Color(0xFFFFC107) else Color(0xFFCF6679)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text("Bluetooth Gamepad", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Spacer(modifier = Modifier.height(8.dp))

        if (ownDeviceName.isNotEmpty()) {
            Text("This device: $ownDeviceName", fontSize = 13.sp, color = Color(0xFFAAAAAA))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status box — tap to open device list
        Text(
            text = statusText,
            fontSize = 14.sp,
            color = statusColor,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252540), RoundedCornerShape(8.dp))
                .clickable { showDeviceDialog.value = true }
                .padding(12.dp),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Start / re-init button
        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A8A))
        ) {
            Text("Start / Reconnect", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pair / connect device
        OutlinedButton(
            onClick = { showDeviceDialog.value = true },
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF555588))
        ) {
            Text("Pair / Connect Device", color = Color(0xFFCCCCFF))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Divider(color = Color(0xFF333355))

        Spacer(modifier = Modifier.height(16.dp))

        // Windows DInput mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Windows (DInput) mode", fontSize = 15.sp, color = Color.White)
                Text("Android/Linux otherwise", fontSize = 12.sp, color = Color(0xFFAAAAAA))
            }
            Switch(
                checked = isWindowsMode,
                onCheckedChange = onWindowsModeToggle
            )
        }

    }

    if (showDeviceDialog.value) {
        DeviceListDialog(
            activity = activity,
            onDismiss = { showDeviceDialog.value = false },
            onPair = { device ->
                showDeviceDialog.value = false
                onPairDevice(device)
            },
            onUnpair = { device ->
                showDeviceDialog.value = false
                onUnpairDevice(device)
            },
            onConnect = { device ->
                showDeviceDialog.value = false
                onConnectDevice(device)
            }
        )
    }

}

@Composable
fun DeviceListDialog(
    activity: MainActivity,
    onDismiss: () -> Unit,
    onPair: (BluetoothDevice) -> Unit,
    onUnpair: (BluetoothDevice) -> Unit,
    onConnect: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    val showUnpairConfirm = remember { mutableStateOf<BluetoothDevice?>(null) }

    // Seed with already bonded devices
    DisposableEffect(Unit) {
        devices.clear()
        devices.addAll(activity.getBondedDevices())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && !devices.contains(device)) {
                        try {
                            if (device.name != null) devices.add(device)
                        } catch (_: SecurityException) {
                            devices.add(device)
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), Context.RECEIVER_EXPORTED)
        activity.startDiscovery()

        onDispose {
            activity.cancelDiscovery()
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth Devices") },
        text = {
            LazyColumn {
                items(devices) { device ->
                    val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                    val isPaired = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPaired) {
                                    showUnpairConfirm.value = device
                                } else {
                                    onPair(device)
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name ?: "Unknown", fontSize = 15.sp)
                            if (isPaired) {
                                Text("[Paired]", fontSize = 12.sp, color = Color(0xFF4CAF50))
                            }
                        }
                        if (isPaired) {
                            TextButton(onClick = { onConnect(device) }) {
                                Text("Connect")
                            }
                        }
                    }
                    Divider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    showUnpairConfirm.value?.let { device ->
        val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
        AlertDialog(
            onDismissRequest = { showUnpairConfirm.value = null },
            title = { Text("Unpair Device") },
            text = { Text("Are you sure you want to unpair from $name?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnpairConfirm.value = null
                    onUnpair(device)
                }) { Text("Unpair") }
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
    if (!profileConnected) return "HID profile not connected\nTap Start to connect"
    if (!appRegistered) return "HID profile connected\nRegistering app..."
    return when (connectionState) {
        BluetoothProfile.STATE_CONNECTING -> "Connecting to device..."
        BluetoothProfile.STATE_CONNECTED -> "Connected to: $connectedName"
        BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
        else -> "Ready — tap 'Pair / Connect Device'"
    }
}
