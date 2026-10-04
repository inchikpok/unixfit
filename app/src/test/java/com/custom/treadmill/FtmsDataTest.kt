package com.custom.treadmill

import com.custom.treadmill.ble.FtmsData
import org.junit.Assert.*
import org.junit.Test

class FtmsDataTest {
    @Test fun basicSpeed() {
        val d = FtmsData.parse(byteArrayOf(0, 0, 0xe8.toByte(), 3))
        assertEquals(10.0, d.speed!!, 0.001)
        assertNull(d.seconds)
    }
    @Test fun distanceEnergyAndTime() {
        val d = FtmsData.parse(byteArrayOf(0x84.toByte(), 4, 0xf4.toByte(), 1, 0xe8.toByte(), 3, 0, 42, 0, 0, 0, 0, 0x2c, 1))
        assertEquals(5.0, d.speed!!, 0.001)
        assertEquals(1.0, d.distanceKm!!, 0.001)
        assertEquals(42, d.calories)
        assertEquals(300, d.seconds)
    }
    @Test fun continuationOmitsSpeed() {
        val d = FtmsData.parse(byteArrayOf(5, 0, 0xe8.toByte(), 3, 0))
        assertNull(d.speed)
        assertEquals(1.0, d.distanceKm!!, 0.001)
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedPacket() {
        FtmsData.parse(byteArrayOf(0, 0, 1))
    }
}
