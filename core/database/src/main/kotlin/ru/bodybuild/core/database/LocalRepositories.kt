package ru.bodybuild.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ru.bodybuild.core.model.*
import ru.bodybuild.domain.catalog.ExerciseCatalogRepository
import ru.bodybuild.domain.catalog.SeedCatalog

class RoomExerciseCatalogRepository(private val dao: ExerciseDao) : ExerciseCatalogRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun ensureSeeded() {
        val existing = dao.getAll().associateBy { it.id }
        val changed = SeedCatalog.exercises.filter {
            (existing[it.id]?.catalogVersion ?: 0) < it.catalogVersion
        }
        if (changed.isNotEmpty()) dao.insertAll(changed.map(::toEntity))
    }

    override fun observeAll(): Flow<List<Exercise>> = dao.observeAll().map { rows -> rows.map(::fromEntity) }
    override suspend fun getAll(): List<Exercise> { ensureSeeded(); return dao.getAll().map(::fromEntity) }
    override suspend fun findById(id: String): Exercise? { ensureSeeded(); return dao.findById(id)?.let(::fromEntity) }
    override suspend fun search(query: String, muscle: MuscleGroup?, equipment: Set<Equipment>): List<Exercise> {
        val normalized = query.trim().lowercase()
        return getAll().filter { exercise ->
            exercise.matchesSearch(normalized) &&
                (muscle == null || exercise.stimuli.any { it.region.group == muscle }) &&
                (equipment.isEmpty() || exercise.equipment.all(equipment::contains))
        }
    }
    private fun toEntity(value: Exercise) = ExerciseEntity(value.id, value.name, value.name.lowercase(), json.encodeToString(value), value.catalogVersion)
    private fun fromEntity(value: ExerciseEntity): Exercise = json.decodeFromString(value.payload)
}

class LocalProfileRepository(private val dao: ProfileDao) {
    fun observe(): Flow<UserProfile?> = dao.observe().map { it?.profile }
    suspend fun get(): UserProfile? = dao.get()?.profile
    suspend fun save(profile: UserProfile) = dao.save(ProfileEntity(profile = profile))
}

class LocalTrainingPlanRepository(private val dao: PlanDao) {
    fun observeActive(): Flow<WeeklyPlan?> = dao.observeActive().map { it?.plan }
    suspend fun getActive(): WeeklyPlan? = dao.getActive()?.plan
    suspend fun getAll(): List<WeeklyPlan> = dao.getAll().map { it.plan }.sortedByDescending { it.createdAtEpochMillis }
    suspend fun saveActive(plan: WeeklyPlan) { dao.deactivateAll(); dao.save(PlanEntity(plan.id, plan, true)) }
    suspend fun delete(id: String) = dao.delete(id)
}

class LocalWorkoutSessionRepository(private val dao: WorkoutSessionDao) {
    suspend fun getActive(): WorkoutSession? = dao.getActive()?.session
    suspend fun getHistory(): List<WorkoutSession> = dao.getHistory().map { it.session }
    suspend fun save(session: WorkoutSession) = dao.save(WorkoutSessionEntity(session.id, session, session.startedAtEpochMillis, session.completedAtEpochMillis))
    suspend fun delete(id: String) = dao.delete(id)
}

class LocalPreferenceRepository(private val dao: PreferenceDao) {
    fun observeAll(): Flow<List<ExercisePreference>> = dao.observeAll().map { rows -> rows.map { ExercisePreference(it.exerciseId, it.kind) } }
    suspend fun getAll(): List<ExercisePreference> = dao.getAll().map { ExercisePreference(it.exerciseId, it.kind) }
    suspend fun set(exerciseId: String, kind: PreferenceKind?) {
        if (kind == null) dao.delete(exerciseId) else dao.save(PreferenceEntity(exerciseId, kind))
    }
}
