package com.bluetoothpad

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.bluetoothpad.ui.theme.Background
import com.bluetoothpad.ui.theme.BtnDisconnect
import com.bluetoothpad.ui.theme.CardBgDInput
import com.bluetoothpad.ui.theme.OnPrimary
import com.bluetoothpad.ui.theme.OnSurface
import com.bluetoothpad.ui.theme.OnSurfaceVariant
import com.bluetoothpad.ui.theme.OutlineVariant
import com.bluetoothpad.ui.theme.Primary
import com.bluetoothpad.ui.theme.Secondary
import com.bluetoothpad.ui.theme.StatusConnected
import com.bluetoothpad.ui.theme.StatusConnecting
import com.bluetoothpad.ui.theme.StatusError
import com.bluetoothpad.ui.theme.Surface
import com.bluetoothpad.ui.theme.TopBarBg

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
    onCancelConnect: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    val showUnpairConfirm = remember { mutableStateOf<BluetoothDevice?>(null) }
    val connectingAddress = remember { mutableStateOf<String?>(null) }

    // Clear the connecting indicator when connection resolves
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
                            try {
                                if (device.name != null) devices.add(device)
                            } catch (_: SecurityException) {
                                devices.add(device)
                            }
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        if (device != null && state == BluetoothDevice.BOND_NONE) {
                            devices.remove(device)
                        }
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
                        text = "Bluetooth Gamepad",
                        fontWeight = FontWeight.SemiBold,
                        color = OnPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TopBarBg
                )
            )
        },
        containerColor = Background
    ) { innerPadding ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Available Devices",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface
        )
        if (ownDeviceName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "This device: $ownDeviceName",
                fontSize = 13.sp,
                color = OnSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Windows DInput mode card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBgDInput, RoundedCornerShape(12.dp))
                .border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Windows DInput Mode", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = OnSurface)
                Text("Legacy controller compatibility", fontSize = 12.sp, color = OnSurfaceVariant)
            }
            Switch(
                checked = isWindowsMode,
                onCheckedChange = onWindowsModeToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnPrimary,
                    checkedTrackColor = Primary,
                    uncheckedThumbColor = OnPrimary,
                    uncheckedTrackColor = OutlineVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection status indicator
        val statusColor = when (hidConnectionState) {
            BluetoothProfile.STATE_CONNECTED -> StatusConnected
            BluetoothProfile.STATE_CONNECTING -> StatusConnecting
            else -> if (hidAppRegistered) StatusConnecting else StatusError
        }
        val statusText = buildStatusText(hidProfileConnected, hidAppRegistered, hidConnectionState, connectedDeviceName)
        Text(
            text = statusText,
            fontSize = 13.sp,
            color = statusColor,
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices) { device ->
                val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                val isPaired = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
                val address = try { device.address } catch (_: SecurityException) { "" }
                val isConnecting = connectingAddress.value == address

                val cardShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(Surface, cardShape)
                        .border(
                            width = if (isConnecting) 1.5.dp else 1.dp,
                            color = if (isConnecting) Primary else OutlineVariant.copy(alpha = 0.3f),
                            shape = cardShape
                        )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = name ?: "Unknown",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OnSurface,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = address,
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant
                                )
                            }

                            if (isPaired) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            containerColor = if (isConnecting) BtnDisconnect else Primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = if (isConnecting) "Cancel" else "Connect",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OnPrimary
                                        )
                                    }
                                    TextButton(
                                        onClick = { showUnpairConfirm.value = device },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Unpair", fontSize = 12.sp, color = BtnDisconnect)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onPairDevice(device) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Pair", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnPrimary)
                                }
                            }
                        }

                        if (isConnecting) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Primary,
                                trackColor = Primary.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Start / Reconnect button
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(99.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Secondary)
        ) {
            Text("Start / Reconnect", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnPrimary)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    } // end Scaffold

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
                }) { Text("Unpair", color = BtnDisconnect) }
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
        BluetoothProfile.STATE_CONNECTING -> "Connecting to device..."
        BluetoothProfile.STATE_CONNECTED -> "Connected to: $connectedName"
        BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
        else -> "Ready — select a device to connect"
    }
}
