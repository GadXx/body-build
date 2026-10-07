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
}
