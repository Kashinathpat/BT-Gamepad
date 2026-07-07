package com.bluetooth.gamepad

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Games
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluetooth.gamepad.ui.theme.StatusConnected
import com.bluetooth.gamepad.ui.theme.StatusConnecting
import com.bluetooth.gamepad.ui.theme.StatusError

@Composable
fun ConnectionScreen(
    activity: MainActivity,
    hidProfileConnected: Boolean,
    hidAppRegistered: Boolean,
    hidConnectionState: Int,
    connectedDeviceName: String,
    ownDeviceName: String,
    onStartClick: () -> Unit,
    onPairDevice: (BluetoothDevice) -> Unit,
    onUnpairDevice: (BluetoothDevice) -> Unit,
    connectedDeviceAddress: String = "",
    connectedDevice: BluetoothDevice? = null,
    activeDInputMode: Boolean = false,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onCancelConnect: (BluetoothDevice) -> Unit,
    onDisconnectDevice: (BluetoothDevice) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    val showUnpairConfirm = remember { mutableStateOf<BluetoothDevice?>(null) }
    val connectingAddress = remember { mutableStateOf<String?>(null) }
    val isConnectedRef = remember { mutableStateOf(hidConnectionState == BluetoothProfile.STATE_CONNECTED) }

    val isDiscovering = remember { mutableStateOf(false) }

    LaunchedEffect(hidConnectionState) {
        isConnectedRef.value = hidConnectionState == BluetoothProfile.STATE_CONNECTED
        if (hidConnectionState == BluetoothProfile.STATE_CONNECTED ||
            hidConnectionState == BluetoothProfile.STATE_DISCONNECTED
        ) {
            connectingAddress.value = null
        }
        if (hidConnectionState == BluetoothProfile.STATE_CONNECTED) {
            activity.cancelDiscovery()
        }
    }

    DisposableEffect(Unit) {
        devices.clear()
        devices.addAll(activity.getBondedDevices())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        if (device != null && !devices.contains(device)) {
                            try { devices.add(device) } catch (_: SecurityException) { }
                        }
                    }
                    BluetoothDevice.ACTION_NAME_CHANGED -> {
                        if (device != null) {
                            val idx = devices.indexOfFirst { it.address == device.address }
                            if (idx >= 0) devices[idx] = device else devices.add(device)
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        if (device != null && state == BluetoothDevice.BOND_NONE) devices.remove(device)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        isDiscovering.value = true
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isDiscovering.value = false
                    }
                }
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).also {
            it.addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            it.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            it.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            it.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // Bluetooth discovery broadcasts come from the system process — must be EXPORTED
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        activity.startDiscovery()
        onDispose {
            activity.cancelDiscovery()
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }

    val isConnected = hidConnectionState == BluetoothProfile.STATE_CONNECTED

    Scaffold(containerColor = cs.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 12.dp,
                bottom = 16.dp + contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Inline header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(cs.primaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Games,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "Gamepad",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                            color = cs.onSurface
                        )
                        if (ownDeviceName.isNotEmpty()) {
                            Text(
                                "Broadcasting as $ownDeviceName",
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Hero connection card
            item {
                val heroGradient = if (isConnected) {
                    Brush.linearGradient(listOf(cs.primaryContainer, cs.secondaryContainer))
                } else {
                    Brush.linearGradient(listOf(cs.surfaceContainerHigh, cs.surfaceContainer))
                }
                val heroTextColor = if (isConnected) cs.onPrimaryContainer else cs.onSurface

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(heroGradient)
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val dotColor = when (hidConnectionState) {
                                BluetoothProfile.STATE_CONNECTED  -> StatusConnected
                                BluetoothProfile.STATE_CONNECTING -> StatusConnecting
                                else -> if (hidAppRegistered) StatusConnecting else StatusError
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Text(
                                buildStatusLabel(hidProfileConnected, hidAppRegistered, hidConnectionState),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = heroTextColor.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.weight(1f))
                            if (hidAppRegistered) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color.Black.copy(alpha = 0.1f),
                                            RoundedCornerShape(999.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (activeDInputMode) "DInput · Active" else "HID · Active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp,
                                        color = heroTextColor
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (isConnected) connectedDeviceName else "No device connected",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            color = heroTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (isConnected && connectedDevice != null) {
                                        onDisconnectDevice(connectedDevice)
                                    } else {
                                        onStartClick()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isConnected) cs.error else cs.primary,
                                    contentColor = cs.onPrimary
                                ),
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isConnected) "Disconnect" else "Connect",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Section header with scan indicator
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "NEARBY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            "${devices.size} devices",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { activity.makeDiscoverable() }
                        ) {
                            Text("Make visible", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (isDiscovering.value) {
                            ScanningIndicator(cs.primary)
                        } else {
                            TextButton(
                                onClick = { activity.startDiscovery() }
                            ) {
                                Text("Scan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Device list
            items(devices, key = { it.address }) { device ->
                val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                val isPaired = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
                val address = try { device.address } catch (_: SecurityException) { "" }
                val isConnecting = connectingAddress.value == address
                val isThisConnected = connectedDeviceAddress.isNotEmpty() && address == connectedDeviceAddress

                val cardBg = when {
                    isThisConnected -> cs.primaryContainer
                    isConnecting    -> cs.surfaceContainerHigh
                    else            -> cs.surfaceContainerLow
                }
                val cardBorderColor = when {
                    isThisConnected -> Color.Transparent
                    else            -> cs.outlineVariant
                }
                val textColor = if (isThisConnected) cs.onPrimaryContainer else cs.onSurface

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isThisConnected) Color.Black.copy(0.08f)
                                    else cs.surfaceContainerHigh,
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = if (isThisConnected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = address,
                                    fontSize = 11.sp,
                                    color = if (isThisConnected)
                                        cs.onPrimaryContainer.copy(alpha = 0.7f)
                                    else cs.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isPaired) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        when {
                                            isThisConnected -> onDisconnectDevice(device)
                                            isConnecting -> {
                                                connectingAddress.value = null
                                                onCancelConnect(device)
                                            }
                                            else -> {
                                                connectingAddress.value = address
                                                onConnectDevice(device)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isThisConnected -> Color.Black.copy(alpha = 0.15f)
                                            isConnecting    -> cs.error
                                            else            -> cs.primary
                                        },
                                        contentColor = when {
                                            isThisConnected -> cs.onPrimaryContainer
                                            else            -> cs.onPrimary
                                        }
                                    ),
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        when {
                                            isThisConnected -> "Disconnect"
                                            isConnecting    -> "Cancel"
                                            else            -> "Connect"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showUnpairConfirm.value = device },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Unpair", fontSize = 13.sp)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onPairDevice(device) },
                                shape = RoundedCornerShape(999.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
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
                    devices.remove(device)
                    onUnpairDevice(device)
                }) { Text("Unpair", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairConfirm.value = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ScanningIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color.copy(alpha = alpha), CircleShape)
        )
        Text(
            "Scanning",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun buildStatusLabel(
    profileConnected: Boolean,
    appRegistered: Boolean,
    connectionState: Int
): String {
    if (!profileConnected) return "HID PROFILE NOT CONNECTED"
    if (!appRegistered) return "REGISTERING APP"
    return when (connectionState) {
        BluetoothProfile.STATE_CONNECTING    -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED     -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "READY"
    }
}
