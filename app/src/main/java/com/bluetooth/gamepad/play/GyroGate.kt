package com.bluetooth.gamepad.play

import com.bluetooth.gamepad.bluetooth.BluetoothHidGamepad
import com.bluetooth.gamepad.motion.MotionSensorManager

// Sensor thread emits through here; the lock orders the final zero after any in-flight sample.
internal class GyroGate {
    private var allowed = true
    var userOn = true
    var gated = false
    var holds = 0

    fun update(motion: MotionSensorManager, gamepad: BluetoothHidGamepad?) {
        val next = userOn && (!gated || holds > 0)
        synchronized(this) {
            if (next == allowed) return
            allowed = next
            motion.recenter()
            if (!next) gamepad?.setRightStickMotion(0f, 0f)
        }
    }

    fun close(gamepad: BluetoothHidGamepad?) {
        synchronized(this) {
            allowed = false
            gamepad?.setRightStickMotion(0f, 0f)
        }
    }

    fun emit(gamepad: BluetoothHidGamepad?, x: Float, y: Float) {
        synchronized(this) {
            if (allowed) gamepad?.setRightStickMotion(x, y)
        }
    }
}
