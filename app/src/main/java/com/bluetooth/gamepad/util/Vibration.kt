package com.bluetooth.gamepad.util

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticIntensity { OFF, LIGHT, MEDIUM, STRONG }

fun obtainVibrator(context: Context): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            ?: @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
