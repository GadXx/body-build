package ru.bodybuild.feature.exercise

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.bodybuild.core.model.Exercise

private object PhotoCache : LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

@Composable
fun ExercisePhotoPreview(exercise: Exercise, modifier: Modifier = Modifier) {
    Photo(exercise, if (exercise.id in setOf("plank", "side-plank")) 1 else 0, modifier.clip(MaterialTheme.shapes.medium))
}

@Composable
fun ExercisePhotos(exercise: Exercise, modifier: Modifier = Modifier) {
    var frame by rememberSaveable(exercise.id) { mutableIntStateOf(if (exercise.id in setOf("plank", "side-plank")) 1 else 0) }
    var enlarged by rememberSaveable(exercise.id) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(MaterialTheme.shapes.medium).clickable { enlarged = true }) {
            Photo(exercise, frame, Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), color = Color.Black.copy(alpha = .7f), shape = MaterialTheme.shapes.small) {
                Text("Увеличить ↗", color = Color.White, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        Frames(frame) { frame = it }
        ExercisePhotoCatalog.notes[exercise.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (enlarged) Dialog(onDismissRequest = { enlarged = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                Photo(exercise, frame, Modifier.fillMaxWidth().aspectRatio(1.5f))
                Frames(frame) { frame = it }
                ExercisePhotoCatalog.notes[exercise.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Фото: Free Exercise DB · Unlicense", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { enlarged = false }, modifier = Modifier.align(Alignment.End)) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun Frames(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("1 · Позиция", "2 · Позиция").forEachIndexed { index, label ->
            FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(label) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Photo(exercise: Exercise, frame: Int, modifier: Modifier) {
    val context = LocalContext.current
    val source = ExercisePhotoCatalog.sources[exercise.id]
    val path = "exercise-photos/$source/$frame.jpg"
    val photo by produceState<Pair<Boolean, Bitmap?>>(initialValue = false to null, path) {
        value = false to null
        val decoded = withContext(Dispatchers.IO) {
            PhotoCache.get(path) ?: runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.also { PhotoCache.put(path, it) }
        }
        value = true to decoded
    }
    Box(modifier.background(Color(0xFF151515)), contentAlignment = Alignment.Center) {
        photo.second?.let { Image(it.asImageBitmap(), "${exercise.name}: позиция ${frame + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Text(if (photo.first) "Не удалось открыть фото" else "Фото загружается", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
