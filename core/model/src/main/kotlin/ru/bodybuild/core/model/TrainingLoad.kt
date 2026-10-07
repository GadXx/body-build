package ru.bodybuild.core.model

import kotlin.math.ceil

/** Coefficients describe independent muscles, not additive heads within one group. */
object TrainingLoad {
    fun coefficients(exercise: Exercise): Map<MuscleGroup, Double> =
        exercise.stimuli.filter { it.role != StimulusRole.STABILIZER }
            .groupBy { it.region.group }
            .mapValues { (_, regions) -> regions.maxOf { it.coefficient.coerceIn(0.0, 1.0) } }

    fun effort(rir: Int): Double = when {
        rir <= 1 -> 1.0
        rir == 2 -> 0.9
        rir == 3 -> 0.8
        else -> 0.65
    }

    fun totals(items: List<Prescription>): Map<MuscleGroup, Double> {
        val totals = mutableMapOf<MuscleGroup, Double>()
        items.forEach { item ->
            coefficients(item.exercise).forEach { (group, coefficient) ->
                totals[group] = totals.getOrDefault(group, 0.0) + item.sets * effort(item.rir) * coefficient
            }
        }
        return totals
    }

    fun seconds(items: List<Prescription>): Int = if (items.isEmpty()) 0 else 300 + items.sumOf {
        val execution = it.exercise.setDurationSeconds * (if (it.exercise.unilateral) 2 else 1)
        it.sets * execution + (it.sets - 1) * it.restSeconds + 60 + it.exercise.setupComplexity * 15
    }

    fun minutes(items: List<Prescription>): Int = ceil(seconds(items) / 60.0).toInt()
}
