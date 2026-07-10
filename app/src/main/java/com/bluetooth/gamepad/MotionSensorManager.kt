package com.bluetooth.gamepad

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gyro-to-stick: rotation accumulates into a held stick position (like PUBG Mobile's gyro aim),
 * and recenters only while the phone is held still. Unlike a pure rate mapping, a slow deliberate
 * turn keeps the stick deflected instead of springing back the instant you slow down.
 */
class MotionSensorManager(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isSupported: Boolean get() = gyroSensor != null

    /** Emits stick x,y in [-1,1] on a background thread. */
    var onMotion: ((x: Float, y: Float) -> Unit)? = null

    private var degPerSecForMax = 90f

    private var biasX = 0f; private var biasY = 0f; private var biasZ = 0f
    private var biasInitialized = false

    private var gravX = 0f; private var gravY = 0f; private var gravZ = -1f
    private var gravInitialized = false

    private var accelAimX = 0f
    private var accelAimY = 0f
    private var accelAimZ = -9.81f

    private var posX = 0f
    private var posY = 0f

    private var lastGyroTsNs = 0L
    private var sensorThread: HandlerThread? = null

    companion object {
        private const val STILL_THRESHOLD = 0.03f
        private const val BIAS_SMOOTH = 0.02f
        private const val ACCEL_TRUST = 0.02f
        private const val YAW_RELAX = 1.41f
        private const val INTEGRATE_HALFLIFE_S = 0.15f
        // Full-rate rotation reaches full deflection in 1/INTEGRATION_GAIN seconds.
        private const val INTEGRATION_GAIN = 3f
        private const val SENSOR_PERIOD_US = 5000
        const val SENS_MIN_DPS = 30f
        const val SENS_MAX_DPS = 180f
    }

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> handleAccel(event)
                Sensor.TYPE_GYROSCOPE    -> handleGyro(event)
            }
        }
    }

    // Landscape aim frame (top edge to the player's left): aim = (-devY, devX, devZ).
    private fun handleAccel(event: SensorEvent) {
        accelAimX = -event.values[1]
        accelAimY = event.values[0]
        accelAimZ = event.values[2]
    }

    private fun handleGyro(event: SensorEvent) {
        val ts = event.timestamp
        if (lastGyroTsNs == 0L) { lastGyroTsNs = ts; return }
        var dt = (ts - lastGyroTsNs) / 1_000_000_000f
        lastGyroTsNs = ts
        if (dt <= 0f) return
        if (dt > 0.05f) dt = 0.05f

        val gx = -event.values[1]
        val gy = event.values[0]
        val gz = event.values[2]

        val speed3 = sqrt(gx * gx + gy * gy + gz * gz)
        val isStill = speed3 < STILL_THRESHOLD
        if (!biasInitialized) {
            biasX = gx; biasY = gy; biasZ = gz; biasInitialized = true
        } else if (isStill) {
            biasX += (gx - biasX) * BIAS_SMOOTH
            biasY += (gy - biasY) * BIAS_SMOOTH
            biasZ += (gz - biasZ) * BIAS_SMOOTH
        }
        val cgx = gx - biasX
        val cgy = gy - biasY
        val cgz = gz - biasZ

        updateGravity(cgx, cgy, cgz, dt)

        // Player-space rates: pitch stays local, yaw takes its sign from rotation about gravity so
        // the hold angle (flat vs upright) does not matter and gravity error cannot leak in.
        val worldYaw = cgy * gravY + cgz * gravZ
        val yawMag = sqrt(cgy * cgy + cgz * cgz)
        val yawRate = -sign(worldYaw) * min(abs(worldYaw) * YAW_RELAX, yawMag)
        val pitchRate = cgx

        val maxRad = (degPerSecForMax * PI / 180.0).toFloat()
        posX = (posX + yawRate / maxRad * dt * INTEGRATION_GAIN).coerceIn(-1f, 1f)
        posY = (posY + pitchRate / maxRad * dt * INTEGRATION_GAIN).coerceIn(-1f, 1f)

        // Recenter only while the phone is held still, so a slow deliberate turn holds its
        // deflection instead of springing back the instant rotation slows down.
        if (isStill) {
            val decay = exp(-0.693f / INTEGRATE_HALFLIFE_S * dt)
            posX *= decay
            posY *= decay
        }

        var x = posX
        var y = posY
        val mag = sqrt(x * x + y * y)
        if (mag > 1f) { x /= mag; y /= mag }

        onMotion?.invoke(x, y)
    }

    // Complementary filter: rotate the stored gravity by the inverse of the device's rotation over
    // dt (Rodrigues), then nudge it toward the accelerometer's "down" and renormalize.
    private fun updateGravity(wx: Float, wy: Float, wz: Float, dt: Float) {
        val ax = accelAimX; val ay = accelAimY; val az = accelAimZ
        val al = sqrt(ax * ax + ay * ay + az * az)
        if (!gravInitialized) {
            if (al > 1e-6f) { gravX = -ax / al; gravY = -ay / al; gravZ = -az / al }
            gravInitialized = true
            return
        }
        val w = sqrt(wx * wx + wy * wy + wz * wz)
        val angle = w * dt
        if (angle > 1e-7f) {
            val kx = -wx / w; val ky = -wy / w; val kz = -wz / w
            val c = cos(angle); val s = sin(angle)
            val dot = kx * gravX + ky * gravY + kz * gravZ
            val crX = ky * gravZ - kz * gravY
            val crY = kz * gravX - kx * gravZ
            val crZ = kx * gravY - ky * gravX
            gravX = gravX * c + crX * s + kx * dot * (1f - c)
            gravY = gravY * c + crY * s + ky * dot * (1f - c)
            gravZ = gravZ * c + crZ * s + kz * dot * (1f - c)
        }
        if (al > 1e-6f) {
            gravX += (-ax / al - gravX) * ACCEL_TRUST
            gravY += (-ay / al - gravY) * ACCEL_TRUST
            gravZ += (-az / al - gravZ) * ACCEL_TRUST
        }
        val gl = sqrt(gravX * gravX + gravY * gravY + gravZ * gravZ)
        if (gl > 1e-6f) { gravX /= gl; gravY /= gl; gravZ /= gl }
    }

    /** sensitivityDps: degrees/second of rotation that yields full stick deflection. */
    fun start(sensitivityDps: Float) {
        if (gyroSensor == null) return
        reset()
        degPerSecForMax = sensitivityDps.coerceIn(SENS_MIN_DPS, SENS_MAX_DPS)
        val thread = HandlerThread("gyro-aim").apply { start() }
        sensorThread = thread
        val handler = Handler(thread.looper)
        sensorManager.registerListener(listener, gyroSensor, SENSOR_PERIOD_US, handler)
        accelSensor?.let { sensorManager.registerListener(listener, it, SENSOR_PERIOD_US, handler) }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        sensorThread?.quitSafely()
        sensorThread = null
        onMotion = null
        reset()
    }

    private fun reset() {
        biasInitialized = false
        gravInitialized = false
        lastGyroTsNs = 0L
        posX = 0f; posY = 0f
        gravX = 0f; gravY = 0f; gravZ = -1f
        accelAimX = 0f; accelAimY = 0f; accelAimZ = -9.81f
    }
}
