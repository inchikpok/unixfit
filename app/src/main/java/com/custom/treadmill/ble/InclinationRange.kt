package com.custom.treadmill.ble

// Все расчёты выполняются в целых десятых долях процента, без накопления ошибки округления.
data class InclinationRange(val minRaw: Int, val maxRaw: Int, val stepRaw: Int) {
    init { require(minRaw in -32768..32766 && maxRaw in minRaw..32766 && stepRaw in 1..65535) }
    val minimum get() = minRaw / 10.0
    val maximum get() = maxRaw / 10.0
    val step get() = stepRaw / 10.0

    fun next(current: Double, direction: Int): Double? {
        if (!current.isFinite() || direction !in listOf(-1, 1)) return null
        val raw = kotlin.math.round(current * 10).toInt()
        if (raw !in minRaw..maxRaw) return null
        val position = (raw - minRaw).toDouble() / stepRaw
        val index = if (direction > 0) kotlin.math.floor(position).toInt() + 1 else kotlin.math.ceil(position).toInt() - 1
        val target = minRaw + index * stepRaw
        // Ограничение первой тестовой версии 0–15% не означает соответствие уровням панели.
        if (target !in minRaw..maxRaw || target !in 0..150) return null
        return target / 10.0
    }

    fun command(target: Double): ByteArray {
        require(target.isFinite())
        val raw = kotlin.math.round(target * 10).toInt()
        require(kotlin.math.abs(target * 10 - raw) < 0.001)
        require(raw in minRaw..maxRaw && raw in 0..150 && (raw - minRaw) % stepRaw == 0)
        return byteArrayOf(3, raw.toByte(), (raw shr 8).toByte())
    }

    companion object {
        fun parse(bytes: ByteArray): InclinationRange {
            require(bytes.size == 6)
            fun u(i: Int) = (bytes[i].toInt() and 255) or ((bytes[i + 1].toInt() and 255) shl 8)
            return InclinationRange(u(0).toShort().toInt(), u(2).toShort().toInt(), u(4))
        }
    }
}
