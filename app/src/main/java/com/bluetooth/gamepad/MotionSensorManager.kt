package com.bluetooth.gamepad

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

enum class MotionSensitivity { LOW, MEDIUM, HIGH }

class MotionSensorManager(private val context: Context) {

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

    private var smoothX = 0f
    private var smoothY = 0f
    private val SMOOTHING = 0.35f

    private val DEAD_ZONE = 0.04f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

            // Remap axes based on actual display rotation so landscape-left and
            // landscape-right both produce correct tilt directions.
            val (axisX, axisY) = when (displayRotation()) {
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
                else                 -> SensorManager.AXIS_X        to SensorManager.AXIS_Z
            }

            SensorManager.remapCoordinateSystem(rotMatrix, axisX, axisY, remappedMatrix)
            SensorManager.getOrientation(remappedMatrix, orientation)

            // orientation[1] = pitch (tilt forward/back) -> stick Y
            // orientation[2] = roll  (tilt left/right)   -> stick X
            val rawPitch = orientation[1]
            val rawRoll  = orientation[2]

            val base = baseAngles
            if (base == null) {
                baseAngles = floatArrayOf(rawRoll, rawPitch)
                return
            }

            // Wrap angle differences to [-PI, PI] to avoid gimbal discontinuity jumps.
            val dx = Math.IEEEremainder((rawRoll  - base[0]).toDouble(), 2.0 * Math.PI).toFloat()
            val dy = Math.IEEEremainder((rawPitch - base[1]).toDouble(), 2.0 * Math.PI).toFloat()

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
        onMotion = null
        baseAngles = null
        smoothX = 0f
        smoothY = 0f
    }

    private fun applyDeadZone(value: Float): Float {
        return if (abs(value) < DEAD_ZONE) 0f else value
    }

    // Context.getDisplay() is API 30+; fall back to the window manager's display below that.
    private fun displayRotation(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_90
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.defaultDisplay?.rotation ?: Surface.ROTATION_90
        }
    } catch (_: Exception) {
        Surface.ROTATION_90
    }

    companion object {
        fun sensitivityScale(s: MotionSensitivity): Float = when (s) {
            MotionSensitivity.LOW    -> 1.2f
            MotionSensitivity.MEDIUM -> 2.2f
            MotionSensitivity.HIGH   -> 3.5f
        }
    }
}
