package ru.bodybuild.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutSessionTest {
    private val exercise = Exercise(
        id = "test", name = "Тест", description = "", technique = emptyList(),
        pattern = MovementPattern.CORE, equipment = setOf(Equipment.BODYWEIGHT),
        stimuli = listOf(MuscleStimulus(MuscleRegion.ABS, 1.0, StimulusRole.PRIMARY)),
    )
    private val day = TrainingDay("a", "A", "Кор", listOf(Prescription(exercise, 2, IntRangeSurrogate(8, 12), 2, 60, "")), 10)

    @Test fun `next set follows completed records`() {
        val session = WorkoutSession("s", "p", "План", day, 100)
        assertEquals(1, session.nextSet()?.setNumber)
        val first = CompletedSet("test", "Тест", 0, 1, 10, 20.0, 200)
        assertEquals(2, session.copy(completedSets = listOf(first)).nextSet()?.setNumber)
        assertNull(session.copy(completedSets = listOf(first, first.copy(setNumber = 2))).nextSet())
    }

    @Test fun `session survives serialization`() {
        val session = WorkoutSession("s", "p", "План", day, 100, restEndsAtEpochMillis = 500)
        assertEquals(session, Json.decodeFromString<WorkoutSession>(Json.encodeToString(session)))
    }

    @Test fun `skipped exercise is not counted as completed`() {
        val secondExercise = exercise.copy(id = "second", name = "Второе")
        val twoExercises = day.copy(exercises = day.exercises + Prescription(secondExercise, 2, IntRangeSurrogate(8, 12), 2, 60, ""))
        val completed = CompletedSet("test", "Тест", 0, 1, 10, 20.0, 200)
        val session = WorkoutSession("s", "p", "План", twoExercises, 100, completedSets = listOf(completed), skippedExerciseIndices = setOf(0))
        assertEquals(1, session.skippedSets)
        assertEquals(3, session.actionableSets)
        assertEquals("second", session.nextSet()?.prescription?.exercise?.id)
        assertEquals(1, session.completedSets.size)
    }

    @Test fun `performance index combines weight and repetitions`() {
        val set = CompletedSet("test", "Тест", 0, 1, 10, 100.0, 200)
        assertEquals(1000.0, set.tonnageKg())
        assertEquals(133.333, set.performanceIndex(), 0.001)
        val bodyweight = set.copy(weightKg = 0.0, bodyweight = true, userBodyWeightKg = 75.0)
        assertEquals(750.0, bodyweight.tonnageKg())
    }
}
