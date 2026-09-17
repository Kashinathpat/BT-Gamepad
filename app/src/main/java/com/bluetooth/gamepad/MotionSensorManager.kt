package com.bluetooth.gamepad

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

enum class MotionMode { AIM, STEERING }

class MotionSensorManager(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    val isSupported: Boolean get() = gyroSensor != null

    fun isModeAvailable(mode: MotionMode): Boolean = when (mode) {
        MotionMode.AIM -> gyroSensor != null
        MotionMode.STEERING -> rotationSensor != null
    }

    // Invoked on the sensor thread.
    var onMotion: ((x: Float, y: Float) -> Unit)? = null

    // Tunables are written from the main thread (updateTuning) and read on the sensor thread.
    private var mode = MotionMode.AIM
    @Volatile private var degPerSecForMax = 90f
    @Volatile private var invertX = false
    @Volatile private var invertY = false

    private var biasX = 0f; private var biasY = 0f; private var biasZ = 0f
    private var biasInitialized = false

    private var gravX = 0f; private var gravY = 0f; private var gravZ = -1f
    private var gravInitialized = false

    private var accelAimX = 0f
    private var accelAimY = 0f
    private var accelAimZ = -9.81f

    private var posX = 0f
    private var posY = 0f

    private var lastSentX = 0f
    private var lastSentY = 0f
    private var wasActive = false

    private var steeringCenter: Float? = null

    private var lastGyroTsNs = 0L
    private var sensorThread: HandlerThread? = null

    private var cachedRotation = Surface.ROTATION_0
    private var lastRotationCheckMs = 0L

    companion object {
        private const val STILL_THRESHOLD = 0.03f
        private const val BIAS_SMOOTH = 0.02f
        private const val ACCEL_TRUST = 0.02f
        private const val YAW_RELAX = 1.41f
        private const val INTEGRATE_HALFLIFE_S = 0.15f
        // Full-rate rotation reaches full deflection in 1/INTEGRATION_GAIN seconds.
        private const val INTEGRATION_GAIN = 3f
        private const val SENSOR_PERIOD_US = 5000
        private const val EMIT_EPSILON = 0.004f
        private const val STEERING_RANGE_DEG = 32.0
        private const val ROTATION_POLL_MS = 250L
        const val SENS_MIN_DPS = 30f
        const val SENS_MAX_DPS = 180f
    }

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> handleAccel(event)
                Sensor.TYPE_GYROSCOPE -> if (mode == MotionMode.AIM) emit(aimValues(event))
                Sensor.TYPE_GAME_ROTATION_VECTOR ->
                    if (mode == MotionMode.STEERING) emit(steeringValues(event))
            }
        }
    }

    // Must use the same rotation table as aimValues, or the yaw/pitch split breaks.
    private fun handleAccel(event: SensorEvent) {
        val dx = event.values[0]
        val dy = event.values[1]
        val (ax, ay) = when (displayRotation()) {
            Surface.ROTATION_90 -> -dy to dx
            Surface.ROTATION_270 -> dy to -dx
            Surface.ROTATION_180 -> -dx to -dy
            else -> dx to dy
        }
        accelAimX = ax
        accelAimY = ay
        accelAimZ = event.values[2]
    }

    // wasActive guarantees exactly one final (0,0) once motion settles, so the stick is always released.
    private fun emit(raw: Pair<Float, Float>?) {
        if (raw == null) return
        var x = raw.first
        var y = raw.second
        if (invertX) x = -x
        if (invertY) y = -y

        val outX = x.coerceIn(-1f, 1f)
        val outY = y.coerceIn(-1f, 1f)

        val active = abs(outX) > EMIT_EPSILON || abs(outY) > EMIT_EPSILON
        if (!active && !wasActive) return
        if (active && abs(outX - lastSentX) < EMIT_EPSILON && abs(outY - lastSentY) < EMIT_EPSILON) return

        val sentX = if (active) outX else 0f
        val sentY = if (active) outY else 0f
        lastSentX = sentX
        lastSentY = sentY
        wasActive = active
        onMotion?.invoke(sentX, sentY)
    }

    private fun aimValues(event: SensorEvent): Pair<Float, Float>? {
        val ts = event.timestamp
        if (lastGyroTsNs == 0L) { lastGyroTsNs = ts; return null }
        var dt = (ts - lastGyroTsNs) / 1_000_000_000f
        lastGyroTsNs = ts
        if (dt <= 0f) return null
        if (dt > 0.05f) dt = 0.05f

        // Rotate gyro axes into the aim frame (x = pitch axis to the right, y = up the screen), so
        // flipping the phone end-for-end does not invert aiming.
        val dx = event.values[0]
        val dy = event.values[1]
        val (gx, gy) = when (displayRotation()) {
            Surface.ROTATION_90 -> -dy to dx
            Surface.ROTATION_270 -> dy to -dx
            Surface.ROTATION_180 -> -dx to -dy
            else -> dx to dy
        }
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

        // Recenter only while still, so a slow deliberate turn holds its deflection.
        if (isStill) {
            val decay = exp(-0.693f / INTEGRATE_HALFLIFE_S * dt)
            posX *= decay
            posY *= decay
        }

        var x = posX
        var y = posY
        val mag = sqrt(x * x + y * y)
        if (mag > 1f) { x /= mag; y /= mag }
        return x to y
    }

    private fun steeringValues(event: SensorEvent): Pair<Float, Float> {
        val matrix = FloatArray(9)
        val remapped = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val (axisX, axisY) = when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped)
        val roll = SensorManager.getOrientation(remapped, FloatArray(3))[2]
        val center = steeringCenter ?: roll.also { steeringCenter = it }
        var delta = roll - center
        while (delta > PI) delta -= (2 * PI).toFloat()
        while (delta < -PI) delta += (2 * PI).toFloat()
        val range = Math.toRadians(STEERING_RANGE_DEG).toFloat() * (degPerSecForMax / 90f)
        return (delta / range).coerceIn(-1f, 1f) to 0f
    }

    // Cached: two 200 Hz streams would hit DisplayManagerGlobal 400 times/s, and gyro and accel
    // must see the same rotation within a batch.
    private fun displayRotation(): Int {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastRotationCheckMs >= ROTATION_POLL_MS) {
            lastRotationCheckMs = now
            cachedRotation = readDisplayRotation()
        }
        return cachedRotation
    }

    @Suppress("DEPRECATION")
    private fun readDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.rotation
        }
    }

    // Rotate stored gravity by the inverse device rotation, then nudge toward accelerometer down.
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

    fun start(
        mode: MotionMode,
        sensitivityDps: Float,
        invertX: Boolean = false,
        invertY: Boolean = false
    ) {
        val sensor = when (mode) {
            MotionMode.AIM -> gyroSensor
            MotionMode.STEERING -> rotationSensor
        } ?: return
        // Do not clear onMotion here: callers set the callback before start().
        unregister()
        reset()
        this.mode = mode
        this.invertX = invertX
        this.invertY = invertY
        degPerSecForMax = sensitivityDps.coerceIn(SENS_MIN_DPS, SENS_MAX_DPS)

        val thread = HandlerThread("gyro-aim").apply { start() }
        sensorThread = thread
        val handler = Handler(thread.looper)
        sensorManager.registerListener(listener, sensor, SENSOR_PERIOD_US, handler)
        if (mode == MotionMode.AIM) {
            accelSensor?.let { sensorManager.registerListener(listener, it, SENSOR_PERIOD_US, handler) }
        }
    }

    // Retunes in place; restarting the sensor would rerun bias calibration on every slider frame.
    fun updateTuning(
        sensitivityDps: Float,
        invertX: Boolean,
        invertY: Boolean
    ) {
        degPerSecForMax = sensitivityDps.coerceIn(SENS_MIN_DPS, SENS_MAX_DPS)
        this.invertX = invertX
        this.invertY = invertY
    }

    fun recenter() {
        reset()
        onMotion?.invoke(0f, 0f)
    }

    fun stop() {
        unregister()
        onMotion = null
        reset()
    }

    private fun unregister() {
        sensorManager.unregisterListener(listener)
        sensorThread?.quitSafely()
        sensorThread = null
    }

    private fun reset() {
        biasInitialized = false
        gravInitialized = false
        lastGyroTsNs = 0L
        posX = 0f; posY = 0f
        lastSentX = 0f; lastSentY = 0f
        wasActive = false
        steeringCenter = null
        gravX = 0f; gravY = 0f; gravZ = -1f
        accelAimX = 0f; accelAimY = 0f; accelAimZ = -9.81f
    }
}
