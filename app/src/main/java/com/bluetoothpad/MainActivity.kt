package com.bluetoothpad

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bluetoothpad.ui.theme.BluetoothPadTheme

class MainActivity : ComponentActivity() {

    private val statusText = mutableStateOf("Tap 'Start Gamepad' to begin")
    private val connected = mutableStateOf(false)
    private val ownName = mutableStateOf("")
    private val connectedName = mutableStateOf("")
    private var gamepad: BluetoothHidGamepad? = null

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode > 0) {
            statusText.value = "Discoverable for ${result.resultCode}s. Waiting for connection..."
        } else {
            statusText.value = "Discoverability denied. Other devices won't find you."
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startGamepad()
        } else {
            statusText.value = "Bluetooth permissions denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothPadTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GamepadScreen(
                        status = statusText.value,
                        isConnected = connected.value,
                        ownDeviceName = ownName.value,
                        connectedDeviceName = connectedName.value,
                        onStartClick = { requestPermissionsAndStart() },
                        onStopClick = { stopGamepad() },
                        onButtonAClick = { gamepad?.pressAndReleaseButton(0x01) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
                return
            }
        }
        startGamepad()
    }

    private fun startGamepad() {
        if (gamepad != null) {
            statusText.value = "Already running"
            return
        }
        gamepad = BluetoothHidGamepad(this).apply {
            onStatusChanged = { status ->
                runOnUiThread {
                    statusText.value = status
                    connected.value = isConnected
                    ownName.value = ownDeviceName
                    connectedName.value = connectedDeviceName
                }
            }
        }
        val result = gamepad!!.start()
        statusText.value = result
        ownName.value = gamepad!!.ownDeviceName
        makeDiscoverable()
    }

    private fun makeDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        discoverableLauncher.launch(intent)
    }

    private fun stopGamepad() {
        gamepad?.stop()
        gamepad = null
        connected.value = false
        statusText.value = "Stopped"
    }

    override fun onDestroy() {
        super.onDestroy()
        gamepad?.stop()
    }
}

@Composable
fun GamepadScreen(
    status: String,
    isConnected: Boolean,
    ownDeviceName: String,
    connectedDeviceName: String,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onButtonAClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "BluetoothPad", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(16.dp))

        if (ownDeviceName.isNotEmpty()) {
            Text(text = "This device: $ownDeviceName", fontSize = 13.sp)
        }
        if (connectedDeviceName.isNotEmpty()) {
            Text(text = "Connected to: $connectedDeviceName", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = status, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onStartClick) {
            Text("Start Gamepad")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onStopClick) {
            Text("Stop")
        }

        if (isConnected) {
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onButtonAClick,
                modifier = Modifier
                    .height(80.dp)
                    .padding(8.dp)
            ) {
                Text("A", fontSize = 24.sp)
            }
        }
    }
}
