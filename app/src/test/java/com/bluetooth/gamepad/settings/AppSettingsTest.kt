package com.bluetooth.gamepad.settings

import com.bluetooth.gamepad.motion.MotionMode
import com.bluetooth.gamepad.ui.theme.AppTheme
import com.bluetooth.gamepad.util.HapticIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun emptyStorageGivesTheDefaultsTheAppAlwaysUsed() {
        val s = AppSettings(FakePrefs())
        assertFalse(s.isWindowsMode)
        assertEquals("default", s.activeLayoutId)
        assertEquals(AppTheme.SYSTEM, s.appTheme)
        assertEquals(HapticIntensity.MEDIUM, s.hapticIntensity)
        assertFalse(s.motionEnabled)
        assertEquals(MotionMode.AIM, s.motionMode)
        assertEquals(90f, s.motionSensitivity, 0f)
        assertFalse(s.motionInvertX)
        assertFalse(s.motionInvertY)
        assertTrue(s.autoReconnect)
        assertFalse(s.showAllDevices)
        assertFalse(s.oledMode)
        assertTrue(s.blockEdgeGestures)
        assertFalse(s.pinScreen)
        assertNull(s.lastDeviceAddress)
    }

    @Test
    fun valuesSavedByEarlierVersionsAreRead() {
        val s = AppSettings(
            FakePrefs(
                mapOf(
                    "isWindowsDInputMode" to true,
                    "activeLayoutId" to "custom_1",
                    "appTheme" to "AMOLED",
                    "hapticIntensity" to "STRONG",
                    "motionEnabled" to true,
                    "motionMode" to "STEERING",
                    "motionSensDps" to 55f,
                    "motionInvertX" to true,
                    "motionInvertY" to true,
                    "autoReconnect" to false,
                    "showAllDevices" to true,
                    "oledMode" to true,
                    "blockEdgeGestures" to false,
                    "pinScreen" to true,
                    "lastDeviceAddress" to "AA:BB:CC:DD:EE:FF"
                )
            )
        )
        assertTrue(s.isWindowsMode)
        assertEquals("custom_1", s.activeLayoutId)
        assertEquals(AppTheme.AMOLED, s.appTheme)
        assertEquals(HapticIntensity.STRONG, s.hapticIntensity)
        assertTrue(s.motionEnabled)
        assertEquals(MotionMode.STEERING, s.motionMode)
        assertEquals(55f, s.motionSensitivity, 0f)
        assertTrue(s.motionInvertX)
        assertTrue(s.motionInvertY)
        assertFalse(s.autoReconnect)
        assertTrue(s.showAllDevices)
        assertTrue(s.oledMode)
        assertFalse(s.blockEdgeGestures)
        assertTrue(s.pinScreen)
        assertEquals("AA:BB:CC:DD:EE:FF", s.lastDeviceAddress)
    }

    @Test
    fun everyThemeAndIntensityNameIsStillRecognised() {
        for (theme in listOf("LIGHT" to AppTheme.LIGHT, "DARK" to AppTheme.DARK, "AMOLED" to AppTheme.AMOLED, "SYSTEM" to AppTheme.SYSTEM)) {
            assertEquals(theme.second, AppSettings(FakePrefs(mapOf("appTheme" to theme.first))).appTheme)
        }
        for (level in listOf("OFF" to HapticIntensity.OFF, "LIGHT" to HapticIntensity.LIGHT, "MEDIUM" to HapticIntensity.MEDIUM, "STRONG" to HapticIntensity.STRONG)) {
            assertEquals(level.second, AppSettings(FakePrefs(mapOf("hapticIntensity" to level.first))).hapticIntensity)
        }
    }

    @Test
    fun unknownOrRetiredNamesFallBackToTheDefault() {
        assertEquals(AppTheme.SYSTEM, AppSettings(FakePrefs(mapOf("appTheme" to "NEON"))).appTheme)
        assertEquals(HapticIntensity.MEDIUM, AppSettings(FakePrefs(mapOf("hapticIntensity" to "huge"))).hapticIntensity)
        for (retired in listOf("AIM_DIRECT", "AIM", "")) {
            assertEquals(MotionMode.AIM, AppSettings(FakePrefs(mapOf("motionMode" to retired))).motionMode)
        }
    }

    @Test
    fun changesAreSavedUnderTheSameKeysAndTypes() {
        val prefs = FakePrefs()
        val s = AppSettings(prefs)
        s.isWindowsMode = true
        s.activeLayoutId = "x"
        s.appTheme = AppTheme.DARK
        s.hapticIntensity = HapticIntensity.LIGHT
        s.motionEnabled = true
        s.motionMode = MotionMode.STEERING
        s.motionSensitivity = 120f
        s.motionInvertX = true
        s.motionInvertY = true
        s.autoReconnect = false
        s.showAllDevices = true
        s.oledMode = true
        s.blockEdgeGestures = false
        s.pinScreen = true
        s.lastDeviceAddress = "11:22:33:44:55:66"

        val expected = mapOf<String, Any>(
            "isWindowsDInputMode" to true,
            "activeLayoutId" to "x",
            "appTheme" to "DARK",
            "hapticIntensity" to "LIGHT",
            "motionEnabled" to true,
            "motionMode" to "STEERING",
            "motionSensDps" to 120f,
            "motionInvertX" to true,
            "motionInvertY" to true,
            "autoReconnect" to false,
            "showAllDevices" to true,
            "oledMode" to true,
            "blockEdgeGestures" to false,
            "pinScreen" to true,
            "lastDeviceAddress" to "11:22:33:44:55:66"
        )
        assertEquals(expected, prefs.data)
        assertTrue(prefs.data["motionSensDps"] is Float)
    }

    @Test
    fun changesSurviveARestart() {
        val prefs = FakePrefs()
        val first = AppSettings(prefs)
        first.oledMode = true
        first.motionSensitivity = 40f
        first.appTheme = AppTheme.LIGHT
        first.lastDeviceAddress = "AA:AA"

        val second = AppSettings(prefs)
        assertTrue(second.oledMode)
        assertEquals(40f, second.motionSensitivity, 0f)
        assertEquals(AppTheme.LIGHT, second.appTheme)
        assertEquals("AA:AA", second.lastDeviceAddress)
    }

    @Test
    fun aChangeIsVisibleImmediately() {
        val s = AppSettings(FakePrefs())
        assertFalse(s.pinScreen)
        s.pinScreen = true
        assertTrue(s.pinScreen)
        s.pinScreen = false
        assertFalse(s.pinScreen)
    }

    @Test
    fun clearingTheLastDeviceRemovesItsKey() {
        val prefs = FakePrefs(mapOf("lastDeviceAddress" to "AA:BB"))
        val s = AppSettings(prefs)
        s.lastDeviceAddress = null
        assertFalse(prefs.data.containsKey("lastDeviceAddress"))
        assertNull(s.lastDeviceAddress)
    }

    @Test
    fun savingSettingsLeavesLayoutDataAlone() {
        val layouts = mapOf<String, Any>("layout_ids" to "a,b", "layout_a" to "{\"x\":1}", "layout_b" to "{\"y\":2}")
        val prefs = FakePrefs(layouts)
        val s = AppSettings(prefs)
        s.oledMode = true
        s.activeLayoutId = "b"
        s.lastDeviceAddress = "AA"
        layouts.forEach { (k, v) -> assertEquals(v, prefs.data[k]) }
    }

    @Test
    fun permissionDeniedFlagUsesTheExistingKey() {
        val prefs = FakePrefs()
        val s = AppSettings(prefs)
        assertFalse(s.wasPermissionDenied("android.permission.BLUETOOTH_CONNECT"))
        s.markPermissionDenied("android.permission.BLUETOOTH_CONNECT")
        assertTrue(s.wasPermissionDenied("android.permission.BLUETOOTH_CONNECT"))
        assertEquals(true, prefs.data["denied_android.permission.BLUETOOTH_CONNECT"])
        assertFalse(s.wasPermissionDenied("android.permission.BLUETOOTH_SCAN"))
    }

    @Test
    fun readingSettingsNeverWritesToStorage() {
        val prefs = FakePrefs()
        val s = AppSettings(prefs)
        s.isWindowsMode
        s.motionSensitivity
        s.lastDeviceAddress
        assertEquals(0, prefs.applyCount)
        assertTrue(prefs.data.isEmpty())
    }
}
