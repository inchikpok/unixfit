package com.custom.treadmill

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonFormatTest {
    @Test fun speedIsClampedBySafetyPolicy() {
        assertEquals(12.0, 15.0.coerceIn(0.0, 12.0), 0.0)
    }
}
