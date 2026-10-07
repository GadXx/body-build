package ru.bodybuild.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExperienceLevel(val title: String) {
    BEGINNER("Новичок"), INTERMEDIATE("Средний"), ADVANCED("Продвинутый")
}

@Serializable
enum class SplitPreference(val title: String) {
    FULL_BODY("Full body"), SPLIT("Сплит"), AUTO("Не важно")
}

@Serializable
enum class TrainingGoal { HYPERTROPHY, MAINTENANCE }

@Serializable
enum class Equipment(val title: String) {
    BODYWEIGHT("Собственный вес"), DUMBBELLS("Гантели"), BARBELL("Штанга"),
    BENCH("Скамья"), CABLE("Блочный тренажёр"), MACHINE("Тренажёры"),
    PULL_UP_BAR("Турник"), DIP_BARS("Брусья"), EZ_BAR("EZ-гриф")
}

@Serializable
enum class MuscleGroup(val title: String) {
    CHEST("Грудь"), BACK("Спина"), SHOULDERS("Плечи"), BICEPS("Бицепс"),
    TRICEPS("Трицепс"), QUADRICEPS("Квадрицепс"), HAMSTRINGS("Задняя поверхность бедра"),
    GLUTES("Ягодицы"), CALVES("Икры"), CORE("Кор"), FOREARMS("Предплечья")
}

@Serializable
enum class MuscleRegion(val title: String, val group: MuscleGroup) {
    UPPER_CHEST("Верх груди", MuscleGroup.CHEST),
    MID_CHEST("Середина груди", MuscleGroup.CHEST),
    LOWER_CHEST("Низ груди", MuscleGroup.CHEST),
    LATS("Широчайшие", MuscleGroup.BACK),
    UPPER_BACK("Верх спины", MuscleGroup.BACK),
    SPINAL_ERECTORS("Разгибатели позвоночника", MuscleGroup.BACK),
    FRONT_DELTS("Передняя дельта", MuscleGroup.SHOULDERS),
    SIDE_DELTS("Средняя дельта", MuscleGroup.SHOULDERS),
    REAR_DELTS("Задняя дельта", MuscleGroup.SHOULDERS),
    BICEPS_LONG("Длинная головка бицепса", MuscleGroup.BICEPS),
    BICEPS_SHORT("Короткая головка бицепса", MuscleGroup.BICEPS),
    BRACHIALIS("Брахиалис", MuscleGroup.BICEPS),
    TRICEPS_LONG("Длинная головка трицепса", MuscleGroup.TRICEPS),
    TRICEPS_LATERAL("Латеральная головка трицепса", MuscleGroup.TRICEPS),
    TRICEPS_MEDIAL("Медиальная головка трицепса", MuscleGroup.TRICEPS),
    QUADS("Квадрицепс", MuscleGroup.QUADRICEPS),
    HAMSTRINGS("Бицепс бедра", MuscleGroup.HAMSTRINGS),
    GLUTE_MAX("Большая ягодичная", MuscleGroup.GLUTES),
    GLUTE_MED("Средняя ягодичная", MuscleGroup.GLUTES),
    CALVES("Икроножная и камбаловидная", MuscleGroup.CALVES),
    ABS("Прямая мышца живота", MuscleGroup.CORE),
    OBLIQUES("Косые мышцы", MuscleGroup.CORE),
    BRACHIORADIALIS("Плечелучевая", MuscleGroup.FOREARMS),
    WRIST_FLEXORS("Сгибатели кисти", MuscleGroup.FOREARMS)
}

@Serializable
enum class MovementPattern(val title: String) {
    HORIZONTAL_PUSH("Горизонтальный жим"), VERTICAL_PUSH("Вертикальный жим"),
    HORIZONTAL_PULL("Горизонтальная тяга"), VERTICAL_PULL("Вертикальная тяга"),
    SQUAT("Приседание"), HIP_HINGE("Тазовый наклон"), LUNGE("Выпад"),
    ELBOW_FLEXION("Сгибание локтя"), ELBOW_EXTENSION("Разгибание локтя"),
    SHOULDER_ABDUCTION("Отведение плеча"), KNEE_FLEXION("Сгибание колена"),
    KNEE_EXTENSION("Разгибание колена"), CALF_RAISE("Подъём на носки"), CORE("Кор"),
    CHEST_FLY("Сведение рук"), REAR_DELT("Отведение рук назад"),
    HIP_EXTENSION("Разгибание бедра"), HIP_ABDUCTION("Отведение бедра"), WRIST_CURL("Сгибание кисти")
}

@Serializable enum class StimulusRole { PRIMARY, SECONDARY, STABILIZER }
@Serializable enum class LengthBias { SHORTENED, MID_RANGE, LENGTHENED }
@Serializable enum class Confidence { LOW, MEDIUM, HIGH }

@Serializable
data class MuscleStimulus(
    val region: MuscleRegion,
    val coefficient: Double,
    val role: StimulusRole,
    val lengthBias: LengthBias = LengthBias.MID_RANGE,
    val confidence: Confidence = Confidence.MEDIUM,
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val description: String,
    val technique: List<String>,
    val pattern: MovementPattern,
    val equipment: Set<Equipment>,
    val minimumLevel: ExperienceLevel = ExperienceLevel.BEGINNER,
    val unilateral: Boolean = false,
    val setupComplexity: Int = 1,
    val repRange: IntRangeSurrogate = IntRangeSurrogate(6, 15),
    val baseRestSeconds: Int = 90,
    val setDurationSeconds: Int = 40,
    val warnings: List<String> = emptyList(),
    val stimuli: List<MuscleStimulus>,
    val catalogVersion: Int = 1,
    val reviewed: Boolean = false,
)

