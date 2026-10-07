package ru.bodybuild.domain.generator

import kotlin.math.*
import ru.bodybuild.core.model.*

/**
 * Bounded deterministic marginal-gain search across the entire week.
 * Each step adds two sets of a new exercise or one set to an existing exercise.
 * The objective is the reduction in normalized squared weekly deficits.
 */
class DeterministicTrainingPlanGenerator(
    private val policy: VolumePolicy = VolumePolicy(),
) : TrainingPlanGenerator, ExerciseReplacementEngine {
    override fun generate(request: GeneratePlanRequest, catalog: List<Exercise>): GeneratePlanResult {
        val profile = request.profile
        require(profile.daysPerWeek in 1..6 && profile.sessionMinutes in 30..120)
        val available = catalog.filter { allowed(it, request) }.sortedBy { it.id }
        val focuses = focuses(profile)
        val days = List(profile.daysPerWeek) { mutableListOf<Prescription>() }
        val targets = MuscleGroup.entries.associateWith { policy.target(profile, it) }
        val coefficients = available.associate { it.id to TrainingLoad.coefficients(it) }
        val favorites = request.exercisePreferences.filter { it.kind == PreferenceKind.FAVORITE }.map { it.exerciseId }.toSet()
        val rir = when (profile.experience) {
            ExperienceLevel.BEGINNER -> 3
            ExperienceLevel.INTERMEDIATE -> 2
            ExperienceLevel.ADVANCED -> 1
        }

        // At most 6 days * 10 exercises * 4 sets. Every accepted step adds at least one set.
        for (iteration in 0 until 240) {
            val totals = TrainingLoad.totals(days.flatten())
            val daySeconds = days.map { TrainingLoad.seconds(it) }
            val localTotals = days.map { TrainingLoad.totals(it) }
            val regionTotals = days.flatten().flatMap { p ->
                p.exercise.stimuli.filter { it.role != StimulusRole.STABILIZER }.map { s ->
                    s.region to p.sets * TrainingLoad.effort(p.rir) * s.coefficient
                }
            }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            var best: Move? = null
            for (dayIndex in days.indices) {
                val current = days[dayIndex]
                for (exercise in available) {
                    val coeff = coefficients.getValue(exercise.id)
                    val primary = exercise.stimuli.filter { it.role == StimulusRole.PRIMARY }.maxByOrNull { it.coefficient }?.region?.group ?: continue
                    if (primary !in focuses[dayIndex].groups) continue
                    val existing = current.indexOfFirst { it.exercise.id == exercise.id }
                    if (existing < 0 && current.size >= policy.maxExercisesPerDay) continue
                    if (existing >= 0 && current[existing].sets >= policy.maxSetsPerExercise) continue
                    val addedSets = if (existing < 0) 2 else 1
                    val delta = coeff.mapValues { it.value * addedSets * TrainingLoad.effort(rir) }
                    if (delta.any { (g, v) -> totals.getOrDefault(g, 0.0) + v > targets.getValue(g) + policy.maximumExtraSets }) continue
                    // Spread concentrated work over available sessions instead of exhausting one day.
                    if (delta.any { (g, v) ->
                        val exposures = focuses.count { g in it.groups }.coerceAtLeast(1)
                        val localCap = max(4.0, targets.getValue(g) / exposures + 2.0)
                        localTotals[dayIndex].getOrDefault(g, 0.0) + v > localCap
                    }) continue
                    val prescription = if (existing >= 0) current[existing].copy(sets = current[existing].sets + 1)
                        else Prescription(exercise, 2, exercise.repRange, rir, exercise.baseRestSeconds, "")
                    val updated = current.toMutableList().apply {
                        if (existing >= 0) set(existing, prescription) else add(prescription)
                    }
                    val seconds = TrainingLoad.seconds(updated)
                    if (seconds > profile.sessionMinutes * 60) continue
                    var benefit = delta.entries.sumOf { (g, value) ->
                        val target = targets.getValue(g)
                        val before = max(0.0, 1.0 - totals.getOrDefault(g, 0.0) / target)
                        val after = max(0.0, 1.0 - (totals.getOrDefault(g, 0.0) + value) / target)
                        val weight = if (g in profile.priorityMuscles) 1.6 else 1.0
                        weight * (before * before - after * after)
                    }
                    // Preserve useful regional coverage: compound pressing must not hide missing side delts.
                    benefit += exercise.stimuli.filter { it.region in regionalFloors && it.role != StimulusRole.STABILIZER }.sumOf {
                        val target = targets.getValue(it.region.group) * 0.25
                        val before = max(0.0, 1.0 - regionTotals.getOrDefault(it.region, 0.0) / target)
                        val after = max(0.0, 1.0 - (regionTotals.getOrDefault(it.region, 0.0) + addedSets * TrainingLoad.effort(rir) * it.coefficient) / target)
                        before * before - after * after
                    }
                    if (benefit <= 0.0001) continue
                    // Anatomy refines equivalent choices but cannot inflate the weekly group volume.
                    val regionBonus = exercise.stimuli.filter { it.role == StimulusRole.PRIMARY }.map {
                        1.0 / (1.0 + regionTotals.getOrDefault(it.region, 0.0))
                    }.average().takeIf { it.isFinite() } ?: 0.0
                    benefit *= 1.0 + 0.12 * regionBonus
                    if (exercise.id in favorites) benefit *= 1.08
                    if (existing < 0 && current.any { it.exercise.pattern == exercise.pattern }) benefit *= 0.75
                    if (existing < 0 && days.any { d -> d.any { it.exercise.id == exercise.id } }) benefit *= 0.93
                    val extraTime = (seconds - daySeconds[dayIndex]).coerceAtLeast(1)
                    // Global fairness: long days become less attractive as other days stay short.
                    val balance = 1.0 + 5.0 * daySeconds[dayIndex] / (profile.sessionMinutes * 60.0)
                    val score = benefit / sqrt(extraTime.toDouble()) / balance
                    if (best == null || score > best.score + 1e-12) best = Move(dayIndex, updated, score)
                }
            }
            val move = best ?: break
            days[move.day].clear()
            days[move.day].addAll(move.items)
        }
        val trainingDays = days.mapIndexed { index, items ->
            val ordered = items.sortedWith(compareBy<Prescription> {
                if (it.exercise.pattern in compoundPatterns) 0 else 1
            }.thenByDescending { p ->
                TrainingLoad.coefficients(p.exercise).filterKeys { it in profile.priorityMuscles }.values.sum()
            }.thenBy { it.exercise.id }).map { p ->
                val primary = p.exercise.stimuli.filter { it.role == StimulusRole.PRIMARY }.maxByOrNull { it.coefficient }?.region?.group
                p.copy(explanation = if (primary in profile.priorityMuscles)
                    "Дополнительный объём для приоритета: ${primary?.title}. Учтены подходы в других упражнениях."
                else "Дополняет недельную нагрузку: ${primary?.title ?: p.exercise.pattern.title}. Объём ограничен временем и балансом недели.")
            }
            TrainingDay("day-${index + 1}", "Тренировка ${('A'.code + index).toChar()}", focuses[index].title, ordered, TrainingLoad.minutes(ordered))
        }
        val plan = WeeklyPlan("plan-${request.seed}-${profile.hashCode()}", "${if (focuses.all { it.groups.size == MuscleGroup.entries.size }) "Full body" else "Сплит"} · ${profile.daysPerWeek} дня", trainingDays, 0L)
        val assessment = evaluate(plan, profile)
        return GeneratePlanResult(plan, assessment.muscleCoverage,
            listOf("Недельный дефицит мышц определяет подбор и число подходов.",
                "Время включает 5 минут разминки, выполнение, отдых между подходами и переходы.",
                "Приоритеты повышают целевой объём; лишние подходы ради заполнения времени не добавляются."),
            assessment.warnings, assessment.validationReport)
    }

    override fun evaluate(plan: WeeklyPlan, profile: UserProfile): PlanAssessment {
        val totals = TrainingLoad.totals(plan.days.flatMap { it.exercises })
        val coverage = MuscleGroup.entries.map {
            val target = policy.target(profile, it)
            MuscleCoverage(it, round(totals.getOrDefault(it, 0.0) * 10) / 10, target, target + policy.maximumExtraSets)
        }
        val issues = buildList {
            if (plan.days.size != profile.daysPerWeek) add(ValidationIssue(ValidationIssue.Severity.ERROR, "Количество тренировок не совпадает с настройками"))
            coverage.forEach {
                if (it.effectiveSets + 0.25 < it.targetMin) add(ValidationIssue(ValidationIssue.Severity.WARNING, "${it.group.title}: ${it.effectiveSets} — ниже недельной цели", it.group))
                if (it.effectiveSets > it.targetMax + 0.05) add(ValidationIssue(ValidationIssue.Severity.WARNING, "${it.group.title}: превышен целевой объём", it.group))
            }
            regionalFloors.forEach { region ->
                val volume = plan.days.flatMap { it.exercises }.sumOf { p ->
                    p.sets * TrainingLoad.effort(p.rir) * (p.exercise.stimuli
                        .filter { it.region == region && it.role != StimulusRole.STABILIZER }
                        .maxOfOrNull { it.coefficient } ?: 0.0)
                }
                if (volume + 0.05 < policy.target(profile, region.group) * 0.25) {
                    add(ValidationIssue(ValidationIssue.Severity.WARNING, "${region.title}: недостаточное покрытие отдела", region.group))
                }
            }
            plan.days.forEach {
                if (it.exercises.isEmpty()) add(ValidationIssue(ValidationIssue.Severity.ERROR, "${it.name}: нет доступных упражнений"))
                if (TrainingLoad.minutes(it.exercises) > profile.sessionMinutes) add(ValidationIssue(ValidationIssue.Severity.ERROR, "${it.name}: превышен лимит времени"))
            }
        }
        val warnings = issues.map { it.message }.toMutableList()
        if (profile.painNotes.isNotBlank()) warnings += "Отмечена боль или ограничение. Согласуйте нагрузку со специалистом."
        return PlanAssessment(coverage, warnings, ValidationReport(issues.none { it.severity == ValidationIssue.Severity.ERROR }, issues))
    }

    override fun alternatives(current: Prescription, day: TrainingDay, plan: WeeklyPlan, request: GeneratePlanRequest, catalog: List<Exercise>, limit: Int): List<Exercise> {
        val original = TrainingLoad.coefficients(current.exercise)
        val primary = original.maxByOrNull { it.value }?.key
        return catalog.filter { candidate ->
            candidate.id != current.exercise.id && allowed(candidate, request) &&
                day.exercises.none { it.exercise.id == candidate.id } &&
                candidate.pattern == current.exercise.pattern &&
                TrainingLoad.coefficients(candidate).getOrDefault(primary, 0.0) >= 0.5 &&
                TrainingLoad.minutes(day.exercises.map {
                    if (it == current) it.copy(exercise = candidate, restSeconds = candidate.baseRestSeconds) else it
                }) <= request.profile.sessionMinutes
        }.sortedWith(compareBy<Exercise> { candidate ->
            val candidateCoefficients = TrainingLoad.coefficients(candidate)
            MuscleGroup.entries.sumOf { abs(original.getOrDefault(it, 0.0) - candidateCoefficients.getOrDefault(it, 0.0)) }
        }.thenBy { it.id }).take(limit.coerceAtLeast(0))
    }

    private fun allowed(e: Exercise, request: GeneratePlanRequest) =
        e.catalogVersion <= request.catalogVersion &&
            e.equipment.all(request.profile.equipment::contains) &&
            e.minimumLevel.ordinal <= request.profile.experience.ordinal &&
            request.exercisePreferences.none { it.exerciseId == e.id && it.kind == PreferenceKind.EXCLUDED }

    private data class Move(val day: Int, val items: List<Prescription>, val score: Double)
    private data class Focus(val title: String, val groups: Set<MuscleGroup>)

    private fun focuses(p: UserProfile): List<Focus> {
        val all = Focus("Всё тело", MuscleGroup.entries.toSet())
        if (p.splitPreference == SplitPreference.FULL_BODY || p.daysPerWeek == 1 ||
            (p.splitPreference == SplitPreference.AUTO && p.daysPerWeek <= 3)) return List(p.daysPerWeek) { all }
        val lowerGroups = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
        val upper = Focus("Верх тела", MuscleGroup.entries.toSet() - lowerGroups)
        val lower = Focus("Ноги · кор", lowerGroups + MuscleGroup.CORE)
        // Accessories can go on shorter sessions; leg day does not also own all shoulder work.
        val chest = Focus("Грудь · трицепс · плечи", setOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS, MuscleGroup.CORE, MuscleGroup.CALVES))
        val back = Focus("Спина · бицепс · плечи", setOf(MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.SHOULDERS, MuscleGroup.FOREARMS, MuscleGroup.CALVES))
        val legs = Focus("Ноги · кор", lowerGroups + MuscleGroup.CORE)
        return when (p.daysPerWeek) {
            2 -> listOf(upper, lower)
            3 -> listOf(chest, back, legs)
            4 -> listOf(upper, lower, upper, lower)
            5 -> listOf(chest, back, legs, upper, lower)
            else -> listOf(chest, back, legs, chest, back, legs)
        }
    }

    private val compoundPatterns = setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH,
        MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL, MovementPattern.SQUAT,
        MovementPattern.HIP_HINGE, MovementPattern.LUNGE)

    private val regionalFloors = setOf(MuscleRegion.UPPER_CHEST, MuscleRegion.MID_CHEST,
        MuscleRegion.LATS, MuscleRegion.UPPER_BACK, MuscleRegion.SIDE_DELTS, MuscleRegion.REAR_DELTS)
}
