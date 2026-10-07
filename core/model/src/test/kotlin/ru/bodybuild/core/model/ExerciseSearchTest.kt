package ru.bodybuild.core.model

import kotlin.test.*

class ExerciseSearchTest {
    private val calf = Exercise("calf", "Подъём на носки", "", emptyList(), MovementPattern.CALF_RAISE,
        setOf(Equipment.BARBELL), stimuli = listOf(MuscleStimulus(MuscleRegion.CALVES, 1.0, StimulusRole.PRIMARY)))
    @Test fun `search accepts muscle equipment and normalized name`() {
        assertTrue(calf.matchesSearch("икры"))
        assertTrue(calf.matchesSearch("подъем штанга"))
        assertTrue(calf.matchesSearch("  "))
        assertFalse(calf.matchesSearch("бицепс"))
    }
}
