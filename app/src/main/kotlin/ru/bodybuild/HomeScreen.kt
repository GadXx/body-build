package ru.bodybuild

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(state: MainUiState, onPlan: () -> Unit, onFree: () -> Unit, onResume: () -> Unit,
    onSetup: () -> Unit, onProfile: () -> Unit, onCatalog: () -> Unit) {
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("BODY BUILD", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Тренируйтесь\nв своём ритме", style = MaterialTheme.typography.displaySmall)
                Text("План на сегодня или ваш выбор по ходу занятия.", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (state.activeWorkout?.isFinished == false) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Занятие уже идёт", style = MaterialTheme.typography.titleLarge)
                        Text("${state.activeWorkout.day.name} · ${state.activeWorkout.completedSets.size} подходов записано")
                        Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Продолжить тренировку") }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Свободная тренировка", style = MaterialTheme.typography.headlineSmall)
                        Text("Выбирайте упражнения и добавляйте подходы прямо в зале. Всё попадёт в историю и прогресс.")
                        Button(onClick = onFree, enabled = state.activeWorkout?.isFinished != false, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Начать без плана") }
                    }
                }
            }
            item {
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Тренировка по плану", style = MaterialTheme.typography.headlineSmall)
                        Text(state.result?.weeklyPlan?.title ?: "Составьте программу под ваше время, оборудование и цели.")
                        OutlinedButton(onClick = if (state.result != null) onPlan else onSetup, modifier = Modifier.fillMaxWidth()) { Text(if (state.result != null) "Открыть программу" else "Составить программу") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onProfile, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Профиль и история") }
                    FilledTonalButton(onClick = onCatalog, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Каталог · ${state.catalog.size}") }
                }
            }
            item { Text("Все записи хранятся на устройстве. Интернет для тренировки не нужен.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)) }
        }
    }
}
