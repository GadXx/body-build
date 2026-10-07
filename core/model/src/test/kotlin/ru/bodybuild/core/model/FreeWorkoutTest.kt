package ru.bodybuild.core.model

import kotlinx.serialization.json.Json
import kotlin.test.*

class FreeWorkoutTest {
    private val curl = Exercise("curl", "Сгибание", "", emptyList(), MovementPattern.ELBOW_FLEXION, setOf(Equipment.EZ_BAR),
        stimuli = listOf(MuscleStimulus(MuscleRegion.BICEPS_LONG, 1.0, StimulusRole.PRIMARY),
            MuscleStimulus(MuscleRegion.BICEPS_SHORT, .9, StimulusRole.PRIMARY),
            MuscleStimulus(MuscleRegion.BRACHIORADIALIS, .3, StimulusRole.SECONDARY)))
    private val calf = curl.copy(id = "calf", stimuli = listOf(MuscleStimulus(MuscleRegion.CALVES, 1.0, StimulusRole.PRIMARY)))
    private fun empty() = WorkoutSession("s", "free", "Свободная", TrainingDay("d", "Свободная", "", emptyList(), 0), 0, mode = WorkoutMode.FREE)
    private fun complete(session: WorkoutSession, reps: Int = 10, weight: Double = 20.0, now: Long = 1000) =
        session.recordSet(session.nextSet()!!, reps, weight, 75.0, now)

    @Test fun `free session starts empty and waits after each set`() {
        val session = empty()
        assertNull(session.nextSet())
        val updated = complete(session.addFreeExercise(curl))
        assertFalse(updated.isFinished)
        assertNull(updated.nextSet())
        assertEquals(91_000, updated.restEndsAtEpochMillis)
        assertEquals(1, updated.completedSets.size)
    }

    @Test fun `repeat set preserves numbering and rest`() {
        val first = complete(empty().addFreeExercise(curl))
        val repeat = first.addFreeExercise(curl)
        assertEquals(2, repeat.nextSet()?.setNumber)
        assertEquals(first.restEndsAtEpochMillis, repeat.restEndsAtEpochMillis)
        assertEquals(repeat, complete(repeat, now = 2000)) // no writing during rest
        assertEquals(2, complete(repeat, now = 100_000).completedSets.size)
    }

    @Test fun `queued duplicate addition is ignored`() {
        val session = empty().addFreeExercise(curl)
        assertEquals(session, session.addFreeExercise(curl))
        assertEquals(session, session.addFreeExercise(calf))
    }

    @Test fun `stale complete callback cannot record next set`() {
        val first = empty().addFreeExercise(curl)
        val oldTarget = first.nextSet()!!
        val next = complete(first).addFreeExercise(curl)
        assertEquals(next, next.recordSet(oldTarget, 10, 20.0, 75.0, 100_000))
    }

    @Test fun `skip does not finish free session or invent volume`() {
        val session = complete(empty().addFreeExercise(curl)).addFreeExercise(calf).skipCurrentExercise(100_000)
        assertFalse(session.isFinished)
        val finished = session.finishFreeWorkout(200_000)
        val load = completedMuscleLoad(listOf(finished))
        assertEquals(1.0, load[MuscleGroup.BICEPS]) // heads are not added together
        assertEquals(.3, load[MuscleGroup.FOREARMS])
        assertEquals(0.0, load[MuscleGroup.CALVES])
    }

    @Test fun `finish retains partial records only and locks changes`() {
        val partial = complete(empty().addFreeExercise(curl)).addFreeExercise(curl)
        val done = partial.finishFreeWorkout(100_000)
        assertEquals(1, done.completedSets.size)
        assertNull(done.restEndsAtEpochMillis)
        assertEquals(done, done.addFreeExercise(calf))
        assertEquals(done, done.recordSet(partial.nextSet()!!, 8, 30.0, 80.0, 200_000))
        assertFalse(empty().finishFreeWorkout(100).isFinished)
    }

    @Test fun `new fields round trip and legacy payload defaults to planned`() {
        val saved = complete(empty().addFreeExercise(curl))
        assertEquals(saved, Json.decodeFromString<WorkoutSession>(Json.encodeToString(saved)))
        val legacy = Json.encodeToString(empty().copy(mode = WorkoutMode.PLANNED))
        assertFalse(legacy.contains("mode"))
        assertEquals(WorkoutMode.PLANNED, Json.decodeFromString<WorkoutSession>(legacy).mode)
    }

    @Test fun `prefill uses matching ordinal from latest session not older sets`() {
        val older = complete(empty().copy(id = "old").addFreeExercise(curl), reps = 12).addFreeExercise(curl)
        val olderDone = complete(older, reps = 15, now = 100_000).finishFreeWorkout(200_000)
        val latest = complete(empty().copy(id = "recent").addFreeExercise(curl), reps = 8, weight = 30.0).finishFreeWorkout(300_000)
        val current = empty().copy(id = "current").addFreeExercise(curl)
        assertEquals(8, previousMatchingSet(listOf(olderDone, latest), current, current.nextSet()!!)?.repetitions)
        val second = complete(current).addFreeExercise(curl)
        assertNull(previousMatchingSet(listOf(olderDone, latest), second, second.nextSet()!!))
    }

    @Test fun `prefill handles exercise revisited later in session`() {
        val previous = complete(empty().copy(id = "old").addFreeExercise(curl), reps = 12).addFreeExercise(curl)
        val previousDone = complete(previous, reps = 9, weight = 25.0, now = 100_000).finishFreeWorkout(200_000)
        val current = complete(empty().addFreeExercise(curl)).addFreeExercise(calf).skipCurrentExercise(100_000).addFreeExercise(curl)
        assertEquals(9, previousMatchingSet(listOf(previousDone), current, current.nextSet()!!)?.repetitions)
    }

    @Test fun `selection ignores duplicate sessions and unfinished workouts`() {
        val done = complete(empty().addFreeExercise(curl)).finishFreeWorkout(100_000)
        val unfinished = complete(empty().copy(id = "other").addFreeExercise(calf))
        assertEquals(completedMuscleLoad(listOf(done)), completedMuscleLoad(listOf(done, done, unfinished)))
        assertTrue(completedMuscleLoad(emptyList()).values.all { it == 0.0 })
    }

    @Test fun `planned sessions still auto finish`() {
        val planned = empty().addFreeExercise(curl).copy(mode = WorkoutMode.PLANNED)
        assertTrue(complete(planned).isFinished)
        assertTrue(planned.skipCurrentExercise(100).isFinished)
    }

    @Test fun `body weight is captured per record not retroactively`() {
        val bw = calf.copy(equipment = setOf(Equipment.BODYWEIGHT))
        val session = empty().addFreeExercise(bw)
        val saved = session.recordSet(session.nextSet()!!, 10, 0.0, 72.0, 1000)
        assertEquals(720.0, saved.completedSets.single().tonnageKg())
        val second = saved.addFreeExercise(bw)
        val changed = second.recordSet(second.nextSet()!!, 10, 0.0, 73.0, 100_000)
        assertEquals(720.0, changed.completedSets.first().tonnageKg())
        assertEquals(730.0, changed.completedSets.last().tonnageKg())
    }
}
