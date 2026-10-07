package ru.bodybuild.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.bodybuild.core.model.UserProfile
import ru.bodybuild.core.model.WeeklyPlan
import ru.bodybuild.core.model.PreferenceKind
import ru.bodybuild.core.model.WorkoutSession

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val payload: String,
    val catalogVersion: Int,
)

@Entity(tableName = "profile")
data class ProfileEntity(@PrimaryKey val slot: Int = 0, val profile: UserProfile)

@Entity(tableName = "plans")
data class PlanEntity(@PrimaryKey val id: String, val plan: WeeklyPlan, val active: Boolean)

@Entity(tableName = "exercise_preferences")
data class PreferenceEntity(@PrimaryKey val exerciseId: String, val kind: PreferenceKind)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val session: WorkoutSession,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
)
