package ru.bodybuild.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import ru.bodybuild.core.designsystem.BodyBuildShapes
import ru.bodybuild.core.model.*
import ru.bodybuild.feature.exercise.ExercisePhotos
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutScreen(
    session: WorkoutSession,
    history: List<WorkoutSession>,
    userWeightKg: Double,
    catalog: List<Exercise>,
    onAddExercise: (Exercise) -> Unit,
    onFinishFreeWorkout: () -> Unit,
    onCompleteSet: (Int, Double) -> Unit,
    onSkipExercise: () -> Unit,
    onCancelWorkout: () -> Unit,
    onSkipRest: () -> Unit,
    onAddRest: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var choosingExercise by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmSkip by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id, session.isFinished) {
        while (!session.isFinished) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val next = session.nextSet()
    val resting = !session.isFinished && (session.restEndsAtEpochMillis ?: 0) > now
    val elapsed = ((session.completedAtEpochMillis ?: now) - session.startedAtEpochMillis).coerceAtLeast(0) / 1000
    val previous = next?.let { previousMatchingSet(history, session, it) }
    val sameWorkoutPrevious = session.completedSets.lastOrNull { it.exerciseId == next?.prescription?.exercise?.id }
    val suggested = previous ?: sameWorkoutPrevious
    var reps by rememberSaveable(session.id, next?.exerciseIndex, next?.setNumber) { mutableStateOf((suggested?.repetitions ?: next?.prescription?.reps?.first)?.toString().orEmpty()) }
    var weight by rememberSaveable(session.id, next?.exerciseIndex, next?.setNumber) {
        mutableStateOf(suggested?.takeUnless { it.bodyweight }?.weightKg?.displayWeight().orEmpty())
    }
    val isBodyweight = Equipment.BODYWEIGHT in (next?.prescription?.exercise?.equipment ?: emptySet())

    Scaffold(bottomBar = {
        if (session.mode == WorkoutMode.FREE && !session.isFinished) Surface(shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (next == null) Button(onClick = { choosingExercise = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Выбрать упражнение") }
                TextButton(onClick = { confirmFinish = true }, enabled = session.completedSets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Завершить и сохранить") }
            }
        }
    }, topBar = {
        Surface(shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(session.day.name, style = MaterialTheme.typography.titleMedium); Text(formatTime(elapsed), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Row { if (!session.isFinished) TextButton(onClick = { confirmCancel = true }) { Text("Отменить") }; TextButton(onClick = onClose) { Text("Закрыть") } }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { WorkoutProgress(session, next) }
            if (session.isFinished) {
                item {
                    Card(shape = BodyBuildShapes.card) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Тренировка завершена", style = MaterialTheme.typography.headlineMedium)
                        Text("${session.completedSets.size} подходов · ${formatTime(elapsed)}${if (session.skippedExerciseIndices.isNotEmpty()) " · пропущено упражнений: ${session.skippedExerciseIndices.size}" else ""}")
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
                    } }
                }
            } else if (resting) {
                item {
                    Card(shape = BodyBuildShapes.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("ОТДЫХ", style = MaterialTheme.typography.labelLarge)
                            Text(formatTime(((session.restEndsAtEpochMillis ?: now) - now + 999) / 1000), style = MaterialTheme.typography.displaySmall)
                            Text(next?.let { "Дальше: ${it.prescription.exercise.name}" } ?: "Восстановитесь и выберите следующий подход")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onAddRest(30) }, modifier = Modifier.weight(1f)) { Text("+30 сек") }
                                Button(onClick = onSkipRest, modifier = Modifier.weight(1f)) { Text("Пропустить") }
                            }
                        }
                    }
                }
            } else if (next != null) {
                item {
                    Card(shape = BodyBuildShapes.card) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Упражнение ${next.exerciseIndex + 1} из ${session.day.exercises.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(next.prescription.exercise.name, style = MaterialTheme.typography.headlineMedium)
                        ExercisePhotos(next.prescription.exercise, Modifier.fillMaxWidth())
                        val ordinal = session.completedSets.count { it.exerciseId == next.prescription.exercise.id } + 1
                        Text(if (session.mode == WorkoutMode.FREE) "Подход $ordinal · запишите ваш результат" else "Подход ${next.setNumber} из ${next.prescription.sets} · цель ${next.prescription.reps.first}–${next.prescription.reps.last} повторений")
                        if (previous != null) Text("Прошлая тренировка, подход $ordinal: ${previous.repetitions} повторений${if (previous.bodyweight) " · вес тела" else " × ${previous.weightKg.displayWeight()} кг"}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        Text("Повторения", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { reps = ((reps.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() }, modifier = Modifier.weight(1f)) { Text("− 1") }
                            OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1.2f))
                            FilledTonalButton(onClick = { reps = ((reps.toIntOrNull() ?: 0) + 1).coerceAtMost(999).toString() }, modifier = Modifier.weight(1f)) { Text("+ 1") }
                        }
                        if (isBodyweight) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text("В расчёте нагрузки автоматически используется вес профиля: ${userWeightKg.displayWeight()} кг", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                        } else {
                            OutlinedTextField(weight, { value -> weight = value.replace(',', '.').filter { it.isDigit() || it == '.' }.take(7) }, label = { Text("Вес, кг") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        Button(
                            onClick = { onCompleteSet(reps.toInt(), weight.toDoubleOrNull() ?: 0.0) },
                            enabled = (reps.toIntOrNull() ?: 0) > 0 && (weight.isBlank() || (weight.toDoubleOrNull()?.let { it >= 0 } == true)),
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                        ) { Text(if (session.mode == WorkoutMode.PLANNED && session.completedSets.size + 1 >= session.actionableSets) "Завершить тренировку" else "Подход выполнен") }
                        OutlinedButton(onClick = { confirmSkip = true }, modifier = Modifier.fillMaxWidth()) { Text("Пропустить упражнение") }
                    } }
                }
            }
            if (session.mode == WorkoutMode.FREE && !session.isFinished && next == null) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = BodyBuildShapes.card) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (session.completedSets.isEmpty()) "С чего начнём?" else "Что дальше?", style = MaterialTheme.typography.titleLarge)
                            Text("Выбирайте упражнения по ходу занятия. Каждый подход сохраняется сразу, а завершение — только по вашей кнопке.", style = MaterialTheme.typography.bodyMedium)
                            session.day.exercises.lastOrNull()?.let { last ->
                                if (session.day.exercises.lastIndex !in session.skippedExerciseIndices) OutlinedButton(onClick = { onAddExercise(last.exercise) }, modifier = Modifier.fillMaxWidth()) { Text("Ещё подход · ${last.exercise.name}") }
                            }
                        }
                    }
                }
            }
            if (session.mode == WorkoutMode.FREE && session.completedSets.isNotEmpty()) item {
                Text("Уже записано", style = MaterialTheme.typography.titleMedium)
                session.completedSets.groupBy { it.exerciseId }.forEach { (_, sets) ->
                    Text("${sets.first().exerciseName} · ${sets.size} подходов", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    if (choosingExercise) ExercisePicker(catalog, onDismiss = { choosingExercise = false }) {
        onAddExercise(it); choosingExercise = false
    }
    if (confirmFinish) AlertDialog(onDismissRequest = { confirmFinish = false }, title = { Text("Сохранить тренировку?") },
        text = { Text("В историю и анализ попадут только ${session.completedSets.size} выполненных подходов. Незаполненные подходы не учитываются.") },
        confirmButton = { Button(onClick = { confirmFinish = false; onFinishFreeWorkout() }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Продолжить занятие") } })
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("Отменить тренировку?") },
        text = { Text("Текущая незавершённая тренировка и введённые в ней подходы будут удалены.") },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Продолжить") } },
        confirmButton = { Button(onClick = { confirmCancel = false; onCancelWorkout(); onClose() }) { Text("Отменить тренировку") } },
    )
    if (confirmSkip) AlertDialog(
        onDismissRequest = { confirmSkip = false },
        title = { Text("Пропустить упражнение?") },
        text = { Text("Оставшиеся подходы этого упражнения не будут засчитаны как выполненные.") },
        dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text("Назад") } },
        confirmButton = { Button(onClick = { confirmSkip = false; onSkipExercise() }) { Text("Пропустить") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutProgress(session: WorkoutSession, next: PlannedSet?) {
    val progress = if (session.actionableSets == 0) if (session.isFinished) 1f else 0f else session.completedSets.size.toFloat() / session.actionableSets
    val currentGroups = next?.takeUnless { session.isFinished }?.prescription?.exercise?.stimuli?.filter { it.role != StimulusRole.STABILIZER && it.coefficient >= .3 }?.map { it.region.group }?.toSet().orEmpty()
    val remainingExercises = session.day.exercises.mapIndexedNotNull { index, p -> p.takeIf { index !in session.skippedExerciseIndices && index >= (next?.exerciseIndex ?: session.day.exercises.size) } }
    val remainingGroups = remainingExercises.flatMap { p -> p.exercise.stimuli.filter { it.role != StimulusRole.STABILIZER && it.coefficient >= .3 }.map { it.region.group } }.toSet()
    val performedGroups = session.completedSets.flatMap { set -> session.day.exercises.getOrNull(set.exerciseIndex)?.exercise?.stimuli.orEmpty().filter { it.role != StimulusRole.STABILIZER && it.coefficient >= .3 }.map { it.region.group } }.toSet()
    val skippedGroups = session.skippedExerciseIndices.flatMap { index -> session.day.exercises.getOrNull(index)?.exercise?.stimuli.orEmpty().filter { it.role != StimulusRole.STABILIZER && it.coefficient >= .3 }.map { it.region.group } }.toSet()
    val allGroups = session.day.exercises.flatMap { it.exercise.stimuli.filter { s -> s.role != StimulusRole.STABILIZER && s.coefficient >= .3 }.map { s -> s.region.group } }.distinct()
    Card(shape = BodyBuildShapes.card) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (session.mode == WorkoutMode.FREE) "Нагрузка сейчас" else "Прогресс", fontWeight = FontWeight.Bold); Text(if (session.mode == WorkoutMode.FREE) "${session.completedSets.size} подходов" else "${session.completedSets.size} / ${session.actionableSets} подходов") }
        if (session.mode == WorkoutMode.PLANNED) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allGroups.forEach { group ->
                val (label, color) = when {
                    group in currentGroups -> "Сейчас" to MaterialTheme.colorScheme.primary
                    group in skippedGroups && group in performedGroups && group !in remainingGroups -> "Частично" to MaterialTheme.colorScheme.errorContainer
                    group in performedGroups && (group !in remainingGroups || session.isFinished) -> "Выполнено" to MaterialTheme.colorScheme.tertiary
                    group in skippedGroups && group !in performedGroups && group !in remainingGroups -> "Пропущено" to MaterialTheme.colorScheme.errorContainer
                    else -> (if (session.isFinished) "Не выполнено" else "Впереди") to MaterialTheme.colorScheme.surfaceVariant
                }
                Surface(color = color, shape = MaterialTheme.shapes.small) { Text("${group.title} · $label", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall, color = if (group in currentGroups) MaterialTheme.colorScheme.onPrimary else Color.Unspecified) }
            }
        }
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    plans: List<WeeklyPlan>,
    activePlanId: String?,
    history: List<WorkoutSession>,
    onSelectPlan: (WeeklyPlan) -> Unit,
    onDeletePlan: (String) -> Unit,
    onDeleteWorkout: (String) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expandedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var planToDelete by remember { mutableStateOf<WeeklyPlan?>(null) }
    var workoutToDelete by remember { mutableStateOf<WorkoutSession?>(null) }
    var progressPeriod by remember { mutableStateOf(ProgressPeriod.LAST) }
    var selectedProgression by remember { mutableStateOf<ProgressionItem?>(null) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showAnalysis by rememberSaveable { mutableStateOf(false) }
    var editMeasurements by rememberSaveable { mutableStateOf(false) }
    val selected = history.filter { it.id in selectedIds }
    val orderedHistory = remember(history) { history.sortedByDescending { it.startedAtEpochMillis } }
    LaunchedEffect(history) { selectedIds = selectedIds.filter { id -> history.any { it.id == id } } }
    Scaffold(
        topBar = { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Мой профиль", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("На главную") }
        } },
        bottomBar = {
            if (tab == 0 && selected.isNotEmpty()) Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedIds = emptyList() }) { Text("Снять") }
                    Button(onClick = { showAnalysis = true }, modifier = Modifier.weight(1f)) { Text("Анализ нагрузки · ${selected.size}") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = BodyBuildShapes.card) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Мои параметры", style = MaterialTheme.typography.labelLarge)
                            Text("${profile.heightCm} см · ${profile.weightKg.displayWeight()} кг", style = MaterialTheme.typography.titleLarge)
                        }
                        TextButton(onClick = { editMeasurements = true }) { Text("Изменить") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Тренировок", history.size.toString(), Modifier.weight(1f))
                    StatCard("Подходов", history.sumOf { it.completedSets.size }.toString(), Modifier.weight(1f))
                    StatCard("Тоннаж, кг", String.format(Locale.US, "%.0f", history.flatMap { it.completedSets }.sumOf(CompletedSet::tonnageKg)), Modifier.weight(1f))
                }
            }
            item {
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("История", "Прогресс", "Программы").forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                    }
                }
            }
            item { Text("Всего времени: ${formatTime(history.sumOf { it.elapsedSeconds })} · в тоннаж включён вес тела для соответствующих упражнений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when (tab) {
                0 -> {
                    item { Text("Отметьте занятия галочками, чтобы сравнить нагрузку на мышцы за выбранные тренировки.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (history.isEmpty()) item { EmptyHistoryCard() }
                    else item {
                        TextButton(onClick = { selectedIds = if (selected.size == history.size) emptyList() else history.map { it.id } }) {
                            Text(if (selected.size == history.size) "Снять все" else "Выбрать все · ${history.size}")
                        }
                    }
                    items(orderedHistory, key = { it.id }) { session ->
                        WorkoutHistoryCard(session, session.id in selectedIds, expandedSessionId == session.id,
                            onSelect = { selectedIds = if (session.id in selectedIds) selectedIds - session.id else selectedIds + session.id },
                            onExpand = { expandedSessionId = if (expandedSessionId == session.id) null else session.id },
                            onDelete = { workoutToDelete = session })
                    }
                }
                1 -> {
                    item { Text("Вес и повторения объединены в условный силовой индекс лучшего подхода. Нажмите на упражнение, чтобы увидеть график.", style = MaterialTheme.typography.bodyMedium) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProgressPeriod.entries.forEach { period -> FilterChip(selected = progressPeriod == period, onClick = { progressPeriod = period }, label = { Text(period.title) }) }
                        }
                    }
                    val progressItems = progression(history, progressPeriod)
                    if (progressItems.isEmpty()) item { Text("Здесь появятся результаты записанных подходов.") }
                    items(progressItems, key = { it.exerciseId }) { item -> ProgressionCard(item) { selectedProgression = item } }
                }
                2 -> {
                    if (plans.isEmpty()) item { Text("Сохранённых программ пока нет. Свободные тренировки доступны без программы.") }
                    items(plans, key = { it.id }) { plan ->
                        Card(Modifier.fillMaxWidth(), shape = BodyBuildShapes.card) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(plan.title, style = MaterialTheme.typography.titleMedium)
                                Text("${plan.days.size} тренировок · ${date(plan.createdAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onSelectPlan(plan) }) { Text(if (plan.id == activePlanId) "Открыть" else "Выбрать") }
                                    TextButton(onClick = { planToDelete = plan }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (editMeasurements) MeasurementsDialog(profile, { editMeasurements = false }) { onUpdateProfile(it); editMeasurements = false }
    if (showAnalysis) LoadAnalysisDialog(selected) { showAnalysis = false }
    planToDelete?.let { plan -> ConfirmDelete("Удалить программу?", "Программа «${plan.title}» будет удалена. Записи выполненных тренировок останутся.", { planToDelete = null }, { planToDelete = null; onDeletePlan(plan.id) }) }
    workoutToDelete?.let { workout -> ConfirmDelete("Удалить тренировку?", "Записи подходов, время и тоннаж этой тренировки будут удалены из статистики.", { workoutToDelete = null }, { workoutToDelete = null; onDeleteWorkout(workout.id) }) }
    selectedProgression?.let { item -> ProgressionDialog(item, progressPeriod) { selectedProgression = null } }
}
internal enum class ProgressPeriod(val title: String, val durationMillis: Long?) {
    LAST("Последняя", null), WEEK("Неделя", 7L * 24 * 60 * 60 * 1_000), MONTH("Месяц", 30L * 24 * 60 * 60 * 1_000),
}

internal data class ProgressPoint(val timestamp: Long, val score: Double, val repetitions: Int, val weightKg: Double, val bodyweight: Boolean)
internal data class ProgressionItem(val exerciseId: String, val name: String, val points: List<ProgressPoint>) {
    val current get() = points.last().score
    val previous get() = points.firstOrNull()?.score?.takeIf { points.size > 1 }
}

internal fun progression(history: List<WorkoutSession>, period: ProgressPeriod, now: Long = System.currentTimeMillis()): List<ProgressionItem> =
    history.flatMap { it.completedSets }.distinctBy { it.exerciseId }.mapNotNull { sample ->
        val allPoints = history.sortedBy { it.startedAtEpochMillis }.mapNotNull { workout ->
            workout.completedSets.filter { it.exerciseId == sample.exerciseId }.maxByOrNull(CompletedSet::performanceIndex)?.let {
                ProgressPoint(workout.startedAtEpochMillis, it.performanceIndex(), it.repetitions, it.effectiveWeightKg(), it.bodyweight)
            }
        }
        val points = if (period.durationMillis == null) allPoints.takeLast(2) else {
            val cutoff = now - period.durationMillis
            val inside = allPoints.filter { it.timestamp >= cutoff }
            if (inside.isEmpty()) emptyList() else listOfNotNull(allPoints.lastOrNull { it.timestamp < cutoff }?.copy(timestamp = cutoff)) + inside
        }
        points.lastOrNull()?.let { ProgressionItem(sample.exerciseId, sample.exerciseName, points) }
    }.sortedByDescending { item -> item.previous?.let { item.current / it } ?: 1.0 }

@Composable
private fun ProgressionCard(item: ProgressionItem, onClick: () -> Unit) {
    val delta = item.previous?.takeIf { it > 0 }?.let { (item.current / it - 1) * 100 }
    val color = when { delta == null -> MaterialTheme.colorScheme.onSurfaceVariant; delta >= 0 -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.error }
    Card(shape = BodyBuildShapes.card, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.Bold); Text("Силовой индекс лучшего подхода", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column { Text(String.format(Locale.US, "%.1f", item.current), fontWeight = FontWeight.Bold); Text(delta?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "нет сравнения", color = color, style = MaterialTheme.typography.bodySmall); Text("Открыть график ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    } }
}

@Composable
private fun ProgressionDialog(item: ProgressionItem, period: ProgressPeriod, onDismiss: () -> Unit) {
    val delta = item.previous?.takeIf { it > 0 }?.let { (item.current / it - 1) * 100 }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = BodyBuildShapes.card, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleLarge); Text("Динамика · ${period.title.lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Текущий индекс", String.format(Locale.US, "%.1f", item.current), Modifier.weight(1f))
                    StatCard("Изменение", delta?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—", Modifier.weight(1f))
                }
                ProgressChart(item.points)
                val latest = item.points.last()
                Text("Последний лучший подход: ${latest.repetitions} × ${latest.weightKg.displayWeight()} кг${if (latest.bodyweight) " (вес тела)" else ""}", style = MaterialTheme.typography.bodySmall)
                if (item.points.size > 1) Text("${shortDate(item.points.first().timestamp)} — ${shortDate(item.points.last().timestamp)} · точек: ${item.points.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgressChart(points: List<ProgressPoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val pointCenter = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val maxScore = points.maxOfOrNull { it.score } ?: 1.0
        val minScore = points.minOfOrNull { it.score } ?: 0.0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(String.format(Locale.US, "%.1f", maxScore), style = MaterialTheme.typography.bodySmall); Text("силовой индекс", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Canvas(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium).background(surface).padding(12.dp)) {
            repeat(5) { line ->
                val y = size.height * line / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val range = max(maxScore - minScore, maxScore * .08).coerceAtLeast(1.0)
            val low = minScore - range * .08
            val firstTime = points.firstOrNull()?.timestamp ?: 0L
            val lastTime = points.lastOrNull()?.timestamp ?: firstTime
            fun x(point: ProgressPoint, index: Int): Float = if (lastTime == firstTime) {
                if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1).toFloat()
            } else size.width * (point.timestamp - firstTime).toFloat() / (lastTime - firstTime).toFloat()
            fun y(point: ProgressPoint): Float = size.height - ((point.score - low) / (range * 1.16)).toFloat() * size.height
            val linePath = Path()
            points.forEachIndexed { index, point -> if (index == 0) linePath.moveTo(x(point, index), y(point)) else linePath.lineTo(x(point, index), y(point)) }
            if (points.size > 1) {
                val fillPath = Path().apply { addPath(linePath); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(fillPath, Brush.verticalGradient(listOf(primary.copy(alpha = .28f), Color.Transparent)))
                drawPath(linePath, primary, style = Stroke(width = 3.dp.toPx()))
            }
            points.forEachIndexed { index, point ->
                drawCircle(primary, 6.dp.toPx(), Offset(x(point, index), y(point)))
                drawCircle(pointCenter, 2.5.dp.toPx(), Offset(x(point, index), y(point)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(points.firstOrNull()?.let { shortDate(it.timestamp) }.orEmpty(), style = MaterialTheme.typography.bodySmall); Text(points.lastOrNull()?.let { shortDate(it.timestamp) }.orEmpty(), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ConfirmDelete(title: String, message: String, dismiss: () -> Unit, confirm: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) },
    dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } },
    confirmButton = { Button(onClick = confirm) { Text("Удалить") } },
)

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) = Card(modifier) { Column(Modifier.padding(12.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall) } }
private fun formatTime(seconds: Long) = "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
private fun date(epoch: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("ru")).format(Date(epoch))
private fun shortDate(epoch: Long) = java.text.SimpleDateFormat("dd.MM", Locale.forLanguageTag("ru")).format(Date(epoch))
private fun Double.displayWeight() = if (this % 1.0 == 0.0) toInt().toString() else toString()
