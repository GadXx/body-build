package ru.bodybuild.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import ru.bodybuild.core.model.*
import ru.bodybuild.feature.preferences.MusclePriorityPicker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onGenerate: () -> Unit,
    generating: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { Row(Modifier.fillMaxWidth().statusBarsPadding()) { TextButton(onClick = onBack) { Text("На главную") } } },
        bottomBar = {
            Surface(shadowElevation = 12.dp) {
                Button(
                    onClick = onGenerate,
                    enabled = !generating && profile.equipment.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(54.dp),
                ) { Text(if (generating) "Составляем программу…" else "Составить мою программу") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("BODY BUILD", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Тренировки, собранные под тебя", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("Ответь на несколько вопросов. Любое упражнение можно будет заменить позже.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SectionTitle("Твой опыт", "Это влияет на сложность, RIR и объём")
                ChoiceRow(ExperienceLevel.entries, profile.experience, { it.title }) { onProfileChange(profile.copy(experience = it)) }
            }
            item {
                SectionTitle("Рост и вес", "Вес используется для статистики упражнений с собственным весом")
                Text("Рост: ${profile.heightCm} см", fontWeight = FontWeight.Bold)
                Slider(profile.heightCm.toFloat(), { onProfileChange(profile.copy(heightCm = it.roundToInt())) }, valueRange = 130f..220f, steps = 89)
                Text("Вес: ${String.format(java.util.Locale.US, "%.1f", profile.weightKg)} кг", fontWeight = FontWeight.Bold)
                Slider(profile.weightKg.toFloat(), { onProfileChange(profile.copy(weightKg = (it * 2).roundToInt() / 2.0)) }, valueRange = 35f..200f, steps = 329)
            }
            item {
                SectionTitle("${profile.daysPerWeek} тренировок в неделю", "От 1 до 6 дней")
                Slider(profile.daysPerWeek.toFloat(), { onProfileChange(profile.copy(daysPerWeek = it.roundToInt())) }, valueRange = 1f..6f, steps = 4)
            }
            item {
                SectionTitle("${profile.sessionMinutes} минут", "Максимальная длительность тренировки")
                Slider(profile.sessionMinutes.toFloat(), { value -> onProfileChange(profile.copy(sessionMinutes = ((value / 15).roundToInt() * 15).coerceIn(30, 120))) }, valueRange = 30f..120f, steps = 5)
            }
            item {
                SectionTitle("Формат", "Можно доверить выбор алгоритму")
                ChoiceRow(SplitPreference.entries, profile.splitPreference, { it.title }) { onProfileChange(profile.copy(splitPreference = it)) }
            }
            item {
                SectionTitle("Оборудование", "Покажем только доступные упражнения")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Equipment.entries.forEach { item ->
                        FilterChip(
                            selected = item in profile.equipment,
                            onClick = { onProfileChange(profile.copy(equipment = profile.equipment.toggle(item))) },
                            label = { Text(item.title) },
                        )
                    }
                }
            }
            item {
                SectionTitle("Приоритетные мышцы", "Необязательно — им достанется больше объёма")
                MusclePriorityPicker(profile.priorityMuscles) { muscle -> onProfileChange(profile.copy(priorityMuscles = profile.priorityMuscles.toggle(muscle))) }
            }
            item {
                SectionTitle("Боль и ограничения", "Мы покажем предупреждение, но не заменяем врача")
                OutlinedTextField(
                    value = profile.painNotes,
                    onValueChange = { onProfileChange(profile.copy(painNotes = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Например: дискомфорт в правом плече") },
                    minLines = 2,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, supporting: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(items: Iterable<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item), fontWeight = if (item == selected) FontWeight.Bold else FontWeight.Normal) })
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
