package ru.bodybuild.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.bodybuild.core.database.*
import ru.bodybuild.domain.catalog.ExerciseCatalogRepository
import ru.bodybuild.domain.generator.DeterministicTrainingPlanGenerator
import ru.bodybuild.domain.generator.ExerciseReplacementEngine
import ru.bodybuild.domain.generator.TrainingPlanGenerator
import ru.bodybuild.domain.validation.DefaultPlanValidator
import ru.bodybuild.domain.validation.PlanValidator

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): BodyBuildDatabase =
        Room.databaseBuilder(context, BodyBuildDatabase::class.java, "body-build.db").addMigrations(MIGRATION_1_2).build()

    @Provides @Singleton fun catalogDatabase(db: BodyBuildDatabase) = RoomExerciseCatalogRepository(db.exerciseDao())
    @Provides @Singleton fun catalogRepository(repository: RoomExerciseCatalogRepository): ExerciseCatalogRepository = repository
    @Provides @Singleton fun profileRepository(db: BodyBuildDatabase) = LocalProfileRepository(db.profileDao())
    @Provides @Singleton fun planRepository(db: BodyBuildDatabase) = LocalTrainingPlanRepository(db.planDao())
    @Provides @Singleton fun preferenceRepository(db: BodyBuildDatabase) = LocalPreferenceRepository(db.preferenceDao())
    @Provides @Singleton fun workoutRepository(db: BodyBuildDatabase) = LocalWorkoutSessionRepository(db.workoutSessionDao())

    @Provides @Singleton
    fun generator(): DeterministicTrainingPlanGenerator = DeterministicTrainingPlanGenerator()
    @Provides fun generatorContract(value: DeterministicTrainingPlanGenerator): TrainingPlanGenerator = value
    @Provides fun replacementContract(value: DeterministicTrainingPlanGenerator): ExerciseReplacementEngine = value
    @Provides fun validator(): PlanValidator = DefaultPlanValidator()
}
