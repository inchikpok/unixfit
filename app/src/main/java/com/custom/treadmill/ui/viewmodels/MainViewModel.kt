package com.custom.treadmill.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.custom.treadmill.ble.BleManager
import kotlinx.coroutines.flow.StateFlow
import com.custom.treadmill.ble.setSpeedTarget
import com.custom.treadmill.ble.setInclineTarget

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val ble = BleManager(app)
    val devices = ble.devices
    val logs: StateFlow<List<String>> = ble.events
    val speed = ble.speed; val heartRate = ble.heartRate; val treadmillConnected = ble.treadmillConnected; val heartRateConnected = ble.heartRateConnected
    fun scan() = ble.scan(); fun stopScan() = ble.stopScan(); fun connectTreadmill(i: Int) = devices.value.getOrNull(i)?.let(ble::connectTreadmill); fun connectHeartRate(i: Int) = devices.value.getOrNull(i)?.let(ble::connectHeartRate)
    fun faster() = ble.setSpeedTarget((speed.value + .5).coerceAtMost(22.0))
    fun slower() = ble.setSpeedTarget((speed.value - .5).coerceAtLeast(0.0))
    fun increaseIncline() = ble.setInclineTarget(((ble.inclination.value ?: 0.0)) + 1.0)
    fun decreaseIncline() = ble.setInclineTarget(((ble.inclination.value ?: 0.0)) - 1.0)
    fun stop() = ble.emergencyStop()
    fun start() = ble.start()
    override fun onCleared() { ble.disconnect(); super.onCleared() }
}
