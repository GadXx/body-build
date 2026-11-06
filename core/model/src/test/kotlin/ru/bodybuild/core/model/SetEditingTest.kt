package ru.bodybuild.core.model

import kotlinx.serialization.json.Json
import kotlin.test.*

class SetEditingTest {
    private val exercise = Exercise("curl", "Сгибание", "", emptyList(), MovementPattern.ELBOW_FLEXION,
        setOf(Equipment.DUMBBELLS), stimuli = listOf(MuscleStimulus(MuscleRegion.BICEPS_LONG, 1.0, StimulusRole.PRIMARY)))
    private fun session(mode: WorkoutMode = WorkoutMode.PLANNED): WorkoutSession {
        val day = TrainingDay("d", "A", "", listOf(Prescription(exercise, 3, IntRangeSurrogate(8, 12), 2, 90, "")), 10)
        val start = WorkoutSession("s", "p", "План", day, 1000, mode = mode)
        return start.recordSet(start.nextSet()!!, 10, 20.0, 75.0, 2000)
    }

    @Test fun `edit preserves timer queue identity and time in both modes`() {
        WorkoutMode.entries.forEach { mode ->
            val before = session(mode)
            val after = before.editCompletedSet(before.completedSets.single(), 12, 22.5)
            assertEquals(before.nextSet(), after.nextSet())
            assertEquals(before.restEndsAtEpochMillis, after.restEndsAtEpochMillis)
            assertEquals(before.completedAtEpochMillis, after.completedAtEpochMillis)
            assertEquals(before.completedSets.single().completedAtEpochMillis, after.completedSets.single().completedAtEpochMillis)
            assertEquals(before, after.copy(completedSets = before.completedSets))
            assertEquals(270.0, after.completedSets.single().tonnageKg())
        }
    }

    @Test fun `finished session can be corrected and serialized without reopening`() {
        val before = session().copy(completedAtEpochMillis = 10000, restEndsAtEpochMillis = null, skippedExerciseIndices = setOf(0))
        val after = before.editCompletedSet(before.completedSets.single(), 8, 30.0)
        assertTrue(after.isFinished)
        assertEquals(before.elapsedSeconds, after.elapsedSeconds)
        assertEquals(before.skippedExerciseIndices, after.skippedExerciseIndices)
        assertEquals(completedMuscleLoad(listOf(before)), completedMuscleLoad(listOf(after)))
        assertEquals(240.0, after.completedSets.single().tonnageKg())
        assertEquals(after, Json.decodeFromString<WorkoutSession>(Json.encodeToString(after)))
    }

    @Test fun `bodyweight edit changes only recorded bodyweight snapshot`() {
        val before = session().let { it.copy(completedSets = listOf(it.completedSets.single().copy(bodyweight = true, weightKg = 0.0))) }
        val after = before.editCompletedSet(before.completedSets.single(), 8, 80.0)
        assertEquals(80.0, after.completedSets.single().userBodyWeightKg)
        assertEquals(0.0, after.completedSets.single().weightKg)
        assertEquals(640.0, after.completedSets.single().tonnageKg())
    }

    @Test fun `invalid input missing record and stale edit rejected`() {
        val before = session()
        val set = before.completedSets.single()
        for (reps in listOf(0, -1, 1000)) assertFailsWith<IllegalArgumentException> { before.editCompletedSet(set, reps, 20.0) }
        for (weight in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, 2001.0)) assertFailsWith<IllegalArgumentException> { before.editCompletedSet(set, 10, weight) }
        assertFailsWith<IllegalArgumentException> { before.editCompletedSet(set.copy(setNumber = 2), 10, 20.0) }
        val after = before.editCompletedSet(set, 12, 30.0)
        assertFailsWith<IllegalArgumentException> { after.editCompletedSet(set, 8, 15.0) }
    }

    @Test fun `repeated exercise occurrences are not conflated and future suggestions use correction`() {
        val before = session().let { it.copy(day = it.day.copy(exercises = it.day.exercises + it.day.exercises.first()),
            completedSets = it.completedSets + it.completedSets.single().copy(exerciseIndex = 1, weightKg = 40.0), completedAtEpochMillis = 5000) }
        val after = before.editCompletedSet(before.completedSets.last(), 7, 45.0)
        assertEquals(before.completedSets.first(), after.completedSets.first())
        assertEquals(45.0, after.completedSets.last().weightKg)
        val nextSession = session().copy(id = "next")
        assertEquals(after.completedSets.last(), previousMatchingSet(listOf(after), nextSession, nextSession.nextSet()!!))
    }
}
