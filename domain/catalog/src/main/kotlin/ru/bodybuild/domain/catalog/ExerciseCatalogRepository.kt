package ru.bodybuild.domain.catalog

import kotlinx.coroutines.flow.Flow
import ru.bodybuild.core.model.Equipment
import ru.bodybuild.core.model.Exercise
import ru.bodybuild.core.model.MuscleGroup

interface ExerciseCatalogRepository {
    fun observeAll(): Flow<List<Exercise>>
    suspend fun getAll(): List<Exercise>
    suspend fun findById(id: String): Exercise?
    suspend fun search(
        query: String = "",
        muscle: MuscleGroup? = null,
        equipment: Set<Equipment> = emptySet(),
    ): List<Exercise>
}
