package com.bluetooth.gamepad.connection

import android.bluetooth.BluetoothClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClassifierTest {

    private val computer = BluetoothClass.Device.Major.COMPUTER
    private val phone = BluetoothClass.Device.Major.PHONE
    private val audioVideo = BluetoothClass.Device.Major.AUDIO_VIDEO
    private val peripheral = BluetoothClass.Device.Major.PERIPHERAL
    private val wearable = BluetoothClass.Device.Major.WEARABLE

    private fun kind(major: Int, device: Int) = deviceKind(major, device)

    @Test
    fun computersAreSplitIntoLaptopsAndTheRest() {
        assertEquals(DeviceKind.LAPTOP, kind(computer, BluetoothClass.Device.COMPUTER_LAPTOP))
        assertEquals(DeviceKind.COMPUTER, kind(computer, BluetoothClass.Device.COMPUTER_DESKTOP))
        assertEquals(DeviceKind.COMPUTER, kind(computer, BluetoothClass.Device.COMPUTER_UNCATEGORIZED))
    }

    @Test
    fun phonesAreRecognised() {
        assertEquals(DeviceKind.PHONE, kind(phone, BluetoothClass.Device.PHONE_SMART))
        assertEquals(DeviceKind.PHONE, kind(phone, BluetoothClass.Device.PHONE_CELLULAR))
    }

    @Test
    fun audioGearIsSplitByWhatItIs() {
        for (d in listOf(
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
        )) assertEquals(DeviceKind.HEADPHONES, kind(audioVideo, d))
        for (d in listOf(
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX
        )) assertEquals(DeviceKind.SCREEN, kind(audioVideo, d))
        assertEquals(DeviceKind.SPEAKER, kind(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO))
        assertEquals(DeviceKind.SPEAKER, kind(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED))
    }

    @Test
    fun wearablesAreWatches() {
        assertEquals(DeviceKind.WEARABLE, kind(wearable, BluetoothClass.Device.WEARABLE_WRIST_WATCH))
    }

    @Test
    fun peripheralsAreToldApartByTheirInputBits() {
        assertEquals(DeviceKind.KEYBOARD, kind(peripheral, 0x0540))
        assertEquals(DeviceKind.MOUSE, kind(peripheral, 0x0580))
        assertEquals(DeviceKind.GAMEPAD, kind(peripheral, 0x0508))
        assertEquals(DeviceKind.GAMEPAD, kind(peripheral, peripheral))
    }

    @Test
    fun aKeyboardWithAPointerCountsAsAKeyboard() {
        assertEquals(DeviceKind.KEYBOARD, kind(peripheral, 0x05C0))
    }

    @Test
    fun otherClassesGetTheGenericIcon() {
        assertEquals(DeviceKind.OTHER, kind(BluetoothClass.Device.Major.UNCATEGORIZED, 0))
        assertEquals(DeviceKind.OTHER, kind(BluetoothClass.Device.Major.NETWORKING, 0))
        assertEquals(DeviceKind.OTHER, kind(BluetoothClass.Device.Major.HEALTH, 0))
    }

    @Test
    fun computersAndPhonesCanHostAGamepad() {
        assertTrue(isLikelyHostClass(computer, BluetoothClass.Device.COMPUTER_LAPTOP))
        assertTrue(isLikelyHostClass(computer, BluetoothClass.Device.COMPUTER_DESKTOP))
        assertTrue(isLikelyHostClass(phone, BluetoothClass.Device.PHONE_SMART))
    }

    @Test
    fun screensAndSetTopBoxesCanHostAGamepad() {
        assertTrue(isLikelyHostClass(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX))
        assertTrue(isLikelyHostClass(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR))
        assertTrue(isLikelyHostClass(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER))
    }

    @Test
    fun audioGearIsNeverAHost() {
        assertFalse(isLikelyHostClass(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES))
        assertFalse(isLikelyHostClass(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET))
        assertFalse(isLikelyHostClass(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO))
        assertFalse(isLikelyHostClass(audioVideo, BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED))
    }

    @Test
    fun inputDevicesWearablesAndMedicalGearAreNeverHosts() {
        for (major in listOf(
            peripheral, wearable, BluetoothClass.Device.Major.HEALTH,
            BluetoothClass.Device.Major.IMAGING, BluetoothClass.Device.Major.TOY
        )) assertFalse(isLikelyHostClass(major, 0))
    }

    @Test
    fun unknownClassesStayVisible() {
        assertTrue(isLikelyHostClass(BluetoothClass.Device.Major.UNCATEGORIZED, 0))
        assertTrue(isLikelyHostClass(BluetoothClass.Device.Major.NETWORKING, 0))
        assertTrue(isLikelyHostClass(0x7F00, 0))
    }

    @Test
    fun everyKindThatLooksLikeAccessoryGearIsHiddenFromTheHostList() {
        val accessories = setOf(DeviceKind.HEADPHONES, DeviceKind.SPEAKER, DeviceKind.WEARABLE, DeviceKind.KEYBOARD, DeviceKind.MOUSE, DeviceKind.GAMEPAD)
        val majors = listOf(computer, phone, audioVideo, peripheral, wearable, BluetoothClass.Device.Major.UNCATEGORIZED)
        val devices = listOf(0, 0x0504, 0x0508, 0x0540, 0x0580, 0x05C0, 0x0404, 0x0408, 0x041C, 0x0420, 0x0424, 0x0428, 0x0418, 0x010C, 0x020C)
        for (major in majors) for (d in devices) {
            if (deviceKind(major, d) in accessories) assertFalse("$major/$d", isLikelyHostClass(major, d))
        }
    }
}
