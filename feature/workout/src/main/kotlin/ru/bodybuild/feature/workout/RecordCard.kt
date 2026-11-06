package ru.bodybuild.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bodybuild.core.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecordCard(records: ExerciseRecords, onOpenSession: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.background(Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha = .45f), colors.surfaceContainer)))
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(records.name, style = MaterialTheme.typography.titleLarge)
            Text("ЛИЧНЫЕ РЕКОРДЫ · ${records.totalSets} подходов в истории", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            records.maximumWeight?.let { result ->
                RecordRow("Максимальный вес", "${result.set.weightKg.kgLabel()} кг", result, onOpenSession)
                HorizontalDivider()
            }
            RecordRow("Больше всего повторений", "${records.maximumRepetitions.set.repetitions} повт.", records.maximumRepetitions, onOpenSession)
            HorizontalDivider()
            RecordRow("Лучший силовой индекс", String.format(Locale.forLanguageTag("ru"), "%.1f", records.bestPerformance.set.performanceIndex()), records.bestPerformance, onOpenSession)
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String, result: RecordResult, onOpenSession: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("${result.set.repetitions} повт. × ${result.set.effectiveWeightKg().kgLabel()} кг${if (result.set.bodyweight) " · вес тела" else ""}", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onOpenSession(result.sessionId) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("${SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(result.set.completedAtEpochMillis))} · К тренировке →")
        }
    }
}
