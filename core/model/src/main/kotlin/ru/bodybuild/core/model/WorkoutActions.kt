package ru.bodybuild.core.model

/** Session transitions are shared by UI modes and independently testable. */
fun WorkoutSession.addFreeExercise(exercise: Exercise): WorkoutSession {
    if (mode != WorkoutMode.FREE || isFinished || nextSet() != null) return this
    val last = day.exercises.lastIndex
    val items = if (last >= 0 && last !in skippedExerciseIndices && day.exercises[last].exercise.id == exercise.id) {
        day.exercises.mapIndexed { i, p -> if (i == last) p.copy(sets = p.sets + 1) else p }
    } else day.exercises + Prescription(exercise, 1, exercise.repRange, 2, exercise.baseRestSeconds, "Выбрано вами в свободной тренировке")
    return copy(day = day.copy(exercises = items, estimatedMinutes = TrainingLoad.minutes(items)))
}

fun WorkoutSession.recordSet(target: PlannedSet, repetitions: Int, weightKg: Double, bodyWeightKg: Double, now: Long): WorkoutSession {
    if (isFinished || target != nextSet() || (restEndsAtEpochMillis ?: 0) > now || repetitions !in 1..999 || !weightKg.isFinite() || weightKg < 0) return this
    val exercise = target.prescription.exercise
    val bodyweight = Equipment.BODYWEIGHT in exercise.equipment
    val record = CompletedSet(exercise.id, exercise.name, target.exerciseIndex, target.setNumber, repetitions,
        if (bodyweight) 0.0 else weightKg, now, bodyweight, bodyWeightKg)
    val updated = copy(completedSets = completedSets + record)
    val finished = mode == WorkoutMode.PLANNED && updated.nextSet() == null
    return updated.copy(completedAtEpochMillis = now.takeIf { finished },
        restEndsAtEpochMillis = if (finished) null else now + target.prescription.restSeconds * 1000L)
}

fun WorkoutSession.skipCurrentExercise(now: Long): WorkoutSession {
    if (isFinished) return this
    val next = nextSet() ?: return this
    val updated = copy(skippedExerciseIndices = skippedExerciseIndices + next.exerciseIndex, restEndsAtEpochMillis = null)
    return updated.copy(completedAtEpochMillis = now.takeIf { mode == WorkoutMode.PLANNED && updated.nextSet() == null })
}

fun WorkoutSession.finishFreeWorkout(now: Long): WorkoutSession =
    if (mode != WorkoutMode.FREE || isFinished || completedSets.isEmpty()) this
    else copy(completedAtEpochMillis = now, restEndsAtEpochMillis = null)

/** Use only the latest completed session containing this exercise, never mix sessions by set number. */
fun previousMatchingSet(history: List<WorkoutSession>, session: WorkoutSession, target: PlannedSet): CompletedSet? {
    val id = target.prescription.exercise.id
    val ordinal = session.completedSets.count { it.exerciseId == id }
    val latest = history.filter { it.isFinished && it.id != session.id && it.completedSets.any { set -> set.exerciseId == id } }
        .maxByOrNull { it.completedAtEpochMillis ?: it.startedAtEpochMillis } ?: return null
    return latest.completedSets.filter { it.exerciseId == id }.sortedWith(compareBy({ it.exerciseIndex }, { it.setNumber })).getOrNull(ordinal)
}

/** Actual recorded volume; no invented RIR or weekly targets for an arbitrary selection. */
fun completedMuscleLoad(sessions: List<WorkoutSession>): Map<MuscleGroup, Double> {
    val totals = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
    sessions.filter { it.isFinished }.distinctBy { it.id }.forEach { session ->
        session.completedSets.distinctBy { it.exerciseIndex to it.setNumber }.forEach setLoop@ { set ->
            val exercise = session.day.exercises.getOrNull(set.exerciseIndex)?.exercise ?: return@setLoop
            if (exercise.id == set.exerciseId) TrainingLoad.coefficients(exercise).forEach { (group, coefficient) ->
                totals[group] = totals.getValue(group) + coefficient
            }
        }
    }
    return totals
}
