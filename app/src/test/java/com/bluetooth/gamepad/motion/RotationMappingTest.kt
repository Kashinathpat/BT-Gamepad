package com.bluetooth.gamepad.motion

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class RotationMappingTest {

    @Test
    fun portraitKeepsTheAxes() {
        assertEquals(3f to 5f, rotateToAimFrame(Surface.ROTATION_0, 3f, 5f))
    }

    @Test
    fun quarterTurnSwapsAndFlipsTheAxes() {
        assertEquals(-5f to 3f, rotateToAimFrame(Surface.ROTATION_90, 3f, 5f))
        assertEquals(5f to -3f, rotateToAimFrame(Surface.ROTATION_270, 3f, 5f))
    }

    @Test
    fun halfTurnFlipsBothAxes() {
        assertEquals(-3f to -5f, rotateToAimFrame(Surface.ROTATION_180, 3f, 5f))
    }

    @Test
    fun anUnknownRotationBehavesLikePortrait() {
        assertEquals(3f to 5f, rotateToAimFrame(7, 3f, 5f))
        assertEquals(3f to 5f, rotateToAimFrame(-1, 3f, 5f))
    }

    @Test
    fun fourQuarterTurnsReturnToTheStart() {
        var v = 3f to 5f
        repeat(4) { v = rotateToAimFrame(Surface.ROTATION_90, v.first, v.second) }
        assertEquals(3f to 5f, v)
    }

    @Test
    fun aQuarterTurnThenItsInverseCancels() {
        val (x, y) = rotateToAimFrame(Surface.ROTATION_90, 3f, 5f)
        assertEquals(3f to 5f, rotateToAimFrame(Surface.ROTATION_270, x, y))
    }

    @Test
    fun twoQuarterTurnsEqualAHalfTurn() {
        val (x, y) = rotateToAimFrame(Surface.ROTATION_90, 3f, 5f)
        assertEquals(rotateToAimFrame(Surface.ROTATION_180, 3f, 5f), rotateToAimFrame(Surface.ROTATION_90, x, y))
    }

    @Test
    fun theTurnSpeedIsNeverChanged() {
        for (rotation in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            val (x, y) = rotateToAimFrame(rotation, 3f, 4f)
            assertEquals(25f, x * x + y * y, 1e-6f)
        }
    }
}
