package com.bluetooth.gamepad.play

import android.os.Handler
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import com.bluetooth.gamepad.bluetooth.BluetoothHidGamepad
import com.bluetooth.gamepad.layout.ButtonBehavior
import com.bluetooth.gamepad.layout.ButtonConfig
import com.bluetooth.gamepad.layout.ControllerLayout
import com.bluetooth.gamepad.layout.baseId
import com.bluetooth.gamepad.layout.isPressable
import com.bluetooth.gamepad.layout.isStick
import kotlin.math.abs
import kotlin.math.sqrt

internal const val FLOATING_STICK_FRAC = 0.24f

// A single full-screen pointer dispatcher reads every pointer and routes each, by PointerId, to the
// control whose region it first landed on. The control keeps the pointer until that finger lifts, so
// any number of controls can be held at once. Controls are pure visuals driven by the snapshot state
// the dispatcher owns.
internal class RuntimeControl(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val onDown: (localX: Float, localY: Float) -> Unit,
    val onMove: (localX: Float, localY: Float) -> Unit,
    val onUp: () -> Unit,
    // Full release, including latched toggles and running turbo; used when the input surface restarts.
    val onReset: () -> Unit = onUp,
    // Claimed only by a real touch-down, never a finger sliding in or left over from another control.
    val freshDownOnly: Boolean = false
) {
    var pointerId: PointerId? = null
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
    val area get() = (right - left) * (bottom - top)
}

// Press count per HID button, so a button shared by several controls is released by the last holder only.
internal class ButtonMixer(private val gamepad: BluetoothHidGamepad?) {
    private val counts = IntArray(16)

    fun press(index: Int) {
        if (counts[index]++ == 0) gamepad?.setButtonState(index, true)
    }

    fun release(index: Int) {
        if (counts[index] == 0) return
        if (--counts[index] == 0) gamepad?.setButtonState(index, false)
    }

    fun clear() = counts.fill(0)
}

internal class ControlEnv(
    val w: Float,
    val h: Float,
    val dim: Float,
    val gamepad: BluetoothHidGamepad?,
    val isWindowsMode: Boolean,
    val mixer: ButtonMixer,
    val handler: Handler,
    val haptic: () -> Unit,
    val onGateHold: (Boolean) -> Unit,
    val pressedButtons: SnapshotStateMap<String, Boolean>,
    val stickOffsets: SnapshotStateMap<String, Offset>,
    val stickOrigins: SnapshotStateMap<String, Offset>,
    val dpadDirs: SnapshotStateMap<String, DpadState>
) {
    // Sticks currently held, by control id, so a duplicate stick is not centred while another holds it.
    val stickHolds = LinkedHashMap<String, StickHold>()
}

internal class StickHold(val isRight: Boolean, val x: Float, val y: Float)

// Shoulders honour the Windows/standard swap (LB<->LT, RB<->RT).
private fun hidIndex(baseId: String, isWindowsMode: Boolean): Int? = when (baseId) {
    "A" -> BluetoothHidGamepad.BUTTON_A
    "B" -> BluetoothHidGamepad.BUTTON_B
    "X" -> BluetoothHidGamepad.BUTTON_X
    "Y" -> BluetoothHidGamepad.BUTTON_Y
    "LB" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_LB else BluetoothHidGamepad.BUTTON_LT
    "RB" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_RB else BluetoothHidGamepad.BUTTON_RT
    "LT" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_LT else BluetoothHidGamepad.BUTTON_LB
    "RT" -> if (isWindowsMode) BluetoothHidGamepad.BUTTON_RT else BluetoothHidGamepad.BUTTON_RB
    "LSB" -> BluetoothHidGamepad.BUTTON_L3
    "RSB" -> BluetoothHidGamepad.BUTTON_R3
    "SELECT" -> BluetoothHidGamepad.BUTTON_SELECT
    "START" -> BluetoothHidGamepad.BUTTON_START
    else -> null
}

