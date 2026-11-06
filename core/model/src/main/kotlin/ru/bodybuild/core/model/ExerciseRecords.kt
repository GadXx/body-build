package ru.bodybuild.core.model

data class RecordResult(val sessionId: String, val set: CompletedSet)

data class ExerciseRecords(
    val exerciseId: String,
    val name: String,
    val maximumWeight: RecordResult?,
    val maximumRepetitions: RecordResult,
    val bestPerformance: RecordResult,
    val totalSets: Int,
)

/** Recomputed from completed sessions; no separate stale record cache. Equal records keep the earliest date. */
fun exerciseRecords(history: List<WorkoutSession>): List<ExerciseRecords> = history
    .filter { it.isFinished }.distinctBy { it.id }
    .flatMap { session -> session.completedSets.distinctBy { it.exerciseIndex to it.setNumber }
        .filter { it.repetitions > 0 && it.effectiveWeightKg().isFinite() && it.effectiveWeightKg() >= 0 }
        .map { RecordResult(session.id, it) } }
    .sortedWith(compareBy({ it.set.completedAtEpochMillis }, { it.sessionId }, { it.set.exerciseIndex }, { it.set.setNumber }))
    .groupBy { it.set.exerciseId }
    .map { (id, results) ->
        ExerciseRecords(
            exerciseId = id,
            name = results.last().set.exerciseName,
            // A heavier body is not itself a strength record.
            maximumWeight = results.filterNot { it.set.bodyweight }.maxByOrNull { it.set.weightKg },
            maximumRepetitions = results.maxBy { it.set.repetitions },
            bestPerformance = results.maxBy { it.set.performanceIndex() },
            totalSets = results.size,
        )
    }.sortedBy { it.name }
