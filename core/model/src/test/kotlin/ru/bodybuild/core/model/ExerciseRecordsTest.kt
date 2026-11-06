package ru.bodybuild.core.model

import kotlin.test.*

class ExerciseRecordsTest {
    private fun session(id: String, time: Long, vararg sets: CompletedSet) = WorkoutSession(
        id, "p", "План", TrainingDay("d", "A", "", emptyList(), 0), time, time + 100,
        sets.toList(), mode = WorkoutMode.FREE,
    )
    private fun set(reps: Int, kg: Double, time: Long = 1000, id: String = "bench") =
        CompletedSet(id, id, 0, 1, reps, kg, time)

    @Test fun `independent records include date and source session`() {
        val records = exerciseRecords(listOf(
            session("heavy", 100, set(3, 100.0, 110)),
            session("reps", 200, set(20, 30.0, 210)),
            session("index", 300, set(10, 90.0, 310)),
        )).single()
        assertEquals("heavy", records.maximumWeight!!.sessionId)
        assertEquals(110L, records.maximumWeight.set.completedAtEpochMillis)
        assertEquals("reps", records.maximumRepetitions.sessionId)
        assertEquals("index", records.bestPerformance.sessionId)
        assertEquals(3, records.totalSets)
    }

    @Test fun `unfinished empty and duplicate sessions do not invent records`() {
        val done = session("done", 100, set(5, 50.0))
        val active = session("active", 200, set(10, 200.0)).copy(completedAtEpochMillis = null)
        val result = exerciseRecords(listOf(done, done, active, session("empty", 300))).single()
        assertEquals(1, result.totalSets)
        assertEquals(50.0, result.maximumWeight!!.set.weightKg)
        assertTrue(exerciseRecords(emptyList()).isEmpty())
    }

    @Test fun `body mass is not a weight lifted record`() {
        val body = set(15, 0.0).copy(bodyweight = true, userBodyWeightKg = 75.0)
        val result = exerciseRecords(listOf(session("body", 100, body))).single()
        assertNull(result.maximumWeight)
        assertEquals(15, result.maximumRepetitions.set.repetitions)
        assertEquals(112.5, result.bestPerformance.set.performanceIndex())
    }

    @Test fun `editing or removing history recalculates records`() {
        val older = session("old", 100, set(10, 80.0))
        val newer = session("new", 200, set(10, 100.0))
        val edited = newer.editCompletedSet(newer.completedSets.single(), 8, 60.0)
        assertEquals("old", exerciseRecords(listOf(older, edited)).single().maximumWeight!!.sessionId)
        assertEquals("new", exerciseRecords(listOf(edited)).single().maximumWeight!!.sessionId)
    }

    @Test fun `ties retain earliest achievement and exercise IDs stay separate`() {
        val old = session("old", 100, set(10, 80.0, 110))
        val new = session("new", 200, set(10, 80.0, 210))
        val other = session("other", 300, set(12, 30.0, 310, "curl"))
        val result = exerciseRecords(listOf(new, other, old))
        assertEquals(2, result.size)
        assertEquals("old", result.first { it.exerciseId == "bench" }.bestPerformance.sessionId)
    }
}
