package ru.bodybuild.feature.workout

import org.junit.Test
import kotlin.test.assertEquals

class SetJournalTest {
    @Test fun `weight editor does not round fractional plates`() {
        assertEquals("22,25", 22.25.kgLabel())
        assertEquals("20", 20.0.kgLabel())
        assertEquals("0", 0.0.kgLabel())
        assertEquals(22.125, 22.125.kgLabel().replace(',', '.').toDouble())
    }
}
