package com.bluetooth.gamepad

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

// Gyro aim: player-space turn speed, smoothed only when slow, mapped to stick past the game's dead zone.
// Takes gyro and accel already rotated into the aim frame.
class GyroAim {
    private var biasX = 0f; private var biasY = 0f; private var biasZ = 0f
    private var biasInitialized = false
    private var stillTimerS = 0f
    private var prevAccelMag = 9.81f
    private val gravity = GravityTracker()

    private val smoothYaw = FloatArray(SMOOTH_SAMPLES)
    private val smoothPitch = FloatArray(SMOOTH_SAMPLES)
    private var smoothIndex = 0

    fun recenter() {
        smoothYaw.fill(0f); smoothPitch.fill(0f)
    }

    fun reset() {
        biasX = 0f; biasY = 0f; biasZ = 0f
        biasInitialized = false
        stillTimerS = 0f
        prevAccelMag = 9.81f
        gravity.reset()
        recenter()
    }

    fun step(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float,
        dt: Float, maxRad: Float
    ): Pair<Float, Float> {
        if (!biasInitialized) {
            if (sqrt(gx * gx + gy * gy + gz * gz) < CALIB_SPEED * 2f) {
                biasX = gx; biasY = gy; biasZ = gz
            }
            biasInitialized = true
        }
        val accelMag = sqrt(ax * ax + ay * ay + az * az)
        val accelSteady = abs(accelMag - prevAccelMag) < CALIB_ACCEL_DELTA
        prevAccelMag = accelMag
        val rx = gx - biasX; val ry = gy - biasY; val rz = gz - biasZ
        if (sqrt(rx * rx + ry * ry + rz * rz) < CALIB_SPEED && accelSteady) {
            stillTimerS += dt
            if (stillTimerS >= CALIB_HOLD_S) {
                biasX += (gx - biasX) * CALIB_SMOOTH
                biasY += (gy - biasY) * CALIB_SMOOTH
                biasZ += (gz - biasZ) * CALIB_SMOOTH
            }
        } else {
            stillTimerS = 0f
        }
        val cgx = gx - biasX
        val cgy = gy - biasY
        val cgz = gz - biasZ

        gravity.update(cgx, cgy, cgz, ax, ay, az, dt)

        // Player space: turn sign from rotation about gravity, size from yaw and roll together.
        val worldYaw = cgy * gravity.y + cgz * gravity.z
        val yawMag = sqrt(cgy * cgy + cgz * cgz)
        var yaw = -sign(worldYaw) * min(abs(worldYaw) * YAW_RELAX, yawMag)
        var pitch = cgx

        // Tiered smoothing: full average below half the threshold, untouched above it, blended between.
        val speed = sqrt(yaw * yaw + pitch * pitch)
        val direct = ((speed - SMOOTH_THRESHOLD / 2f) / (SMOOTH_THRESHOLD / 2f)).coerceIn(0f, 1f)
        smoothIndex = (smoothIndex + 1) % SMOOTH_SAMPLES
        smoothYaw[smoothIndex] = yaw * (1f - direct)
        smoothPitch[smoothIndex] = pitch * (1f - direct)
        yaw = yaw * direct + smoothYaw.average().toFloat()
        pitch = pitch * direct + smoothPitch.average().toFloat()

        val velocity = sqrt(yaw * yaw + pitch * pitch)
        if (velocity <= 0f) return 0f to 0f
        var strength = min(velocity / maxRad, 1f).pow(1f / UNPOWER)
        // Below 1% the stick is released outright, so the dead-zone jump never leaves a creeping aim.
        if (strength <= MIN_STRENGTH) return 0f to 0f
        strength = UNDEADZONE + strength * (1f - UNDEADZONE)
        return (yaw / velocity * strength) to (pitch / velocity * strength)
    }

    private companion object {
        const val CALIB_SPEED = 0.02f
        const val CALIB_ACCEL_DELTA = 0.15f
        const val CALIB_HOLD_S = 0.12f
        const val CALIB_SMOOTH = 0.015f
        const val YAW_RELAX = 1.41f
        // 5 deg/s; sensor runs at 200 Hz, so 25 samples is a 0.125 s window.
        const val SMOOTH_THRESHOLD = 0.0873f
        const val SMOOTH_SAMPLES = 25
        const val UNPOWER = 1.35f
        const val UNDEADZONE = 0.09f
        const val MIN_STRENGTH = 0.01f
    }
}
