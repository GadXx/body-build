package ru.bodybuild

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.bodybuild.core.database.LocalProfileRepository
import ru.bodybuild.core.database.LocalTrainingPlanRepository
import ru.bodybuild.core.database.LocalPreferenceRepository
import ru.bodybuild.core.database.LocalWorkoutSessionRepository
import ru.bodybuild.core.model.*
import ru.bodybuild.domain.catalog.ExerciseCatalogRepository
import ru.bodybuild.domain.generator.TrainingPlanGenerator
import ru.bodybuild.domain.generator.ExerciseReplacementEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MainUiState(
    val loading: Boolean = true,
    val generating: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val catalog: List<Exercise> = emptyList(),
    val result: GeneratePlanResult? = null,
    val exercisePreferences: List<ExercisePreference> = emptyList(),
    val savedPlans: List<WeeklyPlan> = emptyList(),
    val workoutHistory: List<WorkoutSession> = emptyList(),
    val activeWorkout: WorkoutSession? = null,
    val error: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val catalogRepository: ExerciseCatalogRepository,
    private val profileRepository: LocalProfileRepository,
    private val planRepository: LocalTrainingPlanRepository,
    private val preferenceRepository: LocalPreferenceRepository,
    private val generator: TrainingPlanGenerator,
    private val replacementEngine: ExerciseReplacementEngine,
    private val workoutRepository: LocalWorkoutSessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    private val workoutMutex = Mutex()
    private val profileMutex = Mutex()
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    fun clearError() { _state.update { it.copy(error = null) } }

    init {
        viewModelScope.launch {
            runCatching {
                val profile = profileRepository.get() ?: UserProfile()
                val catalog = catalogRepository.getAll()
                val active = planRepository.getActive()
                val preferences = preferenceRepository.getAll()
                val savedPlans = planRepository.getAll()
                val workoutHistory = workoutRepository.getHistory()
                val activeWorkout = workoutRepository.getActive()
                val result = active?.let {
                    val assessment = generator.evaluate(it, profile)
                    GeneratePlanResult(it, assessment.muscleCoverage, listOf("Сохранённый план восстановлен локально."), assessment.warnings, assessment.validationReport)
                }
                _state.value = MainUiState(loading = false, profile = profile, catalog = catalog, result = result, exercisePreferences = preferences, savedPlans = savedPlans, workoutHistory = workoutHistory, activeWorkout = activeWorkout)
            }.onFailure { error -> _state.update { it.copy(loading = false, error = error.message ?: "Не удалось открыть локальные данные") } }
        }
    }

    fun updateProfile(profile: UserProfile) {
        if (profile.heightCm !in 100..250 || !profile.weightKg.isFinite() || profile.weightKg !in 25.0..350.0) return
        _state.update { it.copy(profile = profile) }
        viewModelScope.launch {
            runCatching { profileMutex.withLock { profileRepository.save(_state.value.profile) } }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Не удалось сохранить профиль") } }
        }
    }

    fun generate() {
        if (_state.value.generating) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            runCatching {
                val current = _state.value
                profileMutex.withLock { profileRepository.save(_state.value.profile) }
                val generated = withContext(Dispatchers.Default) {
                    generator.generate(GeneratePlanRequest(current.profile, current.exercisePreferences, seed = 42L), current.catalog)
                }
                val now = System.currentTimeMillis()
                val persisted = generated.weeklyPlan.copy(id = "${generated.weeklyPlan.id}-$now", createdAtEpochMillis = now)
                planRepository.saveActive(persisted)
                generated.copy(weeklyPlan = persisted) to planRepository.getAll()
            }.onSuccess { (result, plans) -> _state.update { it.copy(generating = false, result = result, savedPlans = plans) } }
                .onFailure { error -> _state.update { it.copy(generating = false, error = error.message ?: "Не удалось составить программу") } }
        }
    }

    fun setPreference(exerciseId: String, kind: PreferenceKind?) {
        viewModelScope.launch {
            preferenceRepository.set(exerciseId, kind)
            val updated = preferenceRepository.getAll()
            _state.update { it.copy(exercisePreferences = updated) }
        }
    }

    fun alternatives(item: Prescription, day: TrainingDay): List<Exercise> {
        val current = _state.value
        val plan = current.result?.weeklyPlan ?: return emptyList()
        return replacementEngine.alternatives(item, day, plan, GeneratePlanRequest(current.profile, current.exercisePreferences), current.catalog)
    }

    fun replace(dayId: String, exerciseId: String, replacement: Exercise) {
        viewModelScope.launch {
            val current = _state.value
            val oldResult = current.result ?: return@launch
            val updatedDays = oldResult.weeklyPlan.days.map { day ->
                if (day.id != dayId) day else {
                    val items = day.exercises.map { prescription ->
                        if (prescription.exercise.id != exerciseId) prescription else prescription.copy(
                            exercise = replacement,
                            reps = replacement.repRange,
                            restSeconds = replacement.baseRestSeconds,
                            explanation = "Замена сохраняет паттерн движения и основное мышечное покрытие.",
                        )
                    }
                    day.copy(exercises = items, estimatedMinutes = TrainingLoad.minutes(items))
                }
            }
            val plan = oldResult.weeklyPlan.copy(days = updatedDays)
            val assessment = generator.evaluate(plan, current.profile)
            val result = oldResult.copy(weeklyPlan = plan, muscleCoverage = assessment.muscleCoverage, warnings = assessment.warnings, validationReport = assessment.validationReport)
            planRepository.saveActive(plan)
            _state.update { it.copy(result = result) }
        }
    }

    fun selectPlan(plan: WeeklyPlan) {
        viewModelScope.launch {
            planRepository.saveActive(plan)
            val assessment = generator.evaluate(plan, _state.value.profile)
            _state.update { it.copy(result = GeneratePlanResult(plan, assessment.muscleCoverage, listOf("Сохранённая программа выбрана."), assessment.warnings, assessment.validationReport)) }
        }
    }

    fun startWorkout(day: TrainingDay) {
        if (_state.value.activeWorkout?.isFinished == false) return
        viewModelScope.launch { workoutMutex.withLock {
            if (_state.value.activeWorkout?.isFinished == false) return@withLock
            val plan = _state.value.result?.weeklyPlan ?: return@withLock
            val now = System.currentTimeMillis()
            val session = WorkoutSession("workout-$now", plan.id, plan.title, day, now)
            runCatching { workoutRepository.save(session) }
                .onSuccess { _state.update { it.copy(activeWorkout = session, error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Не удалось начать тренировку") } }
        } }
    }

    fun startFreeWorkout() {
        viewModelScope.launch { workoutMutex.withLock {
            if (_state.value.activeWorkout?.isFinished == false) return@withLock
            val now = System.currentTimeMillis()
            val session = WorkoutSession("workout-$now", "free", "Свободная тренировка",
                TrainingDay("free-$now", "Свободная тренировка", "Ваш выбор упражнений", emptyList(), 0), now, mode = WorkoutMode.FREE)
            runCatching { workoutRepository.save(session) }
                .onSuccess { _state.update { it.copy(activeWorkout = session, error = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Не удалось начать тренировку") } }
        } }
    }

    fun addFreeExercise(exercise: Exercise) = editWorkout { it.addFreeExercise(exercise) }
    fun finishFreeWorkout() = editWorkout { it.finishFreeWorkout(System.currentTimeMillis()) }
    fun completeSet(target: PlannedSet, repetitions: Int, weightKg: Double) = editWorkout {
        it.recordSet(target, repetitions, weightKg, _state.value.profile.weightKg, System.currentTimeMillis())
    }

    private fun editWorkout(transform: (WorkoutSession) -> WorkoutSession) {
        viewModelScope.launch { workoutMutex.withLock {
            val session = _state.value.activeWorkout ?: return@withLock
            if (session.isFinished) return@withLock
            val updated = transform(session)
            if (updated == session) return@withLock
            runCatching {
                workoutRepository.save(updated)
                val history = if (updated.isFinished) workoutRepository.getHistory() else _state.value.workoutHistory
                _state.update { it.copy(activeWorkout = updated, workoutHistory = history, error = null) }
            }.onFailure { error -> _state.update { it.copy(error = error.message ?: "Не удалось сохранить тренировку") } }
        } }
    }

    fun skipRest() = changeRest(null)
    fun addRest(seconds: Int) = changeRest((_state.value.activeWorkout?.restEndsAtEpochMillis ?: System.currentTimeMillis()) + seconds * 1_000L)

    private fun changeRest(until: Long?) {
        editWorkout { it.copy(restEndsAtEpochMillis = until) }
    }

    fun skipExercise() {
        editWorkout { it.skipCurrentExercise(System.currentTimeMillis()) }
    }

    fun cancelWorkout() {
        viewModelScope.launch { workoutMutex.withLock {
            val session = _state.value.activeWorkout ?: return@withLock
            if (session.isFinished) return@withLock
            runCatching { workoutRepository.delete(session.id) }
                .onSuccess { _state.update { it.copy(activeWorkout = null) } }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "Не удалось отменить тренировку") } }
        } }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch {
            workoutRepository.delete(id)
            _state.update { it.copy(workoutHistory = workoutRepository.getHistory()) }
        }
    }

    fun deletePlan(id: String) {
        viewModelScope.launch {
            val deletingActive = _state.value.result?.weeklyPlan?.id == id
            planRepository.delete(id)
            val remaining = planRepository.getAll()
            val replacement = if (deletingActive) remaining.firstOrNull() else null
            if (replacement != null) planRepository.saveActive(replacement)
            val result = when {
                !deletingActive -> _state.value.result
                replacement == null -> null
                else -> generator.evaluate(replacement, _state.value.profile).let { assessment ->
                    GeneratePlanResult(replacement, assessment.muscleCoverage, listOf("Сохранённая программа выбрана."), assessment.warnings, assessment.validationReport)
                }
            }
            _state.update { it.copy(savedPlans = remaining, result = result) }
        }
    }
}
