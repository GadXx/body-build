package ru.bodybuild.feature.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.bodybuild.core.model.*
import java.util.Locale

internal fun Double.kgLabel(): String = if (isFinite()) java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString().replace('.', ',') else "—"

@Composable
internal fun SessionPulse(session: WorkoutSession) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainer))).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (session.isFinished) "ВАШ РЕЗУЛЬТАТ" else "РИТМ ТРЕНИРОВКИ", style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Text("${session.completedSets.sumOf(CompletedSet::tonnageKg).kgLabel()} кг", style = MaterialTheme.typography.displaySmall)
            Text("записанного тоннажа", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("${session.completedSets.size} подходов", fontWeight = FontWeight.Bold)
                Text("${session.completedSets.sumOf { it.repetitions }} повторений", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** One journal for both a running session and profile history; grouped by occurrence, not exercise ID. */
@Composable
internal fun SetJournal(session: WorkoutSession, onEdit: (CompletedSet) -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        session.completedSets.groupBy { it.exerciseIndex }.toSortedMap().forEach { (_, records) ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(records.first().exerciseName, style = MaterialTheme.typography.titleMedium)
                    Text("${records.sumOf(CompletedSet::tonnageKg).kgLabel()} кг тоннажа", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    records.sortedBy { it.setNumber }.forEach { set ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${set.setNumber}", Modifier.padding(10.dp), style = MaterialTheme.typography.labelLarge)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${set.repetitions} повт. × ${set.effectiveWeightKg().kgLabel()} кг", fontWeight = FontWeight.Bold)
                                if (set.bodyweight) Text("Вес тела в этом подходе", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onEdit(set) }, modifier = Modifier.semantics {
                                contentDescription = "Изменить подход ${set.setNumber}: ${set.exerciseName}"
                            }) { Text("Изменить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditSetDialog(set: CompletedSet, onDismiss: () -> Unit, onSave: suspend (Int, Double) -> Boolean) {
    var reps by rememberSaveable { mutableStateOf(set.repetitions.toString()) }
    var weight by rememberSaveable { mutableStateOf(set.effectiveWeightKg().kgLabel()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repetitions = reps.toIntOrNull()
    val kilograms = weight.replace(',', '.').toDoubleOrNull()
    val validReps = repetitions != null && repetitions in 1..999
    val validWeight = kilograms != null && kilograms.isFinite() && kilograms in (if (set.bodyweight) 25.0..350.0 else 0.0..2000.0)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Исправить подход ${set.setNumber}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(set.exerciseName, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(enabled = !saving && (repetitions ?: 1) > 1, onClick = { reps = ((repetitions ?: 1) - 1).toString() }, contentPadding = PaddingValues(12.dp)) { Text("−1") }
                    OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, enabled = !saving,
                        label = { Text("Повторы") }, isError = !validReps, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    FilledTonalButton(enabled = !saving && (repetitions ?: 0) < 999, onClick = { reps = ((repetitions ?: 0) + 1).toString() }, contentPadding = PaddingValues(12.dp)) { Text("+1") }
                }
                OutlinedTextField(weight, { weight = it.take(8) }, enabled = !saving,
                    label = { Text(if (set.bodyweight) "Вес тела в подходе, кг" else "Вес подхода, кг") }, isError = !validWeight,
                    supportingText = { Text(if (set.bodyweight) "25–350 кг · профиль не изменится" else "0–2000 кг · можно использовать запятую") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text("Тоннаж и прогресс пересчитаются. Время выполнения и отдыха останется прежним.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !saving && validReps && validWeight, onClick = {
                saving = true
                scope.launch {
                    try {
                        if (onSave(repetitions!!, kilograms!!)) onDismiss()
                        else error = "Не удалось сохранить. Закройте редактор и повторите — исходная запись сохранена."
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Не удалось сохранить. Попробуйте ещё раз." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Сохраняем…" else "Сохранить подход") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun VolumeTrend(history: List<WorkoutSession>) {
    val sessions = history.sortedBy { it.startedAtEpochMillis }.takeLast(7)
    if (sessions.isEmpty()) return
    val maximum = sessions.maxOf { it.completedSets.sumOf(CompletedSet::tonnageKg) }.coerceAtLeast(1.0)
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Объём последних тренировок", style = MaterialTheme.typography.titleMedium)
            Text("Тоннаж, кг · разные упражнения не сравнивают силу напрямую", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sessions.forEach { session ->
                val volume = session.completedSets.sumOf(CompletedSet::tonnageKg)
                var entered by remember(session.id) { mutableStateOf(false) }
                LaunchedEffect(session.id) { entered = true }
                val fraction by animateFloatAsState(if (entered) (volume / maximum).toFloat() else 0f, tween(600), label = "tonnage")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(java.text.SimpleDateFormat("dd.MM", Locale.forLanguageTag("ru")).format(java.util.Date(session.startedAtEpochMillis)), style = MaterialTheme.typography.labelMedium)
                    Text(volume.kgLabel(), style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(10.dp))
            }
        }
    }
}
