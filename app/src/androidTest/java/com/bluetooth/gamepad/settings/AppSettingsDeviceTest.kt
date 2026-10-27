package com.bluetooth.gamepad.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bluetooth.gamepad.motion.MotionMode
import com.bluetooth.gamepad.ui.theme.AppTheme
import com.bluetooth.gamepad.util.HapticIntensity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "device_test_settings"

    private fun prefs(): SharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    @Before
    @After
    fun clean() {
        // commit() waits for any pending write, which would otherwise recreate the file after it is deleted.
        prefs().edit().clear().commit()
        context.deleteSharedPreferences(fileName)
    }

    @Test
    fun settingsSurviveReopeningTheStorageFile() {
        val first = AppSettings(prefs())
        first.isWindowsMode = true
        first.appTheme = AppTheme.AMOLED
        first.hapticIntensity = HapticIntensity.STRONG
        first.motionMode = MotionMode.STEERING
        first.motionSensitivity = 47.5f
        first.autoReconnect = false
        first.lastDeviceAddress = "AA:BB:CC:DD:EE:FF"

        val second = AppSettings(prefs())
        assertTrue(second.isWindowsMode)
        assertEquals(AppTheme.AMOLED, second.appTheme)
        assertEquals(HapticIntensity.STRONG, second.hapticIntensity)
        assertEquals(MotionMode.STEERING, second.motionMode)
        assertEquals(47.5f, second.motionSensitivity, 0f)
        assertFalse(second.autoReconnect)
        assertEquals("AA:BB:CC:DD:EE:FF", second.lastDeviceAddress)
    }

    @Test
    fun valuesAreStoredWithTheTypesEarlierVersionsUsed() {
        val s = AppSettings(prefs())
        s.motionSensitivity = 120f
        s.oledMode = true
        s.appTheme = AppTheme.DARK
        val raw = prefs()
        assertEquals(120f, raw.getFloat("motionSensDps", 0f), 0f)
        assertTrue(raw.getBoolean("oledMode", false))
        assertEquals("DARK", raw.getString("appTheme", null))
    }

    @Test
    fun valuesWrittenInTheOldWayAreReadByTheNewClass() {
        prefs().edit()
            .putFloat("motionSensDps", 33f)
            .putBoolean("pinScreen", true)
            .putBoolean("blockEdgeGestures", false)
            .putString("hapticIntensity", "LIGHT")
            .putString("motionMode", "AIM_DIRECT")
            .commit()
        val s = AppSettings(prefs())
        assertEquals(33f, s.motionSensitivity, 0f)
        assertTrue(s.pinScreen)
        assertFalse(s.blockEdgeGestures)
        assertEquals(HapticIntensity.LIGHT, s.hapticIntensity)
        assertEquals(MotionMode.AIM, s.motionMode)
    }

    @Test
    fun theRealSettingsFileIsNeverTouchedByTheseTests() {
        assertTrue(fileName != "data")
        val before = context.getSharedPreferences("data", Context.MODE_PRIVATE).all.toMap()
        AppSettings(prefs()).oledMode = true
        assertEquals(before, context.getSharedPreferences("data", Context.MODE_PRIVATE).all.toMap())
    }
}
