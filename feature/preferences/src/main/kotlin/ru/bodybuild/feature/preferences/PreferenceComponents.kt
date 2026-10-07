package ru.bodybuild.feature.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import ru.bodybuild.core.model.MuscleGroup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusclePriorityPicker(selected: Set<MuscleGroup>, onToggle: (MuscleGroup) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MuscleGroup.entries.forEach { muscle ->
            FilterChip(
                selected = muscle in selected,
                onClick = { onToggle(muscle) },
                label = { Text(muscle.title) },
            )
        }
    }
}