@Serializable data class IntRangeSurrogate(val first: Int, val last: Int)
@Serializable enum class PreferenceKind { FAVORITE, ALLOWED, EXCLUDED }
@Serializable data class ExercisePreference(val exerciseId: String, val kind: PreferenceKind)

@Serializable
data class UserProfile(
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val experience: ExperienceLevel = ExperienceLevel.BEGINNER,
    val daysPerWeek: Int = 3,
    val sessionMinutes: Int = 60,
    val splitPreference: SplitPreference = SplitPreference.AUTO,
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    val equipment: Set<Equipment> = Equipment.entries.toSet(),
    val priorityMuscles: Set<MuscleGroup> = emptySet(),
    val maintenanceMuscles: Set<MuscleGroup> = emptySet(),
    val painNotes: String = "",
)

@Serializable
data class GeneratePlanRequest(
    val profile: UserProfile,
    val exercisePreferences: List<ExercisePreference> = emptyList(),
    val catalogVersion: Int = 3,
    val seed: Long = 42L,
)

@Serializable
data class Prescription(
    val exercise: Exercise,
    val sets: Int,
    val reps: IntRangeSurrogate,
    val rir: Int,
    val restSeconds: Int,
    val explanation: String,
)

@Serializable
data class TrainingDay(
    val id: String,
    val name: String,
    val focus: String,
    val exercises: List<Prescription>,
    val estimatedMinutes: Int,
)

@Serializable
data class MuscleCoverage(
    val group: MuscleGroup,
    val effectiveSets: Double,
    val targetMin: Double,
    val targetMax: Double,
)

@Serializable
data class ValidationIssue(
    val severity: Severity,
    val message: String,
    val muscle: MuscleGroup? = null,
) {
    @Serializable enum class Severity { INFO, WARNING, ERROR }
}

@Serializable data class ValidationReport(val isValid: Boolean, val issues: List<ValidationIssue>)

@Serializable
data class WeeklyPlan(
    val id: String,
    val title: String,
    val days: List<TrainingDay>,
    val createdAtEpochMillis: Long,
)

@Serializable
data class GeneratePlanResult(
    val weeklyPlan: WeeklyPlan,
    val muscleCoverage: List<MuscleCoverage>,
    val explanations: List<String>,
    val warnings: List<String>,
    val validationReport: ValidationReport,
)

@Serializable
data class PlanAssessment(
    val muscleCoverage: List<MuscleCoverage>,
    val warnings: List<String>,
    val validationReport: ValidationReport,
)

@Serializable
data class CompletedSet(
    val exerciseId: String,
    val exerciseName: String,
    val exerciseIndex: Int,
    val setNumber: Int,
    val repetitions: Int,
    val weightKg: Double,
    val completedAtEpochMillis: Long,
    val bodyweight: Boolean = false,
    val userBodyWeightKg: Double = 0.0,
)

@Serializable
enum class WorkoutMode { PLANNED, FREE }

@Serializable
data class WorkoutSession(
    val id: String,
    val planId: String,
    val planTitle: String,
    val day: TrainingDay,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val completedSets: List<CompletedSet> = emptyList(),
    val restEndsAtEpochMillis: Long? = null,
    val skippedExerciseIndices: Set<Int> = emptySet(),
    val mode: WorkoutMode = WorkoutMode.PLANNED,
) {
    val totalSets: Int get() = day.exercises.sumOf { it.sets }
    val skippedSets: Int get() = skippedExerciseIndices.sumOf { index ->
        val planned = day.exercises.getOrNull(index)?.sets ?: 0
        planned - completedSets.count { it.exerciseIndex == index }
    }
    val actionableSets: Int get() = (totalSets - skippedSets).coerceAtLeast(0)
    val isFinished: Boolean get() = completedAtEpochMillis != null
    val elapsedSeconds: Long get() = ((completedAtEpochMillis ?: System.currentTimeMillis()) - startedAtEpochMillis).coerceAtLeast(0) / 1000
}

data class PlannedSet(
    val prescription: Prescription,
    val exerciseIndex: Int,
    val setNumber: Int,
)

fun WorkoutSession.nextSet(): PlannedSet? {
    val all = day.exercises.flatMapIndexed { exerciseIndex, prescription ->
        if (exerciseIndex in skippedExerciseIndices) emptyList()
        else (1..prescription.sets).map { PlannedSet(prescription, exerciseIndex, it) }
    }
    return all.firstOrNull { planned -> completedSets.none { it.exerciseIndex == planned.exerciseIndex && it.setNumber == planned.setNumber } }
}

fun CompletedSet.effectiveWeightKg(): Double = if (bodyweight) userBodyWeightKg else weightKg
fun CompletedSet.tonnageKg(): Double = effectiveWeightKg() * repetitions
fun CompletedSet.performanceIndex(): Double = effectiveWeightKg() * (1.0 + repetitions / 30.0)
