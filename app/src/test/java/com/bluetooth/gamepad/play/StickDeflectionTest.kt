package com.bluetooth.gamepad.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickDeflectionTest {

    private fun check(d: StickDeflection, offsetX: Float, offsetY: Float, x: Float, y: Float) {
        assertEquals(offsetX, d.offsetX, 1e-4f)
        assertEquals(offsetY, d.offsetY, 1e-4f)
        assertEquals(x, d.x, 1e-4f)
        assertEquals(y, d.y, 1e-4f)
    }

    @Test
    fun aFingerInsideTheRimMovesTheKnobWithIt() {
        check(stickDeflection(3f, 4f, 10f), 3f, 4f, 0.3f, 0.4f)
    }

    @Test
    fun aFingerOnTheRimIsNotClamped() {
        check(stickDeflection(6f, 8f, 10f), 6f, 8f, 0.6f, 0.8f)
    }

    @Test
    fun aFingerOutsideTheRimPinsToItKeepingTheDirection() {
        check(stickDeflection(30f, 40f, 10f), 6f, 8f, 0.6f, 0.8f)
        check(stickDeflection(-30f, -40f, 10f), -6f, -8f, -0.6f, -0.8f)
        check(stickDeflection(-30f, 40f, 10f), -6f, 8f, -0.6f, 0.8f)
    }

    @Test
    fun aFingerJustOutsideTheRimIsAlreadyPinned() {
        check(stickDeflection(6.6f, 8.8f, 10f), 6f, 8f, 0.6f, 0.8f)
        check(stickDeflection(9f, 12f, 10f), 6f, 8f, 0.6f, 0.8f)
        check(stickDeflection(0f, 10.5f, 10f), 0f, 10f, 0f, 1f)
    }

    @Test
    fun aFingerAtTheCentreIsNeutral() {
        check(stickDeflection(0f, 0f, 10f), 0f, 0f, 0f, 0f)
    }

    @Test
    fun aFarFingerNeverGivesMoreThanFullDeflection() {
        for (angle in 0 until 360 step 15) {
            val rad = Math.toRadians(angle.toDouble())
            val d = stickDeflection((1000 * Math.cos(rad)).toFloat(), (1000 * Math.sin(rad)).toFloat(), 84f)
            assertEquals(1f, d.x * d.x + d.y * d.y, 1e-3f)
            assertTrue(d.offsetX * d.offsetX + d.offsetY * d.offsetY <= 84f * 84f + 0.01f)
        }
    }

    @Test
    fun aStickWithNoTravelStaysNeutral() {
        check(stickDeflection(5f, 5f, 0f), 0f, 0f, 0f, 0f)
        check(stickDeflection(0f, 0f, 0f), 0f, 0f, 0f, 0f)
    }

    @Test
    fun anAxisAlignedFingerGivesAnAxisAlignedStick() {
        check(stickDeflection(5f, 0f, 10f), 5f, 0f, 0.5f, 0f)
        check(stickDeflection(0f, -50f, 10f), 0f, -10f, 0f, -1f)
    }
}
