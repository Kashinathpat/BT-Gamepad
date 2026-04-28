package com.bluetooth.gamepad

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

enum class MotionSensitivity { LOW, MEDIUM, HIGH }

class MotionSensorManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isSupported: Boolean get() = rotationSensor != null

    var onMotion: ((x: Float, y: Float) -> Unit)? = null

    private var baseAngles: FloatArray? = null
    private val rotMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Smoothed output values (exponential moving average)
    private var smoothX = 0f
    private var smoothY = 0f
    private val SMOOTHING = 0.35f

    private val DEAD_ZONE = 0.04f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

            // Remap for landscape-right orientation (controller is always landscape).
            // AXIS_X stays as X; old Y becomes negative Z, so we use AXIS_X, AXIS_Z
            // which correctly maps: tilt up/down → Y, tilt left/right → X.
            SensorManager.remapCoordinateSystem(
                rotMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            SensorManager.getOrientation(remappedMatrix, orientation)

            // After remap for landscape:
            //   orientation[1] (pitch) = tilt forward/back → stick Y
            //   orientation[2] (roll)  = tilt left/right   → stick X
            val rawPitch = orientation[1]
            val rawRoll  = orientation[2]

            val base = baseAngles
            if (base == null) {
                baseAngles = floatArrayOf(rawRoll, rawPitch)
                return
            }

            val dx = rawRoll  - base[0]
            val dy = rawPitch - base[1]

            val x = applyDeadZone(dx).coerceIn(-1f, 1f)
            val y = applyDeadZone(dy).coerceIn(-1f, 1f)

            smoothX = smoothX * (1f - SMOOTHING) + x * SMOOTHING
            smoothY = smoothY * (1f - SMOOTHING) + y * SMOOTHING

            onMotion?.invoke(smoothX, smoothY)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    fun start(sensitivity: MotionSensitivity) {
        if (rotationSensor == null) return
        baseAngles = null
        smoothX = 0f
        smoothY = 0f
        val rate = when (sensitivity) {
            MotionSensitivity.LOW    -> SensorManager.SENSOR_DELAY_GAME
            MotionSensitivity.MEDIUM -> SensorManager.SENSOR_DELAY_GAME
            MotionSensitivity.HIGH   -> SensorManager.SENSOR_DELAY_FASTEST
        }
        sensorManager.registerListener(listener, rotationSensor, rate)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        baseAngles = null
        smoothX = 0f
        smoothY = 0f
    }

    fun recalibrate() {
        baseAngles = null
        smoothX = 0f
        smoothY = 0f
    }

    private fun applyDeadZone(value: Float): Float {
        return if (abs(value) < DEAD_ZONE) 0f else value
    }

    companion object {
        fun sensitivityScale(s: MotionSensitivity): Float = when (s) {
            MotionSensitivity.LOW    -> 1.2f
            MotionSensitivity.MEDIUM -> 2.2f
            MotionSensitivity.HIGH   -> 3.5f
        }
    }
}
