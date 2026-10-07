package ru.bodybuild.domain.catalog

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.bodybuild.core.model.*

/** Generated web data uses the exact Android catalog, including editorial coefficients. */
fun main(args: Array<String>) {
    val json = Json { encodeDefaults = true; prettyPrint = true }
    val document = buildJsonObject {
        put("version", 3)
        put("exercises", json.parseToJsonElement(json.encodeToString(SeedCatalog.exercises)))
        put("groups", buildJsonObject { MuscleGroup.entries.forEach { put(it.name, it.title) } })
        put("equipment", buildJsonObject { Equipment.entries.forEach { put(it.name, it.title) } })
        put("regions", buildJsonObject { MuscleRegion.entries.forEach { region -> put(region.name, buildJsonObject { put("title", region.title); put("group", region.group.name) }) } })
    }
    File(args.single()).apply { parentFile.mkdirs(); writeText(json.encodeToString(document)) }
    println("Exported ${SeedCatalog.exercises.size} exercises")
}