internal fun buildControls(env: ControlEnv, layout: ControllerLayout): List<RuntimeControl> {
    val list = ArrayList<RuntimeControl>(layout.buttons.size)
    layout.buttons.forEach { btn ->
        val btnPx = btn.sizeFrac * env.dim
        val cx = btn.xFrac * env.w
        val cy = btn.yFrac * env.h
        val left = cx - btnPx / 2f
        val top = cy - btnPx / 2f
        val rect = floatArrayOf(left, top, cx + btnPx / 2f, cy + btnPx / 2f)
        val control = when {
            btn.isStick && btn.floating -> floatingStickControl(btn, rect, env)
            btn.isStick -> stickControl(btn, rect, btnPx, env)
            btn.baseId == "DPAD" -> dpadControl(btn, rect, btnPx, env)
            btn.baseId == "TPADD" -> touchDpadControl(btn, rect, btnPx, env)
            btn.baseId == "TPADL" || btn.baseId == "TPADR" -> touchStickControl(btn, rect, btnPx, env)
            btn.isPressable -> pressControl(btn, rect, env)
            else -> null
        }
        control?.let { list.add(it) }
    }
    // Hit-testing picks the first match, so smaller controls go first: a small button overlapping a
    // stick/d-pad claims the touch, not the larger control beneath it.
    list.sortBy { it.area }
    return list
}

private fun holdStick(env: ControlEnv, id: String, isRight: Boolean, x: Float, y: Float) {
    val hold = StickHold(isRight, x, y)
    env.stickHolds[id] = hold
    sendHeldStick(env, isRight, hold)
}

private fun releaseStick(env: ControlEnv, id: String, isRight: Boolean) {
    env.stickHolds.remove(id)
    sendHeldStick(env, isRight, null)
}

// A (near-)centred copy never overrides another copy of the same stick that is still deflected.
private fun sendHeldStick(env: ControlEnv, isRight: Boolean, latest: StickHold?) {
    fun StickHold.deflected() = abs(x) > 0.1f || abs(y) > 0.1f
    val sameStick = env.stickHolds.values.filter { it.isRight == isRight }
    val active = latest?.takeIf { it.deflected() }
        ?: sameStick.lastOrNull { it.deflected() }
        ?: latest
        ?: sameStick.lastOrNull()
    sendStick(env, isRight, active?.x ?: 0f, active?.y ?: 0f)
}

private fun sendStick(env: ControlEnv, isRight: Boolean, x: Float, y: Float) {
    if (isRight) env.gamepad?.setRightStickTouch(x, y) else env.gamepad?.setLeftStick(x, y)
}

private fun stickControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    val isRight = btn.baseId == "RSTICK"
    val radius = btnPx / 2f
    val maxOffset = (btnPx - btnPx * 0.4f) / 2f  // knob is 0.4 of base
    fun apply(localX: Float, localY: Float) {
        // Absolute position from centre, vector clamped to the rim: a finger outside the circle pins
        // to the rim and stays there until it crosses back, rather than jumping to the opposite side.
        var ox = localX - radius
        var oy = localY - radius
        val dist = sqrt(ox * ox + oy * oy)
        if (dist > maxOffset && dist > 0f) {
            ox = ox / dist * maxOffset
            oy = oy / dist * maxOffset
        }
        env.stickOffsets[btn.id] = Offset(ox, oy)
        val nx = if (maxOffset > 0f) (ox / maxOffset).coerceIn(-1f, 1f) else 0f
        val ny = if (maxOffset > 0f) (oy / maxOffset).coerceIn(-1f, 1f) else 0f
        holdStick(env, btn.id, isRight, nx, ny)
    }
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly -> apply(lx, ly) },
        onMove = { lx, ly -> apply(lx, ly) },
        onUp = {
            env.stickOffsets[btn.id] = Offset.Zero
            releaseStick(env, btn.id, isRight)
        }
    )
}

