package ru.bodybuild.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bodybuild.core.designsystem.BodyBuildShapes
import ru.bodybuild.core.model.*
import ru.bodybuild.feature.exercise.ExerciseCard
import ru.bodybuild.feature.exercise.ExerciseDetailsDialog

@Composable
fun PlanScreen(
    result: GeneratePlanResult,
    onEditProfile: () -> Unit,
    onHome: () -> Unit,
    onOpenCatalog: () -> Unit,
    onRegenerate: () -> Unit,
    onStartWorkout: (TrainingDay) -> Unit,
    onOpenProfile: () -> Unit,
    hasActiveWorkout: Boolean,
    onAlternatives: (Prescription, TrainingDay) -> List<Exercise>,
    onReplace: (String, String, Exercise) -> Unit,
) {
    var selectedDay by remember(result.weeklyPlan.id) { mutableIntStateOf(0) }
    var details by remember { mutableStateOf<Exercise?>(null) }
    var replacementTarget by remember { mutableStateOf<Pair<TrainingDay, Prescription>?>(null) }
    val day = result.weeklyPlan.days.getOrNull(selectedDay)
    Scaffold(
        topBar = {
            Surface {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("ТВОЙ ПЛАН", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(result.weeklyPlan.title, style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = onHome) { Text("Главная") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenProfile) { Text("История и профиль") }
                    TextButton(onClick = onEditProfile) { Text("Настроить программу") }
                }
            }
            item {
                PrimaryScrollableTabRow(selectedTabIndex = selectedDay, edgePadding = 16.dp, divider = {}) {
                    result.weeklyPlan.days.forEachIndexed { index, trainingDay ->
                        Tab(selected = selectedDay == index, onClick = { selectedDay = index }, text = { Text(trainingDay.name) })
                    }
                }
            }
            day?.let { current ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(current.focus, style = MaterialTheme.typography.headlineMedium)
                        Text("${current.estimatedMinutes} мин · ${current.exercises.sumOf { it.sets }} рабочих подходов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onStartWorkout(current) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (hasActiveWorkout) "Продолжить тренировку" else "Начать тренировку") }
                    }
                }
                current.exercises.forEachIndexed { index, item ->
                    item(key = "${current.id}-${item.exercise.id}-$index") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            ExerciseCard(
                                item.exercise,
                                "${item.sets} × ${item.reps.first}–${item.reps.last} · RIR ${item.rir} · отдых ${item.restSeconds / 60}:${(item.restSeconds % 60).toString().padStart(2, '0')}",
                            ) { details = item.exercise }
                        }
                        Text(item.explanation, modifier = Modifier.padding(horizontal = 32.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { replacementTarget = current to item }, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Заменить упражнение") }
                    }
                }
            }
            if (result.warnings.isNotEmpty()) item { WarningCard(result.warnings) }
            item { CoverageCard(result.muscleCoverage) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.weight(1f)) { Text("Каталог") }
                    Button(onClick = onRegenerate, modifier = Modifier.weight(1f)) { Text("Пересобрать") }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    details?.let { ExerciseDetailsDialog(it) { details = null } }
    replacementTarget?.let { (targetDay, targetItem) ->
        val alternatives = remember(targetDay.id, targetItem.exercise.id) { onAlternatives(targetItem, targetDay) }
        AlertDialog(
            onDismissRequest = { replacementTarget = null },
            confirmButton = { TextButton(onClick = { replacementTarget = null }) { Text("Отмена") } },
            title = { Text("Заменить ${targetItem.exercise.name}") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (alternatives.isEmpty()) Text("Подходящих замен для выбранного оборудования нет.")
                    alternatives.forEach { alternative ->
                        ExerciseCard(alternative, alternative.stimuli.take(2).joinToString(" · ") { it.region.title }) {
                            onReplace(targetDay.id, targetItem.exercise.id, alternative)
                            replacementTarget = null
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun WarningCard(warnings: List<String>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Обрати внимание", fontWeight = FontWeight.Bold)
            warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CoverageCard(coverage: List<MuscleCoverage>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = BodyBuildShapes.card) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Покрытие за неделю", style = MaterialTheme.typography.titleLarge)
            Text("Условные эффективные подходы: учитывают косвенную нагрузку. Это не число выполненных подходов. Справа — целевой диапазон.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            coverage.forEach { item ->
                val fraction = (item.effectiveSets / item.targetMax).toFloat().coerceIn(0f, 1f)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.group.title, style = MaterialTheme.typography.bodySmall)
                        Text(String.format(java.util.Locale.forLanguageTag("ru"), "%.1f / %.1f–%.1f", item.effectiveSets, item.targetMin, item.targetMax), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (item.effectiveSets + 0.25 >= item.targetMin && item.effectiveSets <= item.targetMax + 0.05) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error))
                    }
                }
            }
        }
    }
}
