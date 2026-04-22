package com.bluetoothpad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    secondary        = Secondary,
    onSecondary      = OnSecondary,
    error            = ErrorColor,
    onError          = OnError,
    background       = Background,
    surface          = Surface,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline          = OutlineVariant
)

@Composable
fun BluetoothPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content
    )
}
