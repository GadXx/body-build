package ru.bodybuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import ru.bodybuild.core.designsystem.BodyBuildTheme
import ru.bodybuild.feature.exercise.CatalogScreen
import ru.bodybuild.feature.onboarding.OnboardingScreen
import ru.bodybuild.feature.plan.PlanScreen
import ru.bodybuild.feature.workout.ProfileScreen
import ru.bodybuild.feature.workout.WorkoutScreen
import ru.bodybuild.core.model.nextSet

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BodyBuildTheme { BodyBuildApp() } }
    }
}

@Composable
private fun BodyBuildApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var waitingForPlan by rememberSaveable { mutableStateOf(false) }
    val home: () -> Unit = { navController.navigate("home") { popUpTo("home") { inclusive = true }; launchSingleTop = true } }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.error != null && state.catalog.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error ?: "Ошибка", color = MaterialTheme.colorScheme.error) }
        return
    }

    LaunchedEffect(state.generating, state.result) {
        if (waitingForPlan && !state.generating) {
            waitingForPlan = false
            if (state.result != null && state.error == null) navController.navigate("plan") { popUpTo("home"); launchSingleTop = true }
        }
    }
    LaunchedEffect(state.activeWorkout?.id) {
        if (state.activeWorkout?.isFinished == false) navController.navigate("workout") { launchSingleTop = true }
    }

    state.error?.let { error -> AlertDialog(onDismissRequest = viewModel::clearError,
        title = { Text("Не удалось выполнить действие") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text("Понятно") } }) }

    NavHost(navController, startDestination = "home") {
        composable("home") {
            HomeScreen(state, onPlan = { navController.navigate("plan") }, onFree = viewModel::startFreeWorkout,
                onResume = { navController.navigate("workout") }, onSetup = { navController.navigate("onboarding") },
                onProfile = { navController.navigate("profile") }, onCatalog = { navController.navigate("catalog") })
        }
        composable("onboarding") {
            OnboardingScreen(
                profile = state.profile,
                onProfileChange = viewModel::updateProfile,
                onGenerate = { waitingForPlan = true; viewModel.generate() },
                generating = state.generating,
                onBack = home,
            )
        }
        composable("plan") {
            state.result?.let { result ->
                PlanScreen(
                    result = result,
                    onEditProfile = { navController.navigate("onboarding") },
                    onHome = home,
                    onOpenCatalog = { navController.navigate("catalog") },
                    onRegenerate = viewModel::generate,
                    onStartWorkout = { day ->
                        if (state.activeWorkout?.isFinished == false) navController.navigate("workout") else viewModel.startWorkout(day)
                    },
                    onOpenProfile = { navController.navigate("profile") },
                    hasActiveWorkout = state.activeWorkout?.isFinished == false,
                    onAlternatives = viewModel::alternatives,
                    onReplace = viewModel::replace,
                )
            } ?: LaunchedEffect(Unit) { home() }
        }
        composable("workout") {
            state.activeWorkout?.let { session ->
                WorkoutScreen(
                    session = session,
                    history = state.workoutHistory,
                    userWeightKg = state.profile.weightKg,
                    catalog = state.catalog,
                    onAddExercise = viewModel::addFreeExercise,
                    onFinishFreeWorkout = viewModel::finishFreeWorkout,
                    onCompleteSet = { reps, weight -> session.nextSet()?.let { viewModel.completeSet(it, reps, weight) } },
                    onSkipExercise = viewModel::skipExercise,
                    onCancelWorkout = viewModel::cancelWorkout,
                    onSkipRest = viewModel::skipRest,
                    onAddRest = viewModel::addRest,
                    onClose = home,
                )
            } ?: LaunchedEffect(Unit) { navController.popBackStack() }
        }
        composable("profile") {
            ProfileScreen(
                profile = state.profile,
                plans = state.savedPlans,
                activePlanId = state.result?.weeklyPlan?.id,
                history = state.workoutHistory,
                onSelectPlan = { viewModel.selectPlan(it); navController.navigate("plan") { launchSingleTop = true } },
                onDeletePlan = viewModel::deletePlan,
                onDeleteWorkout = viewModel::deleteWorkout,
                onUpdateProfile = viewModel::updateProfile,
                onBack = home,
            )
        }
        composable("catalog") {
            CatalogScreen(
                exercises = state.catalog,
                preferences = state.exercisePreferences,
                onPreference = viewModel::setPreference,
                onBack = navController::popBackStack,
            )
        }
    }
}