private fun floatingStickControl(btn: ButtonConfig, r: FloatArray, env: ControlEnv): RuntimeControl {
    val isRight = btn.baseId == "RSTICK"
    val stickPx = FLOATING_STICK_FRAC * env.dim
    val maxOffset = stickPx * 0.3f
    var originX = 0f
    var originY = 0f
    fun apply(localX: Float, localY: Float) {
        var ox = localX - originX
        var oy = localY - originY
        val dist = sqrt(ox * ox + oy * oy)
        if (dist > maxOffset && dist > 0f) {
            ox = ox / dist * maxOffset
            oy = oy / dist * maxOffset
        }
        env.stickOffsets[btn.id] = Offset(ox, oy)
        val nx = if (maxOffset > 0f) (ox / maxOffset).coerceIn(-1f, 1f) else 0f
        val ny = if (maxOffset > 0f) (oy / maxOffset).coerceIn(-1f, 1f) else 0f
        holdStick(env, btn.id, isRight, nx, ny)
    }
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly ->
            originX = lx
            originY = ly
            env.stickOrigins[btn.id] = Offset(lx, ly)
            apply(lx, ly)
        },
        onMove = { lx, ly -> apply(lx, ly) },
        onUp = {
            env.stickOrigins.remove(btn.id)
            env.stickOffsets[btn.id] = Offset.Zero
            releaseStick(env, btn.id, isRight)
        }
    )
}

private fun dpadDirection(dx: Float, dy: Float, dead: Float): DpadState = DpadState(
    h = when { dx > dead -> DpadDir.RIGHT; dx < -dead -> DpadDir.LEFT; else -> null },
    v = when { dy < -dead -> DpadDir.UP; dy > dead -> DpadDir.DOWN; else -> null }
)

private fun applyDpad(btn: ButtonConfig, next: DpadState, env: ControlEnv) {
    val prev = env.dpadDirs[btn.id]
    if (prev != null && prev.h == next.h && prev.v == next.v) return
    val isNewPress = (next.h != null && next.h != prev?.h) || (next.v != null && next.v != prev?.v)
    env.dpadDirs[btn.id] = next
    sendEffectiveDpad(next, env)
    if (isNewPress) env.haptic()
}

private fun releaseDpad(btn: ButtonConfig, env: ControlEnv) {
    val neutral = DpadState(null, null)
    env.dpadDirs[btn.id] = neutral
    sendEffectiveDpad(neutral, env)
}

// D-pad controls share one hat: each axis comes from whichever control is holding it.
private fun sendEffectiveDpad(state: DpadState, env: ControlEnv) {
    val held = env.dpadDirs.values
    val h = state.h ?: held.firstNotNullOfOrNull { it.h }
    val v = state.v ?: held.firstNotNullOfOrNull { it.v }
    sendDpad(env.gamepad, env.isWindowsMode, h, v)
}

private fun dpadControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    val radius = btnPx / 2f
    val dead = radius * 0.25f
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly -> applyDpad(btn, dpadDirection(lx - radius, ly - radius, dead), env) },
        onMove = { lx, ly -> applyDpad(btn, dpadDirection(lx - radius, ly - radius, dead), env) },
        onUp = { releaseDpad(btn, env) }
    )
}

// Touchpad in d-pad mode: 8-way direction from where the finger first lands, so the initial touch
// sets the origin and dragging away from it picks a direction.
private fun touchDpadControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    var originX = 0f
    var originY = 0f
    val dead = btnPx * 0.12f
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly ->
            originX = lx
            originY = ly
            applyDpad(btn, dpadDirection(0f, 0f, dead), env)
        },
        onMove = { lx, ly -> applyDpad(btn, dpadDirection(lx - originX, ly - originY, dead), env) },
        onUp = { releaseDpad(btn, env) }
    )
}

// Touchpad in stick mode (trackpad style): the stick value is the offset from where the finger first
// landed, so the same drag gives the same deflection wherever it started.
private fun touchStickControl(btn: ButtonConfig, r: FloatArray, btnPx: Float, env: ControlEnv): RuntimeControl {
    val isRight = btn.baseId == "TPADR"
    // Full deflection when dragged ~40% of the pad width from the origin.
    val range = btnPx * 0.4f
    var originX = 0f
    var originY = 0f
    fun apply(localX: Float, localY: Float) {
        val nx = if (range > 0f) ((localX - originX) / range).coerceIn(-1f, 1f) else 0f
        val ny = if (range > 0f) ((localY - originY) / range).coerceIn(-1f, 1f) else 0f
        holdStick(env, btn.id, isRight, nx, ny)
    }
    return RuntimeControl(
        r[0], r[1], r[2], r[3],
        onDown = { lx, ly -> originX = lx; originY = ly; apply(lx, ly) },
        onMove = { lx, ly -> apply(lx, ly) },
        onUp = { releaseStick(env, btn.id, isRight) }
    )
}

