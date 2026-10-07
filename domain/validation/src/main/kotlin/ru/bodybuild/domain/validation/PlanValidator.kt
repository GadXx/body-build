package ru.bodybuild.domain.validation

import ru.bodybuild.core.model.TrainingLoad
import ru.bodybuild.core.model.UserProfile
import ru.bodybuild.core.model.ValidationIssue
import ru.bodybuild.core.model.ValidationReport
import ru.bodybuild.core.model.WeeklyPlan

interface PlanValidator {
    fun validate(plan: WeeklyPlan, profile: UserProfile): ValidationReport
}

class DefaultPlanValidator : PlanValidator {
    override fun validate(plan: WeeklyPlan, profile: UserProfile): ValidationReport {
        val issues = buildList {
            if (plan.days.size != profile.daysPerWeek) add(ValidationIssue(ValidationIssue.Severity.ERROR, "Количество тренировок не совпадает с профилем"))
            plan.days.filter { it.exercises.isEmpty() }.forEach { add(ValidationIssue(ValidationIssue.Severity.ERROR, "${it.name}: нет доступных упражнений")) }
            plan.days.filter { TrainingLoad.minutes(it.exercises) > profile.sessionMinutes }.forEach { add(ValidationIssue(ValidationIssue.Severity.ERROR, "${it.name}: превышен лимит времени")) }
            plan.days.flatMap { it.exercises }.filter { it.sets !in 1..6 }.forEach { add(ValidationIssue(ValidationIssue.Severity.ERROR, "Некорректное число подходов: ${it.exercise.name}")) }
        }
        return ValidationReport(issues.none { it.severity == ValidationIssue.Severity.ERROR }, issues)
    }
}
