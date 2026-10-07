package ru.bodybuild.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name") fun observeAll(): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM exercises ORDER BY name") suspend fun getAll(): List<ExerciseEntity>
    @Query("SELECT * FROM exercises WHERE id = :id") suspend fun findById(id: String): ExerciseEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ExerciseEntity>)
    @Query("SELECT COUNT(*) FROM exercises") suspend fun count(): Int
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE slot = 0") fun observe(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profile WHERE slot = 0") suspend fun get(): ProfileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(profile: ProfileEntity)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE active = 1 LIMIT 1") fun observeActive(): Flow<PlanEntity?>
    @Query("SELECT * FROM plans WHERE active = 1 LIMIT 1") suspend fun getActive(): PlanEntity?
    @Query("SELECT * FROM plans") suspend fun getAll(): List<PlanEntity>
    @Query("UPDATE plans SET active = 0") suspend fun deactivateAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(plan: PlanEntity)
    @Query("DELETE FROM plans WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE completedAtEpochMillis IS NULL ORDER BY startedAtEpochMillis DESC LIMIT 1") suspend fun getActive(): WorkoutSessionEntity?
    @Query("SELECT * FROM workout_sessions WHERE completedAtEpochMillis IS NOT NULL ORDER BY completedAtEpochMillis DESC") suspend fun getHistory(): List<WorkoutSessionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: WorkoutSessionEntity)
    @Query("DELETE FROM workout_sessions WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM exercise_preferences") fun observeAll(): Flow<List<PreferenceEntity>>
    @Query("SELECT * FROM exercise_preferences") suspend fun getAll(): List<PreferenceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: PreferenceEntity)
    @Query("DELETE FROM exercise_preferences WHERE exerciseId = :exerciseId") suspend fun delete(exerciseId: String)
}
