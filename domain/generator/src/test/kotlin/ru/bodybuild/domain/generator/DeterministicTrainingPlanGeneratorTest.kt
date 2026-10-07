package ru.bodybuild.domain.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.bodybuild.core.model.*

class DeterministicTrainingPlanGeneratorTest {
    private val generator = DeterministicTrainingPlanGenerator()

    @Test
    fun `same input and seed produce same plan`() {
        val request = GeneratePlanRequest(UserProfile(daysPerWeek = 3, sessionMinutes = 60), seed = 7)
        val first = generator.generate(request, catalog)
        val second = generator.generate(request, catalog)
        assertEquals(first.weeklyPlan, second.weeklyPlan)
    }

    @Test
    fun `plan respects days and duration`() {
        val request = GeneratePlanRequest(UserProfile(daysPerWeek = 4, sessionMinutes = 45, splitPreference = SplitPreference.SPLIT))
        val result = generator.generate(request, catalog)
        assertEquals(4, result.weeklyPlan.days.size)
        assertTrue(result.weeklyPlan.days.all { it.estimatedMinutes <= 45 })
        assertTrue(result.weeklyPlan.days.all { it.exercises.isNotEmpty() })
    }

    @Test
    fun `excluded exercise is never selected`() {
        val excluded = catalog.first().id
        val request = GeneratePlanRequest(
            UserProfile(daysPerWeek = 2),
            exercisePreferences = listOf(ExercisePreference(excluded, PreferenceKind.EXCLUDED)),
        )
        val ids = generator.generate(request, catalog).weeklyPlan.days.flatMap { it.exercises }.map { it.exercise.id }
        assertTrue(excluded !in ids)
    }

    @Test
    fun `equipment is a hard filter`() {
        val request = GeneratePlanRequest(UserProfile(daysPerWeek = 1, equipment = setOf(Equipment.BODYWEIGHT)))
        val result = generator.generate(request, catalog)
        assertTrue(result.weeklyPlan.days.flatMap { it.exercises }.all { it.exercise.equipment == setOf(Equipment.BODYWEIGHT) })
    }

    private fun e(id: String, pattern: MovementPattern, group: MuscleGroup, equipment: Equipment = Equipment.BODYWEIGHT): Exercise {
        val region = MuscleRegion.entries.first { it.group == group }
        return Exercise(id, id, "", emptyList(), pattern, setOf(equipment), stimuli = listOf(MuscleStimulus(region, 1.0, StimulusRole.PRIMARY)))
    }

    private val catalog = listOf(
        e("squat", MovementPattern.SQUAT, MuscleGroup.QUADRICEPS),
        e("push", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST),
        e("vpush", MovementPattern.VERTICAL_PUSH, MuscleGroup.SHOULDERS),
        e("pull", MovementPattern.VERTICAL_PULL, MuscleGroup.BACK),
        e("row", MovementPattern.HORIZONTAL_PULL, MuscleGroup.BACK),
        e("hinge", MovementPattern.HIP_HINGE, MuscleGroup.HAMSTRINGS),
        e("lunge", MovementPattern.LUNGE, MuscleGroup.GLUTES),
        e("curl", MovementPattern.ELBOW_FLEXION, MuscleGroup.BICEPS),
        e("extension", MovementPattern.ELBOW_EXTENSION, MuscleGroup.TRICEPS),
        e("raise", MovementPattern.SHOULDER_ABDUCTION, MuscleGroup.SHOULDERS),
        e("legcurl", MovementPattern.KNEE_FLEXION, MuscleGroup.HAMSTRINGS),
        e("calf", MovementPattern.CALF_RAISE, MuscleGroup.CALVES),
        e("core", MovementPattern.CORE, MuscleGroup.CORE),
    )
}
