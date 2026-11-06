package ru.bodybuild.feature.workout

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.bodybuild.core.model.*

class ProgressionTest {
    private val exercise = Exercise(
        "bench", "Жим", "", emptyList(), MovementPattern.HORIZONTAL_PUSH,
        setOf(Equipment.BARBELL), stimuli = listOf(MuscleStimulus(MuscleRegion.MID_CHEST, 1.0, StimulusRole.PRIMARY)),
    )
    private val day = TrainingDay("a", "A", "Грудь", listOf(Prescription(exercise, 1, IntRangeSurrogate(5, 12), 2, 90, "")), 10)
    private val dayMillis = 24L * 60 * 60 * 1_000

    private fun workout(id: String, time: Long, weight: Double) = WorkoutSession(
        id, "plan", "План", day, time, time + 1_000,
        listOf(CompletedSet("bench", "Жим", 0, 1, 10, weight, time + 500)),
    )

    @Test fun `last period compares last two workouts`() {
        val now = 100 * dayMillis
        val item = progression(listOf(workout("new", now, 120.0), workout("old", now - dayMillis, 100.0)), ProgressPeriod.LAST, now).single()
        assertEquals(160.0, item.current, .001)
        assertEquals(133.333, item.previous!!, .001)
        assertEquals(2, item.points.size)
    }

    @Test fun `week uses value before boundary as baseline`() {
        val now = 100 * dayMillis
        val history = listOf(workout("new", now, 120.0), workout("week", now - 6 * dayMillis, 110.0), workout("baseline", now - 10 * dayMillis, 100.0))
        val item = progression(history, ProgressPeriod.WEEK, now).single()
        assertEquals(3, item.points.size)
        assertEquals(now - 7 * dayMillis, item.points.first().timestamp)
        assertTrue(item.current > item.previous!!)
    }

    @Test fun `period without workouts is empty`() {
        val now = 100 * dayMillis
        assertTrue(progression(listOf(workout("old", now - 40 * dayMillis, 100.0)), ProgressPeriod.MONTH, now).isEmpty())
    }

    @Test fun `free and planned sessions share progression and tonnage`() {
        val now = 100 * dayMillis
        val free = workout("free", now, 120.0).copy(mode = WorkoutMode.FREE)
        val planned = workout("planned", now - dayMillis, 100.0)
        val item = progression(listOf(free, planned), ProgressPeriod.LAST, now).single()
        assertEquals(2, item.points.size)
        assertEquals(160.0, item.current, .001)
        assertEquals(1200.0, free.completedSets.sumOf(CompletedSet::tonnageKg))
    }

    @Test fun `editing old or latest record recalculates graph and comparison`() {
        val now = 100 * dayMillis
        val old = workout("old", now - dayMillis, 100.0)
        val latest = workout("new", now, 120.0)
        val correctedOld = old.editCompletedSet(old.completedSets.single(), 6, 90.0)
        val correctedLatest = latest.editCompletedSet(latest.completedSets.single(), 12, 110.0)
        val item = progression(listOf(correctedLatest, correctedOld), ProgressPeriod.LAST, now).single()
        assertEquals(108.0, item.previous!!, .001)
        assertEquals(154.0, item.current, .001)
        assertEquals(12, item.points.last().repetitions)
        assertEquals(110.0, item.points.last().weightKg)
        assertEquals(old.startedAtEpochMillis, item.points.first().timestamp)
    }
}
