package ru.bodybuild.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.bodybuild.core.model.*

class DefaultPlanValidatorTest {
    private val validator = DefaultPlanValidator()

    @Test
    fun `accepts matching non-empty plan`() {
        val exercise = Exercise("x", "Тест", "", emptyList(), MovementPattern.CORE, setOf(Equipment.BODYWEIGHT), stimuli = listOf(MuscleStimulus(MuscleRegion.ABS, 1.0, StimulusRole.PRIMARY)))
        val day = TrainingDay("a", "A", "Кор", listOf(Prescription(exercise, 2, IntRangeSurrogate(10, 15), 2, 60, "Тест")), 15)
        assertTrue(validator.validate(WeeklyPlan("p", "План", listOf(day), 0), UserProfile(daysPerWeek = 1, sessionMinutes = 30)).isValid)
    }

    @Test
    fun `rejects missing day`() {
        assertFalse(validator.validate(WeeklyPlan("p", "План", emptyList(), 0), UserProfile(daysPerWeek = 1)).isValid)
    }
}
