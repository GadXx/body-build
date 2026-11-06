package ru.bodybuild

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import ru.bodybuild.core.designsystem.BodyBuildTheme
import ru.bodybuild.core.model.*
import ru.bodybuild.feature.workout.ProfileScreen
import ru.bodybuild.feature.workout.WorkoutScreen

/** Run on a connected emulator/phone with :app:connectedDebugAndroidTest. */
class FreeWorkoutUiTest {
    @get:Rule val compose = createComposeRule()
    private val exercise = Exercise("ez-curl", "Сгибание с EZ-грифом", "", emptyList(), MovementPattern.ELBOW_FLEXION,
        setOf(Equipment.EZ_BAR), stimuli = listOf(MuscleStimulus(MuscleRegion.BICEPS_LONG, 1.0, StimulusRole.PRIMARY)))
    private fun empty() = WorkoutSession("test", "free", "Свободная тренировка",
        TrainingDay("d", "Свободная тренировка", "", emptyList(), 0), System.currentTimeMillis(), mode = WorkoutMode.FREE)

    @Test fun freeWorkoutCanChooseExerciseWithoutPlan() {
        compose.setContent {
            var session by remember { mutableStateOf(empty()) }
            BodyBuildTheme { WorkoutScreen(session, emptyList(), 75.0, listOf(exercise),
                onAddExercise = { session = session.addFreeExercise(it) }, onFinishFreeWorkout = {},
                onCompleteSet = { _, _ -> }, onSkipExercise = {}, onCancelWorkout = {}, onSkipRest = {}, onAddRest = {}, onClose = {}) }
        }
        compose.onNodeWithText("+ Выбрать упражнение").performClick()
        compose.onNodeWithText("Добавить подход").performClick()
        compose.onNodeWithText(exercise.name).assertExists()
        compose.onNodeWithText("Подход выполнен").performScrollTo().assertIsDisplayed()
    }

    @Test fun historySelectionOpensLoadAnalysis() {
        val active = empty().addFreeExercise(exercise)
        val done = active.recordSet(active.nextSet()!!, 10, 20.0, 75.0, System.currentTimeMillis()).finishFreeWorkout(System.currentTimeMillis())
        compose.setContent { BodyBuildTheme { ProfileScreen(UserProfile(), emptyList(), null, listOf(done), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Выбрать все · 1").performScrollTo().performClick()
        compose.onNodeWithText("Анализ нагрузки · 1").performClick()
        compose.onNodeWithText("Выбрано тренировок: 1").assertIsDisplayed()
        compose.onNodeWithText("Бицепс").performScrollTo().assertIsDisplayed()
    }

    @Test fun profileMeasurementsCanBeEdited() {
        var saved = UserProfile()
        compose.setContent { BodyBuildTheme { ProfileScreen(saved, emptyList(), null, emptyList(), {}, {}, {}, { saved = it }, {}) } }
        compose.onNodeWithText("Изменить").performClick()
        compose.onNodeWithText("Рост, см").performTextReplacement("182")
        compose.onNodeWithText("Вес, кг").performTextReplacement("81,5")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals(182, saved.heightCm); assertEquals(81.5, saved.weightKg, .001) }
    }

    @Test fun currentPlannedWorkoutShowsAndEditsRecordedSet() {
        val initial = empty().copy(mode = WorkoutMode.PLANNED,
            day = empty().day.copy(exercises = listOf(Prescription(exercise, 3, exercise.repRange, 2, 90, ""))))
        var session by mutableStateOf(initial.recordSet(initial.nextSet()!!, 10, 20.0, 75.0, System.currentTimeMillis()))
        val rest = session.restEndsAtEpochMillis
        compose.setContent {
            BodyBuildTheme { WorkoutScreen(session, emptyList(), 75.0, listOf(exercise),
                onAddExercise = {}, onFinishFreeWorkout = {}, onCompleteSet = { _, _ -> },
                onSkipExercise = {}, onCancelWorkout = {}, onSkipRest = {}, onAddRest = {}, onClose = {},
                onEditSet = { set, reps, weight -> session = session.editCompletedSet(set, reps, weight); true }) }
        }
        compose.onNodeWithText("10 повт. × 20 кг").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Изменить подход 1: ${exercise.name}").performScrollTo().performClick()
        compose.onNodeWithText("Повторы").performTextReplacement("12")
        compose.onNodeWithText("Вес подхода, кг").performTextReplacement("22,5")
        compose.onNodeWithText("Сохранить подход").performClick()
        compose.onNodeWithText("12 повт. × 22,5 кг").assertExists()
        compose.runOnIdle {
            assertEquals(rest, session.restEndsAtEpochMillis)
            assertEquals(270.0, session.completedSets.single().tonnageKg(), .001)
        }
    }

    @Test fun savedHistorySupportsEditingAndCancellation() {
        val active = empty().addFreeExercise(exercise)
        var session by mutableStateOf(active.recordSet(active.nextSet()!!, 10, 20.0, 75.0, System.currentTimeMillis()).finishFreeWorkout(System.currentTimeMillis()))
        compose.setContent {
            BodyBuildTheme { ProfileScreen(UserProfile(), emptyList(), null, listOf(session), {}, {}, {}, {}, {},
                onEditSet = { _, set, reps, weight -> session = session.editCompletedSet(set, reps, weight); true }) }
        }
        compose.onNodeWithText("Упражнения и подходы ↓").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Изменить подход 1: ${exercise.name}").performScrollTo().performClick()
        compose.onNodeWithText("Повторы").performTextReplacement("0")
        compose.onNodeWithText("Сохранить подход").assertIsNotEnabled()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertEquals(10, session.completedSets.single().repetitions) }
        compose.onNodeWithContentDescription("Изменить подход 1: ${exercise.name}").performClick()
        compose.onNodeWithText("Вес подхода, кг").performTextReplacement("30")
        compose.onNodeWithText("Сохранить подход").performClick()
        compose.runOnIdle {
            assertEquals(300.0, session.completedSets.single().tonnageKg(), .001)
            assertEquals(true, session.isFinished)
        }
    }
}
