package ru.bodybuild.domain.catalog

import ru.bodybuild.core.model.*

/**
 * Reviewed mappings of movement variants, not new scientific stimulus measurements.
 * Related movements share provisional coefficients; catalog entries remain unreviewed.
 */
internal object ExtendedCatalog {
    fun exercises(base: List<Exercise>): List<Exercise> {
        val originals = base.associateBy { it.id }
        fun variant(id: String, name: String, baseId: String, equipment: Set<Equipment>, unilateral: Boolean = false): Exercise {
            val original = originals.getValue(baseId)
            return original.copy(id = id, name = name, equipment = equipment, unilateral = unilateral,
                catalogVersion = 3, reviewed = false, warnings = emptyList())
        }
        return listOf(
            variant("ez-curl", "Сгибание рук с EZ-грифом", "barbell-curl", setOf(Equipment.EZ_BAR)),
            variant("ez-close-curl", "Сгибание с EZ-грифом узким хватом", "barbell-curl", setOf(Equipment.EZ_BAR)),
            variant("barbell-close-curl", "Сгибание с прямой штангой узким хватом", "barbell-curl", setOf(Equipment.BARBELL)),
            variant("barbell-wide-curl", "Сгибание с прямой штангой широким хватом", "barbell-curl", setOf(Equipment.BARBELL)),
            variant("ez-preacher-curl", "Сгибание с EZ-грифом на скамье Скотта", "preacher-curl", setOf(Equipment.EZ_BAR, Equipment.BENCH)),
            variant("barbell-reverse-curl", "Обратное сгибание с прямой штангой", "reverse-curl", setOf(Equipment.BARBELL)),
            variant("barbell-drag-curl", "Сгибание штанги вдоль корпуса", "barbell-curl", setOf(Equipment.BARBELL)),
            variant("db-concentration-curl", "Концентрированное сгибание с гантелью", "db-curl", setOf(Equipment.DUMBBELLS, Equipment.BENCH), unilateral = true),
            variant("db-preacher-curl", "Сгибание одной руки с гантелью на скамье Скотта", "preacher-curl", setOf(Equipment.DUMBBELLS, Equipment.BENCH), unilateral = true),
            variant("rope-hammer-curl", "Молотковые сгибания с канатом на блоке", "hammer-curl", setOf(Equipment.CABLE)),
            variant("db-cross-hammer", "Молотки поперёк корпуса", "hammer-curl", setOf(Equipment.DUMBBELLS), unilateral = true),
            variant("db-seated-curl", "Сгибание гантелей сидя", "db-curl", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("db-zottman", "Сгибания Зоттмана", "db-curl", setOf(Equipment.DUMBBELLS)),
            variant("cable-preacher-curl", "Сгибание на блоке на скамье Скотта", "preacher-curl", setOf(Equipment.CABLE, Equipment.BENCH)),
            variant("incline-hammer", "Молотки на наклонной скамье", "hammer-curl", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("calf-leg-press", "Подъём на носки в жиме ногами", "calf-raise", setOf(Equipment.MACHINE)),
            variant("calf-smith", "Подъём на носки в Смите", "calf-raise", setOf(Equipment.MACHINE)),
            variant("calf-barbell", "Подъём на носки со штангой", "calf-raise", setOf(Equipment.BARBELL)),
            variant("calf-seated-barbell", "Подъём на носки сидя со штангой", "seated-calf", setOf(Equipment.BARBELL, Equipment.BENCH)),
            variant("calf-seated-db", "Подъём на носок сидя с гантелью", "seated-calf", setOf(Equipment.DUMBBELLS, Equipment.BENCH), unilateral = true),
            variant("calf-donkey", "Подъём на носки в наклоне в тренажёре", "calf-raise", setOf(Equipment.MACHINE)),
            variant("decline-bench", "Жим штанги на скамье с отрицательным наклоном", "barbell-bench", setOf(Equipment.BARBELL, Equipment.BENCH)),
            variant("decline-db-bench", "Жим гантелей с отрицательным наклоном", "db-bench", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("neutral-db-bench", "Жим гантелей нейтральным хватом", "db-bench", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("incline-db-fly", "Разведение гантелей на наклонной скамье", "db-fly", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("smith-bench", "Жим лёжа в Смите", "barbell-bench", setOf(Equipment.MACHINE, Equipment.BENCH)),
            variant("cable-chest-press", "Жим от груди на блоках стоя", "machine-chest", setOf(Equipment.CABLE)),
            variant("arnold-press", "Жим Арнольда сидя", "overhead-press", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("barbell-shoulder-press", "Жим штанги стоя", "overhead-press", setOf(Equipment.BARBELL)),
            variant("seated-db-press", "Жим гантелей сидя", "overhead-press", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("seated-lateral", "Разведение гантелей в стороны сидя", "lateral-raise", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("cable-rear-fly", "Разведение рук на заднюю дельту в кроссовере", "db-reverse-fly", setOf(Equipment.CABLE)),
            variant("seated-rear-fly", "Разведение гантелей в наклоне сидя", "db-reverse-fly", setOf(Equipment.DUMBBELLS, Equipment.BENCH)),
            variant("reverse-grip-row", "Тяга штанги в наклоне обратным хватом", "barbell-row", setOf(Equipment.BARBELL)),
            variant("two-db-row", "Тяга двух гантелей в наклоне", "barbell-row", setOf(Equipment.DUMBBELLS)),
            variant("single-cable-row", "Тяга блока одной рукой сидя", "cable-row", setOf(Equipment.CABLE), unilateral = true),
            variant("tbar-row", "Тяга Т-грифа с рукоятью", "barbell-row", setOf(Equipment.BARBELL)),
            variant("smith-row", "Тяга в наклоне в Смите", "barbell-row", setOf(Equipment.MACHINE)),
            variant("lying-tbar-row", "Тяга Т-грифа с опорой грудью", "machine-row", setOf(Equipment.MACHINE)),
            variant("ez-skullcrusher", "Французский жим лёжа с EZ-грифом", "lying-db-extension", setOf(Equipment.EZ_BAR, Equipment.BENCH)),
            variant("single-cable-extension", "Разгибание одной руки на блоке", "cable-pushdown", setOf(Equipment.CABLE), unilateral = true),
            variant("reverse-pushdown", "Разгибание рук на блоке обратным хватом", "cable-pushdown", setOf(Equipment.CABLE)),
            variant("vbar-pushdown", "Разгибание рук с V-рукоятью", "cable-pushdown", setOf(Equipment.CABLE)),
            variant("db-kickback", "Разгибание руки с гантелью в наклоне", "lying-db-extension", setOf(Equipment.DUMBBELLS, Equipment.BENCH), unilateral = true),
            variant("machine-triceps", "Разгибание рук в тренажёре", "cable-pushdown", setOf(Equipment.MACHINE)),
            variant("cable-lying-extension", "Разгибание рук лёжа на блоке", "lying-db-extension", setOf(Equipment.CABLE, Equipment.BENCH)),
            variant("front-squat", "Фронтальный присед со штангой", "barbell-squat", setOf(Equipment.BARBELL)),
            variant("smith-squat", "Присед в Смите", "barbell-squat", setOf(Equipment.MACHINE)),
            variant("db-squat", "Присед с гантелями по бокам", "goblet-squat", setOf(Equipment.DUMBBELLS)),
            variant("db-step-up", "Зашагивание на скамью с гантелями", "reverse-lunge", setOf(Equipment.DUMBBELLS, Equipment.BENCH), unilateral = true),
            variant("barbell-lunge", "Выпады со штангой", "reverse-lunge", setOf(Equipment.BARBELL), unilateral = true),
            variant("one-leg-extension", "Разгибание одной ноги в тренажёре", "leg-extension", setOf(Equipment.MACHINE), unilateral = true),
            variant("narrow-leg-press", "Жим ногами с узкой постановкой", "leg-press", setOf(Equipment.MACHINE)),
            variant("ab-machine", "Скручивания в тренажёре", "cable-crunch", setOf(Equipment.MACHINE)),
            variant("hanging-leg-raise", "Подъём ног в висе", "reverse-crunch", setOf(Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR)),
            variant("bench-leg-raise", "Подъём ног лёжа на скамье", "reverse-crunch", setOf(Equipment.BODYWEIGHT, Equipment.BENCH)),
            variant("decline-crunch", "Скручивания на наклонной скамье", "crunch", setOf(Equipment.BODYWEIGHT, Equipment.BENCH)),
            variant("bicycle-crunch", "Скручивания велосипед", "crunch", setOf(Equipment.BODYWEIGHT)),
            variant("db-side-bend", "Наклон в сторону с гантелью", "pallof-press", setOf(Equipment.DUMBBELLS), unilateral = true),
            variant("cable-woodchop", "Дровосек на верхнем блоке", "pallof-press", setOf(Equipment.CABLE), unilateral = true),
        ).map { exercise ->
            when (exercise.id) {
                "decline-bench", "decline-db-bench" -> exercise.copy(stimuli = exercise.stimuli.map { if (it.region == MuscleRegion.MID_CHEST) it.copy(region = MuscleRegion.LOWER_CHEST) else it })
                "incline-db-fly" -> exercise.copy(stimuli = exercise.stimuli.map { if (it.region == MuscleRegion.MID_CHEST) it.copy(region = MuscleRegion.UPPER_CHEST) else it })
                "db-kickback" -> exercise.copy(stimuli = exercise.stimuli.map { it.copy(lengthBias = LengthBias.SHORTENED) })
                else -> exercise
            }
        }
    }
}
