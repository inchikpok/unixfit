package com.custom.treadmill.ble

import android.os.Handler
import android.os.Looper
import com.custom.treadmill.controls.SafetyConfig
import kotlin.math.abs

// Extension function to ramp the treadmill speed smoothly to a target value
fun BleManager.setSpeedTarget(targetKmH: Double) {
    val clamped = SafetyConfig.clampSpeedKmh(targetKmH)
    val intervalMs = 50L
    val rampRate = 6.0 // km/h per second
    val handler = Handler(Looper.getMainLooper())
    var current = speed.value
    val target = clamped
    val runnable = object : Runnable {
        override fun run() {
            current = SafetyConfig.ramp(current, target, rampRate, intervalMs / 1000.0)
            speed.value = current
            if (abs(current - target) > 0.01) {
                handler.postDelayed(this, intervalMs)
            }
        }
    }
    handler.post(runnable)
}

// Extension function to ramp the incline smoothly to a target percentage
fun BleManager.setInclineTarget(targetPercent: Double) {
    if (!inclinationSupported.value) return
    val clamped = SafetyConfig.clampIncline(targetPercent)
    val intervalMs = 50L
    val rampRate = 1.5 // percent per second
    val handler = Handler(Looper.getMainLooper())
    var current = inclination.value ?: 0.0
    val target = clamped
    val runnable = object : Runnable {
        override fun run() {
            current = SafetyConfig.ramp(current, target, rampRate, intervalMs / 1000.0)
            inclination.value = current
            if (abs(current - target) > 0.01) {
                handler.postDelayed(this, intervalMs)
            }
        }
    }
    handler.post(runnable)
}