// "active" is held or latched by the user; "engaged" is the HID state, which turbo flips while active.
private fun pressControl(btn: ButtonConfig, r: FloatArray, env: ControlEnv): RuntimeControl {
    val indices = (if (btn.baseId == "MACRO") btn.macro else listOf(btn.baseId))
        .mapNotNull { hidIndex(it, env.isWindowsMode) }
    val halfPeriodMs = 500L / btn.turboHz
    var engaged = false
    var active = false

    fun engage(on: Boolean) {
        if (on == engaged) return
        engaged = on
        indices.forEach { if (on) env.mixer.press(it) else env.mixer.release(it) }
    }

    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        env.pressedButtons[btn.id] = on
        if (btn.gyroGate) env.onGateHold(on)
    }

    val pulse = object : Runnable {
        override fun run() {
            engage(!engaged)
            env.handler.postDelayed(this, halfPeriodMs)
        }
    }

    fun release() {
        env.handler.removeCallbacks(pulse)
        engage(false)
        setActive(false)
    }

    fun hold() {
        setActive(true)
        engage(true)
        env.haptic()
    }

    return when (btn.behavior) {
        ButtonBehavior.NORMAL -> RuntimeControl(
            r[0], r[1], r[2], r[3],
            onDown = { _, _ -> hold() },
            onMove = { _, _ -> },
            onUp = { release() }
        )
        ButtonBehavior.TOGGLE -> RuntimeControl(
            r[0], r[1], r[2], r[3],
            onDown = { _, _ -> if (active) { release(); env.haptic() } else hold() },
            onMove = { _, _ -> },
            onUp = {},
            onReset = { release() },
            freshDownOnly = true
        )
        ButtonBehavior.TURBO -> RuntimeControl(
            r[0], r[1], r[2], r[3],
            onDown = { _, _ ->
                hold()
                env.handler.postDelayed(pulse, halfPeriodMs)
            },
            onMove = { _, _ -> },
            onUp = { release() }
        )
    }
}

private fun sendDpad(gamepad: BluetoothHidGamepad?, isWindowsMode: Boolean, h: DpadDir?, v: DpadDir?) {
    if (isWindowsMode) {
        gamepad?.setDpadState(
            up = v == DpadDir.UP,
            down = v == DpadDir.DOWN,
            left = h == DpadDir.LEFT,
            right = h == DpadDir.RIGHT
        )
    } else {
        val hat = when {
            v == DpadDir.UP   && h == null          -> BluetoothHidGamepad.HAT_UP
            v == DpadDir.UP   && h == DpadDir.RIGHT -> BluetoothHidGamepad.HAT_UP_RIGHT
            v == null         && h == DpadDir.RIGHT -> BluetoothHidGamepad.HAT_RIGHT
            v == DpadDir.DOWN && h == DpadDir.RIGHT -> BluetoothHidGamepad.HAT_DOWN_RIGHT
            v == DpadDir.DOWN && h == null          -> BluetoothHidGamepad.HAT_DOWN
            v == DpadDir.DOWN && h == DpadDir.LEFT  -> BluetoothHidGamepad.HAT_DOWN_LEFT
            v == null         && h == DpadDir.LEFT  -> BluetoothHidGamepad.HAT_LEFT
            v == DpadDir.UP   && h == DpadDir.LEFT  -> BluetoothHidGamepad.HAT_UP_LEFT
            else                                    -> BluetoothHidGamepad.HAT_NEUTRAL
        }
        gamepad?.setHat(hat)
    }
}

enum class DpadDir { UP, DOWN, LEFT, RIGHT }

data class DpadState(val h: DpadDir?, val v: DpadDir?)
