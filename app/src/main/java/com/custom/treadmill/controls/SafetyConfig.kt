package com.custom.treadmill.controls

import kotlin.math.abs

object SafetyConfig {
    // Default safety limits for treadmill control (adjustable later via config UI)
    const val MAX_SPEED_KMH: Double = 22.0
    const val MAX_INCLINE_PERCENT: Double = 15.0

    fun clampSpeedKmh(speed: Double): Double = speed.coerceIn(0.0, MAX_SPEED_KMH)
    fun clampIncline(percent: Double): Double = percent.coerceIn(0.0, MAX_INCLINE_PERCENT)

    // Simple ramping helper: smoothly reach target at given rate (km/h per second)
    fun ramp(current: Double, target: Double, ratePerSecond: Double, deltaSeconds: Double): Double {
        val diff = target - current
        val step = ratePerSecond * deltaSeconds
        return if (abs(diff) <= step) target else if (diff > 0) current + step else current - step
    }
}
