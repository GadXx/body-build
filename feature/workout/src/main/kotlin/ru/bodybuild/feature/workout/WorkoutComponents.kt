package ru.bodybuild.feature.workout

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.bodybuild.core.model.*
import ru.bodybuild.feature.exercise.ExerciseCard
import ru.bodybuild.feature.exercise.ExerciseDetailsDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ExercisePicker(catalog: List<Exercise>, onDismiss: () -> Unit, onSelect: (Exercise) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var details by remember { mutableStateOf<Exercise?>(null) }
    val filtered = remember(catalog, query, group, equipment) { catalog.filter { exercise ->
        exercise.matchesSearch(query) && (equipment == null || equipment in exercise.equipment) &&
            (group == null || exercise.stimuli.any { it.region.group == group && it.role == StimulusRole.PRIMARY })
    }.sortedBy { it.name } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Следующее упражнение", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Название: например, EZ или икры") }, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(group == null, { group = null }, { Text("Все мышцы") }) }
                    items(MuscleGroup.entries) { muscle -> FilterChip(group == muscle, { group = muscle }, { Text(muscle.title) }) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(equipment == null, { equipment = null }, { Text("Любое оборудование") }) }
                    items(Equipment.entries) { item -> FilterChip(equipment == item, { equipment = item }, { Text(item.title) }) }
                }
                Text("Найдено: ${filtered.size} · добавляется один подход", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (filtered.isEmpty()) item { Text("Ничего не найдено. Измените поиск или фильтры.", modifier = Modifier.padding(16.dp)) }
                    items(filtered, key = { it.id }) { exercise ->
                        Column {
                            ExerciseCard(exercise, exercise.equipment.joinToString(" · ") { it.title }) { details = exercise }
                            Button(onClick = { onSelect(exercise) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить подход") }
                        }
                    }
                }
            }
        }
    }
    details?.let { ExerciseDetailsDialog(it) { details = null } }
}

@Composable
internal fun EmptyHistoryCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ваша история начинается здесь", style = MaterialTheme.typography.titleLarge)
            Text("Завершите тренировку по плану или в свободном режиме. Подходы, время и прогресс появятся здесь.")
        }
    }
}

@Composable
internal fun WorkoutHistoryCard(session: WorkoutSession, selected: Boolean, expanded: Boolean, onSelect: () -> Unit, onExpand: () -> Unit, onDelete: () -> Unit, onEdit: (CompletedSet) -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox, onValueChange = { onSelect() }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = null)
                Column(Modifier.weight(1f)) {
                    Text(historyDate(session.startedAtEpochMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(session.day.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (session.mode == WorkoutMode.FREE) "Свободный режим" else "По программе · ${session.planTitle}", style = MaterialTheme.typography.bodySmall)
                }
            }
            val minutes = session.elapsedSeconds / 60
            Text("${minutes} мин · ${session.completedSets.size} подходов · ${session.completedSets.map { it.exerciseId }.distinct().size} упражнений", style = MaterialTheme.typography.bodyMedium)
            Text("${String.format(Locale.forLanguageTag("ru"), "%.0f", session.completedSets.sumOf(CompletedSet::tonnageKg))} кг тоннажа", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(session.completedSets.distinctBy { it.exerciseId }.joinToString(" · ") { it.exerciseName }, maxLines = if (expanded) Int.MAX_VALUE else 2, style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "Свернуть" else "Упражнения и подходы ↓") }
            if (expanded) {
                SetJournal(session, onEdit)
                if (session.skippedExerciseIndices.isNotEmpty()) Text("Пропущено упражнений: ${session.skippedExerciseIndices.size}. Невыполненные подходы не учтены.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDelete) { Text("Удалить тренировку", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
internal fun LoadAnalysisDialog(sessions: List<WorkoutSession>, onDismiss: () -> Unit) {
    val load = remember(sessions) { completedMuscleLoad(sessions) }
    val maximum = (load.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 680.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp)) {
                Text("Анализ нагрузки", style = MaterialTheme.typography.headlineSmall)
                Text("Выбрано тренировок: ${sessions.size}", style = MaterialTheme.typography.titleMedium)
                if (sessions.isNotEmpty()) Text("${historyDate(sessions.minOf { it.startedAtEpochMillis })} — ${historyDate(sessions.maxOf { it.startedAtEpochMillis })}", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text("Покрытие выбранных тренировок: выполненные подходы × коэффициент участия мышцы. Косвенная нагрузка учитывается, пропуски — нет. RIR не измеряется. Полосы показывают распределение, а не выполнение недельной нормы.", modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall) }
                    items(MuscleGroup.entries) { group ->
                        val value = load.getValue(group)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(group.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(String.format(Locale.forLanguageTag("ru"), "%.1f", value), style = MaterialTheme.typography.titleSmall)
                            }
                            LinearProgressIndicator(progress = { (value / maximum).toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp))
                            if (value == 0.0) Text("Нет записанной нагрузки", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Закрыть") }
            }
        }
    }
}

@Composable
internal fun MeasurementsDialog(profile: UserProfile, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit) {
    var height by rememberSaveable { mutableStateOf(profile.heightCm.toString()) }
    var weight by rememberSaveable { mutableStateOf(profile.weightKg.toString()) }
    val heightValue = height.toIntOrNull()
    val weightValue = weight.replace(',', '.').toDoubleOrNull()
    val validHeight = heightValue != null && heightValue in 100..250
    val validWeight = weightValue != null && weightValue.isFinite() && weightValue in 25.0..350.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Рост и вес") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("Рост, см") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !validHeight, supportingText = { Text("100–250 см") })
            OutlinedTextField(weight, { weight = it.take(6) }, label = { Text("Вес, кг") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !validWeight, supportingText = { Text("25–350 кг") })
            Text("Новый вес используется для следующих подходов. Вес в уже записанных подходах и история не изменяются.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { Button(enabled = validHeight && validWeight, onClick = { onSave(profile.copy(heightCm = heightValue!!, weightKg = weightValue!!)) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

private fun historyDate(timestamp: Long): String = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(timestamp))
