package com.custom.treadmill.ble

import java.util.UUID

interface ITreadmillProtocol {
    fun setSpeed(kmh: Double)
    fun emergencyStop()
    fun setProprietaryWriteUuid(uuid: UUID?)
}

class FTMSProtocol(private val manager: BleManager) : ITreadmillProtocol {
    override fun setSpeed(kmh: Double) = manager.setSpeed(kmh)
    override fun emergencyStop() = manager.emergencyStop()
    override fun setProprietaryWriteUuid(uuid: UUID?) { uuid?.let { manager.setCustomUuid(it.toString()) } }
}

class FitShowProprietaryProtocol(private val manager: BleManager) : ITreadmillProtocol {
    override fun setSpeed(kmh: Double) = manager.setSpeed(kmh)
    override fun emergencyStop() = manager.emergencyStop()
    override fun setProprietaryWriteUuid(uuid: UUID?) { uuid?.let { manager.setCustomUuid(it.toString()) } }
}
