package ru.bodybuild.domain.generator

import kotlin.test.*
import ru.bodybuild.core.model.*
import ru.bodybuild.domain.catalog.SeedCatalog

class WeeklyBalanceTest {
    private val generator = DeterministicTrainingPlanGenerator()
    private fun generate(profile: UserProfile) = generator.generate(GeneratePlanRequest(profile), SeedCatalog.exercises)

    @Test fun headsAreNotCountedAsMultipleSets() {
        val curl = SeedCatalog.exercises.first { it.id == "incline-curl" }
        val totals = TrainingLoad.totals(listOf(Prescription(curl, 3, curl.repRange, 1, 75, "")))
        assertEquals(3.0, totals.getValue(MuscleGroup.BICEPS))
    }

    @Test fun moreTimeProvidesMoreWorkUntilTargetsAreMet() {
        val profiles = listOf(30, 45, 60, 90, 120).map { UserProfile(sessionMinutes = it) }
        val plans = profiles.map(::generate)
        val sets = plans.map { it.weeklyPlan.days.sumOf { day -> day.exercises.sumOf { p -> p.sets } } }
        println("time/sets: ${profiles.map { it.sessionMinutes }.zip(sets)}")
        assertTrue(sets.last() > sets.first())
        assertTrue(plans.last().muscleCoverage.sumOf { it.effectiveSets } > plans.first().muscleCoverage.sumOf { it.effectiveSets })
    }

    @Test fun prioritiesIncreaseActualVolume() {
        val base = generate(UserProfile(sessionMinutes = 60))
        for (group in MuscleGroup.entries) {
            val prioritized = generate(UserProfile(sessionMinutes = 60, priorityMuscles = setOf(group)))
            val before = base.muscleCoverage.first { it.group == group }.effectiveSets
            val after = prioritized.muscleCoverage.first { it.group == group }.effectiveSets
            assertTrue(after > before, "$group: before=$before after=$after")
        }
    }

    @Test fun matrixRespectsBudgetCapsAndCoverage() {
        for (level in ExperienceLevel.entries) for (split in SplitPreference.entries) {
            for (days in 1..6) for (minutes in listOf(30, 60, 120)) {
                val p = UserProfile(experience = level, splitPreference = split, daysPerWeek = days, sessionMinutes = minutes)
                val result = generate(p)
                val label = "$level $split $days x $minutes"
                assertEquals(days, result.weeklyPlan.days.size, label)
                assertTrue(result.weeklyPlan.days.all { it.exercises.isNotEmpty() && it.estimatedMinutes <= minutes }, label)
                assertTrue(result.muscleCoverage.all { it.effectiveSets <= it.targetMax + 0.05 }, label)
                assertTrue(result.weeklyPlan.days.all { d -> d.exercises.map { it.exercise.id }.distinct().size == d.exercises.size }, label)
                if (days >= 3 && minutes >= 60) assertTrue(result.muscleCoverage.all { it.effectiveSets > 0 }, "$label: missing ${result.muscleCoverage.filter { it.effectiveSets == 0.0 }}")
            }
        }
    }

    @Test fun fullBodyDaysAreBalanced() {
        val result = generate(UserProfile(daysPerWeek = 3, sessionMinutes = 60, splitPreference = SplitPreference.FULL_BODY))
        val days = result.weeklyPlan.days
        val counts = days.map { it.exercises.sumOf { p -> p.sets } }
        println("full body: ${days.map { it.estimatedMinutes }} min / $counts sets")
        assertTrue(counts.max() - counts.min() <= 4, counts.toString())
        assertTrue(days.maxOf { it.estimatedMinutes } - days.minOf { it.estimatedMinutes } <= 10)
    }

    @Test fun catalogIsCompleteAndVersioned() {
        val catalog = SeedCatalog.exercises
        assertTrue(catalog.size >= 133)
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        assertTrue(catalog.all { it.description.isEmpty() && it.technique.isEmpty() && it.catalogVersion in 2..3 })
        assertTrue(catalog.count { it.pattern == MovementPattern.CALF_RAISE } >= 10)
        assertTrue(catalog.any { it.id == "ez-curl" && Equipment.EZ_BAR in it.equipment })
        assertTrue(catalog.any { it.id == "barbell-wide-curl" && Equipment.BARBELL in it.equipment })
        assertTrue(catalog.all { e -> e.equipment.isNotEmpty() && e.stimuli.isNotEmpty() && e.stimuli.all { it.coefficient in 0.0..1.0 } })
        for (group in MuscleGroup.entries) assertTrue(catalog.count { e ->
            e.stimuli.any { it.region.group == group && it.role == StimulusRole.PRIMARY }
        } >= 2, group.toString())
    }

    @Test fun splitDaysAndShoulderRegionsAreBalanced() {
        for (split in listOf(SplitPreference.SPLIT, SplitPreference.FULL_BODY)) {
            val result = generate(UserProfile(splitPreference = split))
            val days = result.weeklyPlan.days
            val sets = days.map { it.exercises.sumOf { p -> p.sets } }
            println("$split: ${days.map { it.estimatedMinutes }} minutes; $sets sets")
            assertTrue(sets.max() - sets.min() <= 8, "$split: $sets")
            for (region in listOf(MuscleRegion.SIDE_DELTS, MuscleRegion.REAR_DELTS)) {
                val volume = days.flatMap { it.exercises }.sumOf { p ->
                    p.sets * TrainingLoad.effort(p.rir) * p.exercise.stimuli
                        .filter { it.region == region && it.role != StimulusRole.STABILIZER }
                        .maxOfOrNull { it.coefficient }.orZero()
                }
                assertTrue(volume >= VolumePolicy().target(UserProfile(), region.group) * 0.25, "$split: $region = $volume")
            }
        }
    }

    @Test fun restrictedEquipmentAndImpossibleRequestsAreExplicit() {
        val profile = UserProfile(equipment = setOf(Equipment.BODYWEIGHT), sessionMinutes = 30)
        val result = generate(profile)
        assertTrue(result.weeklyPlan.days.flatMap { it.exercises }.all { p -> p.exercise.equipment.all { it in profile.equipment } })
        assertTrue(result.warnings.isNotEmpty())
        val empty = generator.generate(GeneratePlanRequest(profile), emptyList())
        assertFalse(empty.validationReport.isValid)
        assertTrue(empty.warnings.isNotEmpty())
    }

    @Test fun replacementsAreReevaluatedWithinTimeBudget() {
        val profile = UserProfile(sessionMinutes = 30)
        val request = GeneratePlanRequest(profile)
        val result = generate(profile)
        for (day in result.weeklyPlan.days) for (current in day.exercises) {
            for (replacement in generator.alternatives(current, day, result.weeklyPlan, request, SeedCatalog.exercises, 8)) {
                val updated = day.copy(exercises = day.exercises.map {
                    if (it == current) it.copy(exercise = replacement, restSeconds = replacement.baseRestSeconds) else it
                })
                assertTrue(TrainingLoad.minutes(updated.exercises) <= profile.sessionMinutes)
                val plan = result.weeklyPlan.copy(days = result.weeklyPlan.days.map { if (it.id == day.id) updated else it })
                val assessment = generator.evaluate(plan, profile)
                val actual = TrainingLoad.totals(plan.days.flatMap { it.exercises })
                assessment.muscleCoverage.forEach { assertEquals(kotlin.math.round(actual.getOrDefault(it.group, 0.0) * 10) / 10, it.effectiveSets) }
            }
        }
    }

    private fun Double?.orZero() = this ?: 0.0
}
