package ru.bodybuild.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import ru.bodybuild.core.model.UserProfile
import ru.bodybuild.core.model.WeeklyPlan
import ru.bodybuild.core.model.WorkoutSession
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExerciseEntity::class, ProfileEntity::class, PlanEntity::class, PreferenceEntity::class, WorkoutSessionEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(DatabaseConverters::class)
abstract class BodyBuildDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun profileDao(): ProfileDao
    abstract fun planDao(): PlanDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}

class DatabaseConverters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @TypeConverter fun profileToJson(value: UserProfile): String = json.encodeToString(value)
    @TypeConverter fun jsonToProfile(value: String): UserProfile = json.decodeFromString(value)
    @TypeConverter fun planToJson(value: WeeklyPlan): String = json.encodeToString(value)
    @TypeConverter fun jsonToPlan(value: String): WeeklyPlan = json.decodeFromString(value)
    @TypeConverter fun sessionToJson(value: WorkoutSession): String = json.encodeToString(value)
    @TypeConverter fun jsonToSession(value: String): WorkoutSession = json.decodeFromString(value)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_sessions (id TEXT NOT NULL, session TEXT NOT NULL, startedAtEpochMillis INTEGER NOT NULL, completedAtEpochMillis INTEGER, PRIMARY KEY(id))")
    }
}
