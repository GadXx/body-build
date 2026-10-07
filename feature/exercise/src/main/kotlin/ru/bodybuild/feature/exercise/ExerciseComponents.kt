package ru.bodybuild.feature.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bodybuild.core.designsystem.BodyBuildShapes
import ru.bodybuild.core.model.Exercise

@Composable
fun ExerciseCard(exercise: Exercise, subtitle: String, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = BodyBuildShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ExercisePhotoPreview(exercise, Modifier.width(104.dp).height(76.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun ExerciseDetailsDialog(exercise: Exercise, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
        title = { Text(exercise.name) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExercisePhotos(exercise, Modifier.fillMaxWidth())
                Text("Основная нагрузка", fontWeight = FontWeight.Bold)
                exercise.stimuli.sortedByDescending { it.coefficient }.forEach {
                    Text("${it.region.title}: ${(it.coefficient * 100).toInt()} баллов")
                }
                exercise.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Оценки относительные и не являются медицинским измерением.", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
