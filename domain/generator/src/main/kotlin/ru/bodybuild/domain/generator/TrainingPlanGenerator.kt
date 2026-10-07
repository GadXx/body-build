package ru.bodybuild.domain.generator

import ru.bodybuild.core.model.Exercise
import ru.bodybuild.core.model.GeneratePlanRequest
import ru.bodybuild.core.model.GeneratePlanResult
import ru.bodybuild.core.model.Prescription
import ru.bodybuild.core.model.TrainingDay
import ru.bodybuild.core.model.WeeklyPlan

interface TrainingPlanGenerator {
    fun generate(request: GeneratePlanRequest, catalog: List<Exercise>): GeneratePlanResult
    fun evaluate(plan: WeeklyPlan, profile: ru.bodybuild.core.model.UserProfile): ru.bodybuild.core.model.PlanAssessment
}

interface ExerciseReplacementEngine {
    fun alternatives(
        current: Prescription,
        day: TrainingDay,
        plan: WeeklyPlan,
        request: GeneratePlanRequest,
        catalog: List<Exercise>,
        limit: Int = 8,
    ): List<Exercise>
}
