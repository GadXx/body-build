package ru.bodybuild.feature.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.bodybuild.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    exercises: List<Exercise>,
    preferences: List<ExercisePreference>,
    onPreference: (String, PreferenceKind?) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var selected by remember { mutableStateOf<Exercise?>(null) }
    val filtered = remember(exercises, query, group, equipment) { exercises.filter {
        it.matchesSearch(query) && (equipment == null || equipment in it.equipment) &&
            (group == null || it.stimuli.any { stimulus -> stimulus.region.group == group && stimulus.role == StimulusRole.PRIMARY })
    }.sortedBy { it.name } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Каталог") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Поиск упражнения") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(group == null, { group = null }, { Text("Все мышцы") }) }
                    items(MuscleGroup.entries) { muscle -> FilterChip(group == muscle, { group = muscle }, { Text(muscle.title) }) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(equipment == null, { equipment = null }, { Text("Всё оборудование") }) }
                    items(Equipment.entries) { item -> FilterChip(equipment == item, { equipment = item }, { Text(item.title) }) }
                }
                Spacer(Modifier.height(8.dp))
                Text("${filtered.size} упражнений", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (filtered.isEmpty()) item { Text("Ничего не найдено. Попробуйте другую группу мышц или уберите фильтр оборудования.") }
            items(filtered, key = { it.id }) { exercise ->
                ExerciseCard(exercise, exercise.stimuli.take(2).joinToString(" · ") { it.region.title }) { selected = exercise }
                val kind = preferences.firstOrNull { it.exerciseId == exercise.id }?.kind
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == PreferenceKind.FAVORITE,
                        onClick = { onPreference(exercise.id, if (kind == PreferenceKind.FAVORITE) null else PreferenceKind.FAVORITE) },
                        label = { Text("Любимое") },
                    )
                    FilterChip(
                        selected = kind == PreferenceKind.EXCLUDED,
                        onClick = { onPreference(exercise.id, if (kind == PreferenceKind.EXCLUDED) null else PreferenceKind.EXCLUDED) },
                        label = { Text("Исключить") },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    selected?.let { ExerciseDetailsDialog(it) { selected = null } }
}
