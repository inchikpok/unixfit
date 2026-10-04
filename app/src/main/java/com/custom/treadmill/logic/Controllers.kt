package com.custom.treadmill.logic

import kotlinx.coroutines.*

class HeartRateController(private val change: (Double) -> Unit) {
    var enabled = false; var target = 140; var highDelta = 5; var lowDelta = 5; var step = 0.5; var intervalSec = 30L
    private var job: Job? = null
    fun start(hr: () -> Int?, speed: () -> Double, min: () -> Double, max: () -> Double) { job?.cancel(); job = CoroutineScope(Dispatchers.Default).launch { while (isActive) { delay(intervalSec * 1000); if (!enabled) continue; val pulse = hr() ?: continue; val next = when { pulse > target + highDelta -> speed() - step; pulse < target - lowDelta -> speed() + step; else -> speed() }; change(next.coerceIn(min(), max())) } } }
    fun stop() { job?.cancel() }
}

class WorkoutManager(private val setSpeed: (Double) -> Unit) {
    data class Segment(val durationSec: Int, val speedKmh: Double, val name: String)
    var segments: List<Segment> = emptyList(); var current = 0; var remaining = 0
    private var job: Job? = null
    fun start() { job?.cancel(); job = CoroutineScope(Dispatchers.Default).launch { segments.forEachIndexed { index, s -> current = index; remaining = s.durationSec; setSpeed(s.speedKmh); repeat(s.durationSec) { delay(1000); remaining-- } }; setSpeed(0.0) } }
    fun stop() { job?.cancel(); setSpeed(0.0) }
}
