package ru.cultureguide.kids.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTest {
    @Test
    fun collectingInOrderAdvancesToFinish() {
        var journey = Journey(stopCount = 3).start()
        repeat(3) { journey = journey.collect(journey.activeIndex) }
        assertEquals(3, journey.found)
        assertTrue(journey.finished)
        assertTrue(journey.isFound(2))
    }

    @Test
    fun collectingWrongOrRepeatedStopChangesNothing() {
        val journey = Journey(stopCount = 3).collect(0)
        assertEquals(journey, journey.collect(0))
        assertEquals(journey, journey.collect(2))
        assertFalse(journey.isFound(1))
    }

    @Test
    fun resetClearsProgress() {
        val journey = Journey(stopCount = 2).collect(0).collect(1).reset()
        assertEquals(Journey(stopCount = 2), journey)
    }

    @Test
    fun kidStepsRoundToReadableNumbers() {
        assertEquals(11, kidSteps(5.0))
        assertEquals(110, kidSteps(50.0))
        assertEquals(800, kidSteps(360.0))
    }

    @Test
    fun stepsWordAgreesWithNumber() {
        assertEquals("шаг", stepsWord(1))
        assertEquals("шага", stepsWord(3))
        assertEquals("шагов", stepsWord(12))
        assertEquals("шагов", stepsWord(100))
        assertEquals("шаг", stepsWord(21))
    }
}
