package com.bluetooth.gamepad.motion

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Gravity direction in the aim frame: rotated by the gyro, nudged toward the accelerometer
// except while the phone is being shoved around.
class GravityTracker {
    var x = 0f; private set
    var y = 0f; private set
    var z = -1f; private set
    private var initialized = false

    fun reset() {
        x = 0f; y = 0f; z = -1f
        initialized = false
    }

    fun update(wx: Float, wy: Float, wz: Float, ax: Float, ay: Float, az: Float, dt: Float) {
        val al = sqrt(ax * ax + ay * ay + az * az)
        if (!initialized) {
            if (al > 1e-6f) { x = -ax / al; y = -ay / al; z = -az / al }
            initialized = true
            return
        }
        val w = sqrt(wx * wx + wy * wy + wz * wz)
        val angle = w * dt
        if (angle > 1e-7f) {
            val kx = -wx / w; val ky = -wy / w; val kz = -wz / w
            val c = cos(angle); val s = sin(angle)
            val dot = kx * x + ky * y + kz * z
            val crX = ky * z - kz * y
            val crY = kz * x - kx * z
            val crZ = kx * y - ky * x
            x = x * c + crX * s + kx * dot * (1f - c)
            y = y * c + crY * s + ky * dot * (1f - c)
            z = z * c + crZ * s + kz * dot * (1f - c)
        }
        if (al > 1e-6f && abs(al - 9.81f) < 2f) {
            x += (-ax / al - x) * ACCEL_TRUST
            y += (-ay / al - y) * ACCEL_TRUST
            z += (-az / al - z) * ACCEL_TRUST
        }
        val gl = sqrt(x * x + y * y + z * z)
        if (gl > 1e-6f) { x /= gl; y /= gl; z /= gl }
    }

    private companion object {
        const val ACCEL_TRUST = 0.02f
    }
}
