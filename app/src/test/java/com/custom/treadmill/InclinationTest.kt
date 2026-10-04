package com.custom.treadmill

import com.custom.treadmill.ble.FtmsData
import com.custom.treadmill.ble.InclinationRange
import org.junit.Assert.*
import org.junit.Test

class InclinationTest {
    @Test fun rangeAndCommand() {
        val r = InclinationRange.parse(byteArrayOf(0, 0, 150.toByte(), 0, 10, 0))
        assertEquals(15.0, r.maximum, 0.0)
        assertEquals(1.0, r.next(0.0, 1)!!, 0.0)
        assertArrayEquals(byteArrayOf(3, 10, 0), r.command(1.0))
        assertNull(r.next(15.0, 1))
        assertNull(r.next(0.0, -1))
    }
    @Test fun negativeTelemetryAndFollowingTime() {
        val d = FtmsData.parse(byteArrayOf(8, 4, 100, 0, 246.toByte(), 255.toByte(), 0, 0, 60, 0))
        assertEquals(-1.0, d.inclination!!, 0.0)
        assertEquals(60, d.seconds)
    }
    @Test fun unavailableIncline() {
        assertNull(FtmsData.parse(byteArrayOf(8, 0, 0, 0, 255.toByte(), 127, 0, 0)).inclination)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroStepRejected() {
        InclinationRange.parse(byteArrayOf(0, 0, 150.toByte(), 0, 0, 0))
    }
    @Test(expected = IllegalArgumentException::class) fun unalignedCommandRejected() {
        InclinationRange(0, 150, 10).command(1.5)
    }
    @Test fun fractionalStep() {
        val r = InclinationRange(0, 150, 5)
        assertEquals(1.5, r.next(1.0, 1)!!, 0.0)
        assertEquals(1.0, r.next(1.3, -1)!!, 0.0)
    }
}
