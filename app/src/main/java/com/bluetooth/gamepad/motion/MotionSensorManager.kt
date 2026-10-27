package com.bluetooth.gamepad.motion

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

// AIM maps the current rotation speed to the stick; STEERING maps tilt to the X axis.
enum class MotionMode { AIM, STEERING }

// Rotates a sensor x/y pair into the aim frame (x = pitch axis to the right, y = up the screen) for the
// display rotation, so flipping the phone end-for-end does not invert aiming. Gyro and accelerometer
// must share it or the yaw/pitch split breaks.
internal fun rotateToAimFrame(rotation: Int, x: Float, y: Float): Pair<Float, Float> = when (rotation) {
    Surface.ROTATION_90 -> -y to x
    Surface.ROTATION_270 -> y to -x
    Surface.ROTATION_180 -> -x to -y
    else -> x to y
}

class MotionSensorManager(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    fun isModeAvailable(mode: MotionMode): Boolean = when (mode) {
        MotionMode.AIM -> gyroSensor != null
        MotionMode.STEERING -> rotationSensor != null
    }

    // Invoked on the sensor thread.
    @Volatile var onMotion: ((x: Float, y: Float) -> Unit)? = null

    // Tunables are written from the main thread (updateTuning) and read on the sensor thread.
    private var mode = MotionMode.AIM
    private val gyroAim = GyroAim()
    @Volatile private var degPerSecForMax = 90f
    @Volatile private var invertX = false
    @Volatile private var invertY = false

    private var accelAimX = 0f
    private var accelAimY = 0f
    private var accelAimZ = -9.81f

    private var lastSentX = 0f
    private var lastSentY = 0f
    private var wasActive = false

    private var steeringCenter: Float? = null
    // Set from the main thread, applied on the sensor thread so it never races a sample in progress.
    @Volatile private var recenterRequested = false

    private var lastGyroTsNs = 0L
    private var sensorThread: HandlerThread? = null

    private var cachedRotation = Surface.ROTATION_0
    private var lastRotationCheckMs = 0L

    companion object {
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
            if (recenterRequested) {
                recenterRequested = false
                lastSentX = 0f; lastSentY = 0f
                wasActive = false
                steeringCenter = null
                gyroAim.recenter()
                // Emitted here so it lands after any sample computed before the request.
                onMotion?.invoke(0f, 0f)
            }
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> handleAccel(event)
                Sensor.TYPE_GYROSCOPE -> if (mode == MotionMode.AIM) emit(aimValues(event))
                Sensor.TYPE_GAME_ROTATION_VECTOR ->
                    if (mode == MotionMode.STEERING) emit(steeringValues(event))
            }
        }
    }

    private fun handleAccel(event: SensorEvent) {
        val (ax, ay) = rotateToAimFrame(displayRotation(), event.values[0], event.values[1])
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

        val (gx, gy) = rotateToAimFrame(displayRotation(), event.values[0], event.values[1])
        val gz = event.values[2]

        val maxRad = (degPerSecForMax * PI / 180.0).toFloat()
        return gyroAim.step(gx, gy, gz, accelAimX, accelAimY, accelAimZ, dt, maxRad)
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

    // Re-zeroes aim and the steering centre; gyro bias and gravity calibration are kept.
    fun recenter() {
        recenterRequested = true
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
        lastGyroTsNs = 0L
        gyroAim.reset()
        lastSentX = 0f; lastSentY = 0f
        wasActive = false
        steeringCenter = null
        accelAimX = 0f; accelAimY = 0f; accelAimZ = -9.81f
    }
}
