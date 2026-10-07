package ru.bodybuild.core.model

fun Exercise.matchesSearch(query: String): Boolean {
    fun normalized(value: String) = value.lowercase().replace('ё', 'е').replace("ез", "ez")
    val haystack = normalized(listOf(name, equipment.joinToString(" ") { it.title },
        stimuli.filter { it.role == StimulusRole.PRIMARY }.joinToString(" ") { it.region.group.title }).joinToString(" "))
    return normalized(query).split(Regex("\\s+")).filter { it.isNotBlank() }.all { it in haystack }
}
