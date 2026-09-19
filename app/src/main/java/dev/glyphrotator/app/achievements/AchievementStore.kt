package dev.glyphrotator.app.achievements

import android.content.Context
import dev.glyphrotator.app.habits.HabitRules
import dev.glyphrotator.app.habits.HabitStore
import dev.glyphrotator.app.habits.UsageStore
import dev.glyphrotator.app.habits.WaterStore
import dev.glyphrotator.app.pokemon.EggStore
import dev.glyphrotator.app.pokemon.PokemonRegistry
import dev.glyphrotator.app.pokemon.PokemonRepository
import dev.glyphrotator.app.pokemon.spawn.SleepTracker

/**
 * Qué logros están desbloqueados y cuándo se consiguieron.
 *
 * Guarda **solo la fecha del desbloqueo**, no el progreso: el progreso se recalcula del estado
 * real cada vez ([AchievementCatalog]), así que aquí solo hace falta recordar que ya se avisó,
 * para no dar el mismo aviso dos veces, y poder enseñar "conseguido el 3 de agosto".
 *
 * Algunas cosas no se pueden deducir del estado —cuántas evoluciones llevas, cuántos huevos has
 * abierto, cuántos has soltado— porque no dejan rastro: un Pokémon evolucionado no recuerda que
 * lo hizo. Esos sí se cuentan aquí, y son los únicos.
 */
class AchievementStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** El día en que se consiguió, o null si aún no. */
    fun unlockedOn(id: String): Int? = prefs.getInt(key(id), 0).takeIf { it > 0 }

    fun isUnlocked(id: String): Boolean = unlockedOn(id) != null

    fun unlockedCount(): Int = AchievementCatalog.all.count { isUnlocked(it.id) }

    /**
     * Repasa el catálogo y devuelve los que se acaban de conseguir.
     *
     * Devuelve solo los **nuevos** para que quien llame pueda avisar de ellos sin repetir los de
     * siempre. Si devuelve una lista vacía, no ha pasado nada y no hay que hacer nada.
     */
    fun checkNewlyUnlocked(snapshot: GameSnapshot = snapshot()): List<Achievement> {
        val today = HabitRules.today()
        val fresh = AchievementCatalog.all.filter { it.isUnlocked(snapshot) && !isUnlocked(it.id) }
        if (fresh.isEmpty()) return emptyList()

        val editor = prefs.edit()
        for (achievement in fresh) editor.putInt(key(achievement.id), today)
        editor.apply()
        return fresh
    }

    // Los contadores que el estado no puede reconstruir por sí solo.
    fun registerEvolution() = bump(KEY_EVOLUTIONS)
    fun registerHatch() = bump(KEY_HATCHED)
    fun registerRelease() = bump(KEY_RELEASED)
    fun registerNightCatch() = bump(KEY_NIGHT_CATCH)
    fun registerLowBatteryCatch() = bump(KEY_LOW_BATTERY_CATCH)
    fun registerPerfectDay() = bump(KEY_PERFECT_DAYS)
    fun registerPerfectNight() = bump(KEY_PERFECT_NIGHTS)

    private fun bump(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    /**
     * Reúne el estado de todas las piezas del juego en una sola foto.
     *
     * Se hace aquí y no en cada logro porque son más de treinta y cada uno abriría los mismos
     * archivos: treinta lecturas del disco cada vez que se abre la pantalla, para leer lo mismo.
     */
    fun snapshot(): GameSnapshot {
        val repository = PokemonRepository(appContext)
        val caught = repository.getCaught()
        val pokedex = repository.getPokedex()
        val eggs = EggStore(appContext)
        val water = WaterStore(appContext)
        val sleep = SleepTracker(appContext)
        val habits = HabitStore(appContext).all()

        val types = caught.mapNotNull { it.species }
            .flatMap { listOfNotNull(it.type1, it.type2) }
            .distinct()
            .size

        return GameSnapshot(
            caughtCount = caught.size,
            pokedexCount = pokedex.size,
            speciesTotal = PokemonRegistry.all.size,
            distinctTypes = types,
            evolutionsDone = prefs.getInt(KEY_EVOLUTIONS, 0),
            highestLevel = caught.maxOfOrNull { it.level } ?: 0,
            cappedCount = caught.count { it.levelCap != null },
            eggsHatched = prefs.getInt(KEY_HATCHED, 0),
            eggsHeld = eggs.count,
            startersOwned = STARTERS.count { it in pokedex },
            startersFinal = STARTERS_FINAL.count { it in pokedex },
            legendaryBirds = LEGENDARY_BIRDS.count { it in pokedex },
            hasMewtwo = MEWTWO in pokedex,
            bestStreak = maxOf(water.bestStreak, habits.maxOfOrNull { it.streak() } ?: 0),
            perfectDays = prefs.getInt(KEY_PERFECT_DAYS, 0),
            waterDaysMet = water.daysMet,
            nightsRecorded = sleep.nightsRecorded,
            perfectNights = prefs.getInt(KEY_PERFECT_NIGHTS, 0),
            habitsCreated = habits.size,
            bestHabitStreak = habits.maxOfOrNull { it.streak() } ?: 0,
            caughtAtNight = prefs.getInt(KEY_NIGHT_CATCH, 0),
            caughtOnLowBattery = prefs.getInt(KEY_LOW_BATTERY_CATCH, 0),
            releasedCount = prefs.getInt(KEY_RELEASED, 0),
        )
    }

    private fun key(id: String) = "unlocked_$id"

    private companion object {
        const val PREFS_NAME = "glyph_achievements"
        const val KEY_EVOLUTIONS = "evolutions"
        const val KEY_HATCHED = "hatched"
        const val KEY_RELEASED = "released"
        const val KEY_NIGHT_CATCH = "night_catch"
        const val KEY_LOW_BATTERY_CATCH = "low_battery_catch"
        const val KEY_PERFECT_DAYS = "perfect_days"
        const val KEY_PERFECT_NIGHTS = "perfect_nights"

        val STARTERS = setOf(1, 4, 7)
        val STARTERS_FINAL = setOf(3, 6, 9)
        val LEGENDARY_BIRDS = setOf(144, 145, 146)
        const val MEWTWO = 150
    }
}
