package com.bluetooth.gamepad.connection

import android.bluetooth.BluetoothClass

internal enum class DeviceKind { LAPTOP, COMPUTER, PHONE, HEADPHONES, SCREEN, SPEAKER, WEARABLE, KEYBOARD, MOUSE, GAMEPAD, OTHER }

private val SCREEN_CLASSES = setOf(
    BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
    BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
    BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX
)

internal fun deviceKind(majorClass: Int, deviceClass: Int): DeviceKind = when (majorClass) {
    BluetoothClass.Device.Major.COMPUTER ->
        if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP) DeviceKind.LAPTOP else DeviceKind.COMPUTER
    BluetoothClass.Device.Major.PHONE -> DeviceKind.PHONE
    BluetoothClass.Device.Major.AUDIO_VIDEO -> when (deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> DeviceKind.HEADPHONES
        in SCREEN_CLASSES -> DeviceKind.SCREEN
        else -> DeviceKind.SPEAKER
    }
    BluetoothClass.Device.Major.WEARABLE -> DeviceKind.WEARABLE
    // Peripheral minor bits: 0x40 keyboard, 0x80 pointing device; the rest are gamepads and similar.
    BluetoothClass.Device.Major.PERIPHERAL -> when {
        deviceClass and 0x40 != 0 -> DeviceKind.KEYBOARD
        deviceClass and 0x80 != 0 -> DeviceKind.MOUSE
        else -> DeviceKind.GAMEPAD
    }
    else -> DeviceKind.OTHER
}

// Audio gear, input devices and wearables never host a gamepad; unknown classes stay visible since some hosts report none.
internal fun isLikelyHostClass(majorClass: Int, deviceClass: Int): Boolean = when (majorClass) {
    BluetoothClass.Device.Major.AUDIO_VIDEO -> deviceClass in SCREEN_CLASSES
    BluetoothClass.Device.Major.PERIPHERAL,
    BluetoothClass.Device.Major.WEARABLE,
    BluetoothClass.Device.Major.HEALTH,
    BluetoothClass.Device.Major.IMAGING,
    BluetoothClass.Device.Major.TOY -> false
    else -> true
}
