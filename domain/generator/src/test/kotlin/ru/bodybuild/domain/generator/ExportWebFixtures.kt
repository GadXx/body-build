package ru.bodybuild.domain.generator

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.bodybuild.core.model.*
import ru.bodybuild.domain.catalog.SeedCatalog

/** Golden cases exported from the actual Kotlin engine, not reimplemented in test JS. */
fun main(args: Array<String>) {
    val json = Json { encodeDefaults = true; prettyPrint = true }
    val generator = DeterministicTrainingPlanGenerator()
    val profiles = listOf(UserProfile(), UserProfile(sessionMinutes = 30), UserProfile(daysPerWeek = 1),
        UserProfile(daysPerWeek = 2, splitPreference = SplitPreference.SPLIT),
        UserProfile(daysPerWeek = 4), UserProfile(daysPerWeek = 5, splitPreference = SplitPreference.SPLIT),
        UserProfile(daysPerWeek = 6, experience = ExperienceLevel.ADVANCED),
        UserProfile(priorityMuscles = setOf(MuscleGroup.BICEPS, MuscleGroup.CALVES)),
        UserProfile(equipment = setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS)),
        UserProfile(goal = TrainingGoal.MAINTENANCE), UserProfile(equipment = emptySet()))
    val cases = buildJsonArray {
        profiles.forEach { profile ->
            val plan = generator.generate(GeneratePlanRequest(profile), SeedCatalog.exercises).weeklyPlan
            add(buildJsonObject {
                put("profile", json.parseToJsonElement(json.encodeToString(profile)))
                put("days", buildJsonArray { plan.days.forEach { day -> add(buildJsonObject {
                    put("minutes", day.estimatedMinutes)
                    put("items", buildJsonArray { day.exercises.forEach { p -> add(buildJsonObject { put("id", p.exercise.id); put("sets", p.sets) }) } })
                }) } })
            })
        }
    }
    File(args.single()).apply { parentFile.mkdirs(); writeText(json.encodeToString(cases)) }
}
