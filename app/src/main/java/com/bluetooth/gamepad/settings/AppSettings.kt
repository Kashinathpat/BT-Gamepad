package com.bluetooth.gamepad.settings

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.bluetooth.gamepad.layout.ControllerLayout
import com.bluetooth.gamepad.motion.MotionMode
import com.bluetooth.gamepad.ui.theme.AppTheme
import com.bluetooth.gamepad.util.HapticIntensity
import kotlin.reflect.KProperty

// Key names, value types and defaults are what installed apps already have on disk; changing one resets that setting.
class AppSettings(private val prefs: SharedPreferences) {

    var isWindowsMode by boolSetting("isWindowsDInputMode", false)
    var activeLayoutId by stringSetting("activeLayoutId", ControllerLayout.DEFAULT_ID)
    var appTheme by enumSetting("appTheme", AppTheme.SYSTEM)
    var hapticIntensity by enumSetting("hapticIntensity", HapticIntensity.MEDIUM)
    var motionEnabled by boolSetting("motionEnabled", false)
    var motionMode by enumSetting("motionMode", MotionMode.AIM)
    // Degrees/second of rotation for full stick deflection (lower = faster).
    var motionSensitivity by floatSetting("motionSensDps", 90f)
    var motionInvertX by boolSetting("motionInvertX", false)
    var motionInvertY by boolSetting("motionInvertY", false)
    var autoReconnect by boolSetting("autoReconnect", true)
    var showAllDevices by boolSetting("showAllDevices", false)
    var oledMode by boolSetting("oledMode", false)
    var blockEdgeGestures by boolSetting("blockEdgeGestures", true)
    var pinScreen by boolSetting("pinScreen", false)
    var lastDeviceAddress by SavedState(prefs.getString("lastDeviceAddress", null)) { putString("lastDeviceAddress", it) }

    fun wasPermissionDenied(permission: String): Boolean = prefs.getBoolean("denied_$permission", false)

    fun markPermissionDenied(permission: String) {
        prefs.edit().putBoolean("denied_$permission", true).apply()
    }

    private fun boolSetting(key: String, default: Boolean) =
        SavedState(prefs.getBoolean(key, default)) { putBoolean(key, it) }

    private fun floatSetting(key: String, default: Float) =
        SavedState(prefs.getFloat(key, default)) { putFloat(key, it) }

    private fun stringSetting(key: String, default: String) =
        SavedState(prefs.getString(key, default) ?: default) { putString(key, it) }

    private inline fun <reified E : Enum<E>> enumSetting(key: String, default: E): SavedState<E> {
        val stored = prefs.getString(key, null)
        return SavedState(enumValues<E>().firstOrNull { it.name == stored } ?: default) { putString(key, it.name) }
    }

    private inner class SavedState<T>(initial: T, private val write: SharedPreferences.Editor.(T) -> Unit) {
        private val state = mutableStateOf(initial)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val editor = prefs.edit()
            editor.write(value)
            editor.apply()
        }
    }
}
