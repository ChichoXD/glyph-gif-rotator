package dev.glyphrotator.app.pokemon

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Guarda el progreso del juego (capturados, Pokédex, inventario y compañero de
 * entrenamiento) en SharedPreferences, serializando a JSON. Sin base de datos: el volumen
 * es pequeño y así el proyecto no arrastra Room solo por esto.
 */
class PokemonRepository(context: Context) : PokemonStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // =====================================================================================
    // Capturados
    // =====================================================================================

    override fun getCaught(): List<CaughtPokemon> {
        val raw = prefs.getString(KEY_CAUGHT, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            CaughtPokemon(
                uid = obj.optString("uid"),
                speciesId = obj.optInt("speciesId"),
                caughtAtMillis = obj.optLong("caughtAt"),
                level = obj.optInt("level", 1),
                exp = obj.optInt("exp", 0),
                isTrainingPartner = obj.optBoolean("training", false),
                isEvolutionLocked = obj.optBoolean("evoLocked", false),
                levelCap = obj.optInt("levelCap", 0).takeIf { it > 0 },
                nickname = obj.optString("nickname").ifBlank { null }
            )
        }
    }

    private fun saveCaught(list: List<CaughtPokemon>) {
        val array = JSONArray()
        list.forEach { pokemon ->
            array.put(
                JSONObject().apply {
                    put("uid", pokemon.uid)
                    put("speciesId", pokemon.speciesId)
                    put("caughtAt", pokemon.caughtAtMillis)
                    put("level", pokemon.level)
                    put("exp", pokemon.exp)
                    put("training", pokemon.isTrainingPartner)
                    put("evoLocked", pokemon.isEvolutionLocked)
                    pokemon.levelCap?.let { put("levelCap", it) }
                    pokemon.nickname?.let { put("nickname", it) }
                }
            )
        }
        prefs.edit().putString(KEY_CAUGHT, array.toString()).apply()
    }

    override fun addCaught(speciesId: Int, level: Int): CaughtPokemon {
        val pokemon = CaughtPokemon(
            uid = UUID.randomUUID().toString(),
            speciesId = speciesId,
            caughtAtMillis = System.currentTimeMillis(),
            level = level
        )
        saveCaught(getCaught() + pokemon)
        registerInPokedex(speciesId)
        return pokemon
    }

    override fun updateCaught(updated: CaughtPokemon) {
        saveCaught(getCaught().map { if (it.uid == updated.uid) updated else it })
    }

    override fun releaseCaught(uid: String) {
        saveCaught(getCaught().filterNot { it.uid == uid })
    }

    // =====================================================================================
    // Pokédex (especies vistas alguna vez)
    // =====================================================================================

    override fun getPokedex(): Set<Int> =
        prefs.getStringSet(KEY_POKEDEX, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    /** Marca una especie como vista (al capturarla o al evolucionar hacia ella). */
    override fun registerInPokedex(speciesId: Int) {
        val updated = getPokedex() + speciesId
        prefs.edit().putStringSet(KEY_POKEDEX, updated.map { it.toString() }.toSet()).apply()
    }

    // =====================================================================================
    // Compañero de entrenamiento (solo uno a la vez)
    // =====================================================================================

    override fun getTrainingPartner(): CaughtPokemon? = getCaught().firstOrNull { it.isTrainingPartner }

    override fun setTrainingPartner(uid: String?) {
        saveCaught(getCaught().map { it.copy(isTrainingPartner = it.uid == uid) })
    }

    // =====================================================================================
    // Inventario
    // =====================================================================================

    override fun getInventory(): Map<PokemonItem, Int> =
        PokemonItem.entries.mapNotNull { item ->
            val quantity = prefs.getInt(itemKey(item), 0)
            if (quantity > 0) item to quantity else null
        }.toMap()

    override fun getQuantity(item: PokemonItem): Int = prefs.getInt(itemKey(item), 0)

    override fun addItem(item: PokemonItem, amount: Int) {
        prefs.edit().putInt(itemKey(item), (getQuantity(item) + amount).coerceAtLeast(0)).apply()
    }

    /** Consume una unidad; devuelve false si no quedaba ninguna. */
    override fun consumeItem(item: PokemonItem): Boolean {
        val quantity = getQuantity(item)
        if (quantity <= 0) return false
        prefs.edit().putInt(itemKey(item), quantity - 1).apply()
        return true
    }

    private fun itemKey(item: PokemonItem) = "item_${item.name}"

    // =====================================================================================
    // Marca de tiempo del último tick de entrenamiento
    // =====================================================================================

    override var lastTrainingTickMillis: Long
        get() = prefs.getLong(KEY_LAST_TRAINING_TICK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_TRAINING_TICK, value).apply()

    private companion object {
        const val PREFS_NAME = "glyph_pokemon_prefs"
        const val KEY_CAUGHT = "caught"
        const val KEY_POKEDEX = "pokedex"
        const val KEY_LAST_TRAINING_TICK = "last_training_tick"
    }
}
