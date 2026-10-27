package com.bluetooth.gamepad.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class GravityTrackerTest {

    private val dt = 0.005f

    private fun tracker(ax: Float = 0f, ay: Float = 0f, az: Float = -9.81f) =
        GravityTracker().also { it.update(0f, 0f, 0f, ax, ay, az, dt) }

    private fun GravityTracker.length() = sqrt(x * x + y * y + z * z)

    @Test
    fun theFirstReadingTakesTheDirectionOppositeTheAccelerometer() {
        val g = tracker(0f, 0f, -9.81f)
        assertEquals(0f, g.x, 1e-6f)
        assertEquals(0f, g.y, 1e-6f)
        assertEquals(1f, g.z, 1e-6f)
        val side = tracker(9.81f, 0f, 0f)
        assertEquals(-1f, side.x, 1e-6f)
    }

    @Test
    fun withoutAnyReadingGravityStartsPointingDown() {
        val g = GravityTracker()
        assertEquals(0f, g.x, 0f)
        assertEquals(0f, g.y, 0f)
        assertEquals(-1f, g.z, 0f)
    }

    @Test
    fun aZeroAccelerometerOnTheFirstReadingLeavesTheDefault() {
        val g = GravityTracker()
        g.update(0f, 0f, 0f, 0f, 0f, 0f, dt)
        assertEquals(-1f, g.z, 0f)
    }

    @Test
    fun theGyroTurnsGravityByTheAngleTurned() {
        val g = tracker()
        g.update(Math.PI.toFloat() / 2f, 0f, 0f, 0f, 0f, -20f, 1f)
        assertEquals(0f, g.x, 1e-4f)
        assertEquals(1f, g.y, 1e-4f)
        assertEquals(0f, g.z, 1e-4f)
    }

    @Test
    fun turningAboutTheGravityAxisLeavesItAlone() {
        val g = tracker()
        g.update(0f, 0f, 1f, 0f, 0f, -20f, 1f)
        assertEquals(0f, g.x, 1e-4f)
        assertEquals(0f, g.y, 1e-4f)
        assertEquals(1f, g.z, 1e-4f)
    }

    @Test
    fun theAccelerometerPullsGravityTowardItGently() {
        val g = tracker()
        g.update(0f, 0f, 0f, 0f, -9.81f, 0f, dt)
        assertEquals(0.0204f, g.y, 1e-3f)
        assertEquals(0.9998f, g.z, 1e-3f)
    }

    @Test
    fun anAccelerometerNearEarthGravityIsTrustedButAShovedOneIsNot() {
        val trusted = tracker()
        trusted.update(0f, 0f, 0f, 0f, -11.7f, 0f, dt)
        assertTrue(trusted.y > 0.02f)

        val shoved = tracker()
        shoved.update(0f, 0f, 0f, 0f, -11.9f, 0f, dt)
        assertEquals(0f, shoved.y, 0f)
        assertEquals(1f, shoved.z, 0f)
    }

    @Test
    fun keepsGravityAUnitVectorOverManyUpdates() {
        val g = tracker()
        for (i in 0 until 2000) {
            g.update(0.3f * (i % 7 - 3), 0.2f * (i % 5 - 2), 0.4f * (i % 3 - 1), 0.5f, 1f, -9.5f, dt)
            assertEquals(1f, g.length(), 1e-4f)
        }
    }

    @Test
    fun resetReturnsToTheDefaultAndTakesTheNextReadingAsNew() {
        val g = tracker(0f, 0f, -9.81f)
        g.update(1f, 0f, 0f, 0f, 0f, -9.81f, dt)
        g.reset()
        assertEquals(-1f, g.z, 0f)
        g.update(0f, 0f, 0f, 9.81f, 0f, 0f, dt)
        assertEquals(-1f, g.x, 1e-6f)
    }
}
