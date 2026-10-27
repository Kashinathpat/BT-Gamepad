package com.bluetooth.gamepad.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class GyroAimTest {

    private val dt = 0.005f
    private val maxRad = (Math.PI / 2).toFloat()

    private fun GyroAim.feed(gx: Float, gy: Float, gz: Float, steps: Int, rad: Float = maxRad): Pair<Float, Float> {
        var out = 0f to 0f
        repeat(steps) { out = step(gx, gy, gz, 0f, 0f, -9.81f, dt, rad) }
        return out
    }

    private fun Pair<Float, Float>.magnitude() = sqrt(first * first + second * second)

    @Test
    fun aPhoneThatIsStillGivesNoOutput() {
        val aim = GyroAim()
        repeat(500) {
            assertEquals(0f to 0f, aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad))
        }
    }

    @Test
    fun aSteadySensorOffsetIsLearnedAwayFromTheFirstSample() {
        val aim = GyroAim()
        repeat(300) {
            assertEquals(0f to 0f, aim.step(0.004f, 0.003f, 0.002f, 0f, 0f, -9.81f, dt, maxRad))
        }
    }

    @Test
    fun aTurnAtTheSensitivitySpeedGivesFullStick() {
        val aim = GyroAim()
        val out = aim.feed(maxRad, 0f, 0f, 100)
        assertEquals(0f, out.first, 1e-5f)
        assertEquals(1f, out.second, 1e-3f)
    }

    @Test
    fun turningTheOtherWayReversesTheStick() {
        val up = GyroAim().feed(maxRad / 2, 0f, 0f, 100)
        val down = GyroAim().feed(-maxRad / 2, 0f, 0f, 100)
        assertTrue(up.second > 0.3f)
        assertEquals(-up.second, down.second, 1e-3f)
    }

    @Test
    fun turningAboutTheVerticalAxisMovesTheStickSidewaysInOppositeWays() {
        val positive = GyroAim().feed(0f, 0f, 0.8f, 100)
        val negative = GyroAim().feed(0f, 0f, -0.8f, 100)
        assertTrue(positive.first < -0.3f)
        assertTrue(negative.first > 0.3f)
        assertEquals(0f, positive.second, 1e-4f)
        assertEquals(-positive.first, negative.first, 1e-3f)
    }

    @Test
    fun aDiagonalTurnKeepsItsDirection() {
        val out = GyroAim().feed(1f, 0f, -1f, 1)
        assertTrue(out.first > 0.3f)
        assertEquals(out.first, out.second, 1e-4f)
    }

    @Test
    fun aFasterTurnNeverGivesMoreThanFullStick() {
        for (speed in listOf(2f, 5f, 20f)) {
            val out = GyroAim().feed(speed, 0f, -speed, 50)
            assertTrue(out.magnitude() <= 1.0001f)
        }
    }

    @Test
    fun aHigherSensitivitySettingNeedsAFasterTurn() {
        val sensitive = GyroAim().feed(0.5f, 0f, 0f, 100, rad = 1f).magnitude()
        val relaxed = GyroAim().feed(0.5f, 0f, 0f, 100, rad = 2f).magnitude()
        assertTrue(sensitive > relaxed)
    }

    @Test
    fun aVerySlowDriftIsReleasedToExactlyZero() {
        val aim = GyroAim()
        aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad)
        repeat(10) {
            assertEquals(0f to 0f, aim.step(0.003f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad))
        }
    }

    @Test
    fun aSlowRealTurnJumpsPastTheGamesDeadZone() {
        val aim = GyroAim()
        aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad)
        val out = aim.feed(0.03f, 0f, 0f, 40)
        assertTrue(out.magnitude() >= 0.09f)
        assertTrue(out.magnitude() < 0.2f)
        assertEquals(0f, out.first, 1e-5f)
        assertTrue(out.second > 0f)
    }

    @Test
    fun recenteringDropsAnyAimStillBeingSmoothed() {
        val aim = GyroAim()
        aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad)
        assertTrue(aim.feed(0.03f, 0f, 0f, 40).magnitude() > 0.09f)
        aim.recenter()
        assertEquals(0f to 0f, aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad))
    }

    @Test
    fun withoutRecenteringTheSmoothedAimFadesOutGradually() {
        val aim = GyroAim()
        aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad)
        aim.feed(0.03f, 0f, 0f, 40)
        assertTrue(aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad).magnitude() > 0f)
    }

    @Test
    fun resetForgetsTheLearnedSensorOffset() {
        val aim = GyroAim()
        assertEquals(0f to 0f, aim.step(0.02f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad))
        aim.reset()
        aim.step(0f, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad)
        assertTrue(aim.feed(0.02f, 0f, 0f, 40).magnitude() > 0f)
    }

    @Test
    fun aFastTurnIsNotSmoothedAndRespondsOnTheFirstSample() {
        val out = GyroAim().step(maxRad, 0f, 0f, 0f, 0f, -9.81f, dt, maxRad)
        assertTrue(abs(out.second) > 0.99f)
    }
}
