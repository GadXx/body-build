package ru.bodybuild.domain.generator

import ru.bodybuild.core.model.*

/** Product heuristics, deliberately separate from the search algorithm. */
data class VolumePolicy(
    val baseTargets: Map<ExperienceLevel, Double> = mapOf(
        ExperienceLevel.BEGINNER to 6.0,
        ExperienceLevel.INTERMEDIATE to 8.0,
        ExperienceLevel.ADVANCED to 10.0,
    ),
    val priorityMultiplier: Double = 1.5,
    val maintenanceMultiplier: Double = 0.55,
    val maximumExtraSets: Double = 2.0,
    val maxSetsPerExercise: Int = 4,
    val maxExercisesPerDay: Int = 10,
) {
    fun target(profile: UserProfile, group: MuscleGroup): Double {
        val base = baseTargets.getValue(profile.experience)
        val size = if (group in setOf(MuscleGroup.CORE, MuscleGroup.CALVES, MuscleGroup.FOREARMS)) 0.65 else 1.0
        val multiplier = when {
            profile.goal == TrainingGoal.MAINTENANCE || group in profile.maintenanceMuscles -> maintenanceMultiplier
            group in profile.priorityMuscles -> priorityMultiplier
            else -> 1.0
        }
        return base * size * multiplier
    }
}
