package com.custom.treadmill.ble

data class FtmsData(val speed: Double?, val distanceKm: Double?, val calories: Int?, val seconds: Int?, val inclination: Double? = null) {
    companion object {
        fun parse(bytes: ByteArray): FtmsData {
            var offset = 0
            fun read(count: Int): Int {
                require(offset + count <= bytes.size)
                var value = 0
                repeat(count) { value = value or ((bytes[offset++].toInt() and 255) shl (it * 8)) }
                return value
            }
            val flags = read(2)
            fun has(bit: Int) = flags and (1 shl bit) != 0
            val speed = if (!has(0)) read(2) / 100.0 else null
            if (has(1)) read(2)
            val distance = if (has(2)) read(3) / 1000.0 else null
            // Наклон — знаковое число в десятых долях процента; следующий угол рампы пропускаем.
            val inclination = if (has(3)) { val raw = read(2); read(2); if (raw == 0x7fff) null else raw.toShort().toInt() / 10.0 } else null
            if (has(4)) read(4)
            if (has(5)) read(1)
            if (has(6)) read(1)
            val calories = if (has(7)) { val total = read(2); read(2); read(1); total } else null
            if (has(8)) read(1)
            if (has(9)) read(1)
            val seconds = if (has(10)) read(2) else null
            if (has(11)) read(2)
            if (has(12)) read(4)
            return FtmsData(speed, distance, calories, seconds, inclination)
        }
    }
}
